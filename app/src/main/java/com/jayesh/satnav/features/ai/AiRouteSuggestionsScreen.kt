package com.jayesh.satnav.features.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jayesh.satnav.domain.model.*
import kotlinx.coroutines.launch

/**
 * Screen for displaying AI-powered route suggestions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRouteSuggestionsScreen(
    start: RouteCoordinate,
    end: RouteCoordinate,
    userId: String,
    onRouteSelected: (OfflineRoute) -> Unit,
    onBack: () -> Unit,
    viewModel: AiRouteSuggestionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Load suggestions on first composition
    LaunchedEffect(start, end, userId) {
        viewModel.loadSuggestions(start, end, userId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "AI Route Suggestions",
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
                                viewModel.refreshSuggestions(start, end, userId)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is AiRouteSuggestionsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is AiRouteSuggestionsUiState.Error -> {
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
                        text = "Failed to load suggestions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        scope.launch {
                            viewModel.loadSuggestions(start, end, userId)
                        }
                    }) {
                        Text("Try Again")
                    }
                }
            }

            is AiRouteSuggestionsUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        // Optimization criteria selector
                        OptimizationCriteriaSelector(
                            currentCriteria = state.criteria,
                            onCriteriaChanged = { newCriteria ->
                                scope.launch {
                                    viewModel.updateCriteria(newCriteria, start, end, userId)
                                }
                            }
                        )
                    }

                    item {
                        // Personalization toggle
                        PersonalizationToggle(
                            isEnabled = state.includePersonalization,
                            onToggle = { enabled ->
                                scope.launch {
                                    viewModel.togglePersonalization(enabled, start, end, userId)
                                }
                            }
                        )
                    }

                    item {
                        // Recommended route
                        if (state.recommendedRoute != null) {
                            RecommendedRouteCard(
                                alternative = state.recommendedRoute,
                                isRecommended = true,
                                onClick = {
                                    onRouteSelected(state.recommendedRoute.route)
                                    // Learn from this choice
                                    scope.launch {
                                        viewModel.learnFromChoice(
                                            userId = userId,
                                            chosenRoute = state.recommendedRoute.route,
                                            alternatives = state.alternatives.map { it.route }
                                        )
                                    }
                                }
                            )
                        }
                    }

                    // Alternative routes
                    items(state.alternatives.filter { it != state.recommendedRoute }) { alternative ->
                        RouteAlternativeCard(
                            alternative = alternative,
                            onClick = {
                                onRouteSelected(alternative.route)
                                // Learn from this choice
                                scope.launch {
                                    viewModel.learnFromChoice(
                                        userId = userId,
                                        chosenRoute = alternative.route,
                                        alternatives = state.alternatives.map { it.route }
                                    )
                                }
                            }
                        )
                    }

                    item {
                        // Destination prediction (if available)
                        state.destinationPrediction?.let { prediction ->
                            DestinationPredictionCard(
                                prediction = prediction,
                                onNavigate = {
                                    // Navigate to predicted destination
                                    scope.launch {
                                        viewModel.navigateToPrediction(prediction)
                                    }
                                }
                            )
                        }
                    }

                    item {
                        // Driving insights (if available)
                        state.drivingAnalytics?.let { analytics ->
                            DrivingInsightsCard(analytics = analytics)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Optimization criteria selector
 */
@Composable
private fun OptimizationCriteriaSelector(
    currentCriteria: OptimizationCriteria,
    onCriteriaChanged: (OptimizationCriteria) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
                    text = "Optimization Criteria",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Time weight slider
                CriteriaSlider(
                    label = "Time Priority",
                    icon = Icons.Default.Schedule,
                    value = currentCriteria.timeWeight,
                    onValueChange = { newValue ->
                        onCriteriaChanged(
                            currentCriteria.copy(timeWeight = newValue)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                // Distance weight slider
                CriteriaSlider(
                    label = "Distance Priority",
                    icon = Icons.Default.Directions,
                    value = currentCriteria.distanceWeight,
                    onValueChange = { newValue ->
                        onCriteriaChanged(
                            currentCriteria.copy(distanceWeight = newValue)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                // Scenic weight slider
                CriteriaSlider(
                    label = "Scenic Priority",
                    icon = Icons.Default.Landscape,
                    value = currentCriteria.scenicWeight,
                    onValueChange = { newValue ->
                        onCriteriaChanged(
                            currentCriteria.copy(scenicWeight = newValue)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                // Safety weight slider
                CriteriaSlider(
                    label = "Safety Priority",
                    icon = Icons.Default.Security,
                    value = currentCriteria.safetyWeight,
                    onValueChange = { newValue ->
                        onCriteriaChanged(
                            currentCriteria.copy(safetyWeight = newValue)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                // Toggle options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilterChip(
                        selected = currentCriteria.avoidTolls,
                        onClick = {
                            onCriteriaChanged(
                                currentCriteria.copy(avoidTolls = !currentCriteria.avoidTolls)
                            )
                        },
                        label = { Text("Avoid Tolls") }
                    )
                    
                    FilterChip(
                        selected = currentCriteria.avoidHighways,
                        onClick = {
                            onCriteriaChanged(
                                currentCriteria.copy(avoidHighways = !currentCriteria.avoidHighways)
                            )
                        },
                        label = { Text("Avoid Highways") }
                    )
                    
                    FilterChip(
                        selected = currentCriteria.preferScenicRoutes,
                        onClick = {
                            onCriteriaChanged(
                                currentCriteria.copy(preferScenicRoutes = !currentCriteria.preferScenicRoutes)
                            )
                        },
                        label = { Text("Scenic") }
                    )
                }
            }
        }
    }
}

/**
 * Criteria slider component
 */
@Composable
private fun CriteriaSlider(
    label: String,
    icon: ImageVector,
    value: Double,
    onValueChange: (Double) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = 0f..1f,
            steps = 9
        )
    }
}

/**
 * Personalization toggle
 */
@Composable
private fun PersonalizationToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Personalized Suggestions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEnabled) "Using your preferences and driving history" else "Using general recommendations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

/**
 * Recommended route card
 */
@Composable
private fun RecommendedRouteCard(
    alternative: AiRouteAlternative,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Recommended",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "AI Recommended",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Score badge
                ScoreBadge(score = alternative.score)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Route details
            RouteDetails(alternative = alternative)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // AI insights
            if (alternative.aiInsights.isNotEmpty()) {
                AiInsightsSection(insights = alternative.aiInsights)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Personalization level
            PersonalizationBadge(level = alternative.personalizationLevel)
        }
    }
}

/**
 * Route alternative card
 */
@Composable
private fun RouteAlternativeCard(
    alternative: AiRouteAlternative,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Alternative Route",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Score badge
                ScoreBadge(score = alternative.score)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Route details
            RouteDetails(alternative = alternative)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Quick insights
            if (alternative.aiInsights.isNotEmpty()) {
                val mainInsight = alternative.aiInsights.first()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        getInsightIcon(mainInsight.type),
                        contentDescription = mainInsight.type.name,
                        modifier = Modifier.size(16.dp),
                        tint = getInsightColor(mainInsight.impact)
                    )
                    Text(
                        text = mainInsight.message,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Route details section
 */
@Composable
private fun RouteDetails(alternative: AiRouteAlternative) {
    val route = alternative.route
    val breakdown = alternative.scoreBreakdown
    
    Column {
        // Distance and time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RouteDetailItem(
                icon = Icons.Default.Directions,
                label = "Distance",
                value = "${(route.distanceMeters / 1000).format(1)} km"
            )
            
            RouteDetailItem(
                icon = Icons.Default.Schedule,
                label = "Time",
                value = "${(route.durationMillis / 1000 / 60)} min"
            )
            
            RouteDetailItem(
                icon = Icons.Default.TrendingUp,
                label = "Score",
                value = "${(alternative.score * 100).toInt()}%"
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Score breakdown
        ScoreBreakdownBar(breakdown = breakdown)
    }
}

/**
 * Route detail item
 */
@Composable
private fun RouteDetailItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Score breakdown bar
 */
@Composable
private fun ScoreBreakdownBar(breakdown: ScoreBreakdown) {
    Column {
        Text(
            text = "Score Breakdown",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Time score
            Box(
                modifier = Modifier
                    .weight(breakdown.timeScore.toFloat())
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            // Distance score
            Box(
                modifier = Modifier
                    .weight(breakdown.distanceScore.toFloat())
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondary)
            )
            
            // Scenic score
            Box(
                modifier = Modifier
                    .weight(breakdown.scenicScore.toFloat())
                    .fillMaxHeight()
                    .background(Color(0xFF4CAF50)) // Green
            )
            
            // Safety score
            Box(
                modifier = Modifier
                    .weight(breakdown.safetyScore.toFloat())
                    .fillMaxHeight()
                    .background(Color(0xFFF44336)) // Red
            )
            
            // Familiarity score
            Box(
                modifier = Modifier
                    .weight(breakdown.familiarityScore.toFloat())
                    .fillMaxHeight()
                    .background(Color(0xFF9C27B0)) // Purple
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ScoreLegendItem(color = MaterialTheme.colorScheme.primary, label = "Time")
            ScoreLegendItem(color = MaterialTheme.colorScheme.secondary, label = "Distance")
            ScoreLegendItem(color = Color(0xFF4CAF50), label = "Scenic")
            ScoreLegendItem(color = Color(0xFFF44336), label = "Safety")
            ScoreLegendItem(color = Color(0xFF9C27B0), label = "Familiar")
        }
    }
}

/**
 * Score legend item
 */
@Composable
private fun ScoreLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp
        )
    }
}

/**
 * AI insights section
 */
@Composable
private fun AiInsightsSection(insights: List<AiInsight>) {
    Column {
        Text(
            text = "AI Insights",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        insights.forEach { insight ->
            AiInsightItem(insight = insight)
        }
    }
}

/**
 * AI insight item
 */
@Composable
private fun AiInsightItem(insight: AiInsight) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            getInsightIcon(insight.type),
            contentDescription = insight.type.name,
            modifier = Modifier.size(20.dp),
            tint = getInsightColor(insight.impact)
        )
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = insight.message,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = insight.type.name.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "${(insight.confidence * 100).toInt()}% confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = getInsightColor(insight.impact)
                )
            }
        }
    }
}

/**
 * Get icon for insight type
 */
private fun getInsightIcon(type: InsightType): ImageVector {
    return when (type) {
        InsightType.SCENIC_ROUTE -> Icons.Default.Landscape
        InsightType.TIME_SAVING -> Icons.Default.Schedule
        InsightType.SAFE_ROUTE -> Icons.Default.Security
        InsightType.FAMILIAR_ROUTE -> Icons.Default.History
        InsightType.AVOIDS_TRAFFIC -> Icons.Default.Traffic
        InsightType.GOOD_FOR_WEATHER -> Icons.Default.Cloud
        InsightType.ENERGY_EFFICIENT -> Icons.Default.Eco
        InsightType.AVOIDS_CONSTRUCTION -> Icons.Default.Construction
        InsightType.PREFERRED_BY_USERS -> Icons.Default.ThumbUp
    }
}

/**
 * Get color for impact level
 */
@Composable
private fun getInsightColor(impact: ImpactLevel): Color {
    return when (impact) {
        ImpactLevel.HIGH -> MaterialTheme.colorScheme.primary
        ImpactLevel.MEDIUM -> MaterialTheme.colorScheme.secondary
        ImpactLevel.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Score badge
 */
@Composable
private fun ScoreBadge(score: Double) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    score > 0.8 -> Color(0xFF4CAF50) // Green
                    score > 0.6 -> Color(0xFF2196F3) // Blue
                    else -> Color(0xFFFF9800) // Orange
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "${(score * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * Personalization badge
 */
@Composable
private fun PersonalizationBadge(level: PersonalizationLevel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = "Personalization",
            modifier = Modifier.size(16.dp),
            tint = when (level) {
                PersonalizationLevel.MAXIMUM -> MaterialTheme.colorScheme.primary
                PersonalizationLevel.HIGH -> MaterialTheme.colorScheme.primary
                PersonalizationLevel.MODERATE -> MaterialTheme.colorScheme.secondary
                PersonalizationLevel.BASIC -> MaterialTheme.colorScheme.onSurfaceVariant
                PersonalizationLevel.NONE -> MaterialTheme.colorScheme.outline
            }
        )
        
        Text(
            text = when (level) {
                PersonalizationLevel.MAXIMUM -> "Fully Personalized"
                PersonalizationLevel.HIGH -> "Highly Personalized"
                PersonalizationLevel.MODERATE -> "Moderately Personalized"
                PersonalizationLevel.BASIC -> "Basic Personalization"
                PersonalizationLevel.NONE -> "No Personalization"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when (level) {
                PersonalizationLevel.MAXIMUM -> MaterialTheme.colorScheme.primary
                PersonalizationLevel.HIGH -> MaterialTheme.colorScheme.primary
                PersonalizationLevel.MODERATE -> MaterialTheme.colorScheme.secondary
                PersonalizationLevel.BASIC -> MaterialTheme.colorScheme.onSurfaceVariant
                PersonalizationLevel.NONE -> MaterialTheme.colorScheme.outline
            }
        )
    }
}

/**
 * Destination prediction card
 */
@Composable
private fun DestinationPredictionCard(
    prediction: DestinationPrediction,
    onNavigate: () -> Unit
) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = "Prediction",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Destination Prediction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                ConfidenceBadge(confidence = prediction.confidence)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = prediction.destinationName ?: "Predicted Destination",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Prediction reasons
            prediction.reasons.take(2).forEach { reason ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Reason",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = reason.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Navigate to Predicted Destination")
            }
        }
    }
}

/**
 * Confidence badge
 */
@Composable
private fun ConfidenceBadge(confidence: Double) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    confidence > 0.8 -> Color(0xFF4CAF50) // Green
                    confidence > 0.6 -> Color(0xFFFF9800) // Orange
                    else -> Color(0xFFF44336) // Red
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "${(confidence * 100).toInt()}% confident",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

/**
 * Driving insights card
 */
@Composable
private fun DrivingInsightsCard(analytics: DrivingAnalytics) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Insights,
                        contentDescription = "Insights",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Driving Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "Last 30 days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Key metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DrivingMetricItem(
                    label = "Efficiency",
                    value = "${analytics.efficiencyScore.toInt()}%",
                    color = when {
                        analytics.efficiencyScore > 80 -> Color(0xFF4CAF50)
                        analytics.efficiencyScore > 60 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                )
                
                DrivingMetricItem(
                    label = "Safety",
                    value = "${analytics.safetyScore.toInt()}%",
                    color = when {
                        analytics.safetyScore > 80 -> Color(0xFF4CAF50)
                        analytics.safetyScore > 60 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                )
                
                DrivingMetricItem(
                    label = "Smoothness",
                    value = "${analytics.smoothnessScore.toInt()}%",
                    color = when {
                        analytics.smoothnessScore > 80 -> Color(0xFF4CAF50)
                        analytics.smoothnessScore > 60 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Top recommendation
            analytics.recommendations.firstOrNull()?.let { recommendation ->
                Column {
                    Text(
                        text = "Recommendation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = recommendation.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = recommendation.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Driving metric item
 */
@Composable
private fun DrivingMetricItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Format Double to 1 decimal place
 */
private fun Double.format(decimalPlaces: Int): String {
    return "%.${decimalPlaces}f".format(this)
}
