package com.jayesh.satnav.features.traffic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jayesh.satnav.domain.model.CongestionLevel
import com.jayesh.satnav.domain.model.TrafficPrediction
import com.jayesh.satnav.domain.model.TrafficVisualization as TrafficVisualizationModel
import com.jayesh.satnav.domain.repository.TrafficInsight

/**
 * Composable components for traffic pattern visualization.
 * Phase 13: Real-time Traffic Pattern Awareness
 */

/**
 * Traffic congestion level indicator with color coding.
 */
@Composable
fun TrafficCongestionIndicator(
    congestionLevel: CongestionLevel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color = Color(android.graphics.Color.parseColor(congestionLevel.colorHex)))
    )
}

/**
 * Traffic prediction card showing predicted travel time and congestion.
 */
@Composable
fun TrafficPredictionCard(
    prediction: TrafficPrediction,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with congestion level
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrafficCongestionIndicator(congestionLevel = prediction.congestionLevel)
                Text(
                    text = "Traffic Prediction",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${(prediction.confidence * 100).toInt()}% confident",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Travel time prediction
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Predicted travel time:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatTravelTime(prediction.predictedTravelTimeSeconds),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Congestion description
            Text(
                text = "Expected ${prediction.congestionLevel.displayName.lowercase()} traffic",
                style = MaterialTheme.typography.bodyMedium
            )
            
            // Alternative routes if available
            if (prediction.alternativeRoutes.isNotEmpty()) {
                Text(
                    text = "${prediction.alternativeRoutes.size} alternative route(s) available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Traffic congestion legend showing all congestion levels.
 */
@Composable
fun TrafficCongestionLegend(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Traffic Legend",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            CongestionLevel.values().forEach { level ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrafficCongestionIndicator(congestionLevel = level)
                    Text(
                        text = level.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = getCongestionDescription(level),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Traffic flow visualization for a route segment.
 */
@Composable
fun TrafficFlowVisualization(
    visualization: TrafficVisualizationModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(color = Color(android.graphics.Color.parseColor(visualization.colorHex))),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = visualization.tooltipText,
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontSize = 10.sp
        )
        
        // Confidence indicator
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val confidenceWidth = size.width * visualization.confidence.toFloat()
            drawRect(
                color = Color.White.copy(alpha = 0.2f),
                topLeft = Offset.Zero,
                size = size.copy(width = confidenceWidth)
            )
            
            // Border
            drawRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = Offset.Zero,
                size = size,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Traffic insights panel showing learned patterns.
 */
@Composable
fun TrafficInsightsPanel(
    insights: List<TrafficInsight>,
    modifier: Modifier = Modifier
) {
    if (insights.isEmpty()) return
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Traffic Insights",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            
            insights.forEachIndexed { index, insight ->
                TrafficInsightItem(insight = insight)
                if (index < insights.size - 1) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

/**
 * Individual traffic insight item.
 */
@Composable
private fun TrafficInsightItem(
    insight: TrafficInsight,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${(insight.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Text(
            text = insight.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        insight.recommendation?.let { recommendation ->
            Text(
                text = "💡 $recommendation",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Traffic pattern learning status indicator.
 */
@Composable
fun TrafficLearningStatus(
    isLearningEnabled: Boolean,
    recordsCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    color = if (isLearningEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
        )
        
        Text(
            text = if (isLearningEnabled) {
                "Learning traffic patterns ($recordsCount records)"
            } else {
                "Traffic learning disabled"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isLearningEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    }
}

// ========== HELPER FUNCTIONS ==========

private fun formatTravelTime(seconds: Double): String {
    return when {
        seconds < 60 -> "${seconds.toInt()}s"
        seconds < 3600 -> "${(seconds / 60).toInt()}min"
        else -> "${(seconds / 3600).toInt()}h ${((seconds % 3600) / 60).toInt()}min"
    }
}

private fun getCongestionDescription(level: CongestionLevel): String {
    return when (level) {
        CongestionLevel.FREE_FLOW -> "> 90% of free flow"
        CongestionLevel.LIGHT -> "70-90% of free flow"
        CongestionLevel.MODERATE -> "50-70% of free flow"
        CongestionLevel.HEAVY -> "30-50% of free flow"
        CongestionLevel.SEVERE -> "< 30% of free flow"
    }
}