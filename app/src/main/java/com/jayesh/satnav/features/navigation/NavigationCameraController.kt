package com.jayesh.satnav.features.navigation

import android.location.Location
import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.features.map.MapCameraState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Controls camera behavior during navigation with two modes:
 * 1. FOLLOW_MODE: Camera follows user with smooth animation and rotation
 * 2. FREE_MODE: User manually controls the map
 *
 * Features:
 * - Smooth camera movement with animation
 * - Heading/bearing calculation from GPS or position history
 * - Automatic mode switching
 * - Jitter prevention with position filtering
 * - Position interpolation for buttery-smooth motion (Phase 9+)
 */
class NavigationCameraController {

    enum class CameraMode {
        FOLLOW_MODE,  // Map follows user automatically
        FREE_MODE     // User manually controls the map
    }

    // Configuration
    companion object {
        // Default zoom levels
        private const val DEFAULT_ZOOM = 16.5
        private const val MIN_ZOOM = 14.0
        private const val MAX_ZOOM = 19.0
        
        // Animation duration in milliseconds (300-800ms range for smooth navigation)
        private const val CAMERA_ANIMATION_DURATION_MS = 600L
        
        // Minimum distance to trigger camera update (meters)
        private const val MIN_DISTANCE_FOR_UPDATE = 2.0
        
        // Minimum bearing change to trigger rotation (degrees)
        private const val MIN_BEARING_CHANGE = 5.0
        
        // Position history for bearing calculation
        private const val POSITION_HISTORY_SIZE = 5
        
        // Interpolation duration (Phase 9+)
        private const val INTERPOLATION_DURATION_MS = 500L
    }

    // State
    private var currentMode: CameraMode = CameraMode.FOLLOW_MODE
    private var lastCameraPosition: MapCameraState? = null
    private var lastLocation: Location? = null
    private var lastBearing: Float? = null
    
    // Position history for bearing calculation when GPS bearing is unavailable
    private val positionHistory = ArrayDeque<Pair<Double, Double>>(POSITION_HISTORY_SIZE)
    
    // Position interpolator for smooth motion (Phase 9+)
    private val positionInterpolator = PositionInterpolator()

    init {
        // Set up interpolator callback to forward interpolated positions
        positionInterpolator.onPositionUpdated = { lat, lon, bearing ->
            if (currentMode == CameraMode.FOLLOW_MODE) {
                val interpolatedCamera = MapCameraState(
                    latitude = lat,
                    longitude = lon,
                    zoom = DEFAULT_ZOOM,
                    bearing = bearing?.toDouble()
                )
                lastCameraPosition = interpolatedCamera
                
                // Notify listeners about interpolated position
                // This will be picked up by MapViewModel's observeNavigation()
                NavLog.interpolation("Interpolated position: lat=$lat, lon=$lon, bearing=$bearing")
            }
        }
    }

    /**
     * Update camera based on new location with interpolation.
     * Returns a new MapCameraState if camera should move, null otherwise.
     */
    fun updateCamera(location: Location): MapCameraState? {
        if (currentMode != CameraMode.FOLLOW_MODE) {
            return null
        }

        val lat = location.latitude
        val lon = location.longitude
        
        // Check if we should update based on distance
        if (!shouldUpdateCamera(lat, lon)) {
            return null
        }

        // Calculate bearing (direction of movement)
        val bearing = calculateBearing(location)
        
        // Update position history
        updatePositionHistory(lat, lon)
        
        // Use interpolator for smooth transition (Phase 9+)
        positionInterpolator.interpolateTo(lat, lon, bearing, INTERPOLATION_DURATION_MS)
        
        // Create new camera state based on interpolated position
        val (currentLat, currentLon, currentBearing) = positionInterpolator.getCurrentPosition()
        val newCamera = MapCameraState(
            latitude = currentLat,
            longitude = currentLon,
            zoom = DEFAULT_ZOOM,
            bearing = currentBearing?.toDouble()
        )
        
        lastLocation = location
        lastCameraPosition = newCamera
        
        NavLog.camera("Camera updated with interpolation: lat=$currentLat, lon=$currentLon, bearing=$currentBearing")
        return newCamera
    }

    /**
     * Update camera with snapped position from navigation engine.
     * This is the primary method called during navigation.
     */
    fun updateCamera(latitude: Double, longitude: Double, bearing: Float? = null): MapCameraState? {
        if (currentMode != CameraMode.FOLLOW_MODE) {
            return null
        }

        // Check if we should update based on distance
        if (!shouldUpdateCamera(latitude, longitude)) {
            return null
        }

        // Use provided bearing or calculate from position history
        val calculatedBearing = bearing ?: calculateBearingFromHistory(latitude, longitude)
        
        // Update position history
        updatePositionHistory(latitude, longitude)
        
        // Use interpolator for smooth transition (Phase 9+)
        positionInterpolator.interpolateTo(latitude, longitude, calculatedBearing, INTERPOLATION_DURATION_MS)
        
        // Create new camera state based on interpolated position
        val (currentLat, currentLon, currentBearing) = positionInterpolator.getCurrentPosition()
        val newCamera = MapCameraState(
            latitude = currentLat,
            longitude = currentLon,
            zoom = DEFAULT_ZOOM,
            bearing = currentBearing?.toDouble()
        )
        
        lastCameraPosition = newCamera
        
        NavLog.camera("Camera updated (snapped with interpolation): lat=$currentLat, lon=$currentLon, bearing=$currentBearing")
        return newCamera
    }

    /**
     * Switch between camera modes.
     */
    fun setMode(mode: CameraMode) {
        if (currentMode == mode) return
        
        currentMode = mode
        when (mode) {
            CameraMode.FOLLOW_MODE -> {
                // When switching to follow mode, reset position history and interpolator
                positionHistory.clear()
                positionInterpolator.reset()
                lastCameraPosition = null
                NavLog.camera("Switched to FOLLOW_MODE, reset interpolator")
            }
            CameraMode.FREE_MODE -> {
                // Stop any ongoing interpolation when switching to free mode
                positionInterpolator.stopAndJumpToTarget()
                NavLog.camera("Switched to FREE_MODE, stopped interpolation")
            }
        }
    }

    /**
     * Get current camera mode.
     */
    fun getMode(): CameraMode = currentMode

    /**
     * Toggle between follow and free modes.
     */
    fun toggleMode(): CameraMode {
        val newMode = when (currentMode) {
            CameraMode.FOLLOW_MODE -> CameraMode.FREE_MODE
            CameraMode.FREE_MODE -> CameraMode.FOLLOW_MODE
        }
        setMode(newMode)
        return newMode
    }

    /**
     * Check if camera should update based on distance from last position.
     */
    private fun shouldUpdateCamera(newLat: Double, newLon: Double): Boolean {
        val lastPos = lastCameraPosition ?: return true
        
        val distance = haversineDistance(
            lastPos.latitude, lastPos.longitude,
            newLat, newLon
        )
        
        return distance >= MIN_DISTANCE_FOR_UPDATE
    }

    /**
     * Calculate bearing from GPS location or position history.
     */
    private fun calculateBearing(location: Location): Float? {
        // Prefer GPS bearing if available and valid
        if (location.hasBearing() && location.bearing >= 0) {
            lastBearing = location.bearing
            return location.bearing
        }
        
        // Fall back to position history calculation
        return calculateBearingFromHistory(location.latitude, location.longitude)
    }

    /**
     * Calculate bearing from position history using last N positions.
     */
    private fun calculateBearingFromHistory(newLat: Double, newLon: Double): Float? {
        if (positionHistory.isEmpty()) {
            return lastBearing
        }

        // Use the oldest position in history for bearing calculation
        val (oldLat, oldLon) = positionHistory.first()
        
        // Calculate bearing between old and new position
        val bearing = calculateBearingBetween(oldLat, oldLon, newLat, newLon)
        lastBearing = bearing
        
        return bearing
    }

    /**
     * Calculate bearing between two points in degrees (0-360).
     */
    private fun calculateBearingBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δλ = Math.toRadians(lon2 - lon1)

        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        val θ = atan2(y, x)

        val bearing = Math.toDegrees(θ).toFloat()
        return (bearing + 360) % 360  // Normalize to 0-360
    }

    /**
     * Update position history, maintaining fixed size.
     */
    private fun updatePositionHistory(lat: Double, lon: Double) {
        positionHistory.addLast(lat to lon)
        if (positionHistory.size > POSITION_HISTORY_SIZE) {
            positionHistory.removeFirst()
        }
    }

    /**
     * Calculate haversine distance between two points in meters.
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth's radius in meters
        
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δφ = Math.toRadians(lat2 - lat1)
        val Δλ = Math.toRadians(lon2 - lon1)
        
        val a = sin(Δφ / 2) * sin(Δφ / 2) +
                cos(φ1) * cos(φ2) *
                sin(Δλ / 2) * sin(Δλ / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return R * c
    }

    /**
     * Reset camera controller state.
     */
    fun reset() {
        positionHistory.clear()
        positionInterpolator.reset()
        lastCameraPosition = null
        lastLocation = null
        lastBearing = null
        NavLog.camera("Camera controller reset")
    }
    
    /**
     * Get current interpolated position (for testing/debugging).
     */
    fun getCurrentInterpolatedPosition(): Triple<Double, Double, Float?> {
        return positionInterpolator.getCurrentPosition()
    }
    
    /**
     * Check if currently interpolating (for testing/debugging).
     */
    fun isInterpolating(): Boolean {
        return positionInterpolator.isInterpolating()
    }
}