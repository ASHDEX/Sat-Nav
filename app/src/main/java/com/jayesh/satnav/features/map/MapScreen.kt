package com.jayesh.satnav.features.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val defaultCamera = MapCameraState(
        latitude = 0.0,
        longitude = 0.0,
        zoom = 1.0,
        bearing = null
    )

    MapLibreMapView(
        onMapReady = { /* No-op */ },
        onMapLoadFailed = { /* No-op */ },
        onCameraChanged = { /* No-op */ },
        onMapMovedManually = null,
        cameraMoveRequestId = 0L,
        fitRouteRequestId = 0L,
        targetCamera = defaultCamera,
        styleUrl = "https://demotiles.maplibre.org/style.json",
        routePoints = emptyList(),
        userPosition = null,
        searchPin = null,
        modifier = modifier
    )
}
