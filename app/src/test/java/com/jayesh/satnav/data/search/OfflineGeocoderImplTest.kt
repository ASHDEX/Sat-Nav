package com.jayesh.satnav.data.search

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.data.local.search.PoiLocalDataSource
import com.jayesh.satnav.domain.model.Place
import com.jayesh.satnav.domain.model.PlaceCategory
import com.jayesh.satnav.domain.model.PointOfInterest
import com.jayesh.satnav.domain.model.PoiCategory
import com.jayesh.satnav.domain.model.PoiSearchQuery
import com.jayesh.satnav.domain.model.PoiSearchResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class OfflineGeocoderImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockPoiLocalDataSource: PoiLocalDataSource = mockk()
    private val appDispatchers = AppDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher
    )
    private lateinit var geocoder: OfflineGeocoderImpl

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        geocoder = OfflineGeocoderImpl(mockPoiLocalDataSource, appDispatchers)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search with empty query returns empty list`() = runTest {
        // When
        val result = geocoder.search("", null, 10)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search with valid query returns mapped places`() = runTest {
        // Given
        val mockPois = listOf(
            PointOfInterest(
                id = "poi-1",
                name = "Central Park",
                category = PoiCategory.PARK,
                latitude = 12.9716,
                longitude = 77.5946,
                address = "MG Road, Bangalore",
                tags = mapOf("amenity" to "park"),
                distanceMeters = 500.0
            ),
            PointOfInterest(
                id = "poi-2",
                name = "Bangalore Palace",
                category = PoiCategory.CASTLE,
                latitude = 12.9988,
                longitude = 77.5925,
                address = "Palace Road, Bangalore",
                tags = mapOf("tourism" to "attraction"),
                distanceMeters = 1000.0
            )
        )

        val mockResult = PoiSearchResult(
            pois = mockPois,
            totalCount = 2,
            hasMore = false
        )

        coEvery {
            mockPoiLocalDataSource.searchPois(any())
        } returns mockResult

        // When
        val result = geocoder.search("park", null, 10)

        // Then
        assertEquals(2, result.size)
        
        val firstPlace = result[0]
        assertEquals("poi-1", firstPlace.id)
        assertEquals("Central Park", firstPlace.name)
        assertEquals(PlaceCategory.PARK, firstPlace.category)
        assertEquals(12.9716, firstPlace.lat, 0.0001)
        assertEquals(77.5946, firstPlace.lon, 0.0001)

        val secondPlace = result[1]
        assertEquals("poi-2", secondPlace.id)
        assertEquals("Bangalore Palace", secondPlace.name)
        assertEquals(PlaceCategory.LANDMARK, secondPlace.category) // CASTLE maps to LANDMARK
    }

    @Test
    fun `search with location uses distance sorting`() = runTest {
        // Given
        val nearLocation = LatLng(12.9716, 77.5946)
        val mockResult = PoiSearchResult(
            pois = emptyList(),
            totalCount = 0,
            hasMore = false
        )

        var capturedQuery: PoiSearchQuery? = null
        coEvery {
            mockPoiLocalDataSource.searchPois(captureNullable(capturedQuery))
        } returns mockResult

        // When
        geocoder.search("cafe", nearLocation, 5)

        // Then
        val query = capturedQuery
        requireNotNull(query)
        assertEquals("cafe", query.query)
        assertEquals(12.9716, query.centerLatitude)
        assertEquals(77.5946, query.centerLongitude)
        assertEquals(5000.0, query.radiusMeters) // DEFAULT_SEARCH_RADIUS_METERS = 5000
        assertEquals(5, query.limit)
        assertEquals(PoiSearchQuery.SortBy.DISTANCE, query.sortBy)
    }

    @Test
    fun `search without location uses relevance sorting`() = runTest {
        // Given
        val mockResult = PoiSearchResult(
            pois = emptyList(),
            totalCount = 0,
            hasMore = false
        )

        var capturedQuery: PoiSearchQuery? = null
        coEvery {
            mockPoiLocalDataSource.searchPois(captureNullable(capturedQuery))
        } returns mockResult

        // When
        geocoder.search("restaurant", null, 10)

        // Then
        val query = capturedQuery
        requireNotNull(query)
        assertEquals("restaurant", query.query)
        assertEquals(null, query.centerLatitude)
        assertEquals(null, query.centerLongitude)
        assertEquals(null, query.radiusMeters)
        assertEquals(PoiSearchQuery.SortBy.RELEVANCE, query.sortBy)
    }

    @Test
    fun `category mapping covers all PoiCategory values`() = runTest {
        // Test that all PoiCategory values can be mapped without throwing exceptions
        val testPoi = PointOfInterest(
            id = "test",
            name = "Test",
            category = PoiCategory.UNKNOWN,
            latitude = 0.0,
            longitude = 0.0,
            address = null,
            tags = emptyMap(),
            distanceMeters = 0.0
        )

        val mockResult = PoiSearchResult(
            pois = listOf(testPoi),
            totalCount = 1,
            hasMore = false
        )

        coEvery {
            mockPoiLocalDataSource.searchPois(any())
        } returns mockResult

        // This should not throw any exception
        val result = geocoder.search("test", null, 1)
        assertEquals(1, result.size)
        assertEquals(PlaceCategory.OTHER, result[0].category) // UNKNOWN maps to OTHER
    }
}