package dev.betterwork.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.betterwork.domain.Cue

@Composable
fun CuePicker(
    label: String,
    selected: Cue,
    onSelect: (Cue) -> Unit,
    onPreview: (Cue) -> Unit,
    previewEnabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text("$label: ${selected.name.lowercase()}")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                Cue.entries.forEach { cue ->
                    DropdownMenuItem(
                        text = { Text(cue.name.lowercase()) },
                        onClick = {
                            onSelect(cue)
                            expanded = false
                        },
                    )
                }
            }
        }
        TextButton(enabled = previewEnabled, onClick = { onPreview(selected) }) { Text("Preview") }
    }
}

@Composable
internal fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Switch(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
    }
}
