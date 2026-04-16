package com.jayesh.satnav.data.trip

import android.content.Context
import com.jayesh.satnav.domain.model.TripLeg
import com.jayesh.satnav.domain.model.TripPlan
import com.jayesh.satnav.domain.model.TripStop
import com.jayesh.satnav.domain.model.Place
import com.jayesh.satnav.domain.model.PlaceCategory
import com.jayesh.satnav.domain.model.RouteOption
import com.jayesh.satnav.core.utils.LatLng
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SavedTripsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var repository: SavedTripsRepository

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(tempFolder.root)
        repository = SavedTripsRepository(context)
    }

    private fun createSampleTrip(name: String? = null): TripPlan {
        val places = listOf(
            Place("1", "Start", PlaceCategory.Other, 28.0, 77.0, null, null),
            Place("2", "End", PlaceCategory.Other, 28.1, 77.1, null, null),
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

        val leg = TripLeg(
            fromStopId = stops[0].id,
            toStopId = stops[1].id,
            selectedRoute = routeOption,
            alternativeRoutes = emptyList()
        )

        return TripPlan(
            name = name,
            stops = stops,
            legs = listOf(leg),
            totalDistanceMeters = 10000.0,
            totalDurationMillis = 600000,
            createdAt = Clock.System.now(),
        )
    }

    @Test
    fun `save and getById roundtrip`() = runTest {
        // Arrange
        val trip = createSampleTrip("My Trip")

        // Act
        val saveResult = repository.save(trip)
        assertTrue(saveResult.isSuccess)

        val retrieved = repository.getById(trip.id)

        // Assert
        assertNotNull(retrieved)
        assertEquals(trip.id, retrieved.id)
        assertEquals("My Trip", retrieved.name)
        assertEquals(2, retrieved.stops.size)
        assertEquals(1, retrieved.legs.size)
    }

    @Test
    fun `save and getAll returns summary`() = runTest {
        // Arrange
        val trip1 = createSampleTrip("Trip 1")
        val trip2 = createSampleTrip("Trip 2")

        // Act
        repository.save(trip1)
        repository.save(trip2)

        val all = repository.getAll()

        // Assert
        assertEquals(2, all.size)
        assertTrue(all.any { it.name == "Trip 1" })
        assertTrue(all.any { it.name == "Trip 2" })
    }

    @Test
    fun `delete removes trip`() = runTest {
        // Arrange
        val trip = createSampleTrip("Trip to Delete")
        repository.save(trip)

        // Act
        val deleteResult = repository.delete(trip.id)
        assertTrue(deleteResult.isSuccess)

        val all = repository.getAll()

        // Assert
        assertTrue(all.isEmpty())
    }

    @Test
    fun `exists checks if trip exists`() = runTest {
        // Arrange
        val trip = createSampleTrip("Trip")
        repository.save(trip)

        // Act
        val exists = repository.exists(trip.id)
        val notExists = repository.exists("non-existent-id")

        // Assert
        assertTrue(exists)
        assertTrue(!notExists)
    }

    @Test
    fun `corrupt JSON doesn't crash`() = runTest {
        // Arrange - manually write corrupt JSON
        val tripsDir = File(tempFolder.root, "trips")
        tripsDir.mkdirs()

        val corruptFile = File(tripsDir, "corrupt.json")
        corruptFile.writeText("{ invalid json ]}")

        // Act
        val retrieved = repository.getById("corrupt")

        // Assert - should return null, not crash
        assertEquals(null, retrieved)
    }

    @Test
    fun `updateName updates trip name`() = runTest {
        // Arrange
        val trip = createSampleTrip("Old Name")
        repository.save(trip)

        // Act
        val updateResult = repository.updateName(trip.id, "New Name")
        assertTrue(updateResult.isSuccess)

        val updated = repository.getById(trip.id)

        // Assert
        assertNotNull(updated)
        assertEquals("New Name", updated.name)
    }
}
