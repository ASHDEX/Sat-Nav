package com.jayesh.satnav.data.local.terrain

import android.content.Context
import android.util.Base64
import android.util.Log
import com.jayesh.satnav.core.constants.AppConstants
import com.jayesh.satnav.data.local.maps.MbtilesArchive
import com.jayesh.satnav.data.local.maps.TileReadResult
import com.jayesh.satnav.domain.model.OfflineTileFormat
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.ByteArrayInputStream

class TerrainLocalHttpServer(
    context: Context,
    private val demArchive: MbtilesArchive?,
    private val hillshadeArchive: MbtilesArchive?,
) : NanoHTTPD(AppConstants.OfflineTileServerHost, AppConstants.OfflineTileServerPort + 1) {

    private val styleBuilder = TerrainStyleBuilder {
        context.assets.open("styles/offline_terrain_style.json")
    }

    fun startServer() {
        start(SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "Started Terrain HTTP server at ${styleUrl()}")
    }

    fun styleUrl(): String = "http://${AppConstants.OfflineTileServerHost}:$listeningPort/style.json"

    fun tileJsonUrl(): String = "http://${AppConstants.OfflineTileServerHost}:$listeningPort/tiles.json"

    private fun tilesBaseUrl(): String = "http://${AppConstants.OfflineTileServerHost}:$listeningPort/tiles"

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.uri == "/health" -> newFixedLengthResponse(Status.OK, MIME_PLAINTEXT, "ok")
                session.uri == "/style.json" -> jsonResponse(
                    styleBuilder.build(
                        tilesUrl = tilesBaseUrl(),
                        mapPackage = demArchive?.mapPackage ?: hillshadeArchive?.mapPackage
                            ?: throw IllegalStateException("No terrain archive available"),
                    ),
                )
                session.uri == "/tiles.json" -> jsonResponse(
                    styleBuilder.buildTileJson(
                        tilesUrl = tilesBaseUrl(),
                        mapPackage = demArchive?.mapPackage ?: hillshadeArchive?.mapPackage
                            ?: throw IllegalStateException("No terrain archive available"),
                    ),
                )
                session.uri.startsWith("/tiles/dem/") -> serveDemTile(session.uri)
                session.uri.startsWith("/tiles/hillshade/") -> serveHillshadeTile(session.uri)
                else -> newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (exception: Exception) {
            Log.e(TAG, "HTTP server failure on ${session.uri}", exception)
            newFixedLengthResponse(
                Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Terrain server error",
            )
        }
    }

    private fun serveDemTile(uri: String): Response {
        val tilePath = uri.removePrefix("/tiles/dem/")
        val parts = tilePath.split("/")
        if (parts.size != 3) {
            return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid tile path")
        }

        val z = parts[0].toIntOrNull() ?: return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid zoom")
        val x = parts[1].toIntOrNull() ?: return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid x")
        val y = parts[2].toIntOrNull() ?: return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid y")

        Log.d(TAG, "DEM tile request z=$z x=$x y=$y")

        return when (val archive = demArchive) {
            null -> serveTransparentTile()
            else -> serveTileFromArchive(archive, z, x, y)
        }
    }

    private fun serveHillshadeTile(uri: String): Response {
        val tilePath = uri.removePrefix("/tiles/hillshade/")
        val parts = tilePath.split("/")
        if (parts.size != 3) {
            return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid tile path")
        }

        val z = parts[0].toIntOrNull() ?: return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid zoom")
        val x = parts[1].toIntOrNull() ?: return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid x")
        val y = parts[2].toIntOrNull() ?: return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid y")

        Log.d(TAG, "Hillshade tile request z=$z x=$x y=$y")

        return when (val archive = hillshadeArchive) {
            null -> serveTransparentTile()
            else -> serveTileFromArchive(archive, z, x, y)
        }
    }

    private fun serveTileFromArchive(archive: MbtilesArchive, z: Int, x: Int, y: Int): Response {
        return when (val tileResult = archive.readTile(z, x, y)) {
            is TileReadResult.Hit -> {
                val mimeType = when (tileResult.format) {
                    OfflineTileFormat.RasterPng -> "image/png"
                    OfflineTileFormat.RasterJpeg -> "image/jpeg"
                    OfflineTileFormat.RasterWebp -> "image/webp"
                    OfflineTileFormat.VectorPbf -> "application/x-protobuf"
                    OfflineTileFormat.Unknown -> "application/octet-stream"
                }
                newFixedLengthResponse(
                    Status.OK,
                    mimeType,
                    ByteArrayInputStream(tileResult.bytes),
                    tileResult.bytes.size.toLong(),
                )
            }
            is TileReadResult.Missing -> serveTransparentTile()
            is TileReadResult.Error -> {
                Log.e(TAG, "Tile read error", tileResult.throwable)
                newFixedLengthResponse(
                    Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Tile read error",
                )
            }
        }
    }

    private fun serveTransparentTile(): Response {
        return newFixedLengthResponse(
            Status.OK,
            "image/png",
            ByteArrayInputStream(TRANSPARENT_TILE_BYTES),
            TRANSPARENT_TILE_BYTES.size.toLong(),
        )
    }

    private fun jsonResponse(json: String): Response {
        return newFixedLengthResponse(Status.OK, "application/json", json)
    }

    companion object {
        private const val TAG = "TerrainLocalHttpServer"
        private const val SOCKET_READ_TIMEOUT = 5000

        private val TRANSPARENT_TILE_BYTES = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
            Base64.DEFAULT,
        )
    }
}