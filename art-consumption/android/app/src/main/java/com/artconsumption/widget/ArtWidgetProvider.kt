package com.artconsumption.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.artconsumption.R
import com.artconsumption.data.ArtDatabase
import com.artconsumption.data.ContentScanner
import com.artconsumption.ui.CarouselActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

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

            if (dao.getPostCount() == 0) {
                ContentScanner(context).scan()
            }

            val post = dao.getLeastRecentlyShown() ?: return@launch

            dao.markShown(post.shortcode, System.currentTimeMillis())

            val slides = ContentScanner.getSlidesForPost(post)
            val hookSlide = slides.firstOrNull() ?: return@launch

            val views = RemoteViews(context.packageName, R.layout.widget_art)

            val bitmap = decodeSampledBitmap(hookSlide, 800, 800)
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_image, bitmap)
            }

            views.setTextViewText(R.id.widget_account, "@${post.handle}")
            if (post.slideCount > 1) {
                views.setTextViewText(R.id.widget_slide_count, "1/${post.slideCount}")
            } else {
                views.setTextViewText(R.id.widget_slide_count, "")
            }

            val openIntent = CarouselActivity.intent(context, post.shortcode)
            val pendingOpen = PendingIntent.getActivity(
                context, post.shortcode.hashCode(), openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image, pendingOpen)

            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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
