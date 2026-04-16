package com.jayesh.satnav.features.ai

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.domain.model.*
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * AI component for predicting user's destination based on context
 *
 * Uses pattern recognition and contextual analysis to predict
 * where the user is likely going based on:
 * - Time of day
 * - Day of week
 * - Current location
 * - Calendar events
 * - Historical patterns
 */
@Singleton
class DestinationPredictor @Inject constructor(
    private val appDispatchers: AppDispatchers
) {
    private val userDestinationHistory = mutableMapOf<String, MutableList<DestinationHistory>>()
    private val userCalendarEvents = mutableMapOf<String, MutableList<CalendarEvent>>()
    private val userFrequentLocations = mutableMapOf<String, MutableList<FrequentLocation>>()

    /**
     * Predict destination based on user context
     */
    suspend fun predictDestination(
        userId: String,
        currentLocation: RouteCoordinate,
        context: PredictionContext
    ): DestinationPrediction? = withContext(appDispatchers.io) {
        try {
            // Get candidate destinations
            val candidates = getCandidateDestinations(userId, currentLocation, context)
            
            if (candidates.isEmpty()) {
                return@withContext null
            }
            
            // Score each candidate
            val scoredCandidates = candidates.map { candidate ->
                val score = calculateDestinationScore(candidate, userId, currentLocation, context)
                candidate to score
            }
            
            // Select best candidate
            val (bestCandidate, bestScore) = scoredCandidates.maxByOrNull { it.second } ?: return@withContext null
            
            // Generate prediction reasons
            val reasons = generatePredictionReasons(bestCandidate, userId, context)
            
            // Create prediction
            DestinationPrediction(
                userId = userId,
                predictedDestination = bestCandidate.destination,
                destinationName = bestCandidate.name,
                confidence = bestScore.coerceIn(0.0, 1.0),
                context = context,
                reasons = reasons,
                estimatedDepartureTime = estimateDepartureTime(context),
                estimatedArrivalTime = estimateArrivalTime(bestCandidate, currentLocation, context)
            )
        } catch (e: Exception) {
            NavLog.e("Destination prediction failed", e)
            null
        }
    }

    /**
     * Record a completed destination for learning
     */
    suspend fun recordDestination(
        userId: String,
        destination: RouteCoordinate,
        destinationName: String? = null,
        arrivalTime: Long,
        departureTime: Long? = null,
        context: Map<String, Any> = emptyMap()
    ) = withContext(appDispatchers.io) {
        val history = userDestinationHistory.getOrPut(userId) { mutableListOf() }
        
        val historyEntry = DestinationHistory(
            destination = destination,
            name = destinationName,
            arrivalTime = arrivalTime,
            departureTime = departureTime,
            timeOfDay = extractTimeOfDay(arrivalTime),
            dayOfWeek = extractDayOfWeek(arrivalTime),
            context = context
        )
        
        history.add(historyEntry)
        
        // Keep only last 100 destinations
        if (history.size > 100) {
            history.removeAt(0)
        }
        
        // Update frequent locations
        updateFrequentLocations(userId, historyEntry)
    }

    /**
     * Get user's frequent locations
     */
    suspend fun getFrequentLocations(userId: String, limit: Int = 10): List<FrequentLocation> = withContext(appDispatchers.io) {
        return@withContext userFrequentLocations[userId]?.take(limit) ?: emptyList()
    }

    /**
     * Clear user's destination history
     */
    suspend fun clearHistory(userId: String) = withContext(appDispatchers.io) {
        userDestinationHistory.remove(userId)
        userFrequentLocations.remove(userId)
    }

    /**
     * Get candidate destinations for prediction
     */
    private fun getCandidateDestinations(
        userId: String,
        currentLocation: RouteCoordinate,
        context: PredictionContext
    ): List<DestinationCandidate> {
        val candidates = mutableListOf<DestinationCandidate>()
        
        // 1. Frequent locations
        val frequentLocations = userFrequentLocations[userId] ?: emptyList()
        frequentLocations.forEach { location ->
            candidates.add(
                DestinationCandidate(
                    destination = location.location,
                    name = location.name,
                    type = CandidateType.FREQUENT_LOCATION,
                    baseScore = location.visitCount.toDouble() / 100.0 // Normalize
                )
            )
        }
        
        // 2. Calendar events
        val calendarEvents = userCalendarEvents[userId] ?: emptyList()
        val now = System.currentTimeMillis()
        calendarEvents.forEach { event ->
            if (event.startTime > now && event.startTime < now + 2 * 60 * 60 * 1000) { // Within next 2 hours
                event.locationCoordinates?.let { coordinates ->
                    candidates.add(
                        DestinationCandidate(
                            destination = coordinates,
                            name = event.title,
                            type = CandidateType.CALENDAR_EVENT,
                            baseScore = 0.8
                        )
                    )
                }
            }
        }
        
        // 3. Recent destinations
        val recentDestinations = userDestinationHistory[userId]?.takeLast(5) ?: emptyList()
        recentDestinations.forEach { history ->
            candidates.add(
                DestinationCandidate(
                    destination = history.destination,
                    name = history.name,
                    type = CandidateType.RECENT_DESTINATION,
                    baseScore = 0.6
                )
            )
        }
        
        // 4. Context locations (home, work)
        context.locationContext.homeLocation?.let { home ->
            candidates.add(
                DestinationCandidate(
                    destination = home,
                    name = "Home",
                    type = CandidateType.HOME,
                    baseScore = 0.7
                )
            )
        }
        
        context.locationContext.workLocation?.let { work ->
            candidates.add(
                DestinationCandidate(
                    destination = work,
                    name = "Work",
                    type = CandidateType.WORK,
                    baseScore = 0.7
                )
            )
        }
        
        return candidates.distinctBy { it.destination.latitude to it.destination.longitude }
    }

    /**
     * Calculate score for a destination candidate
     */
    private fun calculateDestinationScore(
        candidate: DestinationCandidate,
        userId: String,
        currentLocation: RouteCoordinate,
        context: PredictionContext
    ): Double {
        var score = candidate.baseScore
        
        // 1. Time of day relevance
        score *= calculateTimeOfDayRelevance(candidate, context.timeOfDay)
        
        // 2. Day of week relevance
        score *= calculateDayOfWeekRelevance(candidate, context.dayOfWeek)
        
        // 3. Proximity score
        score *= calculateProximityScore(candidate.destination, currentLocation)
        
        // 4. Historical pattern score
        score *= calculateHistoricalPatternScore(candidate, userId, context)
        
        // 5. Calendar relevance
        score *= calculateCalendarRelevance(candidate, context.calendarEvents)
        
        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Calculate time of day relevance
     */
    private fun calculateTimeOfDayRelevance(
        candidate: DestinationCandidate,
        currentTimeOfDay: TimeOfDay
    ): Double {
        val destinationHistory = getDestinationHistoryForCandidate(candidate)
        if (destinationHistory.isEmpty()) return 0.7 // Default
        
        val visitsAtThisTime = destinationHistory.count { it.timeOfDay == currentTimeOfDay }
        val totalVisits = destinationHistory.size
        
        return if (totalVisits > 0) {
            max(0.3, visitsAtThisTime.toDouble() / totalVisits)
        } else {
            0.5
        }
    }

    /**
     * Calculate day of week relevance
     */
    private fun calculateDayOfWeekRelevance(
        candidate: DestinationCandidate,
        currentDayOfWeek: DayOfWeek
    ): Double {
        val destinationHistory = getDestinationHistoryForCandidate(candidate)
        if (destinationHistory.isEmpty()) return 0.7 // Default
        
        val visitsOnThisDay = destinationHistory.count { it.dayOfWeek == currentDayOfWeek }
        val totalVisits = destinationHistory.size
        
        return if (totalVisits > 0) {
            max(0.3, visitsOnThisDay.toDouble() / totalVisits)
        } else {
            0.5
        }
    }

    /**
     * Calculate proximity score (closer destinations are more likely)
     */
    private fun calculateProximityScore(
        destination: RouteCoordinate,
        currentLocation: RouteCoordinate
    ): Double {
        val distance = haversineDistance(
            currentLocation.latitude, currentLocation.longitude,
            destination.latitude, destination.longitude
        )
        
        // Score decreases with distance, but not too sharply
        return exp(-distance / 10000.0) // 10km scale
    }

    /**
     * Calculate historical pattern score
     */
    private fun calculateHistoricalPatternScore(
        candidate: DestinationCandidate,
        userId: String,
        context: PredictionContext
    ): Double {
        val history = userDestinationHistory[userId] ?: return 0.5
        
        // Look for similar patterns in history
        val similarTrips = history.filter { trip ->
            val timeSimilar = abs(trip.arrivalTime % (24 * 60 * 60 * 1000) - System.currentTimeMillis() % (24 * 60 * 60 * 1000)) < 2 * 60 * 60 * 1000
            val locationSimilar = haversineDistance(
                trip.destination.latitude, trip.destination.longitude,
                candidate.destination.latitude, candidate.destination.longitude
            ) < 1000 // Within 1km
            
            timeSimilar && locationSimilar
        }
        
        return if (similarTrips.size > 2) {
            0.8 + (similarTrips.size * 0.05).coerceAtMost(0.2)
        } else {
            0.5
        }
    }

    /**
     * Calculate calendar relevance
     */
    private fun calculateCalendarRelevance(
        candidate: DestinationCandidate,
        calendarEvents: List<CalendarEvent>
    ): Double {
        if (calendarEvents.isEmpty()) return 0.8 // No calendar events doesn't penalize
        
        val now = System.currentTimeMillis()
        val upcomingEvents = calendarEvents.filter { event ->
            event.startTime > now && event.startTime < now + 4 * 60 * 60 * 1000 // Next 4 hours
        }
        
        if (upcomingEvents.isEmpty()) return 0.8
        
        // Check if destination matches any upcoming event
        val matchingEvent = upcomingEvents.firstOrNull { event ->
            event.locationCoordinates?.let { coordinates ->
                haversineDistance(
                    coordinates.latitude, coordinates.longitude,
                    candidate.destination.latitude, candidate.destination.longitude
                ) < 500 // Within 500m
            } ?: false
        }
        
        return if (matchingEvent != null) 1.0 else 0.6
    }

    /**
     * Generate reasons for the prediction
     */
    private fun generatePredictionReasons(
        candidate: DestinationCandidate,
        userId: String,
        context: PredictionContext
    ): List<PredictionReason> {
        val reasons = mutableListOf<PredictionReason>()
        
        // Time of day reason
        val timeRelevance = calculateTimeOfDayRelevance(candidate, context.timeOfDay)
        if (timeRelevance > 0.7) {
            reasons.add(
                PredictionReason(
                    type = ReasonType.TIME_OF_DAY,
                    description = "You often go here at this time of day",
                    confidence = timeRelevance
                )
            )
        }
        
        // Day of week reason
        val dayRelevance = calculateDayOfWeekRelevance(candidate, context.dayOfWeek)
        if (dayRelevance > 0.7) {
            reasons.add(
                PredictionReason(
                    type = ReasonType.DAY_OF_WEEK,
                    description = "You often go here on this day of the week",
                    confidence = dayRelevance
                )
            )
        }
        
        // Frequent destination reason
        if (candidate.type == CandidateType.FREQUENT_LOCATION) {
            reasons.add(
                PredictionReason(
                    type = ReasonType.FREQUENT_DESTINATION,
                    description = "This is one of your frequently visited locations",
                    confidence = 0.8
                )
            )
        }
        
        // Calendar event reason
        if (candidate.type == CandidateType.CALENDAR_EVENT) {
            reasons.add(
                PredictionReason(
                    type = ReasonType.CALENDAR_EVENT,
                    description = "You have a calendar event at this location",
                    confidence = 0.9
                )
            )
        }
        
        // Routine pattern reason
        val history = userDestinationHistory[userId] ?: emptyList()
        val similarPatterns = history.count { trip ->
            val timeDiff = abs(trip.arrivalTime % (24 * 60 * 60 * 1000) - System.currentTimeMillis() % (24 * 60 * 60 * 1000))
            timeDiff < 30 * 60 * 1000 // Within 30 minutes
        }
        
        if (similarPatterns > 3) {
            reasons.add(
                PredictionReason(
                    type = ReasonType.ROUTINE_PATTERN,
                    description = "This matches your established routine pattern",
                    confidence = min(0.9, similarPatterns * 0.2)
                )
            )
        }
        
        return reasons
    }

    /**
     * Update frequent locations based on new destination
     */
    private fun updateFrequentLocations(userId: String, historyEntry: DestinationHistory) {
        val frequentLocations = userFrequentLocations.getOrPut(userId) { mutableListOf() }
        
        // Check if location already exists
        val existingIndex = frequentLocations.indexOfFirst { location ->
            haversineDistance(
                location.location.latitude, location.location.longitude,
                historyEntry.destination.latitude, historyEntry.destination.longitude
            ) < 100 // Within 100m
        }
        
        if (existingIndex >= 0) {
            // Update existing location
            val existing = frequentLocations[existingIndex]
            frequentLocations[existingIndex] = existing.copy(
                visitCount = existing.visitCount + 1,
                lastVisitTime = historyEntry.arrivalTime,
                typicalVisitTimes = (existing.typicalVisitTimes + historyEntry.timeOfDay).distinct().take(3),
                typicalVisitDays = (existing.typicalVisitDays + historyEntry.dayOfWeek).distinct().take(3)
            )
        } else {
            // Add new location
            frequentLocations.add(
                FrequentLocation(
                    location = historyEntry.destination,
                    name = historyEntry.name ?: "Unknown Location",
                    visitCount = 1,
                    lastVisitTime = historyEntry.arrivalTime,
                    typicalVisitTimes = listOf(historyEntry.timeOfDay),
                    typicalVisitDays = listOf(historyEntry.dayOfWeek)
                )
            )
        }
        
        // Sort by visit count and keep top 20
        frequentLocations.sortByDescending { it.visitCount }
        if (frequentLocations.size > 20) {
            frequentLocations.subList(20, frequentLocations.size).clear()
        }
    }

    /**
     * Estimate departure time based on context
     */
    private fun estimateDepartureTime(context: PredictionContext): Long? {
        val now = System.currentTimeMillis()
        
        // Check calendar events
        val upcomingEvents = context.calendarEvents.filter { it.startTime > now }
        if (upcomingEvents.isNotEmpty()) {
            // Depart 15 minutes before first event
            val firstEvent = upcomingEvents.minByOrNull { it.startTime }!!
            return firstEvent.startTime - 15 * 60 * 1000
        }
        
        // Based on time of day patterns
        return when (context.timeOfDay) {
            TimeOfDay.MORNING -> now + 30 * 60 * 1000 // 30 minutes from now
            TimeOfDay.AFTERNOON -> now + 15 * 60 * 1000 // 15 minutes
            TimeOfDay.EVENING -> now + 45 * 60 * 1000 // 45 minutes
            TimeOfDay.NIGHT -> now + 60 * 60 * 1000 // 60 minutes
            TimeOfDay.RUSH_HOUR -> now + 20 * 60 * 1000 // 20 minutes
        }
    }

    /**
     * Estimate arrival time based on destination and current location
     */
    private fun estimateArrivalTime(
        candidate: DestinationCandidate,
        currentLocation: RouteCoordinate,
        context: PredictionContext
    ): Long? {
        val departureTime = estimateDepartureTime(context) ?: System.currentTimeMillis()
        
        val distance = haversineDistance(
            currentLocation.latitude, currentLocation.longitude,
            candidate.destination.latitude, candidate.destination.longitude
        )
        
        // Estimate travel time (assume 40 km/h average)
        val travelTimeHours = distance / (40 * 1000) // 40 km/h in m/s
        val travelTimeMs = (travelTimeHours * 3600 * 1000).toLong()
        
        return departureTime + travelTimeMs
    }

    // Helper methods
    private fun getDestinationHistoryForCandidate(candidate: DestinationCandidate): List<DestinationHistory> {
        // In real implementation, filter history for this specific destination
        // For now, return empty list
        return emptyList()
    }

    private fun extractTimeOfDay(timestamp: Long): TimeOfDay {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return when {
            hour in 6..11 -> TimeOfDay.MORNING
            hour in 12..17 -> TimeOfDay.AFTERNOON
            hour in 18..21 -> TimeOfDay.EVENING
            hour == 7 || hour == 8 || hour == 9 || hour == 16 || hour == 17 || hour == 18 -> TimeOfDay.RUSH_HOUR
            else -> TimeOfDay.NIGHT
        }
    }

    private fun extractDayOfWeek(timestamp: Long): DayOfWeek {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> DayOfWeek.MONDAY
            Calendar.TUESDAY -> DayOfWeek.TUESDAY
            Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
            Calendar.THURSDAY -> DayOfWeek.THURSDAY
            Calendar.FRIDAY -> DayOfWeek.FRIDAY
            Calendar.SATURDAY -> DayOfWeek.SATURDAY
            Calendar.SUNDAY -> DayOfWeek.SUNDAY
            else -> DayOfWeek.WEEKDAY
        }
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

    /**
     * Internal data classes
     */
    private data class DestinationHistory(
        val destination: RouteCoordinate,
        val name: String?,
        val arrivalTime: Long,
        val departureTime: Long?,
        val timeOfDay: TimeOfDay,
        val dayOfWeek: DayOfWeek,
        val context: Map<String, Any>
    )

    private data class DestinationCandidate(
        val destination: RouteCoordinate,
        val name: String?,
        val type: CandidateType,
        val baseScore: Double
    )

    private enum class CandidateType {
        FREQUENT_LOCATION,
        CALENDAR_EVENT,
        RECENT_DESTINATION,
        HOME,
        WORK
    }
}