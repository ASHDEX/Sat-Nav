package com.jayesh.satnav.features.navigation

import com.jayesh.satnav.core.utils.NavLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PositionInterpolatorTest {
    
    @Before
    fun setup() {
        NavLog.disableForTests()
    }
    
    @Test
    fun testInitialState() {
        val interpolator = PositionInterpolator()
        val (lat, lon, bearing) = interpolator.getCurrentPosition()
        
        assertEquals("Initial latitude should be 0", 0.0, lat, 0.0001)
        assertEquals("Initial longitude should be 0", 0.0, lon, 0.0001)
        assertNull("Initial bearing should be null", bearing)
        assertFalse("Should not be interpolating initially", interpolator.isInterpolating())
    }
    
    @Test
    fun testFirstPositionSetDirectly() {
        val interpolator = PositionInterpolator()
        var positionUpdates = 0
        var lastLat = 0.0
        var lastLon = 0.0
        
        interpolator.onPositionUpdated = { lat, lon, bearing ->
            positionUpdates++
            lastLat = lat
            lastLon = lon
        }
        
        // First position should be set directly
        interpolator.interpolateTo(37.7749, -122.4194, 45.0f)
        
        // Wait a bit for any async operations (simplified test)
        Thread.sleep(100)
        
        // The callback should have been called
        assertTrue("Should have received at least one position update", positionUpdates >= 1)
        assertEquals("Latitude should match target", 37.7749, lastLat, 0.0001)
        assertEquals("Longitude should match target", -122.4194, lastLon, 0.0001)
    }
    
    @Test
    fun testStopAndJumpToTarget() {
        val interpolator = PositionInterpolator()
        
        // Set first position (use coordinates that will trigger interpolation)
        interpolator.interpolateTo(37.7749, -122.4194)
        Thread.sleep(100)
        
        // Start interpolation with coordinates ~50 meters away
        interpolator.interpolateTo(37.7753, -122.4198, 180.0f)
        
        // Give interpolation a moment to start
        Thread.sleep(50)
        
        // Verify interpolation started
        assertTrue("Should be interpolating", interpolator.isInterpolating())
        
        // Stop and jump to target
        interpolator.stopAndJumpToTarget()
        
        // Should have jumped to target (which is filtered position, not raw input)
        // Filtered latitude = 37.7749 * 0.7 + 37.7753 * 0.3 = 37.77502
        // Filtered longitude = -122.4194 * 0.7 + (-122.4198) * 0.3 = -122.41954
        val (lat, lon, bearing) = interpolator.getCurrentPosition()
        assertEquals("Should jump to filtered target latitude", 37.77502, lat, 0.0001)
        assertEquals("Should jump to filtered target longitude", -122.41954, lon, 0.0001)
        assertNotNull("Bearing should not be null", bearing)
        assertEquals("Should jump to target bearing", 180.0f, bearing!!, 0.1f)
        assertFalse("Should not be interpolating after stop", interpolator.isInterpolating())
    }
    
    @Test
    fun testReset() {
        val interpolator = PositionInterpolator()
        
        // Set a position
        interpolator.interpolateTo(5.0, 5.0, 270.0f)
        Thread.sleep(100)
        
        // Verify position is set
        val (lat1, lon1, bearing1) = interpolator.getCurrentPosition()
        assertEquals(5.0, lat1, 0.0001)
        assertEquals(5.0, lon1, 0.0001)
        assertNotNull("Bearing should not be null", bearing1)
        assertEquals(270.0f, bearing1!!, 0.1f)
        
        // Reset
        interpolator.reset()
        
        // Verify back to initial state
        val (lat2, lon2, bearing2) = interpolator.getCurrentPosition()
        assertEquals("Latitude should reset to 0", 0.0, lat2, 0.0001)
        assertEquals("Longitude should reset to 0", 0.0, lon2, 0.0001)
        assertNull("Bearing should reset to null", bearing2)
        assertFalse("Should not be interpolating after reset", interpolator.isInterpolating())
    }
    
    @Test
    fun testBearingNormalization() {
        // This test verifies the bearing normalization logic works correctly
        // We'll test the normalizeBearing function indirectly through the interpolator
        val interpolator = PositionInterpolator()
        
        // Set first position with a bearing that needs normalization
        interpolator.interpolateTo(37.7749, -122.4194, 450.0f) // 450° = 90° after normalization
        Thread.sleep(100)
        
        // Get current position - bearing should be normalized
        val (_, _, bearing) = interpolator.getCurrentPosition()
        assertNotNull("Bearing should not be null", bearing)
        
        // Check if bearing is in valid range (0-360)
        val normalizedBearing = bearing!!
        assertTrue("Bearing should be normalized to 0-360 range (was $normalizedBearing)",
                   normalizedBearing >= 0f && normalizedBearing <= 360f)
        
        // 450° should normalize to 90°
        assertEquals("450° should normalize to 90°", 90.0f, normalizedBearing, 1.0f)
    }
    
    @Test
    fun testInterpolationStateManagement() {
        val interpolator = PositionInterpolator()
        
        // Initially not interpolating
        assertFalse("Should not be interpolating initially", interpolator.isInterpolating())
        
        // Set first position
        interpolator.interpolateTo(37.7749, -122.4194)
        Thread.sleep(100)
        
        // Start interpolation with coordinates ~50 meters away
        interpolator.interpolateTo(37.7753, -122.4198, 90.0f)
        
        // Should be interpolating
        assertTrue("Should be interpolating after starting", interpolator.isInterpolating())
        
        // Stop interpolation
        interpolator.stopAndJumpToTarget()
        
        // Should not be interpolating
        assertFalse("Should not be interpolating after stop", interpolator.isInterpolating())
        
        // Reset
        interpolator.reset()
        
        // Should not be interpolating after reset
        assertFalse("Should not be interpolating after reset", interpolator.isInterpolating())
    }
    
    @Test
    fun testGetCurrentPosition() {
        val interpolator = PositionInterpolator()
        
        // Initial position
        var (lat, lon, bearing) = interpolator.getCurrentPosition()
        assertEquals(0.0, lat, 0.0001)
        assertEquals(0.0, lon, 0.0001)
        assertNull(bearing)
        
        // Set position
        interpolator.interpolateTo(12.345, 67.890, 123.0f)
        Thread.sleep(100)
        
        // Get updated position
        val (lat2, lon2, bearing2) = interpolator.getCurrentPosition()
        assertEquals(12.345, lat2, 0.0001)
        assertEquals(67.890, lon2, 0.0001)
        assertNotNull("Bearing should not be null", bearing2)
        assertEquals(123.0f, bearing2!!, 0.1f)
    }
    
    @Test
    fun testStationaryBehavior() {
        val interpolator = PositionInterpolator()
        var positionUpdateCount = 0
        var lastLat = 0.0
        var lastLon = 0.0
        
        interpolator.onPositionUpdated = { lat, lon, bearing ->
            positionUpdateCount++
            lastLat = lat
            lastLon = lon
        }
        
        // Set initial position
        interpolator.interpolateTo(37.7749, -122.4194)
        Thread.sleep(100)
        
        val initialUpdateCount = positionUpdateCount
        val initialLat = lastLat
        val initialLon = lastLon
        
        // Send small movements (less than 5m GPS noise filter threshold)
        // These should be filtered out as GPS noise
        interpolator.interpolateTo(37.77491, -122.41941) // ~1.1m movement
        Thread.sleep(50)
        
        interpolator.interpolateTo(37.77489, -122.41939) // ~1.4m movement
        Thread.sleep(50)
        
        interpolator.interpolateTo(37.77492, -122.41942) // ~2.2m movement
        Thread.sleep(50)
        
        // Position should not have changed (GPS noise filtered)
        assertEquals("Should not receive additional position updates for small movements",
            initialUpdateCount, positionUpdateCount)
        assertEquals("Latitude should remain unchanged", initialLat, lastLat, 0.000001)
        assertEquals("Longitude should remain unchanged", initialLon, lastLon, 0.000001)
        assertFalse("Should not be interpolating for stationary noise", interpolator.isInterpolating())
        
        // Now send a much larger movement (>17m raw, >5m after 0.7 smoothing) - should trigger interpolation
        // Raw movement: 37.7752 - 37.7749 = 0.0003 degrees ≈ 33.4 meters
        // Filtered movement: 0.3 * 33.4 ≈ 10 meters > 5m threshold
        interpolator.interpolateTo(37.7752, -122.4197) // ~33m movement
        Thread.sleep(50)
        
        assertTrue("Should be interpolating for movement >5m after smoothing", interpolator.isInterpolating())
    }
    
    @Test
    fun testTurns() {
        val interpolator = PositionInterpolator()
        val bearingUpdates = mutableListOf<Float>()
        
        interpolator.onPositionUpdated = { lat, lon, bearing ->
            if (bearing != null) {
                bearingUpdates.add(bearing)
            }
        }
        
        // Set initial position with bearing 0° (north)
        interpolator.interpolateTo(37.7749, -122.4194, 0.0f)
        Thread.sleep(100)
        
        // Clear initial updates
        bearingUpdates.clear()
        
        // Start interpolation to a new position with bearing 90° (east)
        // This simulates a right turn
        interpolator.interpolateTo(37.7752, -122.4191, 90.0f) // ~50m movement
        Thread.sleep(300) // Wait for some interpolation to happen
        
        // Check that we received bearing updates
        assertTrue("Should have received bearing updates during turn", bearingUpdates.size > 0)
        
        // Verify bearing changed gradually (not snapped)
        if (bearingUpdates.size >= 2) {
            val firstBearing = bearingUpdates.first()
            val lastBearing = bearingUpdates.last()
            
            // Bearing should have changed from near 0° to near 90°
            // But not instantly - should be interpolated
            assertTrue("Bearing should have increased during turn", lastBearing > firstBearing)
            assertTrue("Bearing should be less than or equal to target (90°)", lastBearing <= 91.0f) // Allow small overshoot
            
            // Check for smooth progression (no large jumps)
            var prevBearing = firstBearing
            for (i in 1 until bearingUpdates.size) {
                val currentBearing = bearingUpdates[i]
                val change = kotlin.math.abs(currentBearing - prevBearing)
                // Bearing change between updates should be relatively small (smooth)
                // Max change depends on frame rate and interpolation duration
                // With 60Hz and 500ms duration, max change per frame would be ~90°/30 = 3°
                assertTrue("Bearing change should be smooth (not jumpy), change was $change degrees",
                    change < 10.0f) // Allow up to 10° change between callbacks
                prevBearing = currentBearing
            }
        }
        
        // Wait for interpolation to complete
        Thread.sleep(500)
        
        // Final bearing should be close to target (90°)
        val (_, _, finalBearing) = interpolator.getCurrentPosition()
        assertNotNull("Final bearing should not be null", finalBearing)
        assertEquals("Final bearing should be target bearing", 90.0f, finalBearing!!, 5.0f) // Allow 5° tolerance
    }
    
    @Test
    fun testHighSpeed() {
        val interpolator = PositionInterpolator()
        val positionUpdates = mutableListOf<Triple<Double, Double, Float?>>()
        
        interpolator.onPositionUpdated = { lat, lon, bearing ->
            positionUpdates.add(Triple(lat, lon, bearing))
        }
        
        // Simulate highway speed movement (~100 km/h = 27.8 m/s)
        // Send positions 27.8 meters apart every second
        // First position
        interpolator.interpolateTo(37.7749, -122.4194, 0.0f)
        Thread.sleep(100)
        
        // Second position 27.8m north after 1 second
        interpolator.interpolateTo(37.77515, -122.4194, 0.0f) // ~27.8m north
        Thread.sleep(100)
        
        // Third position another 27.8m north
        interpolator.interpolateTo(37.7754, -122.4194, 0.0f) // ~27.8m north
        Thread.sleep(100)
        
        // Now we should have velocity ~27.8 m/s (100 km/h)
        // Send another position - predictive movement should be applied
        val updatesBefore = positionUpdates.size
        interpolator.interpolateTo(37.77565, -122.4194, 0.0f) // ~27.8m north
        Thread.sleep(150)
        
        // Check that we received position updates
        assertTrue("Should have received position updates at high speed",
            positionUpdates.size > updatesBefore)
        
        // With velocity > 0.5 m/s, predictive movement should be applied
        // We can't directly test if predictive movement was applied (it's internal)
        // But we can verify the system handles high speed without issues
        
        // Check that position is updating (not stuck)
        if (positionUpdates.size >= 2) {
            val lastTwo = positionUpdates.takeLast(2)
            val (lat1, lon1, _) = lastTwo[0]
            val (lat2, lon2, _) = lastTwo[1]
            // At least one coordinate should have changed
            assertTrue("Position should be updating at high speed",
                lat1 != lat2 || lon1 != lon2)
        }
        
        // Verify the interpolator is functioning
        val (finalLat, finalLon, finalBearing) = interpolator.getCurrentPosition()
        // Position should have moved north (latitude increased)
        assertTrue("Latitude should have increased with northward movement",
            finalLat > 37.7749)
        assertEquals("Longitude should remain constant", -122.4194, finalLon, 0.0001)
        assertNotNull("Bearing should not be null", finalBearing)
        
        // Clean up - stop any ongoing interpolation
        interpolator.stopAndJumpToTarget()
    }
}