package com.jayesh.satnav.domain.model

import org.maplibre.android.geometry.LatLng

/**
 * Represents a place (POI, address, etc.) for geocoding results.
 */
data class Place(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val lat: Double,
    val lon: Double,
    val address: String?,
    val distanceMeters: Double?,   // null if user location unknown
) {
    /**
     * Convert to MapLibre LatLng for map operations.
     */
    fun toLatLng(): LatLng = LatLng(lat, lon)
}

/**
 * Simplified place categories for geocoding results.
 */
enum class PlaceCategory {
    Food,
    Fuel,
    Lodging,
    Transport,
    Shop,
    POI,
    Address,
    Other
}