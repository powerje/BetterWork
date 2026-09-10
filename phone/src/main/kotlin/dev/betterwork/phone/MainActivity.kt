package dev.betterwork.phone

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import dev.betterwork.platform.BetterApp
import dev.betterwork.platform.readLibraryText
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val app
        get() = application as BetterApp

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted)
                app.message.value =
                    "Notifications are off. Timer controls remain available in BetterWork."
        }
    private val importFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                app.scope.launch {
                    try {
                        val text =
                            withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(uri).use { input ->
                                    requireNotNull(input) { "Cannot read this file" }
                                    input.readLibraryText()
                                }
                            }
                        app.repository.importCopies(text)
                        app.message.value = "Routines imported as new copies."
                    } catch (error: java.io.IOException) {
                        app.message.value = "Import failed: ${error.message}"
                    } catch (error: SecurityException) {
                        app.message.value =
                            "Import failed: file permission denied (${error.message})"
                    } catch (error: IllegalArgumentException) {
                        app.message.value = "Import failed: ${error.message}"
                    }
                }
        }
    private val exportFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri
            ->
            if (uri != null)
                app.scope.launch {
                    try {
                        val text = app.repository.export()
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(uri, "wt").use { output ->
                                requireNotNull(output) { "Cannot write this file" }
                                    .write(text.encodeToByteArray())
                            }
                        }
                        app.message.value = "Library exported."
                    } catch (error: java.io.IOException) {
                        app.message.value = "Export failed: ${error.message}"
                    } catch (error: SecurityException) {
                        app.message.value =
                            "Export failed: file permission denied (${error.message})"
                    } catch (error: IllegalArgumentException) {
                        app.message.value = "Export failed: ${error.message}"
                    }
                }
        }

    private val timerEntry = kotlinx.coroutines.flow.MutableStateFlow(0)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEntry(intent)
    }

    private fun handleEntry(intent: Intent) {
        if (intent.action == WIDGET_START) {
            val id =
                intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
            if (id <= 0) return
            val activation = activateWidget(app, id, intent.getStringExtra(WIDGET_TOKEN) ?: "")
            app.scope.launch { WidgetUpdates.refresh(app) }
            when (activation) {
                WidgetActivation.STARTED -> {
                    timerEntry.value++
                    requestNotifications()
                }
                WidgetActivation.OPEN_TIMER -> timerEntry.value++
                WidgetActivation.CONFIGURE -> startActivity(widgetConfigurationIntent(this, id))
                WidgetActivation.REFRESHED -> Unit
                WidgetActivation.FAILED -> Unit
            }
        } else if (intent.getBooleanExtra("openTimer", false)) timerEntry.value++
    }

    private fun requestNotifications() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleEntry(intent)
        setContent {
            val entry by timerEntry.collectAsState()
            PhoneApp(
                app = app,
                timerEntry = entry,
                requestNotifications = ::requestNotifications,
                importLibrary = { importFile.launch(arrayOf("application/json", "text/*")) },
                exportLibrary = { exportFile.launch("betterwork-library.json") },
            )
        }
    }
}
