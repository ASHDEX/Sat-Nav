package com.jayesh.satnav.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a complete multi-leg trip plan.
 *
 * @property id Unique identifier for this trip plan
 * @property name Optional user-provided name for the trip
 * @property stops All stops in the trip (at least 2)
 * @property legs Route legs between consecutive stops (stops.size - 1)
 * @property totalDistanceMeters Total distance across all legs in meters
 * @property totalDurationMillis Total estimated travel time across all legs in milliseconds
 * @property createdAt When this trip plan was created
 */
@Serializable
data class TripPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String?,
    val stops: List<TripStop>,       // at least 2
    val legs: List<TripLeg>,         // stops.size - 1
    val totalDistanceMeters: Double,
    val totalDurationMillis: Long,
    val createdAt: Instant,
) {
    init {
        require(stops.size >= 2) { "Trip must have at least 2 stops" }
        require(legs.size == stops.size - 1) { "Must have exactly stops.size - 1 legs" }
    }

    /**
     * Get the stop at the given index.
     */
    fun getStop(index: Int): TripStop = stops[index]

    /**
     * Get the leg at the given index.
     */
    fun getLeg(index: Int): TripLeg = legs[index]

    /**
     * Find the leg that starts from the given stop ID.
     */
    fun findLegByFromStopId(stopId: String): TripLeg? =
        legs.find { it.fromStopId == stopId }

    /**
     * Find the leg that ends at the given stop ID.
     */
    fun findLegByToStopId(stopId: String): TripLeg? =
        legs.find { it.toStopId == stopId }

    /**
     * Get the stop that comes after the given stop ID.
     */
    fun getNextStop(stopId: String): TripStop? {
        val index = stops.indexOfFirst { it.id == stopId }
        return if (index in 0 until stops.lastIndex) stops[index + 1] else null
    }

    /**
     * Get the stop that comes before the given stop ID.
     */
    fun getPreviousStop(stopId: String): TripStop? {
        val index = stops.indexOfFirst { it.id == stopId }
        return if (index > 0) stops[index - 1] else null
    }

    /**
     * Check if all stops are resolved (have places or are current location).
     */
    val allStopsResolved: Boolean
        get() = stops.all { it.isResolved }

    /**
     * Generate a default name based on stop names.
     */
    val defaultName: String
        get() = stops.mapNotNull { stop ->
            when {
                stop.isCurrentLocation -> "Your location"
                stop.place != null -> stop.place.name.take(20)
                else -> null
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(" → ") ?: "Unnamed trip"
}