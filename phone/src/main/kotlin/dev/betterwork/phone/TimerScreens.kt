package dev.betterwork.phone

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.betterwork.domain.*
import dev.betterwork.platform.BetterApp
import dev.betterwork.presentation.*

@Composable
private fun CountdownText(value: String, description: String) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = with(density) { maxWidth.toPx() }
        val styles =
            listOf(
                CountdownStyle,
                typography.displayLarge,
                typography.displayMedium,
                typography.displaySmall,
                typography.headlineMedium,
                typography.titleLarge,
            )
        val style =
            styles.firstOrNull {
                measurer.measure(AnnotatedString(value), style = it, softWrap = false).size.width <=
                    width
            } ?: styles.last()
        Text(
            value,
            style = style.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = "$value $description" },
        )
    }
}

@Composable
internal fun PhoneTimer(
    timer: TimerUi,
    app: BetterApp,
    navigate: (PhoneRoute) -> Unit,
    done: () -> Unit,
) {
    val state = timer.session ?: return
    val recovered by app.recovered.collectAsState()
    val handle = app.sessionHandle
    var stopHandle by rememberSaveable { mutableStateOf<String?>(null) }
    ScrollContent {
        if (recovered) RecoveryNotice()
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (timer.terminal) {
                Text(timer.status, style = MaterialTheme.typography.headlineMedium)
                CountdownText(formatTime(state.activeMs), "active time")
                Text("Active time", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("This summary is not saved.", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        app.dismiss()
                        done()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Done")
                }
            } else {
                if (state.phase != Phase.RUNNING)
                    Text(
                        if (state.phase == Phase.WAITING) "Step complete" else "Paused",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                Text(
                    timer.label,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                CountdownText(timer.time, "remaining")
                Button(
                    onClick = { app.command(timer.primaryCommand, handle) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(timer.primaryLabel)
                }
                if (timer.endsDuringStep)
                    Text(
                        "Session ends after this interval",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else
                    timer.nextStep?.let {
                        Text(
                            "Next: ${it.name} · ${formatTime(it.durationMs)}",
                            textAlign = TextAlign.Center,
                        )
                    }
            }
        }
        if (!timer.terminal) {
            Text(
                "${formatTime(state.activeMs)} active" +
                    (state.options.limitMs?.let { " / ${formatTime(it)}" } ?: ""),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsRow(
                "Progress",
                "Cycle ${state.cycle} · Step ${state.index + 1} of ${timer.stepCount}",
            ) {
                navigate(PhoneRoute("progress"))
            }
            SettingsRow("Session cues", "Changes apply only to this session") {
                navigate(PhoneRoute("session-cues"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.phase != Phase.WAITING)
                    OutlinedButton(onClick = { app.command(SessionCommand.SKIP, handle) }) {
                        Text("Skip step")
                    }
                TextButton(onClick = { stopHandle = handle }) { Text("Stop session") }
            }
        }
    }
    stopHandle?.let { target ->
        StopSessionDialog({ stopHandle = null }) {
            app.command(SessionCommand.STOP, target)
            stopHandle = null
        }
    }
}

@Composable
private fun RecoveryNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Session interrupted", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your saved progress is ready. Resume or continue when you are ready; time away was not counted."
            )
        }
    }
}

@Composable
internal fun SessionProgress(timer: TimerUi) {
    val state = timer.session ?: return
    ScrollContent {
        Text(state.routine.name, style = MaterialTheme.typography.titleLarge)
        Text(timer.status)
        Text(
            "${formatTime(state.activeMs)} active",
            style = MaterialTheme.typography.headlineMedium,
        )
        state.options.limitMs?.let { limit ->
            Text("${formatTime((limit - state.activeMs).coerceAtLeast(0))} until session ends")
            LinearProgressIndicator(
                progress = { (state.activeMs.toFloat() / limit).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("Cycle ${state.cycle} · Step ${state.index + 1} of ${timer.stepCount}")
        Text("Current: ${timer.label}")
        timer.nextStep?.let { Text("Next: ${it.name} · ${formatTime(it.durationMs)}") }
    }
}

@Composable
internal fun SessionCueSettings(timer: TimerUi, app: BetterApp) {
    val state = timer.session ?: return
    val handle = app.sessionHandle
    val options = state.options
    var choosing by rememberSaveable { mutableStateOf<String?>(null) }
    ScrollContent {
        Text("For this session only. Your saved routine and defaults stay unchanged.")
        SettingsRow("Step vibration", options.cueOverride?.displayName() ?: "Per step") {
            choosing = "step"
        }
        SettingsRow("Completion vibration", options.completionCue.displayName()) {
            choosing = "completion"
        }
        LabeledSwitch("Sound", options.sound) { app.updateCues(options.copy(sound = it), handle) }
        if (state.phase == Phase.RUNNING)
            Text(
                "Pause the timer to preview vibration patterns.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        OutlinedButton(
            enabled = state.phase != Phase.RUNNING,
            onClick = {
                app.preview(options.cueOverride ?: state.routine.expandedSteps()[state.index].cue)
            },
        ) {
            Text("Preview step cue")
        }
        OutlinedButton(
            enabled = state.phase != Phase.RUNNING,
            onClick = { app.preview(options.completionCue) },
        ) {
            Text("Preview completion cue")
        }
    }
    choosing?.let { type ->
        CueChoiceDialog(type == "step", options, { choosing = null }) { cue ->
            val updated =
                if (type == "step") options.copy(cueOverride = cue)
                else options.copy(completionCue = requireNotNull(cue))
            app.updateCues(updated, handle)
            choosing = null
        }
    }
}

internal fun Cue.displayName() = name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun CueChoiceDialog(
    step: Boolean,
    options: SessionOptions,
    dismiss: () -> Unit,
    choose: (Cue?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (step) "Step vibration" else "Completion vibration") },
        text = {
            Column {
                if (step) ChoiceRow("Per step", options.cueOverride == null) { choose(null) }
                Cue.entries.forEach { cue ->
                    ChoiceRow(
                        cue.displayName(),
                        cue == if (step) options.cueOverride else options.completionCue,
                    ) {
                        choose(cue)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StopSessionDialog(dismiss: () -> Unit, stop: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Stop this session?") },
        text = {
            Text("Your routine stays in the library. The timer continues until you stop it.")
        },
        confirmButton = { TextButton(onClick = stop) { Text("Stop") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Keep going") } },
    )
}
