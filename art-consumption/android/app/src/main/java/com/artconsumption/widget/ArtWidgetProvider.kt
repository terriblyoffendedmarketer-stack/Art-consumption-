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

            val post = dao.getLeastRecentlyShown() ?: return@launch

            dao.markShown(post.shortcode, System.currentTimeMillis())

            val views = RemoteViews(context.packageName, R.layout.widget_art)

            val bitmap = sync.downloadFirstSlideAsBitmap(post)
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_image, bitmap)
            }

            val captionLines = post.caption.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("http") }

            val title = captionLines.getOrNull(0) ?: ""
            val artist = captionLines.getOrNull(1) ?: "@${post.handle}"

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_subtitle, artist)

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
