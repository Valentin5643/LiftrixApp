package com.example.liftrix.ui.progress.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.liftrix.ui.theme.LiftrixColorsV2
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.ui.common.ChartGradients
import com.example.liftrix.ui.theme.LiftrixChartStyle
import com.example.liftrix.ui.theme.ChartColorsV2
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * MuscleGroupPieChart - Interactive pie chart for muscle group volume distribution
 *
 * Features:
 * - Smooth arc animations with customizable duration
 * - Interactive tap detection with haptic feedback
 * - Percentage labels with smart positioning
 * - Material 3 design system integration
 * - Accessible with detailed content descriptions
 * - Mobile-optimized touch targets (44dp minimum)
 * - Persian Green/Tiffany Blue color system compliance
 */
@Composable
fun MuscleGroupPieChart(
    data: Map<MuscleGroup, Float>,
    onSliceClick: ((MuscleGroup) -> Unit)? = null,
    modifier: Modifier = Modifier,
    showPercentages: Boolean = true,
    showLegend: Boolean = true,
    animationDuration: Int = 300
) {
    var selectedSlice by remember { mutableStateOf<MuscleGroup?>(null) }
    val haptic = LocalHapticFeedback.current
    
    // Animation for pie chart appearance
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.animateTo(1f, animationSpec = tween(animationDuration, easing = LinearEasing))
    }

    // Calculate pie chart data
    val pieData by remember(data) {
        derivedStateOf {
            calculatePieChartData(data)
        }
    }

    Box(
        modifier = modifier.semantics {
            contentDescription = buildPieChartContentDescription(data)
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Chart title
            Text(
                text = "Muscle Group Distribution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (pieData.isNotEmpty()) {
                Row {
                    // Pie chart
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val textMeasurer = rememberTextMeasurer()
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(pieData) {
                                    detectTapGestures { offset ->
                                        val tappedSlice = findTappedSlice(
                                            offset, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()), pieData
                                        )
                                        tappedSlice?.let { slice ->
                                            selectedSlice = slice.muscleGroup
                                            onSliceClick?.invoke(slice.muscleGroup)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                }
                        ) {
                            drawPieChart(
                                pieData = pieData,
                                animationProgress = animationProgress.value,
                                selectedSlice = selectedSlice,
                                showPercentages = showPercentages,
                                textMeasurer = textMeasurer
                            )
                        }
                    }
                    
                    // Legend
                    if (showLegend) {
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        PieChartLegend(
                            pieData = pieData,
                            selectedSlice = selectedSlice,
                            onSliceClick = { muscleGroup ->
                                selectedSlice = muscleGroup
                                onSliceClick?.invoke(muscleGroup)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                EmptyPieChartState()
            }
            
            // Selected slice details
            selectedSlice?.let { selected ->
                Spacer(modifier = Modifier.height(12.dp))
                SelectedSliceDetails(
                    muscleGroup = selected,
                    percentage = pieData.find { it.muscleGroup == selected }?.percentage ?: 0f,
                    onDismiss = { selectedSlice = null }
                )
            }
        }
    }
}

/**
 * Draw the interactive pie chart with modern styling
 */
private fun DrawScope.drawPieChart(
    pieData: List<PieSliceData>,
    animationProgress: Float,
    selectedSlice: MuscleGroup?,
    showPercentages: Boolean,
    textMeasurer: TextMeasurer
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val baseRadius = (size.minDimension / 2f) * 0.75f
    val strokeWidth = 3.dp.toPx()
    
    // Draw subtle background circle for better visual hierarchy
    drawCircle(
        color = ChartColorsV2.Infrastructure.getGridColor(true),
        radius = baseRadius * 1.1f,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
    )
    
    var currentAngle = -90f // Start from top
    
    pieData.forEach { slice ->
        val sweepAngle = slice.sweepAngle * animationProgress
        val isSelected = slice.muscleGroup == selectedSlice
        
        // Enhanced selection effect with proper visual hierarchy
        val sliceRadius = if (isSelected) baseRadius * 1.08f else baseRadius
        val elevationOffset = if (isSelected) 12f else 4f
        val sliceCenter = if (isSelected) {
            val midAngle = Math.toRadians((currentAngle + sweepAngle / 2).toDouble())
            Offset(
                center.x + cos(midAngle).toFloat() * elevationOffset,
                center.y + sin(midAngle).toFloat() * elevationOffset
            )
        } else center
        
        // Draw subtle shadow for depth (only for selected slice)
        if (isSelected) {
            drawArc(
                color = Color(0xFF2A2A2A),
                startAngle = currentAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(
                    sliceCenter.x - sliceRadius + 2f,
                    sliceCenter.y - sliceRadius + 2f
                ),
                size = Size(sliceRadius * 2f, sliceRadius * 2f)
            )
        }
        
        // Draw main slice with enhanced color
        val sliceColor = if (isSelected) {
            slice.color.copy(alpha = 1.0f)
        } else {
            slice.color.copy(alpha = 0.9f)
        }
        
        drawArc(
            color = sliceColor,
            startAngle = currentAngle,
            sweepAngle = sweepAngle,
            useCenter = true,
            topLeft = Offset(
                sliceCenter.x - sliceRadius,
                sliceCenter.y - sliceRadius
            ),
            size = Size(sliceRadius * 2f, sliceRadius * 2f)
        )
        
        // Draw refined border for definition with better contrast
        drawArc(
            color = Color.White.copy(alpha = 0.6f),
            startAngle = currentAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(
                sliceCenter.x - sliceRadius,
                sliceCenter.y - sliceRadius
            ),
            size = Size(sliceRadius * 2f, sliceRadius * 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
        
        // Enhanced percentage labels with better positioning
        if (showPercentages && slice.percentage >= 5f && animationProgress > 0.7f) {
            val labelAngle = Math.toRadians((currentAngle + sweepAngle / 2).toDouble())
            val labelRadius = sliceRadius * 0.78f
            val labelX = sliceCenter.x + cos(labelAngle).toFloat() * labelRadius
            val labelY = sliceCenter.y + sin(labelAngle).toFloat() * labelRadius
            
            // Draw label background for better contrast
            drawCircle(
                color = Color(0xFF888888),
                radius = 18.dp.toPx(),
                center = Offset(labelX, labelY)
            )
            
            val textResult = textMeasurer.measure(
                text = "${slice.percentage.toInt()}%",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(
                    labelX - textResult.size.width / 2f,
                    labelY - textResult.size.height / 2f
                )
            )
        }
        
        currentAngle += sweepAngle
    }
    
    // Draw center highlight for modern look with better contrast
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = baseRadius * 0.15f,
        center = center
    )
    drawCircle(
        color = LiftrixColorsV2.Teal,
        radius = baseRadius * 0.12f,
        center = center
    )
}

/**
 * Pie chart legend component
 */
@Composable
private fun PieChartLegend(
    pieData: List<PieSliceData>,
    selectedSlice: MuscleGroup?,
    onSliceClick: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(pieData.sortedByDescending { it.percentage }) { slice ->
            LegendItem(
                slice = slice,
                isSelected = slice.muscleGroup == selectedSlice,
                onClick = { onSliceClick(slice.muscleGroup) }
            )
        }
    }
}

/**
 * Individual legend item with enhanced styling
 */
@Composable
private fun LegendItem(
    slice: PieSliceData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = if (isSelected) {
            slice.color.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Enhanced color indicator with border
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            slice.color, 
                            RoundedCornerShape(4.dp)
                        )
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.White.copy(alpha = 0.4f),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }
                
                Text(
                    text = slice.muscleGroup.displayName,
                    style = if (isSelected) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = if (isSelected) {
                        slice.color
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            
            Text(
                text = "${slice.percentage.toInt()}%",
                style = if (isSelected) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelSmall
                },
                color = if (isSelected) {
                    slice.color
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                },
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/**
 * Selected slice details
 */
@Composable
private fun SelectedSliceDetails(
    muscleGroup: MuscleGroup,
    percentage: Float,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(muscleGroup.color.copy(alpha = 0.08f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = muscleGroup.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = muscleGroup.color
                )
                Text(
                    text = "${percentage.toInt()}% of total volume",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Empty state for pie chart
 */
@Composable
private fun EmptyPieChartState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🥧",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "No muscle group data",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Track workouts to see distribution",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Find which slice was tapped
 */
private fun findTappedSlice(
    tapOffset: Offset,
    canvasSize: androidx.compose.ui.geometry.Size,
    pieData: List<PieSliceData>
): PieSliceData? {
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val radius = (canvasSize.minDimension / 2f) * 0.8f
    
    // Check if tap is within circle
    val distance = sqrt(
        (tapOffset.x - center.x).let { it * it } + 
        (tapOffset.y - center.y).let { it * it }
    )
    if (distance > radius) return null
    
    // Calculate angle of tap
    val angle = Math.toDegrees(
        atan2(
            (tapOffset.y - center.y).toDouble(),
            (tapOffset.x - center.x).toDouble()
        )
    ).toFloat()
    
    // Normalize angle to 0-360 starting from top
    val normalizedAngle = ((angle + 90f) % 360f + 360f) % 360f
    
    // Find which slice contains this angle
    var currentAngle = 0f
    pieData.forEach { slice ->
        if (normalizedAngle >= currentAngle && normalizedAngle < currentAngle + slice.sweepAngle) {
            return slice
        }
        currentAngle += slice.sweepAngle
    }
    
    return null
}

/**
 * Calculate pie chart slice data from input
 */
private fun calculatePieChartData(data: Map<MuscleGroup, Float>): List<PieSliceData> {
    if (data.isEmpty()) return emptyList()
    
    val total = data.values.sum()
    if (total == 0f) return emptyList()
    
    return data.map { (muscleGroup, value) ->
        val percentage = (value / total) * 100f
        val sweepAngle = (value / total) * 360f
        
        PieSliceData(
            muscleGroup = muscleGroup,
            value = value,
            percentage = percentage,
            sweepAngle = sweepAngle,
            color = muscleGroup.color
        )
    }.filter { it.percentage > 0.5f } // Filter out very small slices
}

/**
 * Data class for pie chart slices
 */
private data class PieSliceData(
    val muscleGroup: MuscleGroup,
    val value: Float,
    val percentage: Float,
    val sweepAngle: Float,
    val color: Color
)

/**
 * Muscle group enum with display properties
 */
enum class MuscleGroup(
    val displayName: String,
    val colorIndex: Int
) {
    CHEST("Chest", 0),
    BACK("Back", 1),
    SHOULDERS("Shoulders", 2),
    ARMS("Arms", 3),
    LEGS("Legs", 4),
    CORE("Core", 5),
    CARDIO("Cardio", 6),
    OTHER("Other", 7);
    
    val color: Color get() = ChartColorsV2.getSeriesColor(colorIndex)
}

/**
 * Build accessibility content description
 */
private fun buildPieChartContentDescription(data: Map<MuscleGroup, Float>): String {
    return buildString {
        append("Muscle group distribution pie chart. ")
        if (data.isNotEmpty()) {
            val total = data.values.sum()
            append("${data.size} muscle groups. ")
            data.entries.sortedByDescending { it.value }.forEach { (group, value) ->
                val percentage = (value / total) * 100f
                append("${group.displayName}: ${percentage.toInt()}%. ")
            }
        } else {
            append("No data available.")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MuscleGroupPieChartPreview() {
    LiftrixTheme {
        val sampleData = mapOf(
            MuscleGroup.CHEST to 2500f,
            MuscleGroup.BACK to 3200f,
            MuscleGroup.SHOULDERS to 1800f,
            MuscleGroup.ARMS to 2100f,
            MuscleGroup.LEGS to 4500f,
            MuscleGroup.CORE to 800f
        )
        
        MuscleGroupPieChart(
            data = sampleData,
            modifier = Modifier.padding(16.dp)
        )
    }
}