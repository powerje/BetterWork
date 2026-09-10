package dev.betterwork.phone

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.betterwork.data.*
import dev.betterwork.domain.Cue
import dev.betterwork.presentation.formatTime

@Composable
internal fun SequenceEditor(state: EditorState, openGroup: (String) -> Unit) {
    val draft = state.draft
    var adding by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(state.error) {
        if (state.error != null && (draft.name.isBlank() || draft.name.length > 80))
            nameFocus.requestFocus()
    }
    ScrollContent {
        Text(
            if (state.dirty) "Unsaved changes" else "Arrange your intervals",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            draft.name,
            { state.update(draft.copy(name = it)) },
            label = { Text("Routine name") },
            singleLine = true,
            isError = draft.name.length > 80 || state.error != null && draft.name.isBlank(),
            supportingText = {
                if (draft.name.length > 80 || state.error != null && draft.name.isBlank())
                    Text("Use 1–80 characters")
            },
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
        )
        Text("Sequence", style = MaterialTheme.typography.titleLarge)
        draft.blocks.forEachIndexed { index, block ->
            key(block.id) {
                val single = block.steps.size == 1 && block.repetitions == "1"
                SequenceItem(
                    if (single) block.steps.first().name
                    else if (block.repetitions == "1") "Run once"
                    else "Repeat ${block.repetitions} times",
                    if (single) stepSummary(block.steps.first())
                    else block.steps.joinToString(" · ") { it.name },
                    index,
                    draft.blocks.size,
                    open = {
                        if (single)
                            state.focus(DraftFocus("step", block.id, block.steps.first().id, block))
                        else openGroup(block.id)
                    },
                    move = { state.update(draft.copy(blocks = draft.blocks.moved(index, it))) },
                    remove = {
                        state.remove(
                            draft.copy(blocks = draft.blocks.filterNot { it.id == block.id })
                        )
                    },
                )
            }
        }
        OutlinedButton(onClick = { adding = true }, enabled = draft.blocks.size < 100) {
            Text("Add to sequence")
        }
        SettingsRow(
            "Session rules",
            (if (draft.repeat) "Repeat routine" else "Run once") +
                " · " +
                if (draft.confirm) "Confirm transitions" else "Automatic",
        ) {
            state.focus(
                DraftFocus(
                    "rules",
                    repeat = draft.repeat,
                    confirm = draft.confirm,
                    limitSeconds = draft.limitSeconds,
                    limitDuration = draft.limitDuration,
                )
            )
        }
        EditorFeedback(state)
    }
    if (adding)
        AddSequenceDialog({ adding = false }) { name ->
            val block = newBlock(name)
            state.update(draft.copy(blocks = draft.blocks + block))
            adding = false
            if (name == "Repeat group") openGroup(block.id)
            else state.focus(DraftFocus("step", block.id, block.steps.first().id, block))
        }
}

@Composable
private fun AddSequenceDialog(dismiss: () -> Unit, add: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add to sequence") },
        text = {
            Column {
                listOf("Step", "Repeat group", "Warmup", "Cooldown").forEach { name ->
                    TextButton(onClick = { add(name) }, modifier = Modifier.fillMaxWidth()) {
                        Text(name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

private fun newBlock(type: String) =
    DraftBlock(
        localId(),
        if (type == "Repeat group") "8" else "1",
        if (type == "Repeat group")
            listOf(DraftStep(localId(), "Work", "20"), DraftStep(localId(), "Rest", "10", Cue.REST))
        else
            listOf(
                DraftStep(
                    localId(),
                    if (type == "Step") "Work" else type,
                    "60",
                    if (type == "Cooldown") Cue.REST else Cue.WORK,
                )
            ),
    )

@Composable
internal fun GroupEditor(state: EditorState, id: String, close: () -> Unit) {
    val draft = state.draft
    val block = draft.blocks.firstOrNull { it.id == id }
    if (block == null) {
        LaunchedEffect(id) { close() }
        return
    }
    ScrollContent {
        SettingsRow(
            "Repeat count",
            if (block.repetitions == "1") "Once" else "${block.repetitions} times",
        ) {
            state.focus(DraftFocus("count", id, originalBlock = block))
        }
        Text("Steps in each repeat", style = MaterialTheme.typography.titleLarge)
        block.steps.forEachIndexed { index, step ->
            key(step.id) {
                SequenceItem(
                    step.name,
                    stepSummary(step),
                    index,
                    block.steps.size,
                    open = { state.focus(DraftFocus("step", id, step.id, block)) },
                    move = {
                        state.update(
                            draft.replaceBlock(block.copy(steps = block.steps.moved(index, it)))
                        )
                    },
                    remove = {
                        state.remove(
                            draft.replaceBlock(
                                block.copy(steps = block.steps.filterNot { it.id == step.id })
                            )
                        )
                    },
                )
            }
        }
        if (block.steps.isEmpty())
            Text("Add at least one step to this group.", color = MaterialTheme.colorScheme.error)
        OutlinedButton(
            onClick = {
                val step = DraftStep(localId())
                val updated = block.copy(steps = block.steps + step)
                state.update(draft.replaceBlock(updated))
                state.focus(DraftFocus("step", id, step.id, updated))
            },
            enabled = block.steps.size < 100,
        ) {
            Text("Add step")
        }
        EditorFeedback(state)
    }
}

@Composable
private fun SequenceItem(
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    open: () -> Unit,
    move: (Int) -> Unit,
    remove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier.semantics {
                customActions = buildList {
                    if (index > 0)
                        add(
                            CustomAccessibilityAction("Move $title up") {
                                move(-1)
                                true
                            }
                        )
                    if (index < count - 1)
                        add(
                            CustomAccessibilityAction("Move $title down") {
                                move(1)
                                true
                            }
                        )
                    add(
                        CustomAccessibilityAction("Remove $title") {
                            remove()
                            true
                        }
                    )
                }
            },
    ) {
        Row(Modifier.padding(horizontal = 16.dp)) {
            Box(Modifier.weight(1f)) {
                SettingsRow(title.ifBlank { "Unnamed step" }, subtitle, open)
            }
            Box {
                TextButton(onClick = { menu = true }) { Text("More") }
                DropdownMenu(menu, { menu = false }) {
                    if (index > 0)
                        DropdownMenuItem(
                            text = { Text("Move up") },
                            onClick = {
                                menu = false
                                move(-1)
                            },
                        )
                    if (index < count - 1)
                        DropdownMenuItem(
                            text = { Text("Move down") },
                            onClick = {
                                menu = false
                                move(1)
                            },
                        )
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = {
                            menu = false
                            remove()
                        },
                    )
                }
            }
        }
    }
}

internal fun stepSummary(step: DraftStep) =
    runCatching {
            val ms = step.durationMs()
            formatTime(ms) + if (ms % 1000 != 0L) " · ${ms % 1000} ms precision" else ""
        }
        .getOrDefault("Check duration")

@Composable
internal fun EditorFeedback(state: EditorState) {
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (state.undo != null)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Removed from draft", modifier = Modifier.weight(1f))
            TextButton(onClick = state::restoreRemoval) { Text("Undo") }
        }
}
