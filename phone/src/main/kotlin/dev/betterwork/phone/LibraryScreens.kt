package dev.betterwork.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.betterwork.domain.*
import dev.betterwork.platform.BetterApp
import dev.betterwork.presentation.formatTime
import java.util.UUID

internal fun routineSummary(routine: Routine): String {
    val duration =
        routine.limitMs?.let { "${formatTime(it)} limit" }
            ?: if (routine.repeat) "Until stopped"
            else formatTime(routine.expandedSteps().sumOf { it.durationMs })
    return "$duration · ${if (routine.transition == Transition.CONFIRM) "Confirm transitions" else "Automatic"}"
}

@Composable
internal fun RoutineLibrary(
    routines: List<Routine>,
    details: (Routine) -> Unit,
    start: (Routine) -> Unit,
    importLibrary: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp)
    ) {
        item {
            Text(
                "Your pace, your intervals.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
        items(routines, key = { it.id }) { routine ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Column(
                    Modifier.weight(1f)
                        .clickable { details(routine) }
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        routineSummary(routine),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        routine.blocks
                            .flatMap { it.steps }
                            .map { it.name }
                            .distinct()
                            .take(3)
                            .joinToString(" / "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = { start(routine) },
                    modifier = Modifier.semantics { contentDescription = "Start ${routine.name}" },
                ) {
                    Text("Start")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (routines.isEmpty())
            item {
                Column(
                    Modifier.padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Make room for your routine",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("Create a routine with the New routine button, or import a saved library.")
                    OutlinedButton(onClick = importLibrary) { Text("Import routines") }
                }
            }
    }
}

@Composable
internal fun RoutineDetails(
    routine: Routine,
    app: BetterApp,
    start: (Routine) -> Unit,
    edit: (String) -> Unit,
    back: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    ScrollContent {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                routineSummary(routine),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { edit(routine.id) }) { Text("Edit") }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "Routine actions")
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = {
                            menu = false
                            val id = UUID.randomUUID().toString()
                            val copy = routine.copy(id = id, name = routine.name.take(75) + " copy")
                            app.repository.saveDraft(copy.toDraft(source = null))
                            edit(id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menu = false
                            deleting = true
                        },
                    )
                }
            }
        }
        Button(
            onClick = { start(routine) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text("Start")
        }
        Text(
            "Sequence",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        routine.blocks.forEach { block ->
            if (block.repetitions > 1)
                Text(
                    "Repeat ${block.repetitions} times",
                    style = MaterialTheme.typography.titleSmall,
                )
            block.steps.forEach { step ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(step.name, Modifier.weight(1f))
                    Text(
                        formatTime(step.durationMs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Text(
            if (routine.repeat)
                "Repeats ${routine.limitMs?.let { "until the active-time limit" } ?: "until stopped"}."
            else "Runs once.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (deleting)
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete ${routine.name}?") },
            text = {
                Text("This removes the saved routine. Any active session continues unchanged.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        app.repository.delete(routine.id)
                        back()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
}
