package dev.betterwork.wear

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.*
import androidx.wear.compose.navigation3.SwipeDismissableSceneStrategy
import dev.betterwork.domain.*
import dev.betterwork.platform.BetterApp
import dev.betterwork.presentation.*
import kotlinx.serialization.json.Json

@Composable
internal fun WatchApp(app: BetterApp, timerEntry: Int, requestNotifications: () -> Unit) {
    val scope = rememberCoroutineScope()
    val presenter = remember { scope.present(app.repository.library, app.sessions) }
    val ui by presenter.collectAsState()
    val preferences by app.repository.appPreferences.collectAsState()
    val message by app.message.collectAsState()
    val stack = rememberNavBackStack(WatchRoute("library"))
    var setupJson by rememberSaveable { mutableStateOf(Json.encodeToString(SessionOptions())) }
    val options = remember(setupJson) { Json.decodeFromString<SessionOptions>(setupJson) }
    var replacement by rememberSaveable { mutableStateOf<String?>(null) }
    var expectedHandle by rememberSaveable { mutableStateOf("") }
    fun navigate(route: WatchRoute) {
        if (stack.last() != route) stack.add(route)
    }
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    fun timer() {
        stack.clear()
        stack.add(WatchRoute("library"))
        stack.add(WatchRoute("timer"))
    }
    fun remoteTimer() {
        stack.clear()
        stack.add(WatchRoute("library"))
        stack.add(WatchRoute("remote-timer"))
    }
    fun start(routine: Routine) {
        if (app.hasActiveSession) {
            replacement = routine.id
            expectedHandle = app.sessionHandle
        } else {
            requestNotifications()
            if (app.start(routine, options)) timer()
        }
    }
    LaunchedEffect(timerEntry) { if (timerEntry > 0) timer() }
    WatchTheme {
        AppScaffold {
            NavDisplay(
                backStack = stack,
                onBack = ::back,
                sceneStrategy = remember { SwipeDismissableSceneStrategy<NavKey>() },
                entryProvider = { key ->
                    NavEntry(key) {
                        val route = key as WatchRoute
                        val routine = ui.library.routines.firstOrNull { it.id == route.id }
                        when (route.screen) {
                            "library" ->
                                WatchLibrary(
                                    ui,
                                    app,
                                    ::remoteTimer,
                                    message,
                                    { app.message.value = null },
                                    { navigate(WatchRoute("timer")) },
                                ) {
                                    setupJson = Json.encodeToString(preferences.optionsFor(it))
                                    navigate(WatchRoute("setup", it.id))
                                }
                            "timer" ->
                                WatchTimer(
                                    ui.timer,
                                    app,
                                    stack.last() == key,
                                    { navigate(WatchRoute("controls")) },
                                    {
                                        stack.clear()
                                        stack.add(WatchRoute("library"))
                                    },
                                )
                            "remote-timer" -> RemoteTimer(app, ::back)
                            "controls",
                            "progress",
                            "session-cues",
                            "session-step",
                            "session-completion" -> WatchPlayback(route, ui.timer, app, ::navigate)
                            else ->
                                if (routine != null)
                                    WatchSetup(
                                        route,
                                        routine,
                                        options,
                                        app,
                                        { setupJson = Json.encodeToString(it) },
                                        ::navigate,
                                        { start(routine) },
                                    )
                                else
                                    WatchList("Routine unavailable") {
                                        item {
                                            WatchText("This routine is no longer in the library.")
                                        }
                                        item { WatchAction("Back", action = ::back) }
                                    }
                        }
                    }
                },
            )
        }
        replacement?.let { id ->
            WatchReplaceDialog(
                { replacement = null },
                {
                    replacement = null
                    timer()
                },
            ) {
                ui.library.routines
                    .firstOrNull { it.id == id }
                    ?.let { if (app.replace(it, options, expectedHandle)) timer() }
                replacement = null
            }
        }
    }
}

@Composable
private fun WatchLibrary(
    ui: AppUi,
    app: BetterApp,
    remoteTimer: () -> Unit,
    message: String?,
    dismiss: () -> Unit,
    timer: () -> Unit,
    select: (Routine) -> Unit,
) {
    val remote by app.remoteSession.collectAsState()
    WatchList("Routines") {
        if (remote != null)
            item { WatchAction("Phone session", primary = true, action = remoteTimer) }
        if (ui.timer.session != null)
            item {
                WatchAction(
                    if (ui.timer.terminal) "Session summary" else "Active session",
                    primary = true,
                    action = timer,
                )
            }
        items(ui.library.routines.size, key = { ui.library.routines[it].id }) { index ->
            val routine = ui.library.routines[index]
            WatchAction(routine.name) { select(routine) }
        }
        if (ui.library.routines.isEmpty())
            item { WatchText("Add or import routines on your phone.") }
        if (message != null) {
            item { WatchText(message) }
            item { WatchAction("Dismiss message", action = dismiss) }
        }
    }
}
