package dev.betterwork.phone

import dev.betterwork.platform.BetterApp

internal enum class WidgetActivation {
    STARTED,
    OPEN_TIMER,
    CONFIGURE,
    REFRESHED,
    FAILED,
}

/** Called on the Activity's main thread, sharing the controller's pending-start guard. */
internal fun activateWidget(app: BetterApp, widgetId: Int, token: String): WidgetActivation {
    if (app.hasActiveSession) return WidgetActivation.OPEN_TIMER
    val bindings = WidgetBindings(app)
    val binding = bindings.read(widgetId)
    val routine =
        app.repository.library.value.routines.firstOrNull { it.id == binding.routineId }
            ?: return WidgetActivation.CONFIGURE
    if (!bindings.consume(widgetId, token)) {
        bindings.refresh(widgetId)
        app.message.value = "Widget refreshed. Tap Start again to begin a new session."
        return if (app.sessions.value != null) WidgetActivation.OPEN_TIMER
        else WidgetActivation.REFRESHED
    }
    return if (app.start(routine, app.repository.appPreferences.value.optionsFor(routine)))
        WidgetActivation.STARTED
    else {
        bindings.retry(widgetId)
        WidgetActivation.FAILED
    }
}
