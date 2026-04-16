package com.jayesh.satnav.domain.navigation

import com.jayesh.satnav.domain.model.RouteOption

/**
 * Internal state used by NavigationEngine for its state machine.
 * This is separate from the public domain.model.NavigationState API.
 */
sealed interface NavigationEngineState {
    data object Idle : NavigationEngineState
    data class Navigating(
        val match: MatchResult,
        val route: RouteOption,
        val etaEpochMs: Long,
        val remainingDistanceM: Double,
        val remainingDurationMs: Long,
    ) : NavigationEngineState
    data class Rerouting(val lastMatch: MatchResult) : NavigationEngineState
    data object Arrived : NavigationEngineState
    data class ArrivedAtWaypoint(
        val waypointIndex: Int,
        val totalWaypoints: Int,
        val waypointName: String?,
        val countdownSeconds: Int
    ) : NavigationEngineState
    data class Error(val message: String) : NavigationEngineState
}
