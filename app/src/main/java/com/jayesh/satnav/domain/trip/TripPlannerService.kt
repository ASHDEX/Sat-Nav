package com.jayesh.satnav.domain.trip

import com.jayesh.satnav.core.utils.LatLng
import com.jayesh.satnav.data.search.OfflineGeocoder
import com.jayesh.satnav.domain.model.Place
import com.jayesh.satnav.domain.model.PlaceCategory
import com.jayesh.satnav.domain.model.TripLeg
import com.jayesh.satnav.domain.model.TripPlan
import com.jayesh.satnav.domain.model.TripStop
import com.jayesh.satnav.domain.routing.NoRouteException
import com.jayesh.satnav.domain.routing.RoutePlanner
import com.jayesh.satnav.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exception thrown when a trip cannot be computed.
 */
class TripPlanningException(message: String) : Exception(message)

/**
 * Service for planning multi-leg trips.
 * Uses the existing RoutePlanner (single GraphHopper instance) and OfflineGeocoder.
 */
@Singleton
class TripPlannerService @Inject constructor(
    private val geocoder: OfflineGeocoder,
    private val routePlanner: RoutePlanner,
    private val locationRepo: LocationRepository,
) {

    /**
     * Resolve a place query to a list of candidate places.
     *
     * @param query Search text
     * @param near Optional location to bias results by proximity
     * @return List of matching places, sorted by relevance
     */
    suspend fun resolvePlace(query: String, near: LatLng? = null): List<Place> {
        val bias = near?.let { org.maplibre.android.geometry.LatLng(it.latitude, it.longitude) }
            ?: locationRepo.lastKnown()?.let { org.maplibre.android.geometry.LatLng(it.latitude, it.longitude) }
        return geocoder.search(query, bias, limit = 5)
    }

    /**
     * Compute a complete trip plan for the given stops.
     *
     * @param stops List of trip stops (must have at least 2 stops)
     * @param profile Routing profile (e.g., "car", "bike", "foot")
     * @return Result containing the computed TripPlan or an error
     */
    suspend fun computeTrip(stops: List<TripStop>, profile: String = "car"): Result<TripPlan> =
        withContext(Dispatchers.Default) {
            runCatching {
                require(stops.size >= 2) { "Trip must have at least 2 stops" }

                // Resolve any "Your location" stops
                val resolvedStops = stops.map { stop ->
                    if (stop.isCurrentLocation && stop.place == null) {
                        val loc = locationRepo.lastKnown()
                            ?: throw IllegalStateException("GPS unavailable for origin")
                        stop.copy(place = Place(
                            id = "current_location",
                            name = "Your location",
                            category = PlaceCategory.Other,
                            lat = loc.latitude,
                            lon = loc.longitude,
                            address = null,
                            distanceMeters = null,
                        ))
                    } else stop
                }

                // Ensure all stops are resolved
                val unresolved = resolvedStops.filter { !it.isResolved }
                if (unresolved.isNotEmpty()) {
                    throw TripPlanningException(
                        "Unresolved stops: ${unresolved.joinToString { it.query }}"
                    )
                }

                // Compute legs in parallel
                val legs = coroutineScope {
                    resolvedStops.windowed(2).mapIndexed { idx, (from, to) ->
                        async {
                            val fromPlace = from.place ?: throw IllegalStateException("Stop ${from.order} not resolved")
                            val toPlace = to.place ?: throw IllegalStateException("Stop ${to.order} not resolved")
                            
                            val routes = routePlanner.plan(
                                start = LatLng(fromPlace.lat, fromPlace.lon),
                                destination = LatLng(toPlace.lat, toPlace.lon),
                                profile = profile,
                            ).getOrThrow()
                            
                            if (routes.isEmpty()) {
                                throw NoRouteException("No route: ${from.query} → ${to.query}")
                            }
                            
                            TripLeg(
                                fromStopId = from.id,
                                toStopId = to.id,
                                selectedRoute = routes.first(),
                                alternativeRoutes = routes.drop(1),
                            )
                        }
                    }.map { it.await() }
                }

                // Create the trip plan
                TripPlan(
                    name = null,
                    stops = resolvedStops,
                    legs = legs,
                    totalDistanceMeters = legs.sumOf { it.selectedRoute.distanceMeters },
                    totalDurationMillis = legs.sumOf { it.selectedRoute.durationMillis },
                    createdAt = Clock.System.now(),
                )
            }
        }

    /**
     * Recompute a single leg between two stops.
     * Useful for reorder/stop change scenarios where only one leg needs updating.
     *
     * @param from Starting stop
     * @param to Destination stop
     * @param profile Routing profile
     * @return Result containing the recomputed TripLeg or an error
     */
    suspend fun recomputeLeg(from: TripStop, to: TripStop, profile: String = "car"): Result<TripLeg> =
        withContext(Dispatchers.Default) {
            runCatching {
                val fromPlace = from.place ?: throw IllegalStateException("From stop not resolved")
                val toPlace = to.place ?: throw IllegalStateException("To stop not resolved")
                
                val routes = routePlanner.plan(
                    start = LatLng(fromPlace.lat, fromPlace.lon),
                    destination = LatLng(toPlace.lat, toPlace.lon),
                    profile = profile,
                ).getOrThrow()
                
                if (routes.isEmpty()) {
                    throw NoRouteException("No route: ${from.query} → ${to.query}")
                }
                
                TripLeg(
                    fromStopId = from.id,
                    toStopId = to.id,
                    selectedRoute = routes.first(),
                    alternativeRoutes = routes.drop(1),
                )
            }
        }

    /**
     * Compute which legs need to be recomputed after a stop reorder.
     * When a stop is moved, only the legs involving that stop and its neighbors need recomputation.
     *
     * @param oldStops Stops before reorder
     * @param newStops Stops after reorder
     * @return List of indices of legs that need recomputation
     */
    fun legsToRecomputeAfterReorder(oldStops: List<TripStop>, newStops: List<TripStop>): List<Int> {
        if (oldStops.size != newStops.size) {
            // If count changed, recompute all legs
            return (0 until newStops.size - 1).toList()
        }

        val changedIndices = mutableSetOf<Int>()
        
        // Find which stops moved
        for (i in oldStops.indices) {
            if (oldStops[i].id != newStops[i].id) {
                // This stop moved, mark affected legs
                if (i > 0) changedIndices.add(i - 1) // leg before this stop
                if (i < oldStops.size - 1) changedIndices.add(i) // leg after this stop
            }
        }
        
        return changedIndices.toList().sorted()
    }
}