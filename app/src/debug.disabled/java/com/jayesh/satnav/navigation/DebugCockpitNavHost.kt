package com.jayesh.satnav.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jayesh.satnav.ui.screens.debug.DebugSettingsScreen
import com.jayesh.satnav.ui.screens.debug.DebugSettingsViewModel
import com.jayesh.satnav.ui.screens.home.HomeScreen
import com.jayesh.satnav.ui.screens.home.HomeViewModel
import com.jayesh.satnav.ui.screens.navigation.NavigationScreen
import com.jayesh.satnav.ui.screens.navigation.NavigationViewModel
import com.jayesh.satnav.ui.screens.offlinemaps.OfflineMapsScreen
import com.jayesh.satnav.ui.screens.offlinemaps.OfflineMapsViewModel
import com.jayesh.satnav.ui.screens.routepreview.RoutePreviewScreen
import com.jayesh.satnav.ui.screens.routepreview.RoutePreviewViewModel
import com.jayesh.satnav.ui.screens.savedplaces.SavedPlacesScreen
import com.jayesh.satnav.ui.screens.savedplaces.SavedPlacesViewModel
import com.jayesh.satnav.ui.screens.search.SearchScreen
import com.jayesh.satnav.ui.screens.search.SearchViewModel
import com.jayesh.satnav.ui.screens.settings.DebugSettingsScreen as DebugSettingsScreenWrapper
import com.jayesh.satnav.ui.screens.settings.SettingsViewModel

/**
 * Debug version of the main navigation host that includes debug routes.
 */
@Composable
fun DebugCockpitNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: NavRoute = Home
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier
    ) {
        // Home screen
        composable(Home.route) {
            val viewModel: HomeViewModel = hiltViewModel()
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            
            HomeScreen(
                uiState = uiState.value,
                onSearchClick = { navController.navigate(Search.route) },
                onSavedPlacesClick = { navController.navigate(SavedPlaces.route) },
                onOfflineMapsClick = { navController.navigate(OfflineMaps.route) },
                onSettingsClick = { navController.navigate(Settings.route) },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Search screen
        composable(Search.route) {
            val viewModel: SearchViewModel = hiltViewModel()
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            
            SearchScreen(
                uiState = uiState.value,
                onBack = { navController.popBackStack() },
                onPlaceSelected = { place ->
                    navController.navigate(
                        RoutePreview.createRoute(
                            destinationLat = place.latitude,
                            destinationLon = place.longitude,
                            destinationName = place.name
                        )
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Route preview screen
        composable(
            route = RoutePreview.ROUTE,
            arguments = listOf(
                navArgument("destinationLat") { type = NavType.FloatType },
                navArgument("destinationLon") { type = NavType.FloatType },
                navArgument("destinationName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val viewModel: RoutePreviewViewModel = hiltViewModel()
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            val arguments = backStackEntry.arguments
            val destinationLat = arguments?.getFloat("destinationLat")?.toDouble() ?: 0.0
            val destinationLon = arguments?.getFloat("destinationLon")?.toDouble() ?: 0.0
            val destinationName = arguments?.getString("destinationName") ?: ""
            
            RoutePreviewScreen(
                uiState = uiState.value,
                destinationLat = destinationLat,
                destinationLon = destinationLon,
                destinationName = destinationName,
                onBack = { navController.popBackStack() },
                onRouteSelected = { routeOptionId ->
                    navController.navigate(Navigation.createRoute(routeOptionId))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Navigation screen
        composable(
            route = Navigation.ROUTE,
            arguments = listOf(
                navArgument("routeOptionId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val viewModel: NavigationViewModel = hiltViewModel()
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            val routeOptionId = backStackEntry.arguments?.getInt("routeOptionId") ?: 0
            
            NavigationScreen(
                uiState = uiState.value,
                routeOptionId = routeOptionId,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Saved places screen
        composable(SavedPlaces.route) {
            val viewModel: SavedPlacesViewModel = hiltViewModel()
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            
            SavedPlacesScreen(
                uiState = uiState.value,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Offline maps screen
        composable(OfflineMaps.route) {
            val viewModel: OfflineMapsViewModel = hiltViewModel()
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            
            OfflineMapsScreen(
                uiState = uiState.value,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Settings screen (debug version)
        composable(Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            
            DebugSettingsScreenWrapper(
                uiState = uiState.value,
                onBack = { navController.popBackStack() },
                navController = navController,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Debug settings screen
        composable(DebugSettings.route) {
            val viewModel: DebugSettingsViewModel = hiltViewModel()
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            
            DebugSettingsScreen(
                uiState = uiState.value,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}