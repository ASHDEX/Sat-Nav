package com.jayesh.satnav.domain.model

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Point of Interest (POI) category enumeration.
 * Based on OpenStreetMap amenity and shop tags.
 */
enum class PoiCategory(val displayName: String) {
    // Food & Drink
    RESTAURANT("Restaurant"),
    CAFE("Cafe"),
    FAST_FOOD("Fast Food"),
    BAR("Bar"),
    PUB("Pub"),
    
    // Transportation
    FUEL_STATION("Fuel Station"),
    PARKING("Parking"),
    BUS_STOP("Bus Stop"),
    TRAIN_STATION("Train Station"),
    AIRPORT("Airport"),
    
    // Accommodation
    HOTEL("Hotel"),
    MOTEL("Motel"),
    HOSTEL("Hostel"),
    GUEST_HOUSE("Guest House"),
    
    // Shopping
    SUPERMARKET("Supermarket"),
    CONVENIENCE_STORE("Convenience Store"),
    PHARMACY("Pharmacy"),
    BAKERY("Bakery"),
    
    // Health & Emergency
    HOSPITAL("Hospital"),
    CLINIC("Clinic"),
    PHARMACY_EMERGENCY("Emergency Pharmacy"),
    POLICE_STATION("Police Station"),
    FIRE_STATION("Fire Station"),
    
    // Leisure & Entertainment
    CINEMA("Cinema"),
    THEATRE("Theatre"),
    MUSEUM("Museum"),
    LIBRARY("Library"),
    PARK("Park"),
    
    // Religious
    CHURCH("Church"),
    MOSQUE("Mosque"),
    TEMPLE("Temple"),
    SYNAGOGUE("Synagogue"),
    
    // Other
    BANK("Bank"),
    ATM("ATM"),
    POST_OFFICE("Post Office"),
    TOILET("Public Toilet"),
    OTHER("Other"),
    
    // Custom categories
    FAVORITE("Favorite"),
    RECENT("Recent"),
    HOME("Home"),
    WORK("Work"),
}

/**
 * Represents a Point of Interest (POI) with location and metadata.
 */
data class PointOfInterest(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: PoiCategory,
    val address: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val openingHours: String? = null,
    val rating: Float? = null,
    val tags: Map<String, String> = emptyMap(),
    val isFavorite: Boolean = false,
    val lastVisited: Long? = null, // timestamp in milliseconds
) {
    /**
     * Calculates distance to another coordinate using Haversine formula.
     */
    fun distanceTo(lat: Double, lon: Double): Double {
        val earthRadius = 6371000.0 // meters
        
        val lat1Rad = Math.toRadians(latitude)
        val lon1Rad = Math.toRadians(longitude)
        val lat2Rad = Math.toRadians(lat)
        val lon2Rad = Math.toRadians(lon)
        
        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad
        
        val a = Math.sin(dLat / 2).pow(2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(dLon / 2).pow(2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
}

/**
 * Search query parameters for POI search.
 */
data class PoiSearchQuery(
    val query: String = "",
    val category: PoiCategory? = null,
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    val radiusMeters: Double? = null, // Search radius in meters
    val limit: Int = 50,
    val offset: Int = 0,
    val sortBy: SortBy = SortBy.RELEVANCE,
) {
    enum class SortBy {
        RELEVANCE,      // Best match for text query
        DISTANCE,       // Nearest first
        NAME,           // Alphabetical by name
        RATING,         // Highest rating first
    }
}

/**
 * Search result with pagination metadata.
 */
data class PoiSearchResult(
    val pois: List<PointOfInterest>,
    val totalCount: Int,
    val query: PoiSearchQuery,
    val hasMore: Boolean,
) {
    companion object {
        val Empty = PoiSearchResult(
            pois = emptyList(),
            totalCount = 0,
            query = PoiSearchQuery(),
            hasMore = false,
        )
    }
}

/**
 * Recent search entry for quick access.
 */
data class RecentSearch(
    val id: String,
    val query: String,
    val timestamp: Long, // milliseconds since epoch
    val resultCount: Int? = null,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
)

/**
 * Favorite POI entry with custom metadata.
 */
data class FavoritePoi(
    val poiId: String,
    val addedTimestamp: Long,
    val customName: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
)