package com.jayesh.satnav.data.local.maps

import com.jayesh.satnav.domain.model.OfflineMapPackage
import com.jayesh.satnav.domain.model.OfflineTileFormat
import com.jayesh.satnav.domain.model.OfflineTileScheme
import java.io.ByteArrayInputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRasterStyleBuilderTest {

    @Test
    fun `build injects local tile metadata into style json`() {
        val builder = OfflineRasterStyleBuilder {
            ByteArrayInputStream(
                """
                {
                  "sources": {
                    "offline-raster": {
                      "tiles": ["{{TILES_URL}}"],
                      "scheme": "{{SCHEME}}",
                      "minzoom": {{MIN_ZOOM}},
                      "maxzoom": {{MAX_ZOOM}},
                      "bounds": {{BOUNDS}}
                    }
                  }
                }
                """.trimIndent().toByteArray(),
            )
        }

        val style = builder.build(
            tilesUrl = "http://127.0.0.1:38765/tiles",
            mapPackage = OfflineMapPackage(
                fileName = "sample",
                absolutePath = "/tmp/map.mbtiles",
                format = OfflineTileFormat.RasterPng,
                scheme = OfflineTileScheme.TMS,
                minZoom = 2,
                maxZoom = 14,
                bounds = listOf(1.0, 2.0, 3.0, 4.0),
            ),
        )

        assertTrue(style.contains("http://127.0.0.1:38765/tiles/{z}/{x}/{y}"))
        assertTrue(style.contains("\"scheme\": \"tms\""))
        assertTrue(style.contains("\"minzoom\": 2"))
        assertTrue(style.contains("[1.0, 2.0, 3.0, 4.0]"))
    }
}

