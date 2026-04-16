package com.jayesh.satnav.domain.model

import kotlinx.serialization.Serializable

/**
 * Enumeration of maneuver signs as defined by GraphHopper's instruction signs.
 * Maps GraphHopper sign integers to semantic maneuver types.
 */
@Serializable
enum class ManeuverSign {
    Continue,
    SlightLeft,
    Left,
    SharpLeft,
    SlightRight,
    Right,
    SharpRight,
    UTurn,
    RoundaboutEnter,
    RoundaboutExit,
    Arrive,
    Depart,
    KeepLeft,
    KeepRight,
    Unknown,
}