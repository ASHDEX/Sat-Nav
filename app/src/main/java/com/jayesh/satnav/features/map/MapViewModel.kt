package com.jayesh.satnav.features.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jayesh.satnav.domain.model.NavigationState
import com.jayesh.satnav.domain.model.OfflineDataset
import com.jayesh.satnav.domain.model.OfflineMapLoadStatus
import com.jayesh.satnav.domain.model.OfflineMapPackage
import com.jayesh.satnav.domain.model.RouteCoordinate
import com.jayesh.satnav.domain.usecase.GetOfflineReadinessUseCase
import com.jayesh.satnav.domain.usecase.ImportOfflineMapUseCase
import com.jayesh.satnav.domain.usecase.ObserveOfflineMapStateUseCase
import com.jayesh.satnav.domain.usecase.RefreshOfflineMapUseCase
import com.jayesh.satnav.features.navigation.NavigationCameraController
import com.jayesh.satnav.features.navigation.NavigationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.jayesh.satnav.features.location.GpsLocationManager

data class MapCameraState(
    val latitude: Double = 20.5937,
    val longitude: Double = 78.9629,
    val zoom: Double = 3.5,
    val bearing: Double? = null,
)

data class MapUiState(
    val totalDatasets: Int = 0,
    val availableDatasets: Int = 0,
    val missingDatasets: List<OfflineDataset> = emptyList(),
    val offlineLoadStatus: OfflineMapLoadStatus = OfflineMapLoadStatus.Missing,
    val offlineStyleUrl: String? = null,
    val offlinePackage: OfflineMapPackage? = null,
    val debugMessage: String = "Searching for offline MBTiles…",
    val errorMessage: String? = null,
    val camera: MapCameraState = MapCameraState(),
    val cameraMoveRequestId: Long = 0L,
    val importInProgress: Boolean = false,
    val userPosition: UserLocation? = null,
    val searchPinPosition: UserLocation? = null,
    val cameraMode: NavigationCameraController.CameraMode = NavigationCameraController.CameraMode.FOLLOW_MODE,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val getOfflineReadinessUseCase: GetOfflineReadinessUseCase,
    private val observeOfflineMapStateUseCase: ObserveOfflineMapStateUseCase,
    private val importOfflineMapUseCase: ImportOfflineMapUseCase,
    private val refreshOfflineMapUseCase: RefreshOfflineMapUseCase,
    private val navigationEngine: NavigationEngine,
    private val gpsLocationManager: GpsLocationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val cameraController = NavigationCameraController()
    private var lastAutoCenteredPackagePath: String? = null

    init {
        observeOfflineMap()
        observeNavigation()
        observeLocation() // Add live location observation
        refresh()
        refreshOfflineMap()
    }

    fun refresh() {
        viewModelScope.launch {
            val readiness = getOfflineReadinessUseCase()
            _uiState.update {
                it.copy(
                    totalDatasets = readiness.totalDatasets,
                    availableDatasets = readiness.availableDatasets,
                    missingDatasets = readiness.missingDatasets,
                )
            }
        }
    }

    fun refreshOfflineMap() {
        viewModelScope.launch {
            refreshOfflineMapUseCase()
        }
    }

    fun importMbtiles(uriString: String) {
        viewModelScope.launch {
            importOfflineMapUseCase(uriString)
            refresh()
        }
    }

    fun onMapReady(camera: MapCameraState) {
        _uiState.update {
            val shouldKeepRequestedCamera = it.camera != MapCameraState() && camera == MapCameraState()
            it.copy(
                camera = if (shouldKeepRequestedCamera) it.camera else camera,
                errorMessage = null,
            )
        }
    }

    fun onMapLoadFailed(error: String) {
        _uiState.update { it.copy(errorMessage = error) }
    }

    fun pinSearchLocation(lat: Double, lon: Double) {
        setCameraFreeMode()
        
        _uiState.update {
            it.copy(
                searchPinPosition = UserLocation(latitude = lat, longitude = lon),
                cameraMoveRequestId = System.currentTimeMillis(),
                camera = it.camera.copy(
                    latitude = lat,
                    longitude = lon,
                    zoom = 15.0 // Zoom in closer for a search result
                )
            )
        }
    }

    fun onCameraChanged(camera: MapCameraState) {
        _uiState.update { it.copy(camera = camera) }
    }

    fun centerCameraOnPackage() {
        val packageCenter = uiState.value.offlinePackage?.center.orEmpty()
        val current = uiState.value.camera
        val nextCamera = if (packageCenter.size >= 3) {
            MapCameraState(
                longitude = packageCenter[0],
                latitude = packageCenter[1],
                zoom = packageCenter[2],
            )
        } else {
            current.copy(zoom = current.zoom.coerceAtLeast(3.5))
        }

        _uiState.update {
            it.copy(
                camera = nextCamera,
                cameraMoveRequestId = it.cameraMoveRequestId + 1L,
            )
        }
    }

    private fun observeNavigation() {
        viewModelScope.launch {
            navigationEngine.state.collect { navState ->
                // Update camera if we have an active navigation state
                if (navState is NavigationState.Active) {
                    val cameraState = cameraController.updateCamera(
                        latitude = navState.snappedLatitude,
                        longitude = navState.snappedLongitude,
                        bearing = null
                    )
                    
                    if (cameraState != null) {
                        // Create UserLocation with bearing from camera state
                        val userLocation = UserLocation(
                            latitude = navState.snappedLatitude,
                            longitude = navState.snappedLongitude,
                            bearing = cameraState.bearing?.toFloat()
                        )
                        
                        _uiState.update {
                            it.copy(
                                userPosition = userLocation,
                                camera = cameraState,
                                cameraMoveRequestId = it.cameraMoveRequestId + 1L
                            )
                        }
                    } else {
                        // Camera state is null (e.g., not in follow mode), still update user position without bearing
                        val userLocation = UserLocation(
                            latitude = navState.snappedLatitude,
                            longitude = navState.snappedLongitude,
                            bearing = null
                        )
                        _uiState.update { it.copy(userPosition = userLocation) }
                    }
                } else if (navState is NavigationState.Idle || navState is NavigationState.Arrived) {
                    // When navigation is idle or arrived, clearing state is handled by observeLocation if needed,
                    // but we ensure we don't accidentally keep a stale ACTIVE position here.
                }
            }
        }
    }

    private fun observeLocation() {
        viewModelScope.launch {
            gpsLocationManager.locationUpdates.collect { location ->
                // Only update userPosition from raw GPS if navigation is NOT active
                val currentState = navigationEngine.state.value
                if (currentState !is NavigationState.Active) {
                    val userLocation = UserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        bearing = if (location.hasBearing()) location.bearing else null
                    )
                    
                    _uiState.update { it.copy(userPosition = userLocation) }

                    // If this is our first ever location fix in this session, center the map
                    if (uiState.value.camera == MapCameraState()) {
                        updateCameraManually(location.latitude, location.longitude)
                    }
                }
            }
        }
    }

    private fun observeOfflineMap() {
        viewModelScope.launch {
            observeOfflineMapStateUseCase().collect { offlineState ->
                _uiState.update {
                    it.copy(
                        offlineLoadStatus = offlineState.loadStatus,
                        offlineStyleUrl = offlineState.styleUrl,
                        offlinePackage = offlineState.mapPackage,
                        debugMessage = offlineState.debugMessage,
                        errorMessage = offlineState.errorMessage,
                        importInProgress = offlineState.importInProgress,
                    )
                }

                if (offlineState.loadStatus == OfflineMapLoadStatus.Ready) {
                    maybeCenterCameraOnLoadedPackage(offlineState.mapPackage)
                } else {
                    lastAutoCenteredPackagePath = null
                }
            }
        }
    }

    private fun maybeCenterCameraOnLoadedPackage(mapPackage: OfflineMapPackage?) {
        if (mapPackage == null) return
        if (mapPackage.absolutePath == lastAutoCenteredPackagePath) return

        val packageCenter = mapPackage.center
        if (packageCenter.size < 3) return

        val currentCamera = uiState.value.camera
        val isAtDefaultCamera = currentCamera == MapCameraState()
        if (!isAtDefaultCamera) return

        lastAutoCenteredPackagePath = mapPackage.absolutePath
        _uiState.update {
            it.copy(
                camera = MapCameraState(
                    longitude = packageCenter[0],
                    latitude = packageCenter[1],
                    zoom = packageCenter[2],
                ),
                cameraMoveRequestId = it.cameraMoveRequestId + 1L,
            )
        }
    }

    /**
     * Switch camera to follow mode (map follows user automatically).
     */
    fun setCameraFollowMode() {
        cameraController.setMode(NavigationCameraController.CameraMode.FOLLOW_MODE)
        _uiState.update { it.copy(cameraMode = NavigationCameraController.CameraMode.FOLLOW_MODE) }
    }

    /**
     * Switch camera to free mode (user manually controls the map).
     */
    fun setCameraFreeMode() {
        cameraController.setMode(NavigationCameraController.CameraMode.FREE_MODE)
        _uiState.update { it.copy(cameraMode = NavigationCameraController.CameraMode.FREE_MODE) }
    }

    /**
     * Toggle between follow and free camera modes.
     * @return The new camera mode after toggling
     */
    fun toggleCameraMode(): NavigationCameraController.CameraMode {
        val newMode = cameraController.toggleMode()
        _uiState.update { it.copy(cameraMode = newMode) }
        return newMode
    }

    /**
     * Get current camera mode.
     */
    fun getCameraMode(): NavigationCameraController.CameraMode {
        return cameraController.getMode()
    }

    /**
     * Reset camera controller state (e.g., when navigation stops).
     */
    fun resetCameraController() {
        cameraController.reset()
    }

    /**
     * Manually update camera position (for testing or manual control).
     */
    fun updateCameraManually(latitude: Double, longitude: Double, bearing: Float? = null) {
        val cameraState = cameraController.updateCamera(latitude, longitude, bearing)
        if (cameraState != null) {
            _uiState.update {
                it.copy(
                    camera = cameraState,
                    cameraMoveRequestId = it.cameraMoveRequestId + 1L
                )
            }
        }
    }
}
