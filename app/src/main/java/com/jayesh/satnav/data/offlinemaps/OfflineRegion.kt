package com.jayesh.satnav.data.offlinemaps

import com.jayesh.satnav.core.utils.LatLngBounds

/**
 * Represents a single offline map region stored as an MBTiles file.
 *
 * @property id Unique identifier (filename without extension)
 * @property name Display name (from metadata.name or derived from id)
 * @property filePath Absolute path to the .mbtiles file
 * @property sizeBytes File size in bytes
 * @property bounds Geographic bounds from MBTiles metadata "bounds" key, null if missing
 * @property minZoom Minimum zoom level from metadata, null if missing
 * @property maxZoom Maximum zoom level from metadata, null if missing
 * @property installedAt Timestamp when file was last modified (milliseconds since epoch)
 */
data class OfflineRegion(
    val id: String,
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val bounds: LatLngBounds?,
    val minZoom: Int?,
    val maxZoom: Int?,
    val installedAt: Long,
)