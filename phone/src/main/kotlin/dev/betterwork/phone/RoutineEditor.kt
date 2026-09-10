package dev.betterwork.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.betterwork.platform.BetterApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineEditor(id: String, app: BetterApp, onClose: () -> Unit, openTimer: () -> Unit) {
    val session by app.sessions.collectAsState()
    val state = remember(id) { EditorState(app, id) }
    var group by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val draft = state.draft
    fun back() {
        when {
            draft.focus != null -> state.finishFocus()
            group != null -> group = null
            state.dirty -> state.dialog = "leave"
            else -> {
                state.discard()
                onClose()
            }
        }
    }
    BackHandler { back() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    when (draft.focus?.screen) {
                        "step" -> "Edit step"
                        "count" -> "Repeat count"
                        "rules" -> "Session rules"
                        else -> if (group == null) "Edit routine" else "Repeat group"
                    }
                )
            },
            navigationIcon = {
                IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            actions = {
                if (session != null) TextButton(onClick = openTimer) { Text("Timer") }
                TextButton(
                    onClick = {
                        if (draft.focus != null) state.finishFocus()
                        else if (group != null) group = null else if (state.save()) onClose()
                    }
                ) {
                    Text(if (draft.focus != null || group != null) "Done" else "Save")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        when {
            draft.focus != null -> FocusedEditor(state)
            group != null -> GroupEditor(state, group!!) { group = null }
            else -> SequenceEditor(state) { group = it }
        }
    }
    EditorDialog(state, onClose)
}

@Composable
private fun EditorDialog(state: EditorState, close: () -> Unit) {
    val dialog = state.dialog ?: return
    val invalid = dialog == "invalid"
    val conflict = dialog == "conflict"
    AlertDialog(
        onDismissRequest = { state.dialog = null },
        title = {
            Text(
                when {
                    invalid -> "Check this form"
                    conflict -> "Routine changed"
                    else -> "Save your changes?"
                }
            )
        },
        text = {
            Text(
                when {
                    invalid ->
                        state.error ?: "Correct the highlighted fields or discard these edits."
                    conflict ->
                        "The saved routine was changed or deleted. Save your draft as a new routine."
                    else -> "Your changes have not been saved to the library."
                }
            )
        },
        confirmButton = {
            if (!invalid)
                TextButton(
                    onClick = {
                        state.dialog = null
                        if (state.save(asNew = conflict)) close()
                    }
                ) {
                    Text(if (conflict) "Save as new" else "Save")
                }
        },
        dismissButton = {
            Column {
                TextButton(
                    onClick = {
                        state.dialog = null
                        if (invalid) state.update(state.draft.discardFocus())
                        else {
                            state.discard()
                            close()
                        }
                    }
                ) {
                    Text("Discard changes")
                }
                TextButton(onClick = { state.dialog = null }) { Text("Keep editing") }
            }
        },
    )
}
