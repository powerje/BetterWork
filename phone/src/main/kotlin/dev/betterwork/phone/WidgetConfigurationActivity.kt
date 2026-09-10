package dev.betterwork.phone

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.betterwork.platform.BetterApp
import kotlinx.coroutines.launch

class WidgetConfigurationActivity : ComponentActivity() {
    private val app
        get() = application as BetterApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if (info?.provider != ComponentName(this, RoutineWidgetReceiver::class.java)) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent { ConfigurationScreen(id) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ConfigurationScreen(id: Int) {
        val library by app.repository.library.collectAsState()
        val preferences by app.repository.appPreferences.collectAsState()
        var selected by
            rememberSaveable(id) { mutableStateOf(WidgetBindings(app).read(id).routineId) }
        var error by remember { mutableStateOf<String?>(null) }
        PhoneTheme(preferences) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Widget routine") },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel")
                            }
                        },
                        actions = {
                            TextButton(
                                enabled = library.routines.any { it.id == selected },
                                onClick = {
                                    try {
                                        save(id, requireNotNull(selected))
                                    } catch (invalid: IllegalStateException) {
                                        error = invalid.message
                                    }
                                },
                            ) {
                                Text("Save")
                            }
                        },
                    )
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    ScrollContent {
                        Text(
                            "Choose the routine this widget starts. It opens your active timer if one already exists."
                        )
                        library.routines.forEach { routine ->
                            key(routine.id) {
                                ChoiceRow(
                                    routine.name,
                                    selected == routine.id,
                                    widgetSummary(routine),
                                ) {
                                    selected = routine.id
                                }
                            }
                        }
                        if (library.routines.isEmpty()) {
                            Text("Create or import a routine in BetterWork, then return here.")
                            Button(
                                onClick = {
                                    startActivity(
                                        Intent(
                                            this@WidgetConfigurationActivity,
                                            MainActivity::class.java,
                                        )
                                    )
                                }
                            ) {
                                Text("Open BetterWork")
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    private fun save(id: Int, routineId: String) {
        check(app.repository.library.value.routines.any { it.id == routineId }) {
            "This routine was removed. Choose another."
        }
        WidgetBindings(app).bind(id, routineId)
        app.scope.launch { WidgetUpdates.refresh(app) }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
