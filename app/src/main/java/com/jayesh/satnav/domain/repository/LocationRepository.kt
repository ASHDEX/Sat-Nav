package com.jayesh.satnav.domain.repository

import android.location.Location
import kotlinx.coroutines.flow.Flow

/**
 * Repository for accessing device location.
 */
interface LocationRepository {
    /**
     * Cold flow of current device location updates.
     * Backed by FusedLocationProviderClient callbackFlow.
     * Emits Location objects with latitude, longitude, bearing, speed, accuracy.
     */
    val currentLocation: Flow<Location>

    /**
     * Get the most recent known location, or null if none.
     */
    suspend fun lastKnown(): Location?
}