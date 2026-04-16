package com.jayesh.satnav.domain.model

/**
 * Represents lane information for a road segment.
 * Based on OSM lane tags: lanes, turn:lanes, change:lanes, etc.
 */
data class LaneInfo(
    /** Total number of lanes in this direction */
    val totalLanes: Int,
    /** Number of lanes that continue straight */
    val straightLanes: Int,
    /** Number of lanes for left turns */
    val leftTurnLanes: Int,
    /** Number of lanes for right turns */
    val rightTurnLanes: Int,
    /** Lane change restrictions (none, forward, backward, both) */
    val laneChangeRestriction: LaneChangeRestriction = LaneChangeRestriction.NONE,
    /** Recommended lane for current maneuver (0-indexed from left) */
    val recommendedLane: Int? = null,
    /** Distance to lane change point in meters */
    val distanceToLaneChange: Double = 0.0
)

/**
 * Lane change restriction types based on OSM change:lanes tag.
 */
enum class LaneChangeRestriction {
    /** No lane change restrictions */
    NONE,
    /** Can change to left lane only */
    FORWARD,
    /** Can change to right lane only */
    BACKWARD,
    /** Cannot change lanes at all */
    BOTH,
    /** Only allowed to change to left lane */
    ONLY_LEFT,
    /** Only allowed to change to right lane */
    ONLY_RIGHT
}

/**
 * Speed limit information for a road segment.
 */
data class SpeedLimit(
    /** Speed limit in km/h */
    val limitKmh: Int,
    /** Type of speed limit (fixed, variable, advisory) */
    val type: SpeedLimitType = SpeedLimitType.FIXED,
    /** Road type for adaptive speed limits */
    val roadType: RoadType? = null,
    /** Whether this is a temporary/conditional limit */
    val isTemporary: Boolean = false,
    /** Distance to speed limit change in meters */
    val distanceToChange: Double = 0.0
)

/**
 * Types of speed limits.
 */
enum class SpeedLimitType {
    /** Fixed legal speed limit */
    FIXED,
    /** Advisory/recommended speed */
    ADVISORY,
    /** Variable speed limit (e.g., digital signs) */
    VARIABLE,
    /** Minimum speed limit */
    MINIMUM
}

/**
 * Road types for adaptive speed limit calculation.
 */
enum class RoadType {
    MOTORWAY,
    TRUNK,
    PRIMARY,
    SECONDARY,
    TERTIARY,
    UNCLASSIFIED,
    RESIDENTIAL,
    LIVING_STREET,
    SERVICE,
    PEDESTRIAN,
    TRACK,
    BUS_GUIDEWAY,
    RACEWAY,
    ROAD,
    FERRY
}

/**
 * Advanced maneuver types for complex navigation scenarios.
 */
enum class AdvancedManeuver {
    /** Complex roundabout with multiple exits */
    COMPLEX_ROUNDABOUT,
    /** Highway exit with lane guidance */
    HIGHWAY_EXIT,
    /** Highway entrance with acceleration lane */
    HIGHWAY_ENTRANCE,
    /** U-turn at intersection */
    U_TURN,
    /** Keep left instruction (fork) */
    KEEP_LEFT,
    /** Keep right instruction (fork) */
    KEEP_RIGHT,
    /** Merge onto highway */
    MERGE,
    /** Lane reduction */
    LANE_REDUCTION,
    /** Lane addition */
    LANE_ADDITION,
    /** Complex intersection with multiple turn options */
    COMPLEX_INTERSECTION
}

/**
 * Enhanced navigation instruction with lane guidance and speed limit info.
 */
data class EnhancedNavInstruction(
    /** Base navigation instruction */
    val baseInstruction: NavInstruction,
    /** Lane guidance for this maneuver */
    val laneGuidance: LaneInfo? = null,
    /** Speed limit for this road segment */
    val speedLimit: SpeedLimit? = null,
    /** Advanced maneuver type if applicable */
    val advancedManeuver: AdvancedManeuver? = null,
    /** Distance to maneuver in meters */
    val distanceToManeuver: Double = 0.0,
    /** Whether lane change is recommended */
    val laneChangeRecommended: Boolean = false,
    /** Voice prompt for lane guidance */
    val laneVoicePrompt: String? = null
)