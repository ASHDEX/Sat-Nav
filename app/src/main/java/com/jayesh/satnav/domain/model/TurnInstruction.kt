package com.jayesh.satnav.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a single turn instruction along a route.
 *
 * @property sign Maneuver type (e.g., left, right, roundabout).
 * @property text Human‑readable instruction text.
 * @property streetName Name of the street where the turn occurs (optional).
 * @property distanceMeters Distance from previous instruction to this one.
 * @property durationMillis Estimated travel time for this segment.
 * @property pointIndex Index into the route's point list where this instruction begins.
 */
@Serializable
data class TurnInstruction(
    val sign: ManeuverSign,
    val text: String,
    val streetName: String?,
    val distanceMeters: Double,
    val durationMillis: Long,
    val pointIndex: Int,
) {
    companion object {
        fun fromGraphHopperInstruction(
            instr: com.graphhopper.util.Instruction,
            pointIndex: Int,
        ): TurnInstruction {
            val sign = mapGraphHopperSign(instr.sign)
            val streetName = instr.name.takeIf { it.isNotBlank() }
            val text = buildInstructionText(sign, streetName)

            return TurnInstruction(
                sign = sign,
                text = text,
                streetName = streetName,
                distanceMeters = instr.distance,
                durationMillis = instr.time,
                pointIndex = pointIndex,
            )
        }

        private fun mapGraphHopperSign(sign: Int): ManeuverSign {
            return when (sign) {
                -8 -> ManeuverSign.UTurn
                -7 -> ManeuverSign.SharpLeft
                -6 -> ManeuverSign.Left
                -5 -> ManeuverSign.SlightLeft
                -4 -> ManeuverSign.Continue
                -3 -> ManeuverSign.KeepLeft
                -2 -> ManeuverSign.KeepRight
                -1 -> ManeuverSign.SlightRight
                0 -> ManeuverSign.Right
                1 -> ManeuverSign.SharpRight
                2 -> ManeuverSign.RoundaboutEnter
                3 -> ManeuverSign.RoundaboutExit
                4 -> ManeuverSign.Arrive
                5 -> ManeuverSign.Depart
                else -> ManeuverSign.Unknown
            }
        }

        private fun buildInstructionText(sign: ManeuverSign, streetName: String?): String {
            val base = when (sign) {
                ManeuverSign.Continue -> "Continue"
                ManeuverSign.SlightLeft -> "Slight left"
                ManeuverSign.Left -> "Turn left"
                ManeuverSign.SharpLeft -> "Sharp left"
                ManeuverSign.SlightRight -> "Slight right"
                ManeuverSign.Right -> "Turn right"
                ManeuverSign.SharpRight -> "Sharp right"
                ManeuverSign.UTurn -> "Make a U‑turn"
                ManeuverSign.RoundaboutEnter -> "Enter roundabout"
                ManeuverSign.RoundaboutExit -> "Exit roundabout"
                ManeuverSign.Arrive -> "Arrive at destination"
                ManeuverSign.Depart -> "Depart"
                ManeuverSign.KeepLeft -> "Keep left"
                ManeuverSign.KeepRight -> "Keep right"
                ManeuverSign.Unknown -> "Continue"
            }
            return if (streetName != null) "$base onto $streetName" else base
        }
    }
}