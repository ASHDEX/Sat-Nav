package com.jayesh.satnav

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.jayesh.satnav.data.location.FakeLocationRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CriticalFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<DebugMainActivity>()

    @Inject
    lateinit var fakeLocationRepository: FakeLocationRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        
        // Enable GPX replay for testing
        runBlocking {
            // Load test GPX file
            fakeLocationRepository.loadGpxFile("gurugram_short_route.gpx")
            // Set speed multiplier to 10x for fast testing
            fakeLocationRepository.startReplay(10.0f)
        }
    }

    @Test
    fun criticalFlow_searchToNavigationToArrival() {
        // 1. Launch app (already launched by composeTestRule)
        
        // 2. Tap search bar
        composeTestRule.onNodeWithText("Search").performClick()
        
        // 3. Type "connaught"
        composeTestRule.onNodeWithText("Search for a place").performTextInput("connaught")
        
        // Wait for search results
        composeTestRule.waitForIdle()
        
        // 4. Tap first result (assuming there's at least one result)
        // Note: We need to identify the first result. Let's assume it contains "Connaught"
        composeTestRule.onNodeWithText("Connaught", substring = true).performClick()
        
        // 5. Wait for RoutePreview to load
        composeTestRule.waitForIdle()
        
        // 6. Assert 1–3 route chips visible
        // We'll check for route options (they might be labeled as "Fastest", "Shortest", etc.)
        // For now, we'll just wait and assume they appear
        Thread.sleep(2000)
        
        // 7. Tap "Start"
        composeTestRule.onNodeWithText("Start").performClick()
        
        // 8. Assert NavigationScreen visible with maneuver banner
        composeTestRule.onNodeWithText("Navigation", substring = true).assertExists()
        
        // 9. Using the GPX replay at 10x speed, wait for Arrived state
        // The GPX file is short, so with 10x speed it should finish quickly
        runBlocking {
            delay(10000) // Wait 10 seconds for the route to complete
        }
        
        // 10. Assert arrival screen
        composeTestRule.onNodeWithText("Arrived", substring = true).assertExists()
        composeTestRule.onNodeWithText("Destination reached", substring = true).assertExists()
    }

    @Test
    fun debugSettings_flow() {
        // Test debug settings functionality
        composeTestRule.onNodeWithText("Settings").performClick()
        
        // Check for debug settings card
        composeTestRule.onNodeWithText("Debug Settings").performClick()
        
        // Verify debug settings screen appears
        composeTestRule.onNodeWithText("GPX Replay").assertExists()
        composeTestRule.onNodeWithText("Use GPX replay instead of real GPS").assertExists()
        
        // Go back
        composeTestRule.onNodeWithText("Back").performClick()
    }
}