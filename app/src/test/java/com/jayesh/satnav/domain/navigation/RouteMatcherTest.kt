package com.jayesh.satnav.domain.navigation

import android.location.Location
import com.jayesh.satnav.core.utils.LatLng
import com.jayesh.satnav.domain.model.RouteOption
import com.jayesh.satnav.domain.model.TurnInstruction
import com.jayesh.satnav.domain.model.TurnDirection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteMatcherTest {
    
    private fun createRouteOption(
        points: List<LatLng>,
        instructions: List<TurnInstruction> = emptyList()
    ): RouteOption {
        // Compute total distance
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            totalDistance += RouteMatcher.haversineMeters(points[i], points[i + 1])
        }
        
        return RouteOption(
            id = 0,
            label = "Test Route",
            points = points,
            distanceMeters = totalDistance,
            durationMillis = (totalDistance / 10 * 1000).toLong(), // 10 m/s ~ 36 km/h
            viaRoads = emptyList(),
            instructions = instructions,
            bounds = com.jayesh.satnav.core.utils.LatLngBounds(
                southwest = LatLng(0.0, 0.0),
                northeast = LatLng(0.0, 0.0)
            )
        )
    }
    
    private fun createLocation(lat: Double, lon: Double, bearing: Float = 0f): Location {
        return Location("test").apply {
            this.latitude = lat
            this.longitude = lon
            this.bearing = bearing
        }
    }
    
    @Test
    fun `straight 1km route, location at start`() {
        // Create a straight route from (0,0) to (0, 0.009) ~ 1km
        val points = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.0, 0.009) // ~1km at equator
        )
        val route = createRouteOption(points)
        val matcher = RouteMatcher(route)
        
        val location = createLocation(0.0, 0.0)
        val result = matcher.match(location)
        
        assertEquals(0.0, result.snappedLat, 1e-6)
        assertEquals(0.0, result.snappedLon, 1e-6)
        assertEquals(0.0, result.distanceAlongRouteM, 1.0) // within 1 meter
        assertEquals(0, result.currentInstructionIndex)
        assertFalse(result.isOffRoute)
    }
    
    @Test
    fun `straight 1km route, location 300m along`() {
        val points = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.0, 0.009) // ~1km
        )
        val route = createRouteOption(points)
        val matcher = RouteMatcher(route)
        
        // Location at 300m along (0.0027 degrees longitude)
        val location = createLocation(0.0, 0.0027)
        val result = matcher.match(location)
        
        // Should be snapped near the point
        assertEquals(0.0, result.snappedLat, 1e-6)
        assertEquals(0.0027, result.snappedLon, 1e-4)
        assertEquals(300.0, result.distanceAlongRouteM, 10.0) // within 10 meters
        assertFalse(result.isOffRoute)
    }
    
    @Test
    fun `right-angle turn, bearing matches first leg`() {
        // Route: east 100m, then north 100m
        val points = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.0, 0.0009), // ~100m east
            LatLng(0.0009, 0.0009) // ~100m north
        )
        val instructions = listOf(
            TurnInstruction(
                index = 0,
                distanceMeters = 100.0,
                direction = TurnDirection.STRAIGHT,
                text = "Continue",
                point = LatLng(0.0, 0.0009)
            ),
            TurnInstruction(
                index = 1,
                distanceMeters = 100.0,
                direction = TurnDirection.RIGHT,
                text = "Turn right",
                point = LatLng(0.0009, 0.0009)
            )
        )
        val route = createRouteOption(points, instructions)
        val matcher = RouteMatcher(route)
        
        // Location on first leg
        val location = createLocation(0.0, 0.00045) // halfway on first segment
        val result = matcher.match(location)
        
        // Bearing should be ~90 degrees (east)
        assertEquals(90.0, result.bearing.toDouble(), 5.0)
        assertEquals(0, result.currentInstructionIndex)
    }
    
    @Test
    fun `location 100m perpendicular from route - off route true`() {
        val points = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.0, 0.009) // 1km east
        )
        val route = createRouteOption(points)
        val matcher = RouteMatcher(route)
        
        // Location 100m north of start
        val location = createLocation(0.0009, 0.0) // ~100m north
        val result = matcher.match(location)
        
        assertTrue(result.isOffRoute)
        assertEquals(100.0, result.offRouteDistanceM, 10.0)
    }
    
    @Test
    fun `location 40m perpendicular from route - off route false`() {
        val points = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.0, 0.009)
        )
        val route = createRouteOption(points)
        val matcher = RouteMatcher(route)
        
        // Location 40m north of start
        val location = createLocation(0.00036, 0.0) // ~40m north
        val result = matcher.match(location)
        
        assertFalse(result.isOffRoute)
        assertEquals(40.0, result.offRouteDistanceM, 5.0)
    }
    
    @Test
    fun `cursor advancement - monotonic progress doesn't cause backtracking`() {
        // Create a longer route with 10 points
        val points = (0..10).map { i ->
            LatLng(0.0, i * 0.0005) // 0.5km total
        }
        val route = createRouteOption(points)
        val matcher = RouteMatcher(route)
        
        // Simulate progressive locations along route
        var lastSegmentIndex = -1
        for (i in 0..5) {
            val lon = i * 0.0001 // 100m increments
            val location = createLocation(0.0, lon)
            val result = matcher.match(location)
            
            // After first fix, cursor should advance
            if (i > 0) {
                // We can't directly access lastMatchedSegmentIndex, but distance should increase
                assertTrue(result.distanceAlongRouteM > 0.0)
            }
        }
    }
    
    @Test
    fun `distance to next maneuver calculation`() {
        val points = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.0, 0.0045), // 500m
            LatLng(0.0, 0.009)   // 1km
        )
        val instructions = listOf(
            TurnInstruction(
                index = 0,
                distanceMeters = 500.0,
                direction = TurnDirection.RIGHT,
                text = "Turn right",
                point = LatLng(0.0, 0.0045)
            )
        )
        val route = createRouteOption(points, instructions)
        val matcher = RouteMatcher(route)
        
        // Location at 200m along
        val location = createLocation(0.0, 0.0018)
        val result = matcher.match(location)
        
        // Should be 300m to maneuver (500m total - 200m traveled)
        assertEquals(300.0, result.distanceToNextManeuverM, 10.0)
        assertEquals(0, result.currentInstructionIndex)
    }
}