package com.jayesh.satnav.data.local.maps

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import android.util.LruCache
import com.jayesh.satnav.domain.model.OfflineMapPackage
import com.jayesh.satnav.domain.model.OfflineTileFormat
import com.jayesh.satnav.domain.model.OfflineTileScheme
import java.io.Closeable
import java.io.File

class MbtilesArchive private constructor(
    private val database: SQLiteDatabase,
    val mapPackage: OfflineMapPackage,
) : Closeable {

    private val tileCache = object : LruCache<String, ByteArray>(CACHE_MAX_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    private val databaseLock = Any()

    fun readTile(z: Int, x: Int, y: Int): TileReadResult {
        if (z !in mapPackage.minZoom..mapPackage.maxZoom) {
            Log.d(TAG, "Ignoring tile outside zoom range: z=$z x=$x y=$y")
            return TileReadResult.Missing
        }

        val cacheKey = "$z/$x/$y"
        tileCache.get(cacheKey)?.let { cached ->
            Log.d(TAG, "Tile cache hit for $cacheKey")
            return TileReadResult.Hit(cached, mapPackage.format)
        }

        return try {
            synchronized(databaseLock) {
                for (candidateRow in TileSchemeResolver.candidateRows(z, y, mapPackage.scheme)) {
                    database.query(
                        "tiles",
                        arrayOf("tile_data"),
                        "zoom_level = ? AND tile_column = ? AND tile_row = ?",
                        arrayOf(z.toString(), x.toString(), candidateRow.toString()),
                        null,
                        null,
                        null,
                        "1",
                    ).useCursor { cursor ->
                        if (cursor.moveToFirst()) {
                            val tile = cursor.getBlob(0)
                            tileCache.put(cacheKey, tile)
                            Log.d(TAG, "Served tile $cacheKey via row=$candidateRow bytes=${tile.size}")
                            return@synchronized TileReadResult.Hit(tile, mapPackage.format)
                        }
                    }
                }
                Log.w(TAG, "Tile not found for z=$z x=$x y=$y")
                TileReadResult.Missing
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Tile read failed for z=$z x=$x y=$y", exception)
            TileReadResult.Error(exception)
        }
    }

    override fun close() {
        synchronized(databaseLock) {
            if (database.isOpen) {
                database.close()
            }
        }
    }

    companion object {
        private const val TAG = "MbtilesArchive"
        private const val CACHE_MAX_BYTES = 12 * 1024 * 1024

        fun open(file: File): Result<MbtilesArchive> = runCatching {
            val database = try {
                SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                )
            } catch (e: SQLiteException) {
                Log.e(TAG, "Critical SQLite error opening ${file.name}", e)
                throw e
            }

            // Basic integrity check: Read metadata
            val archiveMetadata: Map<String, String> = try {
                database.query(
                    "metadata",
                    arrayOf("name", "value"),
                    null,
                    null,
                    null,
                    null,
                    null,
                ).useCursor { cursor ->
                    val result = mutableMapOf<String, String>()
                    while (cursor.moveToNext()) {
                        val key = cursor.getString(0).lowercase()
                        val value = cursor.getString(1)
                        result[key] = value
                    }
                    result
                }
            } catch (e: Exception) {
                database.close()
                Log.e(TAG, "Integrity check failed for ${file.name}: Malformed metadata", e)
                throw SQLiteException("Malformed MBTiles metadata: ${e.message}")
            }

            if (archiveMetadata.isEmpty()) {
                database.close()
                throw SQLiteException("Empty MBTiles metadata")
            }

            val format = when (archiveMetadata["format"]?.lowercase()) {
                "png" -> OfflineTileFormat.RasterPng
                "jpg", "jpeg" -> OfflineTileFormat.RasterJpeg
                "webp" -> OfflineTileFormat.RasterWebp
                "pbf", "mvt" -> OfflineTileFormat.VectorPbf
                else -> OfflineTileFormat.Unknown
            }
            val scheme = when (archiveMetadata["scheme"]?.lowercase()) {
                "xyz" -> OfflineTileScheme.XYZ
                else -> OfflineTileScheme.TMS
            }

            // Helper to parse comma-separated doubles
            fun parseDoubles(value: String?): List<Double> {
                return value?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() } ?: emptyList()
            }

            val mapPackage = OfflineMapPackage(
                fileName = archiveMetadata["name"] ?: file.name,
                absolutePath = file.absolutePath,
                format = format,
                scheme = scheme,
                minZoom = archiveMetadata["minzoom"]?.toIntOrNull() ?: 0,
                maxZoom = archiveMetadata["maxzoom"]?.toIntOrNull() ?: 14,
                bounds = parseDoubles(archiveMetadata["bounds"]),
                center = parseDoubles(archiveMetadata["center"]),
                tileSize = archiveMetadata["tile_size"]?.toIntOrNull() ?: 256,
                attribution = archiveMetadata["attribution"],
                description = archiveMetadata["description"],
            )

            MbtilesArchive(database, mapPackage)
        }.recoverCatching { error ->
            Log.e(TAG, "Failed to resolve MBTiles archive ${file.absolutePath}", error)
            throw SQLiteException("Failed to open MBTiles archive ${file.absolutePath}: ${error.message}")
        }
    }
}

sealed interface TileReadResult {
    data class Hit(
        val bytes: ByteArray,
        val format: OfflineTileFormat,
    ) : TileReadResult

    data object Missing : TileReadResult

    data class Error(val throwable: Throwable) : TileReadResult
}

private inline fun <T> Cursor.useCursor(block: (Cursor) -> T): T {
    return use(block)
}

