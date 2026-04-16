package com.jayesh.satnav.data.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.domain.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentSearchesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_searches"
)

/**
 * Store for recent search selections backed by DataStore.
 * Serializes Place objects using kotlinx.serialization.
 */
@Singleton
class RecentSearchesStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val appDispatchers: AppDispatchers,
) {
    private companion object {
        private val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches_v1")
        private const val MAX_RECENT_SEARCHES = 20
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val dataStore = context.recentSearchesDataStore

    /**
     * Flow of recent places, sorted by most recent first.
     */
    val recents: Flow<List<Place>> = dataStore.data
        .map { preferences ->
            val serialized = preferences[RECENT_SEARCHES_KEY]
            if (serialized.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    json.decodeFromString<List<Place>>(serialized)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

    /**
     * Add a place to recent searches, deduplicating by id and capping at [MAX_RECENT_SEARCHES].
     */
    suspend fun addPlace(place: Place) = withContext(appDispatchers.io) {
        dataStore.edit { preferences ->
            val currentList = try {
                val serialized = preferences[RECENT_SEARCHES_KEY]
                if (serialized.isNullOrBlank()) {
                    emptyList()
                } else {
                    json.decodeFromString<List<Place>>(serialized)
                }
            } catch (e: Exception) {
                emptyList()
            }

            // Deduplicate by id, keeping the most recent (new) entry
            val newList = listOf(place) + currentList.filter { it.id != place.id }
                .take(MAX_RECENT_SEARCHES - 1)

            preferences[RECENT_SEARCHES_KEY] = json.encodeToString(newList)
        }
    }

    /**
     * Clear all recent searches.
     */
    suspend fun clear() = withContext(appDispatchers.io) {
        dataStore.edit { preferences ->
            preferences.remove(RECENT_SEARCHES_KEY)
        }
    }
}