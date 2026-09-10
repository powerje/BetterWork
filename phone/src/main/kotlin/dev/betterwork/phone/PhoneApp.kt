package dev.betterwork.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.betterwork.domain.*
import dev.betterwork.platform.BetterApp
import dev.betterwork.presentation.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PhoneApp(
    app: BetterApp,
    timerEntry: Int,
    requestNotifications: () -> Unit,
    importLibrary: () -> Unit,
    exportLibrary: () -> Unit,
) {
    val preferences by app.repository.appPreferences.collectAsState()
    val scope = rememberCoroutineScope()
    val presenter = remember { scope.present(app.repository.library, app.sessions) }
    val ui by presenter.collectAsState()
    val stack = rememberNavBackStack(PhoneRoute("library"))
    val route = stack.last() as PhoneRoute
    val snackbar = remember { SnackbarHostState() }
    val message by app.message.collectAsState()
    var replacingId by rememberSaveable { mutableStateOf<String?>(null) }
    var replacingHandle by rememberSaveable { mutableStateOf("") }
    fun navigate(target: PhoneRoute) {
        if (stack.last() != target) {
            if (target.screen == "timer") stack.removeAll { it == target }
            stack.add(target)
        }
    }
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    fun start(routine: Routine) {
        if (app.hasActiveSession) {
            replacingId = routine.id
            replacingHandle = app.sessionHandle
        } else {
            requestNotifications()
            if (app.start(routine, preferences.optionsFor(routine))) navigate(PhoneRoute("timer"))
        }
    }
    LaunchedEffect(timerEntry) { if (timerEntry > 0) navigate(PhoneRoute("timer")) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it, duration = SnackbarDuration.Long)
            if (app.message.value == it) app.message.value = null
        }
    }
    PhoneTheme(preferences) {
        val imeVisible = WindowInsets.isImeVisible
        Scaffold(
            topBar = {
                if (route.screen != "edit")
                    PhoneTopBar(
                        screenTitle(route, ui),
                        stack.size > 1,
                        route.screen == "library",
                        imeVisible && ui.timer.session != null && route.screen != "timer",
                        ::back,
                        ::navigate,
                    )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (route.screen == "library")
                    ExtendedFloatingActionButton(
                        onClick = { navigate(PhoneRoute("edit", UUID.randomUUID().toString())) },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("New routine") },
                    )
            },
            bottomBar = {
                if (!imeVisible && route.screen != "timer" && ui.timer.session != null)
                    ActiveSessionStrip(ui.timer, app) { navigate(PhoneRoute("timer")) }
            },
        ) { padding ->
            Box(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                NavDisplay(
                    backStack = stack,
                    onBack = ::back,
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
                    entryProvider = { key ->
                        NavEntry(key) {
                            PhoneDestination(
                                key as PhoneRoute,
                                ui,
                                app,
                                ::navigate,
                                ::back,
                                ::start,
                                importLibrary,
                                exportLibrary,
                            )
                        }
                    },
                )
            }
        }
        replacingId?.let { id ->
            ReplaceSessionDialog(
                onDismiss = { replacingId = null },
                onReturn = {
                    replacingId = null
                    navigate(PhoneRoute("timer"))
                },
                onReplace = {
                    ui.library.routines
                        .firstOrNull { it.id == id }
                        ?.let {
                            if (app.replace(it, preferences.optionsFor(it), replacingHandle))
                                navigate(PhoneRoute("timer"))
                        }
                    replacingId = null
                },
            )
        }
    }
}

private fun screenTitle(route: PhoneRoute, ui: AppUi): String =
    when (route.screen) {
        "library" -> "Routines"
        "details" -> ui.library.routines.firstOrNull { it.id == route.id }?.name ?: "Routine"
        "edit" -> "Edit routine"
        "timer" -> ui.timer.session?.routine?.name ?: "Timer"
        "appearance" -> "Appearance"
        "defaults" -> "Default cues"
        "progress" -> "Session progress"
        "session-cues" -> "Session cues"
        "files" -> "Import & export"
        "sync" -> "Watch sync"
        else -> "Settings"
    }

@Composable
private fun PhoneDestination(
    route: PhoneRoute,
    ui: AppUi,
    app: BetterApp,
    navigate: (PhoneRoute) -> Unit,
    back: () -> Unit,
    start: (Routine) -> Unit,
    importLibrary: () -> Unit,
    exportLibrary: () -> Unit,
) {
    when (route.screen) {
        "library" ->
            RoutineLibrary(
                ui.library.routines,
                { navigate(PhoneRoute("details", it.id)) },
                start,
                importLibrary,
            )
        "details" ->
            ui.library.routines
                .firstOrNull { it.id == route.id }
                ?.let { RoutineDetails(it, app, start, { navigate(PhoneRoute("edit", it)) }, back) }
                ?: EmptyRoutine(back)
        "edit" -> RoutineEditor(route.id, app, back) { navigate(PhoneRoute("timer")) }
        "timer" ->
            if (ui.timer.session != null) PhoneTimer(ui.timer, app, navigate, back)
            else EmptyTimer(back)
        "progress" -> if (ui.timer.session != null) SessionProgress(ui.timer) else EmptyTimer(back)
        "session-cues" ->
            if (ui.timer.session != null && !ui.timer.terminal) SessionCueSettings(ui.timer, app)
            else EmptyTimer(back)
        else -> PhoneSettings(route.screen, app, navigate, importLibrary, exportLibrary)
    }
}

@Composable
internal fun ScrollContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun EmptyRoutine(back: () -> Unit) {
    ScrollContent {
        Text("This routine is no longer in your library.")
        TextButton(onClick = back) { Text("Back to routines") }
    }
}

@Composable
private fun ActiveSessionStrip(timer: TimerUi, app: BetterApp, open: () -> Unit) {
    val state = requireNotNull(timer.session)
    val terminal = state.phase in listOf(Phase.COMPLETED, Phase.STOPPED)
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clickable(onClick = open).padding(12.dp)) {
                Text(state.routine.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    if (terminal) "${timer.status} · View summary"
                    else "${timer.label} · ${timer.time} · ${timer.status}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = { if (terminal) open() else app.command(timer.primaryCommand) }) {
                Text(if (terminal) "View" else timer.primaryLabel)
            }
        }
    }
}

@Composable
private fun ReplaceSessionDialog(
    onDismiss: () -> Unit,
    onReturn: () -> Unit,
    onReplace: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A timer is already active") },
        text = { Text("Return to the current session, or end it and start this routine.") },
        confirmButton = { TextButton(onClick = onReplace) { Text("End current and start new") } },
        dismissButton = {
            Column {
                TextButton(onClick = onReturn) { Text("Return to current session") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneTopBar(
    title: String,
    canGoBack: Boolean,
    library: Boolean,
    showTimer: Boolean,
    back: () -> Unit,
    navigate: (PhoneRoute) -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (canGoBack)
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = {
            if (showTimer) TextButton(onClick = { navigate(PhoneRoute("timer")) }) { Text("Timer") }
            if (library)
                IconButton(onClick = { navigate(PhoneRoute("settings")) }) {
                    Icon(Icons.Default.Settings, "Settings")
                }
        },
    )
}

@Composable
private fun EmptyTimer(back: () -> Unit) {
    ScrollContent {
        Text("No active timer")
        TextButton(onClick = back) { Text("Browse routines") }
    }
}
