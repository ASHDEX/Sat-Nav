package com.jayesh.satnav.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the cockpit navigation system.
 * All routes are defined as @Serializable data classes for safe argument passing.
 */
sealed interface NavRoute {
    val route: String
}

@Serializable
object Home : NavRoute {
    override val route: String = "home"
}

@Serializable
data class Search(
    val stopIndex: Int? = null
) : NavRoute {
    override val route: String = "search"
    
    companion object {
        const val ROUTE = "search/{stopIndex}"
        
        fun createRoute(stopIndex: Int? = null): String {
            return if (stopIndex != null) {
                "search/$stopIndex"
            } else {
                "search"
            }
        }
    }
}

@Serializable
data class RoutePreview(
    val destinationLat: Double,
    val destinationLon: Double,
    val destinationName: String
) : NavRoute {
    override val route: String = "route_preview"
    
    companion object {
        const val ROUTE = "route_preview/{destinationLat}/{destinationLon}/{destinationName}"
        
        fun createRoute(
            destinationLat: Double,
            destinationLon: Double,
            destinationName: String
        ): String = "route_preview/$destinationLat/$destinationLon/${destinationName.encodeForRoute()}"
        
        private fun String.encodeForRoute(): String = this.replace("/", "%2F")
    }
}

@Serializable
data class Navigation(
    val routeOptionId: Int
) : NavRoute {
    override val route: String = "navigation"
    
    companion object {
        const val ROUTE = "navigation/{routeOptionId}"
        
        fun createRoute(routeOptionId: Int): String = "navigation/$routeOptionId"
    }
}

@Serializable
object SavedPlaces : NavRoute {
    override val route: String = "saved_places"
}

@Serializable
object OfflineMaps : NavRoute {
    override val route: String = "offline_maps"
}

@Serializable
object Settings : NavRoute {
    override val route: String = "settings"
}

@Serializable
object TripCreator : NavRoute {
    override val route: String = "trip_creator"
}

/**
 * Helper functions for navigation argument parsing.
 */
fun parseRouteArguments(route: String, arguments: Map<String, String>): NavRoute? {
    return when (route) {
        "home" -> Home
        Search.ROUTE -> {
            val stopIndex = arguments["stopIndex"]?.toIntOrNull()
            Search(stopIndex)
        }
        "search" -> Search(null) // Route without arguments
        RoutePreview.ROUTE -> {
            val lat = arguments["destinationLat"]?.toDoubleOrNull()
            val lon = arguments["destinationLon"]?.toDoubleOrNull()
            val name = arguments["destinationName"]?.decodeFromRoute()
            if (lat != null && lon != null && name != null) {
                RoutePreview(lat, lon, name)
            } else {
                null
            }
        }
        Navigation.ROUTE -> {
            val id = arguments["routeOptionId"]?.toIntOrNull()
            if (id != null) Navigation(id) else null
        }
        "saved_places" -> SavedPlaces
        "offline_maps" -> OfflineMaps
        "settings" -> Settings
        "trip_creator" -> TripCreator
        else -> null
    }
}

private fun String.decodeFromRoute(): String = this.replace("%2F", "/")