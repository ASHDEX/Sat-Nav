package com.jayesh.satnav.features.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jayesh.satnav.domain.model.NavigationState
import com.jayesh.satnav.domain.navigation.ManeuverAnnouncer
import com.jayesh.satnav.domain.navigation.NavigationEngine
import com.jayesh.satnav.domain.navigation.NavigationEngineState
import com.jayesh.satnav.features.routing.TripCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class NavigationUiState(
    val maneuverIconRes: Int,
    val primaryInstruction: String,
    val secondaryInstruction: String?,
    val distanceToManeuverText: String,
    val currentStreetName: String?,
    val etaText: String,
    val remainingDistanceText: String,
    val remainingDurationText: String,
    val isRerouting: Boolean,
    val snappedLat: Double,
    val snappedLon: Double,
    val bearing: Float
)

@HiltViewModel
class NavigationViewModelNew @Inject constructor(
    private val engine: NavigationEngine,
    private val announcer: ManeuverAnnouncer,
    private val tripCoordinator: TripCoordinator,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val uiState: StateFlow<NavigationUiState> = engine.state.map { engineState ->
        createUiStateFromEngine(engineState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = createIdleUiState()
    )

    val navigationState: StateFlow<NavigationState> = engine.state.map { engineState ->
        convertEngineStateToPublic(engineState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NavigationState.Idle
    )

    init {
        val routeId = savedStateHandle.get<Int>("routeOptionId") ?: -1
        val route = tripCoordinator.take(routeId)
            ?: error("RouteOption $routeId not found in TripCoordinator")

        viewModelScope.launch {
            engine.start(route)
        }

        announcer.attach(engine.state, viewModelScope)
    }

    fun onEndTrip() {
        engine.stop()
    }

    fun onContinueToNextLeg() {
        viewModelScope.launch {
            tripCoordinator.advanceToNextLeg()
        }
    }

    override fun onCleared() {
        engine.stop()
        announcer.detach()
    }

    private fun createUiStateFromEngine(engineState: NavigationEngineState): NavigationUiState {
        return when (engineState) {
            NavigationEngineState.Idle -> createIdleUiState()
            is NavigationEngineState.Navigating -> {
                val instruction = engineState.route.instructions.getOrNull(engineState.match.currentInstructionIndex)
                val (primary, secondary) = extractInstructionText(instruction?.streetName ?: "Navigate")
                NavigationUiState(
                    android.R.drawable.ic_menu_directions,
                    primary,
                    secondary,
                    formatDistance(engineState.match.distanceToNextManeuverM),
                    instruction?.streetName,
                    formatTime(engineState.etaEpochMs),
                    formatDistance(engineState.route.distanceMeters - engineState.match.distanceAlongRouteM),
                    formatDuration(engineState.remainingDurationMs),
                    false,
                    engineState.match.snappedLat,
                    engineState.match.snappedLon,
                    engineState.match.bearing
                )
            }
            NavigationEngineState.Arrived -> NavigationUiState(
                android.R.drawable.ic_menu_mylocation,
                "Arrived at destination",
                null,
                "",
                null,
                "",
                "0 m",
                "0 min",
                false,
                0.0,
                0.0,
                0f
            )
            is NavigationEngineState.ArrivedAtWaypoint -> {
                val waypointName = engineState.waypointName ?: "Waypoint ${engineState.waypointIndex + 1}"
                NavigationUiState(
                    android.R.drawable.ic_menu_mylocation,
                    "Arrived at $waypointName",
                    "${engineState.waypointIndex + 1} of ${engineState.totalWaypoints}",
                    "",
                    null,
                    "",
                    "0 m",
                    "0 min",
                    false,
                    0.0,
                    0.0,
                    0f
                )
            }
            is NavigationEngineState.Rerouting -> NavigationUiState(
                android.R.drawable.ic_menu_info_details,
                "Rerouting",
                "Finding new route...",
                "",
                null,
                "",
                "",
                "",
                true,
                engineState.lastMatch.snappedLat,
                engineState.lastMatch.snappedLon,
                engineState.lastMatch.bearing
            )
            is NavigationEngineState.Error -> NavigationUiState(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Navigation error",
                engineState.message,
                "",
                null,
                "",
                "",
                "",
                false,
                0.0,
                0.0,
                0f
            )
        }
    }

    private fun createIdleUiState(): NavigationUiState {
        return NavigationUiState(
            android.R.drawable.ic_menu_directions,
            "Ready to navigate",
            null,
            "",
            null,
            "",
            "",
            "",
            false,
            0.0,
            0.0,
            0f
        )
    }

    private fun convertEngineStateToPublic(engineState: NavigationEngineState): NavigationState {
        return when (engineState) {
            NavigationEngineState.Idle -> NavigationState.Idle
            is NavigationEngineState.Navigating -> NavigationState.Rerouting()
            is NavigationEngineState.Rerouting -> NavigationState.Rerouting(
                lastKnownLat = engineState.lastMatch.snappedLat,
                lastKnownLon = engineState.lastMatch.snappedLon
            )
            NavigationEngineState.Arrived -> NavigationState.Arrived
            is NavigationEngineState.ArrivedAtWaypoint -> NavigationState.ArrivedAtWaypoint(
                stoppedAtName = engineState.waypointName ?: "Waypoint",
                nextLegIndex = engineState.waypointIndex,
                nextStopName = "Next Stop",
                autoAdvanceInSeconds = engineState.countdownSeconds
            )
            is NavigationEngineState.Error -> NavigationState.Error(engineState.message)
        }
    }

    private fun extractInstructionText(fullText: String): Pair<String, String?> {
        val ontoIndex = fullText.indexOf("onto ")
        return if (ontoIndex != -1) {
            Pair(fullText.substring(0, ontoIndex).trim(), fullText.substring(ontoIndex).trim())
        } else {
            val onIndex = fullText.indexOf("on ")
            if (onIndex != -1) {
                Pair(fullText.substring(0, onIndex).trim(), fullText.substring(onIndex).trim())
            } else {
                Pair(fullText, null)
            }
        }
    }

    private fun formatDistance(meters: Double): String {
        return when {
            meters >= 1000 -> "%.1f km".format(meters / 1000)
            meters > 0 -> "%.0f m".format(meters)
            else -> ""
        }
    }

    private fun formatTime(epochMs: Long): String {
        val date = Date(epochMs)
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        return format.format(date)
    }

    private fun formatDuration(millis: Long): String {
        val minutes = millis / 60000
        return when {
            minutes >= 60 -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) "$hours h $remainingMinutes min" else "$hours h"
            }
            minutes > 0 -> "$minutes min"
            else -> "<1 min"
        }
    }
}
