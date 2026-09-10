package dev.betterwork.platform

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import co.touchlab.kermit.Logger
import dev.betterwork.data.LibraryCodec
import dev.betterwork.data.LibraryRepository
import dev.betterwork.db.BetterDatabase
import dev.betterwork.domain.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString

open class BetterApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val isWatch by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
    val repository by lazy {
        LibraryRepository(
            BetterDatabase(AndroidSqliteDriver(BetterDatabase.Schema, this, "betterwork.db")),
            { UUID.randomUUID().toString() },
            !isWatch,
        )
    }
    val clock = MonotonicClock { SystemClock.elapsedRealtime() }
    var engine: Session? = null
        internal set

    private val mutableSession = MutableStateFlow<Checkpoint?>(null)
    val sessions = mutableSession.asStateFlow()
    val message = MutableStateFlow<String?>(null)
    val syncStatus = MutableStateFlow("Library saved on this device")
    val recovered = MutableStateFlow(false)
    /** A phone-owned mirror received by Wear; never used to drive TimerService. */
    val remoteSession = MutableStateFlow<RemoteSession?>(null)
    val remoteNodeId = MutableStateFlow<String?>(null)
    val remoteConnected = MutableStateFlow(false)
    val remoteAck = MutableStateFlow<RemoteAck?>(null)
    internal var sessionRevision: Long = 0
    private val remoteCommandIds = LinkedHashSet<String>()
    var sessionHandle: String = UUID.randomUUID().toString()
        internal set

    internal var pendingStart: String? = null
    val hasActiveSession: Boolean
        get() =
            pendingStart != null ||
                engine?.state?.phase in listOf(Phase.RUNNING, Phase.PAUSED, Phase.WAITING)

    override fun onCreate() {
        super.onCreate()
        try {
            engine = repository.checkpoint()?.let { Session.recover(it, clock) }
            mutableSession.value = engine?.state
            recovered.value = engine != null
        } catch (error: IllegalArgumentException) {
            Logger.e(error) { "Recovery checkpoint could not be read" }
            message.value = "Saved session could not be recovered. Start a new routine."
        }
        scope.launch {
            if (isWatch) LibrarySync.readLatest(this@BetterApp)
            else repository.library.collect { LibrarySync.publish(this@BetterApp) }
        }
    }

    fun start(routine: Routine, options: SessionOptions): Boolean {
        if (hasActiveSession) {
            message.value = "Stop the current session before starting another."
            return false
        }
        return requestStart(routine, options, false)
    }

    fun replace(routine: Routine, options: SessionOptions, expectedHandle: String): Boolean {
        if (pendingStart != null || expectedHandle != sessionHandle) return false
        return requestStart(routine, options, true)
    }

    private fun requestStart(routine: Routine, options: SessionOptions, replace: Boolean): Boolean {
        routine.validate()
        options.validate()
        val request = UUID.randomUUID().toString()
        pendingStart = request
        try {
            startForegroundService(
                Intent(this, TimerService::class.java)
                    .setAction(if (replace) "replace" else "start")
                    .putExtra("request", request)
                    .putExtra("handle", sessionHandle)
                    .putExtra("routine", LibraryCodec.json.encodeToString(routine))
                    .putExtra("options", LibraryCodec.json.encodeToString(options))
            )
        } catch (error: IllegalStateException) {
            pendingStart = null
            message.value = "Could not start the timer. Open BetterWork and try again."
            Logger.w(error) { "Timer service start rejected" }
            return false
        }
        return true
    }

    fun command(command: SessionCommand, expectedHandle: String = sessionHandle) {
        if (expectedHandle != sessionHandle) return
        command(command.action)
    }

    fun command(action: String) {
        startForegroundService(
            Intent(this, TimerService::class.java)
                .setAction(action)
                .putExtra("handle", sessionHandle)
        )
    }

    fun updateCues(options: SessionOptions, expectedHandle: String = sessionHandle) {
        if (expectedHandle != sessionHandle) return
        startForegroundService(
            Intent(this, TimerService::class.java)
                .setAction("cues")
                .putExtra("handle", expectedHandle)
                .putExtra("options", LibraryCodec.json.encodeToString(options))
        )
    }

    internal fun publish(persist: Boolean) {
        val state = engine?.state
        mutableSession.value = state
        if (persist && !isWatch) {
            sessionRevision += 1
            scope.launch {
                SessionSync.publish(
                    this@BetterApp,
                    state?.takeUnless { it.phase in listOf(Phase.COMPLETED, Phase.STOPPED) },
                    sessionRevision,
                )
            }
        }
        if (persist)
            repository.checkpoint(
                state?.takeUnless { it.phase in listOf(Phase.COMPLETED, Phase.STOPPED) }
            )
    }

    internal fun acceptRemoteCommand(command: RemoteCommand): Boolean {
        if (isWatch || command.schema != 1 || command.sessionId != sessionHandle) return false
        if (!remoteCommandIds.add(command.commandId)) return false
        if (remoteCommandIds.size > 128) remoteCommandIds.remove(remoteCommandIds.first())
        if (command.expectedRevision != sessionRevision || !hasActiveSession) return false
        this.command(command.command)
        return true
    }

    fun dismiss() {
        if (hasActiveSession) return
        engine = null
        repository.checkpoint(null)
        mutableSession.value = null
        recovered.value = false
    }

    fun preview(cue: Cue) {
        if (engine?.state?.phase == Phase.RUNNING) {
            message.value = "Pause the timer to preview a vibration pattern."
            return
        }
        Cues(this).play(cue, false)
    }
}
