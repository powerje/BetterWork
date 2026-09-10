package dev.betterwork.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.betterwork.data.*
import dev.betterwork.domain.Phase
import dev.betterwork.presentation.parseDurationSeconds

@Composable
internal fun FocusedEditor(state: EditorState) {
    val session by state.app.sessions.collectAsState()
    val draft = state.draft
    val focus = draft.focus ?: return
    val block = draft.blocks.firstOrNull { it.id == focus.blockId }
    ScrollContent {
        when (focus.screen) {
            "step" ->
                block
                    ?.steps
                    ?.firstOrNull { it.id == focus.stepId }
                    ?.let { step ->
                        StepForm(
                            step,
                            state.error != null,
                            session?.phase != Phase.RUNNING,
                            change = { value ->
                                state.update(
                                    draft.replaceBlock(
                                        block.copy(
                                            steps =
                                                block.steps.map {
                                                    if (it.id == value.id) value else it
                                                }
                                        )
                                    )
                                )
                            },
                            preview = state.app::preview,
                        )
                    }
            "count" ->
                block?.let {
                    val requester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { requester.requestFocus() }
                    OutlinedTextField(
                        block.repetitions,
                        { state.update(draft.replaceBlock(block.copy(repetitions = it))) },
                        label = { Text("Repeat count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = block.repetitions.toIntOrNull() !in 1..100,
                        supportingText = { Text("Repeat this group 1–100 times") },
                        modifier = Modifier.fillMaxWidth().focusRequester(requester),
                    )
                }
            "rules" -> RulesForm(draft) { state.update(it) }
        }
        EditorFeedback(state)
    }
}

@Composable
private fun StepForm(
    step: DraftStep,
    showError: Boolean,
    previewEnabled: Boolean,
    change: (DraftStep) -> Unit,
    preview: (dev.betterwork.domain.Cue) -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    OutlinedTextField(
        step.name,
        { change(step.copy(name = it)) },
        label = { Text("Step name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(requester),
        isError = step.name.length > 80 || showError && step.name.isBlank(),
        supportingText = {
            if (step.name.length > 80 || showError && step.name.isBlank())
                Text("Use 1–80 characters")
        },
    )
    Text("Duration", style = MaterialTheme.typography.titleMedium)
    DurationForm(
        step.duration
            ?: runCatching { durationFields(parseDurationSeconds(step.seconds)) }
                .getOrDefault(DraftDuration(seconds = step.seconds, minutes = "0"))
    ) {
        change(step.copy(duration = it))
    }
    CuePicker(
        "Step vibration",
        step.cue,
        { change(step.copy(cue = it)) },
        preview,
        previewEnabled = previewEnabled,
    )
    if (!previewEnabled) Text("Pause the timer to preview vibration patterns.")
}

@Composable
private fun RulesForm(draft: RoutineDraft, change: (RoutineDraft) -> Unit) {
    LabeledSwitch("Repeat routine", draft.repeat) { change(draft.copy(repeat = it)) }
    LabeledSwitch("Confirm before next step", draft.confirm) { change(draft.copy(confirm = it)) }
    Text(
        if (draft.confirm) "The timer waits for Continue between steps."
        else "Steps advance automatically."
    )
    val limited = draft.limitDuration != null || draft.limitSeconds.isNotBlank()
    LabeledSwitch("Active time limit", limited) {
        change(
            draft.copy(
                limitSeconds = "",
                limitDuration = if (it) DraftDuration(minutes = "30") else null,
            )
        )
    }
    if (limited)
        DurationForm(
            draft.limitDuration
                ?: runCatching { durationFields(parseDurationSeconds(draft.limitSeconds)) }
                    .getOrDefault(DraftDuration(minutes = "0", seconds = draft.limitSeconds))
        ) {
            change(draft.copy(limitSeconds = "", limitDuration = it))
        }
    Text("Paused time and confirmation waits do not count toward the limit.")
}

@Composable
internal fun DurationForm(value: DraftDuration, change: (DraftDuration) -> Unit) {
    var advanced by rememberSaveable {
        mutableStateOf(value.hours != "0" || value.milliseconds != "0")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DurationField("Minutes", value.minutes, 59, Modifier.weight(1f)) {
            change(value.copy(minutes = it))
        }
        DurationField("Seconds", value.seconds, 59, Modifier.weight(1f)) {
            change(value.copy(seconds = it))
        }
    }
    TextButton(onClick = { advanced = !advanced }) {
        Text(if (advanced) "Hide advanced duration" else "Hours & milliseconds")
    }
    if (advanced)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DurationField("Hours", value.hours, 168, Modifier.weight(1f)) {
                change(value.copy(hours = it))
            }
            DurationField("Milliseconds", value.milliseconds, 999, Modifier.weight(1f)) {
                change(value.copy(milliseconds = it))
            }
        }
    val error = runCatching { value.toMillis() }.exceptionOrNull()?.message
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun DurationField(
    label: String,
    value: String,
    max: Int,
    modifier: Modifier,
    change: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        change,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = value.toIntOrNull() !in 0..max,
        modifier = modifier,
        supportingText = { Text("0–$max") },
    )
}
