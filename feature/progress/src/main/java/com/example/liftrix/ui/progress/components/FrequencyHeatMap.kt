package com.example.liftrix.ui.progress.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.ui.common.analytics.ChartThemeProvider
import com.example.liftrix.ui.common.analytics.HeatMapColorScheme
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.max
import kotlin.math.min

/**
 * Frequency heatmap component for workout frequency pattern visualization.
 * 
 * Displays workout frequency patterns in a GitHub-style heatmap format using
 * LiftrixColors theming with Material 3 design principles. Supports interactive
 * date selection and accessibility features.
 * 
 * Features:
 * - GitHub-style heatmap visualization
 * - Color intensity based on workout frequency
 * - Interactive date selection with callbacks
 * - Accessibility-compliant content descriptions
 * - Smooth animations and transitions
 * - Material 3 design system integration
 */
@Composable
fun FrequencyHeatMap(
    data: Map<LocalDate, Int>,
    modifier: Modifier = Modifier,
    title: String = "Workout Frequency",
    maxValue: Int = 5,
    onDateClick: (LocalDate, Int) -> Unit = { _, _ -> },
    contentDescription: String? = null
) {
    val colorScheme = ChartThemeProvider.createHeatMapColorScheme()
    val accessibilityConfig = ChartThemeProvider.createAccessibilityConfig()
    
    // Generate date range for display (last 12 weeks)
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val startDate = today.minus(days(84)) // 12 weeks ago
    val dateRange = generateDateRange(startDate, today)
    
    // Calculate statistics
    val totalWorkouts = data.values.sum()
    val activeDays = data.filter { it.value > 0 }.size
    val averageFrequency = if (data.isNotEmpty()) data.values.average() else 0.0
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = contentDescription ?: buildContentDescription(
                    totalWorkouts, activeDays, averageFrequency
                )
            }
    ) {
        // Header with title and statistics
        HeatMapHeader(
            title = title,
            totalWorkouts = totalWorkouts,
            activeDays = activeDays,
            averageFrequency = averageFrequency
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Day labels row
        DayLabelsRow()
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Heatmap grid
        HeatMapGrid(
            data = data,
            dateRange = dateRange,
            maxValue = maxValue,
            colorScheme = colorScheme,
            onDateClick = onDateClick
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Color intensity legend
        IntensityLegend(
            colorScheme = colorScheme,
            maxValue = maxValue
        )
    }
}

@Composable
private fun HeatMapHeader(
    title: String,
    totalWorkouts: Int,
    activeDays: Int,
    averageFrequency: Double
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatisticItem(
                label = "Total Workouts",
                value = totalWorkouts.toString(),
                modifier = Modifier.weight(1f)
            )
            
            StatisticItem(
                label = "Active Days",
                value = activeDays.toString(),
                modifier = Modifier.weight(1f)
            )
            
            StatisticItem(
                label = "Daily Average",
                value = String.format("%.1f", averageFrequency),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = LiftrixColors.Primary
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DayLabelsRow() {
    val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(dayLabels) { day ->
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.take(1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp  // Updated to meet 14sp minimum requirement
                )
            }
        }
    }
}

@Composable
private fun HeatMapGrid(
    data: Map<LocalDate, Int>,
    dateRange: List<LocalDate>,
    maxValue: Int,
    colorScheme: HeatMapColorScheme,
    onDateClick: (LocalDate, Int) -> Unit
) {
    val chunkedDates = dateRange.chunked(7) // Group by weeks
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        chunkedDates.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                week.forEach { date ->
                    val workoutCount = data[date] ?: 0
                    val intensity = if (maxValue > 0) workoutCount.toFloat() / maxValue else 0f
                    
                    HeatMapCell(
                        date = date,
                        workoutCount = workoutCount,
                        intensity = intensity,
                        colorScheme = colorScheme,
                        onClick = { onDateClick(date, workoutCount) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatMapCell(
    date: LocalDate,
    workoutCount: Int,
    intensity: Float,
    colorScheme: HeatMapColorScheme,
    onClick: () -> Unit
) {
    val animatedIntensity by animateFloatAsState(
        targetValue = intensity,
        animationSpec = tween(durationMillis = 300),
        label = "cell_intensity"
    )
    
    val cellColor = remember(animatedIntensity) {
        when {
            animatedIntensity == 0f -> colorScheme.noDataColor
            animatedIntensity <= 0.25f -> colorScheme.lowIntensityColor
            animatedIntensity <= 0.5f -> colorScheme.mediumIntensityColor
            animatedIntensity <= 0.75f -> colorScheme.highIntensityColor
            else -> colorScheme.highIntensityColor
        }
    }
    
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(cellColor)
            .clickable { onClick() }
            .semantics {
                contentDescription = buildCellContentDescription(date, workoutCount)
            }
    ) {
        // Optional: Add subtle border for better visibility
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                color = colorScheme.strokeColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx())
            )
        }
    }
}

@Composable
private fun IntensityLegend(
    colorScheme: HeatMapColorScheme,
    maxValue: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Workout Intensity",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Less",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Legend colors
            LegendColor(color = colorScheme.noDataColor, label = "0")
            LegendColor(color = colorScheme.lowIntensityColor, label = "1-${maxValue/4}")
            LegendColor(color = colorScheme.mediumIntensityColor, label = "${maxValue/4 + 1}-${maxValue/2}")
            LegendColor(color = colorScheme.highIntensityColor, label = "${maxValue/2 + 1}+")
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "More",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun LegendColor(
    color: Color,
    label: String
) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
            .semantics {
                contentDescription = "Intensity level: $label workouts"
            }
    )
}

private fun generateDateRange(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
    val dates = mutableListOf<LocalDate>()
    var currentDate = startDate
    
    while (currentDate <= endDate) {
        dates.add(currentDate)
        currentDate = currentDate.plus(days(1))
    }
    
    return dates
}

private fun buildContentDescription(
    totalWorkouts: Int,
    activeDays: Int,
    averageFrequency: Double
): String {
    return buildString {
        append("Workout frequency heatmap. ")
        append("Total workouts: $totalWorkouts. ")
        append("Active days: $activeDays. ")
        append("Daily average: ${String.format("%.1f", averageFrequency)} workouts.")
    }
}

private fun buildCellContentDescription(date: LocalDate, workoutCount: Int): String {
    return when (workoutCount) {
        0 -> "No workouts on $date"
        1 -> "1 workout on $date"
        else -> "$workoutCount workouts on $date"
    }
}

@Preview(showBackground = true)
@Composable
private fun FrequencyHeatMapPreview() {
    LiftrixTheme {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val sampleData = mapOf(
            today to 2,
            today.minus(days(1)) to 1,
            today.minus(days(2)) to 3,
            today.minus(days(5)) to 1,
            today.minus(days(7)) to 2,
            today.minus(days(10)) to 1,
            today.minus(days(14)) to 4,
            today.minus(days(16)) to 2,
            today.minus(days(21)) to 1,
            today.minus(days(28)) to 3
        )
        
        FrequencyHeatMap(
            data = sampleData,
            modifier = Modifier.padding(16.dp),
            onDateClick = { date, count ->
                println("Clicked: $date with $count workouts")
            }
        )
    }
}

/**
 * Extension function to create days duration
 */
private fun days(days: Int): kotlinx.datetime.DatePeriod {
    return kotlinx.datetime.DatePeriod(days = days)
}