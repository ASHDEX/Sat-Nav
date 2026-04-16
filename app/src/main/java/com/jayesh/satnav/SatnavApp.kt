package com.jayesh.satnav

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre

@HiltAndroidApp
class SatnavApp : Application() {

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
