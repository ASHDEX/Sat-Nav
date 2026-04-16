package com.jayesh.satnav.core.utils

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.jayesh.satnav.BuildConfig

/**
 * Centralized debug logger for the navigation pipeline.
 * All output is suppressed in release builds.
 *
 * Use logcat filter: NAV/ to see all navigation logs together.
 */
internal object NavLog {
    private var enabled = BuildConfig.DEBUG
    
    /** Test-only method to disable logging for unit tests */
    @VisibleForTesting
    internal fun disableForTests() {
        enabled = false
    }

    /** Raw GPS fix from FusedLocationProvider before any processing. */
    fun gps(msg: String) { if (enabled) Log.d("NAV/GPS", msg) }

    /** Result of snapping raw GPS onto the route polyline. */
    fun match(msg: String) { if (enabled) Log.d("NAV/Match", msg) }

    /** Reroute lifecycle events: trigger, success, failure. */
    fun reroute(msg: String) { if (enabled) Log.d("NAV/Reroute", msg) }

    /** Current navigation instruction and turn distance. */
    fun instr(msg: String) { if (enabled) Log.d("NAV/Instr", msg) }

    /** Voice/TTS related logs. */
    fun voice(msg: String) { if (enabled) Log.d("NAV/Voice", msg) }

    /** Camera movement and follow mode logs. */
    fun camera(msg: String) { if (enabled) Log.d("NAV/Camera", msg) }

    /** Position interpolation and smoothing logs. */
    fun interpolation(msg: String) { if (enabled) Log.d("NAV/Interpolation", msg) }

    /** Generic error logging. */
    fun e(msg: String, e: Exception? = null) {
        if (enabled) {
            Log.e("NAV/Error", msg)
            e?.let { Log.e("NAV/Error", it.stackTraceToString()) }
        }
    }

    /** Generic warning logging. */
    fun w(msg: String) { if (enabled) Log.w("NAV/Warn", msg) }

    /** Generic info logging. */
    fun i(msg: String) { if (enabled) Log.i("NAV/Info", msg) }
}
