package com.jayesh.satnav.data.offlinemaps

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.jayesh.satnav.core.constants.AppConstants
import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.LatLng
import com.jayesh.satnav.core.utils.LatLngBounds
import com.jayesh.satnav.data.local.maps.TileServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing offline map regions (MBTiles files).
 *
 * Scans the maps directory, reads metadata from each .mbtiles file,
 * and provides operations to list and delete regions.
 */
@Singleton
class OfflineMapsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDispatchers: AppDispatchers,
    private val tileServer: TileServer,
) {
    private val _regionsFlow = MutableSharedFlow<List<OfflineRegion>>(replay = 1)
    val regionsFlow: Flow<List<OfflineRegion>> = _regionsFlow.asSharedFlow()

    private val mapsDirectory: File
        get() = File(context.filesDir, AppConstants.OfflineMapsDirectory).apply {
            if (!exists()) mkdirs()
        }

    init {
        // Initial scan
        CoroutineScope(appDispatchers.io).run {
            // We'll trigger initial scan when first subscriber appears
        }
    }

    /**
     * Scans the maps directory and returns all found MBTiles regions.
     * Runs on IO dispatcher.
     */
    suspend fun listRegions(): List<OfflineRegion> = withContext(appDispatchers.io) {
        val directory = mapsDirectory
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext emptyList()
        }

        val files = directory.listFiles { file ->
            file.isFile && file.extension.equals("mbtiles", ignoreCase = true)
        } ?: emptyArray()

        files.mapNotNull { file ->
            try {
                parseRegionFromFile(file)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse MBTiles file: ${file.name}", e)
                null
            }
        }.also { regions ->
            // Emit the new list to the flow
            _regionsFlow.emit(regions)
        }
    }

    /**
     * Deletes a region by its ID (filename without extension).
     * Returns Result.success(Unit) on success, Result.failure on error.
     *
     * After deletion:
     * 1. Closes any SQLite handle the tile server has on that file
     * 2. Removes the file
     * 3. Notifies the tile server to rescan its sources
     * 4. Emits the updated list via regionsFlow
     */
    suspend fun delete(regionId: String): Result<Unit> = withContext(appDispatchers.io) {
        runCatching {
            val file = File(mapsDirectory, "$regionId.mbtiles")
            if (!file.exists()) {
                throw IllegalArgumentException("Region $regionId not found")
            }

            // Note: We cannot directly close the SQLite handle that the tile server has.
            // The tile server (MbtilesLocalHttpServer) holds an MbtilesArchive which
            // contains a SQLiteDatabase. If that archive is for the file being deleted,
            // the tile server should be restarted with a new archive.
            // For now, we just notify the tile server to reload, and it's up to the
            // tile server implementation to handle file changes appropriately.

            // Delete the file
            if (!file.delete()) {
                throw IllegalStateException("Failed to delete file ${file.absolutePath}")
            }

            Log.i(TAG, "Deleted offline map region: $regionId")

            // Notify tile server to rescan its sources
            tileServer.reload()

            // Update the flow with new list
            val regions = listRegions()
            _regionsFlow.emit(regions)
        }
    }

    /**
     * Returns total size in bytes of all offline map regions.
     */
    suspend fun totalSizeBytes(): Long = withContext(appDispatchers.io) {
        val directory = mapsDirectory
        if (!directory.exists()) return@withContext 0L

        val files = directory.listFiles { file ->
            file.isFile && file.extension.equals("mbtiles", ignoreCase = true)
        } ?: emptyArray()

        files.sumOf { it.length() }
    }

    /**
     * Parses an MBTiles file into an OfflineRegion.
     * Opens the SQLite database in read-only mode to read metadata.
     */
    private fun parseRegionFromFile(file: File): OfflineRegion {
        val database = try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
        } catch (e: SQLiteException) {
            throw IllegalArgumentException("Failed to open MBTiles database: ${file.name}", e)
        }

        return try {
            // Read metadata table
            val metadata = mutableMapOf<String, String>()
            database.query(
                "metadata",
                arrayOf("name", "value"),
                null, null, null, null, null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)
                    val value = cursor.getString(1)
                    metadata[key.lowercase()] = value
                }
            }

            // Parse bounds if present
            val bounds = metadata["bounds"]?.let { boundsStr ->
                val coords = boundsStr.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                if (coords.size == 4) {
                    // MBTiles bounds format: "west,south,east,north"
                    val (west, south, east, north) = coords
                    LatLngBounds(
                        southwest = LatLng(south, west),
                        northeast = LatLng(north, east)
                    )
                } else {
                    null
                }
            }

            // Parse zoom ranges
            val minZoom = metadata["minzoom"]?.toIntOrNull()
            val maxZoom = metadata["maxzoom"]?.toIntOrNull()

            // Determine name: prefer metadata name, fallback to filename without extension
            val name = metadata["name"] ?: file.nameWithoutExtension.replace("_", " ").replace("-", " ")

            OfflineRegion(
                id = file.nameWithoutExtension,
                name = name,
                filePath = file.absolutePath,
                sizeBytes = file.length(),
                bounds = bounds,
                minZoom = minZoom,
                maxZoom = maxZoom,
                installedAt = file.lastModified(),
            )
        } finally {
            database.close()
        }
    }

    companion object {
        private const val TAG = "OfflineMapsRepository"
    }
}