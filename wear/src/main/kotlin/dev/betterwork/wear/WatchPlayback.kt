package dev.betterwork.wear

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.betterwork.domain.*
import dev.betterwork.platform.BetterApp
import dev.betterwork.presentation.*

@Composable
internal fun WatchPlayback(
    route: WatchRoute,
    timer: TimerUi,
    app: BetterApp,
    navigate: (WatchRoute) -> Unit,
) {
    val state = timer.session
    val handle = app.sessionHandle
    if (state == null || timer.terminal) {
        WatchList("Session ended") {
            item { WatchAction("View summary") { navigate(WatchRoute("timer")) } }
        }
        return
    }
    when (route.screen) {
        "controls" -> WatchControls(timer, app, navigate)
        "progress" -> WatchProgress(timer)
        "session-cues" ->
            WatchCues(
                state.options,
                state.phase != Phase.RUNNING,
                state.options.cueOverride ?: state.routine.expandedSteps()[state.index].cue,
                { app.updateCues(it, handle) },
                app::preview,
            ) {
                navigate(WatchRoute("session-$it"))
            }
        "session-step" ->
            WatchCueChoice(state.options.cueOverride, true) {
                app.updateCues(state.options.copy(cueOverride = it), handle)
            }
        "session-completion" ->
            WatchCueChoice(state.options.completionCue, false) {
                app.updateCues(state.options.copy(completionCue = requireNotNull(it)), handle)
            }
    }
}

@Composable
private fun WatchControls(timer: TimerUi, app: BetterApp, navigate: (WatchRoute) -> Unit) {
    val state = timer.session ?: return
    val recovered by app.recovered.collectAsState()
    val handle = app.sessionHandle
    var stopping by rememberSaveable { mutableStateOf<String?>(null) }
    WatchList("Controls") {
        if (recovered)
            item {
                WatchText(
                    "Session interrupted. Resume or continue when ready. Time away was not counted."
                )
            }
        if (state.phase == Phase.WAITING)
            item {
                WatchText(
                    timer.nextStep?.let { "Next: ${it.name} · ${formatTime(it.durationMs)}" }
                        ?: "Ready to finish"
                )
            }
        else item { WatchAction("Skip step") { app.command(SessionCommand.SKIP, handle) } }
        item { WatchAction("Progress") { navigate(WatchRoute("progress")) } }
        item { WatchAction("Session cues") { navigate(WatchRoute("session-cues")) } }
        item { WatchAction("Stop session") { stopping = handle } }
    }
    stopping?.let { target ->
        WatchStopDialog({ stopping = null }) {
            app.command(SessionCommand.STOP, target)
            stopping = null
            navigate(WatchRoute("timer"))
        }
    }
}

@Composable
private fun WatchProgress(timer: TimerUi) {
    val state = timer.session ?: return
    WatchList("Progress") {
        item { WatchText(state.routine.name) }
        item { WatchText("${formatTime(state.activeMs)} active") }
        state.options.limitMs?.let { limit ->
            item { WatchText("${formatTime((limit - state.activeMs).coerceAtLeast(0))} to finish") }
        }
        item { WatchText("Cycle ${state.cycle}") }
        item { WatchText("Step ${state.index + 1} of ${timer.stepCount}") }
        if (timer.endsDuringStep) item { WatchText("Session ends after this interval") }
        else
            timer.nextStep?.let { next ->
                item { WatchText("Next: ${next.name} · ${formatTime(next.durationMs)}") }
            }
    }
}
