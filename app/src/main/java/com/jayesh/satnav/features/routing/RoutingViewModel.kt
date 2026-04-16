package com.jayesh.satnav.features.routing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jayesh.satnav.domain.model.OfflineMapLoadStatus
import com.jayesh.satnav.domain.model.OfflineRoute
import com.jayesh.satnav.domain.model.RoutingProfile
import com.jayesh.satnav.domain.model.Waypoint
import com.jayesh.satnav.domain.usecase.GetRoutingEngineStatusUseCase
import com.jayesh.satnav.domain.usecase.ObserveOfflineMapStateUseCase
import com.jayesh.satnav.domain.usecase.RefreshOfflineMapUseCase
import com.jayesh.satnav.features.map.MapCameraState
import com.jayesh.satnav.features.navigation.NavigationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WaypointInput(
    val id: Long,
    val latitude: String,
    val longitude: String,
)

data class RoutingUiState(
    val waypoints: List<WaypointInput> = listOf(
        WaypointInput(id = 1L, latitude = "12.97160", longitude = "77.59460"),
        WaypointInput(id = 2L, latitude = "12.93520", longitude = "77.62450"),
        WaypointInput(id = 3L, latitude = "12.92600", longitude = "77.67620"),
    ),
    val selectedProfile: RoutingProfile = RoutingProfile.CAR,
    val routingEngineReady: Boolean = false,
    val graphDirectory: String? = null,
    val availableProfiles: List<String> = emptyList(),
    val routingDebugMessage: String = "Searching for graph-cache…",
    val offlineStyleUrl: String? = null,
    val offlineMapLoadStatus: OfflineMapLoadStatus = OfflineMapLoadStatus.Missing,
    val route: OfflineRoute? = null,
    val routeError: String? = null,
    val isRouting: Boolean = false,
    val navigationReady: Boolean = false,
    val camera: MapCameraState = MapCameraState(
        latitude = 12.97160,
        longitude = 77.59460,
        zoom = 11.5,
    ),
    val cameraMoveRequestId: Long = 0L,
    val fitRouteRequestId: Long = 0L,
    val nextWaypointId: Long = 4L,
)

@HiltViewModel
class RoutingViewModel @Inject constructor(
    private val observeOfflineMapStateUseCase: ObserveOfflineMapStateUseCase,
    private val refreshOfflineMapUseCase: RefreshOfflineMapUseCase,
    private val getRoutingEngineStatusUseCase: GetRoutingEngineStatusUseCase,
    private val multiRouteManager: MultiRouteManager,
    private val navigationEngine: NavigationEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutingUiState())
    val uiState: StateFlow<RoutingUiState> = _uiState.asStateFlow()

    init {
        observeOfflineMap()
        refreshOfflineMap()
        refreshRoutingEngine()
    }

    fun addWaypoint() {
        _uiState.update { state ->
            state.copy(
                waypoints = state.waypoints + WaypointInput(
                    id = state.nextWaypointId,
                    latitude = "",
                    longitude = "",
                ),
                nextWaypointId = state.nextWaypointId + 1L,
            )
        }
    }

    fun removeWaypoint(id: Long) {
        _uiState.update { state ->
            if (state.waypoints.size <= 2) {
                state
            } else {
                state.copy(
                    waypoints = state.waypoints.filterNot { it.id == id },
                )
            }
        }
    }

    fun updateWaypointLatitude(id: Long, value: String) {
        _uiState.update { state ->
            state.copy(
                waypoints = state.waypoints.map { waypoint ->
                    if (waypoint.id == id) waypoint.copy(latitude = value) else waypoint
                },
            )
        }
    }

    fun updateWaypointLongitude(id: Long, value: String) {
        _uiState.update { state ->
            state.copy(
                waypoints = state.waypoints.map { waypoint ->
                    if (waypoint.id == id) waypoint.copy(longitude = value) else waypoint
                },
            )
        }
    }

    fun updateSelectedProfile(profile: RoutingProfile) {
        _uiState.update { it.copy(selectedProfile = profile) }
    }

    fun refreshRoutingEngine() {
        viewModelScope.launch {
            val status = getRoutingEngineStatusUseCase()
            _uiState.update {
                val supportedProfiles = status.availableProfiles
                    .mapNotNull(RoutingProfile::fromProfileName)
                    .ifEmpty { RoutingProfile.entries }
                val selectedProfile = it.selectedProfile.takeIf(supportedProfiles::contains)
                    ?: supportedProfiles.firstOrNull()
                    ?: RoutingProfile.default()
                it.copy(
                    selectedProfile = selectedProfile,
                    routingEngineReady = status.isLoaded,
                    graphDirectory = status.graphDirectory,
                    availableProfiles = status.availableProfiles,
                    routingDebugMessage = status.errorMessage ?: status.debugMessage,
                    routeError = if (status.isLoaded) it.routeError else status.errorMessage,
                )
            }
        }
    }

    fun refreshOfflineMap() {
        viewModelScope.launch {
            refreshOfflineMapUseCase()
        }
    }

    fun computeRoute() {
        val state = uiState.value
        val waypoints = state.waypoints.mapIndexedNotNull { index, waypoint ->
            val latitude = waypoint.latitude.toDoubleOrNull()
            val longitude = waypoint.longitude.toDoubleOrNull()
            if (latitude == null || longitude == null) {
                null
            } else {
                Waypoint(lat = latitude, lon = longitude)
            }
        }
        if (waypoints.size != state.waypoints.size) {
            _uiState.update {
                it.copy(routeError = "Enter valid decimal coordinates for every waypoint.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRouting = true, routeError = null) }
            multiRouteManager.computeRoute(
                waypoints = waypoints,
                profile = uiState.value.selectedProfile,
            ).fold(
                onSuccess = { route ->
                    navigationEngine.prepareRoute(route)
                    _uiState.update {
                        it.copy(
                            route = route,
                            isRouting = false,
                            routeError = null,
                            navigationReady = true,
                            routingDebugMessage = "Merged ${route.segmentCount} segments across ${route.waypointCount} waypoints in ${route.computationTimeMillis} ms",
                            fitRouteRequestId = it.fitRouteRequestId + 1L,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRouting = false,
                            route = null,
                            routeError = error.message ?: "Route computation failed.",
                        )
                    }
                },
            )
        }
    }

    fun onMapReady(camera: MapCameraState) {
        _uiState.update { it.copy(camera = camera, routeError = null) }
    }

    fun onMapLoadFailed(message: String) {
        _uiState.update { it.copy(routeError = message) }
    }

    fun onCameraChanged(camera: MapCameraState) {
        _uiState.update { it.copy(camera = camera) }
    }

    fun centerOnRouteStart() {
        val firstPoint = uiState.value.route?.points?.firstOrNull() ?: return
        _uiState.update {
            it.copy(
                camera = MapCameraState(
                    latitude = firstPoint.latitude,
                    longitude = firstPoint.longitude,
                    zoom = it.camera.zoom.coerceAtLeast(12.0),
                ),
                cameraMoveRequestId = it.cameraMoveRequestId + 1L,
            )
        }
    }

    private fun observeOfflineMap() {
        viewModelScope.launch {
            observeOfflineMapStateUseCase().collect { offlineState ->
                _uiState.update {
                    it.copy(
                        offlineStyleUrl = offlineState.styleUrl,
                        offlineMapLoadStatus = offlineState.loadStatus,
                    )
                }
            }
        }
    }
}
