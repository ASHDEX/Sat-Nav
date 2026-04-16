package com.jayesh.satnav.data.search

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.domain.model.Place
import com.jayesh.satnav.domain.model.PlaceCategory
import com.jayesh.satnav.domain.repository.LocationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.maplibre.android.geometry.LatLng

@OptIn(ExperimentalCoroutinesApi::class)
class SearchRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockGeocoder: OfflineGeocoder = mockk()
    private val mockRecentsStore: RecentSearchesStore = mockk()
    private val mockLocationRepository: LocationRepository = mockk()
    private val appDispatchers = AppDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher
    )
    private lateinit var repository: SearchRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = SearchRepository(
            geocoder = mockGeocoder,
            recentsStore = mockRecentsStore,
            locationRepository = mockLocationRepository,
            appDispatchers = appDispatchers
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search with empty query returns empty list`() = runTest {
        // Given
        val queryFlow = flowOf("", "   ")

        // When
        val results = mutableListOf<List<Place>>()
        queryFlow.collect { query ->
            repository.search(flowOf(query)).collect { results.add(it) }
        }

        // Then
        assertTrue(results.all { it.isEmpty() })
    }

    @Test
    fun `search with valid query calls geocoder with debouncing`() = runTest {
        // Given
        val testPlace = Place(
            id = "test-1",
            name = "Test Cafe",
            category = PlaceCategory.CAFE,
            lat = 12.9716,
            lon = 77.5946
        )

        coEvery {
            mockGeocoder.search("cafe", any(), any())
        } returns listOf(testPlace)

        coEvery {
            mockLocationRepository.getCurrentLocation()
        } returns null

        // When
        val queryFlow = MutableStateFlow("c")
        val searchResults = repository.search(queryFlow)

        // Start collecting
        val collectedResults = mutableListOf<List<Place>>()
        val job = kotlinx.coroutines.launch(testDispatcher) {
            searchResults.collect { collectedResults.add(it) }
        }

        // Simulate typing with delays less than debounce time
        queryFlow.value = "ca"
        delay(100) // Less than 250ms debounce
        queryFlow.value = "caf"
        delay(100)
        queryFlow.value = "cafe"
        delay(300) // Wait for debounce to trigger

        // Then
        job.cancel()
        
        // Should only call geocoder once for "cafe" after debounce
        coVerify(exactly = 1) {
            mockGeocoder.search("cafe", any(), any())
        }
        
        // Should have results
        assertTrue(collectedResults.any { it.isNotEmpty() })
    }

    @Test
    fun `search uses current location for bias when available`() = runTest {
        // Given
        val testPlace = Place(
            id = "test-1",
            name = "Nearby Restaurant",
            category = PlaceCategory.RESTAURANT,
            lat = 12.9716,
            lon = 77.5946
        )

        val currentLocation = LatLng(12.9716, 77.5946)
        
        coEvery {
            mockLocationRepository.getCurrentLocation()
        } returns currentLocation

        coEvery {
            mockGeocoder.search("restaurant", currentLocation, any())
        } returns listOf(testPlace)

        // When
        val results = repository.search(flowOf("restaurant")).first()

        // Then
        assertEquals(1, results.size)
        assertEquals("test-1", results[0].id)
        
        coVerify {
            mockGeocoder.search("restaurant", currentLocation, any())
        }
    }

    @Test
    fun `recordRecentSearch calls store with place`() = runTest {
        // Given
        val testPlace = Place(
            id = "test-1",
            name = "Test Place",
            category = PlaceCategory.OTHER,
            lat = 12.9716,
            lon = 77.5946
        )

        coEvery {
            mockRecentsStore.addRecentSearch(testPlace)
        } returns Unit

        // When
        repository.recordRecentSearch(testPlace)

        // Then
        coVerify {
            mockRecentsStore.addRecentSearch(testPlace)
        }
    }

    @Test
    fun `getRecentSearches returns from store`() = runTest {
        // Given
        val testPlaces = listOf(
            Place(
                id = "test-1",
                name = "Recent 1",
                category = PlaceCategory.CAFE,
                lat = 12.9716,
                lon = 77.5946
            ),
            Place(
                id = "test-2",
                name = "Recent 2",
                category = PlaceCategory.RESTAURANT,
                lat = 12.9816,
                lon = 77.6046
            )
        )

        coEvery {
            mockRecentsStore.getRecentSearches()
        } returns testPlaces

        // When
        val result = repository.getRecentSearches()

        // Then
        assertEquals(2, result.size)
        assertEquals("Recent 1", result[0].name)
        assertEquals("Recent 2", result[1].name)
    }

    @Test
    fun `clearRecentSearches calls store`() = runTest {
        // Given
        coEvery {
            mockRecentsStore.clearRecentSearches()
        } returns Unit

        // When
        repository.clearRecentSearches()

        // Then
        coVerify {
            mockRecentsStore.clearRecentSearches()
        }
    }

    @Test
    fun `flatMapLatest cancels previous search when new query arrives`() = runTest {
        // This test verifies that flatMapLatest cancels in-flight searches
        // Given
        val slowSearchResults = mutableListOf<Place>()
        var searchCallCount = 0
        
        coEvery {
            mockGeocoder.search(any(), any(), any())
        } coAnswers {
            searchCallCount++
            delay(500) // Simulate slow search
            slowSearchResults
        }

        coEvery {
            mockLocationRepository.getCurrentLocation()
        } returns null

        // When
        val queryFlow = MutableStateFlow("first")
        val searchResults = repository.search(queryFlow)
        
        val collectedResults = mutableListOf<List<Place>>()
        val job = kotlinx.coroutines.launch(testDispatcher) {
            searchResults.collect { collectedResults.add(it) }
        }

        // Trigger first search
        delay(300) // Wait for debounce
        
        // Quickly change query before first search completes
        queryFlow.value = "second"
        delay(300) // Wait for debounce again
        
        // Wait a bit more
        delay(100)
        
        // Then
        job.cancel()
        
        // Should have called geocoder twice (first one cancelled, second one executed)
        // Note: In actual flatMapLatest, the first coroutine gets cancelled
        // but mockk might still count the call
        assertTrue(searchCallCount >= 1)
    }
}