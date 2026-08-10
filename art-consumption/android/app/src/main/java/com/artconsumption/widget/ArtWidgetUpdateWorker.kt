package com.artconsumption.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.artconsumption.data.ContentScanner
import java.util.concurrent.TimeUnit

class ArtWidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ContentScanner(applicationContext).scan()
        ArtWidgetProvider.triggerUpdate(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "art_widget_update"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ArtWidgetUpdateWorker>(
                30, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
