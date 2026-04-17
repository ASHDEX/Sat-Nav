package com.jayesh.satnav.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jayesh.satnav.features.demo.TripDemonstrationScreen
import com.jayesh.satnav.features.home.HomeScreen
import com.jayesh.satnav.features.location.LocationScreen
import com.jayesh.satnav.features.map.MapScreen
import com.jayesh.satnav.features.navigation.NavigationScreen
import com.jayesh.satnav.features.offlinemaps.OfflineMapsScreen
import com.jayesh.satnav.features.routing.RoutePreviewScreen
import com.jayesh.satnav.features.routing.RoutingScreen
import com.jayesh.satnav.features.savedplaces.SavedPlacesScreen
import com.jayesh.satnav.features.search.SearchScreen
import com.jayesh.satnav.features.settings.SettingsScreen

/**
 * Main navigation host for the Satnav application.
 * Defines all composable destinations reachable from the home screen.
 */
@Composable
fun SatnavNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Home.route,
        modifier = modifier
    ) {
        // Home screen
        composable(Home.route) {
            HomeScreen(onNavigate = { route ->
                navController.navigate(route)
            })
        }

        // Search screen
        composable(Search.ROUTE) {
            SearchScreen(
                onLocationSelected = { lat, lon ->
                    // Navigate to route preview with selected location
                    navController.navigate(
                        RoutePreview.createRoute(
                            destinationLat = lat,
                            destinationLon = lon,
                            destinationName = "Selected location"
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Map screen
        composable("map") {
            MapScreen()
        }

        // Location screen
        composable("location") {
            LocationScreen()
        }

        // Routing screen
        composable("routing") {
            RoutingScreen(
                onBack = { navController.popBackStack() },
                onNavigationReady = { navController.navigate("navigation_stub") }
            )
        }

        // Navigation stub (placeholder for actual navigation)
        composable("navigation_stub") {
            NavigationScreen()
        }

        // Trip demonstration screen
        composable("trip_demo") {
            TripDemonstrationScreen(
                onStartNavigation = { offlineRoute ->
                    // For now, navigate to navigation stub
                    navController.navigate("navigation_stub")
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Route preview screen
        composable(RoutePreview.ROUTE) { backStackEntry ->
            val destinationLat = backStackEntry.arguments?.getString("destinationLat")?.toDoubleOrNull() ?: 0.0
            val destinationLon = backStackEntry.arguments?.getString("destinationLon")?.toDoubleOrNull() ?: 0.0
            val destinationName = backStackEntry.arguments?.getString("destinationName")?.decodeFromRoute() ?: "Destination"
            
            RoutePreviewScreen(
                onStartNavigation = { routeOptionId ->
                    navController.navigate(Navigation.createRoute(routeOptionId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Saved places screen
        composable(SavedPlaces.route) {
            SavedPlacesScreen(
                onPlaceSelected = { place ->
                    // Navigate to route preview with selected place
                    navController.navigate(
                        RoutePreview.createRoute(
                            destinationLat = place.lat,
                            destinationLon = place.lon,
                            destinationName = place.name
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Offline maps screen
        composable(OfflineMaps.route) {
            OfflineMapsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Settings screen
        composable(Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // TODO: Wire TripCreator, AiRouteSuggestions when those screens are implemented
    }
}

private fun String.decodeFromRoute(): String = this.replace("%2F", "/")