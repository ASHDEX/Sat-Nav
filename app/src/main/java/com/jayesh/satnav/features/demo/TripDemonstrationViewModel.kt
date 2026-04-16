package com.jayesh.satnav.features.demo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jayesh.satnav.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.jayesh.satnav.domain.model.RoutingProfile

enum class TripType {
    LOCAL, HILL
}

/**
 * UI state for trip demonstration
 */
sealed interface TripDemonstrationUiState {
    data object Loading : TripDemonstrationUiState
    data class Ready(
        val tripOverview: TripOverview,
        val featureDemonstrations: List<FeatureDemonstration>,
        val testScenarios: List<TestScenario>,
        val performanceMetrics: PerformanceMetrics,
        val improvementSuggestions: ImprovementSuggestions
    ) : TripDemonstrationUiState
    data class Demonstrating(
        val currentFeature: FeatureDemonstration,
        val nextFeature: FeatureDemonstration?,
        val progress: Float,
        val isPaused: Boolean
    ) : TripDemonstrationUiState
    data class Completed(
        val results: DemonstrationResults
    ) : TripDemonstrationUiState
    data class Error(val message: String) : TripDemonstrationUiState
}

/**
 * Data classes for demonstration
 */
data class TripOverview(
    val title: String,
    val startLocation: String,
    val endLocation: String,
    val estimatedDuration: Int,
    val featuresIncluded: List<String>
)

data class FeatureDemonstration(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val phase: Int,
    val priority: Priority,
    val status: DemoStatus,
    val complexity: Int
)

data class TestScenario(
    val id: String,
    val title: String,
    val description: String,
    val status: TestStatus,
    val expectedDuration: Int
)

data class PerformanceMetrics(
    val routeComputeTime: Int,
    val mapLoadTime: Int,
    val memoryUsageMB: Int,
    val batteryImpact: Int,
    val overallScore: Int
)

data class ImprovementSuggestions(
    val suggestions: List<Suggestion>
)

data class Suggestion(
    val title: String,
    val description: String,
    val priority: Priority
)

data class DemonstrationResults(
    val featuresTested: Int,
    val successRate: Int,
    val performanceScore: Int,
    val issuesFound: Int
)

enum class DemoStatus {
    READY, RUNNING, COMPLETED, FAILED
}

enum class TestStatus {
    READY, RUNNING, PASSED, FAILED
}

enum class Priority {
    HIGH, MEDIUM, LOW
}

/**
 * ViewModel for trip demonstration
 */
@HiltViewModel
class TripDemonstrationViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow<TripDemonstrationUiState>(TripDemonstrationUiState.Loading)
    val uiState: StateFlow<TripDemonstrationUiState> = _uiState.asStateFlow()

    private var currentDemonstrationIndex = 0
    private var demonstrationTimerJob: kotlinx.coroutines.Job? = null
    private val _selectedTripType = MutableStateFlow(TripType.LOCAL)
    val selectedTripType: StateFlow<TripType> = _selectedTripType.asStateFlow()

    private val featureDemonstrations = createFeatureDemonstrations()
    private val testScenarios = createTestScenarios()

    /**
     * Load demonstration data
     */
    fun loadDemonstrationData() = viewModelScope.launch {
        _uiState.value = TripDemonstrationUiState.Loading
        
        // Simulate loading delay
        delay(800)
        
        _uiState.value = TripDemonstrationUiState.Ready(
            tripOverview = createTripOverview(_selectedTripType.value),
            featureDemonstrations = featureDemonstrations,
            testScenarios = testScenarios,
            performanceMetrics = createPerformanceMetrics(),
            improvementSuggestions = createImprovementSuggestions()
        )
    }

    /**
     * Switch trip type
     */
    fun selectTrip(type: TripType) {
        _selectedTripType.value = type
        loadDemonstrationData()
    }

    /**
     * Start comprehensive trip demonstration
     */
    fun startComprehensiveTrip() = viewModelScope.launch {
        currentDemonstrationIndex = 0
        startNextDemonstration()
    }

    /**
     * Start specific feature demonstration
     */
    fun startFeatureDemonstration(featureId: String) = viewModelScope.launch {
        val feature = featureDemonstrations.find { it.id == featureId }
        if (feature != null) {
            _uiState.value = TripDemonstrationUiState.Demonstrating(
                currentFeature = feature,
                nextFeature = null,
                progress = 0f,
                isPaused = false
            )
            startDemonstrationTimer(feature)
        }
    }

    /**
     * Run test scenario
     */
    fun runTestScenario(scenarioId: String) = viewModelScope.launch {
        val scenario = testScenarios.find { it.id == scenarioId }
        if (scenario != null) {
            // Update scenario status
            val updatedScenarios = testScenarios.map {
                if (it.id == scenarioId) it.copy(status = TestStatus.RUNNING) else it
            }
            
            // Update UI state
            val currentState = _uiState.value
            if (currentState is TripDemonstrationUiState.Ready) {
                _uiState.value = currentState.copy(testScenarios = updatedScenarios)
            }
            
            // Simulate test execution
            delay(scenario.expectedDuration * 1000L)
            
            // Mark as passed (for demo purposes)
            val finalScenarios = updatedScenarios.map {
                if (it.id == scenarioId) it.copy(status = TestStatus.PASSED) else it
            }
            
            if (currentState is TripDemonstrationUiState.Ready) {
                _uiState.value = currentState.copy(testScenarios = finalScenarios)
            }
        }
    }

    /**
     * Skip current demonstration
     */
    fun skipCurrentDemonstration() = viewModelScope.launch {
        demonstrationTimerJob?.cancel()
        currentDemonstrationIndex++
        
        if (currentDemonstrationIndex < featureDemonstrations.size) {
            startNextDemonstration()
        } else {
            completeDemonstration()
        }
    }

    /**
     * Pause/resume demonstration
     */
    fun pauseResumeDemonstration() {
        val currentState = _uiState.value
        if (currentState is TripDemonstrationUiState.Demonstrating) {
            if (currentState.isPaused) {
                // Resume
                _uiState.value = currentState.copy(isPaused = false)
                startDemonstrationTimer(currentState.currentFeature)
            } else {
                // Pause
                _uiState.value = currentState.copy(isPaused = true)
                demonstrationTimerJob?.cancel()
            }
        }
    }

    /**
     * Reset demonstration
     */
    fun resetDemonstration() = viewModelScope.launch {
        demonstrationTimerJob?.cancel()
        loadDemonstrationData()
    }

    /**
     * Start navigation with demonstrated route
     */
    fun startNavigation(onStartNavigation: (OfflineRoute) -> Unit) = viewModelScope.launch {
        // Create a test route for demonstration based on selection
        val testRoute = if (_selectedTripType.value == TripType.HILL) {
            createKangraRoute()
        } else {
            createLocalRoute()
        }
        onStartNavigation(testRoute)
    }

    /**
     * Start next demonstration in sequence
     */
    private fun startNextDemonstration() {
        val feature = featureDemonstrations[currentDemonstrationIndex]
        val nextFeature = if (currentDemonstrationIndex + 1 < featureDemonstrations.size) {
            featureDemonstrations[currentDemonstrationIndex + 1]
        } else {
            null
        }
        
        _uiState.value = TripDemonstrationUiState.Demonstrating(
            currentFeature = feature,
            nextFeature = nextFeature,
            progress = 0f,
            isPaused = false
        )
        
        startDemonstrationTimer(feature)
    }

    /**
     * Start demonstration timer
     */
    private fun startDemonstrationTimer(feature: FeatureDemonstration) {
        demonstrationTimerJob?.cancel()
        
        demonstrationTimerJob = viewModelScope.launch {
            val duration = when (feature.complexity) {
                1 -> 3000L
                2 -> 5000L
                3 -> 7000L
                else -> 4000L
            }
            
            val steps = 100
            val stepDuration = duration / steps
            
            for (i in 0..steps) {
                if (_uiState.value is TripDemonstrationUiState.Demonstrating) {
                    val currentState = _uiState.value as TripDemonstrationUiState.Demonstrating
                    if (!currentState.isPaused) {
                        val progress = i.toFloat() / steps
                        _uiState.value = currentState.copy(progress = progress)
                        delay(stepDuration)
                    } else {
                        delay(100)
                    }
                } else {
                    break
                }
            }
            
            // Mark feature as completed
            val updatedFeatures = featureDemonstrations.map {
                if (it.id == feature.id) it.copy(status = DemoStatus.COMPLETED) else it
            }
            
            // Move to next or complete
            currentDemonstrationIndex++
            if (currentDemonstrationIndex < featureDemonstrations.size) {
                startNextDemonstration()
            } else {
                completeDemonstration()
            }
        }
    }

    /**
     * Complete demonstration
     */
    private fun completeDemonstration() {
        demonstrationTimerJob?.cancel()
        
        _uiState.value = TripDemonstrationUiState.Completed(
            results = DemonstrationResults(
                featuresTested = featureDemonstrations.size,
                successRate = 95,
                performanceScore = 88,
                issuesFound = 2
            )
        )
    }

    /**
     * Create local test route (Gurugram to IGI)
     */
    private fun createLocalRoute(): OfflineRoute {
        return OfflineRoute(
            profile = RoutingProfile.CAR,
            points = listOf(
                RouteCoordinate(28.4595, 77.0266), // Start: Near Gurugram Sector 44
                RouteCoordinate(28.4700, 77.0400),
                RouteCoordinate(28.4800, 77.0600),
                RouteCoordinate(28.4950, 77.0890), // Via: Cyber Hub
                RouteCoordinate(28.5100, 77.0900),
                RouteCoordinate(28.5400, 77.1000),
                RouteCoordinate(28.5562, 77.1000)  // End: Near IGI Airport
            ),
            distanceMeters = 15000.0,
            durationMillis = 1800000, // 30 minutes
            computationTimeMillis = 450,
            segmentCount = 6,
            waypointCount = 7,
            instructions = listOf(
                NavInstruction(sign = 1, streetName = "Netaji Subhash Marg", distanceMeters = 1200.0, durationMillis = 240000),
                NavInstruction(sign = 2, streetName = "NH-48 (Delhi-Gurgaon Expressway)", distanceMeters = 8500.0, durationMillis = 900000),
                NavInstruction(sign = 4, streetName = "Cyber City Entry", distanceMeters = 500.0, durationMillis = 120000),
                NavInstruction(sign = 0, streetName = "Airport Approach Road", distanceMeters = 4800.0, durationMillis = 540000)
            )
        )
    }

    /**
     * Create Kangra Hill route (~450km)
     */
    private fun createKangraRoute(): OfflineRoute {
        return OfflineRoute(
            profile = RoutingProfile.CAR,
            points = listOf(
                RouteCoordinate(28.4595, 77.0266), // Start: Gurugram
                RouteCoordinate(28.7041, 77.1025), // Delhi Outer
                RouteCoordinate(29.3909, 76.9635), // Panipat
                RouteCoordinate(29.6857, 76.9905), // Karnal
                RouteCoordinate(29.9691, 76.8783), // Kurukshetra
                RouteCoordinate(30.3782, 76.7767), // Ambala
                RouteCoordinate(30.7333, 76.7794), // Chandigarh
                RouteCoordinate(30.9415, 76.5274), // Ropar
                RouteCoordinate(31.2500, 76.3500), // Entry to Foothills
                RouteCoordinate(31.4685, 76.2708), // Una
                RouteCoordinate(31.8000, 76.2500), // Ghat Starts
                RouteCoordinate(31.9500, 76.2000), // Jawalamukhi
                RouteCoordinate(32.1001, 76.2733)  // End: Kangra
            ),
            distanceMeters = 455000.0,
            durationMillis = 30600000, // 8.5 hours
            computationTimeMillis = 1200,
            segmentCount = 12,
            waypointCount = 13,
            instructions = listOf(
                NavInstruction(sign = 1, streetName = "NH-44", distanceMeters = 220000.0, durationMillis = 10800000),
                NavInstruction(sign = 1, streetName = "Zirakpur-Shimla Hwy", distanceMeters = 50000.0, durationMillis = 3600000),
                NavInstruction(sign = 2, streetName = "Expressway to Chandigarh", distanceMeters = 30000.0, durationMillis = 1800000),
                NavInstruction(sign = 4, streetName = "Una-Kangra Road", distanceMeters = 150000.0, durationMillis = 14400000),
                NavInstruction(sign = 0, streetName = "Kangra Town Square", distanceMeters = 5000.0, durationMillis = 600000)
            )
        )
    }

    /**
     * Create feature demonstrations
     */
    private fun createFeatureDemonstrations(): List<FeatureDemonstration> {
        return listOf(
            FeatureDemonstration(
                id = "ai-optimization",
                title = "AI Route Optimization",
                description = "Smart route planning based on historical patterns and preferences",
                icon = Icons.Default.AutoAwesome,
                phase = 15,
                priority = Priority.HIGH,
                status = DemoStatus.READY,
                complexity = 3
            ),
            FeatureDemonstration(
                id = "lane-guidance",
                title = "Lane Guidance",
                description = "Real-time lane recommendations for complex intersections",
                icon = Icons.Default.Directions,
                phase = 12,
                priority = Priority.HIGH,
                status = DemoStatus.READY,
                complexity = 2
            ),
            FeatureDemonstration(
                id = "traffic-patterns",
                title = "Traffic Pattern Awareness",
                description = "Predictive traffic flow based on time and day patterns",
                icon = Icons.Default.Traffic,
                phase = 13,
                priority = Priority.MEDIUM,
                status = DemoStatus.READY,
                complexity = 2
            ),
            FeatureDemonstration(
                id = "multi-modal",
                title = "Multi-modal Transportation",
                description = "Seamless integration of walking, cycling, and public transit",
                icon = Icons.Default.DirectionsTransit,
                phase = 14,
                priority = Priority.MEDIUM,
                status = DemoStatus.READY,
                complexity = 3
            ),
            FeatureDemonstration(
                id = "smooth-navigation",
                title = "Smooth Navigation",
                description = "Fluid position interpolation and camera movement",
                icon = Icons.Default.Navigation,
                phase = 11,
                priority = Priority.HIGH,
                status = DemoStatus.READY,
                complexity = 2
            ),
            FeatureDemonstration(
                id = "camera-sync",
                title = "Camera-Arrow Synchronization",
                description = "Perfect alignment between map view and navigation arrow",
                icon = Icons.Default.Explore,
                phase = 12,
                priority = Priority.LOW,
                status = DemoStatus.READY,
                complexity = 1
            )
        )
    }

    /**
     * Create test scenarios
     */
    private fun createTestScenarios(): List<TestScenario> {
        return listOf(
            TestScenario(
                id = "route-recalculation",
                title = "Route Recalculation",
                description = "Test automatic rerouting when off course",
                status = TestStatus.READY,
                expectedDuration = 5
            ),
            TestScenario(
                id = "voice-guidance",
                title = "Voice Guidance",
                description = "Test timely and clear voice instructions",
                status = TestStatus.READY,
                expectedDuration = 3
            ),
            TestScenario(
                id = "battery-optimization",
                title = "Battery Optimization",
                description = "Test power consumption during navigation",
                status = TestStatus.READY,
                expectedDuration = 10
            ),
            TestScenario(
                id = "memory-usage",
                title = "Memory Usage",
                description = "Test memory footprint with large maps",
                status = TestStatus.READY,
                expectedDuration = 8
            )
        )
    }

    /**
     * Create trip overview based on selection
     */
    private fun createTripOverview(type: TripType): TripOverview {
        return when (type) {
            TripType.LOCAL -> TripOverview(
                title = "Gurugram Local Demo",
                startLocation = "Cyber Hub, Gurugram",
                endLocation = "IGI Airport, Delhi",
                estimatedDuration = 30,
                featuresIncluded = listOf(
                    "Offline Map Rendering",
                    "Self-Healing Storage",
                    "AI Route Optimization",
                    "Lane Guidance", 
                    "Smooth Navigation",
                    "Camera-Arrow Sync"
                )
            )
            TripType.HILL -> TripOverview(
                title = "Kangra Hill Exploration",
                startLocation = "Gurugram, HR",
                endLocation = "Kangra, HP",
                estimatedDuration = 510, // 8.5 hours
                featuresIncluded = listOf(
                    "Cross-State Route Merging",
                    "Foothill Optimization",
                    "Ghat Section Warning",
                    "Offline Hillshading",
                    "Voice Fatigue Management"
                )
            )
        }
    }

    /**
     * Create performance metrics
     */
    private fun createPerformanceMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            routeComputeTime = 85,
            mapLoadTime = 320,
            memoryUsageMB = 42,
            batteryImpact = 3,
            overallScore = 88
        )
    }

    /**
     * Create improvement suggestions
     */
    private fun createImprovementSuggestions(): ImprovementSuggestions {
        return ImprovementSuggestions(
            suggestions = listOf(
                Suggestion(
                    title = "Add Real-time Weather Integration",
                    description = "Consider weather conditions in route planning",
                    priority = Priority.MEDIUM
                ),
                Suggestion(
                    title = "Improve Offline Search Accuracy",
                    description = "Enhance POI search with better categorization",
                    priority = Priority.HIGH
                ),
                Suggestion(
                    title = "Add CarPlay/Android Auto Support",
                    description = "Extend navigation to car infotainment systems",
                    priority = Priority.LOW
                )
            )
        )
    }
}