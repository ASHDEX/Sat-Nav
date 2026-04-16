package com.jayesh.satnav.features.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jayesh.satnav.domain.model.*
import kotlinx.coroutines.launch

/**
 * Comprehensive trip demonstration screen that showcases all Satnav features
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDemonstrationScreen(
    onStartNavigation: (OfflineRoute) -> Unit,
    onBack: () -> Unit,
    viewModel: TripDemonstrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Load demonstration data on first composition
    LaunchedEffect(Unit) {
        viewModel.loadDemonstrationData()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Satnav Feature Demonstration",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                viewModel.resetDemonstration()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                }
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is TripDemonstrationUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Loading demonstration data...")
                    }
                }
            }

            is TripDemonstrationUiState.Ready -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Trip Selection Segmented Buttons
                    item {
                        val currentTripType by viewModel.selectedTripType.collectAsState()
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = currentTripType == TripType.LOCAL,
                                onClick = { viewModel.selectTrip(TripType.LOCAL) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Local Trip")
                            }
                            SegmentedButton(
                                selected = currentTripType == TripType.HILL,
                                onClick = { viewModel.selectTrip(TripType.HILL) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("Hill Trip (Kangra)")
                            }
                        }
                    }

                    // Trip overview
                    item {
                        TripOverviewCard(state.tripOverview)
                    }

                    // Feature demonstration sections
                    item {
                        Text(
                            text = "Feature Demonstrations",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(state.featureDemonstrations) { demonstration ->
                        FeatureDemonstrationCard(
                            demonstration = demonstration,
                            onStartDemo = {
                                scope.launch {
                                    viewModel.startFeatureDemonstration(demonstration.id)
                                }
                            }
                        )
                    }

                    // Test scenarios
                    item {
                        Text(
                            text = "Test Scenarios",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    items(state.testScenarios) { scenario ->
                        TestScenarioCard(
                            scenario = scenario,
                            onRunTest = {
                                scope.launch {
                                    viewModel.runTestScenario(scenario.id)
                                }
                            }
                        )
                    }

                    // Performance metrics
                    item {
                        PerformanceMetricsCard(state.performanceMetrics)
                    }

                    // Improvement suggestions
                    item {
                        ImprovementSuggestionsCard(state.improvementSuggestions)
                    }

                    // Start trip button — always visible at the bottom of the list
                    item {
                        Button(
                            onClick = {
                                scope.launch { viewModel.startComprehensiveTrip() }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text("Start Comprehensive Trip")
                        }
                    }
                }
            }

            is TripDemonstrationUiState.Demonstrating -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Current feature being demonstrated
                        Card(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    state.currentFeature.icon,
                                    contentDescription = state.currentFeature.title,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = state.currentFeature.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.currentFeature.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Next feature preview
                        if (state.nextFeature != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "Next: ${state.nextFeature.title}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = state.nextFeature.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2
                                    )
                                }
                            }
                        }

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(0.8f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        viewModel.skipCurrentDemonstration()
                                    }
                                }
                            ) {
                                Text("Skip")
                            }
                            
                            Button(
                                onClick = {
                                    scope.launch {
                                        viewModel.pauseResumeDemonstration()
                                    }
                                }
                            ) {
                                Text(if (state.isPaused) "Resume" else "Pause")
                            }
                        }
                    }
                }
            }

            is TripDemonstrationUiState.Completed -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Demonstration Complete!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "All features have been successfully demonstrated.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Results summary
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Results Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ResultItem(
                                label = "Features Tested",
                                value = state.results.featuresTested.toString(),
                                icon = Icons.Default.Checklist
                            )
                            ResultItem(
                                label = "Success Rate",
                                value = "${state.results.successRate}%",
                                icon = Icons.Default.Star
                            )
                            ResultItem(
                                label = "Performance Score",
                                value = "${state.results.performanceScore}/100",
                                icon = Icons.Default.Speed
                            )
                            ResultItem(
                                label = "Issues Found",
                                value = state.results.issuesFound.toString(),
                                icon = Icons.Default.Warning
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.startNavigation(onStartNavigation)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text("Start Real Navigation")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text("Return to Main Menu")
                    }
                }
            }

            is TripDemonstrationUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Demonstration Failed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        scope.launch {
                            viewModel.loadDemonstrationData()
                        }
                    }) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}

/**
 * Trip overview card
 */
@Composable
private fun TripOverviewCard(overview: TripOverview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = overview.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                // Duration badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${overview.estimatedDuration} min",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Route summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoutePoint(
                    label = "Start",
                    location = overview.startLocation,
                    icon = Icons.Default.Place
                )
                
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "Route",
                    modifier = Modifier.size(24.dp)
                )
                
                RoutePoint(
                    label = "End",
                    location = overview.endLocation,
                    icon = Icons.Default.Flag
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Features included
            Text(
                text = "Features Included:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                overview.featuresIncluded.forEach { feature ->
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text(feature) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = feature,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Route point display
 */
@Composable
private fun RoutePoint(
    label: String,
    location: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = location,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

/**
 * Feature demonstration card
 */
@Composable
private fun FeatureDemonstrationCard(
    demonstration: FeatureDemonstration,
    onStartDemo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (demonstration.priority) {
                Priority.HIGH -> MaterialTheme.colorScheme.errorContainer
                Priority.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                Priority.LOW -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        demonstration.icon,
                        contentDescription = demonstration.title,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = demonstration.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Phase ${demonstration.phase}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Button(
                    onClick = onStartDemo,
                    enabled = demonstration.status == DemoStatus.READY
                ) {
                    Text("Demo")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = demonstration.description,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Status and metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (demonstration.status) {
                                DemoStatus.READY -> Color(0xFF4CAF50)
                                DemoStatus.RUNNING -> Color(0xFF2196F3)
                                DemoStatus.COMPLETED -> Color(0xFF9C27B0)
                                DemoStatus.FAILED -> Color(0xFFF44336)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = demonstration.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                
                // Complexity indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(demonstration.complexity) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Complexity",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Test scenario card
 */
@Composable
private fun TestScenarioCard(
    scenario: TestScenario,
    onRunTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = scenario.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Button(
                    onClick = onRunTest,
                    enabled = scenario.status == TestStatus.READY
                ) {
                    Text("Run Test")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = scenario.description,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Test details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (scenario.status) {
                                TestStatus.READY -> Color(0xFF4CAF50)
                                TestStatus.RUNNING -> Color(0xFF2196F3)
                                TestStatus.PASSED -> Color(0xFF9C27B0)
                                TestStatus.FAILED -> Color(0xFFF44336)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = scenario.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                
                // Expected duration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = "Duration",
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${scenario.expectedDuration}s",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/**
 * Performance metrics card
 */
@Composable
private fun PerformanceMetricsCard(metrics: PerformanceMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Performance Metrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Metrics grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "Route Compute",
                    value = "${metrics.routeComputeTime}ms",
                    target = "≤100ms"
                )
                MetricItem(
                    label = "Map Load",
                    value = "${metrics.mapLoadTime}ms",
                    target = "≤500ms"
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "Memory Usage",
                    value = "${metrics.memoryUsageMB}MB",
                    target = "≤50MB"
                )
                MetricItem(
                    label = "Battery Impact",
                    value = "${metrics.batteryImpact}%",
                    target = "≤5%"
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Overall score
            LinearProgressIndicator(
                progress = { metrics.overallScore / 100f },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Overall Performance",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "${metrics.overallScore}/100",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Metric item display
 */
@Composable
private fun MetricItem(label: String, value: String, target: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Target: $target",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Improvement suggestions card
 */
@Composable
private fun ImprovementSuggestionsCard(suggestions: ImprovementSuggestions) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Improvement Suggestions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            suggestions.suggestions.forEach { suggestion ->
                SuggestionItem(suggestion)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Feedback form
            var feedbackText by remember { mutableStateOf("") }
            
            OutlinedTextField(
                value = feedbackText,
                onValueChange = { feedbackText = it },
                label = { Text("Your feedback...") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = { /* TODO: Submit feedback */ },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Submit Feedback")
            }
        }
    }
}

/**
 * Suggestion item
 */
@Composable
private fun SuggestionItem(suggestion: Suggestion) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            when (suggestion.priority) {
                Priority.HIGH -> Icons.Default.Warning
                Priority.MEDIUM -> Icons.Default.Info
                Priority.LOW -> Icons.Default.Lightbulb
            },
            contentDescription = "Suggestion",
            tint = when (suggestion.priority) {
                Priority.HIGH -> MaterialTheme.colorScheme.error
                Priority.MEDIUM -> MaterialTheme.colorScheme.primary
                Priority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = suggestion.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Result item for summary
 */
@Composable
private fun ResultItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
