package com.jayesh.satnav.domain.repository

import com.jayesh.satnav.domain.model.*

interface RoutingRepository {
    suspend fun getRoutingEngineStatus(): RoutingEngineStatus

    suspend fun computeRoute(
        points: List<RouteCoordinate>,
        profile: RoutingProfile = RoutingProfile.CAR,
    ): Result<OfflineRoute>

    /**
     * Compute route with AI optimization
     */
    suspend fun computeOptimizedRoute(
        start: RouteCoordinate,
        end: RouteCoordinate,
        userId: String,
        criteria: OptimizationCriteria = OptimizationCriteria(),
        includePersonalization: Boolean = true
    ): Result<AiOptimizationResult>

    /**
     * Get multiple route alternatives for comparison
     */
    suspend fun getRouteAlternatives(
        start: RouteCoordinate,
        end: RouteCoordinate,
        maxAlternatives: Int = 3
    ): Result<List<OfflineRoute>>

    /**
     * Learn from user's route choice for future optimization
     */
    suspend fun learnFromRouteChoice(
        userId: String,
        chosenRoute: OfflineRoute,
        alternatives: List<OfflineRoute>,
        context: Map<String, Any> = emptyMap()
    )

    /**
     * Predict destination based on user context
     */
    suspend fun predictDestination(
        userId: String,
        currentLocation: RouteCoordinate,
        context: PredictionContext
    ): DestinationPrediction?

    /**
     * Get driving analytics for user
     */
    suspend fun getDrivingAnalytics(
        userId: String,
        startTime: Long,
        endTime: Long
    ): DrivingAnalytics?
}
