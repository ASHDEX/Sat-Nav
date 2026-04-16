package com.jayesh.satnav.features.ai

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Machine learning component for learning user's route preferences
 *
 * Uses on-device ML to learn patterns from user's route choices
 * and driving behavior.
 */
@Singleton
class PreferenceLearner @Inject constructor(
    private val appDispatchers: AppDispatchers
) {
    private val userPreferences = mutableMapOf<String, MutableList<RoutePreference>>()
    private val userBehaviorHistory = mutableMapOf<String, MutableList<UserBehavior>>()

    /**
     * Get user's current preferences
     */
    suspend fun getUserPreferences(userId: String): List<RoutePreference> = withContext(appDispatchers.io) {
        return@withContext userPreferences[userId] ?: emptyList()
    }

    /**
     * Learn from user's route choice
     */
    suspend fun learnFromChoice(
        userId: String,
        chosenRoute: OfflineRoute,
        alternatives: List<OfflineRoute>,
        context: Map<String, Any> = emptyMap()
    ) = withContext(appDispatchers.io) {
        try {
            // Extract features from chosen route
            val chosenFeatures = extractRouteFeatures(chosenRoute)
            
            // Extract features from alternatives
            val alternativeFeatures = alternatives.map { extractRouteFeatures(it) }
            
            // Determine what made this route preferable
            val preferences = inferPreferences(chosenFeatures, alternativeFeatures, context)
            
            // Update user preferences
            updateUserPreferences(userId, preferences)
            
            NavLog.i("Learned ${preferences.size} preferences from user $userId route choice")
        } catch (e: Exception) {
            NavLog.e("Failed to learn from route choice", e)
        }
    }

    /**
     * Record user behavior for later analysis
     */
    suspend fun recordBehavior(behavior: UserBehavior) = withContext(appDispatchers.io) {
        val userId = behavior.userId
        val history = userBehaviorHistory.getOrPut(userId) { mutableListOf() }
        history.add(behavior)
        
        // Keep only last 100 behaviors per user
        if (history.size > 100) {
            history.removeAt(0)
        }
        
        // Learn from behavior patterns
        learnFromBehaviorPatterns(userId)
    }

    /**
     * Get behavior history for a user
     */
    suspend fun getBehaviorHistory(userId: String, limit: Int = 50): List<UserBehavior> = withContext(appDispatchers.io) {
        return@withContext userBehaviorHistory[userId]?.takeLast(limit) ?: emptyList()
    }

    /**
     * Clear all learned preferences for a user
     */
    suspend fun clearPreferences(userId: String) = withContext(appDispatchers.io) {
        userPreferences.remove(userId)
        userBehaviorHistory.remove(userId)
    }

    /**
     * Extract features from a route for ML analysis
     */
    private fun extractRouteFeatures(route: OfflineRoute): RouteFeatures {
        val points = route.points
        val distance = route.distanceMeters
        val duration = route.durationMillis / 1000.0
        
        // Calculate various features
        val straightness = calculateStraightness(points)
        val elevationVariation = calculateElevationVariation(points)
        val curvature = calculateCurvature(points)
        val roadTypeDistribution = estimateRoadTypeDistribution(points)
        val intersectionDensity = estimateIntersectionDensity(points)
        
        return RouteFeatures(
            distance = distance,
            duration = duration,
            averageSpeed = distance / duration,
            straightness = straightness,
            elevationVariation = elevationVariation,
            curvature = curvature,
            roadTypeDistribution = roadTypeDistribution,
            intersectionDensity = intersectionDensity,
            timeOfDay = extractTimeOfDayFromContext(),
            estimatedScenicScore = estimateScenicScore(points)
        )
    }

    /**
     * Infer preferences by comparing chosen route with alternatives
     */
    private fun inferPreferences(
        chosenFeatures: RouteFeatures,
        alternativeFeatures: List<RouteFeatures>,
        context: Map<String, Any>
    ): List<RoutePreference> {
        if (alternativeFeatures.isEmpty()) return emptyList()
        
        val preferences = mutableListOf<RoutePreference>()
        
        // Compare each feature
        val timeOfDay = chosenFeatures.timeOfDay
        val dayOfWeek = extractDayOfWeekFromContext(context)
        
        // 1. Time preference
        val timeDifference = alternativeFeatures.map { it.duration - chosenFeatures.duration }.average()
        if (timeDifference > 300) { // 5 minutes difference
            preferences.add(
                createPreference(
                    preferenceType = PreferenceType.PREFER,
                    featureType = "time_efficiency",
                    weight = min(1.0, timeDifference / 600), // Scale to 0-1
                    timeOfDay = timeOfDay,
                    dayOfWeek = dayOfWeek
                )
            )
        }
        
        // 2. Distance preference
        val distanceDifference = alternativeFeatures.map { it.distance - chosenFeatures.distance }.average()
        if (distanceDifference > 1000) { // 1 km difference
            preferences.add(
                createPreference(
                    preferenceType = PreferenceType.PREFER,
                    featureType = "short_distance",
                    weight = min(1.0, distanceDifference / 5000),
                    timeOfDay = timeOfDay,
                    dayOfWeek = dayOfWeek
                )
            )
        }
        
        // 3. Scenic preference
        val scenicDifference = chosenFeatures.estimatedScenicScore - alternativeFeatures.map { it.estimatedScenicScore }.average()
        if (scenicDifference > 0.2) {
            preferences.add(
                createPreference(
                    preferenceType = PreferenceType.PREFER,
                    featureType = "scenic_routes",
                    weight = scenicDifference,
                    timeOfDay = timeOfDay,
                    dayOfWeek = dayOfWeek
                )
            )
        }
        
        // 4. Straightness preference
        val straightnessDifference = chosenFeatures.straightness - alternativeFeatures.map { it.straightness }.average()
        if (abs(straightnessDifference) > 0.15) {
            val prefType = if (straightnessDifference > 0) PreferenceType.PREFER else PreferenceType.AVOID
            preferences.add(
                createPreference(
                    preferenceType = prefType,
                    featureType = "straight_roads",
                    weight = abs(straightnessDifference),
                    timeOfDay = timeOfDay,
                    dayOfWeek = dayOfWeek
                )
            )
        }
        
        return preferences
    }

    /**
     * Update user preferences with new learnings
     */
    private fun updateUserPreferences(userId: String, newPreferences: List<RoutePreference>) {
        val existingPreferences = userPreferences.getOrPut(userId) { mutableListOf() }
        
        for (newPref in newPreferences) {
            // Check if similar preference exists
            val existingIndex = existingPreferences.indexOfFirst { pref ->
                pref.preferenceType == newPref.preferenceType &&
                pref.roadType == newPref.roadType &&
                pref.timeOfDay == newPref.timeOfDay &&
                pref.dayOfWeek == newPref.dayOfWeek
            }
            
            if (existingIndex >= 0) {
                // Update existing preference
                val existing = existingPreferences[existingIndex]
                val updatedWeight = (existing.weight * existing.usageCount + newPref.weight) / (existing.usageCount + 1)
                val updatedConfidence = min(1.0, existing.confidence + 0.1)
                
                existingPreferences[existingIndex] = existing.copy(
                    weight = updatedWeight,
                    confidence = updatedConfidence,
                    lastUpdated = System.currentTimeMillis(),
                    usageCount = existing.usageCount + 1
                )
            } else {
                // Add new preference
                existingPreferences.add(newPref)
            }
        }
        
        // Remove low-confidence preferences
        existingPreferences.removeAll { pref ->
            pref.confidence < 0.2 && pref.lastUpdated < System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L // 30 days
        }
        
        // Keep only top 20 preferences
        if (existingPreferences.size > 20) {
            existingPreferences.sortByDescending { it.confidence * it.usageCount }
            existingPreferences.subList(20, existingPreferences.size).clear()
        }
    }

    /**
     * Learn from behavior patterns over time
     */
    private suspend fun learnFromBehaviorPatterns(userId: String) = withContext(appDispatchers.io) {
        val behaviors = userBehaviorHistory[userId] ?: return@withContext
        
        if (behaviors.size < 10) return@withContext // Need more data
        
        // Analyze patterns
        val timeOfDayPatterns = analyzeTimeOfDayPatterns(behaviors)
        val roadTypePatterns = analyzeRoadTypePatterns(behaviors)
        val speedPatterns = analyzeSpeedPatterns(behaviors)
        
        // Create preferences from patterns
        val patternPreferences = mutableListOf<RoutePreference>()
        
        timeOfDayPatterns.forEach { (timeOfDay, preferenceStrength) ->
            if (preferenceStrength > 0.3) {
                patternPreferences.add(
                    createPreference(
                        preferenceType = PreferenceType.PREFER,
                        featureType = "time_pattern",
                        weight = preferenceStrength,
                        timeOfDay = timeOfDay,
                        dayOfWeek = null
                    )
                )
            }
        }
        
        // Update preferences
        updateUserPreferences(userId, patternPreferences)
    }

    /**
     * Analyze time of day patterns from behavior history
     */
    private fun analyzeTimeOfDayPatterns(behaviors: List<UserBehavior>): Map<TimeOfDay, Double> {
        val patternCounts = mutableMapOf<TimeOfDay, Int>()
        val totalTrips = behaviors.size
        
        behaviors.forEach { behavior ->
            patternCounts[behavior.timeOfDay] = patternCounts.getOrDefault(behavior.timeOfDay, 0) + 1
        }
        
        return patternCounts.mapValues { (_, count) ->
            count.toDouble() / totalTrips
        }
    }

    /**
     * Analyze road type preferences from behavior history
     */
    private fun analyzeRoadTypePatterns(behaviors: List<UserBehavior>): Map<RoadType, Double> {
        val roadTypeDistances = mutableMapOf<RoadType, Double>()
        var totalDistance = 0.0
        
        behaviors.forEach { behavior ->
            behavior.roadTypes.forEach { (roadType, distance) ->
                roadTypeDistances[roadType] = roadTypeDistances.getOrDefault(roadType, 0.0) + distance
                totalDistance += distance
            }
        }
        
        return if (totalDistance > 0) {
            roadTypeDistances.mapValues { (_, distance) -> distance / totalDistance }
        } else {
            emptyMap()
        }
    }

    /**
     * Analyze speed patterns from behavior history
     */
    private fun analyzeSpeedPatterns(behaviors: List<UserBehavior>): Map<String, Double> {
        if (behaviors.isEmpty()) return emptyMap()
        
        val averageSpeeds = behaviors.map { it.averageSpeed }
        val avgSpeed = averageSpeeds.average()
        val speedStdDev = calculateStandardDeviation(averageSpeeds)
        
        return mapOf(
            "average_speed" to avgSpeed,
            "speed_consistency" to if (speedStdDev > 0) 1.0 / (1.0 + speedStdDev) else 1.0
        )
    }

    // Helper methods
    private fun calculateStraightness(points: List<RouteCoordinate>): Double {
        if (points.size < 2) return 1.0
        
        val straightLineDistance = haversineDistance(
            points.first().latitude,
            points.first().longitude,
            points.last().latitude,
            points.last().longitude
        )
        
        val routeDistance = calculateRouteDistance(points)
        
        return if (routeDistance > 0) straightLineDistance / routeDistance else 1.0
    }

    private fun calculateElevationVariation(points: List<RouteCoordinate>): Double {
        // Simplified - in real implementation, use elevation data
        return 0.5
    }

    private fun calculateCurvature(points: List<RouteCoordinate>): Double {
        if (points.size < 3) return 0.0
        
        var totalAngle = 0.0
        for (i in 1 until points.size - 1) {
            val angle = calculateAngle(points[i-1], points[i], points[i+1])
            totalAngle += abs(angle)
        }
        
        return totalAngle / (points.size - 2)
    }

    private fun estimateRoadTypeDistribution(points: List<RouteCoordinate>): Map<RoadType, Double> {
        // Simplified estimation
        // In real implementation, use OSM data or other sources
        return mapOf(
            RoadType.MOTORWAY to 0.3,
            RoadType.PRIMARY to 0.4,
            RoadType.SECONDARY to 0.2,
            RoadType.TERTIARY to 0.1
        )
    }

    private fun estimateIntersectionDensity(points: List<RouteCoordinate>): Double {
        // Simplified - assume 1 intersection per km
        val distanceKm = calculateRouteDistance(points) / 1000
        return if (distanceKm > 0) 1.0 / distanceKm else 0.0
    }

    private fun estimateScenicScore(points: List<RouteCoordinate>): Double {
        // Simplified scenic score based on curvature and elevation
        val curvature = calculateCurvature(points)
        val elevationVariation = calculateElevationVariation(points)
        
        return (0.3 + curvature * 0.3 + elevationVariation * 0.4).coerceIn(0.0, 1.0)
    }

    private fun extractTimeOfDayFromContext(): TimeOfDay {
        // Simplified - in real implementation, use actual time
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 6..11 -> TimeOfDay.MORNING
            hour in 12..17 -> TimeOfDay.AFTERNOON
            hour in 18..21 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }

    private fun extractDayOfWeekFromContext(context: Map<String, Any>): DayOfWeek? {
        // Simplified - in real implementation, extract from context
        return DayOfWeek.WEEKDAY
    }

    private fun createPreference(
        preferenceType: PreferenceType,
        featureType: String,
        weight: Double,
        timeOfDay: TimeOfDay?,
        dayOfWeek: DayOfWeek?
    ): RoutePreference {
        return RoutePreference(
            id = UUID.randomUUID().toString(),
            userId = "current", // Will be replaced with actual user ID
            preferenceType = preferenceType,
            roadType = when (featureType) {
                "highway" -> RoadType.MOTORWAY
                "primary_road" -> RoadType.PRIMARY
                "secondary_road" -> RoadType.SECONDARY
                "tertiary_road" -> RoadType.TERTIARY
                "residential" -> RoadType.RESIDENTIAL
                else -> null
            },
            timeOfDay = timeOfDay,
            dayOfWeek = dayOfWeek,
            weight = weight.coerceIn(0.0, 1.0),
            confidence = 0.5, // Initial confidence
            lastUpdated = System.currentTimeMillis(),
            usageCount = 1
        )
    }

    private fun calculateRouteDistance(points: List<RouteCoordinate>): Double {
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            totalDistance += haversineDistance(
                points[i].latitude, points[i].longitude,
                points[i+1].latitude, points[i+1].longitude
            )
        }
        return totalDistance
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun calculateAngle(
        a: RouteCoordinate,
        b: RouteCoordinate,
        c: RouteCoordinate
    ): Double {
        val ab = haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
        val bc = haversineDistance(b.latitude, b.longitude, c.latitude, c.longitude)
        val ac = haversineDistance(a.latitude, a.longitude, c.latitude, c.longitude)
        
        val cosAngle = (ab * ab + bc * bc - ac * ac) / (2 * ab * bc)
        return Math.toDegrees(acos(cosAngle.coerceIn(-1.0, 1.0)))
    }

    private fun calculateStandardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    /**
     * Internal data class for route features
     */
    private data class RouteFeatures(
        val distance: Double,
        val duration: Double,
        val averageSpeed: Double,
        val straightness: Double,
        val elevationVariation: Double,
        val curvature: Double,
        val roadTypeDistribution: Map<RoadType, Double>,
        val intersectionDensity: Double,
        val timeOfDay: TimeOfDay,
        val estimatedScenicScore: Double
    )
}