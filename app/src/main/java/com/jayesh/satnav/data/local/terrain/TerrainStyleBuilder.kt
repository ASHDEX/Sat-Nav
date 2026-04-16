package com.jayesh.satnav.data.local.terrain

import com.jayesh.satnav.data.local.maps.OfflineStyleBuilder
import com.jayesh.satnav.domain.model.OfflineMapPackage
import java.io.InputStream

class TerrainStyleBuilder(
    private val templateLoader: () -> InputStream,
) : OfflineStyleBuilder {

    override fun build(tilesUrl: String, mapPackage: OfflineMapPackage): String {
        return templateLoader()
            .bufferedReader()
            .use { it.readText() }
            .replace("{{STYLE_NAME}}", escapeJson("${mapPackage.fileName} Terrain"))
            .replace("{{DEM_TILES_URL}}", escapeJson("$tilesUrl/dem/{z}/{x}/{y}"))
            .replace("{{HILLSHADE_TILES_URL}}", escapeJson("$tilesUrl/hillshade/{z}/{x}/{y}"))
            .replace("{{MIN_ZOOM}}", mapPackage.minZoom.toString())
            .replace("{{MAX_ZOOM}}", mapPackage.maxZoom.toString())
            .replace("{{TILE_SIZE}}", mapPackage.tileSize.toString())
            .replace("{{SCHEME}}", mapPackage.scheme.name.lowercase())
            .replace("{{BOUNDS}}", boundsJson(mapPackage.bounds))
    }

    override fun buildTileJson(tilesUrl: String, mapPackage: OfflineMapPackage): String {
        return """
            {
              "tilejson": "3.0.0",
              "name": "${escapeJson("${mapPackage.fileName} Terrain")}",
              "tiles": [
                "${escapeJson("$tilesUrl/dem/{z}/{x}/{y}")}",
                "${escapeJson("$tilesUrl/hillshade/{z}/{x}/{y}")}"
              ],
              "minzoom": ${mapPackage.minZoom},
              "maxzoom": ${mapPackage.maxZoom},
              "scheme": "${mapPackage.scheme.name.lowercase()}",
              "bounds": ${boundsJson(mapPackage.bounds)},
              "type": "raster-dem",
              "encoding": "terrarium"
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