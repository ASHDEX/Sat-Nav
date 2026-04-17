package com.jayesh.satnav.features.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jayesh.satnav.domain.model.OfflineRoute
import com.jayesh.satnav.domain.model.RoutingProfile
import com.jayesh.satnav.features.routing.RoutingViewModel
import com.jayesh.satnav.features.routing.WaypointInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(
    onBack: () -> Unit,
    onNavigationReady: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Plan Route") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Waypoints",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            itemsIndexed(uiState.waypoints) { index, waypoint ->
                WaypointRow(
                    waypoint = waypoint,
                    index = index,
                    onLatitudeChange = { newValue ->
                        viewModel.updateWaypointLatitude(waypoint.id, newValue)
                    },
                    onLongitudeChange = { newValue ->
                        viewModel.updateWaypointLongitude(waypoint.id, newValue)
                    },
                    onDelete = {
                        if (uiState.waypoints.size > 2) {
                            viewModel.removeWaypoint(waypoint.id)
                        }
                    },
                    canDelete = uiState.waypoints.size > 2
                )
            }

            item {
                Button(
                    onClick = { viewModel.addWaypoint() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add waypoint")
                }
            }

            item {
                Text(
                    text = "Routing Profile",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoutingProfile.entries.forEach { profile ->
                        val displayName = when (profile) {
                            RoutingProfile.CAR -> "Car"
                            RoutingProfile.BIKE -> "Bike"
                            RoutingProfile.FOOT -> "Foot"
                            RoutingProfile.PUBLIC_TRANSIT -> "Public Transit"
                            RoutingProfile.MIXED -> "Mixed"
                        }
                        FilterChip(
                            selected = uiState.selectedProfile == profile,
                            onClick = { viewModel.updateSelectedProfile(profile) },
                            label = { Text(displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Engine Status",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!uiState.routingEngineReady) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = uiState.routingDebugMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Button(
                    onClick = { viewModel.computeRoute() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.routingEngineReady && !uiState.isRouting
                ) {
                    if (uiState.isRouting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Computing...")
                    } else {
                        Text("Compute Route")
                    }
                }
            }

            if (uiState.route != null) {
                item {
                    RouteResultCard(
                        route = uiState.route!!,
                        onStartNavigation = onNavigationReady,
                        navigationReady = uiState.navigationReady
                    )
                }
            }

            if (uiState.routeError != null) {
                item {
                    ErrorCard(
                        errorMessage = uiState.routeError!!,
                        onRetry = { viewModel.computeRoute() }
                    )
                }
            }
        }
    }

    // Show snackbar for errors
    LaunchedEffect(uiState.routeError) {
        uiState.routeError?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }
}

@Composable
private fun WaypointRow(
    waypoint: WaypointInput,
    index: Int,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Waypoint ${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete waypoint"
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = waypoint.latitude,
                    onValueChange = onLatitudeChange,
                    label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = waypoint.longitude,
                    onValueChange = onLongitudeChange,
                    label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun RouteResultCard(
    route: OfflineRoute,
    onStartNavigation: () -> Unit,
    navigationReady: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Route Computed",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${route.segmentCount} segments, ${route.waypointCount} waypoints, ${route.computationTimeMillis} ms",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onStartNavigation,
                modifier = Modifier.fillMaxWidth(),
                enabled = navigationReady
            ) {
                Text("Start Navigation")
            }
        }
    }
}

@Composable
private fun ErrorCard(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}
