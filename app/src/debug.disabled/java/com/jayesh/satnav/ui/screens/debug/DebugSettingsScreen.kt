package com.jayesh.satnav.ui.screens.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jayesh.satnav.R
import com.jayesh.satnav.ui.components.CockpitTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(
    viewModel: DebugSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState
    var selectedGpxFile by remember { mutableStateOf("") }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(Unit) {
        viewModel.loadAvailableGpxFiles()
        viewModel.loadDebugSettings()
    }

    Scaffold(
        topBar = {
            CockpitTopBar(
                title = "Debug Settings",
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // GPX Replay Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "GPX Replay",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Use GPX replay instead of real GPS",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = uiState.useGpxReplay,
                            onCheckedChange = { enabled ->
                                viewModel.setUseGpxReplay(enabled)
                            }
                        )
                    }
                }
            }

            if (uiState.useGpxReplay) {
                // GPX File Selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "GPX File",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Text(
                            text = "Select a GPX file to replay:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        // Simple dropdown simulation
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.availableGpxFiles.forEach { filename ->
                                Button(
                                    onClick = {
                                        selectedGpxFile = filename
                                        viewModel.loadGpxFile(filename)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = filename.isNotEmpty()
                                ) {
                                    Text(text = filename)
                                }
                            }
                        }
                        
                        if (selectedGpxFile.isNotEmpty()) {
                            Text(
                                text = "Selected: $selectedGpxFile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Speed Multiplier
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Speed Multiplier",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Text(
                            text = "Speed: ${"%.1f".format(speedMultiplier)}x",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Slider(
                            value = speedMultiplier,
                            onValueChange = { speedMultiplier = it },
                            valueRange = 0.5f..10f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Button(
                            onClick = {
                                viewModel.setSpeedMultiplier(speedMultiplier)
                                viewModel.startReplay(speedMultiplier)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Start Replay")
                        }
                    }
                }

                // Control Buttons
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Controls",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.jumpToStart() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "Jump to Start")
                            }
                            
                            Button(
                                onClick = { viewModel.stopReplay() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "Stop")
                            }
                        }
                    }
                }

                // Status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Text(
                            text = if (uiState.isReplaying) "Replaying GPX file" else "Stopped",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.isReplaying) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (uiState.currentLocation != null) {
                            Text(
                                text = "Lat: ${"%.6f".format(uiState.currentLocation.latitude)}, " +
                                      "Lon: ${"%.6f".format(uiState.currentLocation.longitude)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Need to create a simple Row composable since we're not importing it
@Composable
private fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content
    )
}