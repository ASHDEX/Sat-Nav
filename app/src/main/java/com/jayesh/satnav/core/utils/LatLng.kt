package com.jayesh.satnav.core.utils

import kotlinx.serialization.Serializable
import org.maplibre.android.geometry.LatLng as MapLibreLatLng

/**
 * Simple latitude/longitude wrapper that can be converted to MapLibre's LatLng.
 */
@Serializable
data class LatLng(
    val latitude: Double,
    val longitude: Double,
) {
    fun toMapLibreLatLng(): MapLibreLatLng = MapLibreLatLng(latitude, longitude)
}

/**
 * Bounding box defined by southwest and northeast corners.
 */
@Serializable
data class LatLngBounds(
    val southwest: LatLng,
    val northeast: LatLng,
) {
    fun toMapLibreLatLngBounds(): org.maplibre.android.geometry.LatLngBounds =
        org.maplibre.android.geometry.LatLngBounds.Builder()
            .include(southwest.toMapLibreLatLng())
            .include(northeast.toMapLibreLatLng())
            .build()
}