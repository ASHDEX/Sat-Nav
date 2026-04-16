package com.jayesh.satnav.features.transport

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.domain.model.*
import com.jayesh.satnav.domain.repository.RoutingRepository
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
import kotlin.math.*

/**
 * Manager for multi-modal transportation support.
 * Phase 14: Multi-modal Transportation Support
 * 
 * Responsibilities:
 * 1. Manage transportation mode preferences
 * 2. Handle mode switching during navigation
 * 3. Coordinate multi-modal route planning
 * 4. Provide mode availability and suggestions
 */
@Singleton
class MultiModalManager @Inject constructor(
    private val routingRepository: RoutingRepository,
    private val appDispatchers: AppDispatchers
) {
    
    companion object {
        private const val TAG = "MultiModalManager"
        
        // Mode switching parameters
        private const val MODE_SUGGESTION_INTERVAL_MS = 60000L // 1 minute
        private const val MIN_DISTANCE_FOR_MODE_SWITCH = 1000.0 // meters
        private const val MIN_TIME_SAVING_FOR_SUGGESTION = 300.0 // 5 minutes in seconds
    }
    
    private val _state = MutableStateFlow(MultiModalState())
    val state: StateFlow<MultiModalState> = _state.asStateFlow()
    
    private var suggestionJob: Job? = null
    private var currentRoute: OfflineRoute? = null
    private var currentPosition: RouteCoordinate? = null
    
    private val coroutineScope = CoroutineScope(appDispatchers.default)
    
    /**
     * Initialize multi-modal manager with user preferences.
     * @param preferences Transportation preferences
     */
    fun initialize(preferences: TransportationPreferences = TransportationPreferences()) {
        NavLog.i("Initializing multi-modal manager with mode: ${preferences.primaryMode}")
        
        _state.update { it.copy(preferences = preferences) }
        
        // Start periodic mode suggestions
        startModeSuggestionUpdates()
    }
    
    /**
     * Update transportation preferences.
     * @param preferences New transportation preferences
     */
    fun updatePreferences(preferences: TransportationPreferences) {
        NavLog.i("Updating transportation preferences to: ${preferences.primaryMode}")
        _state.update { it.copy(preferences = preferences) }
    }
    
    /**
     * Switch to a different transportation mode.
     * @param mode New transportation mode
     * @param forceRecompute Whether to force route recomputation
     */
    suspend fun switchMode(
        mode: TransportationMode,
        forceRecompute: Boolean = true
    ): Boolean {
        NavLog.i("Switching transportation mode to: $mode")
        
        val oldMode = _state.value.preferences.primaryMode
        if (oldMode == mode && !forceRecompute) {
            return true
        }
        
        // Update preferences
        val newPreferences = _state.value.preferences.copy(primaryMode = mode)
        _state.update { it.copy(preferences = newPreferences) }
        
        // If we have a current route and position, recompute route with new mode
        if (forceRecompute && currentRoute != null && currentPosition != null) {
            return recomputeRouteWithCurrentMode()
        }
        
        return true
    }
    
    /**
     * Set current route for mode management.
     * @param route Current navigation route
     */
    fun setCurrentRoute(route: OfflineRoute?) {
        currentRoute = route
        _state.update { it.copy(currentRouteId = route?.hashCode()?.toString()) }

        if (route != null) {
            NavLog.i("Set current route for multi-modal management")
        }
    }
    
    /**
     * Update current position for mode suggestions.
     * @param latitude Current latitude
     * @param longitude Current longitude
     * @param speedKmh Current speed in km/h (optional)
     */
    fun updatePosition(
        latitude: Double,
        longitude: Double,
        speedKmh: Double? = null
    ) {
        currentPosition = RouteCoordinate(latitude, longitude)
        
        // Update speed if available
        speedKmh?.let { speed ->
            _state.update { it.copy(currentSpeedKmh = speed) }
        }
        
        // Check for mode switching opportunities
        coroutineScope.launch(appDispatchers.io) {
            checkModeSwitchingOpportunities()
        }
    }
    
    /**
     * Get mode availability for current location.
     */
    suspend fun getModeAvailability(): List<ModeAvailability> {
        val position = currentPosition ?: return getDefaultModeAvailability()
        
        return TransportationMode.values().map { mode ->
            when (mode) {
                TransportationMode.CAR -> ModeAvailability(
                    mode = mode,
                    isAvailable = true,
                    availabilityReason = AvailabilityReason.ALWAYS_AVAILABLE,
                    costPerKm = 0.15 // Estimated cost per km for car
                )
                TransportationMode.BICYCLE -> ModeAvailability(
                    mode = mode,
                    isAvailable = true,
                    availabilityReason = AvailabilityReason.ALWAYS_AVAILABLE,
                    costPerKm = 0.0
                )
                TransportationMode.WALKING -> ModeAvailability(
                    mode = mode,
                    isAvailable = true,
                    availabilityReason = AvailabilityReason.ALWAYS_AVAILABLE,
                    costPerKm = 0.0
                )
                TransportationMode.PUBLIC_TRANSIT -> {
                    // Check if transit is available (simplified - would check offline transit data)
                    val hasTransit = checkTransitAvailability(position)
                    ModeAvailability(
                        mode = mode,
                        isAvailable = hasTransit,
                        availabilityReason = if (hasTransit) {
                            AvailabilityReason.TRANSIT_SCHEDULE
                        } else {
                            AvailabilityReason.NO_TRANSIT_COVERAGE
                        },
                        estimatedWaitTime = if (hasTransit) 5.0 else null, // 5 minutes average wait
                        costPerKm = 0.05 // Estimated cost per km for transit
                    )
                }
                TransportationMode.MIXED -> ModeAvailability(
                    mode = mode,
                    isAvailable = true,
                    availabilityReason = AvailabilityReason.ALWAYS_AVAILABLE,
                    costPerKm = 0.08 // Average cost for mixed mode
                )
            }
        }
    }
    
    /**
     * Get mode comparison for a route.
     * @param start Start coordinate
     * @param end End coordinate
     */
    suspend fun getModeComparison(
        start: RouteCoordinate,
        end: RouteCoordinate
    ): List<ModeComparison> {
        val comparisons = mutableListOf<ModeComparison>()
        
        // Get routes for each mode (simplified - would call routing engine)
        for (mode in TransportationMode.values()) {
            if (mode == TransportationMode.MIXED) continue // Skip mixed for individual comparison
            
            val comparison = estimateModeComparison(mode, start, end)
            comparisons.add(comparison)
        }
        
        return comparisons.sortedBy { it.estimatedDurationSeconds }
    }
    
    /**
     * Get mode switching suggestions based on current context.
     */
    suspend fun getModeSuggestions(): List<ModeSuggestion> {
        val currentMode = _state.value.preferences.primaryMode
        val position = currentPosition ?: return emptyList()
        val route = currentRoute ?: return emptyList()
        
        val suggestions = mutableListOf<ModeSuggestion>()
        
        // Check each alternative mode
        for (alternativeMode in TransportationMode.values()) {
            if (alternativeMode == currentMode) continue
            
            val suggestion = evaluateModeSuggestion(currentMode, alternativeMode, position, route)
            suggestion?.let { suggestions.add(it) }
        }
        
        return suggestions.sortedByDescending { it.confidence }
    }
    
    /**
     * Plan a multi-modal route.
     * @param start Start coordinate
     * @param end End coordinate
     * @param preferences Transportation preferences
     */
    suspend fun planMultiModalRoute(
        start: RouteCoordinate,
        end: RouteCoordinate,
        preferences: TransportationPreferences = _state.value.preferences
    ): MultiModalRoute? {
        NavLog.i("Planning multi-modal route from $start to $end")
        
        // For now, return a simplified multi-modal route
        // In production, this would:
        // 1. Query routing engine for each segment
        // 2. Combine segments with mode changes
        // 3. Optimize for time/cost/comfort
        
        return createSampleMultiModalRoute(start, end, preferences.primaryMode)
    }
    
    /**
     * Get transportation statistics for user.
     */
    suspend fun getTransportationStats(): List<TransportationStats> {
        // Simplified - would query from local database
        return TransportationMode.values().map { mode ->
            TransportationStats(
                mode = mode,
                totalDistanceMeters = when (mode) {
                    TransportationMode.CAR -> 150000.0 // 150 km
                    TransportationMode.BICYCLE -> 50000.0 // 50 km
                    TransportationMode.WALKING -> 20000.0 // 20 km
                    TransportationMode.PUBLIC_TRANSIT -> 80000.0 // 80 km
                    TransportationMode.MIXED -> 100000.0 // 100 km
                },
                totalDurationSeconds = when (mode) {
                    TransportationMode.CAR -> 10800.0 // 3 hours
                    TransportationMode.BICYCLE -> 18000.0 // 5 hours
                    TransportationMode.WALKING -> 7200.0 // 2 hours
                    TransportationMode.PUBLIC_TRANSIT -> 14400.0 // 4 hours
                    TransportationMode.MIXED -> 21600.0 // 6 hours
                },
                totalCaloriesBurned = when (mode) {
                    TransportationMode.BICYCLE -> 2000.0
                    TransportationMode.WALKING -> 1000.0
                    TransportationMode.MIXED -> 1500.0
                    else -> null
                },
                averageSpeedKmh = mode.averageSpeedKmh,
                tripCount = when (mode) {
                    TransportationMode.CAR -> 15
                    TransportationMode.BICYCLE -> 8
                    TransportationMode.WALKING -> 20
                    TransportationMode.PUBLIC_TRANSIT -> 12
                    TransportationMode.MIXED -> 10
                }
            )
        }
    }
    
    /**
     * Clean up resources.
     */
    fun cleanup() {
        suggestionJob?.cancel()
        suggestionJob = null
        NavLog.i("Multi-modal manager cleaned up")
    }
    
    // ========== PRIVATE METHODS ==========
    
    private fun startModeSuggestionUpdates() {
        suggestionJob = coroutineScope.launch(appDispatchers.io) {
            while (true) {
                delay(MODE_SUGGESTION_INTERVAL_MS)
                updateModeSuggestions()
            }
        }
    }
    
    private suspend fun updateModeSuggestions() {
        val suggestions = getModeSuggestions()
        _state.update { it.copy(currentSuggestions = suggestions) }
        
        if (suggestions.isNotEmpty()) {
            NavLog.i("Updated mode suggestions: ${suggestions.size} suggestions")
        }
    }
    
    private suspend fun checkModeSwitchingOpportunities() {
        val route = currentRoute ?: return
        val position = currentPosition ?: return
        val currentMode = _state.value.preferences.primaryMode
        
        // Check if we're approaching a point where mode switching might be beneficial
        // For example: approaching city center where parking is difficult
        val shouldSuggestSwitch = shouldSuggestModeSwitch(currentMode, position, route)
        
        if (shouldSuggestSwitch) {
            val suggestions = getModeSuggestions()
            _state.update { it.copy(activeSuggestion = suggestions.firstOrNull()) }
        }
    }
    
    private suspend fun recomputeRouteWithCurrentMode(): Boolean {
        val route = currentRoute ?: return false
        val position = currentPosition ?: return false
        val mode = _state.value.preferences.primaryMode
        
        NavLog.i("Recomputing route with mode: $mode")
        
        try {
            // In production, this would call the routing engine with the new mode
            // For now, just update the state
            _state.update { it.copy(lastRouteRecomputation = Instant.fromEpochMilliseconds(System.currentTimeMillis())) }
            return true
        } catch (e: Exception) {
            NavLog.e("Failed to recompute route with mode $mode", e)
            return false
        }
    }
    
    private fun checkTransitAvailability(position: RouteCoordinate): Boolean {
        // Simplified - would check offline transit data
        // For now, assume transit is available in urban areas
        return true
    }
    
    private suspend fun evaluateModeSuggestion(
        currentMode: TransportationMode,
        alternativeMode: TransportationMode,
        position: RouteCoordinate,
        route: OfflineRoute
    ): ModeSuggestion? {
        // Simplified evaluation - would use real routing and traffic data
        val currentTime = estimateTravelTime(currentMode, route)
        val alternativeTime = estimateTravelTime(alternativeMode, route)
        
        val timeSavings = currentTime - alternativeTime
        
        // Only suggest if significant time savings
        if (timeSavings < MIN_TIME_SAVING_FOR_SUGGESTION) {
            return null
        }
        
        val reason = when {
            alternativeMode == TransportationMode.PUBLIC_TRANSIT && currentMode == TransportationMode.CAR ->
                SuggestionReason.TRAFFIC_CONGESTION
            alternativeMode == TransportationMode.BICYCLE && currentMode == TransportationMode.CAR ->
                SuggestionReason.PARKING_DIFFICULTY
            alternativeMode == TransportationMode.WALKING && currentMode == TransportationMode.CAR ->
                SuggestionReason.HEALTH_BENEFITS
            else -> SuggestionReason.BETTER_ALTERNATIVE
        }
        
        val confidence = calculateSuggestionConfidence(currentMode, alternativeMode, timeSavings)
        
        return ModeSuggestion(
            fromMode = currentMode,
            toMode = alternativeMode,
            reason = reason,
            estimatedTimeSavingsSeconds = timeSavings,
            estimatedCostSavings = estimateCostSavings(currentMode, alternativeMode, route.distanceMeters),
            confidence = confidence
        )
    }
    
    private fun estimateTravelTime(mode: TransportationMode, route: OfflineRoute): Double {
        // Simplified estimation based on average speed
        val averageSpeedMs = mode.averageSpeedKmh / 3.6 // Convert km/h to m/s
        return route.distanceMeters / averageSpeedMs
    }
    
    private fun estimateCostSavings(
        fromMode: TransportationMode,
        toMode: TransportationMode,
        distanceMeters: Double
    ): Double? {
        val distanceKm = distanceMeters / 1000.0
        
        val fromCostPerKm = when (fromMode) {
            TransportationMode.CAR -> 0.15
            TransportationMode.PUBLIC_TRANSIT -> 0.05
            else -> 0.0
        }
        
        val toCostPerKm = when (toMode) {
            TransportationMode.CAR -> 0.15
            TransportationMode.PUBLIC_TRANSIT -> 0.05
            else -> 0.0
        }
        
        val savings = (fromCostPerKm - toCostPerKm) * distanceKm
        return if (savings > 0) savings else null
    }
    
    private fun calculateSuggestionConfidence(
        fromMode: TransportationMode,
        toMode: TransportationMode,
        timeSavings: Double
    ): Double {
        var confidence = 0.5
        
        // Increase confidence based on time savings
        confidence += (timeSavings / 1800.0).coerceAtMost(0.3) // Up to 30% for 30min savings
        
        // Mode-specific confidence adjustments
        when {
            toMode == TransportationMode.PUBLIC_TRANSIT && fromMode == TransportationMode.CAR ->
                confidence += 0.1 // Transit is often reliable
            toMode == TransportationMode.BICYCLE && fromMode == TransportationMode.CAR ->
                confidence += 0.05 // Weather dependent
            toMode == TransportationMode.WALKING && fromMode == TransportationMode.CAR ->
                confidence += 0.02 // Distance dependent
        }
        
        return confidence.coerceIn(0.0, 1.0)
    }
    
    private fun shouldSuggestModeSwitch(
        currentMode: TransportationMode,
        position: RouteCoordinate,
        route: OfflineRoute
    ): Boolean {
        // Simplified - would use real-time data
        // For now, suggest switch if we're in last 20% of route (approaching destination)
        val remainingDistance = estimateRemainingDistance(position, route)
        val totalDistance = route.distanceMeters
        
        return remainingDistance < totalDistance * 0.2 && currentMode == TransportationMode.CAR
    }
    
    private fun estimateRemainingDistance(position: RouteCoordinate, route: OfflineRoute): Double {
        // Simplified - would use real snapping to route
        return route.distanceMeters * 0.3 // Assume 30% remaining
    }
    
    private fun estimateModeComparison(
        mode: TransportationMode,
        start: RouteCoordinate,
        end: RouteCoordinate
    ): ModeComparison {
        val distance = haversineDistance(start.latitude, start.longitude, end.latitude, end.longitude)
        val duration = distance / (mode.averageSpeedKmh / 3.6)
        
        val cost = when (mode) {
            TransportationMode.CAR -> distance / 1000.0 * 0.15
            TransportationMode.PUBLIC_TRANSIT -> distance / 1000.0 * 0.05
            else -> 0.0
        }
        
        val carbonEmissions = when (mode) {
            TransportationMode.CAR -> distance / 1000.0 * 0.12 // kg CO2 per km
            TransportationMode.PUBLIC_TRANSIT -> distance / 1000.0 * 0.05
            TransportationMode.BICYCLE -> 0.0
            TransportationMode.WALKING -> 0.0
            TransportationMode.MIXED -> distance / 1000.0 * 0.06
        }
        
        val caloriesBurned = when (mode) {
            TransportationMode.BICYCLE -> distance / 1000.0 * 30.0 // 30 calories per km
            TransportationMode.WALKING -> distance / 1000.0 * 60.0 // 60 calories per km
            else -> null
        }
        
        val healthBenefits = when (mode) {
            TransportationMode.BICYCLE -> "Cardiovascular exercise, muscle strength"
            TransportationMode.WALKING -> "Low-impact exercise, mental health benefits"
            else -> null
        }
        
        return ModeComparison(
            mode = mode,
            estimatedDurationSeconds = duration,
            estimatedCost = cost,
            caloriesBurned = caloriesBurned,
            carbonEmissionsKg = carbonEmissions,
            healthBenefits = healthBenefits
        )
    }
    
    private fun getDefaultModeAvailability(): List<ModeAvailability> {
        return TransportationMode.values().map { mode ->
            ModeAvailability(
                mode = mode,
                isAvailable = true,
                availabilityReason = AvailabilityReason.ALWAYS_AVAILABLE,
                costPerKm = when (mode) {
                    TransportationMode.CAR -> 0.15
                    TransportationMode.PUBLIC_TRANSIT -> 0.05
                    else -> 0.0
                }
            )
        }
    }
    
    private fun createSampleMultiModalRoute(
        start: RouteCoordinate,
        end: RouteCoordinate,
        primaryMode: TransportationMode
    ): MultiModalRoute {
        // Create a sample multi-modal route
        val walkingSegment = MultiModalSegment(
            mode = TransportationMode.WALKING,
            startCoordinate = start,
            endCoordinate = RouteCoordinate(start.latitude + 0.001, start.longitude + 0.001),
            distanceMeters = 500.0,
            durationSeconds = 360.0, // 6 minutes
            instructions = listOf(
                NavInstruction(
                    sign = 0, // Continue
                    streetName = "Main Street",
                    distanceMeters = 500.0,
                    durationMillis = 360_000L
                )
            ),
            elevationGainMeters = 10.0,
            caloriesBurned = 30.0
        )
        
        val transitSegment = MultiModalSegment(
            mode = TransportationMode.PUBLIC_TRANSIT,
            startCoordinate = walkingSegment.endCoordinate,
            endCoordinate = RouteCoordinate(end.latitude - 0.001, end.longitude - 0.001),
            distanceMeters = 5000.0,
            durationSeconds = 1200.0, // 20 minutes
            instructions = listOf(
                NavInstruction(
                    sign = 1, // Turn slight right
                    streetName = "Bus Line 101",
                    distanceMeters = 5000.0,
                    durationMillis = 1_200_000L
                )
            ),
            transitDetails = TransitDetails(
                agencyName = "City Transit",
                routeName = "101",
                routeType = TransitType.BUS,
                departureTime = Instant.fromEpochMilliseconds(System.currentTimeMillis() + 360000),
                arrivalTime = Instant.fromEpochMilliseconds(System.currentTimeMillis() + 1560000),
                fromStopName = "Main St & 1st Ave",
                toStopName = "Central Station",
                tripId = "101_12345",
                headsign = "Downtown",
                wheelchairAccessible = true
            )
        )
        
        val finalWalkingSegment = MultiModalSegment(
            mode = TransportationMode.WALKING,
            startCoordinate = transitSegment.endCoordinate,
            endCoordinate = end,
            distanceMeters = 300.0,
            durationSeconds = 240.0, // 4 minutes
            instructions = listOf(
                NavInstruction(
                    sign = 2, // Turn right
                    streetName = "Destination Street",
                    distanceMeters = 300.0,
                    durationMillis = 240_000L
                )
            ),
            elevationGainMeters = 5.0,
            caloriesBurned = 18.0
        )
        
        val segments = listOf(walkingSegment, transitSegment, finalWalkingSegment)
        val totalDistance = segments.sumOf { it.distanceMeters }
        val totalDuration = segments.sumOf { it.durationSeconds }
        val totalCalories = segments.sumOf { it.caloriesBurned ?: 0.0 }
        val totalElevation = segments.sumOf { it.elevationGainMeters }
        
        return MultiModalRoute(
            segments = segments,
            totalDistanceMeters = totalDistance,
            totalDurationSeconds = totalDuration,
            modeChanges = segments.size - 1,
            totalCaloriesBurned = totalCalories,
            totalElevationGainMeters = totalElevation,
            accessibilityScore = 95
        )
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
 * State for multi-modal transportation manager.
 */
data class MultiModalState(
    val preferences: TransportationPreferences = TransportationPreferences(),
    val currentRouteId: String? = null,
    val currentSpeedKmh: Double? = null,
    val currentSuggestions: List<ModeSuggestion> = emptyList(),
    val activeSuggestion: ModeSuggestion? = null,
    val modeAvailability: List<ModeAvailability> = emptyList(),
    val lastRouteRecomputation: Instant? = null,
    val transportationStats: List<TransportationStats> = emptyList()
)