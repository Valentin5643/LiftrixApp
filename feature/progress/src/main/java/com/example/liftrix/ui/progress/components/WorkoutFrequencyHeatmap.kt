package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.theme.LiftrixColors
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Modern workout frequency heatmap component with enhanced accessibility.
 * 
 * Features professional calendar-style layout with brand colors, enhanced visual
 * hierarchy, and improved accessibility. Displays workout frequency in a modern
 * heatmap format with month navigation and intensity indicators.
 * 
 * Key improvements:
 * - LiftrixCard system with 24dp border radius
 * - Enhanced color coding with better accessibility
 * - Professional calendar layout with modern styling
 * - Improved visual hierarchy and spacing
 * - Better accessibility support with proper contrast ratios
 * 
 * @param data List of frequency data points to display
 * @param isLoading Whether the chart is currently loading data
 * @param modifier Modifier for styling the chart container
 */
@Composable
fun WorkoutFrequencyHeatmap(
    data: List<FrequencyDataPoint>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // DEBUG: Log component loading state
    LaunchedEffect(isLoading, data.size) {
        timber.log.Timber.d("🔍 FREQUENCY-COMPONENT-DEBUG: isLoading=$isLoading, data.size=${data.size}")
    }
    
    // FLASH FIX: Only show loading for genuine delays, not brief flashes
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        timber.log.Timber.d("🔍 FREQUENCY-COMPONENT-DEBUG: LaunchedEffect triggered - isLoading=$isLoading")
        if (isLoading) {
            // Delay showing loading to prevent flashes for quick loads
            delay(300) 
            showLoading = isLoading // Only show if still loading after 300ms
        } else {
            showLoading = false // Hide loading immediately when done
        }
    }
    
    LiftrixCard(
        modifier = modifier.fillMaxWidth(),
        contentDescription = "Workout frequency calendar heatmap with navigation"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
        ) {
            // Modern header with enhanced typography
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Frequency Calendar",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Daily workout activity patterns",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Frequency calendar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(GridSystem.iconMedium)
                )
            }
            
            when {
                showLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ModernLoadingState()
                    }
                }
                data.isEmpty() -> {
                    // Show zero-value chart instead of empty state
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
                    ) {
                        // Enhanced frequency calendar with zero data
                        ModernNavigableFrequencyCalendar(
                            data = getZeroFrequencyData(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Modern legend with improved accessibility
                        ModernHeatmapLegend(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                else -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
                    ) {
                        // Enhanced frequency calendar
                        ModernNavigableFrequencyCalendar(
                            data = data,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Modern legend with improved accessibility
                        ModernHeatmapLegend(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern loading state with enhanced visual design
 */
@Composable
private fun ModernLoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = "Loading frequency data...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Modern empty state with enhanced visual hierarchy
 */
@Composable
private fun ModernEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = "No frequency data",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(GridSystem.iconLarge)
        )
        Text(
            text = "No frequency data available",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Complete workouts regularly to see frequency patterns",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Enhanced navigable calendar with modern styling and improved accessibility
 */
@Composable
private fun ModernNavigableFrequencyCalendar(
    data: List<FrequencyDataPoint>,
    modifier: Modifier = Modifier
) {
    // Create a map of dates to workout counts for quick lookup
    val dataMap = remember(data) {
        data.associate { it.date to it.workoutCount }
    }
    
    // Calculate the maximum workout count for intensity scaling
    val maxWorkouts = remember(data) {
        data.maxOfOrNull { it.workoutCount } ?: 1
    }
    
    // Get available months from the data (last 3 months for navigation)
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val availableMonths = remember {
        (0..2).map { monthsBack ->
            today.minus(DatePeriod(months = monthsBack))
        }.reversed() // Show oldest to newest
    }
    
    // State for current month selection
    var currentMonthIndex by remember {
        mutableStateOf(availableMonths.size - 1) // Start with the most recent month
    }
    
    val currentDate = availableMonths[currentMonthIndex]
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
    ) {
        // Modern month navigation header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { 
                    if (currentMonthIndex > 0) currentMonthIndex--
                },
                enabled = currentMonthIndex > 0
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous month",
                    tint = if (currentMonthIndex > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                )
            }
            
            Text(
                text = "${currentDate.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${currentDate.year}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            
            IconButton(
                onClick = { 
                    if (currentMonthIndex < availableMonths.size - 1) currentMonthIndex++
                },
                enabled = currentMonthIndex < availableMonths.size - 1
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next month",
                    tint = if (currentMonthIndex < availableMonths.size - 1) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                )
            }
        }
        
        // Enhanced calendar grid
        ModernCalendarGrid(
            currentDate = currentDate,
            dataMap = dataMap,
            maxWorkouts = maxWorkouts,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Modern calendar grid with enhanced visual design and accessibility
 */
@Composable
private fun ModernCalendarGrid(
    currentDate: LocalDate,
    dataMap: Map<LocalDate, Int>,
    maxWorkouts: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
    ) {
        // Day headers with enhanced styling
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // Calendar days grid with improved spacing
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1),
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
        ) {
            // Generate calendar days for current month
            val daysInMonth = generateCalendarDays(currentDate)
            
            items(daysInMonth.size) { index ->
                val dayData = daysInMonth[index]
                ModernCalendarDay(
                    dayData = dayData,
                    workoutCount = dataMap[dayData.date] ?: 0,
                    maxWorkouts = maxWorkouts,
                    isCurrentMonth = dayData.isCurrentMonth
                )
            }
        }
    }
}

/**
 * Individual calendar day with modern styling and accessibility
 */
@Composable
private fun ModernCalendarDay(
    dayData: CalendarDayData,
    workoutCount: Int,
    maxWorkouts: Int,
    isCurrentMonth: Boolean
) {
    val intensity = if (maxWorkouts > 0) workoutCount.toFloat() / maxWorkouts else 0f
    val backgroundColor = getIntensityColor(intensity, isCurrentMonth)
    val textColor = if (intensity > 0.5f) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(GridSystem.cornerRadiusMedium))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (isCurrentMonth) {
            Text(
                text = dayData.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = if (workoutCount > 0) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

/**
 * Modern heatmap legend with improved accessibility
 */
@Composable
private fun ModernHeatmapLegend(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        Text(
            text = "Activity Level",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Less",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
            ) {
                (0..4).forEach { level ->
                    val intensity = level / 4f
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(GridSystem.cornerRadiusSmall))
                            .background(getIntensityColor(intensity, true))
                    )
                }
            }
            
            Text(
                text = "More",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Get intensity color with improved accessibility and brand color integration
 */
private fun getIntensityColor(intensity: Float, isCurrentMonth: Boolean): Color {
    return if (!isCurrentMonth) {
        Color.Transparent
    } else {
        when {
            intensity == 0f -> LiftrixColors.SurfaceLight.copy(alpha = 0.1f)
            intensity <= 0.25f -> LiftrixColors.Primary.copy(alpha = 0.2f)
            intensity <= 0.5f -> LiftrixColors.Primary.copy(alpha = 0.4f)
            intensity <= 0.75f -> LiftrixColors.Primary.copy(alpha = 0.7f)
            else -> LiftrixColors.Primary
        }
    }
}

/**
 * Generate calendar days for the given month
 */
private fun generateCalendarDays(currentDate: LocalDate): List<CalendarDayData> {
    val firstDayOfMonth = LocalDate(currentDate.year, currentDate.month, 1)
    val lastDayOfMonth = LocalDate(currentDate.year, currentDate.month, currentDate.month.length(false))
    
    val startOfWeek = firstDayOfMonth.minus(DatePeriod(days = firstDayOfMonth.dayOfWeek.value % 7))
    val endOfWeek = lastDayOfMonth.plus(DatePeriod(days = 6 - (lastDayOfMonth.dayOfWeek.value % 7)))
    
    val days = mutableListOf<CalendarDayData>()
    var current = startOfWeek
    
    while (current <= endOfWeek) {
        days.add(
            CalendarDayData(
                date = current,
                isCurrentMonth = current.month == currentDate.month
            )
        )
        current = current.plus(DatePeriod(days = 1))
    }
    
    return days
}

/**
 * Generate zero-value sample data for empty state
 */
private fun getZeroFrequencyData(): List<FrequencyDataPoint> {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return (0..29).map { daysBack ->
        FrequencyDataPoint(
            date = today.minus(DatePeriod(days = daysBack)),
            workoutCount = 0,
            intensity = 0f
        )
    }
}

/**
 * Data class for calendar day information
 */
private data class CalendarDayData(
    val date: LocalDate,
    val isCurrentMonth: Boolean
) 
