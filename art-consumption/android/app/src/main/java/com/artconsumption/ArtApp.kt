package com.artconsumption

import android.app.Application
import com.artconsumption.widget.ArtWidgetUpdateWorker

class ArtApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ArtWidgetUpdateWorker.schedule(this)
    }
}
