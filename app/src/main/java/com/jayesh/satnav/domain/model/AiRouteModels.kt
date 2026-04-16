package com.jayesh.satnav.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain models for AI-powered route optimization
 */

/**
 * User's route preferences learned over time
 */
@Serializable
data class RoutePreference(
    val id: String,
    val userId: String,
    val preferenceType: PreferenceType,
    val roadType: RoadType? = null,
    val timeOfDay: TimeOfDay? = null,
    val dayOfWeek: DayOfWeek? = null,
    val weatherCondition: WeatherCondition? = null,
    val weight: Double, // 0.0 to 1.0, higher = stronger preference
    val confidence: Double, // 0.0 to 1.0, confidence in this preference
    val lastUpdated: Long,
    val usageCount: Int
)

enum class PreferenceType {
    PREFER, // User prefers this type of route
    AVOID,  // User avoids this type of route
    NEUTRAL // No strong preference
}

enum class TimeOfDay {
    MORNING,    // 6:00 - 12:00
    AFTERNOON,  // 12:00 - 18:00
    EVENING,    // 18:00 - 22:00
    NIGHT,      // 22:00 - 6:00
    RUSH_HOUR   // 7:00-10:00, 16:00-19:00
}

enum class DayOfWeek {
    WEEKDAY,
    WEEKEND,
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

// WeatherCondition enum is defined in TrafficPatternModels.kt
// import com.jayesh.satnav.domain.model.WeatherCondition

/**
 * User behavior data collected during navigation
 */
@Serializable
data class UserBehavior(
    val sessionId: String,
    val userId: String,
    val routeId: String? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistance: Double, // meters
    val totalDuration: Long, // milliseconds
    val averageSpeed: Double, // km/h
    val maxSpeed: Double, // km/h
    val brakingEvents: Int,
    val accelerationEvents: Int,
    val sharpTurns: Int,
    val routeDeviations: Int,
    val roadTypes: Map<RoadType, Double>, // road type -> distance traveled
    val timeOfDay: TimeOfDay,
    val weatherCondition: WeatherCondition? = null,
    val userFeedback: UserFeedback? = null
)

@Serializable
data class UserFeedback(
    val rating: Int, // 1-5 stars
    val comments: String? = null,
    val likedAspects: List<String> = emptyList(),
    val dislikedAspects: List<String> = emptyList(),
    val wouldTakeAgain: Boolean? = null
)

/**
 * AI optimization criteria and weights
 */
@Serializable
data class OptimizationCriteria(
    val timeWeight: Double = 0.4,
    val distanceWeight: Double = 0.3,
    val scenicWeight: Double = 0.1,
    val safetyWeight: Double = 0.1,
    val familiarityWeight: Double = 0.1,
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
    val preferScenicRoutes: Boolean = false,
    val preferQuietRoads: Boolean = false
) {
    init {
        val total = timeWeight + distanceWeight + scenicWeight + safetyWeight + familiarityWeight
        require(total in 0.95..1.05) { "Weights must sum to approximately 1.0, got $total" }
    }
}

/**
 * AI optimization result with multiple route alternatives
 */
@Serializable
data class AiOptimizationResult(
    val requestId: String,
    val userId: String,
    val start: RouteCoordinate,
    val end: RouteCoordinate,
    val criteria: OptimizationCriteria,
    val alternatives: List<AiRouteAlternative>,
    val recommendedAlternativeIndex: Int,
    val processingTimeMs: Long,
    val modelVersion: String
)

@Serializable
data class AiRouteAlternative(
    val route: OfflineRoute,
    val score: Double, // 0.0 to 1.0, higher is better
    val scoreBreakdown: ScoreBreakdown,
    val aiInsights: List<AiInsight>,
    val personalizationLevel: PersonalizationLevel
)

@Serializable
data class ScoreBreakdown(
    val timeScore: Double,
    val distanceScore: Double,
    val scenicScore: Double,
    val safetyScore: Double,
    val familiarityScore: Double,
    val totalScore: Double
)

@Serializable
data class AiInsight(
    val type: InsightType,
    val message: String,
    val confidence: Double,
    val impact: ImpactLevel
)

enum class InsightType {
    SCENIC_ROUTE,           // Route passes through scenic areas
    TIME_SAVING,           // Saves significant time vs alternatives
    SAFE_ROUTE,            // Fewer dangerous intersections
    FAMILIAR_ROUTE,        // User has taken similar routes before
    AVOIDS_TRAFFIC,        // Avoids known traffic hotspots
    GOOD_FOR_WEATHER,      // Suitable for current weather
    ENERGY_EFFICIENT,      // Good for fuel/battery efficiency
    AVOIDS_CONSTRUCTION,   // Avoids known construction zones
    PREFERRED_BY_USERS     // Popular with other users
}

enum class ImpactLevel {
    LOW,
    MEDIUM,
    HIGH
}

enum class PersonalizationLevel {
    NONE,           // No personalization applied
    BASIC,          // Basic preferences only
    MODERATE,       // Some personalization
    HIGH,           // Strong personalization
    MAXIMUM         // Fully personalized
}

/**
 * Destination prediction based on user behavior
 */
@Serializable
data class DestinationPrediction(
    val userId: String,
    val predictedDestination: RouteCoordinate,
    val destinationName: String? = null,
    val confidence: Double,
    val context: PredictionContext,
    val reasons: List<PredictionReason>,
    val estimatedDepartureTime: Long? = null,
    val estimatedArrivalTime: Long? = null
)

@Serializable
data class PredictionContext(
    val timeOfDay: TimeOfDay,
    val dayOfWeek: DayOfWeek,
    val locationContext: LocationContext,
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val recentDestinations: List<RecentDestination> = emptyList()
)

@Serializable
data class LocationContext(
    val currentLocation: RouteCoordinate,
    val homeLocation: RouteCoordinate? = null,
    val workLocation: RouteCoordinate? = null,
    val frequentLocations: List<FrequentLocation> = emptyList()
)

@Serializable
data class CalendarEvent(
    val title: String,
    val location: String? = null,
    val startTime: Long,
    val endTime: Long,
    val locationCoordinates: RouteCoordinate? = null
)

@Serializable
data class RecentDestination(
    val destination: RouteCoordinate,
    val name: String? = null,
    val visitCount: Int,
    val lastVisitTime: Long,
    val averageVisitDuration: Long? = null
)

@Serializable
data class FrequentLocation(
    val location: RouteCoordinate,
    val name: String,
    val visitCount: Int,
    val lastVisitTime: Long,
    val typicalVisitTimes: List<TimeOfDay> = emptyList(),
    val typicalVisitDays: List<DayOfWeek> = emptyList()
)

@Serializable
data class PredictionReason(
    val type: ReasonType,
    val description: String,
    val confidence: Double
)

enum class ReasonType {
    TIME_OF_DAY,        // User typically goes here at this time
    DAY_OF_WEEK,        // User typically goes here on this day
    CALENDAR_EVENT,     // Matches calendar event
    FREQUENT_DESTINATION, // User visits frequently
    LOCATION_PROXIMITY, // Close to current location
    ROUTINE_PATTERN,    // Part of established routine
    WEATHER_RELATED,    // Weather suggests this destination
    SOCIAL_CONTEXT      // Social patterns suggest destination
}

/**
 * Driving analytics and insights
 */
@Serializable
data class DrivingAnalytics(
    val userId: String,
    val periodStart: Long,
    val periodEnd: Long,
    val totalDistance: Double, // meters
    val totalDuration: Long, // milliseconds
    val trips: Int,
    val averageSpeed: Double, // km/h
    val efficiencyScore: Double, // 0-100
    val safetyScore: Double, // 0-100
    val smoothnessScore: Double, // 0-100
    val insights: List<DrivingInsight>,
    val recommendations: List<DrivingRecommendation>,
    val comparisonToAverage: ComparisonStats
)

@Serializable
data class DrivingInsight(
    val type: DrivingInsightType,
    val message: String,
    val data: Map<String, Any>,
    val trend: TrendDirection
)

enum class DrivingInsightType {
    SPEED_PATTERN,
    BRAKING_PATTERN,
    ACCELERATION_PATTERN,
    ROUTE_EFFICIENCY,
    TIME_MANAGEMENT,
    FUEL_EFFICIENCY,
    SAFETY_METRICS,
    ROUTE_PREFERENCES
}

enum class TrendDirection {
    IMPROVING,
    DECLINING,
    STABLE,
    VARIABLE
}

@Serializable
data class DrivingRecommendation(
    val type: RecommendationType,
    val title: String,
    val description: String,
    val priority: PriorityLevel,
    val estimatedImpact: ImpactEstimate,
    val actionSteps: List<String>
)

enum class RecommendationType {
    ROUTE_OPTIMIZATION,
    DRIVING_STYLE,
    TIME_MANAGEMENT,
    VEHICLE_EFFICIENCY,
    SAFETY_IMPROVEMENT,
    COST_SAVING
}

enum class PriorityLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

@Serializable
data class ImpactEstimate(
    val timeSavingsMinutes: Double? = null,
    val distanceSavingsMeters: Double? = null,
    val fuelSavingsLiters: Double? = null,
    val safetyImprovementPercent: Double? = null
)

@Serializable
data class ComparisonStats(
    val vsPersonalAverage: ComparisonMetrics,
    val vsRegionalAverage: ComparisonMetrics? = null
)

@Serializable
data class ComparisonMetrics(
    val distancePercent: Double, // +10% means 10% more than average
    val durationPercent: Double,
    val efficiencyPercent: Double,
    val safetyPercent: Double
)