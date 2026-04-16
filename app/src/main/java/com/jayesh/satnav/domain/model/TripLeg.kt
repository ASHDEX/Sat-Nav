package com.jayesh.satnav.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a single leg between two consecutive stops in a trip.
 *
 * @property fromStopId ID of the starting stop
 * @property toStopId ID of the destination stop
 * @property selectedRoute The route option selected for this leg
 * @property alternativeRoutes Other route alternatives for this leg
 */
@Serializable
data class TripLeg(
    val fromStopId: String,
    val toStopId: String,
    val selectedRoute: RouteOption,
    val alternativeRoutes: List<RouteOption>,
) {
    /**
     * All available routes for this leg (selected + alternatives).
     */
    val allRoutes: List<RouteOption>
        get() = listOf(selectedRoute) + alternativeRoutes
}