package com.jayesh.satnav.domain.repository

import com.jayesh.satnav.domain.model.AggregatedTrafficPattern
import com.jayesh.satnav.domain.model.TrafficAnalysis
import com.jayesh.satnav.domain.model.TrafficLearningConfig
import com.jayesh.satnav.domain.model.TrafficPattern
import com.jayesh.satnav.domain.model.TrafficPrediction
import com.jayesh.satnav.domain.model.TrafficVisualization
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Repository for traffic pattern data storage and retrieval.
 * Phase 13: Real-time Traffic Pattern Awareness
 */
interface TrafficPatternRepository {
    
    // ========== PATTERN RECORDING ==========
    
    /**
     * Record a traffic pattern for a road segment.
     * @param pattern The traffic pattern to record
     */
    suspend fun recordPattern(pattern: TrafficPattern)
    
    /**
     * Record multiple traffic patterns in batch.
     * @param patterns List of traffic patterns to record
     */
    suspend fun recordPatterns(patterns: List<TrafficPattern>)
    
    /**
     * Record travel time for a segment with automatic timestamp.
     * @param segmentId Road segment identifier
     * @param travelTimeSeconds Travel time in seconds
     * @param confidence Confidence in measurement (0.0-1.0)
     * @param vehicleCount Estimated vehicle count (optional)
     */
    suspend fun recordTravelTime(
        segmentId: String,
        travelTimeSeconds: Double,
        confidence: Double = 1.0,
        vehicleCount: Int? = null
    )
    
    // ========== PATTERN RETRIEVAL ==========
    
    /**
     * Get traffic patterns for a specific segment.
     * @param segmentId Road segment identifier
     * @param limit Maximum number of patterns to return
     * @param fromTime Start time filter (optional)
     * @param toTime End time filter (optional)
     */
    suspend fun getPatternsForSegment(
        segmentId: String,
        limit: Int = 100,
        fromTime: Instant? = null,
        toTime: Instant? = null
    ): List<TrafficPattern>
    
    /**
     * Get aggregated traffic patterns for prediction.
     * @param segmentId Road segment identifier
     * @param dayOfWeek Day of week (0-6, optional)
     * @param hourOfDay Hour of day (0-23, optional)
     */
    suspend fun getAggregatedPatterns(
        segmentId: String,
        dayOfWeek: Int? = null,
        hourOfDay: Int? = null
    ): List<AggregatedTrafficPattern>
    
    /**
     * Get traffic patterns for multiple segments.
     * @param segmentIds List of segment identifiers
     * @param limitPerSegment Maximum patterns per segment
     */
    suspend fun getPatternsForSegments(
        segmentIds: List<String>,
        limitPerSegment: Int = 20
    ): Map<String, List<TrafficPattern>>
    
    // ========== PREDICTIONS ==========
    
    /**
     * Get traffic prediction for a segment at a specific time.
     * @param segmentId Road segment identifier
     * @param targetTime Time to predict for
     * @param predictionHorizonHours How far ahead to predict
     */
    suspend fun getPrediction(
        segmentId: String,
        targetTime: Instant,
        predictionHorizonHours: Int = 1
    ): TrafficPrediction?
    
    /**
     * Get predictions for multiple segments.
     * @param segmentIds List of segment identifiers
     * @param targetTime Time to predict for
     */
    suspend fun getPredictions(
        segmentIds: List<String>,
        targetTime: Instant
    ): Map<String, TrafficPrediction>
    
    /**
     * Get traffic analysis for route planning.
     * @param segmentIds List of segment identifiers in the route
     * @param departureTime Planned departure time
     */
    suspend fun getRouteAnalysis(
        segmentIds: List<String>,
        departureTime: Instant
    ): TrafficAnalysis
    
    // ========== VISUALIZATION ==========
    
    /**
     * Get traffic visualization data for map display.
     * @param segmentIds List of segment identifiers to visualize
     * @param targetTime Time to visualize traffic for
     */
    suspend fun getVisualizationData(
        segmentIds: List<String>,
        targetTime: Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    ): List<TrafficVisualization>
    
    /**
     * Get congestion heatmap data for an area.
     * @param boundingBox Bounding box coordinates [minLat, minLon, maxLat, maxLon]
     * @param targetTime Time to get heatmap for
     * @param zoomLevel Map zoom level for detail
     */
    suspend fun getHeatmapData(
        boundingBox: List<Double>,
        targetTime: Instant,
        zoomLevel: Int
    ): List<TrafficVisualization>
    
    // ========== STATISTICS & INSIGHTS ==========
    
    /**
     * Get traffic pattern statistics.
     * @param segmentId Road segment identifier (optional)
     * @param fromTime Start time filter (optional)
     * @param toTime End time filter (optional)
     */
    suspend fun getStatistics(
        segmentId: String? = null,
        fromTime: Instant? = null,
        toTime: Instant? = null
    ): TrafficStatistics
    
    /**
     * Get traffic pattern insights for user.
     * @param limit Maximum number of insights to return
     */
    suspend fun getInsights(limit: Int = 5): List<TrafficInsight>
    
    /**
     * Check if we have enough data for reliable predictions.
     * @param segmentId Road segment identifier
     */
    suspend fun hasSufficientData(segmentId: String): Boolean
    
    // ========== CONFIGURATION ==========
    
    /**
     * Get current traffic learning configuration.
     */
    fun getConfig(): TrafficLearningConfig
    
    /**
     * Update traffic learning configuration.
     * @param config New configuration
     */
    suspend fun updateConfig(config: TrafficLearningConfig)
    
    // ========== MAINTENANCE ==========
    
    /**
     * Clean up old traffic pattern data.
     * @param olderThan Delete patterns older than this time
     */
    suspend fun cleanupOldData(olderThan: Instant)
    
    /**
     * Export traffic pattern data for backup or analysis.
     * @param format Export format (JSON, CSV)
     */
    suspend fun exportData(format: ExportFormat): String
    
    /**
     * Import traffic pattern data from backup.
     * @param data Data to import
     * @param format Import format
     */
    suspend fun importData(data: String, format: ExportFormat)
    
    /**
     * Clear all traffic pattern data.
     */
    suspend fun clearAllData()
}

/**
 * Traffic pattern statistics.
 */
data class TrafficStatistics(
    val totalRecords: Long,
    val uniqueSegments: Int,
    val oldestRecord: Instant?,
    val newestRecord: Instant?,
    val averageRecordsPerSegment: Double,
    val segmentsWithSufficientData: Int,
    val predictionAccuracy: Double? = null
)

/**
 * Traffic insight for user.
 */
data class TrafficInsight(
    val title: String,
    val description: String,
    val segmentId: String?,
    val confidence: Double,
    val recommendation: String? = null
)

/**
 * Data export/import formats.
 */
enum class ExportFormat {
    JSON,
    CSV
}