package com.jayesh.satnav.domain.routing

import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.ResponsePath
import com.graphhopper.util.Instruction
import com.graphhopper.util.InstructionList
import com.graphhopper.util.PointList
import com.jayesh.satnav.core.utils.LatLng
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RoutePlannerTest {

    private val mockHopper = mockk<GraphHopper>()
    private val planner = RoutePlanner(mockHopper)

    @Test
    fun `plan returns 3 routes when GraphHopper returns 3 paths`() = runTest {
        // Given
        val start = LatLng(12.9716, 77.5946) // Bangalore
        val dest = LatLng(13.0827, 80.2707)  // Chennai
        val mockPaths = listOf(
            createMockPath(0, 10000.0, 3600000L),
            createMockPath(1, 11000.0, 3800000L),
            createMockPath(2, 12000.0, 4000000L),
        )
        val mockResponse = mockk<com.graphhopper.GHResponse>().apply {
            every { hasErrors() } returns false
            every { all } returns mockPaths
            every { errors } returns emptyList()
        }
        coEvery { mockHopper.route(any<GHRequest>()) } returns mockResponse

        // When
        val result = planner.plan(start, dest)

        // Then
        assertTrue(result.isSuccess)
        val routes = result.getOrThrow()
        assertEquals(3, routes.size)
        assertEquals("Fastest", routes[0].label)
        assertEquals("Alternative 1", routes[1].label)
        assertEquals("Alternative 2", routes[2].label)
        assertEquals(10000.0, routes[0].distanceMeters, 0.01)
        assertEquals(11000.0, routes[1].distanceMeters, 0.01)
        assertEquals(12000.0, routes[2].distanceMeters, 0.01)
    }

    @Test
    fun `plan returns empty list when start equals destination`() = runTest {
        val point = LatLng(12.9716, 77.5946)
        val result = planner.plan(point, point)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `plan returns failure when GraphHopper returns errors`() = runTest {
        val start = LatLng(12.9716, 77.5946)
        val dest = LatLng(13.0827, 80.2707)
        val mockResponse = mockk<com.graphhopper.GHResponse>().apply {
            every { hasErrors() } returns true
            every { errors } returns listOf(
                com.graphhopper.util.exceptions.ConnectionNotFoundException("No connection", emptyList())
            )
        }
        coEvery { mockHopper.route(any<GHRequest>()) } returns mockResponse

        val result = planner.plan(start, dest)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RoutingException)
    }

    @Test
    fun `plan returns failure when no route found`() = runTest {
        val start = LatLng(12.9716, 77.5946)
        val dest = LatLng(13.0827, 80.2707)
        val mockResponse = mockk<com.graphhopper.GHResponse>().apply {
            every { hasErrors() } returns false
            every { all } returns emptyList()
        }
        coEvery { mockHopper.route(any<GHRequest>()) } returns mockResponse

        val result = planner.plan(start, dest)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoRouteException)
    }

    @Test
    fun `plan returns fewer alternatives when GraphHopper returns fewer`() = runTest {
        val start = LatLng(12.9716, 77.5946)
        val dest = LatLng(13.0827, 80.2707)
        val mockPaths = listOf(
            createMockPath(0, 10000.0, 3600000L),
        )
        val mockResponse = mockk<com.graphhopper.GHResponse>().apply {
            every { hasErrors() } returns false
            every { all } returns mockPaths
        }
        coEvery { mockHopper.route(any<GHRequest>()) } returns mockResponse

        val result = planner.plan(start, dest)

        assertTrue(result.isSuccess)
        val routes = result.getOrThrow()
        assertEquals(1, routes.size)
        assertEquals("Fastest", routes[0].label)
    }

    @Test
    fun `plan uses ALT_ROUTE algorithm with correct hints`() = runTest {
        val start = LatLng(12.9716, 77.5946)
        val dest = LatLng(13.0827, 80.2707)
        val mockResponse = mockk<com.graphhopper.GHResponse>().apply {
            every { hasErrors() } returns false
            every { all } returns listOf(createMockPath(0, 10000.0, 3600000L))
        }
        var capturedRequest: GHRequest? = null
        coEvery { mockHopper.route(captureNullable(capturedRequest)) } answers {
            capturedRequest = it.invocation.args[0] as GHRequest
            mockResponse
        }

        planner.plan(start, dest)

        assertNotNull(capturedRequest)
        assertEquals("car", capturedRequest?.profile)
        assertEquals("alt_route", capturedRequest?.algorithm)
        assertEquals(3, capturedRequest?.hints?.getInt("alternative_route.max_paths", 0))
        assertEquals(1.4, capturedRequest?.hints?.getDouble("alternative_route.max_weight", 0.0), 0.01)
        assertEquals(0.6, capturedRequest?.hints?.getDouble("alternative_route.max_share", 0.0), 0.01)
        assertEquals(Locale.getDefault(), capturedRequest?.locale)
        assertTrue(capturedRequest?.pathDetails?.contains("street_name") == true)
    }

    private fun createMockPath(index: Int, distance: Double, time: Long): ResponsePath {
        val path = mockk<ResponsePath>()
        val points = PointList(2, true).apply {
            add(12.9716, 77.5946)
            add(13.0827, 80.2707)
        }
        every { path.points } returns points
        every { path.distance } returns distance
        every { path.time } returns time
        every { path.instructions } returns InstructionList().apply {
            add(Instruction(0, "Turn right", null, 0.0, 0, 0))
        }
        every { path.pathDetails } returns null
        return path
    }
}