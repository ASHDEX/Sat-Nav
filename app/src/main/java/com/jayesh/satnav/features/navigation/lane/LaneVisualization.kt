package com.jayesh.satnav.features.navigation.lane

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jayesh.satnav.domain.model.LaneInfo

/**
 * Visual representation of lane guidance.
 * Shows lanes with their directions (straight, left, right) and highlights recommended lane.
 */
@Composable
fun LaneVisualization(
    laneInfo: LaneInfo,
    modifier: Modifier = Modifier,
    showRecommended: Boolean = true
) {
    val totalLanes = laneInfo.totalLanes
    if (totalLanes <= 0) return
    
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .width((totalLanes * 40).dp)
                .height(60.dp)
        ) {
            val laneWidth = size.width / totalLanes
            val laneHeight = size.height
            
            // Draw each lane
            for (i in 0 until totalLanes) {
                val isRecommended = showRecommended && laneInfo.recommendedLane == i
                val laneType = getLaneType(i, laneInfo)
                
                drawLane(
                    index = i,
                    totalLanes = totalLanes,
                    laneWidth = laneWidth,
                    laneHeight = laneHeight,
                    laneType = laneType,
                    isRecommended = isRecommended
                )
            }
            
            // Draw lane separators
            for (i in 1 until totalLanes) {
                val x = i * laneWidth
                drawLine(
                    color = Color.Gray.copy(alpha = 0.5f),
                    start = Offset(x, 0f),
                    end = Offset(x, laneHeight),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

private fun DrawScope.drawLane(
    index: Int,
    totalLanes: Int,
    laneWidth: Float,
    laneHeight: Float,
    laneType: LaneType,
    isRecommended: Boolean
) {
    val x = index * laneWidth
    val centerX = x + laneWidth / 2
    val centerY = laneHeight / 2
    
    // Lane background
    val backgroundColor = when {
        isRecommended -> Color(0xFF4CAF50).copy(alpha = 0.2f) // Green highlight for recommended
        else -> Color.LightGray.copy(alpha = 0.1f)
    }
    
    drawRect(
        color = backgroundColor,
        topLeft = Offset(x, 0f),
        size = Size(laneWidth, laneHeight),
        style = Fill
    )
    
    // Lane border
    if (isRecommended) {
        drawRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(x, 0f),
            size = Size(laneWidth, laneHeight),
            style = Stroke(width = 2.dp.toPx())
        )
    }
    
    // Lane direction arrows
    when (laneType) {
        LaneType.STRAIGHT -> drawStraightArrow(centerX, centerY, laneWidth)
        LaneType.LEFT -> drawLeftArrow(centerX, centerY, laneWidth)
        LaneType.RIGHT -> drawRightArrow(centerX, centerY, laneWidth)
        LaneType.LEFT_STRAIGHT -> drawLeftStraightArrow(centerX, centerY, laneWidth)
        LaneType.RIGHT_STRAIGHT -> drawRightStraightArrow(centerX, centerY, laneWidth)
        LaneType.LEFT_RIGHT -> drawLeftRightArrow(centerX, centerY, laneWidth)
        LaneType.ALL -> drawAllDirectionsArrow(centerX, centerY, laneWidth)
    }
    
    // Lane number
    drawLaneNumber(index + 1, centerX, laneHeight - 10, isRecommended)
}

private fun DrawScope.drawStraightArrow(centerX: Float, centerY: Float, laneWidth: Float) {
    val arrowSize = laneWidth * 0.3f
    val arrowColor = Color.Black
    
    // Arrow line
    drawLine(
        color = arrowColor,
        start = Offset(centerX, centerY - arrowSize),
        end = Offset(centerX, centerY + arrowSize),
        strokeWidth = 2.dp.toPx()
    )
    
    // Arrow head
    drawTriangle(
        center = Offset(centerX, centerY + arrowSize),
        size = arrowSize * 0.4f,
        direction = ArrowDirection.DOWN,
        color = arrowColor
    )
}

private fun DrawScope.drawLeftArrow(centerX: Float, centerY: Float, laneWidth: Float) {
    val arrowSize = laneWidth * 0.3f
    val arrowColor = Color.Black
    
    // Arrow line (curved left)
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(centerX + arrowSize * 0.5f, centerY)
        quadraticBezierTo(
            centerX, centerY - arrowSize * 0.5f,
            centerX - arrowSize * 0.5f, centerY
        )
    }
    
    drawPath(
        path = path,
        color = arrowColor,
        style = Stroke(width = 2.dp.toPx())
    )
    
    // Arrow head
    drawTriangle(
        center = Offset(centerX - arrowSize * 0.5f, centerY),
        size = arrowSize * 0.4f,
        direction = ArrowDirection.LEFT,
        color = arrowColor
    )
}

private fun DrawScope.drawRightArrow(centerX: Float, centerY: Float, laneWidth: Float) {
    val arrowSize = laneWidth * 0.3f
    val arrowColor = Color.Black
    
    // Arrow line (curved right)
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(centerX - arrowSize * 0.5f, centerY)
        quadraticBezierTo(
            centerX, centerY - arrowSize * 0.5f,
            centerX + arrowSize * 0.5f, centerY
        )
    }
    
    drawPath(
        path = path,
        color = arrowColor,
        style = Stroke(width = 2.dp.toPx())
    )
    
    // Arrow head
    drawTriangle(
        center = Offset(centerX + arrowSize * 0.5f, centerY),
        size = arrowSize * 0.4f,
        direction = ArrowDirection.RIGHT,
        color = arrowColor
    )
}

private fun DrawScope.drawLeftStraightArrow(centerX: Float, centerY: Float, laneWidth: Float) {
    val arrowSize = laneWidth * 0.25f
    val arrowColor = Color.Black
    
    // Left arrow
    val leftPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(centerX, centerY - arrowSize)
        lineTo(centerX - arrowSize, centerY)
        moveTo(centerX - arrowSize, centerY)
        lineTo(centerX, centerY + arrowSize)
    }
    
    drawPath(
        path = leftPath,
        color = arrowColor,
        style = Stroke(width = 1.5.dp.toPx())
    )
    
    // Straight arrow
    drawLine(
        color = arrowColor,
        start = Offset(centerX, centerY - arrowSize),
        end = Offset(centerX, centerY + arrowSize),
        strokeWidth = 1.5.dp.toPx()
    )
    
    // Arrow heads
    drawTriangle(
        center = Offset(centerX - arrowSize, centerY),
        size = arrowSize * 0.3f,
        direction = ArrowDirection.LEFT,
        color = arrowColor
    )
    drawTriangle(
        center = Offset(centerX, centerY + arrowSize),
        size = arrowSize * 0.3f,
        direction = ArrowDirection.DOWN,
        color = arrowColor
    )
}

private fun DrawScope.drawRightStraightArrow(centerX: Float, centerY: Float, laneWidth: Float) {
    val arrowSize = laneWidth * 0.25f
    val arrowColor = Color.Black
    
    // Right arrow
    val rightPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(centerX, centerY - arrowSize)
        lineTo(centerX + arrowSize, centerY)
        moveTo(centerX + arrowSize, centerY)
        lineTo(centerX, centerY + arrowSize)
    }
    
    drawPath(
        path = rightPath,
        color = arrowColor,
        style = Stroke(width = 1.5.dp.toPx())
    )
    
    // Straight arrow
    drawLine(
        color = arrowColor,
        start = Offset(centerX, centerY - arrowSize),
        end = Offset(centerX, centerY + arrowSize),
        strokeWidth = 1.5.dp.toPx()
    )
    
    // Arrow heads
    drawTriangle(
        center = Offset(centerX + arrowSize, centerY),
        size = arrowSize * 0.3f,
        direction = ArrowDirection.RIGHT,
        color = arrowColor
    )
    drawTriangle(
        center = Offset(centerX, centerY + arrowSize),
        size = arrowSize * 0.3f,
        direction = ArrowDirection.DOWN,
        color = arrowColor
    )
}

private fun DrawScope.drawLeftRightArrow(centerX: Float, centerY: Float, laneWidth: Float) {
    val arrowSize = laneWidth * 0.25f
    val arrowColor = Color.Black
    
    // Left arrow
    val leftPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(centerX - arrowSize, centerY - arrowSize * 0.5f)
        lineTo(centerX - arrowSize * 1.5f, centerY)
        moveTo(centerX - arrowSize * 1.5f, centerY)
        lineTo(centerX - arrowSize, centerY + arrowSize * 0.5f)
    }
    
    // Right arrow
    val rightPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(centerX + arrowSize, centerY - arrowSize * 0.5f)
        lineTo(centerX + arrowSize * 1.5f, centerY)
        moveTo(centerX + arrowSize * 1.5f, centerY)
        lineTo(centerX + arrowSize, centerY + arrowSize * 0.5f)
    }
    
    drawPath(
        path = leftPath,
        color = arrowColor,
        style = Stroke(width = 1.5.dp.toPx())
    )
    
    drawPath(
        path = rightPath,
        color = arrowColor,
        style = Stroke(width = 1.5.dp.toPx())
    )
    
    // Arrow heads
    drawTriangle(
        center = Offset(centerX - arrowSize * 1.5f, centerY),
        size = arrowSize * 0.3f,
        direction = ArrowDirection.LEFT,
        color = arrowColor
    )
    drawTriangle(
        center = Offset(centerX + arrowSize * 1.5f, centerY),
        size = arrowSize * 0.3f,
        direction = ArrowDirection.RIGHT,
        color = arrowColor
    )
}

private fun DrawScope.drawAllDirectionsArrow(centerX: Float, centerY: Float, laneWidth: Float) {
    val arrowSize = laneWidth * 0.2f
    val arrowColor = Color.Black
    
    // Draw small arrows in all directions
    drawTriangle(
        center = Offset(centerX, centerY - arrowSize),
        size = arrowSize * 0.4f,
        direction = ArrowDirection.UP,
        color = arrowColor
    )
    drawTriangle(
        center = Offset(centerX, centerY + arrowSize),
        size = arrowSize * 0.4f,
        direction = ArrowDirection.DOWN,
        color = arrowColor
    )
    drawTriangle(
        center = Offset(centerX - arrowSize, centerY),
        size = arrowSize * 0.4f,
        direction = ArrowDirection.LEFT,
        color = arrowColor
    )
    drawTriangle(
        center = Offset(centerX + arrowSize, centerY),
        size = arrowSize * 0.4f,
        direction = ArrowDirection.RIGHT,
        color = arrowColor
    )
}

private fun DrawScope.drawLaneNumber(
    number: Int,
    centerX: Float,
    bottomY: Float,
    isRecommended: Boolean
) {
    val textColor = if (isRecommended) Color(0xFF4CAF50) else Color.Gray
    val textSize = 10.dp.toPx()
    
    // Simple number drawing (in a real app, you'd use drawText with proper text rendering)
    // For simplicity, we'll draw a circle with the number
    drawCircle(
        color = textColor.copy(alpha = 0.2f),
        center = Offset(centerX, bottomY),
        radius = textSize
    )
    
    // In a real implementation, you would use:
    // drawText(
    //     text = number.toString(),
    //     color = textColor,
    //     fontSize = textSize,
    //     textAlign = TextAlign.Center,
    //     ...
    // )
}

private fun DrawScope.drawTriangle(
    center: Offset,
    size: Float,
    direction: ArrowDirection,
    color: Color
) {
    val halfSize = size / 2
    
    val points = when (direction) {
        ArrowDirection.UP -> listOf(
            Offset(center.x, center.y - halfSize),
            Offset(center.x - halfSize, center.y + halfSize),
            Offset(center.x + halfSize, center.y + halfSize)
        )
        ArrowDirection.DOWN -> listOf(
            Offset(center.x, center.y + halfSize),
            Offset(center.x - halfSize, center.y - halfSize),
            Offset(center.x + halfSize, center.y - halfSize)
        )
        ArrowDirection.LEFT -> listOf(
            Offset(center.x - halfSize, center.y),
            Offset(center.x + halfSize, center.y - halfSize),
            Offset(center.x + halfSize, center.y + halfSize)
        )
        ArrowDirection.RIGHT -> listOf(
            Offset(center.x + halfSize, center.y),
            Offset(center.x - halfSize, center.y - halfSize),
            Offset(center.x - halfSize, center.y + halfSize)
        )
    }
    
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { point ->
            lineTo(point.x, point.y)
        }
        close()
    }
    
    drawPath(
        path = path,
        color = color,
        style = Fill
    )
}

private fun getLaneType(index: Int, laneInfo: LaneInfo): LaneType {
    val totalLanes = laneInfo.totalLanes
    
    // Determine which lanes are for which direction based on typical road layout
    // Left lanes are for left turns, right lanes are for right turns, middle lanes are straight
    val leftTurnStart = totalLanes - laneInfo.leftTurnLanes
    val rightTurnEnd = laneInfo.rightTurnLanes
    
    return when {
        index < rightTurnEnd && index >= leftTurnStart -> {
            // This lane can be both left and right (unusual but possible)
            LaneType.LEFT_RIGHT
        }
        index < rightTurnEnd -> {
            // Right turn lane
            LaneType.RIGHT
        }
        index >= leftTurnStart -> {
            // Left turn lane
            LaneType.LEFT
        }
        laneInfo.straightLanes > 0 && index >= rightTurnEnd && index < leftTurnStart -> {
            // Straight lane, but check if it also allows turns
            when {
                index == rightTurnEnd && laneInfo.rightTurnLanes > 0 -> LaneType.RIGHT_STRAIGHT
                index == leftTurnStart - 1 && laneInfo.leftTurnLanes > 0 -> LaneType.LEFT_STRAIGHT
                else -> LaneType.STRAIGHT
            }
        }
        else -> LaneType.STRAIGHT
    }
}

private enum class LaneType {
    STRAIGHT,
    LEFT,
    RIGHT,
    LEFT_STRAIGHT,
    RIGHT_STRAIGHT,
    LEFT_RIGHT,
    ALL
}

private enum class ArrowDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}