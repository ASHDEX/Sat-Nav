package com.jayesh.satnav.features.transport

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.domain.model.*
import com.jayesh.satnav.domain.repository.RoutingRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Router for multi-modal transportation planning.
 * Phase 14: Multi-modal Transportation Support
 * 
 * Handles planning routes with multiple transportation modes:
 * - Walking segments
 * - Public transit segments
 * - Bicycle segments
 * - Car segments
 * - Mixed mode combinations
 */
@Singleton
class MultiModalRouter @Inject constructor(
    private val routingRepository: RoutingRepository,
    private val appDispatchers: AppDispatchers
) {
    
    companion object {
        private const val TAG = "MultiModalRouter"
        
        // Routing parameters
        private const val MAX_WALKING_DISTANCE = 2000.0 // meters
        private const val MAX_TRANSFER_WALKING_DISTANCE = 500.0 // meters
        private const val MIN_TRANSIT_DISTANCE = 1000.0 // meters
    }
    
    /**
     * Plan a multi-modal route between two points.
     * @param start Start coordinate
     * @param end End coordinate
     * @param preferences Transportation preferences
     * @return Multi-modal route or null if planning fails
     */
    suspend fun planRoute(
        start: RouteCoordinate,
        end: RouteCoordinate,
        preferences: TransportationPreferences
    ): MultiModalRoute? = withContext(appDispatchers.default) {
        NavLog.i("Planning multi-modal route from $start to $end with mode: ${preferences.primaryMode}")
        
        return@withContext when (preferences.primaryMode) {
            TransportationMode.CAR -> planCarRoute(start, end, preferences)
            TransportationMode.BICYCLE -> planBicycleRoute(start, end, preferences)
            TransportationMode.WALKING -> planWalkingRoute(start, end, preferences)
            TransportationMode.PUBLIC_TRANSIT -> planTransitRoute(start, end, preferences)
            TransportationMode.MIXED -> planMixedRoute(start, end, preferences)
        }
    }
    
    /**
     * Plan a car-only route.
     */
    private suspend fun planCarRoute(
        start: RouteCoordinate,
        end: RouteCoordinate,
        preferences: TransportationPreferences
    ): MultiModalRoute? {
        try {
            val route = routingRepository.computeRoute(
                points = listOf(start, end),
                profile = RoutingProfile.CAR
            ).getOrNull() ?: return null
            
            val segment = MultiModalSegment(
                mode = TransportationMode.CAR,
                startCoordinate = start,
                endCoordinate = end,
                distanceMeters = route.distanceMeters,
                durationSeconds = route.durationMillis / 1000.0,
                instructions = route.instructions,
                elevationGainMeters = calculateElevationGain(route.points)
            )
            
            return MultiModalRoute(
                segments = listOf(segment),
                totalDistanceMeters = route.distanceMeters,
                totalDurationSeconds = route.durationMillis / 1000.0,
                modeChanges = 0,
                totalElevationGainMeters = segment.elevationGainMeters
            )
        } catch (e: Exception) {
            NavLog.e("Failed to plan car route", e)
            return null
        }
    }
    
    /**
     * Plan a bicycle route.
     */
    private suspend fun planBicycleRoute(
        start: RouteCoordinate,
        end: RouteCoordinate,
        preferences: TransportationPreferences
    ): MultiModalRoute? {
        try {
            val route = routingRepository.computeRoute(
                points = listOf(start, end),
                profile = RoutingProfile.BIKE
            ).getOrNull() ?: return null
            
            val elevationGain = calculateElevationGain(route.points)
            val caloriesBurned = estimateCaloriesBurned(route.distanceMeters, TransportationMode.BICYCLE)
            
            val segment = MultiModalSegment(
                mode = TransportationMode.BICYCLE,
                startCoordinate = start,
                endCoordinate = end,
                distanceMeters = route.distanceMeters,
                durationSeconds = route.durationMillis / 1000.0,
                instructions = route.instructions,
                elevationGainMeters = elevationGain,
                caloriesBurned = caloriesBurned
            )
            
            return MultiModalRoute(
                segments = listOf(segment),
                totalDistanceMeters = route.distanceMeters,
                totalDurationSeconds = route.durationMillis / 1000.0,
                modeChanges = 0,
                totalCaloriesBurned = caloriesBurned,
                totalElevationGainMeters = elevationGain
            )
        } catch (e: Exception) {
            NavLog.e("Failed to plan bicycle route", e)
            return null
        }
    }
    
    /**
     * Plan a walking route.
     */
    private suspend fun planWalkingRoute(
        start: RouteCoordinate,
        end: RouteCoordinate,
        preferences: TransportationPreferences
    ): MultiModalRoute? {
        try {
            val route = routingRepository.computeRoute(
                points = listOf(start, end),
                profile = RoutingProfile.FOOT
            ).getOrNull() ?: return null
            
            val elevationGain = calculateElevationGain(route.points)
            val caloriesBurned = estimateCaloriesBurned(route.distanceMeters, TransportationMode.WALKING)
            
            val segment = MultiModalSegment(
                mode = TransportationMode.WALKING,
                startCoordinate = start,
                endCoordinate = end,
                distanceMeters = route.distanceMeters,
                durationSeconds = route.durationMillis / 1000.0,
                instructions = route.instructions,
                elevationGainMeters = elevationGain,
                caloriesBurned = caloriesBurned
            )
            
            return MultiModalRoute(
                segments = listOf(segment),
                totalDistanceMeters = route.distanceMeters,
                totalDurationSeconds = route.durationMillis / 1000.0,
                modeChanges = 0,
                totalCaloriesBurned = caloriesBurned,
                totalElevationGainMeters = elevationGain
            )
        } catch (e: Exception) {
            NavLog.e("Failed to plan walking route", e)
            return null
        }
    }
    
    /**
     * Plan a public transit route.
     * Note: This is a simplified version. In production, this would integrate with offline transit data.
     */
    private suspend fun planTransitRoute(
        start: RouteCoordinate,
        end: RouteCoordinate,
        preferences: TransportationPreferences
    ): MultiModalRoute? {
        // For now, create a sample transit route
        // In production, this would:
        // 1. Find nearest transit stops
        // 2. Query offline transit schedules
        // 3. Plan walking segments to/from stops
        // 4. Combine into multi-modal route
        
        val distance = haversineDistance(start.latitude, start.longitude, end.latitude, end.longitude)
        
        if (distance < MIN_TRANSIT_DISTANCE) {
            // Too short for transit, suggest walking instead
            return planWalkingRoute(start, end, preferences)
        }
        
        // Create sample walking segment to transit stop
        val walkToStop = MultiModalSegment(
            mode = TransportationMode.WALKING,
            startCoordinate = start,
            endCoordinate = RouteCoordinate(start.latitude + 0.001, start.longitude + 0.001),
            distanceMeters = 300.0,
            durationSeconds = 240.0,
            instructions = listOf(
                NavInstruction(
                    sign = 0,
                    streetName = "Main Street",
                    distanceMeters = 300.0,
                    durationMillis = 240.0.toLong() * 1000L
                )
            ),
            caloriesBurned = 18.0
        )
        
        // Create sample transit segment
        val transitSegment = MultiModalSegment(
            mode = TransportationMode.PUBLIC_TRANSIT,
            startCoordinate = walkToStop.endCoordinate,
            endCoordinate = RouteCoordinate(end.latitude - 0.001, end.longitude - 0.001),
            distanceMeters = distance - 600.0,
            durationSeconds = (distance - 600.0) / (TransportationMode.PUBLIC_TRANSIT.averageSpeedKmh / 3.6),
            instructions = listOf(
                NavInstruction(
                    sign = 0,
                    streetName = "Bus Line 101",
                    distanceMeters = distance - 600.0,
                    durationMillis = ((distance - 600.0) / (TransportationMode.PUBLIC_TRANSIT.averageSpeedKmh / 3.6) * 1000).toLong()
                )
            ),
            transitDetails = TransitDetails(
                agencyName = "City Transit",
                routeName = "101",
                routeType = TransitType.BUS,
                departureTime = kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis() + 300000),
                arrivalTime = kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis() + 1500000),
                fromStopName = "Main St Stop",
                toStopName = "Central Station",
                tripId = "101_12345",
                headsign = "Downtown",
                wheelchairAccessible = true
            )
        )
        
        // Create sample walking segment from transit stop
        val walkFromStop = MultiModalSegment(
            mode = TransportationMode.WALKING,
            startCoordinate = transitSegment.endCoordinate,
            endCoordinate = end,
            distanceMeters = 300.0,
            durationSeconds = 240.0,
            instructions = listOf(
                NavInstruction(
                    sign = 2,
                    streetName = "Destination Street",
                    distanceMeters = 300.0,
                    durationMillis = 240.0.toLong() * 1000L
                )
            ),
            caloriesBurned = 18.0
        )
        
        val segments = listOf(walkToStop, transitSegment, walkFromStop)
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
            accessibilityScore = if (transitSegment.transitDetails?.wheelchairAccessible == true) 95 else 80
        )
    }
    
    /**
     * Plan a mixed-mode route.
     */
    private suspend fun planMixedRoute(
        start: RouteCoordinate,
        end: RouteCoordinate,
        preferences: TransportationPreferences
    ): MultiModalRoute? {
        // For mixed mode, try to find the optimal combination
        // This is a simplified version that just returns a transit route
        return planTransitRoute(start, end, preferences)
    }
    
    /**
     * Compare routes for different transportation modes.
     */
    suspend fun compareModes(
        start: RouteCoordinate,
        end: RouteCoordinate,
        modes: List<TransportationMode> = TransportationMode.values().toList()
    ): Map<TransportationMode, MultiModalRoute?> {
        val comparisons = mutableMapOf<TransportationMode, MultiModalRoute?>()
        
        for (mode in modes) {
            if (mode == TransportationMode.MIXED) continue // Skip mixed for individual comparison
            
            val preferences = TransportationPreferences(primaryMode = mode)
            val route = planRoute(start, end, preferences)
            comparisons[mode] = route
        }
        
        return comparisons
    }
    
    /**
     * Optimize route for given criteria (time, cost, calories, etc.).
     */
    suspend fun optimizeRoute(
        start: RouteCoordinate,
        end: RouteCoordinate,
        optimizationCriteria: OptimizationCriteria
    ): MultiModalRoute? {
        val allRoutes = compareModes(start, end)
        
        return when (optimizationCriteria) {
            OptimizationCriteria.TIME -> allRoutes.values.minByOrNull { it?.totalDurationSeconds ?: Double.MAX_VALUE }
            OptimizationCriteria.COST -> allRoutes.values.minByOrNull { estimateRouteCost(it) }
            OptimizationCriteria.CALORIES -> allRoutes.values.maxByOrNull { it?.totalCaloriesBurned ?: 0.0 }
            OptimizationCriteria.ENVIRONMENT -> allRoutes.values.minByOrNull { estimateCarbonEmissions(it) }
            OptimizationCriteria.COMFORT -> allRoutes.values.maxByOrNull { estimateComfortScore(it) }
        }
    }
    
    // ========== HELPER METHODS ==========
    
    private fun calculateElevationGain(points: List<RouteCoordinate>): Double {
        if (points.size < 2) return 0.0
        
        var totalGain = 0.0
        // Simplified - would use actual elevation data
        // For now, estimate based on latitude changes
        for (i in 1 until points.size) {
            val gain = (points[i].latitude - points[i-1].latitude).absoluteValue * 1000
            if (gain > 0) totalGain += gain
        }
        
        return totalGain
    }
    
    private fun estimateCaloriesBurned(distanceMeters: Double, mode: TransportationMode): Double {
        val distanceKm = distanceMeters / 1000.0
        return when (mode) {
            TransportationMode.BICYCLE -> distanceKm * 30.0 // 30 calories per km
            TransportationMode.WALKING -> distanceKm * 60.0 // 60 calories per km
            else -> 0.0
        }
    }
    
    private fun estimateRouteCost(route: MultiModalRoute?): Double {
        if (route == null) return Double.MAX_VALUE
        
        var totalCost = 0.0
        for (segment in route.segments) {
            val distanceKm = segment.distanceMeters / 1000.0
            val costPerKm = when (segment.mode) {
                TransportationMode.CAR -> 0.15
                TransportationMode.PUBLIC_TRANSIT -> 0.05
                else -> 0.0
            }
            totalCost += distanceKm * costPerKm
        }
        
        return totalCost
    }
    
    private fun estimateCarbonEmissions(route: MultiModalRoute?): Double {
        if (route == null) return Double.MAX_VALUE
        
        var totalEmissions = 0.0
        for (segment in route.segments) {
            val distanceKm = segment.distanceMeters / 1000.0
            val emissionsPerKm = when (segment.mode) {
                TransportationMode.CAR -> 0.12 // kg CO2 per km
                TransportationMode.PUBLIC_TRANSIT -> 0.05
                else -> 0.0
            }
            totalEmissions += distanceKm * emissionsPerKm
        }
        
        return totalEmissions
    }
    
    private fun estimateComfortScore(route: MultiModalRoute?): Int {
        if (route == null) return 0
        
        var score = 100
        
        // Penalize mode changes
        score -= route.modeChanges * 10
        
        // Penalize long walking segments
        for (segment in route.segments) {
            if (segment.mode == TransportationMode.WALKING && segment.distanceMeters > 1000) {
                score -= 5
            }
        }
        
        // Bonus for transit accessibility
        if (route.segments.any { it.mode == TransportationMode.PUBLIC_TRANSIT && it.transitDetails?.wheelchairAccessible == true }) {
            score += 10
        }
        
        return score.coerceIn(0, 100)
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
 * Optimization criteria for route planning.
 */
enum class OptimizationCriteria {
    TIME,      // Minimize travel time
    COST,      // Minimize cost
    CALORIES,  // Maximize calories burned (for exercise)
    ENVIRONMENT, // Minimize carbon emissions
    COMFORT    // Maximize comfort score
}