package com.jayesh.satnav.features.navigation

import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.domain.model.*
import com.jayesh.satnav.features.navigation.lane.LaneGuidanceManager
import com.jayesh.satnav.features.navigation.lane.SpeedLimitViolation
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class NavigationEngine @Inject constructor(
    private val laneGuidanceManager: LaneGuidanceManager,
    private val tripCoordinator: com.jayesh.satnav.features.routing.TripCoordinator,
) {

    private val _state = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    // Track waypoint countdown timer
    private var waypointCountdownJob: kotlinx.coroutines.Job? = null

    // Route currently loaded for navigation (may not have been started yet)
    private var preparedRoute: OfflineRoute? = null

    // Cumulative distances from start to each route point
    private var pointCumulativeDistances: DoubleArray = DoubleArray(0)

    // Cumulative distances at the END of each instruction step
    private var stepEndDistances: DoubleArray = DoubleArray(0)

    /** Load a route for navigation. Resets state to Idle until startNavigation() is called. */
    fun prepareRoute(route: OfflineRoute) {
        preparedRoute = route
        pointCumulativeDistances = buildPointCumulativeDistances(route.points)
        stepEndDistances = buildStepEndDistances(route.instructions)
        _state.value = NavigationState.Idle
    }

    fun hasPreparedRoute(): Boolean = preparedRoute != null

    fun getPreparedRoute(): OfflineRoute? = preparedRoute

    /** Begin turn-by-turn navigation from the start of the prepared route. */
    fun startNavigation() {
        val route = preparedRoute ?: return
        if (route.instructions.isEmpty()) return
        val start = route.points.first()
        val firstInstruction = route.instructions.first()
        
        // Get road tags for the start segment
        val roadTags = getRoadTagsForSegment(0, route)
        
        // Enhance the first instruction
        val enhancedInstruction = laneGuidanceManager.enhanceInstruction(
            instruction = firstInstruction,
            currentPosition = start,
            nextPosition = if (route.points.size > 1) route.points[1] else null,
            roadTags = roadTags,
            distanceToManeuver = firstInstruction.distanceMeters
        )
        
        _state.value = NavigationState.Active(
            route = route,
            currentStepIndex = 0,
            currentInstruction = firstInstruction,
            enhancedInstruction = enhancedInstruction,
            distanceToNextTurnMeters = firstInstruction.distanceMeters,
            distanceTravelledMeters = 0.0,
            snappedLatitude = start.latitude,
            snappedLongitude = start.longitude,
            isOffRoute = false,
            currentSpeedLimit = enhancedInstruction.speedLimit,
            speedLimitViolation = SpeedLimitViolation.NONE,
            laneGuidance = enhancedInstruction.laneGuidance,
            laneChangeRecommended = enhancedInstruction.laneChangeRecommended
        )
    }

    fun stopNavigation() {
        waypointCountdownJob?.cancel()
        _state.value = NavigationState.Idle
    }

    /**
     * Skip to the next leg immediately (user tapped "Continue Now" on waypoint arrival).
     */
    fun skipToNextLeg() {
        waypointCountdownJob?.cancel()
        val nextLeg = tripCoordinator.advanceToNextLeg()
        if (nextLeg != null) {
            // In a real app, you'd convert RouteOption to OfflineRoute and prepare it
            // For now, just transition back to Navigating with the same route
            // The actual route preparation would be handled by the caller
            val currentState = _state.value
            if (currentState is NavigationState.Active) {
                _state.value = currentState.copy(currentStepIndex = 0)
            }
        }
    }

    private fun startWaypointCountdown() {
        waypointCountdownJob?.cancel()
        waypointCountdownJob = kotlinx.coroutines.GlobalScope.launch {
            for (seconds in 4 downTo 0) {
                kotlinx.coroutines.delay(1000)
                val currentState = _state.value
                if (currentState is NavigationState.ArrivedAtWaypoint) {
                    _state.value = currentState.copy(autoAdvanceInSeconds = seconds)
                }
            }
            // Auto-advance after countdown
            skipToNextLeg()
        }
    }

    /**
     * Feed a new GPS position. The engine snaps it to the route, advances steps,
     * detects off-route, and emits a new [NavigationState].
     */
    fun updatePosition(latitude: Double, longitude: Double, currentSpeedKmh: Double? = null) {
        val route = preparedRoute ?: return
        if (_state.value !is NavigationState.Active) return
        if (route.points.size < 2) return

        // 1. Snap GPS to nearest point on route polyline
        val snap = snapToPolyline(latitude, longitude, route.points)
        val distanceToRoute = haversineMeters(latitude, longitude, snap.lat, snap.lon)
        val isOffRoute = distanceToRoute > OFF_ROUTE_THRESHOLD_METERS
        NavLog.match(
            "snap lat=%.6f lon=%.6f distToRoute=%.1fm offRoute=%b".format(
                snap.lat, snap.lon, distanceToRoute, isOffRoute,
            ),
        )

        // 2. Compute distance travelled along route to the snapped point
        val distanceTravelled = pointCumulativeDistances[snap.segmentIndex] +
            haversineMeters(
                route.points[snap.segmentIndex].latitude,
                route.points[snap.segmentIndex].longitude,
                snap.lat,
                snap.lon,
            )

        // 3. Check arrival
        val totalRouteDistance = pointCumulativeDistances.lastOrNull() ?: return
        if (distanceTravelled >= totalRouteDistance - ARRIVAL_THRESHOLD_METERS) {
            // Handle waypoint arrival for multi-leg trips
            if (tripCoordinator.isMultiLeg() && !tripCoordinator.isLastLeg()) {
                val currentLeg = tripCoordinator.currentLeg()
                val nextLegIndex = (tripCoordinator.currentLegIndex() ?: 0) + 1
                val nextLeg = tripCoordinator.getLeg(nextLegIndex)

                if (currentLeg != null && nextLeg != null) {
                    val currentStop = currentLeg.toStopId
                    val nextStop = nextLeg.toStopId

                    _state.value = NavigationState.ArrivedAtWaypoint(
                        stoppedAtName = currentStop.take(30), // Use stop ID as placeholder (ideally get actual name)
                        nextLegIndex = nextLegIndex,
                        nextStopName = nextStop.take(30),
                        autoAdvanceInSeconds = 5
                    )
                    startWaypointCountdown()
                    return
                }
            }

            // Single-leg or last leg: normal arrival
            _state.value = NavigationState.Arrived
            return
        }

        // 4. Find current step: first step whose end distance exceeds distance travelled
        val stepIndex = stepEndDistances.indexOfFirst { it > distanceTravelled }
            .let { if (it < 0) stepEndDistances.lastIndex else it }
            .coerceAtLeast(0)

        val instruction = route.instructions.getOrNull(stepIndex) ?: return
        val stepEndDistance = stepEndDistances.getOrElse(stepIndex) { totalRouteDistance }
        val distanceToNextTurn = (stepEndDistance - distanceTravelled).coerceAtLeast(0.0)
        NavLog.instr(
            "step=%d/%d toTurn=%.0fm [%s]".format(
                stepIndex + 1, route.instructions.size,
                distanceToNextTurn,
                instruction.humanText,
            ),
        )

        // 5. Get road tags for current segment (simulated - in real app would come from OSM data)
        val roadTags = getRoadTagsForSegment(snap.segmentIndex, route)

        // 6. Enhance instruction with lane guidance and speed limit
        val currentPosition = if (snap.segmentIndex < route.points.size - 1) {
            route.points[snap.segmentIndex]
        } else {
            route.points.last()
        }
        
        val nextPosition = if (snap.segmentIndex < route.points.size - 2) {
            route.points[snap.segmentIndex + 1]
        } else {
            null
        }
        
        val enhancedInstruction = laneGuidanceManager.enhanceInstruction(
            instruction = instruction,
            currentPosition = currentPosition,
            nextPosition = nextPosition,
            roadTags = roadTags,
            distanceToManeuver = distanceToNextTurn
        )

        // 7. Check speed limit violation if current speed is available
        val speedLimitViolation = if (currentSpeedKmh != null) {
            laneGuidanceManager.checkSpeedLimitViolation(currentSpeedKmh, enhancedInstruction.speedLimit)
        } else {
            SpeedLimitViolation.NONE
        }

        _state.value = NavigationState.Active(
            route = route,
            currentStepIndex = stepIndex,
            currentInstruction = instruction,
            enhancedInstruction = enhancedInstruction,
            distanceToNextTurnMeters = distanceToNextTurn,
            distanceTravelledMeters = distanceTravelled,
            snappedLatitude = snap.lat,
            snappedLongitude = snap.lon,
            isOffRoute = isOffRoute,
            currentSpeedLimit = enhancedInstruction.speedLimit,
            speedLimitViolation = speedLimitViolation,
            laneGuidance = enhancedInstruction.laneGuidance,
            laneChangeRecommended = enhancedInstruction.laneChangeRecommended
        )
    }

    // ── Geometry helpers ─────────────────────────────────────────────────────

    private fun buildPointCumulativeDistances(points: List<RouteCoordinate>): DoubleArray {
        val result = DoubleArray(points.size)
        for (i in 1 until points.size) {
            result[i] = result[i - 1] + haversineMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
            )
        }
        return result
    }

    private fun buildStepEndDistances(instructions: List<NavInstruction>): DoubleArray {
        val result = DoubleArray(instructions.size)
        var cumulative = 0.0
        for (i in instructions.indices) {
            cumulative += instructions[i].distanceMeters
            result[i] = cumulative
        }
        return result
    }

    private data class SnapResult(val lat: Double, val lon: Double, val segmentIndex: Int)

    private fun snapToPolyline(lat: Double, lon: Double, points: List<RouteCoordinate>): SnapResult {
        var bestIndex = 0
        var bestDist = Double.MAX_VALUE
        var bestLat = points.first().latitude
        var bestLon = points.first().longitude

        for (i in 0 until points.lastIndex) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val (sLat, sLon) = projectOnSegment(
                lat, lon,
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude,
            )
            val dist = haversineMeters(lat, lon, sLat, sLon)
            if (dist < bestDist) {
                bestDist = dist
                bestIndex = i
                bestLat = sLat
                bestLon = sLon
            }
        }
        return SnapResult(bestLat, bestLon, bestIndex)
    }

    /** Project point P onto segment A–B in planar lat/lon space (good enough at city scale). */
    private fun projectOnSegment(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double,
    ): Pair<Double, Double> {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-14) return ax to ay
        val t = ((px - ax) * dx + (py - ay) * dy) / lenSq
        val tClamped = t.coerceIn(0.0, 1.0)
        return (ax + tClamped * dx) to (ay + tClamped * dy)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Simulated method to get road tags for a segment.
     * In a real implementation, this would query OSM data or a pre-processed database.
     */
    private fun getRoadTagsForSegment(segmentIndex: Int, route: OfflineRoute): Map<String, String> {
        // This is a simplified simulation
        // In reality, you would need to:
        // 1. Load OSM way data for the route segment
        // 2. Extract tags like "lanes", "maxspeed", "turn:lanes", etc.
        // 3. Return as key-value pairs
        
        val simulatedTags = mutableMapOf<String, String>()
        
        // Simulate based on segment index
        when {
            segmentIndex % 10 == 0 -> {
                // Highway segment
                simulatedTags["highway"] = "motorway"
                simulatedTags["lanes"] = "3"
                simulatedTags["maxspeed"] = "120 km/h"
                simulatedTags["turn:lanes"] = "through|through|right"
                simulatedTags["change:lanes"] = "yes|yes|no"
            }
            segmentIndex % 5 == 0 -> {
                // Urban road with turns
                simulatedTags["highway"] = "primary"
                simulatedTags["lanes"] = "2"
                simulatedTags["maxspeed"] = "60 km/h"
                simulatedTags["turn:lanes"] = "left|through;right"
                simulatedTags["change:lanes"] = "no|yes"
            }
            else -> {
                // Default residential road
                simulatedTags["highway"] = "residential"
                simulatedTags["lanes"] = "1"
                simulatedTags["maxspeed"] = "30 km/h"
            }
        }
        
        return simulatedTags
    }

    private companion object {
        const val OFF_ROUTE_THRESHOLD_METERS = 30.0
        const val ARRIVAL_THRESHOLD_METERS = 20.0
    }
}
