package dev.betterwork.wear

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.*
import dev.betterwork.domain.*
import dev.betterwork.platform.*
import dev.betterwork.presentation.formatTime
import kotlinx.coroutines.launch

@Composable
internal fun RemoteTimer(app: BetterApp, back: () -> Unit) {
    val remote by app.remoteSession.collectAsState()
    val connected by app.remoteConnected.collectAsState()
    val ack by app.remoteAck.collectAsState()
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(remote?.revision) {
        while (remote != null) {
            now = android.os.SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(250)
        }
    }
    val snapshot = remote
    if (snapshot == null) {
        WatchList("Phone session") {
            item { WatchText("Phone session unavailable") }
            item { WatchAction("Back", action = back) }
        }
        return
    }
    val state = snapshot.checkpoint
    val elapsed = (now - snapshot.receivedAtElapsedMs).coerceAtLeast(0)
    val remaining =
        if (state.phase == Phase.RUNNING) (state.remainingMs - elapsed).coerceAtLeast(0)
        else state.remainingMs
    val command: (SessionCommand) -> Unit = { requested ->
        if (connected) {
            app.scope.launch { SessionSync.sendCommand(app, newRemoteCommand(snapshot, requested)) }
        }
    }
    var stopping by rememberSaveable(snapshot.sessionId) { mutableStateOf(false) }
    WatchList("Phone session", timer = true, resetKey = snapshot.revision) {
        item { WatchText(state.routine.name) }
        item { WatchCountdownRemote(formatTime(remaining), "remaining") }
        if (!connected) item { WatchText("Phone disconnected. Controls are unavailable.") }
        ack?.reason?.let { reason -> item { WatchText("Command not applied: $reason") } }
        item {
            WatchAction(
                when (state.phase) {
                    Phase.RUNNING -> "Pause"
                    Phase.PAUSED -> "Resume"
                    Phase.WAITING -> "Continue"
                    else -> "Back"
                },
                primary = true,
                enabled = connected,
            ) {
                when (state.phase) {
                    Phase.RUNNING -> command(SessionCommand.PAUSE)
                    Phase.PAUSED -> command(SessionCommand.RESUME)
                    Phase.WAITING -> command(SessionCommand.CONTINUE)
                    else -> back()
                }
            }
        }
        item {
            WatchText(
                "Cycle ${state.cycle} · Step ${state.index + 1} · ${formatTime(state.activeMs)} active"
            )
        }
        item { WatchAction("Skip step", enabled = connected) { command(SessionCommand.SKIP) } }
        item { WatchAction("Stop session", enabled = connected) { stopping = true } }
        item { WatchAction("Back to routines", action = back) }
    }
    if (stopping) {
        WatchStopDialog({ stopping = false }) {
            command(SessionCommand.STOP)
            stopping = false
            back()
        }
    }
}

@Composable
private fun WatchCountdownRemote(value: String, description: String) {
    Text(
        value,
        style = MaterialTheme.typography.numeralMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$value $description" },
    )
}
