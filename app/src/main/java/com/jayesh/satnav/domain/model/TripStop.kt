package com.jayesh.satnav.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a single stop in a multi-leg trip.
 *
 * @property id Unique identifier for this stop
 * @property place The resolved place (null if user hasn't picked a result yet)
 * @property query What the user typed in the search field
 * @property order Sequential order in the trip (0-based)
 * @property isCurrentLocation Whether this stop represents the user's current location
 */
@Serializable
data class TripStop(
    val id: String = UUID.randomUUID().toString(),
    val place: Place?,
    val query: String,
    val order: Int,
    val isCurrentLocation: Boolean = false,
) {
    /**
     * Check if this stop is resolved (has a place or is current location).
     */
    val isResolved: Boolean
        get() = place != null || isCurrentLocation
}