package com.jayesh.satnav.data.search

import com.jayesh.satnav.domain.model.Place
import kotlinx.coroutines.flow.Flow
import org.maplibre.android.geometry.LatLng

/**
 * Interface for offline geocoding (searching places without internet).
 */
interface OfflineGeocoder {
    /**
     * Search for places matching the query, optionally biased near a location.
     *
     * @param query Search text (name, address, category)
     * @param near Optional location to bias results by proximity
     * @param limit Maximum number of results (default 20)
     * @return List of matching places, sorted by relevance
     */
    suspend fun search(query: String, near: LatLng?, limit: Int = 20): List<Place>
}