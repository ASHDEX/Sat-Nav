package com.jayesh.satnav.features.navigation

import com.jayesh.satnav.core.utils.NavLog
import com.jayesh.satnav.domain.model.NavInstruction

/**
 * Determines WHEN to speak navigation instructions based on distance thresholds.
 * Implements multi-level timing logic:
 * - Far warning (~200-300m)
 * - Near warning (~50m)
 * - Immediate (at turn)
 */
class VoiceTimingEngine {

    companion object {
        // Distance thresholds in meters
        private const val FAR_WARNING_DISTANCE = 300.0
        private const val NEAR_WARNING_DISTANCE = 50.0
        private const val IMMEDIATE_DISTANCE = 10.0
        
        // Minimum distance between same instruction repeats
        private const val MIN_DISTANCE_BETWEEN_REPEATS = 100.0
        
        // Special handling for different instruction types
        private const val ROUNDABOUT_FAR_WARNING = 200.0
        private const val ARRIVAL_WARNING_DISTANCE = 100.0
    }

    data class TimingDecision(
        val shouldSpeak: Boolean,
        val speechType: SpeechType,
        val distanceToTurn: Double,
        val instructionId: String,
    )

    enum class SpeechType {
        FAR_WARNING,    // "In 200 meters, turn right"
        NEAR_WARNING,   // "Turn right"
        IMMEDIATE,      // "Now, turn right"
        ARRIVAL,        // "You have arrived"
        REROUTING,      // "Rerouting"
        OFF_ROUTE,      // "You are off route"
    }

    // Track last spoken instructions to prevent repeats
    private val lastSpokenInstructions = mutableMapOf<String, SpokenRecord>()

    data class SpokenRecord(
        val instructionId: String,
        val distanceWhenSpoken: Double,
        val timestamp: Long = System.currentTimeMillis(),
    )

    /**
     * Main decision function: determines if/when to speak based on current distance to turn.
     */
    fun decide(
        instruction: NavInstruction,
        distanceToTurn: Double,
        previousDistance: Double? = null,
    ): TimingDecision {
        val instructionId = VoiceInstructionExtractor.generateInstructionId(instruction, distanceToTurn)
        
        // Check if we should skip this instruction entirely
        if (!VoiceInstructionExtractor.shouldSpeakInstruction(instruction)) {
            return TimingDecision(false, SpeechType.FAR_WARNING, distanceToTurn, instructionId)
        }

        // Special handling for arrival
        if (instruction.sign == NavInstruction.SIGN_FINISH) {
            return handleArrival(distanceToTurn, instructionId)
        }

        // Special handling for roundabouts
        if (instruction.sign == 6) {
            return handleRoundabout(distanceToTurn, instructionId, instruction)
        }

        // Check for repeat prevention
        if (shouldSkipDueToRepeat(instructionId, distanceToTurn)) {
            NavLog.voice("Skipping repeat instruction: $instructionId at ${distanceToTurn}m")
            return TimingDecision(false, SpeechType.FAR_WARNING, distanceToTurn, instructionId)
        }

        // Determine speech type based on distance
        val speechType = when {
            distanceToTurn > FAR_WARNING_DISTANCE -> {
                // Only speak far warnings for significant distances
                if (distanceToTurn > 500 && previousDistance != null && previousDistance <= FAR_WARNING_DISTANCE) {
                    SpeechType.FAR_WARNING
                } else {
                    null // Don't speak yet
                }
            }
            distanceToTurn > NEAR_WARNING_DISTANCE -> SpeechType.FAR_WARNING
            distanceToTurn > IMMEDIATE_DISTANCE -> SpeechType.NEAR_WARNING
            else -> SpeechType.IMMEDIATE
        }

        return if (speechType != null) {
            // Record that we're speaking this instruction
            lastSpokenInstructions[instructionId] = SpokenRecord(instructionId, distanceToTurn)
            TimingDecision(true, speechType, distanceToTurn, instructionId)
        } else {
            TimingDecision(false, SpeechType.FAR_WARNING, distanceToTurn, instructionId)
        }
    }

    private fun handleArrival(distanceToTurn: Double, instructionId: String): TimingDecision {
        return when {
            distanceToTurn <= ARRIVAL_WARNING_DISTANCE && distanceToTurn > IMMEDIATE_DISTANCE -> {
                TimingDecision(true, SpeechType.ARRIVAL, distanceToTurn, instructionId)
            }
            distanceToTurn <= IMMEDIATE_DISTANCE -> {
                TimingDecision(true, SpeechType.ARRIVAL, distanceToTurn, instructionId)
            }
            else -> {
                TimingDecision(false, SpeechType.ARRIVAL, distanceToTurn, instructionId)
            }
        }
    }

    private fun handleRoundabout(
        distanceToTurn: Double,
        instructionId: String,
        instruction: NavInstruction,
    ): TimingDecision {
        // Roundabouts need earlier warnings
        val speechType = when {
            distanceToTurn > ROUNDABOUT_FAR_WARNING -> {
                if (distanceToTurn > 300) null else SpeechType.FAR_WARNING
            }
            distanceToTurn > NEAR_WARNING_DISTANCE -> SpeechType.FAR_WARNING
            distanceToTurn > IMMEDIATE_DISTANCE -> SpeechType.NEAR_WARNING
            else -> SpeechType.IMMEDIATE
        }

        return if (speechType != null && !shouldSkipDueToRepeat(instructionId, distanceToTurn)) {
            lastSpokenInstructions[instructionId] = SpokenRecord(instructionId, distanceToTurn)
            TimingDecision(true, speechType, distanceToTurn, instructionId)
        } else {
            TimingDecision(false, SpeechType.FAR_WARNING, distanceToTurn, instructionId)
        }
    }

    private fun shouldSkipDueToRepeat(instructionId: String, currentDistance: Double): Boolean {
        val lastRecord = lastSpokenInstructions[instructionId] ?: return false
        
        // Don't repeat if we're still far from the turn
        if (currentDistance > lastRecord.distanceWhenSpoken) {
            return true
        }
        
        // Don't repeat if we haven't moved much since last speech
        val distanceMoved = lastRecord.distanceWhenSpoken - currentDistance
        if (distanceMoved < MIN_DISTANCE_BETWEEN_REPEATS) {
            return true
        }
        
        return false
    }

    fun decideRerouting(): TimingDecision {
        return TimingDecision(
            shouldSpeak = true,
            speechType = SpeechType.REROUTING,
            distanceToTurn = 0.0,
            instructionId = "rerouting_${System.currentTimeMillis()}"
        )
    }

    fun decideOffRoute(): TimingDecision {
        return TimingDecision(
            shouldSpeak = true,
            speechType = SpeechType.OFF_ROUTE,
            distanceToTurn = 0.0,
            instructionId = "off_route_${System.currentTimeMillis()}"
        )
    }

    fun clearHistory() {
        lastSpokenInstructions.clear()
        NavLog.voice("Cleared voice timing history")
    }

    fun clearInstruction(instructionId: String) {
        lastSpokenInstructions.remove(instructionId)
    }
}