package com.jayesh.satnav.data.search

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.data.local.search.PoiLocalDataSource
import com.jayesh.satnav.domain.model.Place
import com.jayesh.satnav.domain.model.PlaceCategory
import com.jayesh.satnav.domain.model.PoiCategory
import com.jayesh.satnav.domain.model.PoiSearchQuery
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of OfflineGeocoder that wraps the existing POI search index.
 */
@Singleton
class OfflineGeocoderImpl @Inject constructor(
    private val poiLocalDataSource: PoiLocalDataSource,
    private val appDispatchers: AppDispatchers,
) : OfflineGeocoder {

    override suspend fun search(query: String, near: LatLng?, limit: Int): List<Place> =
        withContext(appDispatchers.io) {
            // Empty query should return empty list without hitting the index
            if (query.isBlank()) {
                return@withContext emptyList()
            }

            // Build search query for POI database
            val poiQuery = PoiSearchQuery(
                query = query,
                category = null, // We'll filter categories later if needed
                centerLatitude = near?.latitude,
                centerLongitude = near?.longitude,
                radiusMeters = if (near != null) DEFAULT_SEARCH_RADIUS_METERS else null,
                limit = limit,
                offset = 0,
                sortBy = if (near != null) PoiSearchQuery.SortBy.DISTANCE else PoiSearchQuery.SortBy.RELEVANCE
            )

            // Execute search
            val result = poiLocalDataSource.searchPois(poiQuery)
            result.pois.map { poi ->
                Place(
                    id = poi.id,
                    name = poi.name,
                    category = mapPoiCategoryToPlaceCategory(poi.category),
                    lat = poi.latitude,
                    lon = poi.longitude,
                    address = poi.address,
                    distanceMeters = if (near != null) {
                        poi.distanceTo(near.latitude, near.longitude)
                    } else null
                )
            }
        }

    private fun mapPoiCategoryToPlaceCategory(poiCategory: PoiCategory): PlaceCategory {
        return when (poiCategory) {
            // Food & Drink
            PoiCategory.RESTAURANT,
            PoiCategory.CAFE,
            PoiCategory.FAST_FOOD,
            PoiCategory.BAR,
            PoiCategory.PUB -> PlaceCategory.Food

            // Transportation
            PoiCategory.FUEL_STATION,
            PoiCategory.PARKING,
            PoiCategory.BUS_STOP,
            PoiCategory.TRAIN_STATION,
            PoiCategory.AIRPORT -> PlaceCategory.Transport

            // Accommodation
            PoiCategory.HOTEL,
            PoiCategory.MOTEL,
            PoiCategory.HOSTEL,
            PoiCategory.GUEST_HOUSE -> PlaceCategory.Lodging

            // Shopping
            PoiCategory.SUPERMARKET,
            PoiCategory.CONVENIENCE_STORE,
            PoiCategory.PHARMACY,
            PoiCategory.BAKERY -> PlaceCategory.Shop

            // Health & Emergency
            PoiCategory.HOSPITAL,
            PoiCategory.CLINIC,
            PoiCategory.PHARMACY_EMERGENCY,
            PoiCategory.POLICE_STATION,
            PoiCategory.FIRE_STATION -> PlaceCategory.POI

            // Leisure & Entertainment
            PoiCategory.CINEMA,
            PoiCategory.THEATRE,
            PoiCategory.MUSEUM,
            PoiCategory.LIBRARY,
            PoiCategory.PARK -> PlaceCategory.POI

            // Religious
            PoiCategory.CHURCH,
            PoiCategory.MOSQUE,
            PoiCategory.TEMPLE,
            PoiCategory.SYNAGOGUE -> PlaceCategory.POI

            // Other
            PoiCategory.BANK,
            PoiCategory.ATM,
            PoiCategory.POST_OFFICE,
            PoiCategory.TOILET -> PlaceCategory.Other

            // Custom categories
            PoiCategory.FAVORITE,
            PoiCategory.RECENT,
            PoiCategory.HOME,
            PoiCategory.WORK -> PlaceCategory.Other

            PoiCategory.OTHER -> PlaceCategory.Other
        }
    }

    companion object {
        private const val DEFAULT_SEARCH_RADIUS_METERS = 5000.0 // 5km
    }
}