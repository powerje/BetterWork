package dev.betterwork.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.*

@Composable
internal fun WatchReplaceDialog(
    dismiss: () -> Unit,
    returnToTimer: () -> Unit,
    replace: () -> Unit,
) {
    AlertDialog(
        visible = true,
        onDismissRequest = dismiss,
        title = { Text("A timer is active") },
        text = { Text("End it before starting this routine?") },
        content = {
            item {
                Button(onClick = replace, modifier = Modifier.fillMaxWidth()) {
                    Text("End and start new")
                }
            }
            item {
                FilledTonalButton(onClick = returnToTimer, modifier = Modifier.fillMaxWidth()) {
                    Text("Return to timer")
                }
            }
            item {
                ChildButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
internal fun WatchStopDialog(dismiss: () -> Unit, stop: () -> Unit) {
    AlertDialog(
        visible = true,
        onDismissRequest = dismiss,
        title = { Text("Stop session?") },
        text = { Text("Your routine stays in the library.") },
        content = {
            item { Button(onClick = stop, modifier = Modifier.fillMaxWidth()) { Text("Stop") } }
            item {
                FilledTonalButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Keep going")
                }
            }
        },
    )
}
