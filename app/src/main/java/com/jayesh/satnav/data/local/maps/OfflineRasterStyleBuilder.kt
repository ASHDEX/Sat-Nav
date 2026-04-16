package com.jayesh.satnav.data.local.maps

import com.jayesh.satnav.domain.model.OfflineMapPackage
import java.io.InputStream

class OfflineRasterStyleBuilder(
    private val templateLoader: () -> InputStream,
) : OfflineStyleBuilder {

    override fun build(tilesUrl: String, mapPackage: OfflineMapPackage): String {
        return templateLoader()
            .bufferedReader()
            .use { it.readText() }
            .replace("{{STYLE_NAME}}", escapeJson(mapPackage.fileName))
            .replace("{{TILES_URL}}", escapeJson("$tilesUrl/{z}/{x}/{y}"))
            .replace("{{MIN_ZOOM}}", mapPackage.minZoom.toString())
            .replace("{{MAX_ZOOM}}", mapPackage.maxZoom.toString())
            .replace("{{TILE_SIZE}}", mapPackage.tileSize.toString())
            .replace("{{SCHEME}}", mapPackage.scheme.name.lowercase())
            .replace("{{ATTRIBUTION}}", escapeJson(mapPackage.attribution ?: "Offline MBTiles"))
            .replace("{{BOUNDS}}", boundsJson(mapPackage.bounds))
    }

    override fun buildTileJson(tilesUrl: String, mapPackage: OfflineMapPackage): String {
        return """
            {
              "tilejson": "3.0.0",
              "name": "${escapeJson(mapPackage.fileName)}",
              "tiles": ["${escapeJson("$tilesUrl/{z}/{x}/{y}")}"],
              "minzoom": ${mapPackage.minZoom},
              "maxzoom": ${mapPackage.maxZoom},
              "scheme": "${mapPackage.scheme.name.lowercase()}",
              "bounds": ${boundsJson(mapPackage.bounds)}
            }
        """.trimIndent()
    }

    private fun boundsJson(bounds: List<Double>): String {
        return if (bounds.size == 4) {
            bounds.joinToString(prefix = "[", postfix = "]")
        } else {
            "[-180.0, -85.05112878, 180.0, 85.05112878]"
        }
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
