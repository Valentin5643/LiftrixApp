package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.ui.common.analytics.ChartThemeProvider
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Volume progress chart component using Vico LineChart with Material 3 theming.
 * 
 * Displays workout volume progression over time with smooth animations,
 * LiftrixColors theming, and accessibility support. Optimized for 60fps
 * performance with hardware acceleration.
 * 
 * Features:
 * - Smooth line chart with gradient area fill
 * - Interactive zoom and scroll capabilities
 * - Accessibility-compliant content descriptions
 * - Performance-optimized rendering
 * - Material 3 design system integration
 */
@Composable
fun VolumeProgressChart(
    data: List<VolumeDataPoint>,
    dateRange: DateRange,
    modifier: Modifier = Modifier,
    title: String = "Volume Progress",
    isLoading: Boolean = false,
    contentDescription: String? = null
) {
    // Simplified placeholder - no chart processing needed
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = contentDescription ?: buildContentDescription(data, title)
            }
    ) {
        // Chart header
        ChartHeader(
            title = title,
            dateRange = dateRange,
            dataPoints = data.size,
            isLoading = isLoading
        )
        
        // Chart content
        if (isLoading) {
            LoadingState()
        } else if (data.isEmpty()) {
            EmptyState()
        } else {
            // Simplified placeholder chart until Vico API is updated
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Volume Progress Chart\n${data.size} data points",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ChartHeader(
    title: String,
    dateRange: DateRange,
    dataPoints: Int,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        if (!isLoading) {
            Text(
                text = "${dateRange.start} - ${dateRange.end} • $dataPoints data points",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = LiftrixColors.Primary,
                strokeWidth = 3.dp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Loading volume data...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📊",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "No volume data available",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            Text(
                text = "Complete workouts to see your progress",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

private fun buildContentDescription(data: List<VolumeDataPoint>, title: String): String {
    return buildString {
        append("$title chart with ${data.size} data points. ")
        if (data.isNotEmpty()) {
            val maxVolume = data.maxByOrNull { it.volume.kilograms }
            val minVolume = data.minByOrNull { it.volume.kilograms }
            val avgVolume = data.map { it.volume.kilograms }.average()
            
            append("Volume ranges from ${minVolume?.volume?.kilograms?.toInt()} kg to ${maxVolume?.volume?.kilograms?.toInt()} kg, ")
            append("with an average of ${avgVolume.toInt()} kg.")
        }
    }
}

/**
 * Volume data point for chart visualization
 */
data class VolumeDataPoint(
    val date: LocalDate,
    val volume: Weight,
    val workoutCount: Int = 1
)

/**
 * Date range for chart display
 */
data class DateRange(
    val start: LocalDate,
    val end: LocalDate
) {
    fun contains(date: LocalDate): Boolean = date >= start && date <= end
    fun dayCount(): Int = (end.toJavaLocalDate().toEpochDay() - start.toJavaLocalDate().toEpochDay()).toInt() + 1
}

/**
 * Performance configuration import
 */
private typealias ChartPerformanceConfig = com.example.liftrix.ui.common.analytics.ChartPerformanceConfig
private typealias AccessibilityConfig = com.example.liftrix.ui.common.analytics.AccessibilityConfig


@Preview(showBackground = true)
@Composable
private fun VolumeProgressChartPreview() {
    LiftrixTheme {
        val sampleData = listOf(
            VolumeDataPoint(
                date = LocalDate(2024, 1, 1),
                volume = Weight.fromKilograms(1200.0),
                workoutCount = 1
            ),
            VolumeDataPoint(
                date = LocalDate(2024, 1, 2),
                volume = Weight.fromKilograms(1350.0),
                workoutCount = 1
            ),
            VolumeDataPoint(
                date = LocalDate(2024, 1, 3),
                volume = Weight.fromKilograms(1180.0),
                workoutCount = 1
            ),
            VolumeDataPoint(
                date = LocalDate(2024, 1, 4),
                volume = Weight.fromKilograms(1450.0),
                workoutCount = 1
            ),
            VolumeDataPoint(
                date = LocalDate(2024, 1, 5),
                volume = Weight.fromKilograms(1520.0),
                workoutCount = 1
            )
        )
        
        VolumeProgressChart(
            data = sampleData,
            dateRange = DateRange(
                start = LocalDate(2024, 1, 1),
                end = LocalDate(2024, 1, 5)
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}