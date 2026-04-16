package com.jayesh.satnav.domain.repository

import com.jayesh.satnav.domain.model.FavoritePoi
import com.jayesh.satnav.domain.model.PointOfInterest
import com.jayesh.satnav.domain.model.PoiSearchQuery
import com.jayesh.satnav.domain.model.PoiSearchResult
import com.jayesh.satnav.domain.model.RecentSearch
import kotlinx.coroutines.flow.Flow

/**
 * Repository for POI search and management operations.
 */
interface SearchRepository {
    
    // ========== SEARCH OPERATIONS ==========
    
    /**
     * Search for POIs based on query parameters.
     */
    suspend fun searchPois(query: PoiSearchQuery): PoiSearchResult
    
    /**
     * Get autocomplete suggestions for a partial query.
     */
    suspend fun getAutocompleteSuggestions(
        partialQuery: String,
        limit: Int = 10,
    ): List<String>
    
    /**
     * Get POI by its unique ID.
     */
    suspend fun getPoiById(poiId: String): PointOfInterest?
    
    /**
     * Get nearby POIs around a location.
     */
    suspend fun getNearbyPois(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = 1000.0,
        category: String? = null,
        limit: Int = 20,
    ): List<PointOfInterest>
    
    // ========== FAVORITES MANAGEMENT ==========
    
    /**
     * Get all favorite POIs.
     */
    suspend fun getFavoritePois(): List<PointOfInterest>
    
    /**
     * Check if a POI is marked as favorite.
     */
    suspend fun isPoiFavorite(poiId: String): Boolean
    
    /**
     * Add a POI to favorites.
     */
    suspend fun addToFavorites(poiId: String, customName: String? = null, notes: String? = null)
    
    /**
     * Remove a POI from favorites.
     */
    suspend fun removeFromFavorites(poiId: String)
    
    /**
     * Update favorite POI metadata.
     */
    suspend fun updateFavorite(favoritePoi: FavoritePoi)
    
    /**
     * Observe changes to favorites (for reactive UI updates).
     */
    fun observeFavorites(): Flow<List<PointOfInterest>>
    
    // ========== RECENT SEARCHES ==========
    
    /**
     * Get recent search queries.
     */
    suspend fun getRecentSearches(limit: Int = 10): List<RecentSearch>
    
    /**
     * Add a search query to recent searches.
     */
    suspend fun addRecentSearch(query: String, latitude: Double? = null, longitude: Double? = null)
    
    /**
     * Clear recent search history.
     */
    suspend fun clearRecentSearches()
    
    // ========== POI DATABASE MANAGEMENT ==========
    
    /**
     * Get total count of POIs in database.
     */
    suspend fun getPoiCount(): Long
    
    /**
     * Check if POI database is available/loaded.
     */
    suspend fun isPoiDatabaseAvailable(): Boolean
    
    /**
     * Import POIs from external source (GeoJSON, OSM PBF, etc.)
     */
    suspend fun importPoisFromSource(sourcePath: String): Result<Int> // returns count of imported POIs
    
    /**
     * Clear all POI data (for testing or reset).
     */
    suspend fun clearAllPoiData()
    
    // ========== CATEGORY OPERATIONS ==========
    
    /**
     * Get all available POI categories with counts.
     */
    suspend fun getPoiCategoriesWithCounts(): Map<String, Int> // category name -> count
    
    /**
     * Get popular/search trending categories.
     */
    suspend fun getPopularCategories(limit: Int = 10): List<String>
}