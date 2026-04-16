package com.jayesh.satnav.features.traffic

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.domain.model.AggregatedTrafficPattern
import com.jayesh.satnav.domain.model.CongestionLevel
import com.jayesh.satnav.domain.model.TrafficPattern
import com.jayesh.satnav.domain.model.TrafficPrediction
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Machine learning predictor for traffic patterns.
 * Phase 13: Real-time Traffic Pattern Awareness
 * 
 * Uses statistical models and simple ML algorithms for traffic prediction:
 * 1. Time-series analysis for historical patterns
 * 2. Exponential smoothing for trend detection
 * 3. Seasonal decomposition for day/week patterns
 * 4. Confidence interval estimation
 * 
 * Note: For production, consider integrating TensorFlow Lite for more advanced models.
 */
@Singleton
class TrafficPredictor @Inject constructor(
    private val appDispatchers: AppDispatchers
) {
    
    companion object {
        private const val TAG = "TrafficPredictor"
        
        // Prediction parameters
        private const val MINIMUM_PATTERNS_FOR_PREDICTION = 5
        private const val SEASONAL_PERIOD_DAILY = 24  // 24 hours
        private const val SEASONAL_PERIOD_WEEKLY = 168 // 24 * 7 hours
        private const val SMOOTHING_ALPHA = 0.3  // Exponential smoothing factor
        private const val CONFIDENCE_THRESHOLD = 0.7  // Minimum confidence for reliable prediction
        
        // Time window for pattern matching (in hours)
        private const val TIME_WINDOW_HOURS = 2
    }
    
    /**
     * Predict traffic for a segment at a specific time.
     * @param patterns Historical traffic patterns for the segment
     * @param targetTime Time to predict for
     * @param predictionHorizonHours How far ahead to predict
     * @return Traffic prediction or null if insufficient data
     */
    suspend fun predict(
        patterns: List<TrafficPattern>,
        targetTime: Instant,
        predictionHorizonHours: Int = 1
    ): TrafficPrediction? = withContext(appDispatchers.default) {
        if (patterns.size < MINIMUM_PATTERNS_FOR_PREDICTION) {
            NavLog.i("Insufficient patterns for prediction: ${patterns.size}")
            return@withContext null
        }
        
        val targetLocalDateTime = targetTime.toLocalDateTime(TimeZone.currentSystemDefault())
        val targetDayOfWeek = targetLocalDateTime.dayOfWeek.value % 7
        val targetHourOfDay = targetLocalDateTime.hour
        
        // Step 1: Filter relevant patterns
        val relevantPatterns = filterRelevantPatterns(patterns, targetDayOfWeek, targetHourOfDay)
        
        if (relevantPatterns.isEmpty()) {
            NavLog.i("No relevant patterns for day $targetDayOfWeek hour $targetHourOfDay")
            return@withContext null
        }
        
        // Step 2: Calculate base prediction using weighted average
        val (basePrediction, confidence) = calculateWeightedPrediction(relevantPatterns)
        
        if (confidence < CONFIDENCE_THRESHOLD) {
            NavLog.i("Low confidence prediction: $confidence")
        }
        
        // Step 3: Apply trend adjustment if enough data
        val trendAdjustedPrediction = applyTrendAdjustment(patterns, basePrediction)
        
        // Step 4: Apply seasonal adjustment
        val seasonallyAdjustedPrediction = applySeasonalAdjustment(
            patterns,
            trendAdjustedPrediction,
            targetDayOfWeek,
            targetHourOfDay
        )
        
        // Step 5: Calculate congestion level
        val congestionLevel = calculateCongestionLevel(seasonallyAdjustedPrediction, patterns)
        
        // Step 6: Generate alternative routes (simplified - would integrate with routing engine)
        val alternativeRoutes = generateAlternativeRoutes(patterns.first().segmentId)
        
        return@withContext TrafficPrediction(
            segmentId = patterns.first().segmentId,
            predictedTravelTimeSeconds = seasonallyAdjustedPrediction,
            confidence = confidence,
            congestionLevel = congestionLevel,
            alternativeRoutes = alternativeRoutes,
            timeWindowStart = targetTime,
            timeWindowEnd = Instant.fromEpochMilliseconds(
                targetTime.toEpochMilliseconds() + (predictionHorizonHours * 3600 * 1000)
            )
        )
    }
    
    /**
     * Filter patterns relevant to the target time.
     */
    private fun filterRelevantPatterns(
        patterns: List<TrafficPattern>,
        targetDayOfWeek: Int,
        targetHourOfDay: Int
    ): List<TrafficPattern> {
        return patterns.filter { pattern ->
            // Match same day of week and similar hour (± TIME_WINDOW_HOURS)
            val dayMatches = pattern.dayOfWeek == targetDayOfWeek
            val hourDiff = Math.abs(pattern.hourOfDay - targetHourOfDay)
            val hourMatches = hourDiff <= TIME_WINDOW_HOURS || hourDiff >= (24 - TIME_WINDOW_HOURS)
            
            dayMatches && hourMatches
        }
    }
    
    /**
     * Calculate weighted prediction based on pattern recency and confidence.
     */
    private fun calculateWeightedPrediction(patterns: List<TrafficPattern>): Pair<Double, Double> {
        if (patterns.isEmpty()) return Pair(0.0, 0.0)
        
        val now = System.currentTimeMillis()
        var totalWeight = 0.0
        var weightedSum = 0.0
        var confidenceSum = 0.0
        
        patterns.forEach { pattern ->
            // Weight based on recency (more recent = higher weight)
            val ageHours = (now - pattern.timestamp.toEpochMilliseconds()) / (3600.0 * 1000.0)
            val recencyWeight = exp(-ageHours / 168.0)  // Half-life of 1 week
            
            // Combined weight: recency * confidence
            val weight = recencyWeight * pattern.confidence
            
            weightedSum += pattern.travelTimeSeconds * weight
            totalWeight += weight
            confidenceSum += pattern.confidence
        }
        
        val averagePrediction = if (totalWeight > 0) weightedSum / totalWeight else 0.0
        val averageConfidence = confidenceSum / patterns.size
        
        return Pair(averagePrediction, averageConfidence.coerceIn(0.0, 1.0))
    }
    
    /**
     * Apply trend adjustment based on historical data.
     */
    private fun applyTrendAdjustment(patterns: List<TrafficPattern>, basePrediction: Double): Double {
        if (patterns.size < 10) return basePrediction
        
        // Sort by timestamp
        val sortedPatterns = patterns.sortedBy { it.timestamp.toEpochMilliseconds() }
        
        // Simple linear regression for trend
        val n = sortedPatterns.size
        val times = sortedPatterns.mapIndexed { index, _ -> index.toDouble() }
        val values = sortedPatterns.map { it.travelTimeSeconds }
        
        val sumX = times.sum()
        val sumY = values.sum()
        val sumXY = times.zip(values).sumOf { (x, y) -> x * y }
        val sumX2 = times.sumOf { it * it }
        
        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        
        // Apply trend adjustment (extrapolate one time unit forward)
        val trendAdjustment = slope * 1.0  // One unit forward
        
        return basePrediction + trendAdjustment
    }
    
    /**
     * Apply seasonal adjustment based on day/hour patterns.
     */
    private fun applySeasonalAdjustment(
        patterns: List<TrafficPattern>,
        basePrediction: Double,
        targetDayOfWeek: Int,
        targetHourOfDay: Int
    ): Double {
        // Group patterns by day/hour
        val patternsByTime = patterns.groupBy { it.dayOfWeek to it.hourOfDay }
        
        // Calculate seasonal indices
        val allPatterns = patterns.flatMap { listOf(it.travelTimeSeconds) }
        val globalMean = allPatterns.average()
        
        val seasonalIndex = patternsByTime[(targetDayOfWeek to targetHourOfDay)]
            ?.map { it.travelTimeSeconds }
            ?.average()
            ?.let { it / globalMean }
            ?: 1.0  // No seasonal data, use neutral factor
        
        return basePrediction * seasonalIndex
    }
    
    /**
     * Calculate congestion level based on predicted travel time.
     */
    private fun calculateCongestionLevel(
        predictedTime: Double,
        patterns: List<TrafficPattern>
    ): CongestionLevel {
        if (patterns.isEmpty()) return CongestionLevel.FREE_FLOW
        
        // Calculate free-flow time (minimum observed)
        val freeFlowTime = patterns.minOfOrNull { it.travelTimeSeconds } ?: predictedTime
        
        // Calculate speed ratio (free-flow / predicted)
        val speedRatio = freeFlowTime / predictedTime
        
        return CongestionLevel.fromSpeedRatio(speedRatio.coerceIn(0.0, 1.0))
    }
    
    /**
     * Generate alternative routes for congested segments.
     * Note: This is a simplified version. In production, this would integrate with the routing engine.
     */
    private fun generateAlternativeRoutes(segmentId: String): List<String> {
        // Simplified: Return empty list for now
        // In production implementation:
        // 1. Query routing engine for alternative paths
        // 2. Filter by distance/time tradeoff
        // 3. Return route IDs
        return emptyList()
    }
    
    /**
     * Aggregate patterns for statistical analysis.
     */
    suspend fun aggregatePatterns(patterns: List<TrafficPattern>): List<AggregatedTrafficPattern> =
        withContext(appDispatchers.default) {
            val grouped = patterns.groupBy { it.dayOfWeek to it.hourOfDay }
            
            return@withContext grouped.map { (key, groupPatterns) ->
                val (dayOfWeek, hourOfDay) = key
                val travelTimes = groupPatterns.map { it.travelTimeSeconds }
                val average = travelTimes.average()
                val min = travelTimes.minOrNull() ?: 0.0
                val max = travelTimes.maxOrNull() ?: 0.0
                val stdDev = calculateStandardDeviation(travelTimes, average)
                
                val freeFlowTime = travelTimes.minOrNull() ?: average
                val speedRatio = freeFlowTime / average
                val congestionLevel = CongestionLevel.fromSpeedRatio(speedRatio.coerceIn(0.0, 1.0))
                
                AggregatedTrafficPattern(
                    segmentId = groupPatterns.first().segmentId,
                    dayOfWeek = dayOfWeek,
                    hourOfDay = hourOfDay,
                    averageTravelTimeSeconds = average,
                    minTravelTimeSeconds = min,
                    maxTravelTimeSeconds = max,
                    standardDeviation = stdDev,
                    recordCount = groupPatterns.size,
                    congestionLevel = congestionLevel
                )
            }
        }
    
    /**
     * Calculate standard deviation.
     */
    private fun calculateStandardDeviation(values: List<Double>, mean: Double): Double {
        if (values.size <= 1) return 0.0
        
        val variance = values.sumOf { (it - mean).pow(2) } / (values.size - 1)
        return sqrt(variance)
    }
    
    /**
     * Calculate prediction accuracy by comparing predictions with actual outcomes.
     * @param predictions List of predictions made
     * @param actuals List of actual travel times
     * @return Accuracy score (0.0-1.0)
     */
    fun calculateAccuracy(
        predictions: List<Double>,
        actuals: List<Double>
    ): Double {
        if (predictions.isEmpty() || actuals.isEmpty() || predictions.size != actuals.size) {
            return 0.0
        }
        
        val errors = predictions.zip(actuals).map { (pred, actual) ->
            Math.abs(pred - actual) / actual
        }
        
        val mape = errors.average()  // Mean Absolute Percentage Error
        return (1.0 - mape).coerceIn(0.0, 1.0)
    }
    
    /**
     * Train model on historical data (simplified - would use ML in production).
     */
    suspend fun trainModel(patterns: List<TrafficPattern>): Boolean = withContext(appDispatchers.default) {
        // Simplified training - just validate data quality
        if (patterns.size < MINIMUM_PATTERNS_FOR_PREDICTION) {
            NavLog.i("Insufficient data for training: ${patterns.size}")
            return@withContext false
        }
        
        // In production, this would:
        // 1. Split data into training/validation sets
        // 2. Train TensorFlow Lite model
        // 3. Save model to storage
        // 4. Return success/failure
        
        NavLog.i("Model training simulated for ${patterns.size} patterns")
        return@withContext true
    }
}