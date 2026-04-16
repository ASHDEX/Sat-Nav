package com.jayesh.satnav.features.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripDemonstrationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `test createFeatureDemonstrations returns correct list`() = runTest {
        // Since the function is private, we'll test through public API
        // Create a test view model
        val viewModel = TripDemonstrationViewModel()
        
        // Load demonstration data
        viewModel.loadDemonstrationData()
        
        // Advance time to complete loading
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Check that UI state is Ready
        val state = viewModel.uiState.value
        assertTrue("State should be Ready", state is TripDemonstrationUiState.Ready)
        
        if (state is TripDemonstrationUiState.Ready) {
            // Check feature demonstrations
            assertEquals("Should have 6 feature demonstrations", 6, state.featureDemonstrations.size)
            
            // Check test scenarios
            assertEquals("Should have 4 test scenarios", 4, state.testScenarios.size)
            
            // Check trip overview
            assertNotNull("Trip overview should not be null", state.tripOverview)
            assertEquals("Start location should be Connaught Place", "Connaught Place, Delhi", state.tripOverview.startLocation)
            assertEquals("End location should be India Gate", "India Gate, Delhi", state.tripOverview.endLocation)
            
            // Check performance metrics
            assertNotNull("Performance metrics should not be null", state.performanceMetrics)
            assertTrue("Route compute time should be positive", state.performanceMetrics.routeComputeTime > 0)
            
            // Check improvement suggestions
            assertNotNull("Improvement suggestions should not be null", state.improvementSuggestions)
            assertTrue("Should have at least one suggestion", state.improvementSuggestions.suggestions.isNotEmpty())
        }
    }
    
    @Test
    fun `test createTestRoute returns valid route`() = runTest {
        // Since createTestRoute is private, we'll test the public startNavigation method
        // This is a bit indirect but tests the integration
        val viewModel = TripDemonstrationViewModel()
        var routeReceived: com.jayesh.satnav.domain.model.OfflineRoute? = null
        
        // Set up navigation callback
        val onStartNavigation: (com.jayesh.satnav.domain.model.OfflineRoute) -> Unit = { route ->
            routeReceived = route
        }
        
        // Start navigation
        viewModel.startNavigation(onStartNavigation)
        
        // Advance time
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Check route was created
        assertNotNull("Route should be created", routeReceived)
        routeReceived?.let { route ->
            assertEquals("Should have 4 points", 4, route.points.size)
            assertTrue("Distance should be positive", route.distanceMeters > 0)
            assertTrue("Duration should be positive", route.durationMillis > 0)
            assertEquals("Should have 3 instructions", 3, route.instructions.size)
            assertEquals("Profile should be CAR", com.jayesh.satnav.domain.model.RoutingProfile.CAR, route.profile)
        }
    }
    
    @Test
    fun `test feature demonstration states`() = runTest {
        val viewModel = TripDemonstrationViewModel()
        
        // Load data first
        viewModel.loadDemonstrationData()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Get initial state
        val initialState = viewModel.uiState.value
        assertTrue("Initial state should be Ready", initialState is TripDemonstrationUiState.Ready)
        
        if (initialState is TripDemonstrationUiState.Ready) {
            // Start first feature demonstration
            val firstFeature = initialState.featureDemonstrations.first()
            viewModel.startFeatureDemonstration(firstFeature.id)
            
            // Advance a bit
            testDispatcher.scheduler.advanceTimeBy(1000)
            
            // Check state changed to Demonstrating
            val demonstratingState = viewModel.uiState.value
            assertTrue("Should be in Demonstrating state", demonstratingState is TripDemonstrationUiState.Demonstrating)
            
            if (demonstratingState is TripDemonstrationUiState.Demonstrating) {
                assertEquals("Current feature should match", firstFeature.id, demonstratingState.currentFeature.id)
                assertTrue("Progress should be between 0 and 1", demonstratingState.progress >= 0f && demonstratingState.progress <= 1f)
            }
        }
    }
    
    @Test
    fun `test test scenario execution`() = runTest {
        val viewModel = TripDemonstrationViewModel()
        
        // Load data first
        viewModel.loadDemonstrationData()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Get initial state
        val initialState = viewModel.uiState.value as TripDemonstrationUiState.Ready
        
        // Run first test scenario
        val firstScenario = initialState.testScenarios.first()
        assertEquals("Initial status should be READY", TestStatus.READY, firstScenario.status)
        
        viewModel.runTestScenario(firstScenario.id)
        
        // Advance time to complete test
        testDispatcher.scheduler.advanceTimeBy((firstScenario.expectedDuration + 1) * 1000L)
        
        // Get updated state
        val updatedState = viewModel.uiState.value as TripDemonstrationUiState.Ready
        val updatedScenario = updatedState.testScenarios.find { it.id == firstScenario.id }
        
        assertNotNull("Scenario should still exist", updatedScenario)
        assertEquals("Status should be PASSED after execution", TestStatus.PASSED, updatedScenario?.status)
    }
}