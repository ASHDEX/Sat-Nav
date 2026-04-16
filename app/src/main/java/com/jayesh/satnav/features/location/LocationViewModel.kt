package com.jayesh.satnav.features.location

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocationUiState(
    val permissionGranted: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val speedMps: Float? = null,
    val fixCount: Int = 0,
    val statusMessage: String = "Waiting for location permission…",
)

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val gpsLocationManager: GpsLocationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    private var collectionJob: Job? = null

    /**
     * Whether the screen is currently in the STARTED lifecycle state.
     * GPS collection only runs while true.
     */
    private var isLifecycleStarted = false

    init {
        if (gpsLocationManager.isPermissionGranted()) {
            _uiState.update { it.copy(permissionGranted = true, statusMessage = "Acquiring GPS fix…") }
        }
    }

    // ── Lifecycle events (called by LocationScreen via DisposableEffect) ───────

    /** Called when the screen reaches the STARTED state (onStart equivalent). */
    fun onLifecycleStart() {
        isLifecycleStarted = true
        if (gpsLocationManager.isPermissionGranted()) {
            startCollecting()
        }
    }

    /** Called when the screen drops below STARTED (onStop equivalent). Cancels GPS. */
    fun onLifecycleStop() {
        isLifecycleStarted = false
        stopCollecting()
    }

    // ── Permission callbacks ──────────────────────────────────────────────────

    fun onPermissionGranted() {
        _uiState.update { it.copy(permissionGranted = true, statusMessage = "Acquiring GPS fix…") }
        if (isLifecycleStarted) {
            startCollecting()
        }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                permissionGranted = false,
                statusMessage = "Location permission denied. Grant it to enable GPS tracking.",
            )
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun startCollecting() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            gpsLocationManager.locationUpdates.collect { location ->
                _uiState.update { current ->
                    current.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy,
                        speedMps = if (location.hasSpeed()) location.speed else null,
                        fixCount = current.fixCount + 1,
                        statusMessage = "",
                    )
                }
            }
            // Upstream closed: permission was not granted when shareIn started
            _uiState.update {
                it.copy(statusMessage = "No GPS updates — check location permission.")
            }
        }
    }

    private fun stopCollecting() {
        collectionJob?.cancel()
        collectionJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopCollecting()
    }
}
