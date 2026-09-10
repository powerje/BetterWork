package dev.betterwork.phone

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class RoutineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RoutineWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetBindings(context).delete(appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        WidgetBindings(context).restore(oldWidgetIds, newWidgetIds)
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }
}
