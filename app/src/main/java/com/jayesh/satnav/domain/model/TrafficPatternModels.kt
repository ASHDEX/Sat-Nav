package com.jayesh.satnav.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Domain models for traffic pattern awareness system.
 * Phase 13: Real-time Traffic Pattern Awareness
 */

/**
 * Represents a traffic pattern record for a specific road segment.
 * @param segmentId Unique identifier for the road segment (could be OSM way ID or internal segment hash)
 * @param travelTimeSeconds Average travel time in seconds for this segment
 * @param timestamp When this travel time was recorded
 * @param dayOfWeek Day of week (0=Sunday, 6=Saturday)
 * @param hourOfDay Hour of day (0-23)
 * @param confidence How confident we are in this measurement (0.0-1.0)
 * @param vehicleCount Estimated number of vehicles during measurement
 * @param weatherCondition Weather condition during measurement (optional)
 */
data class TrafficPattern(
    val segmentId: String,
    val travelTimeSeconds: Double,
    val timestamp: Instant,
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val confidence: Double = 1.0,
    val vehicleCount: Int? = null,
    val weatherCondition: WeatherCondition? = null
) {
    companion object {
        fun fromNow(
            segmentId: String,
            travelTimeSeconds: Double,
            confidence: Double = 1.0,
            vehicleCount: Int? = null,
            weatherCondition: WeatherCondition? = null
        ): TrafficPattern {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
            
            return TrafficPattern(
                segmentId = segmentId,
                travelTimeSeconds = travelTimeSeconds,
                timestamp = now,
                dayOfWeek = localDateTime.dayOfWeek.value % 7, // Convert to 0-6 (Sun-Sat)
                hourOfDay = localDateTime.hour,
                confidence = confidence,
                vehicleCount = vehicleCount,
                weatherCondition = weatherCondition
            )
        }
    }
}

/**
 * Represents aggregated traffic pattern for prediction.
 * @param segmentId Road segment identifier
 * @param dayOfWeek Day of week (0-6)
 * @param hourOfDay Hour of day (0-23)
 * @param averageTravelTimeSeconds Average travel time across all records
 * @param minTravelTimeSeconds Minimum observed travel time
 * @param maxTravelTimeSeconds Maximum observed travel time
 * @param standardDeviation Standard deviation of travel times
 * @param recordCount Number of records used for this aggregation
 * @param congestionLevel Estimated congestion level
 */
data class AggregatedTrafficPattern(
    val segmentId: String,
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val averageTravelTimeSeconds: Double,
    val minTravelTimeSeconds: Double,
    val maxTravelTimeSeconds: Double,
    val standardDeviation: Double,
    val recordCount: Int,
    val congestionLevel: CongestionLevel
)

/**
 * Congestion level classification.
 */
enum class CongestionLevel(val displayName: String, val colorHex: String) {
    FREE_FLOW("Free Flow", "#00FF00"),      // Green
    LIGHT("Light", "#FFFF00"),              // Yellow
    MODERATE("Moderate", "#FFA500"),        // Orange
    HEAVY("Heavy", "#FF0000"),              // Red
    SEVERE("Severe", "#8B0000");            // Dark Red
    
    companion object {
        fun fromSpeedRatio(ratio: Double): CongestionLevel {
            return when {
                ratio >= 0.9 -> FREE_FLOW
                ratio >= 0.7 -> LIGHT
                ratio >= 0.5 -> MODERATE
                ratio >= 0.3 -> HEAVY
                else -> SEVERE
            }
        }
    }
}

/**
 * Weather conditions that can affect traffic.
 */
enum class WeatherCondition(val displayName: String) {
    CLEAR("Clear"),
    CLOUDY("Cloudy"),
    RAIN("Rain"),
    HEAVY_RAIN("Heavy Rain"),
    SNOW("Snow"),
    FOG("Fog"),
    WINDY("Windy");
}

/**
 * Traffic prediction for a specific time period.
 * @param segmentId Road segment identifier
 * @param predictedTravelTimeSeconds Predicted travel time in seconds
 * @param confidence Confidence score (0.0-1.0)
 * @param congestionLevel Predicted congestion level
 * @param alternativeRoutes Suggested alternative routes if congested
 * @param timeWindowStart Start of prediction time window
 * @param timeWindowEnd End of prediction time window
 */
data class TrafficPrediction(
    val segmentId: String,
    val predictedTravelTimeSeconds: Double,
    val confidence: Double,
    val congestionLevel: CongestionLevel,
    val alternativeRoutes: List<String> = emptyList(),
    val timeWindowStart: Instant,
    val timeWindowEnd: Instant
)

/**
 * Traffic pattern learning configuration.
 * @param learningEnabled Whether to learn from user's travel patterns
 * @param privacyMode Privacy mode for data collection
 * @param minimumRecordsForPrediction Minimum records needed before making predictions
 * @param predictionHorizonHours How far ahead to predict (in hours)
 */
data class TrafficLearningConfig(
    val learningEnabled: Boolean = true,
    val privacyMode: PrivacyMode = PrivacyMode.ANONYMIZED,
    val minimumRecordsForPrediction: Int = 10,
    val predictionHorizonHours: Int = 24
)

/**
 * Privacy modes for traffic data collection.
 */
enum class PrivacyMode {
    /**
     * No data collection.
     */
    DISABLED,
    
    /**
     * Collect anonymized data (no personal identifiers).
     */
    ANONYMIZED,
    
    /**
     * Collect personalized data for better predictions.
     */
    PERSONALIZED
}

/**
 * Traffic pattern analysis result.
 * @param segmentId Road segment identifier
 * @param currentTravelTime Current observed travel time
 * @param predictedTravelTime Predicted travel time based on patterns
 * @param timeSavingsPotential Potential time savings by taking alternative route
 * @param confidence How confident we are in the prediction
 * @param recommendations List of recommendations for this segment
 */
data class TrafficAnalysis(
    val segmentId: String,
    val currentTravelTime: Double,
    val predictedTravelTime: Double,
    val timeSavingsPotential: Double,
    val confidence: Double,
    val recommendations: List<TrafficRecommendation>
)

/**
 * Traffic recommendations.
 */
sealed interface TrafficRecommendation {
    data class AlternativeRoute(
        val routeId: String,
        val estimatedTimeSavings: Double,
        val reason: String
    ) : TrafficRecommendation
    
    data class TimeAdjustment(
        val suggestedDepartureTime: Instant,
        val estimatedTimeSavings: Double
    ) : TrafficRecommendation
    
    data class SpeedAdjustment(
        val suggestedSpeed: Double,
        val reason: String
    ) : TrafficRecommendation
}

/**
 * Traffic pattern visualization data for UI.
 * @param segmentId Road segment identifier
 * @param congestionLevel Current/predicted congestion level
 * @param colorHex Color to display on map
 * @param tooltipText Text to show on hover/tap
 * @param confidence Confidence in the visualization data
 */
data class TrafficVisualization(
    val segmentId: String,
    val congestionLevel: CongestionLevel,
    val colorHex: String,
    val tooltipText: String,
    val confidence: Double
)