package com.jayesh.satnav.data.repository

import android.location.Location
import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.domain.repository.LocationRepository
import com.jayesh.satnav.features.location.GpsLocationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val gpsLocationManager: GpsLocationManager,
    private val appDispatchers: AppDispatchers,
) : LocationRepository {

    override val currentLocation: Flow<Location> = gpsLocationManager.locationUpdates

    override suspend fun lastKnown(): Location? = withContext(appDispatchers.io) {
        // GpsLocationManager doesn't expose last known location directly.
        // For simplicity, we'll return null; could be enhanced later.
        null
    }
}