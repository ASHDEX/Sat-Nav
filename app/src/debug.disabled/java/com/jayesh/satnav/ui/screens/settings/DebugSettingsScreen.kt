package com.jayesh.satnav.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jayesh.satnav.R
import com.jayesh.satnav.navigation.DebugSettings
import com.jayesh.satnav.ui.components.CockpitTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    navController: NavController? = null,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            CockpitTopBar(
                title = stringResource(R.string.settings_screen_title),
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState) {
                is SettingsUiState.Loading -> {
                    Text(text = "Loading settings...")
                }
                
                is SettingsUiState.Error -> {
                    Text(text = "Error: ${uiState.message}")
                }
                
                is SettingsUiState.Ready -> {
                    // Regular settings content
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
                                text = "Settings Screen - ${uiState.data}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Debug settings card (only in debug builds)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        onClick = {
                            navController?.navigate(DebugSettings.route)
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚙️ Debug Settings",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "GPX replay, testing tools, and debug features",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = {
                                    navController?.navigate(DebugSettings.route)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Open Debug Settings")
                            }
                        }
                    }
                }
            }
        }
    }
}