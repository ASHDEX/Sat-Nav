package com.jayesh.satnav.features.traffic

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.domain.model.*
import com.jayesh.satnav.domain.repository.ExportFormat
import com.jayesh.satnav.domain.repository.TrafficInsight
import com.jayesh.satnav.domain.repository.TrafficPatternRepository
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for traffic pattern learning and prediction integration.
 * Phase 13: Real-time Traffic Pattern Awareness
 * 
 * Responsibilities:
 * 1. Record travel times during navigation
 * 2. Provide traffic-aware route analysis
 * 3. Update UI with traffic predictions
 * 4. Manage traffic pattern learning lifecycle
 */
@Singleton
class TrafficPatternManager @Inject constructor(
    private val trafficPatternRepository: TrafficPatternRepository,
    private val trafficPredictor: TrafficPredictor,
    private val appDispatchers: AppDispatchers
) {
    
    companion object {
        private const val TAG = "TrafficPatternManager"
        
        // Recording parameters
        private const val MIN_DISTANCE_FOR_RECORDING = 50.0 // meters
        private const val MIN_TIME_FOR_RECORDING = 30.0 // seconds
        private const val RECORDING_INTERVAL_MS = 5000L // 5 seconds
        
        // Prediction parameters
        private const val PREDICTION_UPDATE_INTERVAL_MS = 30000L // 30 seconds
    }
    
    private val _state = MutableStateFlow(TrafficPatternState())
    val state: StateFlow<TrafficPatternState> = _state.asStateFlow()
    
    private var recordingJob: Job? = null
    private var predictionJob: Job? = null
    private var currentRoute: OfflineRoute? = null
    private var currentSegmentIndex: Int = -1
    private var lastRecordedPosition: RouteCoordinate? = null
    private var lastRecordedTime: Long = 0
    
    private val coroutineScope = CoroutineScope(appDispatchers.default)
    
    /**
     * Start traffic pattern recording for a route.
     * @param route The route being navigated
     */
    fun startRecording(route: OfflineRoute) {
        NavLog.i("Starting traffic pattern recording for route with ${route.points.size} points")
        
        currentRoute = route
        currentSegmentIndex = 0
        lastRecordedPosition = null
        lastRecordedTime = System.currentTimeMillis()
        
        // Stop any existing recording
        stopRecording()
        
        // Start periodic recording
        recordingJob = coroutineScope.launch(appDispatchers.io) {
            while (true) {
                delay(RECORDING_INTERVAL_MS)
                recordCurrentSegmentIfNeeded()
            }
        }
        
        // Start periodic prediction updates
        startPredictionUpdates()
        
        _state.update { it.copy(isRecording = true, currentRouteId = route.hashCode().toString()) }
    }
    
    /**
     * Stop traffic pattern recording.
     */
    fun stopRecording() {
        NavLog.i("Stopping traffic pattern recording")
        
        recordingJob?.cancel()
        recordingJob = null
        
        predictionJob?.cancel()
        predictionJob = null
        
        currentRoute = null
        currentSegmentIndex = -1
        lastRecordedPosition = null
        
        _state.update { it.copy(isRecording = false, currentRouteId = null) }
    }
    
    /**
     * Update current position during navigation.
     * @param latitude Current latitude
     * @param longitude Current longitude
     * @param segmentIndex Current segment index in route
     * @param speedKmh Current speed in km/h (optional)
     */
    fun updatePosition(
        latitude: Double,
        longitude: Double,
        segmentIndex: Int,
        speedKmh: Double? = null
    ) {
        currentSegmentIndex = segmentIndex
        val currentPosition = RouteCoordinate(latitude, longitude)
        
        // Check if we should record travel time for completed segment
        if (lastRecordedPosition != null && currentSegmentIndex > 0) {
            val previousSegmentIndex = currentSegmentIndex - 1
            coroutineScope.launch { recordSegmentTravelTime(previousSegmentIndex, lastRecordedPosition!!, currentPosition) }
        }
        
        lastRecordedPosition = currentPosition
        lastRecordedTime = System.currentTimeMillis()
        
        // Update speed if available
        speedKmh?.let { speed ->
            _state.update { it.copy(currentSpeedKmh = speed) }
        }
    }
    
    /**
     * Get traffic-aware route analysis.
     * @param route Route to analyze
     * @param departureTime Planned departure time
     * @return Traffic analysis with predictions and recommendations
     */
    suspend fun analyzeRoute(
        route: OfflineRoute,
        departureTime: Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    ): TrafficAnalysis {
        // Extract segment IDs from route
        val segmentIds = extractSegmentIds(route)
        
        // Get analysis from repository
        return trafficPatternRepository.getRouteAnalysis(segmentIds, departureTime)
    }
    
    /**
     * Get traffic predictions for route segments.
     * @param route Route to get predictions for
     * @param targetTime Time to predict for
     * @return Map of segment ID to traffic prediction
     */
    suspend fun getRoutePredictions(
        route: OfflineRoute,
        targetTime: Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    ): Map<String, TrafficPrediction> {
        val segmentIds = extractSegmentIds(route)
        return trafficPatternRepository.getPredictions(segmentIds, targetTime)
    }
    
    /**
     * Get traffic visualization for map display.
     * @param route Route to visualize
     * @param targetTime Time to visualize for
     * @return List of traffic visualization data
     */
    suspend fun getRouteVisualization(
        route: OfflineRoute,
        targetTime: Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    ): List<TrafficVisualization> {
        val segmentIds = extractSegmentIds(route)
        return trafficPatternRepository.getVisualizationData(segmentIds, targetTime)
    }
    
    /**
     * Get traffic insights for user.
     * @param limit Maximum number of insights
     * @return List of traffic insights
     */
    suspend fun getInsights(limit: Int = 5): List<TrafficInsight> {
        return trafficPatternRepository.getInsights(limit)
    }
    
    /**
     * Check if traffic pattern learning is enabled.
     */
    fun isLearningEnabled(): Boolean {
        return trafficPatternRepository.getConfig().learningEnabled
    }
    
    /**
     * Update traffic learning configuration.
     * @param config New configuration
     */
    suspend fun updateConfig(config: TrafficLearningConfig) {
        trafficPatternRepository.updateConfig(config)
        _state.update { it.copy(config = config) }
    }
    
    /**
     * Export traffic pattern data.
     * @param format Export format
     * @return Exported data as string
     */
    suspend fun exportData(format: ExportFormat): String {
        return trafficPatternRepository.exportData(format)
    }
    
    /**
     * Import traffic pattern data.
     * @param data Data to import
     * @param format Import format
     */
    suspend fun importData(data: String, format: ExportFormat) {
        trafficPatternRepository.importData(data, format)
    }
    
    // ========== PRIVATE METHODS ==========
    
    private fun startPredictionUpdates() {
        predictionJob = coroutineScope.launch(appDispatchers.io) {
            while (true) {
                delay(PREDICTION_UPDATE_INTERVAL_MS)
                updatePredictions()
            }
        }
    }
    
    private suspend fun updatePredictions() {
        val route = currentRoute ?: return
        
        try {
            val predictions = getRoutePredictions(route)
            val visualization = getRouteVisualization(route)
            
            _state.update { state ->
                state.copy(
                    currentPredictions = predictions,
                    currentVisualization = visualization,
                    lastPredictionUpdate = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                )
            }
            
            NavLog.i("Updated predictions for ${predictions.size} segments")
        } catch (e: Exception) {
            NavLog.e("Failed to update predictions", e)
        }
    }
    
    private suspend fun recordCurrentSegmentIfNeeded() {
        val route = currentRoute ?: return
        val currentPosition = lastRecordedPosition ?: return
        
        if (currentSegmentIndex >= route.points.size - 1) {
            return // At end of route
        }
        
        val currentTime = System.currentTimeMillis()
        val timeElapsed = (currentTime - lastRecordedTime) / 1000.0
        
        // Only record if enough time has passed
        if (timeElapsed >= MIN_TIME_FOR_RECORDING) {
            val segmentId = generateSegmentId(route, currentSegmentIndex)
            val segmentLength = calculateSegmentLength(route, currentSegmentIndex)
            
            // Estimate travel time based on elapsed time and distance
            val travelTimeSeconds = timeElapsed * (segmentLength / MIN_DISTANCE_FOR_RECORDING)
            
            if (travelTimeSeconds > 0) {
                trafficPatternRepository.recordTravelTime(
                    segmentId = segmentId,
                    travelTimeSeconds = travelTimeSeconds,
                    confidence = calculateConfidence(segmentLength, timeElapsed)
                )
                
                NavLog.i("Recorded travel time for segment $segmentId: ${travelTimeSeconds}s")
            }
        }
    }
    
    private suspend fun recordSegmentTravelTime(
        segmentIndex: Int,
        startPosition: RouteCoordinate,
        endPosition: RouteCoordinate
    ) {
        val route = currentRoute ?: return
        
        if (segmentIndex < 0 || segmentIndex >= route.points.size - 1) {
            return
        }
        
        val segmentId = generateSegmentId(route, segmentIndex)
        val segmentLength = calculateSegmentLength(route, segmentIndex)
        val actualLength = haversineDistance(
            startPosition.latitude,
            startPosition.longitude,
            endPosition.latitude,
            endPosition.longitude
        )
        
        // Calculate travel time based on elapsed time and proportion of segment traveled
        val currentTime = System.currentTimeMillis()
        val timeElapsed = (currentTime - lastRecordedTime) / 1000.0
        val proportionTraveled = actualLength / segmentLength
        val travelTimeSeconds = if (proportionTraveled > 0) timeElapsed / proportionTraveled else 0.0
        
        if (travelTimeSeconds > 0 && segmentLength >= MIN_DISTANCE_FOR_RECORDING) {
            trafficPatternRepository.recordTravelTime(
                segmentId = segmentId,
                travelTimeSeconds = travelTimeSeconds,
                confidence = calculateConfidence(segmentLength, timeElapsed)
            )
            
            NavLog.i("Recorded completed segment $segmentId: ${travelTimeSeconds}s (${proportionTraveled * 100}% traveled)")
        }
    }
    
    private fun extractSegmentIds(route: OfflineRoute): List<String> {
        return (0 until route.points.size - 1).map { index ->
            generateSegmentId(route, index)
        }
    }
    
    private fun generateSegmentId(route: OfflineRoute, segmentIndex: Int): String {
        if (segmentIndex < 0 || segmentIndex >= route.points.size - 1) {
            return "invalid"
        }
        
        val point1 = route.points[segmentIndex]
        val point2 = route.points[segmentIndex + 1]
        
        // Create a hash-based ID for the segment
        val hash = "${point1.latitude}:${point1.longitude}:${point2.latitude}:${point2.longitude}"
        return "segment_${hash.hashCode().toUInt().toString(16)}"
    }
    
    private fun calculateSegmentLength(route: OfflineRoute, segmentIndex: Int): Double {
        if (segmentIndex < 0 || segmentIndex >= route.points.size - 1) {
            return 0.0
        }
        
        val point1 = route.points[segmentIndex]
        val point2 = route.points[segmentIndex + 1]
        
        return haversineDistance(
            point1.latitude,
            point1.longitude,
            point2.latitude,
            point2.longitude
        )
    }
    
    private fun calculateConfidence(distance: Double, timeElapsed: Double): Double {
        // Confidence based on distance traveled and time measurement quality
        val distanceConfidence = (distance / MIN_DISTANCE_FOR_RECORDING).coerceIn(0.0, 1.0)
        val timeConfidence = (timeElapsed / MIN_TIME_FOR_RECORDING).coerceIn(0.0, 1.0)
        
        return (distanceConfidence * timeConfidence).coerceIn(0.0, 1.0)
    }
    
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δφ = Math.toRadians(lat2 - lat1)
        val Δλ = Math.toRadians(lon2 - lon1)
        
        val a = sin(Δφ / 2).pow(2) + cos(φ1) * cos(φ2) * sin(Δλ / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return R * c
    }
}

/**
 * State for traffic pattern manager.
 */
data class TrafficPatternState(
    val isRecording: Boolean = false,
    val currentRouteId: String? = null,
    val currentSpeedKmh: Double? = null,
    val currentPredictions: Map<String, TrafficPrediction> = emptyMap(),
    val currentVisualization: List<TrafficVisualization> = emptyList(),
    val lastPredictionUpdate: Instant? = null,
    val config: TrafficLearningConfig = TrafficLearningConfig(),
    val insights: List<TrafficInsight> = emptyList()
)