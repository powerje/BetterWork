package dev.betterwork.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import dev.betterwork.data.Library
import dev.betterwork.domain.Checkpoint
import dev.betterwork.domain.Phase
import dev.betterwork.domain.SessionCommand
import dev.betterwork.domain.Step
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

data class TimerUi(
    val session: Checkpoint?,
    val label: String,
    val time: String,
    val status: String,
    val nextStep: Step? = null,
    val stepCount: Int = 0,
) {
    val effectiveRemainingMs: Long
        get() =
            session?.let {
                minOf(it.remainingMs, it.options.limitMs?.minus(it.activeMs) ?: Long.MAX_VALUE)
                    .coerceAtLeast(0)
            } ?: 0

    val endsDuringStep: Boolean
        get() = session?.let { effectiveRemainingMs < it.remainingMs } ?: false

    val terminal: Boolean
        get() = session?.phase in listOf(Phase.COMPLETED, Phase.STOPPED)

    val primaryCommand: SessionCommand
        get() =
            when (session?.phase) {
                Phase.RUNNING -> SessionCommand.PAUSE
                Phase.WAITING -> SessionCommand.CONTINUE
                else -> SessionCommand.RESUME
            }

    val primaryLabel: String
        get() =
            when (primaryCommand) {
                SessionCommand.PAUSE -> "Pause"
                SessionCommand.CONTINUE -> "Continue"
                else -> "Resume"
            }
}

data class AppUi(val library: Library, val timer: TimerUi)

fun CoroutineScope.present(
    library: StateFlow<Library>,
    session: StateFlow<Checkpoint?>,
): StateFlow<AppUi> {
    val displayed =
        session
            .distinctUntilChanged(::sameDisplayedCheckpoint)
            .stateIn(this, SharingStarted.Eagerly, session.value)
    return launchMolecule(mode = RecompositionMode.Immediate) { Presenter(library, displayed) }
}

private fun sameDisplayedCheckpoint(before: Checkpoint?, after: Checkpoint?): Boolean {
    if (before == null || after == null) return before == after
    if (before.phase != Phase.RUNNING || after.phase != Phase.RUNNING) return before == after
    fun remaining(state: Checkpoint) =
        minOf(state.remainingMs, state.options.limitMs?.minus(state.activeMs) ?: Long.MAX_VALUE)
    return before.copy(activeMs = after.activeMs, remainingMs = after.remainingMs) == after &&
        formatTime(before.activeMs) == formatTime(after.activeMs) &&
        formatTime(remaining(before)) == formatTime(remaining(after)) &&
        (remaining(before) < before.remainingMs) == (remaining(after) < after.remainingMs)
}

@Composable
private fun Presenter(library: StateFlow<Library>, sessions: StateFlow<Checkpoint?>): AppUi {
    val saved by library.collectAsState()
    val session by sessions.collectAsState()
    val current = session
    val steps = remember(current?.routine) { current?.routine?.expandedSteps() }
    val label = current?.let { steps?.get(it.index)?.name } ?: "Ready"
    val status =
        when (current?.phase) {
            Phase.RUNNING -> "Active"
            Phase.PAUSED -> "Paused"
            Phase.WAITING -> "Ready for the next step"
            Phase.COMPLETED -> "Complete"
            Phase.STOPPED -> "Stopped"
            null -> "Choose a routine"
        }
    val remaining =
        current?.let {
            minOf(it.remainingMs, it.options.limitMs?.minus(it.activeMs) ?: Long.MAX_VALUE)
        } ?: 0
    val next =
        current?.let { state ->
            if (state.phase in listOf(Phase.COMPLETED, Phase.STOPPED)) null
            else if (state.index + 1 < (steps?.size ?: 0)) steps?.get(state.index + 1)
            else if (state.routine.repeat) steps?.first() else null
        }
    return AppUi(
        saved,
        TimerUi(current, label, formatTime(remaining), status, next, steps?.size ?: 0),
    )
}

fun formatTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + 999) / 1000
    val minute = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    val secondPart = (seconds % 60).toString().padStart(2, '0')
    val minutePart = (seconds / 60 % 60).toString().padStart(2, '0')
    return if (seconds < 3600) minute else "${seconds / 3600}:$minutePart:$secondPart"
}
