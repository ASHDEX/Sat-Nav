package com.jayesh.satnav.data.repository

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.data.local.graphhopper.GraphHopperManager
import com.jayesh.satnav.domain.model.*
import com.jayesh.satnav.domain.repository.RoutingRepository
import com.jayesh.satnav.features.ai.RouteOptimizer
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RoutingRepositoryImpl @Inject constructor(
    private val graphHopperManager: GraphHopperManager,
    private val routeOptimizerProvider: Provider<RouteOptimizer>,
    private val appDispatchers: AppDispatchers
) : RoutingRepository {
    private val routeOptimizer get() = routeOptimizerProvider.get()

    override suspend fun getRoutingEngineStatus(): RoutingEngineStatus {
        return graphHopperManager.getRoutingEngineStatus()
    }

    override suspend fun computeRoute(
        points: List<RouteCoordinate>,
        profile: RoutingProfile,
    ): Result<OfflineRoute> {
        return graphHopperManager.computeRoute(points, profile)
    }

    /**
     * Compute route with AI optimization
     */
    override suspend fun computeOptimizedRoute(
        start: RouteCoordinate,
        end: RouteCoordinate,
        userId: String,
        criteria: OptimizationCriteria,
        includePersonalization: Boolean
    ): Result<AiOptimizationResult> = withContext(appDispatchers.io) {
        try {
            val result = routeOptimizer.optimizeRoute(
                start = start,
                end = end,
                userId = userId,
                criteria = criteria,
                includePersonalization = includePersonalization
            )
            Result.success(result)
        } catch (e: Exception) {
            NavLog.e("AI route optimization failed", e)
            // Fallback to basic route
            computeRoute(listOf(start, end), RoutingProfile.CAR).fold(
                onSuccess = { route ->
                    val fallbackResult = AiOptimizationResult(
                        requestId = "fallback-${System.currentTimeMillis()}",
                        userId = userId,
                        start = start,
                        end = end,
                        criteria = criteria,
                        alternatives = listOf(
                            AiRouteAlternative(
                                route = route,
                                score = 0.7,
                                scoreBreakdown = ScoreBreakdown(
                                    timeScore = 0.7,
                                    distanceScore = 0.7,
                                    scenicScore = 0.5,
                                    safetyScore = 0.6,
                                    familiarityScore = 0.5,
                                    totalScore = 0.7
                                ),
                                aiInsights = listOf(
                                    AiInsight(
                                        type = InsightType.TIME_SAVING,
                                        message = "Standard route computed",
                                        confidence = 0.5,
                                        impact = ImpactLevel.LOW
                                    )
                                ),
                                personalizationLevel = PersonalizationLevel.NONE
                            )
                        ),
                        recommendedAlternativeIndex = 0,
                        processingTimeMs = 0,
                        modelVersion = "fallback-1.0"
                    )
                    Result.success(fallbackResult)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        }
    }

    /**
     * Get multiple route alternatives for comparison
     */
    override suspend fun getRouteAlternatives(
        start: RouteCoordinate,
        end: RouteCoordinate,
        maxAlternatives: Int
    ): Result<List<OfflineRoute>> = withContext(appDispatchers.io) {
        try {
            val alternatives = mutableListOf<OfflineRoute>()

            // 1. Fastest route
            val fastestRoute = computeRoute(listOf(start, end), RoutingProfile.CAR).getOrThrow()
            alternatives.add(fastestRoute)

            // 2. Shortest route (if different)
            if (maxAlternatives > 1) {
                // In real implementation, compute with different weightings
                // For now, create a variant
                val shortestVariant = createRouteVariant(fastestRoute, "shorter")
                alternatives.add(shortestVariant)
            }

            // 3. Scenic route (if different)
            if (maxAlternatives > 2) {
                val scenicVariant = createRouteVariant(fastestRoute, "scenic")
                alternatives.add(scenicVariant)
            }

            Result.success(alternatives.distinctBy { it.points.hashCode() })
        } catch (e: Exception) {
            NavLog.e("Failed to get route alternatives", e)
            Result.failure(e)
        }
    }

    /**
     * Learn from user's route choice for future optimization
     */
    override suspend fun learnFromRouteChoice(
        userId: String,
        chosenRoute: OfflineRoute,
        alternatives: List<OfflineRoute>,
        context: Map<String, Any>
    ) = withContext(appDispatchers.io) {
        routeOptimizer.learnFromRouteChoice(userId, chosenRoute, alternatives, context)
    }

    /**
     * Predict destination based on user context
     */
    override suspend fun predictDestination(
        userId: String,
        currentLocation: RouteCoordinate,
        context: PredictionContext
    ): DestinationPrediction? = withContext(appDispatchers.io) {
        routeOptimizer.predictDestination(userId, currentLocation, context)
    }

    /**
     * Get driving analytics for user
     */
    override suspend fun getDrivingAnalytics(
        userId: String,
        startTime: Long,
        endTime: Long
    ): DrivingAnalytics? = withContext(appDispatchers.io) {
        routeOptimizer.analyzeDrivingBehavior(userId, startTime, endTime)
    }

    /**
     * Create a route variant for alternative routing
     */
    private fun createRouteVariant(originalRoute: OfflineRoute, variantType: String): OfflineRoute {
        return when (variantType) {
            "shorter" -> {
                // Create shorter variant by simplifying the route
                val simplifiedPoints = if (originalRoute.points.size > 10) {
                    originalRoute.points.filterIndexed { index, _ -> index % 2 == 0 }
                } else {
                    originalRoute.points
                }
                
                originalRoute.copy(
                    points = simplifiedPoints,
                    distanceMeters = originalRoute.distanceMeters * 0.9, // 10% shorter
                    durationMillis = (originalRoute.durationMillis * 0.85).toLong() // 15% faster
                )
            }
            "scenic" -> {
                // Create scenic variant
                originalRoute.copy(
                    distanceMeters = originalRoute.distanceMeters * 1.1, // 10% longer
                    durationMillis = (originalRoute.durationMillis * 1.2).toLong(), // 20% slower
                    // Add scenic metadata
                )
            }
            else -> originalRoute
        }
    }
}
