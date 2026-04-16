package com.jayesh.satnav.domain.model

import com.graphhopper.ResponsePath
import com.graphhopper.util.Instruction
import com.graphhopper.util.InstructionList
import com.graphhopper.util.PointList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ResponsePathMappingTest {

    @Test
    fun `viaRoads extraction picks longest-duration street names`() {
        // Simulate path details with street name intervals
        // This test is conceptual because we cannot directly instantiate PathDetail
        // Instead we test the extraction logic in RouteOption companion object.
        // We'll test the helper function extractViaRoads.
        val intervals = listOf(
            Triple(0, 10, "MG Road"),
            Triple(10, 30, "Outer Ring Road"),
            Triple(30, 35, "Lane"),
            Triple(35, 50, "Highway"),
        )
        val totalTime = 100L

        // The extraction sorts by interval length (end - start) descending
        // and picks top 3.
        val viaRoads = RouteOption.extractViaRoads(intervals, totalTime)

        // Expected order: Outer Ring Road (length 20), Highway (15), MG Road (10)
        assertEquals(3, viaRoads.size)
        assertEquals("Outer Ring Road", viaRoads[0])
        assertEquals("Highway", viaRoads[1])
        assertEquals("MG Road", viaRoads[2])
    }

    @Test
    fun `viaRoads returns empty list when no intervals`() {
        val viaRoads = RouteOption.extractViaRoads(emptyList(), 100L)
        assertTrue(viaRoads.isEmpty())
    }

    @Test
    fun `ManeuverSign mapping covers all GraphHopper sign ints`() {
        // Test mapping for known GraphHopper sign values
        val mapping = mapOf(
            -8 to ManeuverSign.UTurn,
            -7 to ManeuverSign.SharpLeft,
            -6 to ManeuverSign.Left,
            -5 to ManeuverSign.SlightLeft,
            -4 to ManeuverSign.Continue,
            -3 to ManeuverSign.KeepLeft,
            -2 to ManeuverSign.KeepRight,
            -1 to ManeuverSign.SlightRight,
            0 to ManeuverSign.Right,
            1 to ManeuverSign.SharpRight,
            2 to ManeuverSign.RoundaboutEnter,
            3 to ManeuverSign.RoundaboutExit,
            4 to ManeuverSign.Arrive,
            5 to ManeuverSign.Depart,
            99 to ManeuverSign.Unknown, // unknown sign
        )

        mapping.forEach { (signInt, expected) ->
            val instruction = mock<Instruction> {
                on { sign } doReturn signInt
                on { name } doReturn ""
                on { distance } doReturn 100.0
                on { time } doReturn 5000L
            }
            val turnInstruction = TurnInstruction.fromGraphHopperInstruction(instruction, 0)
            assertEquals(expected, turnInstruction.sign)
        }
    }

    @Test
    fun `bounds computation from points`() {
        val points = listOf(
            LatLng(12.0, 77.0),
            LatLng(13.0, 78.0),
            LatLng(11.5, 79.0),
            LatLng(12.5, 76.5),
        )
        val bounds = RouteOption.computeBounds(points)

        assertEquals(11.5, bounds.southwest.latitude, 0.001)
        assertEquals(76.5, bounds.southwest.longitude, 0.001)
        assertEquals(13.0, bounds.northeast.latitude, 0.001)
        assertEquals(79.0, bounds.northeast.longitude, 0.001)
    }

    @Test
    fun `bounds computation with single point`() {
        val points = listOf(LatLng(12.9716, 77.5946))
        val bounds = RouteOption.computeBounds(points)

        assertEquals(12.9716, bounds.southwest.latitude, 0.001)
        assertEquals(77.5946, bounds.southwest.longitude, 0.001)
        assertEquals(12.9716, bounds.northeast.latitude, 0.001)
        assertEquals(77.5946, bounds.northeast.longitude, 0.001)
    }

    @Test
    fun `bounds computation with empty points returns zero bounds`() {
        val bounds = RouteOption.computeBounds(emptyList())
        assertEquals(0.0, bounds.southwest.latitude, 0.001)
        assertEquals(0.0, bounds.southwest.longitude, 0.001)
        assertEquals(0.0, bounds.northeast.latitude, 0.001)
        assertEquals(0.0, bounds.northeast.longitude, 0.001)
    }

    @Test
    fun `TurnInstruction text includes street name when present`() {
        val instruction = mock<Instruction> {
            on { sign } doReturn 0 // Right
            on { name } doReturn "MG Road"
            on { distance } doReturn 150.0
            on { time } doReturn 30000L
        }
        val turnInstruction = TurnInstruction.fromGraphHopperInstruction(instruction, 5)

        assertEquals(ManeuverSign.Right, turnInstruction.sign)
        assertEquals("Turn right onto MG Road", turnInstruction.text)
        assertEquals("MG Road", turnInstruction.streetName)
        assertEquals(150.0, turnInstruction.distanceMeters, 0.01)
        assertEquals(30000L, turnInstruction.durationMillis)
        assertEquals(5, turnInstruction.pointIndex)
    }

    @Test
    fun `TurnInstruction text excludes street name when blank`() {
        val instruction = mock<Instruction> {
            on { sign } doReturn -4 // Continue
            on { name } doReturn ""
            on { distance } doReturn 200.0
            on { time } doReturn 40000L
        }
        val turnInstruction = TurnInstruction.fromGraphHopperInstruction(instruction, 2)

        assertEquals(ManeuverSign.Continue, turnInstruction.sign)
        assertEquals("Continue", turnInstruction.text)
        assertEquals(null, turnInstruction.streetName)
    }
}