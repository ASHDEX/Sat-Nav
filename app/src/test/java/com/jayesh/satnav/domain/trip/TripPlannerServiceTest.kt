package com.jayesh.satnav.domain.trip

import com.jayesh.satnav.core.utils.LatLng
import com.jayesh.satnav.data.search.OfflineGeocoder
import com.jayesh.satnav.domain.model.ManeuverSign
import com.jayesh.satnav.domain.model.Place
import com.jayesh.satnav.domain.model.PlaceCategory
import com.jayesh.satnav.domain.model.RouteOption
import com.jayesh.satnav.domain.model.TripPlan
import com.jayesh.satnav.domain.model.TripStop
import com.jayesh.satnav.domain.repository.LocationRepository
import com.jayesh.satnav.domain.routing.NoRouteException
import com.jayesh.satnav.domain.routing.RoutePlanner
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import android.location.Location
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TripPlannerServiceTest {

    private lateinit var geocoder: OfflineGeocoder
    private lateinit var routePlanner: RoutePlanner
    private lateinit var locationRepo: LocationRepository
    private lateinit var service: TripPlannerService

    @Before
    fun setUp() {
        geocoder = mockk()
        routePlanner = mockk()
        locationRepo = mockk()
        service = TripPlannerService(geocoder, routePlanner, locationRepo)
    }

    @Test
    fun `resolvePlace returns list of places`() = runTest {
        // Arrange
        val places = listOf(
            Place(
                id = "1",
                name = "India Gate",
                category = PlaceCategory.Landmark,
                lat = 28.6129,
                lon = 77.2295,
                address = "Rajpath, New Delhi",
                distanceMeters = null
            )
        )
        coEvery { geocoder.search(any(), any(), any()) } returns places

        // Act
        val result = service.resolvePlace("india gate", null)

        // Assert
        assertEquals(1, result.size)
        assertEquals("India Gate", result[0].name)
    }

    @Test
    fun `computeTrip with 2 stops creates 1 leg`() = runTest {
        // Arrange
        val place1 = Place("1", "Start", PlaceCategory.Other, 28.0, 77.0, null, null)
        val place2 = Place("2", "End", PlaceCategory.Other, 28.1, 77.1, null, null)

        val stop1 = TripStop(place = place1, query = "Start", order = 0)
        val stop2 = TripStop(place = place2, query = "End", order = 1)

        val routeOption = RouteOption(
            id = 0,
            label = "Fastest",
            points = listOf(LatLng(28.0, 77.0), LatLng(28.1, 77.1)),
            distanceMeters = 10000.0,
            durationMillis = 600000,
            viaRoads = emptyList(),
            instructions = emptyList(),
            bounds = null
        )

        coEvery { routePlanner.plan(any(), any(), any()) } returns Result.success(listOf(routeOption))
        coEvery { locationRepo.lastKnown() } returns null

        // Act
        val result = service.computeTrip(listOf(stop1, stop2), "car")

        // Assert
        assertTrue(result.isSuccess)
        val tripPlan = result.getOrNull()
        assertNotNull(tripPlan)
        assertEquals(2, tripPlan.stops.size)
        assertEquals(1, tripPlan.legs.size)
        assertEquals(10000.0, tripPlan.totalDistanceMeters)
    }

    @Test
    fun `computeTrip with 4 stops creates 3 legs`() = runTest {
        // Arrange
        val places = listOf(
            Place("1", "A", PlaceCategory.Other, 28.0, 77.0, null, null),
            Place("2", "B", PlaceCategory.Other, 28.1, 77.1, null, null),
            Place("3", "C", PlaceCategory.Other, 28.2, 77.2, null, null),
            Place("4", "D", PlaceCategory.Other, 28.3, 77.3, null, null),
        )
        val stops = places.mapIndexed { i, p -> TripStop(place = p, query = p.name, order = i) }

        val routeOption = RouteOption(
            id = 0,
            label = "Fastest",
            points = listOf(LatLng(28.0, 77.0), LatLng(28.1, 77.1)),
            distanceMeters = 10000.0,
            durationMillis = 600000,
            viaRoads = emptyList(),
            instructions = emptyList(),
            bounds = null
        )

        coEvery { routePlanner.plan(any(), any(), any()) } returns Result.success(listOf(routeOption))
        coEvery { locationRepo.lastKnown() } returns null

        // Act
        val result = service.computeTrip(stops, "car")

        // Assert
        assertTrue(result.isSuccess)
        val tripPlan = result.getOrNull()
        assertNotNull(tripPlan)
        assertEquals(4, tripPlan.stops.size)
        assertEquals(3, tripPlan.legs.size)
    }

    @Test
    fun `computeTrip fails when one leg has no route`() = runTest {
        // Arrange
        val place1 = Place("1", "Start", PlaceCategory.Other, 28.0, 77.0, null, null)
        val place2 = Place("2", "End", PlaceCategory.Other, 28.1, 77.1, null, null)

        val stop1 = TripStop(place = place1, query = "Start", order = 0)
        val stop2 = TripStop(place = place2, query = "End", order = 1)

        coEvery { routePlanner.plan(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { locationRepo.lastKnown() } returns null

        // Act
        val result = service.computeTrip(listOf(stop1, stop2), "car")

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `computeTrip fails when GPS unavailable for origin`() = runTest {
        // Arrange
        val stop1 = TripStop(place = null, query = "Current location", order = 0, isCurrentLocation = true)
        val place2 = Place("2", "End", PlaceCategory.Other, 28.1, 77.1, null, null)
        val stop2 = TripStop(place = place2, query = "End", order = 1)

        coEvery { locationRepo.lastKnown() } returns null

        // Act
        val result = service.computeTrip(listOf(stop1, stop2), "car")

        // Assert
        assertTrue(result.isFailure)
    }
}
