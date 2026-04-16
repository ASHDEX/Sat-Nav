package com.jayesh.satnav.features.navigation

import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.domain.model.NavInstruction

/**
 * Extracts and formats voice instructions from NavInstruction objects.
 * Handles special cases like roundabouts, arrivals, and street names.
 */
object VoiceInstructionExtractor {

    /**
     * Generate a voice-friendly instruction string from a NavInstruction.
     * This is used for TTS playback.
     */
    fun extractVoiceInstruction(instruction: NavInstruction): String {
        return when (instruction.sign) {
            NavInstruction.SIGN_FINISH -> {
                "You have arrived at your destination"
            }
            6 -> { // Roundabout
                val exitInfo = extractRoundaboutExit(instruction.streetName)
                "Take the $exitInfo exit at the roundabout"
            }
            else -> {
                val turnPhrase = getTurnPhrase(instruction.sign)
                val streetPhrase = if (instruction.streetName.isNotBlank()) {
                    " onto ${formatStreetName(instruction.streetName)}"
                } else {
                    ""
                }
                "$turnPhrase$streetPhrase"
            }
        }
    }

    /**
     * Generate a concise instruction for display (without distance).
     */
    fun extractDisplayInstruction(instruction: NavInstruction): String {
        return instruction.humanText
    }

    /**
     * Generate instruction with distance prefix for far warnings.
     */
    fun extractInstructionWithDistance(instruction: NavInstruction, distanceMeters: Double): String {
        val distanceText = when {
            distanceMeters >= 1000 -> "${"%.1f".format(distanceMeters / 1000)} kilometers"
            else -> "${distanceMeters.toInt()} meters"
        }
        
        val baseInstruction = when (instruction.sign) {
            NavInstruction.SIGN_FINISH -> "arrive at your destination"
            6 -> { // Roundabout
                val exitInfo = extractRoundaboutExit(instruction.streetName)
                "take the $exitInfo exit at the roundabout"
            }
            else -> {
                val turnPhrase = getTurnPhrase(instruction.sign).lowercase()
                val streetPhrase = if (instruction.streetName.isNotBlank()) {
                    " onto ${formatStreetName(instruction.streetName)}"
                } else {
                    ""
                }
                "$turnPhrase$streetPhrase"
            }
        }
        
        return "In $distanceText, $baseInstruction"
    }

    private fun getTurnPhrase(sign: Int): String {
        return when (sign) {
            -3 -> "Sharp left"
            -2 -> "Turn left"
            -1 -> "Slight left"
            0 -> "Continue"
            1 -> "Slight right"
            2 -> "Turn right"
            3 -> "Sharp right"
            5 -> "Via point" // Usually not spoken
            else -> "Continue"
        }
    }

    private fun extractRoundaboutExit(streetName: String): String {
        // Try to extract exit number from street name
        // Format might be "Roundabout (2nd exit)" or just "2nd exit"
        val regex = """(\d+)(?:st|nd|rd|th)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(streetName)
        
        return if (match != null) {
            val number = match.groupValues[1]
            val ordinal = when (number) {
                "1" -> "first"
                "2" -> "second"
                "3" -> "third"
                "4" -> "fourth"
                "5" -> "fifth"
                "6" -> "sixth"
                "7" -> "seventh"
                "8" -> "eighth"
                "9" -> "ninth"
                else -> "$number-th"
            }
            "$ordinal exit"
        } else {
            "appropriate exit"
        }
    }

    private fun formatStreetName(streetName: String): String {
        // Clean up street names for TTS
        return streetName
            .replace(Regex("""\s+"""), " ") // Collapse multiple spaces
            .replace(Regex("""^[A-Z]{2,}\s""")) { match -> // State abbreviations
                match.value.replace(" ", " ") + " "
            }
            .trim()
    }

    /**
     * Determine if an instruction should be spoken.
     * Some instructions (like "Continue" or "Via point") may not need voice.
     */
    fun shouldSpeakInstruction(instruction: NavInstruction): Boolean {
        return when (instruction.sign) {
            0, 5 -> false // Continue and Via point - usually not spoken
            else -> true
        }
    }

    /**
     * Generate a unique ID for an instruction for duplicate prevention.
     * Based on sign, street name, and distance (rounded to 10m).
     */
    fun generateInstructionId(instruction: NavInstruction, distanceToTurn: Double): String {
        val roundedDistance = (distanceToTurn / 10).toInt() * 10
        return "inst_${instruction.sign}_${instruction.streetName.hashCode()}_${roundedDistance}"
    }
}