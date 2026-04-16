package com.jayesh.satnav.data.repository

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.data.local.search.PoiLocalDataSource
import com.jayesh.satnav.domain.model.FavoritePoi
import com.jayesh.satnav.domain.model.PointOfInterest
import com.jayesh.satnav.domain.model.PoiSearchQuery
import com.jayesh.satnav.domain.model.PoiSearchResult
import com.jayesh.satnav.domain.model.RecentSearch
import com.jayesh.satnav.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SearchRepository that uses PoiLocalDataSource.
 */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val poiLocalDataSource: PoiLocalDataSource,
    private val appDispatchers: AppDispatchers,
) : SearchRepository {
    
    private var isInitialized = false
    
    /**
     * Initialize the repository and underlying data source.
     */
    suspend fun initialize(): Result<Unit> = withContext(appDispatchers.io) {
        return@withContext try {
            val result = poiLocalDataSource.initialize()
            if (result.isSuccess) {
                isInitialized = true
            }
            result
        } catch (e: Exception) {
            NavLog.e("Failed to initialize SearchRepository", e)
            Result.failure(e)
        }
    }
    
    /**
     * Ensure repository is initialized before operations.
     */
    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            initialize()
        }
    }
    
    // ========== SEARCH OPERATIONS ==========
    
    override suspend fun searchPois(query: PoiSearchQuery): PoiSearchResult {
        ensureInitialized()
        return poiLocalDataSource.searchPois(query)
    }
    
    override suspend fun getAutocompleteSuggestions(
        partialQuery: String,
        limit: Int,
    ): List<String> {
        ensureInitialized()
        return poiLocalDataSource.getAutocompleteSuggestions(partialQuery, limit)
    }
    
    override suspend fun getPoiById(poiId: String): PointOfInterest? {
        ensureInitialized()
        return poiLocalDataSource.getPoiById(poiId)
    }
    
    override suspend fun getNearbyPois(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        category: String?,
        limit: Int,
    ): List<PointOfInterest> {
        ensureInitialized()
        return poiLocalDataSource.getNearbyPois(
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            category = category,
            limit = limit,
        )
    }
    
    // ========== FAVORITES MANAGEMENT ==========
    
    override suspend fun getFavoritePois(): List<PointOfInterest> {
        ensureInitialized()
        return poiLocalDataSource.getFavoritePois()
    }
    
    override suspend fun isPoiFavorite(poiId: String): Boolean {
        ensureInitialized()
        return poiLocalDataSource.isPoiFavorite(poiId)
    }
    
    override suspend fun addToFavorites(
        poiId: String,
        customName: String?,
        notes: String?,
    ) {
        ensureInitialized()
        poiLocalDataSource.addToFavorites(poiId, customName, notes)
    }
    
    override suspend fun removeFromFavorites(poiId: String) {
        ensureInitialized()
        poiLocalDataSource.removeFromFavorites(poiId)
    }
    
    override suspend fun updateFavorite(favoritePoi: FavoritePoi) {
        ensureInitialized()
        poiLocalDataSource.updateFavorite(favoritePoi)
    }
    
    override fun observeFavorites(): Flow<List<PointOfInterest>> {
        return poiLocalDataSource.favoritesFlow
    }
    
    // ========== RECENT SEARCHES ==========
    
    override suspend fun getRecentSearches(limit: Int): List<RecentSearch> {
        ensureInitialized()
        return poiLocalDataSource.getRecentSearches(limit)
    }
    
    override suspend fun addRecentSearch(
        query: String,
        latitude: Double?,
        longitude: Double?,
    ) {
        ensureInitialized()
        poiLocalDataSource.addRecentSearch(query, latitude, longitude)
    }
    
    override suspend fun clearRecentSearches() {
        ensureInitialized()
        // Implementation would be added to PoiLocalDataSource
        // For now, we'll log that this needs implementation
        NavLog.w("clearRecentSearches not yet implemented in PoiLocalDataSource")
    }
    
    // ========== POI DATABASE MANAGEMENT ==========
    
    override suspend fun getPoiCount(): Long {
        ensureInitialized()
        // Implementation would be added to PoiLocalDataSource
        // For now, return 0
        return 0L
    }
    
    override suspend fun isPoiDatabaseAvailable(): Boolean {
        return isInitialized
    }
    
    override suspend fun importPoisFromSource(sourcePath: String): Result<Int> {
        ensureInitialized()
        
        return withContext(appDispatchers.io) {
            try {
                val sourceFile = File(sourcePath)
                if (!sourceFile.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Source file does not exist: $sourcePath")
                    )
                }
                
                // Determine file type and import accordingly
                val importedCount = when {
                    sourcePath.endsWith(".geojson", ignoreCase = true) -> {
                        importFromGeoJson(sourceFile)
                    }
                    sourcePath.endsWith(".json", ignoreCase = true) -> {
                        importFromGeoJson(sourceFile)
                    }
                    sourcePath.endsWith(".osm.pbf", ignoreCase = true) -> {
                        importFromOsmPbf(sourceFile)
                    }
                    else -> {
                        return@withContext Result.failure(
                            IllegalArgumentException("Unsupported file format: $sourcePath")
                        )
                    }
                }
                
                Result.success(importedCount)
            } catch (e: Exception) {
                NavLog.e("Failed to import POIs from $sourcePath", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun clearAllPoiData() {
        ensureInitialized()
        // Implementation would be added to PoiLocalDataSource
        NavLog.w("clearAllPoiData not yet implemented in PoiLocalDataSource")
    }
    
    // ========== CATEGORY OPERATIONS ==========
    
    override suspend fun getPoiCategoriesWithCounts(): Map<String, Int> {
        ensureInitialized()
        // Implementation would be added to PoiLocalDataSource
        // For now, return empty map
        return emptyMap()
    }
    
    override suspend fun getPopularCategories(limit: Int): List<String> {
        ensureInitialized()
        // Implementation would be added to PoiLocalDataSource
        // For now, return some default categories
        return listOf(
            "RESTAURANT",
            "CAFE", 
            "FUEL_STATION",
            "HOTEL",
            "SUPERMARKET",
            "PHARMACY",
            "HOSPITAL",
            "BANK",
            "PARK",
        ).take(limit)
    }
    
    // ========== PRIVATE HELPER METHODS ==========
    
    /**
     * Import POIs from GeoJSON file.
     */
    private suspend fun importFromGeoJson(file: File): Int {
        // TODO: Implement GeoJSON parsing and import
        // This would parse the GeoJSON file and insert POIs into the database
        NavLog.i("Importing POIs from GeoJSON: ${file.absolutePath}")
        
        // For now, return 0 as placeholder
        return 0
    }
    
    /**
     * Import POIs from OSM PBF file.
     */
    private suspend fun importFromOsmPbf(file: File): Int {
        // TODO: Implement OSM PBF parsing and import
        // This would parse the OSM PBF file, extract amenities, shops, etc.
        NavLog.i("Importing POIs from OSM PBF: ${file.absolutePath}")
        
        // For now, return 0 as placeholder
        return 0
    }
    
    /**
     * Close repository and release resources.
     */
    suspend fun close() {
        poiLocalDataSource.close()
        isInitialized = false
    }
}