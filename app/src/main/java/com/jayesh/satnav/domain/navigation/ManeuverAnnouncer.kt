package com.jayesh.satnav.domain.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subscribes to NavigationEngine.state and triggers voice announcements
 * at specific distance thresholds before maneuvers.
 */
@Singleton
class ManeuverAnnouncer @Inject constructor(
    private val voiceGuide: VoiceGuide
) {
    private var announcementJob: Job? = null
    private val dedupeSet = mutableSetOf<AnnouncementKey>()
    private var currentInstructionIndex = -1
    
    /**
     * Attach to a NavigationEngine state flow and start listening for announcements.
     * @param stateFlow The navigation state flow to observe
     * @param scope Coroutine scope to launch the collector in
     */
    fun attach(stateFlow: Flow<NavigationEngineState>, scope: CoroutineScope) {
        announcementJob?.cancel()
        announcementJob = scope.launch(Dispatchers.Main) {
            stateFlow.collectLatest { state ->
                when (state) {
                    is NavigationEngineState.Navigating -> {
                        processNavigatingState(state)
                    }
                    NavigationEngineState.Arrived -> {
                        voiceGuide.speak("You have arrived at your destination", Priority.High)
                        resetDedupe()
                    }
                    is NavigationEngineState.ArrivedAtWaypoint -> {
                        voiceGuide.speak("Arrived at ${state.waypointName}", Priority.High)
                        resetDedupe()
                    }
                    NavigationEngineState.Idle -> {
                        // Idle - reset
                        resetDedupe()
                    }
                    is NavigationEngineState.Rerouting -> {
                        voiceGuide.speak("Rerouting", Priority.Normal)
                        resetDedupe()
                    }
                    is NavigationEngineState.Error -> {
                        voiceGuide.speak("Navigation error: ${state.message}", Priority.High)
                        resetDedupe()
                    }
                }
            }
        }
    }
    
    /**
     * Detach from the current state flow.
     */
    fun detach() {
        announcementJob?.cancel()
        announcementJob = null
        resetDedupe()
    }
    
    private fun processNavigatingState(state: NavigationEngineState.Navigating) {
        val route = state.route
        val match = state.match

        // Reset dedupe set when instruction index advances
        if (match.currentInstructionIndex != currentInstructionIndex) {
            currentInstructionIndex = match.currentInstructionIndex
            dedupeSet.clear()
        }

        // Get current instruction
        val currentInstruction = route.instructions.getOrNull(match.currentInstructionIndex) ?: return
        val distanceToManeuver = match.distanceToNextManeuverM
        
        // 500m threshold
        if (distanceToManeuver <= 500.0 && distanceToManeuver > 200.0) {
            val key = AnnouncementKey(match.currentInstructionIndex, 500)
            if (key !in dedupeSet) {
                val announcement = buildAnnouncement(currentInstruction, "500 meters")
                voiceGuide.speak(announcement, Priority.Normal)
                dedupeSet.add(key)
            }
        }
        
        // 200m threshold
        if (distanceToManeuver <= 200.0 && distanceToManeuver > 50.0) {
            val key = AnnouncementKey(match.currentInstructionIndex, 200)
            if (key !in dedupeSet) {
                val announcement = buildAnnouncement(currentInstruction, "200 meters")
                voiceGuide.speak(announcement, Priority.Normal)
                dedupeSet.add(key)
            }
        }
        
        // 50m threshold
        if (distanceToManeuver <= 50.0 && distanceToManeuver > 0.0) {
            val key = AnnouncementKey(match.currentInstructionIndex, 50)
            if (key !in dedupeSet) {
                val announcement = buildImmediateAnnouncement(currentInstruction)
                voiceGuide.speak(announcement, Priority.High)
                dedupeSet.add(key)
            }
        }
        
        // Immediate maneuver (within 10m)
        if (distanceToManeuver <= 10.0) {
            // Already announced at 50m, but ensure we don't miss it
            val key = AnnouncementKey(match.currentInstructionIndex, 10)
            if (key !in dedupeSet) {
                val announcement = buildImmediateAnnouncement(currentInstruction)
                voiceGuide.speak(announcement, Priority.High)
                dedupeSet.add(key)
            }
        }
    }
    
    private fun buildAnnouncement(instruction: com.jayesh.satnav.domain.model.TurnInstruction, distance: String): String {
        val direction = when (instruction.sign) {
            com.jayesh.satnav.domain.model.ManeuverSign.Left -> "turn left"
            com.jayesh.satnav.domain.model.ManeuverSign.Right -> "turn right"
            com.jayesh.satnav.domain.model.ManeuverSign.SharpLeft -> "turn sharp left"
            com.jayesh.satnav.domain.model.ManeuverSign.SharpRight -> "turn sharp right"
            com.jayesh.satnav.domain.model.ManeuverSign.SlightLeft -> "turn slightly left"
            com.jayesh.satnav.domain.model.ManeuverSign.SlightRight -> "turn slightly right"
            com.jayesh.satnav.domain.model.ManeuverSign.Continue -> "continue straight"
            com.jayesh.satnav.domain.model.ManeuverSign.UTurn -> "make a U-turn"
            com.jayesh.satnav.domain.model.ManeuverSign.Arrive -> "arrive at destination"
            else -> "turn"
        }

        // Extract street name from instruction text if available
        val streetName = extractStreetName(instruction.text)
        return if (streetName.isNotEmpty()) {
            "In $distance, $direction onto $streetName"
        } else {
            "In $distance, $direction"
        }
    }

    private fun buildImmediateAnnouncement(instruction: com.jayesh.satnav.domain.model.TurnInstruction): String {
        val direction = when (instruction.sign) {
            com.jayesh.satnav.domain.model.ManeuverSign.Left -> "Turn left now"
            com.jayesh.satnav.domain.model.ManeuverSign.Right -> "Turn right now"
            com.jayesh.satnav.domain.model.ManeuverSign.SharpLeft -> "Turn sharp left now"
            com.jayesh.satnav.domain.model.ManeuverSign.SharpRight -> "Turn sharp right now"
            com.jayesh.satnav.domain.model.ManeuverSign.SlightLeft -> "Turn slightly left now"
            com.jayesh.satnav.domain.model.ManeuverSign.SlightRight -> "Turn slightly right now"
            com.jayesh.satnav.domain.model.ManeuverSign.Continue -> "Continue straight"
            com.jayesh.satnav.domain.model.ManeuverSign.UTurn -> "Make a U-turn now"
            com.jayesh.satnav.domain.model.ManeuverSign.Arrive -> "Arrive at destination"
            else -> "Turn now"
        }
        
        val streetName = extractStreetName(instruction.text)
        return if (streetName.isNotEmpty()) {
            "$direction onto $streetName"
        } else {
            direction
        }
    }
    
    private fun extractStreetName(instructionText: String): String {
        // Simple extraction: look for "onto" or "on" patterns
        val ontoIndex = instructionText.indexOf("onto ")
        if (ontoIndex != -1) {
            return instructionText.substring(ontoIndex + 5).trim()
        }
        
        val onIndex = instructionText.indexOf("on ")
        if (onIndex != -1) {
            return instructionText.substring(onIndex + 3).trim()
        }
        
        return ""
    }
    
    private fun resetDedupe() {
        dedupeSet.clear()
        currentInstructionIndex = -1
    }
    
    private data class AnnouncementKey(
        val instructionIndex: Int,
        val thresholdMeters: Int
    )
}