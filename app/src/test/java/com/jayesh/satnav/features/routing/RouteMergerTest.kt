package com.jayesh.satnav.features.routing

import com.jayesh.satnav.domain.model.OfflineRoute
import com.jayesh.satnav.domain.model.NavInstruction
import com.jayesh.satnav.domain.model.RouteCoordinate
import com.jayesh.satnav.domain.model.RoutingProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouteMergerTest {

    // =========================================
    // ✅ TEST 1: Ordered Waypoints
    // =========================================
    @Test
    fun `merge preserves order for 4 waypoints A B C D`() {
        val segAB = createSegment(12.0, 77.0, 12.1, 77.1, 1000.0, 120000)
        val segBC = createSegment(12.1, 77.1, 12.2, 77.2, 1500.0, 180000)
        val segCD = createSegment(12.2, 77.2, 12.3, 77.3, 2000.0, 240000)

        val merged = RouteMerger.merge(listOf(segAB, segBC, segCD), RoutingProfile.CAR, 4)

        assertEquals(12.0, merged.points.first().latitude, 1e-6)
        assertEquals(77.0, merged.points.first().longitude, 1e-6)
        assertEquals(12.3, merged.points.last().latitude, 1e-6)
        assertEquals(77.3, merged.points.last().longitude, 1e-6)
        assertEquals(4500.0, merged.distanceMeters, 0.0)
        assertEquals(540000L, merged.durationMillis)
    }

    // =========================================
    // ✅ TEST 2: Segment Continuity
    // =========================================
    @Test
    fun `merge ensures end of segment equals start of next`() {
        val seg1 = createSegment(12.0, 77.0, 12.1, 77.1, 1000.0, 120000)
        val seg2 = createSegment(12.1, 77.1, 12.2, 77.2, 1000.0, 120000)
        val seg3 = createSegment(12.2, 77.2, 12.3, 77.3, 1000.0, 120000)

        val merged = RouteMerger.merge(listOf(seg1, seg2, seg3), RoutingProfile.CAR, 4)

        for (i in 0 until merged.points.lastIndex) {
            val current = merged.points[i]
            val next = merged.points[i + 1]
            assertTrue(current.latitude != next.latitude || current.longitude != next.longitude)
        }
    }

    // =========================================
    // ✅ TEST 3: Duplicate Point Handling
    // =========================================
    @Test
    fun `merge handles duplicate waypoints A B B C`() {
        val segAB = createSegment(12.0, 77.0, 12.1, 77.1, 1000.0, 120000)
        val segBB = createSegment(12.1, 77.1, 12.1, 77.1, 0.0, 0)
        val segBC = createSegment(12.1, 77.1, 12.2, 77.2, 1000.0, 120000)

        val merged = RouteMerger.merge(listOf(segAB, segBB, segBC), RoutingProfile.CAR, 4)

        assertEquals(2000.0, merged.distanceMeters, 0.0)
        assertEquals(240000L, merged.durationMillis)
    }

    // =========================================
    // ✅ TEST 4: Very Close Waypoints
    // =========================================
    @Test
    fun `merge handles very close waypoints within 5 meters`() {
        val seg1 = createSegment(12.9716, 77.5946, 12.97165, 77.59465, 5.0, 1000)
        val seg2 = createSegment(12.97165, 77.59465, 12.9717, 77.5947, 5.0, 1000)

        val merged = RouteMerger.merge(listOf(seg1, seg2), RoutingProfile.CAR, 3)

        assertEquals(10.0, merged.distanceMeters, 0.0)
        assertEquals(2000L, merged.durationMillis)
        assertTrue(merged.points.size >= 2)
    }

    // =========================================
    // ✅ TEST 5: Same Start & End
    // =========================================
    @Test
    fun `merge handles same start and end point A A`() {
        val seg = createSegment(12.0, 77.0, 12.0, 77.0, 0.0, 0)

        val merged = RouteMerger.merge(listOf(seg), RoutingProfile.CAR, 2)

        assertEquals(0.0, merged.distanceMeters, 0.0)
        assertEquals(0L, merged.durationMillis)
    }

    // =========================================
    // ✅ TEST 15: Duplicate Node Removal
    // =========================================
    @Test
    fun `merge removes duplicate nodes at segment boundaries`() {
        val seg1 = OfflineRoute(
            profile = RoutingProfile.CAR,
            points = listOf(RouteCoordinate(12.0, 77.0), RouteCoordinate(12.1, 77.1), RouteCoordinate(12.2, 77.2)),
            distanceMeters = 1000.0,
            durationMillis = 120000,
            computationTimeMillis = 80,
            instructions = emptyList()
        )
        val seg2 = OfflineRoute(
            profile = RoutingProfile.CAR,
            points = listOf(RouteCoordinate(12.2, 77.2), RouteCoordinate(12.3, 77.3), RouteCoordinate(12.4, 77.4)),
            distanceMeters = 1000.0,
            durationMillis = 120000,
            computationTimeMillis = 80,
            instructions = emptyList()
        )

        val merged = RouteMerger.merge(listOf(seg1, seg2), RoutingProfile.CAR, 3)

        assertEquals(5, merged.points.size)
        assertEquals(12.2, merged.points[2].latitude, 1e-6)
    }

    // =========================================
    // ✅ TEST 17: Distance Aggregation
    // =========================================
    @Test
    fun `merge correctly sums total distance`() {
        val segments = listOf(
            createSegment(12.0, 77.0, 12.1, 77.1, 1000.0, 120000),
            createSegment(12.1, 77.1, 12.2, 77.2, 1500.0, 180000),
            createSegment(12.2, 77.2, 12.3, 77.3, 2500.0, 300000)
        )

        val merged = RouteMerger.merge(segments, RoutingProfile.CAR, 4)

        assertEquals(5000.0, merged.distanceMeters, 0.0)
    }

    // =========================================
    // ✅ TEST 18: Duration Aggregation
    // =========================================
    @Test
    fun `merge correctly sums total duration`() {
        val segments = listOf(
            createSegment(12.0, 77.0, 12.1, 77.1, 1000.0, 10000),
            createSegment(12.1, 77.1, 12.2, 77.2, 1000.0, 20000),
            createSegment(12.2, 77.2, 12.3, 77.3, 1000.0, 30000)
        )

        val merged = RouteMerger.merge(segments, RoutingProfile.CAR, 4)

        assertEquals(60000L, merged.durationMillis)
    }

    // =========================================
    // Edge Cases & Error Tests
    // =========================================


    // =========================================
    // Original baseline test
    // =========================================
    @Test
    fun `merge trims duplicate boundary points and aggregates totals`() {
        val segmentOne = OfflineRoute(
            profile = RoutingProfile.CAR,
            points = listOf(
                RouteCoordinate(12.0, 77.0),
                RouteCoordinate(12.1, 77.1),
                RouteCoordinate(12.2, 77.2),
            ),
            distanceMeters = 1000.0,
            durationMillis = 120000,
            computationTimeMillis = 80,
            instructions = emptyList()
        )
        val segmentTwo = OfflineRoute(
            profile = RoutingProfile.CAR,
            points = listOf(
                RouteCoordinate(12.2, 77.2),
                RouteCoordinate(12.3, 77.3),
            ),
            distanceMeters = 500.0,
            durationMillis = 60000,
            computationTimeMillis = 40,
            instructions = emptyList()
        )

        val merged = RouteMerger.merge(
            segments = listOf(segmentOne, segmentTwo),
            profile = RoutingProfile.CAR,
            waypointCount = 3,
        )

        assertEquals(4, merged.points.size)
        assertEquals(1500.0, merged.distanceMeters, 0.0)
        assertEquals(180000L, merged.durationMillis)
        assertEquals(120L, merged.computationTimeMillis)
        assertEquals(2, merged.segmentCount)
        assertEquals(3, merged.waypointCount)
    }

    @Test
    fun `merge drops intermediate arrive instruction but keeps final arrival`() {
        val segmentOne = OfflineRoute(
            profile = RoutingProfile.CAR,
            points = listOf(
                RouteCoordinate(12.0, 77.0),
                RouteCoordinate(12.1, 77.1),
            ),
            distanceMeters = 1000.0,
            durationMillis = 120000,
            computationTimeMillis = 80,
            instructions = listOf(
                NavInstruction(sign = 2, streetName = "MG Road", distanceMeters = 750.0, durationMillis = 90000),
                NavInstruction(sign = NavInstruction.SIGN_FINISH, streetName = "", distanceMeters = 250.0, durationMillis = 30000),
            ),
        )
        val segmentTwo = OfflineRoute(
            profile = RoutingProfile.CAR,
            points = listOf(
                RouteCoordinate(12.1, 77.1),
                RouteCoordinate(12.2, 77.2),
            ),
            distanceMeters = 500.0,
            durationMillis = 60000,
            computationTimeMillis = 40,
            instructions = listOf(
                NavInstruction(sign = 1, streetName = "Brigade Road", distanceMeters = 250.0, durationMillis = 30000),
                NavInstruction(sign = NavInstruction.SIGN_FINISH, streetName = "", distanceMeters = 250.0, durationMillis = 30000),
            ),
        )

        val merged = RouteMerger.merge(
            segments = listOf(segmentOne, segmentTwo),
            profile = RoutingProfile.CAR,
            waypointCount = 3,
        )

        assertEquals(3, merged.instructions.size)
        assertEquals(1, merged.instructions[1].sign)
        assertEquals(NavInstruction.SIGN_FINISH, merged.instructions.last().sign)
    }

    private fun createSegment(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double,
        distance: Double, duration: Long
    ): OfflineRoute {
        return OfflineRoute(
            profile = RoutingProfile.CAR,
            points = listOf(RouteCoordinate(fromLat, fromLon), RouteCoordinate(toLat, toLon)),
            distanceMeters = distance,
            durationMillis = duration,
            computationTimeMillis = 50,
            instructions = emptyList()
        )
    }
}
