package com.jayesh.satnav.features.navigation

import android.location.Location
import com.jayesh.satnav.core.utils.NavLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Smooth position interpolation engine for pro-level UX.
 *
 * Transforms jumpy GPS updates into buttery-smooth motion:
 * GPS → filter → predict → interpolate → animate → render
 *
 * Features:
 * - Linear interpolation (lerp) between positions
 * - Configurable interpolation duration (~500ms)
 * - Continuous position updates during interpolation
 * - Bearing interpolation for smooth rotation with angle normalization
 * - GPS noise filtering with low-pass smoothing
 * - Stationary jitter elimination (<5m movements ignored)
 * - Predictive movement for reduced perceived lag
 *
 * GPS Noise Filtering:
 * 1. Low-pass filter applied to raw GPS positions (α=0.7)
 * 2. Movements <5 meters are ignored (stationary jitter elimination)
 * 3. Filtered positions used for interpolation targets
 * 4. Maintains responsiveness for real movement while filtering noise
 *
 * Predictive Movement:
 * 1. Tracks position history to calculate velocity (last 3 positions)
 * 2. Estimates movement direction from bearing
 * 3. Predicts position slightly ahead (up to 200ms, limited to 10m)
 * 4. Reduces perceived lag by anticipating movement
 * 5. Only activates when moving >0.5 m/s to avoid prediction when stationary
 *
 * Bearing Interpolation Algorithm:
 * 1. Normalize bearings to 0-360° range
 * 2. Calculate shortest rotational path (handles 0/360 wrap-around)
 * 3. Apply smoothing factor (0.3) for natural rotation
 * 4. Linear interpolation between old and new bearing
 * 5. Re-normalize result to ensure valid bearing
 *
 * This eliminates sudden bearing jumps and provides smooth,
 * natural rotation even with noisy GPS bearing data.
 */
class PositionInterpolator(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    
    companion object {
        // Default interpolation duration in milliseconds
        private const val DEFAULT_INTERPOLATION_DURATION_MS = 500L
        
        // Minimum distance to trigger interpolation (meters)
        private const val MIN_DISTANCE_FOR_INTERPOLATION = 1.0
        
        // GPS noise filter threshold - ignore movements smaller than this (meters)
        private const val GPS_NOISE_FILTER_THRESHOLD = 5.0
        
        // Maximum interpolation distance (meters) - beyond this we jump directly
        private const val MAX_INTERPOLATION_DISTANCE = 100.0
        
        // Interpolation frame rate (Hz)
        private const val INTERPOLATION_FRAME_RATE = 60
        private const val FRAME_DELAY_MS = 1000L / INTERPOLATION_FRAME_RATE
        
        // Bearing interpolation smoothing factor (0-1, higher = smoother)
        private const val BEARING_SMOOTHING_FACTOR = 0.3f
        
        // Position smoothing factor for low-pass filter (0-1, higher = smoother)
        private const val POSITION_SMOOTHING_FACTOR = 0.7f
        
        // Predictive movement factor (0-1, higher = more prediction)
        private const val PREDICTIVE_MOVEMENT_FACTOR = 0.3f
        
        // Maximum prediction time (milliseconds) - how far ahead to predict
        private const val MAX_PREDICTION_TIME_MS = 200L
        
        // Position history size for velocity calculation
        private const val POSITION_HISTORY_SIZE = 3
    }
    
    // State
    private var interpolationJob: Job? = null
    private var isInterpolating = false
    
    // Current interpolated position
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var currentBearing: Float? = null
    
    // Smoothed position (low-pass filtered) for GPS noise reduction
    private var smoothedLatitude: Double = 0.0
    private var smoothedLongitude: Double = 0.0
    
    // Position history for velocity calculation (timestamp, lat, lon)
    private data class PositionRecord(val timestamp: Long, val latitude: Double, val longitude: Double)
    private val positionHistory = ArrayDeque<PositionRecord>()
    
    // Interpolation source and target
    private var sourceLatitude: Double = 0.0
    private var sourceLongitude: Double = 0.0
    private var sourceBearing: Float? = null
    
    private var targetLatitude: Double = 0.0
    private var targetLongitude: Double = 0.0
    private var targetBearing: Float? = null
    
    // Callback for position updates
    var onPositionUpdated: ((latitude: Double, longitude: Double, bearing: Float?) -> Unit)? = null
    
    /**
     * Start interpolation to a new target position.
     * If already interpolating, the current interpolation will be smoothly transitioned.
     */
    fun interpolateTo(
        latitude: Double,
        longitude: Double,
        bearing: Float? = null,
        durationMs: Long = DEFAULT_INTERPOLATION_DURATION_MS
    ) {
        // If this is the first position, set it directly
        if (!isInterpolating && currentLatitude == 0.0 && currentLongitude == 0.0) {
            currentLatitude = latitude
            currentLongitude = longitude
            currentBearing = bearing?.let { normalizeBearing(it) }
            smoothedLatitude = latitude
            smoothedLongitude = longitude
            onPositionUpdated?.invoke(latitude, longitude, currentBearing)
            NavLog.interpolation("First position set directly: lat=$latitude, lon=$longitude, bearing=$currentBearing")
            return
        }
        
        // Apply low-pass filter to raw GPS position for noise reduction
        val filteredLatitude = applyLowPassFilter(latitude, smoothedLatitude, POSITION_SMOOTHING_FACTOR)
        val filteredLongitude = applyLowPassFilter(longitude, smoothedLongitude, POSITION_SMOOTHING_FACTOR)
        
        // Update smoothed position
        smoothedLatitude = filteredLatitude
        smoothedLongitude = filteredLongitude
        
        // Calculate distance from current position to filtered position
        val distance = haversineDistance(currentLatitude, currentLongitude, filteredLatitude, filteredLongitude)
        
        // GPS noise filter: ignore movements smaller than threshold (stationary jitter)
        if (distance < GPS_NOISE_FILTER_THRESHOLD) {
            NavLog.interpolation("GPS noise filtered: movement ${distance}m < ${GPS_NOISE_FILTER_THRESHOLD}m threshold")
            return
        }
        
        // If distance is too small for interpolation (but passed noise filter), skip interpolation
        if (distance < MIN_DISTANCE_FOR_INTERPOLATION) {
            NavLog.interpolation("Distance too small for interpolation: ${distance}m")
            return
        }
        
        // If distance is too large, jump directly (likely GPS error or teleport)
        if (distance > MAX_INTERPOLATION_DISTANCE) {
            NavLog.interpolation("Distance too large, jumping directly: ${distance}m")
            currentLatitude = filteredLatitude
            currentLongitude = filteredLongitude
            currentBearing = bearing?.let { normalizeBearing(it) }
            onPositionUpdated?.invoke(filteredLatitude, filteredLongitude, currentBearing)
            return
        }
        
        // Update position history for velocity calculation
        updatePositionHistory(filteredLatitude, filteredLongitude)
        
        // Calculate current velocity for predictive movement
        val velocity = calculateVelocity()
        var predictedLatitude = filteredLatitude
        var predictedLongitude = filteredLongitude
        
        // Apply predictive movement if we have valid velocity and bearing
        if (velocity != null && velocity > 0.5) { // Only predict if moving > 0.5 m/s
            val (predictedLat, predictedLon) = applyPrediction(
                filteredLatitude,
                filteredLongitude,
                bearing,
                velocity
            )
            predictedLatitude = predictedLat
            predictedLongitude = predictedLon
            
            NavLog.interpolation("Predictive movement applied: velocity=${velocity}m/s, bearing=$bearing")
        }
        
        // Set interpolation source to current position
        sourceLatitude = currentLatitude
        sourceLongitude = currentLongitude
        sourceBearing = currentBearing
        
        // Set interpolation target (use predicted position for reduced lag)
        targetLatitude = predictedLatitude
        targetLongitude = predictedLongitude
        targetBearing = bearing
        
        // Cancel any existing interpolation
        interpolationJob?.cancel()
        
        // Start new interpolation
        isInterpolating = true
        interpolationJob = scope.launch {
            val startTime = System.currentTimeMillis()
            val totalDuration = durationMs
            
            NavLog.interpolation("Starting interpolation: source=($sourceLatitude,$sourceLongitude) → target=($targetLatitude,$targetLongitude), duration=${totalDuration}ms")
            
            while (isActive && System.currentTimeMillis() - startTime < totalDuration) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / totalDuration).coerceIn(0f, 1f)
                
                // Calculate eased progress (ease-in-out cubic for smoother motion)
                val easedProgress = easeInOutCubic(progress)
                
                // Interpolate position
                currentLatitude = interpolateLinear(sourceLatitude, targetLatitude, easedProgress)
                currentLongitude = interpolateLinear(sourceLongitude, targetLongitude, easedProgress)
                
                // Interpolate bearing with smoothing
                currentBearing = interpolateBearing(sourceBearing, targetBearing, easedProgress)
                
                // Notify listeners
                onPositionUpdated?.invoke(currentLatitude, currentLongitude, currentBearing)
                
                // Wait for next frame
                delay(FRAME_DELAY_MS)
            }
            
            // Ensure we end exactly at target
            currentLatitude = targetLatitude
            currentLongitude = targetLongitude
            currentBearing = targetBearing
            onPositionUpdated?.invoke(currentLatitude, currentLongitude, currentBearing)
            
            isInterpolating = false
            NavLog.interpolation("Interpolation completed: lat=$currentLatitude, lon=$currentLongitude")
        }
    }
    
    /**
     * Interpolate to a new location.
     */
    fun interpolateTo(location: Location, durationMs: Long = DEFAULT_INTERPOLATION_DURATION_MS) {
        val bearing = if (location.hasBearing()) location.bearing else null
        interpolateTo(location.latitude, location.longitude, bearing, durationMs)
    }
    
    /**
     * Get current interpolated position.
     */
    fun getCurrentPosition(): Triple<Double, Double, Float?> {
        return Triple(currentLatitude, currentLongitude, currentBearing)
    }
    
    /**
     * Stop any ongoing interpolation and jump to target.
     */
    fun stopAndJumpToTarget() {
        interpolationJob?.cancel()
        interpolationJob = null
        
        if (isInterpolating) {
            currentLatitude = targetLatitude
            currentLongitude = targetLongitude
            currentBearing = targetBearing?.let { normalizeBearing(it) }
            onPositionUpdated?.invoke(currentLatitude, currentLongitude, currentBearing)
            NavLog.interpolation("Interpolation stopped, jumped to target")
        }
        
        isInterpolating = false
    }
    
    /**
     * Reset interpolator to initial state.
     */
    fun reset() {
        interpolationJob?.cancel()
        interpolationJob = null
        isInterpolating = false
        
        currentLatitude = 0.0
        currentLongitude = 0.0
        currentBearing = null
        
        smoothedLatitude = 0.0
        smoothedLongitude = 0.0
        
        positionHistory.clear()
        
        sourceLatitude = 0.0
        sourceLongitude = 0.0
        sourceBearing = null
        
        targetLatitude = 0.0
        targetLongitude = 0.0
        targetBearing = null
        
        NavLog.interpolation("Interpolator reset")
    }
    
    /**
     * Check if currently interpolating.
     */
    fun isInterpolating(): Boolean = isInterpolating
    
    // Private helper functions
    
    private fun interpolateLinear(start: Double, end: Double, progress: Float): Double {
        return start + (end - start) * progress
    }
    
    /**
     * Interpolates between two bearing values with smooth rotation.
     *
     * Algorithm:
     * 1. Normalize both bearings to 0-360° range
     * 2. Calculate shortest rotational path (handles wrap-around at 0/360)
     * 3. Apply smoothing factor (0.3) to reduce sudden jumps
     * 4. Perform linear interpolation (lerp) between start and end
     * 5. Normalize result back to 0-360° range
     *
     * @param start Starting bearing in degrees (0-360, but handles any value)
     * @param end Target bearing in degrees (0-360, but handles any value)
     * @param progress Interpolation progress (0.0 to 1.0)
     * @return Interpolated bearing, or null if both inputs are null
     */
    private fun interpolateBearing(start: Float?, end: Float?, progress: Float): Float? {
        if (start == null && end == null) return null
        if (start == null) return end
        if (end == null) return start
        
        // Normalize bearings to 0-360 range
        val normalizedStart = normalizeBearing(start)
        val normalizedEnd = normalizeBearing(end)
        
        // Calculate shortest path for bearing interpolation
        var diff = normalizedEnd - normalizedStart
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        
        // Apply smoothing factor for smoother rotation
        val smoothedDiff = diff * BEARING_SMOOTHING_FACTOR
        
        val interpolated = normalizedStart + smoothedDiff * progress
        return normalizeBearing(interpolated)
    }
    
    /**
     * Normalizes a bearing value to the 0-360° range.
     *
     * Handles both positive and negative values:
     * - 450° → 90°
     * - -90° → 270°
     * - 720° → 0°
     *
     * @param bearing Raw bearing value in degrees
     * @return Normalized bearing in 0-360° range
     */
    private fun normalizeBearing(bearing: Float): Float {
        var result = bearing % 360
        if (result < 0) result += 360
        return result
    }
    
    /**
     * Applies a low-pass filter to smooth position updates and reduce GPS noise.
     *
     * Formula: filtered = old * α + new * (1 - α)
     * Where α is the smoothing factor (0-1, higher = smoother)
     *
     * @param newValue New raw GPS value
     * @param oldValue Previous filtered value
     * @param smoothingFactor Smoothing factor (0.0 to 1.0, higher = more smoothing)
     * @return Filtered value with reduced noise
     */
    private fun applyLowPassFilter(newValue: Double, oldValue: Double, smoothingFactor: Float): Double {
        return oldValue * smoothingFactor + newValue * (1 - smoothingFactor)
    }
    
    /**
     * Updates position history with a new position record.
     * Maintains history size limit for velocity calculation.
     */
    private fun updatePositionHistory(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        positionHistory.addLast(PositionRecord(now, latitude, longitude))
        
        // Keep history size limited
        while (positionHistory.size > POSITION_HISTORY_SIZE) {
            positionHistory.removeFirst()
        }
    }
    
    /**
     * Calculates current velocity in meters per second based on position history.
     * Returns null if insufficient history or velocity cannot be calculated.
     */
    private fun calculateVelocity(): Double? {
        if (positionHistory.size < 2) return null
        
        val records = positionHistory.toList()
        val oldest = records.first()
        val newest = records.last()
        
        val timeDiffMs = newest.timestamp - oldest.timestamp
        if (timeDiffMs <= 0) return null
        
        val distance = haversineDistance(oldest.latitude, oldest.longitude, newest.latitude, newest.longitude)
        val timeDiffSeconds = timeDiffMs / 1000.0
        
        return distance / timeDiffSeconds
    }
    
    /**
     * Applies predictive movement to a position based on current velocity.
     * Moves position slightly ahead in the direction of movement.
     *
     * @param latitude Base latitude
     * @param longitude Base longitude
     * @param bearing Direction of movement in degrees (0-360)
     * @param velocityMps Velocity in meters per second
     * @return Predicted position (lat, lon) slightly ahead
     */
    private fun applyPrediction(
        latitude: Double,
        longitude: Double,
        bearing: Float?,
        velocityMps: Double
    ): Pair<Double, Double> {
        if (bearing == null || velocityMps <= 0.1) {
            // No bearing or very slow movement, no prediction
            return latitude to longitude
        }
        
        // Calculate prediction distance based on velocity and max prediction time
        val predictionTimeMs = (MAX_PREDICTION_TIME_MS * PREDICTIVE_MOVEMENT_FACTOR).toLong()
        val predictionTimeSeconds = predictionTimeMs / 1000.0
        val predictionDistanceMeters = velocityMps * predictionTimeSeconds
        
        // Limit prediction distance to avoid overshooting
        val maxPredictionDistance = 10.0 // meters
        val limitedDistance = minOf(predictionDistanceMeters, maxPredictionDistance)
        
        if (limitedDistance < 0.5) {
            // Prediction too small to matter
            return latitude to longitude
        }
        
        // Calculate predicted position using bearing and distance
        val bearingRad = Math.toRadians(bearing.toDouble())
        val earthRadius = 6371000.0 // meters
        
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)
        
        val predictedLatRad = asin(
            sin(latRad) * cos(limitedDistance / earthRadius) +
            cos(latRad) * sin(limitedDistance / earthRadius) * cos(bearingRad)
        )
        
        val predictedLonRad = lonRad + atan2(
            sin(bearingRad) * sin(limitedDistance / earthRadius) * cos(latRad),
            cos(limitedDistance / earthRadius) - sin(latRad) * sin(predictedLatRad)
        )
        
        return Math.toDegrees(predictedLatRad) to Math.toDegrees(predictedLonRad)
    }
    
    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4 * t * t * t
        } else {
            1 - (-2 * t + 2).pow(3) / 2
        }
    }
    
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
}