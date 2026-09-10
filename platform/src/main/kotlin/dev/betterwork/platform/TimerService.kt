package dev.betterwork.platform

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import co.touchlab.kermit.Logger
import dev.betterwork.data.LibraryCodec
import dev.betterwork.domain.*
import dev.betterwork.presentation.formatTime
import kotlinx.coroutines.*

class TimerService : Service() {
    private val app
        get() = application as BetterApp

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var cues: Cues
    private var loop: Job? = null
    private var lastPersist = 0L
    private var lastNotification = ""

    override fun onCreate() {
        super.onCreate()
        cues = Cues(this)
        wakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BetterWork:interval")
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(CHANNEL, "Active timer", NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        setSound(null, null)
                        enableVibration(false)
                    }
            )
        startForeground(NOTIFICATION, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val expected = intent.getStringExtra("handle")
        if (expected != null && expected != app.sessionHandle) {
            if (app.engine == null) stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            "start",
            "replace" ->
                if (
                    intent.action == "replace" ||
                        app.engine?.state?.phase !in
                            listOf(Phase.RUNNING, Phase.PAUSED, Phase.WAITING)
                ) {
                    val routine =
                        LibraryCodec.json.decodeFromString<Routine>(
                            requireNotNull(intent.getStringExtra("routine"))
                        )
                    val options =
                        LibraryCodec.json.decodeFromString<SessionOptions>(
                            requireNotNull(intent.getStringExtra("options"))
                        )
                    if (intent.action == "replace") {
                        app.engine?.stop()
                        cues.cancel()
                    }
                    app.engine = Session.start(routine, options, app.clock)
                    app.sessionHandle =
                        intent.getStringExtra("request") ?: java.util.UUID.randomUUID().toString()
                    app.pendingStart = null
                    app.recovered.value = false
                }
            "pause" -> app.engine?.pause()
            "resume" -> {
                app.engine?.resume()
                app.recovered.value = false
            }
            "confirm" -> {
                app.engine?.confirm()
                app.recovered.value = false
            }
            "cues" -> {
                val options =
                    LibraryCodec.json.decodeFromString<SessionOptions>(
                        requireNotNull(intent.getStringExtra("options"))
                    )
                app.engine?.updateCues(options.cueOverride, options.completionCue, options.sound)
            }
            "skip" -> app.engine?.skip()
            "stop" -> {
                app.engine?.stop()
                cues.cancel()
            }
        }
        update(force = true)
        if (
            loop == null &&
                app.engine?.state?.phase in listOf(Phase.RUNNING, Phase.PAUSED, Phase.WAITING)
        ) {
            loop =
                scope.launch {
                    while (isActive) {
                        delay(100)
                        app.engine?.tick()
                        update(force = false)
                    }
                }
        }
        return START_NOT_STICKY
    }

    // A user-started foreground timer can repeat without a limit. Release on every inactive
    // phase and in onDestroy; an arbitrary timeout would silently disable background cues.
    @SuppressLint("WakelockTimeout")
    private fun update(force: Boolean) {
        val engine =
            app.engine
                ?: run {
                    stopSelf()
                    return
                }
        val events = engine.drainCues()
        val now = SystemClock.elapsedRealtime()
        val persist = force || events.isNotEmpty() || now - lastPersist >= 1000
        app.publish(persist)
        if (persist) lastPersist = now
        events.forEach {
            val deliveredAt = SystemClock.elapsedRealtime()
            Logger.i {
                "GW_CUE serial=${it.serial} pattern=${it.cue} monotonic=$deliveredAt active=${engine.state.activeMs}"
            }
            cues.play(it.cue, it.sound)
        }
        if (engine.state.phase == Phase.RUNNING) {
            if (!wakeLock.isHeld) wakeLock.acquire()
        } else if (wakeLock.isHeld) wakeLock.release()
        val notificationKey = "${engine.state.phase}:${engine.state.index}:${engine.state.cycle}"
        if (force || notificationKey != lastNotification) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
            lastNotification = notificationKey
        }
        if (engine.state.phase in listOf(Phase.COMPLETED, Phase.STOPPED)) {
            loop?.cancel()
            loop = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        launch.putExtra("openTimer", true)
        val pending =
            PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val state = app.engine?.state
        val running = state?.phase == Phase.RUNNING
        val title = app.engine?.step?.name ?: "BetterWork"
        val builder =
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(state?.routine?.name ?: "BetterWork")
                .setContentText(notificationText(state, title))
                .setContentIntent(pending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (running)
            builder
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(
                    System.currentTimeMillis() +
                        minOf(
                            state.remainingMs,
                            state.options.limitMs?.minus(state.activeMs) ?: Long.MAX_VALUE,
                        )
                )
        val action =
            when (state?.phase) {
                Phase.RUNNING -> "pause"
                Phase.WAITING -> "confirm"
                else -> "resume"
            }
        val label =
            when (action) {
                "pause" -> "Pause"
                "confirm" -> "Continue"
                else -> "Resume"
            }
        builder
            .addAction(0, label, actionIntent(action, 1))
            .addAction(0, "Stop", actionIntent("stop", 2))
        if (app.isWatch) {
            OngoingActivity.Builder(this, NOTIFICATION, builder)
                .setStaticIcon(R.drawable.ic_timer)
                .setTouchIntent(pending)
                .setStatus(ongoingStatus(state, title))
                .build()
                .apply(this)
        }
        return builder.build()
    }

    private fun notificationText(state: Checkpoint?, title: String): String {
        if (state == null) return "Starting"
        val remaining =
            minOf(state.remainingMs, state.options.limitMs?.minus(state.activeMs) ?: Long.MAX_VALUE)
        return when (state.phase) {
            Phase.RUNNING -> title
            Phase.PAUSED -> "Paused · $title · ${formatTime(remaining)} remaining"
            Phase.WAITING -> {
                val steps = state.routine.expandedSteps()
                "$title complete · Next: ${steps[(state.index + 1) % steps.size].name}"
            }
            Phase.COMPLETED -> "Complete"
            Phase.STOPPED -> "Stopped"
        }
    }

    private fun ongoingStatus(state: Checkpoint?, title: String): Status {
        val builder = Status.Builder().addPart("step", Status.TextPart(title))
        if (state?.phase == Phase.RUNNING) {
            val remaining =
                minOf(
                    state.remainingMs,
                    state.options.limitMs?.minus(state.activeMs) ?: Long.MAX_VALUE,
                )
            builder.addPart("time", Status.TimerPart(SystemClock.elapsedRealtime() + remaining))
            builder.addTemplate("#step# #time#")
        } else {
            builder.addTemplate(
                if (state?.phase == Phase.WAITING) "#step# · Continue when ready"
                else "#step# · Paused"
            )
        }
        return builder.build()
    }

    private fun actionIntent(action: String, request: Int): PendingIntent =
        PendingIntent.getService(
            this,
            request,
            Intent(this, TimerService::class.java)
                .setAction(action)
                .setData(android.net.Uri.parse("betterwork://session/${app.sessionHandle}/$action"))
                .putExtra("handle", app.sessionHandle),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        if (app.engine?.state?.phase == Phase.RUNNING) {
            app.engine?.pause()
            app.publish(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "timer"
        private const val NOTIFICATION = 1
    }
}
