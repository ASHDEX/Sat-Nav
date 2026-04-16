package com.jayesh.satnav.data.local.search

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.jayesh.satnav.core.constants.AppConstants
import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.domain.model.FavoritePoi
import com.jayesh.satnav.domain.model.PointOfInterest
import com.jayesh.satnav.domain.model.PoiCategory
import com.jayesh.satnav.domain.model.PoiSearchQuery
import com.jayesh.satnav.domain.model.PoiSearchResult
import com.jayesh.satnav.domain.model.RecentSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SQLite-based local data source for POI (Points of Interest) database.
 * 
 * Features:
 * - Full-text search with FTS5 virtual tables
 * - Spatial indexing for distance-based queries
 * - Favorites and recent searches management
 * - Category-based filtering
 * - Efficient pagination for large datasets
 */
@Singleton
class PoiLocalDataSource @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val appDispatchers: AppDispatchers,
) {
    
    private companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "poi.db"
        
        // Table names
        const val TABLE_POIS = "pois"
        const val TABLE_POIS_FTS = "pois_fts"
        const val TABLE_FAVORITES = "favorites"
        const val TABLE_RECENT_SEARCHES = "recent_searches"
        const val TABLE_CATEGORIES = "categories"
        
        // Column names
        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_LATITUDE = "latitude"
        const val COL_LONGITUDE = "longitude"
        const val COL_CATEGORY = "category"
        const val COL_ADDRESS = "address"
        const val COL_PHONE = "phone"
        const val COL_WEBSITE = "website"
        const val COL_OPENING_HOURS = "opening_hours"
        const val COL_RATING = "rating"
        const val COL_TAGS = "tags"
        const val COL_IS_FAVORITE = "is_favorite"
        const val COL_LAST_VISITED = "last_visited"
        const val COL_ADDED_TIMESTAMP = "added_timestamp"
        const val COL_CUSTOM_NAME = "custom_name"
        const val COL_NOTES = "notes"
        const val COL_FAVORITE_TAGS = "favorite_tags"
        const val COL_QUERY = "query"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_RESULT_COUNT = "result_count"
        const val COL_LOCATION_LATITUDE = "location_latitude"
        const val COL_LOCATION_LONGITUDE = "location_longitude"
        const val COL_CATEGORY_NAME = "category_name"
        const val COL_CATEGORY_DISPLAY_NAME = "category_display_name"
        const val COL_POI_COUNT = "poi_count"
    }
    
    private val _favoritesFlow = MutableStateFlow<List<PointOfInterest>>(emptyList())
    val favoritesFlow: Flow<List<PointOfInterest>> = _favoritesFlow.asStateFlow()
    
    private var databaseHelper: PoiDatabaseHelper? = null
    private var database: SQLiteDatabase? = null
    
    /**
     * Initialize the database connection.
     * Must be called before any other operations.
     */
    suspend fun initialize(): Result<Unit> = withContext(appDispatchers.io) {
        return@withContext try {
            requireDatabase() // triggers lazy init if not already done
            refreshFavoritesFlow()
            Result.success(Unit)
        } catch (e: Exception) {
            NavLog.e("Failed to initialize POI database", e)
            Result.failure(e)
        }
    }
    
    /**
     * Close database connection.
     */
    suspend fun close() = withContext(appDispatchers.io) {
        database?.close()
        databaseHelper?.close()
        database = null
        databaseHelper = null
    }
    
    // ========== SEARCH OPERATIONS ==========
    
    suspend fun searchPois(query: PoiSearchQuery): PoiSearchResult = withContext(appDispatchers.io) {
        val db = requireDatabase()
        
        val whereClauses = mutableListOf<String>()
        val whereArgs = mutableListOf<String>()
        
        // Text search using LIKE (FTS5 not available on all Android SQLite builds)
        if (query.query.isNotBlank()) {
            whereClauses.add("($COL_NAME LIKE ? OR $COL_ADDRESS LIKE ?)")
            val pattern = "%${query.query}%"
            whereArgs.add(pattern)
            whereArgs.add(pattern)
        }
        
        // Category filter
        query.category?.let { category ->
            whereClauses.add("$COL_CATEGORY = ?")
            whereArgs.add(category.name)
        }
        
        // Location-based filter (distance)
        if (query.centerLatitude != null && query.centerLongitude != null && query.radiusMeters != null) {
            // For SQLite without spatial extensions, we use bounding box approximation
            val lat = query.centerLatitude
            val lon = query.centerLongitude
            val radiusDegrees = query.radiusMeters / 111000.0 // Approximate degrees per meter at equator
            
            whereClauses.add("$COL_LATITUDE BETWEEN ? AND ?")
            whereArgs.add((lat - radiusDegrees).toString())
            whereArgs.add((lat + radiusDegrees).toString())
            
            whereClauses.add("$COL_LONGITUDE BETWEEN ? AND ?")
            whereArgs.add((lon - radiusDegrees).toString())
            whereArgs.add((lon + radiusDegrees).toString())
        }
        
        val whereClause = if (whereClauses.isNotEmpty()) {
            whereClauses.joinToString(" AND ")
        } else {
            "1=1"
        }
        
        // Build ORDER BY clause based on sort preference
        val orderBy = when (query.sortBy) {
            PoiSearchQuery.SortBy.DISTANCE -> {
                if (query.centerLatitude != null && query.centerLongitude != null) {
                    // Simplified distance calculation (approximation)
                    "ABS($COL_LATITUDE - ${query.centerLatitude}) + ABS($COL_LONGITUDE - ${query.centerLongitude}) ASC"
                } else {
                    "$COL_NAME ASC"
                }
            }
            PoiSearchQuery.SortBy.NAME -> "$COL_NAME ASC"
            PoiSearchQuery.SortBy.RATING -> "$COL_RATING DESC NULLS LAST"
            else -> "$COL_NAME ASC"
        }

        val fromTable = TABLE_POIS
        
        // Get total count
        val countCursor = db.rawQuery(
            "SELECT COUNT(*) FROM $fromTable WHERE $whereClause",
            whereArgs.toTypedArray()
        )
        val totalCount = if (countCursor.moveToFirst()) countCursor.getLong(0) else 0
        countCursor.close()
        
        // Get paginated results
        val limitClause = "LIMIT ${query.limit} OFFSET ${query.offset}"
        val sql = """
            SELECT * FROM $fromTable 
            WHERE $whereClause 
            ORDER BY $orderBy 
            $limitClause
        """.trimIndent()
        
        val cursor = db.rawQuery(sql, whereArgs.toTypedArray())
        val pois = mutableListOf<PointOfInterest>()
        
        while (cursor.moveToNext()) {
            pois.add(cursorToPoi(cursor))
        }
        cursor.close()
        
        val hasMore = (query.offset + query.limit) < totalCount
        
        return@withContext PoiSearchResult(
            pois = pois,
            totalCount = totalCount.toInt(),
            query = query,
            hasMore = hasMore,
        )
    }
    
    suspend fun getAutocompleteSuggestions(
        partialQuery: String,
        limit: Int = 10,
    ): List<String> = withContext(appDispatchers.io) {
        val db = requireDatabase()
        
        if (partialQuery.isBlank()) {
            return@withContext emptyList()
        }
        
        val suggestions = mutableListOf<String>()

        // Search in POI names using LIKE (no FTS5)
        val cursor = db.rawQuery(
            """
            SELECT DISTINCT $COL_NAME FROM $TABLE_POIS
            WHERE $COL_NAME LIKE ?
            ORDER BY $COL_NAME
            LIMIT ?
            """.trimIndent(),
            arrayOf("$partialQuery%", limit.toString())
        )
        
        while (cursor.moveToNext()) {
            suggestions.add(cursor.getString(0))
        }
        cursor.close()
        
        // Search in categories if we need more suggestions
        if (suggestions.size < limit) {
            val categoryCursor = db.rawQuery(
                """
                SELECT DISTINCT $COL_CATEGORY FROM $TABLE_POIS 
                WHERE $COL_CATEGORY LIKE ? 
                LIMIT ?
                """.trimIndent(),
                arrayOf("$partialQuery%", (limit - suggestions.size).toString())
            )
            
            while (categoryCursor.moveToNext()) {
                val category = categoryCursor.getString(0)
                suggestions.add(category.replace('_', ' ').replaceFirstChar { it.uppercase() })
            }
            categoryCursor.close()
        }
        
        return@withContext suggestions.distinct().take(limit)
    }
    
    suspend fun getPoiById(poiId: String): PointOfInterest? = withContext(appDispatchers.io) {
        val db = requireDatabase()
        
        val cursor = db.query(
            TABLE_POIS,
            null,
            "$COL_ID = ?",
            arrayOf(poiId),
            null, null, null, "1"
        )
        
        return@withContext if (cursor.moveToFirst()) {
            val poi = cursorToPoi(cursor)
            cursor.close()
            poi
        } else {
            cursor.close()
            null
        }
    }
    
    suspend fun getNearbyPois(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = 1000.0,
        category: String? = null,
        limit: Int = 20,
    ): List<PointOfInterest> = withContext(appDispatchers.io) {
        val db = requireDatabase()
        
        val whereClauses = mutableListOf<String>()
        val whereArgs = mutableListOf<String>()
        
        // Bounding box approximation for performance
        val radiusDegrees = radiusMeters / 111000.0
        
        whereClauses.add("$COL_LATITUDE BETWEEN ? AND ?")
        whereArgs.add((latitude - radiusDegrees).toString())
        whereArgs.add((latitude + radiusDegrees).toString())
        
        whereClauses.add("$COL_LONGITUDE BETWEEN ? AND ?")
        whereArgs.add((longitude - radiusDegrees).toString())
        whereArgs.add((longitude + radiusDegrees).toString())
        
        category?.let {
            whereClauses.add("$COL_CATEGORY = ?")
            whereArgs.add(it)
        }
        
        val whereClause = whereClauses.joinToString(" AND ")
        
        // Simplified distance calculation for ordering
        val sql = """
            SELECT *, 
                   (($COL_LATITUDE - $latitude) * ($COL_LATITUDE - $latitude) + 
                    ($COL_LONGITUDE - $longitude) * ($COL_LONGITUDE - $longitude)) as distance_sq
            FROM $TABLE_POIS 
            WHERE $whereClause
            ORDER BY distance_sq ASC
            LIMIT $limit
        """.trimIndent()
        
        val cursor = db.rawQuery(sql, whereArgs.toTypedArray())
        val pois = mutableListOf<PointOfInterest>()
        
        while (cursor.moveToNext()) {
            pois.add(cursorToPoi(cursor))
        }
        cursor.close()
        
        return@withContext pois
    }
    
    // ========== FAVORITES MANAGEMENT ==========
    
    suspend fun getFavoritePois(): List<PointOfInterest> = withContext(appDispatchers.io) {
        val db = requireDatabase()
        
        val cursor = db.rawQuery(
            """
            SELECT p.*, f.$COL_CUSTOM_NAME, f.$COL_NOTES, f.$COL_ADDED_TIMESTAMP
            FROM $TABLE_POIS p
            JOIN $TABLE_FAVORITES f ON p.$COL_ID = f.poi_id
            ORDER BY f.$COL_ADDED_TIMESTAMP DESC
            """.trimIndent(),
            null
        )
        
        val favorites = mutableListOf<PointOfInterest>()
        while (cursor.moveToNext()) {
            val poi = cursorToPoi(cursor)
            // TODO: Apply custom name and notes from favorites table
            favorites.add(poi)
        }
        cursor.close()
        
        return@withContext favorites
    }
    
    suspend fun isPoiFavorite(poiId: String): Boolean = withContext(appDispatchers.io) {
        val db = requireDatabase()
        
        val cursor = db.query(
            TABLE_FAVORITES,
            arrayOf(COL_ID),
            "$COL_ID = ?",
            arrayOf(poiId),
            null, null, null, "1"
        )
        
        val isFavorite = cursor.count > 0
        cursor.close()
        
        return@withContext isFavorite
    }
    
    suspend fun addToFavorites(
        poiId: String,
        customName: String? = null,
        notes: String? = null,
    ) = withContext(appDispatchers.io) {
        val db = requireDatabase()
        
        val values = ContentValues().apply {
            put(COL_ID, poiId)
            put(COL_ADDED_TIMESTAMP, System.currentTimeMillis())
            customName?.let { put(COL_CUSTOM_NAME, it) }
            notes?.let { put(COL_NOTES, it) }
        }
        
        db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        
        // Update the POI's is_favorite flag
        val updateValues = ContentValues().apply {
            put(COL_IS_FAVORITE, true)
        }
        db.update(TABLE_POIS, updateValues, "$COL_ID = ?", arrayOf(poiId))
        
        refreshFavoritesFlow()
    }
    
    suspend fun removeFromFavorites(poiId: String) = withContext(appDispatchers.io) {
        val db = requireDatabase()
        
        db.delete(TABLE_FAVORITES, "$COL_ID = ?", arrayOf(poiId))
        
        // Update the POI's is_favorite flag
        val values = ContentValues().apply {
            put(COL_IS_FAVORITE, false)
        }
        db.update(TABLE_POIS, values, "$COL_ID = ?", arrayOf(poiId))
        
        refreshFavoritesFlow()
    }
    
    suspend fun updateFavorite(favoritePoi: FavoritePoi) = withContext(appDispatchers.io) {
        val db = requireDatabase()
        
        val values = ContentValues().apply {
            favoritePoi.customName?.let { put(COL_CUSTOM_NAME, it) }
            favoritePoi.notes?.let { put(COL_NOTES, it) }
            put(COL_FAVORITE_TAGS, favoritePoi.tags.joinToString(","))
        }
        
        db.update(
            TABLE_FAVORITES,
            values,
            "$COL_ID = ?",
            arrayOf(favoritePoi.poiId)
        )
        
        refreshFavoritesFlow()
    }
    
    // ========== RECENT SEARCHES ==========
    
    suspend fun getRecentSearches(limit: Int = 10): List<RecentSearch> = withContext(appDispatchers.io) {
        val db = requireDatabase()
        
        val cursor = db.query(
            TABLE_RECENT_SEARCHES,
            null,
            null, null, null, null,
            "$COL_TIMESTAMP DESC",
            limit.toString()
        )
        
        val recentSearches = mutableListOf<RecentSearch>()
        while (cursor.moveToNext()) {
            recentSearches.add(cursorToRecentSearch(cursor))
        }
        cursor.close()
        
        return@withContext recentSearches
    }
    
    suspend fun addRecentSearch(
        query: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ) = withContext(appDispatchers.io) {
        val db = requireDatabase()
        
        // Remove old entries if we have too many
        val countCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_RECENT_SEARCHES", null)
        val count = if (countCursor.moveToFirst()) countCursor.getLong(0) else 0
        countCursor.close()
        
        if (count >= 100) {
            // Keep only the 50 most recent
            db.execSQL(
                "DELETE FROM $TABLE_RECENT_SEARCHES WHERE $COL_ID IN (" +
                "SELECT $COL_ID FROM $TABLE_RECENT_SEARCHES " +
                "ORDER BY $COL_TIMESTAMP ASC LIMIT ${count - 50}" +
                ")"

            )
        }
        
        // Insert new recent search
        val values = ContentValues().apply {
            put(COL_ID, UUID.randomUUID().toString())
            put(COL_QUERY, query)
            put(COL_TIMESTAMP, System.currentTimeMillis())
            if (latitude != null && longitude != null) {
                put(COL_LOCATION_LATITUDE, latitude)
                put(COL_LOCATION_LONGITUDE, longitude)
            }
        }
        
        db.insert(TABLE_RECENT_SEARCHES, null, values)
    }
    
    // ========== PRIVATE HELPER METHODS ==========
    
    private fun requireDatabase(): SQLiteDatabase {
        database?.let { return it }
        // Auto-initialize on first access so callers don't need to call initialize() manually.
        val databaseFile = getDatabaseFile(context)
        val helper = PoiDatabaseHelper(context, databaseFile.absolutePath)
        val db = helper.writableDatabase
        databaseHelper = helper
        database = db
        return db
    }
    
    private suspend fun refreshFavoritesFlow() = withContext(appDispatchers.io) {
        val favorites = getFavoritePois()
        _favoritesFlow.update { favorites }
    }
    
    private fun cursorToPoi(cursor: Cursor): PointOfInterest {
        return PointOfInterest(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
            latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LATITUDE)),
            longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LONGITUDE)),
            category = PoiCategory.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY))),
            address = cursor.getStringOrNull(cursor.getColumnIndex(COL_ADDRESS)),
            phone = cursor.getStringOrNull(cursor.getColumnIndex(COL_PHONE)),
            website = cursor.getStringOrNull(cursor.getColumnIndex(COL_WEBSITE)),
            openingHours = cursor.getStringOrNull(cursor.getColumnIndex(COL_OPENING_HOURS)),
            rating = cursor.getFloatOrNull(cursor.getColumnIndex(COL_RATING)),
            tags = parseTags(cursor.getStringOrNull(cursor.getColumnIndex(COL_TAGS))),
            isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_FAVORITE)) == 1,
            lastVisited = cursor.getLongOrNull(cursor.getColumnIndex(COL_LAST_VISITED))
        )
    }
    
    private fun cursorToRecentSearch(cursor: Cursor): RecentSearch {
        return RecentSearch(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
            query = cursor.getString(cursor.getColumnIndexOrThrow(COL_QUERY)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)),
            resultCount = cursor.getIntOrNull(cursor.getColumnIndex(COL_RESULT_COUNT)),
            locationLatitude = cursor.getDoubleOrNull(cursor.getColumnIndex(COL_LOCATION_LATITUDE)),
            locationLongitude = cursor.getDoubleOrNull(cursor.getColumnIndex(COL_LOCATION_LONGITUDE))
        )
    }
    
    private fun parseTags(tagsJson: String?): Map<String, String> {
        if (tagsJson.isNullOrBlank()) return emptyMap()
        return try {
            // Simple JSON parsing - in real implementation use proper JSON parser
            tagsJson.removePrefix("{").removeSuffix("}")
                .split(",")
                .associate { pair ->
                    val keyValue = pair.split(":")
                    keyValue[0].trim().removePrefix("\"") to keyValue[1].trim().removeSuffix("\"")
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex) else null
    }
    
    private fun Cursor.getIntOrNull(columnIndex: Int): Int? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getInt(columnIndex) else null
    }
    
    private fun Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getLong(columnIndex) else null
    }
    
    private fun Cursor.getFloatOrNull(columnIndex: Int): Float? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getFloat(columnIndex) else null
    }
    
    private fun Cursor.getDoubleOrNull(columnIndex: Int): Double? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getDouble(columnIndex) else null
    }
}
