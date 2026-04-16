package com.jayesh.satnav.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.domain.model.*
import com.jayesh.satnav.domain.repository.RoutingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for AI route suggestions screen
 */
@HiltViewModel
class AiRouteSuggestionsViewModel @Inject constructor(
    private val routingRepository: RoutingRepository,
    private val appDispatchers: AppDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow<AiRouteSuggestionsUiState>(AiRouteSuggestionsUiState.Loading)
    val uiState: StateFlow<AiRouteSuggestionsUiState> = _uiState.asStateFlow()

    private var currentOptimizationResult: AiOptimizationResult? = null
    private var currentDestinationPrediction: DestinationPrediction? = null
    private var currentDrivingAnalytics: DrivingAnalytics? = null

    /**
     * Load AI route suggestions
     */
    fun loadSuggestions(
        start: RouteCoordinate,
        end: RouteCoordinate,
        userId: String,
        criteria: OptimizationCriteria = OptimizationCriteria(),
        includePersonalization: Boolean = true
    ) {
        viewModelScope.launch(appDispatchers.io) {
            _uiState.value = AiRouteSuggestionsUiState.Loading

            try {
                // Load AI-optimized routes
                val optimizationResult = routingRepository.computeOptimizedRoute(
                    start = start,
                    end = end,
                    userId = userId,
                    criteria = criteria,
                    includePersonalization = includePersonalization
                ).getOrThrow()

                currentOptimizationResult = optimizationResult

                // Load destination prediction (if user has history)
                val destinationPrediction = routingRepository.predictDestination(
                    userId = userId,
                    currentLocation = start,
                    context = createPredictionContext(start)
                )

                currentDestinationPrediction = destinationPrediction

                // Load driving analytics (last 30 days)
                val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                val drivingAnalytics = routingRepository.getDrivingAnalytics(
                    userId = userId,
                    startTime = thirtyDaysAgo,
                    endTime = System.currentTimeMillis()
                )

                currentDrivingAnalytics = drivingAnalytics

                // Update UI state
                _uiState.value = AiRouteSuggestionsUiState.Success(
                    alternatives = optimizationResult.alternatives,
                    recommendedRoute = optimizationResult.alternatives.getOrNull(optimizationResult.recommendedAlternativeIndex),
                    criteria = optimizationResult.criteria,
                    includePersonalization = includePersonalization,
                    destinationPrediction = destinationPrediction,
                    drivingAnalytics = drivingAnalytics
                )
            } catch (e: Exception) {
                NavLog.e("Failed to load AI route suggestions", e)
                _uiState.value = AiRouteSuggestionsUiState.Error(
                    message = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    /**
     * Refresh suggestions with current criteria
     */
    fun refreshSuggestions(
        start: RouteCoordinate,
        end: RouteCoordinate,
        userId: String
    ) {
        val currentState = _uiState.value
        if (currentState is AiRouteSuggestionsUiState.Success) {
            loadSuggestions(
                start = start,
                end = end,
                userId = userId,
                criteria = currentState.criteria,
                includePersonalization = currentState.includePersonalization
            )
        }
    }

    /**
     * Update optimization criteria and reload
     */
    fun updateCriteria(
        newCriteria: OptimizationCriteria,
        start: RouteCoordinate,
        end: RouteCoordinate,
        userId: String
    ) {
        val currentState = _uiState.value
        if (currentState is AiRouteSuggestionsUiState.Success) {
            loadSuggestions(
                start = start,
                end = end,
                userId = userId,
                criteria = newCriteria,
                includePersonalization = currentState.includePersonalization
            )
        }
    }

    /**
     * Toggle personalization and reload
     */
    fun togglePersonalization(
        enabled: Boolean,
        start: RouteCoordinate,
        end: RouteCoordinate,
        userId: String
    ) {
        val currentState = _uiState.value
        if (currentState is AiRouteSuggestionsUiState.Success) {
            loadSuggestions(
                start = start,
                end = end,
                userId = userId,
                criteria = currentState.criteria,
                includePersonalization = enabled
            )
        }
    }

    /**
     * Learn from user's route choice
     */
    fun learnFromChoice(
        userId: String,
        chosenRoute: OfflineRoute,
        alternatives: List<OfflineRoute>
    ) {
        viewModelScope.launch(appDispatchers.io) {
            try {
                routingRepository.learnFromRouteChoice(
                    userId = userId,
                    chosenRoute = chosenRoute,
                    alternatives = alternatives,
                    context = createLearningContext()
                )
                NavLog.i("Learned from user route choice")
            } catch (e: Exception) {
                NavLog.e("Failed to learn from route choice", e)
            }
        }
    }

    /**
     * Navigate to predicted destination
     */
    fun navigateToPrediction(prediction: DestinationPrediction) {
        // In a real implementation, this would trigger navigation
        // For now, just log the action
        NavLog.i("Navigating to predicted destination: ${prediction.destinationName}")
        
        viewModelScope.launch(appDispatchers.io) {
            // Record this as a successful prediction
            // This would update the prediction model
        }
    }

    /**
     * Get detailed route analysis
     */
    fun getRouteAnalysis(routeId: String): RouteAnalysis? {
        // In real implementation, fetch detailed analysis
        return null
    }

    /**
     * Compare two routes
     */
    fun compareRoutes(route1: OfflineRoute, route2: OfflineRoute): RouteComparison {
        return RouteComparison(
            route1 = route1,
            route2 = route2,
            distanceDifference = route2.distanceMeters - route1.distanceMeters,
            timeDifference = (route2.durationMillis - route1.durationMillis) / 1000L,
            advantages = listOf(),
            tradeoffs = listOf()
        )
    }

    /**
     * Create prediction context based on current time and location
     */
    private fun createPredictionContext(currentLocation: RouteCoordinate): PredictionContext {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return PredictionContext(
            timeOfDay = when {
                hour in 6..11 -> TimeOfDay.MORNING
                hour in 12..17 -> TimeOfDay.AFTERNOON
                hour in 18..21 -> TimeOfDay.EVENING
                hour == 7 || hour == 8 || hour == 9 || hour == 16 || hour == 17 || hour == 18 -> TimeOfDay.RUSH_HOUR
                else -> TimeOfDay.NIGHT
            },
            dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> DayOfWeek.SATURDAY
                Calendar.SUNDAY -> DayOfWeek.SUNDAY
                else -> DayOfWeek.WEEKDAY
            },
            locationContext = LocationContext(
                currentLocation = currentLocation,
                homeLocation = null, // Would be loaded from user preferences
                workLocation = null, // Would be loaded from user preferences
                frequentLocations = emptyList()
            ),
            calendarEvents = emptyList(), // Would be loaded from calendar
            recentDestinations = emptyList() // Would be loaded from history
        )
    }

    /**
     * Create learning context for route choice
     */
    private fun createLearningContext(): Map<String, Any> {
        val calendar = Calendar.getInstance()
        
        return mapOf(
            "timestamp" to System.currentTimeMillis(),
            "hour_of_day" to calendar.get(Calendar.HOUR_OF_DAY),
            "day_of_week" to calendar.get(Calendar.DAY_OF_WEEK),
            "month" to calendar.get(Calendar.MONTH),
            "weather" to "unknown" // Would come from weather service
        )
    }
}

/**
 * UI state for AI route suggestions
 */
sealed interface AiRouteSuggestionsUiState {
    data object Loading : AiRouteSuggestionsUiState
    data class Error(val message: String) : AiRouteSuggestionsUiState
    data class Success(
        val alternatives: List<AiRouteAlternative>,
        val recommendedRoute: AiRouteAlternative?,
        val criteria: OptimizationCriteria,
        val includePersonalization: Boolean,
        val destinationPrediction: DestinationPrediction?,
        val drivingAnalytics: DrivingAnalytics?
    ) : AiRouteSuggestionsUiState
}

/**
 * Route analysis data class
 */
data class RouteAnalysis(
    val route: OfflineRoute,
    val safetyScore: Double,
    val scenicScore: Double,
    val trafficScore: Double,
    val elevationProfile: List<Double>,
    val turnByTurnAnalysis: List<TurnAnalysis>
)

data class TurnAnalysis(
    val instruction: NavInstruction,
    val safetyLevel: SafetyLevel,
    val complexity: ComplexityLevel,
    val recommendations: List<String>
)

enum class SafetyLevel {
    LOW, MEDIUM, HIGH
}

enum class ComplexityLevel {
    SIMPLE, MODERATE, COMPLEX
}

/**
 * Route comparison data class
 */
data class RouteComparison(
    val route1: OfflineRoute,
    val route2: OfflineRoute,
    val distanceDifference: Double,
    val timeDifference: Long,
    val advantages: List<String>,
    val tradeoffs: List<String>
)