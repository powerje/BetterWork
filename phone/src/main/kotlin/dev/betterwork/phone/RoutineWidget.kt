package dev.betterwork.phone

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.*
import dev.betterwork.data.AppPreferences
import dev.betterwork.data.Appearance
import dev.betterwork.domain.Phase
import dev.betterwork.domain.Routine
import dev.betterwork.platform.BetterApp
import dev.betterwork.presentation.formatTime
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal const val WIDGET_START = "dev.betterwork.START_WIDGET"
internal const val WIDGET_TOKEN = "activationToken"

class RoutineWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as BetterApp
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val store = WidgetBindings(context)
        val bindingFlow = store.observe(widgetId)
        val activeFlow =
            app.sessions
                .map { it?.phase in listOf(Phase.RUNNING, Phase.PAUSED, Phase.WAITING) }
                .distinctUntilChanged()
        provideContent {
            val binding by bindingFlow.collectAsState(initial = store.read(widgetId))
            val library by app.repository.library.collectAsState()
            val preferences by app.repository.appPreferences.collectAsState()
            val active by activeFlow.collectAsState(initial = app.hasActiveSession)
            val routine = library.routines.firstOrNull { it.id == binding.routineId }
            GlanceTheme(colors = widgetColors(context, preferences)) {
                WidgetContent(context, widgetId, binding.activationToken, routine, active)
            }
        }
    }
}

private fun widgetColors(
    context: Context,
    preferences: AppPreferences,
): androidx.glance.color.ColorProviders {
    val dynamic = preferences.wallpaperColors && Build.VERSION.SDK_INT >= 31
    val light =
        if (dynamic && Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context)
        else LightColors
    val dark =
        if (dynamic && Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else DarkColors
    return when (preferences.appearance) {
        Appearance.LIGHT -> ColorProviders(light)
        Appearance.DARK -> ColorProviders(dark)
        Appearance.SYSTEM -> ColorProviders(light, dark)
    }
}

@Composable
private fun WidgetContent(
    context: Context,
    widgetId: Int,
    token: String,
    routine: Routine?,
    active: Boolean,
) {
    val size = LocalSize.current
    val largeText = context.resources.configuration.fontScale > 1.3f
    val action =
        if (active || routine != null) {
            Intent(context, MainActivity::class.java)
                .setAction(WIDGET_START)
                .setData(Uri.parse("betterwork://widget/$widgetId/$token"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(WIDGET_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } else widgetConfigurationIntent(context, widgetId)
    val compact = size.height < 104.dp
    val description =
        if (active) "Open active timer"
        else routine?.let { "Start ${it.name}" } ?: "Choose a routine"
    val label =
        when {
            active -> if (largeText) "Timer" else "Open active timer"
            routine == null -> "Choose"
            else -> "Start"
        }
    Column(
        modifier =
            GlanceModifier.fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(16.dp)
                .padding(8.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (!compact) {
            Text(
                routine?.name ?: "Choose a routine",
                maxLines = if (size.height >= 160.dp && !largeText) 2 else 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
            if (size.height >= 140.dp && !largeText && routine != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    widgetSummary(routine),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
        }
        Button(
            if (compact) description else label,
            actionStartActivity(action),
            modifier =
                GlanceModifier.fillMaxWidth().height(48.dp).semantics {
                    contentDescription = description
                },
            style = TextStyle(fontSize = 14.sp),
            maxLines = if (largeText) 1 else 2,
        )
    }
}

internal fun widgetConfigurationIntent(context: Context, id: Int) =
    Intent(context, WidgetConfigurationActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun widgetSummary(routine: Routine): String =
    routine.limitMs?.let { "${formatTime(it)} limit" }
        ?: if (routine.repeat) "Until stopped"
        else formatTime(routine.expandedSteps().sumOf { it.durationMs })
