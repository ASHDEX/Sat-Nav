package com.jayesh.satnav.domain.navigation

import com.jayesh.satnav.domain.model.NavigationState
import com.jayesh.satnav.domain.model.OfflineRoute
import com.jayesh.satnav.domain.model.RouteOption
import com.jayesh.satnav.domain.routing.RoutePlanner
import com.jayesh.satnav.domain.repository.LocationRepository
import com.jayesh.satnav.features.routing.TripCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core navigation engine that manages the state machine for turn-by-turn navigation.
 * Runs all work on Dispatchers.Default.
 */
@Singleton
class NavigationEngine @Inject constructor(
    private val locationRepo: LocationRepository,
    private val planner: RoutePlanner,
    private val tripCoordinator: TripCoordinator,
) {
    private val _state = MutableStateFlow<NavigationEngineState>(NavigationEngineState.Idle)
    val state: StateFlow<NavigationEngineState> = _state.asStateFlow()

    private var activeRoute: RouteOption? = null
    private var matcher: RouteMatcher? = null
    private var offRouteStreak = 0
    private var reroutingAttempts = 0
    
    private var locationCollectionJob: Job? = null
    private var countdownJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.Default)
    
    /**
     * Start navigation with the given route.
     */
    suspend fun start(route: RouteOption) {
        stop()
        
        activeRoute = route
        matcher = RouteMatcher(route)
        offRouteStreak = 0
        reroutingAttempts = 0
        
        // Initial state
        val initialMatch = MatchResult(
            snappedLat = route.points.first().latitude,
            snappedLon = route.points.first().longitude,
            bearing = 0f,
            distanceAlongRouteM = 0.0,
            distanceToNextManeuverM = route.instructions.firstOrNull()?.distanceMeters ?: 0.0,
            currentInstructionIndex = 0,
            isOffRoute = false,
            offRouteDistanceM = 0.0
        )
        
        _state.value = NavigationEngineState.Navigating(
            match = initialMatch,
            route = route,
            etaEpochMs = System.currentTimeMillis() + route.durationMillis,
            remainingDistanceM = route.distanceMeters,
            remainingDurationMs = route.durationMillis
        )
        
        // Start collecting location updates
        startLocationCollection()
    }
    
    /**
     * Stop navigation and clean up.
     */
    fun stop() {
        locationCollectionJob?.cancel()
        locationCollectionJob = null
        countdownJob?.cancel()
        countdownJob = null
        activeRoute = null
        matcher = null
        offRouteStreak = 0
        reroutingAttempts = 0
        _state.value = NavigationEngineState.Idle
    }
    
    /**
     * Skip to the next leg in a multi-leg trip.
     * Only works when in ArrivedAtWaypoint state.
     */
    suspend fun skipToNextLeg() {
        val currentState = _state.value
        if (currentState !is NavigationEngineState.ArrivedAtWaypoint) {
            return
        }
        
        countdownJob?.cancel()
        countdownJob = null
        
        val nextLeg = tripCoordinator.advanceToNextLeg()
        if (nextLeg != null) {
            // Start navigation with the next leg's route
            start(nextLeg.selectedRoute)
        } else {
            // No more legs, trip is complete
            _state.value = NavigationEngineState.Arrived
            stop()
        }
    }
    
    private fun startLocationCollection() {
        locationCollectionJob = locationRepo.currentLocation
            .onEach { location ->
                processLocation(location)
            }
            .catch { e ->
                _state.value = NavigationEngineState.Error("Location error: ${e.message}")
            }
            .launchIn(engineScope)
    }
    
    private suspend fun processLocation(location: android.location.Location) {
        val currentState = _state.value
        val currentRoute = activeRoute
        val currentMatcher = matcher
        
        if (currentRoute == null || currentMatcher == null) {
            return
        }
        
        when (currentState) {
            is NavigationEngineState.Navigating -> {
                val match = currentMatcher.matchLocation(location)
                
                // Check for arrival
                val distanceToEnd = currentRoute.distanceMeters - match.distanceAlongRouteM
                val speedMs = location.speed
                if (distanceToEnd <= 30.0 && (speedMs < 3.0 || speedMs == 0f)) {
                    // Check if this is a multi-leg trip and not the last leg
                    if (tripCoordinator.isMultiLeg() && !tripCoordinator.isLastLeg()) {
                        // Arrived at a waypoint, not final destination
                        val currentIndex = tripCoordinator.currentLegIndex() ?: 0
                        val totalLegs = tripCoordinator.totalLegs()
                        val stopNames = tripCoordinator.currentLegStopNames()
                        
                        _state.value = NavigationEngineState.ArrivedAtWaypoint(
                            waypointIndex = currentIndex + 1, // 1-based for UI
                            totalWaypoints = totalLegs,
                            waypointName = stopNames?.second ?: "Waypoint ${currentIndex + 1}",
                            countdownSeconds = 30 // 30-second countdown
                        )
                        
                        // Start countdown for auto-advance
                        startCountdown()
                    } else {
                        // Final destination or single-route navigation
                        _state.value = NavigationEngineState.Arrived
                        stop()
                    }
                    return
                }
                
                // Update off-route streak
                if (match.isOffRoute) {
                    offRouteStreak++
                    if (offRouteStreak >= 3) {
                        _state.value = NavigationEngineState.Rerouting(match)
                        reroutingAttempts = 0
                        attemptReroute(location, match)
                        return
                    }
                } else {
                    offRouteStreak = 0
                }
                
                // Update navigating state
                val remainingDistance = maxOf(0.0, currentRoute.distanceMeters - match.distanceAlongRouteM)
                val remainingDuration = (remainingDistance / currentRoute.distanceMeters * currentRoute.durationMillis).toLong()
                val eta = System.currentTimeMillis() + remainingDuration
                
                _state.value = NavigationEngineState.Navigating(
                    match = match,
                    route = currentRoute,
                    etaEpochMs = eta,
                    remainingDistanceM = remainingDistance,
                    remainingDurationMs = remainingDuration
                )
            }
            
            is NavigationEngineState.Rerouting -> {
                // Already in rerouting, wait for completion or try again
                if (reroutingAttempts < 5) {
                    attemptReroute(location, currentState.lastMatch)
                } else {
                    _state.value = NavigationEngineState.Error("Failed to reroute after 5 attempts")
                }
            }
            
            else -> {
                // Idle, Arrived, Error - ignore location updates
            }
        }
    }
    
    private suspend fun attemptReroute(
        currentLocation: android.location.Location,
        lastMatch: MatchResult
    ) {
        reroutingAttempts++
        
        try {
            val destination = activeRoute?.points?.lastOrNull() ?: return
            val start = com.jayesh.satnav.core.utils.LatLng(
                currentLocation.latitude,
                currentLocation.longitude
            )

            val result = planner.plan(
                start = start,
                destination = destination,
                profile = "car"
            )

            val newRoute = result.getOrNull()?.firstOrNull() ?: return

            // Successfully rerouted
            activeRoute = newRoute
            matcher = RouteMatcher(newRoute)
            offRouteStreak = 0
            reroutingAttempts = 0

            val match = MatchResult(
                snappedLat = currentLocation.latitude,
                snappedLon = currentLocation.longitude,
                bearing = currentLocation.bearing,
                distanceAlongRouteM = 0.0,
                distanceToNextManeuverM = newRoute.instructions.firstOrNull()?.distanceMeters ?: 0.0,
                currentInstructionIndex = 0,
                isOffRoute = false,
                offRouteDistanceM = 0.0
            )

            val remainingDistance = newRoute.distanceMeters
            val remainingDuration = newRoute.durationMillis
            val eta = System.currentTimeMillis() + remainingDuration

            _state.value = NavigationEngineState.Navigating(
                match = match,
                route = newRoute,
                etaEpochMs = eta,
                remainingDistanceM = remainingDistance,
                remainingDurationMs = remainingDuration
            )
        } catch (e: Exception) {
            // Rerouting failed
            delay(1000)
        }
    }
    
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = engineScope.launch {
            var remainingSeconds = 30
            
            while (remainingSeconds > 0 && isActive) {
                // Update the countdown in the state
                val currentState = _state.value
                if (currentState is NavigationEngineState.ArrivedAtWaypoint) {
                    _state.value = currentState.copy(countdownSeconds = remainingSeconds)
                }
                
                delay(1000) // 1 second
                remainingSeconds--
            }
            
            // Countdown finished, auto-advance to next leg
            if (isActive) {
                val nextLeg = tripCoordinator.advanceToNextLeg()
                if (nextLeg != null) {
                    // Start navigation with the next leg's route
                    start(nextLeg.selectedRoute)
                } else {
                    // No more legs, trip is complete
                    _state.value = NavigationEngineState.Arrived
                    stop()
                }
            }
        }
    }
    
    override fun toString(): String {
        return "NavigationEngine(state=${_state.value}, routeId=${activeRoute?.id})"
    }
}