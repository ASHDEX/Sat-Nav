package com.jayesh.satnav.features.map

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jayesh.satnav.domain.model.RouteCoordinate
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconColor
import org.maplibre.android.style.layers.PropertyFactory.iconHaloColor
import org.maplibre.android.style.layers.PropertyFactory.iconHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private val DefaultLatLng = LatLng(20.5937, 78.9629)

private val DefaultCameraPosition = CameraPosition.Builder()
    .target(DefaultLatLng)
    .zoom(3.5)
    .build()

/**
 * Represents user location with optional bearing (direction of travel).
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float? = null
)

@Composable
fun MapLibreMapView(
    onMapReady: (MapCameraState) -> Unit,
    onMapLoadFailed: (String) -> Unit,
    onCameraChanged: (MapCameraState) -> Unit,
    onMapMovedManually: (() -> Unit)? = null,
    cameraMoveRequestId: Long,
    fitRouteRequestId: Long = 0L,
    targetCamera: MapCameraState,
    styleUrl: String?,
    routePoints: List<RouteCoordinate> = emptyList(),
    userPosition: UserLocation? = null,
    searchPin: UserLocation? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var lastHandledCameraMoveRequestId by remember { mutableLongStateOf(0L) }
    var lastHandledFitRouteRequestId by remember { mutableLongStateOf(0L) }
    var lastLoadedStyleUrl by remember { mutableStateOf<String?>(null) }
    var lastRenderedRouteSignature by remember { mutableIntStateOf(0) }
    var lastRenderedUserPosition by remember { mutableStateOf<UserLocation?>(null) }
    var lastRenderedSearchPin by remember { mutableStateOf<UserLocation?>(null) }
    val mapView = remember {
        MapView(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = mapView.lifecycleObserver()
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        mapView.getMapAsync { map: MapLibreMap ->
            mapLibreMap = map

            map.addOnCameraMoveStartedListener { reason: Int ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && onMapMovedManually != null) {
                    onMapMovedManually()
                }
            }

            map.addOnCameraIdleListener {
                onCameraChanged(map.cameraPosition.toCameraState())
            }
        }

        onDispose {
            mapLibreMap = null
        }
    }

    AndroidView<MapView>(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            val activeMap = mapLibreMap
            if (activeMap != null && styleUrl != null && styleUrl != lastLoadedStyleUrl) {
                lastLoadedStyleUrl = styleUrl
                activeMap.setStyle(Style.Builder().fromUri(styleUrl)) { loadedStyle ->
                    val cameraPosition = if (targetCamera == MapCameraState()) {
                        DefaultCameraPosition
                    } else {
                        CameraPosition.Builder()
                            .target(LatLng(targetCamera.latitude, targetCamera.longitude))
                            .zoom(targetCamera.zoom)
                            .apply {
                                targetCamera.bearing?.let { bearing(it) }
                            }
                            .build()
                    }
                    loadedStyle.renderRouteOverlay(routePoints)
                    lastRenderedRouteSignature = routePoints.hashCode()
                    loadedStyle.renderUserLocationOverlay(userPosition)
                    lastRenderedUserPosition = userPosition
                    loadedStyle.renderSearchPinOverlay(searchPin)
                    lastRenderedSearchPin = searchPin
                    activeMap.cameraPosition = cameraPosition
                    onMapReady(activeMap.cameraPosition.toCameraState())
                }
            }

            activeMap?.getStyle { loadedStyle ->
                val routeSignature = routePoints.hashCode()
                if (routeSignature != lastRenderedRouteSignature) {
                    loadedStyle.renderRouteOverlay(routePoints)
                    lastRenderedRouteSignature = routeSignature
                }
                if (userPosition != lastRenderedUserPosition) {
                    loadedStyle.renderUserLocationOverlay(userPosition)
                    lastRenderedUserPosition = userPosition
                }
                if (searchPin != lastRenderedSearchPin) {
                    loadedStyle.renderSearchPinOverlay(searchPin)
                    lastRenderedSearchPin = searchPin
                }
            }

            if (cameraMoveRequestId > lastHandledCameraMoveRequestId) {
                lastHandledCameraMoveRequestId = cameraMoveRequestId
                activeMap?.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(targetCamera.latitude, targetCamera.longitude))
                            .zoom(targetCamera.zoom)
                            .apply {
                                targetCamera.bearing?.let { bearing(it) }
                            }
                            .build()
                    ),
                    CAMERA_ANIMATION_DURATION_MS
                )
            }

            if (fitRouteRequestId > lastHandledFitRouteRequestId && routePoints.isNotEmpty()) {
                lastHandledFitRouteRequestId = fitRouteRequestId
                routePoints.toLatLngBounds()?.let { bounds ->
                    activeMap?.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            bounds,
                            ROUTE_CAMERA_PADDING
                        ),
                        CAMERA_ANIMATION_DURATION_MS
                    )
                }
            }
        }
    )
}

private fun CameraPosition.toCameraState(): MapCameraState {
    return MapCameraState(
        latitude = target?.latitude ?: 0.0,
        longitude = target?.longitude ?: 0.0,
        zoom = zoom,
        bearing = bearing
    )
}

private fun MapView.lifecycleObserver(): DefaultLifecycleObserver {
    return object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            this@lifecycleObserver.onStart()
        }

        override fun onStop(owner: LifecycleOwner) {
            this@lifecycleObserver.onStop()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            this@lifecycleObserver.onDestroy()
        }
    }
}

private fun Style.renderRouteOverlay(routePoints: List<RouteCoordinate>) {
    val source = getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
    if (routePoints.isEmpty()) {
        source?.setGeoJson(FeatureCollection.fromFeatures(arrayOf()))
        return
    }

    val featureCollection = routePoints.toFeatureCollection()
    if (source == null) {
        addSource(GeoJsonSource(ROUTE_SOURCE_ID, featureCollection))
    } else {
        source.setGeoJson(featureCollection)
    }

    // Casing layer (wider, semi-transparent background)
    if (getLayer(ROUTE_CASING_LAYER_ID) == null) {
        addLayer(
            LineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                lineColor("#FFFFFF"),
                lineWidth(7f),
                lineOpacity(0.5f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
    }

    // Main route layer
    if (getLayer(ROUTE_LAYER_ID) == null) {
        addLayer(
            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                lineColor("#007AFF"),
                lineWidth(4f),
                lineOpacity(0.9f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
    }
}

private fun Style.renderUserLocationOverlay(position: UserLocation?) {
    val source = getSourceAs<GeoJsonSource>(USER_LOCATION_SOURCE_ID)
    if (position == null) {
        source?.setGeoJson(FeatureCollection.fromFeatures(arrayOf()))
        return
    }

    // Create feature with bearing property if available
    val feature = org.maplibre.geojson.Feature.fromGeometry(
        Point.fromLngLat(position.longitude, position.latitude),
    )
    
    // Add bearing property to feature for symbol rotation
    position.bearing?.let { bearing ->
        feature.addNumberProperty("bearing", bearing.toDouble())
    }
    
    if (source == null) {
        addSource(GeoJsonSource(USER_LOCATION_SOURCE_ID, feature))
    } else {
        source.setGeoJson(feature)
    }

    // Remove old circle layer if it exists (from previous implementation)
    getLayer(USER_LOCATION_LAYER_ID)?.let { removeLayer(it) }

    // Add symbol layer for arrow/directional marker
    if (getLayer(USER_LOCATION_ARROW_LAYER_ID) == null) {
        // Use default marker icon (MapLibre provides a default marker)
        addLayer(
            SymbolLayer(USER_LOCATION_ARROW_LAYER_ID, USER_LOCATION_SOURCE_ID).withProperties(
                iconImage("marker-15"),  // Default MapLibre marker
                iconSize(1.0f),
                iconColor("#007AFF"),  // Blue color
                iconHaloColor("#FFFFFF"),  // White halo for visibility
                iconHaloWidth(1.0f),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                // Rotate icon based on bearing property if available
                iconRotate(
                    position.bearing?.let { it } ?: 0f
                )
            )
        )
    } else {
        // Update the existing layer's rotation
        getLayer(USER_LOCATION_ARROW_LAYER_ID)?.let { layer ->
            (layer as SymbolLayer).setProperties(
                iconRotate(
                    position.bearing?.let { it } ?: 0f
                )
            )
        }
    }

    // Add shadow/background circle for better visibility
    if (getLayer(USER_LOCATION_SHADOW_LAYER_ID) == null) {
        addLayer(
            CircleLayer(USER_LOCATION_SHADOW_LAYER_ID, USER_LOCATION_SOURCE_ID).withProperties(
                circleRadius(10f),
                circleColor("#000000"),
                circleOpacity(0.15f),
            ),
        )
    }
}

private fun Style.renderSearchPinOverlay(position: UserLocation?) {
    val source = getSourceAs<GeoJsonSource>(SEARCH_PIN_SOURCE_ID)
    if (position == null) {
        source?.setGeoJson(FeatureCollection.fromFeatures(arrayOf()))
        return
    }

    val feature = org.maplibre.geojson.Feature.fromGeometry(
        Point.fromLngLat(position.longitude, position.latitude),
    )
    
    if (source == null) {
        addSource(GeoJsonSource(SEARCH_PIN_SOURCE_ID, feature))
    } else {
        source.setGeoJson(feature)
    }

    if (getLayer(SEARCH_PIN_LAYER_ID) == null) {
        addLayer(
            SymbolLayer(SEARCH_PIN_LAYER_ID, SEARCH_PIN_SOURCE_ID).withProperties(
                iconImage("marker-15"),
                iconSize(1.5f),
                iconColor("#FF3B30"), // Red Pin for Search
                iconHaloColor("#FFFFFF"),
                iconHaloWidth(1.5f),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
        )
    }
}

private fun List<RouteCoordinate>.toFeatureCollection(): FeatureCollection {
    if (size < 2) {
        return FeatureCollection.fromFeatures(arrayOf())
    }

    return FeatureCollection.fromFeature(
        org.maplibre.geojson.Feature.fromGeometry(
            LineString.fromLngLats(
                map { coordinate ->
                    Point.fromLngLat(coordinate.longitude, coordinate.latitude)
                },
            ),
        ),
    )
}

private fun List<RouteCoordinate>.toLatLngBounds(): LatLngBounds? {
    if (isEmpty()) return null

    val first = first()
    var minLat = first.latitude
    var maxLat = first.latitude
    var minLon = first.longitude
    var maxLon = first.longitude

    for (coord in this) {
        minLat = minOf(minLat, coord.latitude)
        maxLat = maxOf(maxLat, coord.latitude)
        minLon = minOf(minLon, coord.longitude)
        maxLon = maxOf(maxLon, coord.longitude)
    }

    return LatLngBounds.Builder()
        .include(LatLng(minLat, minLon))
        .include(LatLng(maxLat, maxLon))
        .build()
}

private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_LAYER_ID = "route-layer"
private const val ROUTE_CASING_LAYER_ID = "route-casing-layer"
private const val USER_LOCATION_SOURCE_ID = "user-location-source"
private const val USER_LOCATION_LAYER_ID = "user-location-layer"  // Old circle layer ID
private const val USER_LOCATION_SHADOW_LAYER_ID = "user-location-shadow-layer"
private const val USER_LOCATION_ARROW_LAYER_ID = "user-location-arrow-layer"
private const val SEARCH_PIN_SOURCE_ID = "search-pin-source"
private const val SEARCH_PIN_LAYER_ID = "search-pin-layer"
private const val CAMERA_ANIMATION_DURATION_MS = 600
private const val ROUTE_CAMERA_PADDING = 100
