package com.jayesh.satnav.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Summary of a trip plan for listing views (saved trips).
 *
 * @property id Unique identifier for this trip plan
 * @property name Optional user-provided name for the trip
 * @property stopNames Names of the stops in order
 * @property totalDistanceMeters Total distance across all legs in meters
 * @property totalDurationMillis Total estimated travel time across all legs in milliseconds
 * @property createdAt When this trip plan was created
 */
@Serializable
data class TripPlanSummary(
    val id: String,
    val name: String?,
    val stopNames: List<String>,
    val totalDistanceMeters: Double,
    val totalDurationMillis: Long,
    val createdAt: Instant,
) {
    /**
     * Create a summary from a full trip plan.
     */
    companion object {
        fun fromTripPlan(plan: TripPlan): TripPlanSummary {
            val stopNames = plan.stops.map { stop ->
                when {
                    stop.isCurrentLocation -> "Your location"
                    stop.place != null -> stop.place.name
                    else -> stop.query.takeIf { it.isNotBlank() } ?: "Unnamed stop"
                }
            }
            return TripPlanSummary(
                id = plan.id,
                name = plan.name,
                stopNames = stopNames,
                totalDistanceMeters = plan.totalDistanceMeters,
                totalDurationMillis = plan.totalDurationMillis,
                createdAt = plan.createdAt,
            )
        }
    }

    /**
     * Format the total distance as a human-readable string.
     */
    val formattedDistance: String
        get() = when {
            totalDistanceMeters >= 1000 -> "%.1f km".format(totalDistanceMeters / 1000)
            else -> "%.0f m".format(totalDistanceMeters)
        }

    /**
     * Format the total duration as a human-readable string.
     */
    val formattedDuration: String
        get() = when {
            totalDurationMillis >= 3600000 -> {
                val hours = totalDurationMillis / 3600000
                val minutes = (totalDurationMillis % 3600000) / 60000
                if (minutes > 0) "${hours}h ${minutes}min" else "${hours}h"
            }
            else -> "${totalDurationMillis / 60000} min"
        }

    /**
     * Get a display name for the trip.
     */
    val displayName: String
        get() = name ?: stopNames.joinToString(" → ")
}