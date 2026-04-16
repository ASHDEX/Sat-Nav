package com.jayesh.satnav.data.search

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.domain.model.Place
import com.jayesh.satnav.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for search operations with debouncing and recent searches.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val geocoder: OfflineGeocoder,
    private val recentsStore: RecentSearchesStore,
    private val locationRepository: LocationRepository,
    private val appDispatchers: AppDispatchers,
) {
    /**
     * Search with debouncing (250ms) and cancellation of in-flight searches.
     * Emits empty list when query is blank.
     */
    fun search(queryFlow: Flow<String>): Flow<List<Place>> =
        queryFlow
            .debounce(DEBOUNCE_MS)
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    flow { emit(emptyList()) }
                } else {
                    flow {
                        // Get current location for 'near' bias if available
                        val near = getCurrentLocationForBias()
                        val results = geocoder.search(query, near, DEFAULT_SEARCH_LIMIT)
                        emit(results)
                    }
                }
            }
            .flowOn(appDispatchers.io)

    /**
     * Record a place selection into recent searches.
     */
    suspend fun recordSelection(place: Place) = withContext(appDispatchers.io) {
        recentsStore.addPlace(place)
    }

    /**
     * Flow of recent places (most recent first).
     */
    val recents: Flow<List<Place>> = recentsStore.recents

    private suspend fun getCurrentLocationForBias(): LatLng? =
        withContext(appDispatchers.io) {
            // Try to get last known location; if not available, return null
            val location = locationRepository.lastKnown()
            location?.let { LatLng(it.latitude, it.longitude) }
        }

    companion object {
        private const val DEBOUNCE_MS = 250L
        private const val DEFAULT_SEARCH_LIMIT = 20
    }
}