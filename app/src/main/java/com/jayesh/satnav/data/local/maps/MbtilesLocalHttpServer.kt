package com.jayesh.satnav.data.local.maps

import android.content.Context
import android.util.Base64
import android.util.Log
import com.jayesh.satnav.core.constants.AppConstants
import com.jayesh.satnav.domain.model.OfflineTileFormat
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.ByteArrayInputStream

class MbtilesLocalHttpServer(
    context: Context,
    private val archive: MbtilesArchive,
) : NanoHTTPD(AppConstants.OfflineTileServerHost, AppConstants.OfflineTileServerPort),
    TileServer {

    private val styleBuilder = when (archive.mapPackage.format) {
        OfflineTileFormat.VectorPbf -> OfflineVectorStyleBuilder {
            context.assets.open("styles/offline_vector_style.json")
        }
        else -> OfflineRasterStyleBuilder {
            context.assets.open("styles/offline_raster_style.json")
        }
    }
    
    private val cockpitDarkStyleBuilder = when (archive.mapPackage.format) {
        OfflineTileFormat.VectorPbf -> OfflineVectorStyleBuilder {
            context.assets.open("styles/cockpit-dark.json")
        }
        else -> OfflineRasterStyleBuilder {
            context.assets.open("styles/offline_raster_style.json")
        }
    }
    
    private val cockpitLightStyleBuilder = when (archive.mapPackage.format) {
        OfflineTileFormat.VectorPbf -> OfflineVectorStyleBuilder {
            context.assets.open("styles/cockpit-light.json")
        }
        else -> OfflineRasterStyleBuilder {
            context.assets.open("styles/offline_raster_style.json")
        }
    }

    fun startServer() {
        start(SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "Started MBTiles HTTP server at ${styleUrl()}")
    }

    override fun reload() {
        // The tile server serves a single archive that was passed in the constructor.
        // If the underlying file changes (e.g., deleted), the archive may become invalid.
        // For now, we just log; the caller (OfflineMapRepositoryImpl) should restart the server
        // with a new archive when needed.
        Log.i(TAG, "TileServer reload requested")
    }

    fun styleUrl(): String = "http://${AppConstants.OfflineTileServerHost}:$listeningPort/style.json"
    
    fun styleDarkUrl(): String = "http://${AppConstants.OfflineTileServerHost}:$listeningPort/style-dark.json"
    
    fun styleLightUrl(): String = "http://${AppConstants.OfflineTileServerHost}:$listeningPort/style-light.json"

    fun tileJsonUrl(): String = "http://${AppConstants.OfflineTileServerHost}:$listeningPort/tiles.json"

    private fun tilesBaseUrl(): String = "http://${AppConstants.OfflineTileServerHost}:$listeningPort/tiles"

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.uri == "/health" -> newFixedLengthResponse(Status.OK, MIME_PLAINTEXT, "ok")
                session.uri == "/style.json" -> jsonResponse(
                    styleBuilder.build(
                        tilesUrl = tilesBaseUrl(),
                        mapPackage = archive.mapPackage,
                    ),
                )
                session.uri == "/style-dark.json" -> jsonResponse(
                    cockpitDarkStyleBuilder.build(
                        tilesUrl = tilesBaseUrl(),
                        mapPackage = archive.mapPackage,
                    ),
                )
                session.uri == "/style-light.json" -> jsonResponse(
                    cockpitLightStyleBuilder.build(
                        tilesUrl = tilesBaseUrl(),
                        mapPackage = archive.mapPackage,
                    ),
                )
                session.uri == "/tiles.json" -> jsonResponse(
                    styleBuilder.buildTileJson(
                        tilesUrl = tilesBaseUrl(),
                        mapPackage = archive.mapPackage,
                    ),
                )
                TILE_ROUTE.matches(session.uri) -> serveTile(session.uri)
                else -> newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (exception: Exception) {
            Log.e(TAG, "HTTP server failure on ${session.uri}", exception)
            newFixedLengthResponse(
                Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Tile server error",
            )
        }
    }

    private fun serveTile(uri: String): Response {
        val match = requireNotNull(TILE_ROUTE.matchEntire(uri))
        val z = match.groupValues[1].toInt()
        val x = match.groupValues[2].toInt()
        val y = match.groupValues[3].toInt()
        Log.d(TAG, "Tile request z=$z x=$x y=$y")

        return when (val tileResult = archive.readTile(z, x, y)) {
            is TileReadResult.Hit -> {
                val mimeType = when (tileResult.format) {
                    OfflineTileFormat.RasterPng -> "image/png"
                    OfflineTileFormat.RasterJpeg -> "image/jpeg"
                    OfflineTileFormat.RasterWebp -> "image/webp"
                    OfflineTileFormat.VectorPbf -> "application/vnd.mapbox-vector-tile"
                    OfflineTileFormat.Unknown -> "application/octet-stream"
                }
                newFixedLengthResponse(
                    Status.OK,
                    mimeType,
                    ByteArrayInputStream(tileResult.bytes),
                    tileResult.bytes.size.toLong(),
                ).apply {
                    addHeader("Cache-Control", "public, max-age=3600")
                    if (tileResult.format == OfflineTileFormat.VectorPbf) {
                        // MBTiles vector tiles are typically stored gzip-compressed.
                        addHeader("Content-Encoding", "gzip")
                    }
                }
            }
            TileReadResult.Missing -> newFixedLengthResponse(
                Status.OK,
                "image/png",
                ByteArrayInputStream(TRANSPARENT_TILE_BYTES),
                TRANSPARENT_TILE_BYTES.size.toLong(),
            )
            is TileReadResult.Error -> newFixedLengthResponse(
                Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Tile unavailable",
            )
        }
    }

    private fun jsonResponse(body: String): Response {
        return newFixedLengthResponse(Status.OK, "application/json", body)
    }

    companion object {
        private const val TAG = "MbtilesLocalServer"
        private val TILE_ROUTE = Regex("^/tiles/(\\d+)/(\\d+)/(\\d+)$")
        private val TRANSPARENT_TILE_BYTES = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9WnSUswAAAAASUVORK5CYII=",
            Base64.DEFAULT,
        )
    }
}
