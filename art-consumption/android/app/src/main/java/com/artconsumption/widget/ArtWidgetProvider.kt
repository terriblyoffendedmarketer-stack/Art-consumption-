package com.artconsumption.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.artconsumption.R
import com.artconsumption.data.ArtDatabase
import com.artconsumption.data.FirebaseSync
import com.artconsumption.ui.CarouselActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ArtWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_NEXT) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ArtWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        scope.launch {
            val dao = ArtDatabase.get(context).artDao()
            val sync = FirebaseSync(context)

            if (dao.getPostCount() == 0) {
                sync.sync()
            }

            val prefs = context.getSharedPreferences("carousel", Context.MODE_PRIVATE)
            val widgetShortcode = prefs.getString("widget_shortcode", null)
            val post = if (widgetShortcode != null) {
                dao.getPost(widgetShortcode) ?: dao.getLeastRecentlyShown()
            } else {
                dao.getLeastRecentlyShown()
            } ?: return@launch

            dao.markShown(post.shortcode, System.currentTimeMillis())

            val views = RemoteViews(context.packageName, R.layout.widget_art)

            val bitmap = sync.downloadLastSlideAsBitmap(post)
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_image, bitmap)
            }

            val captionLines = post.caption.lines()
                .map { it.trim() }
                .filter { line ->
                    line.isNotEmpty() &&
                    !line.startsWith("http") &&
                    !line.startsWith("#")
                }

            val hook = extractHook(captionLines)
            val truncated = if (hook.length > 150) hook.take(147) + "..." else hook

            val paintingName = captionLines.getOrNull(0) ?: ""
            val artist = captionLines.getOrNull(1) ?: ""
            val subtitle = if (artist.startsWith("By ", ignoreCase = true)) {
                "$paintingName • $artist"
            } else {
                paintingName
            }

            views.setTextViewText(R.id.widget_title, truncated)
            views.setTextViewText(R.id.widget_subtitle, subtitle)

            if (post.slideCount > 1) {
                views.setTextViewText(R.id.widget_slide_count, "${post.slideCount} slides")
            } else {
                views.setTextViewText(R.id.widget_slide_count, "")
            }

            val openIntent = CarouselActivity.intent(context, post.shortcode)
            val pendingOpen = PendingIntent.getActivity(
                context, widgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingOpen)

            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun extractHook(lines: List<String>): String {
        for (line in lines) {
            val lower = line.lowercase()
            if (line.length <= 40) continue
            if (lower.startsWith("by ")) continue
            if (lower.contains("now i understand")) continue
            if (line.startsWith("📍")) continue
            if (lower.matches("^.+\\(\\d{4}\\)$".toRegex())) continue
            return line
        }
        return lines.firstOrNull() ?: ""
    }

    companion object {
        const val ACTION_NEXT = "com.artconsumption.ACTION_NEXT_ART"

        fun triggerUpdate(context: Context) {
            val intent = Intent(context, ArtWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ArtWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
