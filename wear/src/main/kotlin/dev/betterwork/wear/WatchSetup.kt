package dev.betterwork.wear

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.*
import dev.betterwork.domain.*
import dev.betterwork.platform.BetterApp
import dev.betterwork.presentation.formatTime

@Composable
internal fun WatchSetup(
    route: WatchRoute,
    routine: Routine,
    options: SessionOptions,
    app: BetterApp,
    update: (SessionOptions) -> Unit,
    navigate: (WatchRoute) -> Unit,
    start: () -> Unit,
) {
    val session by app.sessions.collectAsState()
    when (route.screen) {
        "setup" ->
            WatchList(routine.name) {
                item {
                    WatchText(
                        options.limitMs?.let { "${formatTime(it)} limit" }
                            ?: if (routine.repeat) "Until stopped" else "Run once"
                    )
                }
                item { WatchAction("Start", primary = true, action = start) }
                item {
                    WatchAction("Adjust this session") {
                        navigate(WatchRoute("adjust", routine.id))
                    }
                }
            }
        "adjust" ->
            WatchList("This session only") {
                item {
                    WatchAction("Active time limit") { navigate(WatchRoute("limit", routine.id)) }
                }
                item {
                    WatchAction("Cues & sound") { navigate(WatchRoute("setup-cues", routine.id)) }
                }
            }
        "limit" -> WatchLimit(options, update)
        "setup-cues" ->
            WatchCues(
                options,
                session?.phase != Phase.RUNNING,
                options.cueOverride ?: routine.expandedSteps().first().cue,
                update,
                app::preview,
            ) {
                navigate(WatchRoute("setup-$it", routine.id))
            }
        "setup-step" ->
            WatchCueChoice(options.cueOverride, true) { update(options.copy(cueOverride = it)) }
        "setup-completion" ->
            WatchCueChoice(options.completionCue, false) {
                update(options.copy(completionCue = requireNotNull(it)))
            }
    }
}

@Composable
private fun WatchLimit(options: SessionOptions, update: (SessionOptions) -> Unit) {
    val limit = options.limitMs ?: 0L
    WatchList("Active time limit") {
        item { WatchText("This session only") }
        item { WatchText(if (limit == 0L) "No limit" else formatTime(limit)) }
        item {
            RadioButton(
                limit == 0L,
                { update(options.copy(limitMs = null)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("No limit")
            }
        }
        item {
            RadioButton(
                limit == 1_800_000L,
                { update(options.copy(limitMs = 1_800_000)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("30 minutes")
            }
        }
        item {
            RadioButton(
                limit == 3_600_000L,
                { update(options.copy(limitMs = 3_600_000)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("60 minutes")
            }
        }
        item {
            WatchAction("+5 minutes", enabled = limit < MAX_DURATION_MS) {
                update(options.copy(limitMs = (limit + 300_000).coerceAtMost(MAX_DURATION_MS)))
            }
        }
        item {
            WatchAction("−5 minutes", enabled = limit > 0) {
                update(options.copy(limitMs = (limit - 300_000).takeIf { it > 0 }))
            }
        }
    }
}

@Composable
internal fun WatchCues(
    options: SessionOptions,
    previewEnabled: Boolean,
    stepCue: Cue,
    update: (SessionOptions) -> Unit,
    preview: (Cue) -> Unit,
    choose: (String) -> Unit,
) {
    WatchList("Session cues") {
        item { WatchText("This session only") }
        item {
            WatchAction("Step: ${options.cueOverride?.displayName() ?: "Per step"}") {
                choose("step")
            }
        }
        item {
            WatchAction("Finish: ${options.completionCue.displayName()}") { choose("completion") }
        }
        item {
            SwitchButton(
                options.sound,
                { update(options.copy(sound = it)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sound")
            }
        }
        if (!previewEnabled) item { WatchText("Pause the timer to preview.") }
        item { WatchAction("Preview step cue", enabled = previewEnabled) { preview(stepCue) } }
        item {
            WatchAction("Preview finish cue", enabled = previewEnabled) {
                preview(options.completionCue)
            }
        }
    }
}

@Composable
internal fun WatchCueChoice(selected: Cue?, perStep: Boolean, update: (Cue?) -> Unit) {
    WatchList(if (perStep) "Step vibration" else "Finish vibration") {
        if (perStep)
            item {
                RadioButton(
                    selected == null,
                    { update(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Per step")
                }
            }
        items(Cue.entries.size) { index ->
            val cue = Cue.entries[index]
            RadioButton(selected == cue, { update(cue) }, modifier = Modifier.fillMaxWidth()) {
                Text(cue.displayName())
            }
        }
    }
}

internal fun Cue.displayName() = name.lowercase().replaceFirstChar { it.uppercase() }
