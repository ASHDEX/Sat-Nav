package com.jayesh.satnav.data.local.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jayesh.satnav.domain.model.RouteOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists active trip (RouteOption) to DataStore to survive process death.
 * SavedStateHandle only survives configuration changes, not process death.
 * DataStore persists to disk and survives process death.
 */
private val Context.tripDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "active_trip"
)

class TripPersistence(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private val KEY_ACTIVE_TRIP = stringPreferencesKey("active_trip_json")
        private val KEY_TRIP_START_TIME = stringPreferencesKey("trip_start_time")
    }
    
    /**
     * Save active trip when navigation starts
     */
    suspend fun saveActiveTrip(routeOption: RouteOption) {
        context.tripDataStore.edit { preferences ->
            preferences[KEY_ACTIVE_TRIP] = json.encodeToString(routeOption)
            preferences[KEY_TRIP_START_TIME] = System.currentTimeMillis().toString()
        }
    }
    
    /**
     * Load active trip if exists
     */
    fun loadActiveTrip(): Flow<RouteOption?> {
        return context.tripDataStore.data.map { preferences ->
            val jsonString = preferences[KEY_ACTIVE_TRIP]
            jsonString?.let {
                try {
                    json.decodeFromString<RouteOption>(it)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    /**
     * Clear active trip when navigation ends (arrival, cancellation)
     */
    suspend fun clearActiveTrip() {
        context.tripDataStore.edit { preferences ->
            preferences.remove(KEY_ACTIVE_TRIP)
            preferences.remove(KEY_TRIP_START_TIME)
        }
    }
    
    /**
     * Check if there's an active trip
     */
    fun hasActiveTrip(): Flow<Boolean> {
        return context.tripDataStore.data.map { preferences ->
            preferences[KEY_ACTIVE_TRIP] != null
        }
    }
    
    /**
     * Get trip start time
     */
    fun getTripStartTime(): Flow<Long?> {
        return context.tripDataStore.data.map { preferences ->
            preferences[KEY_TRIP_START_TIME]?.toLongOrNull()
        }
    }
}