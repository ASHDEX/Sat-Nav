package com.jayesh.satnav.data.local.persistence

import androidx.lifecycle.SavedStateHandle
import com.jayesh.satnav.domain.model.NavInstruction
import com.jayesh.satnav.domain.model.NavigationState
import com.jayesh.satnav.domain.model.OfflineRoute
import com.jayesh.satnav.domain.model.RouteCoordinate
import com.jayesh.satnav.domain.model.RoutingProfile
import com.jayesh.satnav.domain.model.Waypoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Lightweight persistence for navigation state to survive process death.
 * Uses SavedStateHandle with JSON serialization for route and waypoints.
 *
 * We persist:
 * 1. Current route (if any)
 * 2. Current navigation state (Idle/Active/Arrived)
 * 3. Waypoints used to compute the route
 * 4. Snapped position if active
 *
 * We do NOT persist:
 * - Large graph data
 * - Full route geometry (only key points)
 * - GPS history
 */
@Serializable
data class PersistedInstruction(
    val sign: Int,
    val streetName: String,
    val distanceMeters: Double,
    val durationMillis: Long,
)

@Serializable
data class PersistedRoute(
    val profile: String,
    val points: List<PersistedCoordinate>,
    val distanceMeters: Double,
    val durationMillis: Long,
    val computationTimeMillis: Long,
    val segmentCount: Int,
    val waypointCount: Int,
    val instructions: List<PersistedInstruction> = emptyList(),
)

@Serializable
data class PersistedCoordinate(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class PersistedWaypoint(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class PersistedNavigationState(
    val route: PersistedRoute? = null,
    val waypoints: List<PersistedWaypoint> = emptyList(),
    val navigationStateType: String = "Idle", // "Idle", "Active", "Arrived"
    val snappedLatitude: Double = 0.0,
    val snappedLongitude: Double = 0.0,
    val distanceTravelledMeters: Double = 0.0,
    val currentStepIndex: Int = 0,
    val isOffRoute: Boolean = false,
)

class NavigationStatePersistence(
    private val savedStateHandle: SavedStateHandle,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_NAVIGATION_STATE = "navigation_state"
        private const val KEY_ROUTE = "persisted_route"
        private const val KEY_WAYPOINTS = "persisted_waypoints"
    }

    fun saveRoute(route: OfflineRoute?) {
        if (route == null) {
            savedStateHandle[KEY_ROUTE] = null
            return
        }

        val persisted = PersistedRoute(
            profile = route.profile.profileName,
            points = route.points.map { PersistedCoordinate(it.latitude, it.longitude) },
            distanceMeters = route.distanceMeters,
            durationMillis = route.durationMillis,
            computationTimeMillis = route.computationTimeMillis,
            segmentCount = route.segmentCount,
            waypointCount = route.waypointCount,
            instructions = route.instructions.map {
                PersistedInstruction(it.sign, it.streetName, it.distanceMeters, it.durationMillis)
            },
        )
        savedStateHandle[KEY_ROUTE] = json.encodeToString(persisted)
    }

    fun loadRoute(): OfflineRoute? {
        val jsonString: String? = savedStateHandle[KEY_ROUTE]
        if (jsonString.isNullOrEmpty()) return null

        return try {
            val persisted = json.decodeFromString<PersistedRoute>(jsonString)
            OfflineRoute(
                profile = RoutingProfile.entries.find { it.profileName == persisted.profile }
                    ?: RoutingProfile.CAR,
                points = persisted.points.map { RouteCoordinate(it.lat, it.lon) },
                distanceMeters = persisted.distanceMeters,
                durationMillis = persisted.durationMillis,
                computationTimeMillis = persisted.computationTimeMillis,
                segmentCount = persisted.segmentCount,
                waypointCount = persisted.waypointCount,
                instructions = persisted.instructions.map {
                    NavInstruction(it.sign, it.streetName, it.distanceMeters, it.durationMillis)
                },
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveWaypoints(waypoints: List<Waypoint>) {
        val persisted = waypoints.map { PersistedWaypoint(it.lat, it.lon) }
        savedStateHandle[KEY_WAYPOINTS] = json.encodeToString(persisted)
    }

    fun loadWaypoints(): List<Waypoint> {
        val jsonString: String? = savedStateHandle[KEY_WAYPOINTS]
        if (jsonString.isNullOrEmpty()) return emptyList()

        return try {
            val persisted = json.decodeFromString<List<PersistedWaypoint>>(jsonString)
            persisted.map { Waypoint(it.lat, it.lon) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveNavigationState(state: NavigationState, route: OfflineRoute?) {
        val persisted = when (state) {
            is NavigationState.Idle -> PersistedNavigationState(
                route = null, // Route stored separately
                navigationStateType = "Idle",
            )
            is NavigationState.Active -> PersistedNavigationState(
                route = null, // Route stored separately
                navigationStateType = "Active",
                snappedLatitude = state.snappedLatitude,
                snappedLongitude = state.snappedLongitude,
                distanceTravelledMeters = state.distanceTravelledMeters,
                currentStepIndex = state.currentStepIndex,
                isOffRoute = state.isOffRoute,
            )
            is NavigationState.ArrivedAtWaypoint -> PersistedNavigationState(
                route = null, // Route stored separately
                navigationStateType = "ArrivedAtWaypoint",
            )
            is NavigationState.Arrived -> PersistedNavigationState(
                route = null, // Route stored separately
                navigationStateType = "Arrived",
            )
            is NavigationState.Rerouting -> PersistedNavigationState(
                route = null, // Route stored separately
                navigationStateType = "Rerouting",
                snappedLatitude = state.lastKnownLat,
                snappedLongitude = state.lastKnownLon,
            )
            is NavigationState.Error -> PersistedNavigationState(
                route = null, // Route stored separately
                navigationStateType = "Error",
            )
        }
        savedStateHandle[KEY_NAVIGATION_STATE] = json.encodeToString(persisted)
    }

    fun loadNavigationState(): PersistedNavigationState? {
        val jsonString: String? = savedStateHandle[KEY_NAVIGATION_STATE]
        if (jsonString.isNullOrEmpty()) return null

        return try {
            json.decodeFromString<PersistedNavigationState>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        savedStateHandle.remove<Any>(KEY_NAVIGATION_STATE)
        savedStateHandle.remove<Any>(KEY_ROUTE)
        savedStateHandle.remove<Any>(KEY_WAYPOINTS)
    }
}