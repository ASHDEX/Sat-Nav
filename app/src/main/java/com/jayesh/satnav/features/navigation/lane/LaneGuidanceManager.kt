package com.jayesh.satnav.features.navigation.lane

import com.jayesh.satnav.domain.model.*
import com.jayesh.satnav.core.utils.NavLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Manages lane guidance and advanced navigation features.
 * Processes OSM lane tags and provides lane recommendations for navigation.
 */
@Singleton
class LaneGuidanceManager @Inject constructor() {

    companion object {
        private const val TAG = "LaneGuidanceManager"
        
        /** Distance threshold for lane change recommendation (meters) */
        private const val LANE_CHANGE_DISTANCE_THRESHOLD = 300.0
        
        /** Minimum distance to show lane guidance (meters) */
        private const val MIN_LANE_GUIDANCE_DISTANCE = 50.0
        
        /** Default speed limits by road type (km/h) */
        private val DEFAULT_SPEED_LIMITS = mapOf(
            RoadType.MOTORWAY to 120,
            RoadType.TRUNK to 100,
            RoadType.PRIMARY to 80,
            RoadType.SECONDARY to 60,
            RoadType.TERTIARY to 50,
            RoadType.UNCLASSIFIED to 50,
            RoadType.RESIDENTIAL to 30,
            RoadType.LIVING_STREET to 20,
            RoadType.SERVICE to 20,
            RoadType.PEDESTRIAN to 10
        )
    }

    /**
     * Enhance navigation instruction with lane guidance and speed limit information.
     */
    fun enhanceInstruction(
        instruction: NavInstruction,
        currentPosition: RouteCoordinate?,
        nextPosition: RouteCoordinate?,
        roadTags: Map<String, String> = emptyMap(),
        distanceToManeuver: Double
    ): EnhancedNavInstruction {
        val laneGuidance = extractLaneGuidance(instruction, roadTags, distanceToManeuver)
        val speedLimit = extractSpeedLimit(roadTags)
        val advancedManeuver = detectAdvancedManeuver(instruction, roadTags, distanceToManeuver)
        val laneChangeRecommended = shouldRecommendLaneChange(laneGuidance, distanceToManeuver)
        val laneVoicePrompt = generateLaneVoicePrompt(laneGuidance, advancedManeuver, laneChangeRecommended)

        return EnhancedNavInstruction(
            baseInstruction = instruction,
            laneGuidance = laneGuidance,
            speedLimit = speedLimit,
            advancedManeuver = advancedManeuver,
            distanceToManeuver = distanceToManeuver,
            laneChangeRecommended = laneChangeRecommended,
            laneVoicePrompt = laneVoicePrompt
        )
    }

    /**
     * Extract lane guidance information from OSM tags.
     */
    private fun extractLaneGuidance(
        instruction: NavInstruction,
        roadTags: Map<String, String>,
        distanceToManeuver: Double
    ): LaneInfo? {
        if (distanceToManeuver < MIN_LANE_GUIDANCE_DISTANCE) {
            return null
        }

        val totalLanes = roadTags["lanes"]?.toIntOrNull() ?: return null
        val turnLanes = roadTags["turn:lanes"]
        val changeLanes = roadTags["change:lanes"]

        val (straightLanes, leftTurnLanes, rightTurnLanes) = parseTurnLanes(turnLanes, instruction.sign)
        val laneChangeRestriction = parseLaneChangeRestriction(changeLanes)
        val recommendedLane = calculateRecommendedLane(instruction.sign, totalLanes, leftTurnLanes, rightTurnLanes)

        return LaneInfo(
            totalLanes = totalLanes,
            straightLanes = straightLanes,
            leftTurnLanes = leftTurnLanes,
            rightTurnLanes = rightTurnLanes,
            laneChangeRestriction = laneChangeRestriction,
            recommendedLane = recommendedLane,
            distanceToLaneChange = distanceToManeuver
        )
    }

    /**
     * Parse turn:lanes tag to count lanes for each direction.
     */
    private fun parseTurnLanes(
        turnLanes: String?,
        turnSign: Int
    ): Triple<Int, Int, Int> {
        if (turnLanes.isNullOrBlank()) {
            return Triple(0, 0, 0)
        }

        val laneDirections = turnLanes.split("|")
        var straightLanes = 0
        var leftTurnLanes = 0
        var rightTurnLanes = 0

        for (direction in laneDirections) {
            when {
                direction.contains("through") || direction.contains("straight") -> straightLanes++
                direction.contains("left") -> leftTurnLanes++
                direction.contains("right") -> rightTurnLanes++
                direction.contains("merge_to_left") -> leftTurnLanes++
                direction.contains("merge_to_right") -> rightTurnLanes++
            }
        }

        return Triple(straightLanes, leftTurnLanes, rightTurnLanes)
    }

    /**
     * Parse change:lanes tag to determine lane change restrictions.
     */
    private fun parseLaneChangeRestriction(changeLanes: String?): LaneChangeRestriction {
        if (changeLanes.isNullOrBlank()) {
            return LaneChangeRestriction.NONE
        }

        val restrictions = changeLanes.split("|")
        var hasForward = false
        var hasBackward = false
        var hasOnlyLeft = false
        var hasOnlyRight = false

        for (restriction in restrictions) {
            when (restriction) {
                "yes", "not_right", "only_left" -> {
                    hasForward = true
                    if (restriction == "only_left") hasOnlyLeft = true
                }
                "not_left", "only_right" -> {
                    hasBackward = true
                    if (restriction == "only_right") hasOnlyRight = true
                }
                "no" -> {
                    hasForward = true
                    hasBackward = true
                }
            }
        }

        return when {
            hasOnlyLeft && hasOnlyRight -> LaneChangeRestriction.BOTH
            hasOnlyLeft -> LaneChangeRestriction.ONLY_LEFT
            hasOnlyRight -> LaneChangeRestriction.ONLY_RIGHT
            hasForward && hasBackward -> LaneChangeRestriction.BOTH
            hasForward -> LaneChangeRestriction.FORWARD
            hasBackward -> LaneChangeRestriction.BACKWARD
            else -> LaneChangeRestriction.NONE
        }
    }

    /**
     * Calculate recommended lane based on turn direction.
     */
    private fun calculateRecommendedLane(
        turnSign: Int,
        totalLanes: Int,
        leftTurnLanes: Int,
        rightTurnLanes: Int
    ): Int? {
        return when {
            turnSign < 0 -> { // Left turn
                if (leftTurnLanes > 0) totalLanes - leftTurnLanes else null
            }
            turnSign > 0 -> { // Right turn
                if (rightTurnLanes > 0) rightTurnLanes - 1 else null
            }
            else -> { // Straight
                if (totalLanes > 0) totalLanes / 2 else null
            }
        }
    }

    /**
     * Extract speed limit from OSM tags or use default based on road type.
     */
    private fun extractSpeedLimit(roadTags: Map<String, String>): SpeedLimit? {
        // Try to get explicit speed limit
        val explicitLimit = roadTags["maxspeed"]?.let { limitStr ->
            when {
                limitStr.endsWith("km/h") -> limitStr.removeSuffix("km/h").trim().toIntOrNull()
                limitStr.endsWith("mph") -> (limitStr.removeSuffix("mph").trim().toDoubleOrNull()?.times(1.60934))?.toInt()
                limitStr.toIntOrNull() != null -> limitStr.toInt()
                else -> null
            }
        }

        if (explicitLimit != null) {
            return SpeedLimit(
                limitKmh = explicitLimit,
                type = SpeedLimitType.FIXED,
                isTemporary = roadTags.containsKey("maxspeed:conditional")
            )
        }

        // Fall back to default based on road type
        val highwayType = roadTags["highway"]
        val roadType = when (highwayType) {
            "motorway", "motorway_link" -> RoadType.MOTORWAY
            "trunk", "trunk_link" -> RoadType.TRUNK
            "primary", "primary_link" -> RoadType.PRIMARY
            "secondary", "secondary_link" -> RoadType.SECONDARY
            "tertiary", "tertiary_link" -> RoadType.TERTIARY
            "unclassified" -> RoadType.UNCLASSIFIED
            "residential" -> RoadType.RESIDENTIAL
            "living_street" -> RoadType.LIVING_STREET
            "service" -> RoadType.SERVICE
            "pedestrian" -> RoadType.PEDESTRIAN
            else -> null
        }

        return roadType?.let {
            SpeedLimit(
                limitKmh = DEFAULT_SPEED_LIMITS[it] ?: 50,
                type = SpeedLimitType.ADVISORY,
                roadType = it
            )
        }
    }

    /**
     * Detect advanced maneuvers based on instruction and road tags.
     */
    private fun detectAdvancedManeuver(
        instruction: NavInstruction,
        roadTags: Map<String, String>,
        distanceToManeuver: Double
    ): AdvancedManeuver? {
        if (distanceToManeuver > 500) return null // Too far to be relevant

        return when {
            instruction.sign == 6 && roadTags.containsKey("junction") && 
            roadTags["junction"] == "roundabout" -> AdvancedManeuver.COMPLEX_ROUNDABOUT
            
            roadTags.containsKey("highway") && roadTags["highway"]?.endsWith("_link") == true -> {
                if (instruction.sign < 0) AdvancedManeuver.HIGHWAY_EXIT
                else AdvancedManeuver.HIGHWAY_ENTRANCE
            }
            
            instruction.sign == -3 || instruction.sign == 3 -> AdvancedManeuver.U_TURN
            
            roadTags.containsKey("fork") -> {
                if (instruction.sign < 0) AdvancedManeuver.KEEP_LEFT
                else AdvancedManeuver.KEEP_RIGHT
            }
            
            roadTags.containsKey("lanes") && roadTags.containsKey("lanes:forward") -> {
                val total = roadTags["lanes"]?.toIntOrNull() ?: 0
                val forward = roadTags["lanes:forward"]?.toIntOrNull() ?: 0
                if (forward < total / 2) AdvancedManeuver.LANE_REDUCTION
                else if (forward > total / 2) AdvancedManeuver.LANE_ADDITION
                else null
            }
            
            else -> null
        }
    }

    /**
     * Determine if lane change should be recommended.
     */
    private fun shouldRecommendLaneChange(
        laneGuidance: LaneInfo?,
        distanceToManeuver: Double
    ): Boolean {
        if (laneGuidance == null) return false
        
        return distanceToManeuver in MIN_LANE_GUIDANCE_DISTANCE..LANE_CHANGE_DISTANCE_THRESHOLD &&
               laneGuidance.recommendedLane != null &&
               laneGuidance.laneChangeRestriction != LaneChangeRestriction.BOTH
    }

    /**
     * Generate voice prompt for lane guidance.
     */
    private fun generateLaneVoicePrompt(
        laneGuidance: LaneInfo?,
        advancedManeuver: AdvancedManeuver?,
        laneChangeRecommended: Boolean
    ): String? {
        if (laneGuidance == null) return null

        return when {
            laneChangeRecommended && laneGuidance.recommendedLane != null -> {
                val laneNumber = laneGuidance.recommendedLane!! + 1 // Convert to 1-indexed for voice
                "Get into lane $laneNumber"
            }
            advancedManeuver == AdvancedManeuver.KEEP_LEFT -> "Keep left at the fork"
            advancedManeuver == AdvancedManeuver.KEEP_RIGHT -> "Keep right at the fork"
            advancedManeuver == AdvancedManeuver.HIGHWAY_EXIT -> "Take the highway exit"
            advancedManeuver == AdvancedManeuver.HIGHWAY_ENTRANCE -> "Merge onto the highway"
            laneGuidance.laneChangeRestriction == LaneChangeRestriction.ONLY_LEFT -> "Change to left lane only"
            laneGuidance.laneChangeRestriction == LaneChangeRestriction.ONLY_RIGHT -> "Change to right lane only"
            else -> null
        }
    }

    /**
     * Check if current speed exceeds speed limit.
     */
    fun checkSpeedLimitViolation(
        currentSpeedKmh: Double,
        speedLimit: SpeedLimit?
    ): SpeedLimitViolation {
        if (speedLimit == null) return SpeedLimitViolation.NONE
        
        val tolerance = when (speedLimit.type) {
            SpeedLimitType.FIXED -> 5.0 // 5 km/h tolerance for fixed limits
            SpeedLimitType.ADVISORY -> 10.0 // 10 km/h tolerance for advisory
            else -> 0.0
        }
        
        val excess = currentSpeedKmh - speedLimit.limitKmh - tolerance
        
        return when {
            excess <= 0 -> SpeedLimitViolation.NONE
            excess <= 10 -> SpeedLimitViolation.WARNING
            excess <= 20 -> SpeedLimitViolation.MODERATE
            else -> SpeedLimitViolation.SEVERE
        }
    }

    /**
     * Get adaptive speed limit based on road conditions.
     */
    fun getAdaptiveSpeedLimit(
        baseLimit: SpeedLimit,
        conditions: RoadConditions
    ): SpeedLimit {
        var adjustedLimit = baseLimit.limitKmh
        
        // Adjust based on conditions
        when {
            conditions.isRaining -> adjustedLimit = (adjustedLimit * 0.8).toInt()
            conditions.isNight -> adjustedLimit = (adjustedLimit * 0.9).toInt()
            conditions.roadSurface == RoadSurface.WET -> adjustedLimit = (adjustedLimit * 0.85).toInt()
            conditions.roadSurface == RoadSurface.ICY -> adjustedLimit = (adjustedLimit * 0.6).toInt()
        }
        
        return baseLimit.copy(
            limitKmh = adjustedLimit.coerceAtLeast(20),
            type = SpeedLimitType.ADVISORY
        )
    }
}

/**
 * Road conditions for adaptive speed limits.
 */
data class RoadConditions(
    val isRaining: Boolean = false,
    val isNight: Boolean = false,
    val roadSurface: RoadSurface = RoadSurface.DRY,
    val visibilityMeters: Int = 1000
)

/**
 * Road surface conditions.
 */
enum class RoadSurface {
    DRY,
    WET,
    SNOW,
    ICE,
    ICY
}

/**
 * Speed limit violation levels.
 */
enum class SpeedLimitViolation {
    NONE,
    WARNING,
    MODERATE,
    SEVERE
}