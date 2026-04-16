package com.jayesh.satnav.features.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jayesh.satnav.core.utils.NavLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class GpsLocationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    fun isPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Cold flow of device GPS fixes at ~1 s intervals, high accuracy.
     * Each collector creates its own FusedLocationProvider subscription and
     * cancels it automatically when collection ends.
     * Returns nothing (suspends forever) if ACCESS_FINE_LOCATION is not granted.
     *
     * Callers should cancel the collecting coroutine to stop GPS.
     */
    val locationUpdates: Flow<Location> = callbackFlow {
        if (!isPermissionGranted()) {
            // Do not close() — let the caller's Job cancellation end this naturally.
            // Closing early makes the flow permanently terminated for re-collectors.
            awaitClose { /* nothing to clean up */ }
            return@callbackFlow
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS,
        ).setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    NavLog.gps(
                        "raw lat=%.6f lon=%.6f acc=%.1fm spd=%.1fkm/h".format(
                            loc.latitude, loc.longitude,
                            loc.accuracy,
                            if (loc.hasSpeed()) loc.speed * 3.6f else 0f,
                        ),
                    )
                    trySend(loc)
                }
            }
        }

        @SuppressLint("MissingPermission")
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
        const val MIN_UPDATE_INTERVAL_MS = 500L
    }
}
