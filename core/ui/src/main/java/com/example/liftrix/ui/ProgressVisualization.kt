package com.example.liftrix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.components.animations.AnimatedProgressRingWithGradient
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.PrimaryGradient

/**
 * Line chart for progress visualization with teal-indigo gradient
 * 
 * @param data List of progress data points
 * @param modifier Modifier for styling the chart
 * @param strokeWidth Width of the line stroke
 */
@Composable
fun LineChartProgress(
    data: List<ProgressDataPoint>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp
) {
    val tealIndigoGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF20C9B7), // Teal
                Color(0xFF2A3B7D)  // Indigo
            )
        )
    }
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        if (data.size < 2) return@Canvas
        
        val path = Path()
        val maxValue = data.maxOfOrNull { it.value } ?: 1f
        val minValue = data.minOfOrNull { it.value } ?: 0f
        val valueRange = maxValue - minValue
        
        // Calculate points
        val points = data.mapIndexed { index, point ->
            val x = (index.toFloat() / (data.size - 1)) * size.width
            val y = size.height - ((point.value - minValue) / valueRange) * size.height
            Offset(x, y)
        }
        
        // Draw the line path
        path.moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        
        // Draw the line with gradient
        drawPath(
            path = path,
            brush = tealIndigoGradient,
            style = Stroke(width = strokeWidth.toPx())
        )
        
        // Draw data points
        points.forEach { point ->
            drawCircle(
                brush = tealIndigoGradient,
                radius = strokeWidth.toPx() * 1.5f,
                center = point
            )
        }
    }
}

/**
 * Data point for progress line chart
 */
data class ProgressDataPoint(
    val value: Float,
    val timestamp: Long = System.currentTimeMillis()
)

