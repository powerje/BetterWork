package dev.betterwork.phone

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.betterwork.data.Appearance
import dev.betterwork.platform.BetterApp
import dev.betterwork.platform.LibrarySync
import kotlinx.coroutines.launch

@Composable
internal fun PhoneSettings(
    screen: String,
    app: BetterApp,
    navigate: (PhoneRoute) -> Unit,
    importLibrary: () -> Unit,
    exportLibrary: () -> Unit,
) {
    val preferences by app.repository.appPreferences.collectAsState()
    val sync by app.syncStatus.collectAsState()
    ScrollContent {
        when (screen) {
            "appearance" -> {
                Appearance.entries.forEach { mode ->
                    val label = mode.name.lowercase().replaceFirstChar { it.uppercase() }
                    ChoiceRow(label, preferences.appearance == mode) {
                        app.repository.appPreferences(preferences.copy(appearance = mode))
                    }
                }
                if (Build.VERSION.SDK_INT >= 31)
                    LabeledSwitch("Use wallpaper colors", preferences.wallpaperColors) {
                        app.repository.appPreferences(preferences.copy(wallpaperColors = it))
                    }
                Text(
                    "System follows your device’s light or dark appearance.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            "defaults" -> {
                Text(
                    "Used for new sessions. Step vibration patterns are set in each routine.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LabeledSwitch("Sound", preferences.sound) {
                    app.repository.appPreferences(preferences.copy(sound = it))
                }
                CuePicker(
                    "Completion",
                    preferences.completionCue,
                    { app.repository.appPreferences(preferences.copy(completionCue = it)) },
                    app::preview,
                )
            }
            "files" -> {
                Text("Keep a copy of your routines or bring in a saved library.")
                SettingsRow(
                    "Import routines",
                    "Adds new copies without replacing your library",
                    importLibrary,
                )
                SettingsRow("Export library", "Save all routines as a JSON file", exportLibrary)
            }
            "sync" -> {
                Text(sync, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your phone sends its library to your watch when available. " +
                        "Each device runs its own timer, even offline."
                )
                OutlinedButton(onClick = { app.scope.launch { LibrarySync.publish(app) } }) {
                    Text("Retry sync")
                }
                Text(
                    "Queued means the update was submitted, not that your watch has received it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                SettingsRow("Appearance", "System, light, dark and wallpaper colors") {
                    navigate(PhoneRoute("appearance"))
                }
                SettingsRow("Default cues", "Sound and completion vibration") {
                    navigate(PhoneRoute("defaults"))
                }
                SettingsRow("Import & export", "Back up or add routines") {
                    navigate(PhoneRoute("files"))
                }
                SettingsRow("Watch sync", "Transfer your library") { navigate(PhoneRoute("sync")) }
                Text(
                    "BetterWork · Offline intervals",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ChoiceRow(
    label: String,
    selected: Boolean,
    supportingText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected, onClick = null)
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(label)
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
