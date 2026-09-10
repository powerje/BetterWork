package dev.betterwork.phone

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.betterwork.platform.BetterApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal object WidgetUpdates {
    fun observe(app: BetterApp) {
        app.scope.launch {
            combine(
                    app.repository.library,
                    app.repository.appPreferences,
                    app.sessions.map { it?.phase }.distinctUntilChanged(),
                ) { _, _, _ ->
                    Unit
                }
                .collect { refresh(app) }
        }
    }

    suspend fun refresh(context: Context) {
        val ids =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, RoutineWidgetReceiver::class.java))
        val store = WidgetBindings(context)
        val manager = GlanceAppWidgetManager(context)
        ids.forEach { id ->
            try {
                store.refresh(id)
                RoutineWidget().update(context, manager.getGlanceIdBy(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) {
                Log.w("BetterWork", "Widget was removed during refresh", error)
            } catch (error: IllegalStateException) {
                Log.w("BetterWork", "Widget host is unavailable", error)
            } catch (error: java.io.IOException) {
                Log.w("BetterWork", "Widget state could not be read", error)
            } catch (error: SecurityException) {
                Log.w("BetterWork", "Widget host access was denied", error)
            }
        }
    }
}
