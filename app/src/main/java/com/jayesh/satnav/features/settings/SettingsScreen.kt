package com.jayesh.satnav.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Screen for app settings and preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 3.dp
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.saveSettings() },
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text("Save Settings") }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "App Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(settingsItems) { item ->
                    when (item) {
                        is SettingsItem.Toggle -> {
                            ListItem(
                                headlineContent = { Text(item.title) },
                                supportingContent = { Text(item.description) },
                                leadingContent = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = when (item.key) {
                                            "fake_location" -> uiState.useFakeLocation
                                            "dark_mode" -> uiState.darkMode
                                            "voice_guidance" -> uiState.voiceGuidance
                                            "traffic_updates" -> uiState.trafficUpdates
                                            "save_trip_history" -> uiState.saveTripHistory
                                            else -> false
                                        },
                                        onCheckedChange = { checked ->
                                            when (item.key) {
                                                "fake_location" -> viewModel.toggleFakeLocation(checked)
                                                "dark_mode" -> viewModel.toggleDarkMode(checked)
                                                "voice_guidance" -> viewModel.toggleVoiceGuidance(checked)
                                                "traffic_updates" -> viewModel.toggleTrafficUpdates(checked)
                                                "save_trip_history" -> viewModel.toggleSaveTripHistory(checked)
                                            }
                                        }
                                    )
                                }
                            )
                            Divider()
                        }

                        is SettingsItem.Info -> {
                            ListItem(
                                headlineContent = { Text(item.title) },
                                supportingContent = { Text(item.value) },
                                leadingContent = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null
                                    )
                                }
                            )
                            Divider()
                        }
                    }
                }

                item {
                    if (uiState.errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = uiState.errorMessage!!,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "About",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Satnav v1.0.0",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Offline GPS Navigation System",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Built with MapLibre, GraphHopper, and Kotlin",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class SettingsItem {
    abstract val title: String
    abstract val description: String
    abstract val icon: androidx.compose.ui.graphics.vector.ImageVector
    abstract val key: String

    data class Toggle(
        override val title: String,
        override val description: String,
        override val icon: androidx.compose.ui.graphics.vector.ImageVector,
        override val key: String
    ) : SettingsItem()

    data class Info(
        override val title: String,
        override val description: String,
        override val icon: androidx.compose.ui.graphics.vector.ImageVector,
        override val key: String,
        val value: String
    ) : SettingsItem()
}

private val settingsItems = listOf(
    SettingsItem.Toggle(
        title = "Fake Location",
        description = "Use simulated GPS for testing",
        icon = Icons.Default.LocationOn,
        key = "fake_location"
    ),
    SettingsItem.Toggle(
        title = "Dark Mode",
        description = "Use dark theme",
        icon = Icons.Default.DarkMode,
        key = "dark_mode"
    ),
    SettingsItem.Toggle(
        title = "Voice Guidance",
        description = "Enable turn-by-turn voice instructions",
        icon = Icons.Default.VolumeUp,
        key = "voice_guidance"
    ),
    SettingsItem.Toggle(
        title = "Traffic Updates",
        description = "Show traffic conditions on map",
        icon = Icons.Default.Traffic,
        key = "traffic_updates"
    ),
    SettingsItem.Toggle(
        title = "Save Trip History",
        description = "Store completed trips for later review",
        icon = Icons.Default.History,
        key = "save_trip_history"
    ),
    SettingsItem.Info(
        title = "Offline Maps",
        description = "Current map status",
        icon = Icons.Default.Storage,
        key = "map_status",
        value = "Ready"
    ),
    SettingsItem.Info(
        title = "Routing Engine",
        description = "GraphHopper version",
        icon = Icons.Default.Directions,
        key = "routing_engine",
        value = "GraphHopper 8.0"
    )
)