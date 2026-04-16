package com.jayesh.satnav.domain.navigation

import android.location.Location
import com.jayesh.satnav.core.utils.LatLng
import com.jayesh.satnav.domain.model.RouteOption
import com.jayesh.satnav.domain.model.RoutingProfile
import com.jayesh.satnav.domain.model.TurnInstruction
import com.jayesh.satnav.domain.model.TurnDirection
import com.jayesh.satnav.domain.repository.LocationRepository
import com.jayesh.satnav.domain.routing.RoutePlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationEngineTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    
    private lateinit var fakeLocationRepo: FakeLocationRepository
    private lateinit var fakeRoutePlanner: FakeRoutePlanner
    private lateinit var engine: NavigationEngine
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeLocationRepo = FakeLocationRepository()
        fakeRoutePlanner = FakeRoutePlanner()
        engine = NavigationEngine(fakeLocationRepo, fakeRoutePlanner)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `normal navigation through straight route reaches Arrived`() = testScope.runTest {
        // Create a simple route
        val route = createStraightRoute()
        
        // Start navigation
        engine.start(route)
        advanceUntilIdle()
        
        // Should be in Navigating state
        val initialState = engine.state.first { it is NavigationState.Navigating }
        assertIs<NavigationState.Navigating>(initialState)
        
        // Simulate progress along route
        val locations = generateLocationsAlongRoute(route)
        locations.forEach { location ->
            fakeLocationRepo.emitLocation(location)
            advanceUntilIdle()
        }
        
        // Should reach Arrived state
        val finalState = engine.state.first { it is NavigationState.Arrived }
        assertIs<NavigationState.Arrived>(finalState)
    }
    
    @Test
    fun `off-route detection triggers Rerouting after 3 bad fixes`() = testScope.runTest {
        val route = createStraightRoute()
        engine.start(route)
        advanceUntilIdle()
        
        // First location on route
        fakeLocationRepo.emitLocation(createLocation(0.0, 0.0))
        advanceUntilIdle()
        
        // Three consecutive off-route locations
        repeat(3) { i ->
            // Location 100m perpendicular (off route)
            fakeLocationRepo.emitLocation(createLocation(0.0009 * (i + 1), 0.0))
            advanceUntilIdle()
        }
        
        // Should be in Rerouting state
        val state = engine.state.first { it is NavigationState.Rerouting }
        assertIs<NavigationState.Rerouting>(state)
    }
    
    @Test
    fun `rerouting success returns to Navigating with new route`() = testScope.runTest {
        val route = createStraightRoute()
        engine.start(route)
        advanceUntilIdle()
        
        // Trigger rerouting
        repeat(3) {
            fakeLocationRepo.emitLocation(createLocation(0.0009 * (it + 1), 0.0))
            advanceUntilIdle()
        }
        
        // Should be in Rerouting
        val reroutingState = engine.state.first { it is NavigationState.Rerouting }
        assertIs<NavigationState.Rerouting>(reroutingState)
        
        // Configure fake planner to return a new route
        val newRoute = createAlternativeRoute()
        fakeRoutePlanner.nextResult = listOf(newRoute)
        
        // Wait for rerouting attempt
        advanceUntilIdle()
        delay(1100) // Rerouting delay
        
        // Should return to Navigating with new route
        val navigatingState = engine.state.first { it is NavigationState.Navigating }
        assertIs<NavigationState.Navigating>(navigatingState)
        assertEquals(newRoute.id, navigatingState.route.id)
    }
    
    @Test
    fun `rerouting failure 5x leads to Error state`() = testScope.runTest {
        val route = createStraightRoute()
        engine.start(route)
        advanceUntilIdle()
        
        // Trigger rerouting
        repeat(3) {
            fakeLocationRepo.emitLocation(createLocation(0.0009 * (it + 1), 0.0))
            advanceUntilIdle()
        }
        
        // Configure fake planner to return empty result (failure)
        fakeRoutePlanner.nextResult = emptyList()
        
        // Wait for 5 rerouting attempts
        repeat(5) {
            advanceUntilIdle()
            delay(1100)
        }
        
        // Should be in Error state
        val errorState = engine.state.first { it is NavigationState.Error }
        assertIs<NavigationState.Error>(errorState)
        assertTrue(errorState.message.contains("Failed to reroute"))
    }
    
    @Test
    fun `stop navigation returns to Idle state`() = testScope.runTest {
        val route = createStraightRoute()
        engine.start(route)
        advanceUntilIdle()
        
        // Should be Navigating
        val navigatingState = engine.state.first { it is NavigationState.Navigating }
        assertIs<NavigationState.Navigating>(navigatingState)
        
        // Stop navigation
        engine.stop()
        advanceUntilIdle()
        
        // Should be Idle
        val idleState = engine.state.first { it is NavigationState.Idle }
        assertIs<NavigationState.Idle>(idleState)
    }
    
    // Helper functions
    
    private fun createStraightRoute(): RouteOption {
        val points = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.0, 0.009) // ~1km
        )
        return RouteOption(
            id = 1,
            label = "Test Route",
            points = points,
            distanceMeters = 1000.0,
            durationMillis = 60000L, // 1 minute
            viaRoads = emptyList(),
            instructions = listOf(
                TurnInstruction(
                    index = 0,
                    distanceMeters = 1000.0,
                    direction = TurnDirection.STRAIGHT,
                    text = "Continue",
                    point = points.last()
                )
            ),
            bounds = com.jayesh.satnav.core.utils.LatLngBounds(
                southwest = LatLng(0.0, 0.0),
                northeast = LatLng(0.0, 0.009)
            )
        )
    }
    
    private fun createAlternativeRoute(): RouteOption {
        val points = listOf(
            LatLng(0.001, 0.001),
            LatLng(0.001, 0.010)
        )
        return RouteOption(
            id = 2,
            label = "Alternative",
            points = points,
            distanceMeters = 1100.0,
            durationMillis = 66000L,
            viaRoads = emptyList(),
            instructions = listOf(
                TurnInstruction(
                    index = 0,
                    distanceMeters = 1100.0,
                    direction = TurnDirection.STRAIGHT,
                    text = "Continue",
                    point = points.last()
                )
            ),
            bounds = com.jayesh.satnav.core.utils.LatLngBounds(
                southwest = LatLng(0.001, 0.001),
                northeast = LatLng(0.001, 0.010)
            )
        )
    }
    
    private fun createLocation(lat: Double, lon: Double): Location {
        return Location("test").apply {
            this.latitude = lat
            this.longitude = lon
            this.speed = 5f // 5 m/s
        }
    }
    
    private fun generateLocationsAlongRoute(route: RouteOption): List<Location> {
        val points = route.points
        return listOf(
            createLocation(points[0].latitude, points[0].longitude),
            createLocation(points[0].latitude, points[0].longitude + 0.0045), // halfway
            createLocation(points[1].latitude, points[1].longitude - 0.0001), // near end
            createLocation(points[1].latitude, points[1].longitude) // at end with low speed
        ).apply {
            last().speed = 2f // Low speed for arrival detection
        }
    }
}

// Fake implementations

class FakeLocationRepository : LocationRepository {
    private val _locations = MutableSharedFlow<Location>()
    override val currentLocation = _locations
    
    suspend fun emitLocation(location: Location) {
        _locations.emit(location)
    }
    
    override suspend fun lastKnown(): Location? = null
}

class FakeRoutePlanner : RoutePlanner {
    var nextResult: List<RouteOption> = emptyList()
    
    override suspend fun plan(
        from: LatLng,
        to: LatLng,
        profile: RoutingProfile
    ): List<RouteOption> {
        return nextResult
    }
    
    override suspend fun planWithWaypoints(
        waypoints: List<LatLng>,
        profile: RoutingProfile
    ): List<RouteOption> {
        return nextResult
    }
}