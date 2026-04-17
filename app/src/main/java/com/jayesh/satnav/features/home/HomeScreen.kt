package com.jayesh.satnav.features.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Satnav") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier.padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(destinationItems) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text(item.description) },
                    leadingContent = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { onNavigate(item.route) }
                )
                Divider()
            }
        }
    }
}

private data class DestinationItem(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)

private val destinationItems = listOf(
    DestinationItem(
        title = "Map",
        description = "Interactive offline map",
        icon = Icons.Default.Map,
        route = "map"
    ),
    DestinationItem(
        title = "Location",
        description = "GPS and location services",
        icon = Icons.Default.LocationOn,
        route = "location"
    ),
    DestinationItem(
        title = "Search",
        description = "Find places and addresses",
        icon = Icons.Default.Search,
        route = "search"
    ),
    DestinationItem(
        title = "Routing",
        description = "Plan routes and get directions",
        icon = Icons.Default.Directions,
        route = "routing"
    ),
    DestinationItem(
        title = "Trip Demo",
        description = "Demonstrate trip features",
        icon = Icons.Default.DirectionsCar,
        route = "trip_demo"
    ),
    DestinationItem(
        title = "Navigation",
        description = "Active navigation screen",
        icon = Icons.Default.Navigation,
        route = "navigation_stub"
    ),
    DestinationItem(
        title = "Saved Places",
        description = "View and manage saved locations",
        icon = Icons.Default.Favorite,
        route = "saved_places"
    ),
    DestinationItem(
        title = "Offline Maps",
        description = "Manage downloaded map packs",
        icon = Icons.Default.Storage,
        route = "offline_maps"
    ),
    DestinationItem(
        title = "Settings",
        description = "App preferences and configuration",
        icon = Icons.Default.Settings,
        route = "settings"
    )
)