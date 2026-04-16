package com.jayesh.satnav.domain.model

import com.jayesh.satnav.features.navigation.lane.SpeedLimitViolation

sealed interface NavigationState {

    data object Idle : NavigationState

    data class Active(
        val route: OfflineRoute,
        val currentStepIndex: Int,
        val currentInstruction: NavInstruction,
        val enhancedInstruction: EnhancedNavInstruction?,
        val distanceToNextTurnMeters: Double,
        val distanceTravelledMeters: Double,
        val snappedLatitude: Double,
        val snappedLongitude: Double,
        val isOffRoute: Boolean,
        val currentSpeedLimit: SpeedLimit? = null,
        val speedLimitViolation: SpeedLimitViolation = SpeedLimitViolation.NONE,
        val laneGuidance: LaneInfo? = null,
        val laneChangeRecommended: Boolean = false,
    ) : NavigationState {
        /** Backward compatibility constructor */
        constructor(
            route: OfflineRoute,
            currentStepIndex: Int,
            currentInstruction: NavInstruction,
            distanceToNextTurnMeters: Double,
            distanceTravelledMeters: Double,
            snappedLatitude: Double,
            snappedLongitude: Double,
            isOffRoute: Boolean,
        ) : this(
            route = route,
            currentStepIndex = currentStepIndex,
            currentInstruction = currentInstruction,
            enhancedInstruction = null,
            distanceToNextTurnMeters = distanceToNextTurnMeters,
            distanceTravelledMeters = distanceTravelledMeters,
            snappedLatitude = snappedLatitude,
            snappedLongitude = snappedLongitude,
            isOffRoute = isOffRoute,
            currentSpeedLimit = null,
            speedLimitViolation = SpeedLimitViolation.NONE,
            laneGuidance = null,
            laneChangeRecommended = false
        )
    }

    data class ArrivedAtWaypoint(
        val stoppedAtName: String,
        val nextLegIndex: Int,
        val nextStopName: String,
        val autoAdvanceInSeconds: Int = 5,
    ) : NavigationState

    data object Arrived : NavigationState

    /**
     * Rerouting state when navigation engine detects off-route condition.
     * Transitional state while a new route is being calculated.
     */
    data class Rerouting(
        val lastKnownLat: Double = 0.0,
        val lastKnownLon: Double = 0.0,
    ) : NavigationState

    /**
     * Error state when navigation encounters a recoverable or unrecoverable error.
     */
    data class Error(val message: String) : NavigationState
}
