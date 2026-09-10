package dev.betterwork.phone

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentActivity

/** Debug-only host for testing actual RemoteViews and PendingIntent activation. */
class WidgetHostActivity : ComponentActivity() {
    private lateinit var host: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, HOST_ID)
        val manager = AppWidgetManager.getInstance(this)
        val width = intent.getIntExtra("widthDp", 200)
        val height = intent.getIntExtra("heightDp", 112)
        fun pixels(dp: Int) = (dp * resources.displayMetrics.density).toInt()
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pixels(24), pixels(48), pixels(24), pixels(24))
            }
        intent.getIntArrayExtra("widgetIds")?.forEach { id ->
            val info = manager.getAppWidgetInfo(id) ?: return@forEach
            val view = host.createView(this, id, info)
            // The requested test dimensions describe content, excluding the host's default inset.
            val inset = AppWidgetHostView.getDefaultPaddingForWidget(this, info.provider, null)
            val outerWidth =
                width + ((inset.left + inset.right) / resources.displayMetrics.density).toInt()
            val outerHeight =
                height + ((inset.top + inset.bottom) / resources.displayMetrics.density).toInt()
            view.updateAppWidgetSize(null, outerWidth, outerHeight, outerWidth, outerHeight)
            root.addView(
                view,
                LinearLayout.LayoutParams(pixels(outerWidth), pixels(outerHeight)).apply {
                    bottomMargin = pixels(24)
                },
            )
        }
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        host.stopListening()
        super.onStop()
    }

    companion object {
        const val HOST_ID = 90210
    }
}
