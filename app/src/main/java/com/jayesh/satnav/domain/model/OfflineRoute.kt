package com.jayesh.satnav.domain.model

data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double,
)

data class Waypoint(
    val lat: Double,
    val lon: Double,
)

data class OfflineRoute(
    val profile: RoutingProfile,
    val points: List<RouteCoordinate>,
    val distanceMeters: Double,
    val durationMillis: Long,
    val computationTimeMillis: Long,
    val segmentCount: Int = 1,
    val waypointCount: Int = 2,
    val instructions: List<NavInstruction> = emptyList(),
)

data class RoutingEngineStatus(
    val isGraphPresent: Boolean = false,
    val isLoaded: Boolean = false,
    val graphDirectory: String? = null,
    val availableProfiles: List<String> = emptyList(),
    val debugMessage: String = "Searching for graph-cache…",
    val errorMessage: String? = null,
)
