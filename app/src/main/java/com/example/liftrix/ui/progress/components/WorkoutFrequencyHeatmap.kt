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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.domain.repository.FrequencyDataPoint
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Calendar-style workout frequency heatmap component with month navigation.
 * 
 * Displays workout frequency in a calendar format with month headers,
 * day numbers, and workout counts. Features navigation arrows to browse
 * through months, making it compact like modern calendar apps.
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
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Workout Frequency Calendar",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingState()
                    }
                }
                data.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState()
                    }
                }
                else -> {
                    NavigableFrequencyCalendar(
                        data = data,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Legend for heatmap intensity
            if (!isLoading && data.isNotEmpty()) {
                HeatmapLegend(
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/**
 * Loading state for the frequency heatmap
 */
@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Loading frequency data...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * Empty state for the frequency heatmap
 */
@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No frequency data available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Complete workouts regularly to see frequency patterns",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Navigable calendar with month selection arrows
 */
@Composable
private fun NavigableFrequencyCalendar(
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
    
    // Generate calendar data for current month
    val currentMonthData = remember(currentDate, dataMap, maxWorkouts) {
        generateMonthCalendarData(currentDate, dataMap, maxWorkouts)
    }
    
    Column(modifier = modifier) {
        // Month navigation header
        MonthNavigationHeader(
            currentMonth = currentDate.month,
            canNavigatePrevious = currentMonthIndex > 0,
            canNavigateNext = currentMonthIndex < availableMonths.size - 1,
            onPreviousMonth = { 
                if (currentMonthIndex > 0) {
                    currentMonthIndex--
                }
            },
            onNextMonth = { 
                if (currentMonthIndex < availableMonths.size - 1) {
                    currentMonthIndex++
                }
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Current month calendar
        MonthCalendarGrid(
            cells = currentMonthData,
            currentMonth = currentDate.month,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Month navigation header with arrows and month name
 */
@Composable
private fun MonthNavigationHeader(
    currentMonth: Month,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous month button
        IconButton(
            onClick = onPreviousMonth,
            enabled = canNavigatePrevious
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Previous month",
                tint = if (canNavigatePrevious) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
        }
        
        // Current month name
        Text(
            text = currentMonth.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        
        // Next month button
        IconButton(
            onClick = onNextMonth,
            enabled = canNavigateNext
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Next month",
                tint = if (canNavigateNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
        }
    }
}

/**
 * Calendar grid for a single month
 */
@Composable
private fun MonthCalendarGrid(
    cells: List<CalendarCell>,
    currentMonth: Month,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Week day headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Calendar grid
        val weeks = cells.chunked(7)
        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                week.forEach { cell ->
                    CalendarCellView(
                        cell = cell,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining cells if week is incomplete
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
        }
    }
}

/**
 * Individual calendar cell with day number and workout count
 */
@Composable
private fun CalendarCellView(
    cell: CalendarCell,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        !cell.isCurrentMonth -> MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
        cell.workoutCount == 0 -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        cell.workoutCount == 1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        cell.workoutCount == 2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        cell.workoutCount >= 3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.primary
    }
    
    val textColor = when {
        !cell.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        cell.workoutCount >= 2 -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Day number
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
            
            // Workout count (only show if > 0 and in current month)
            if (cell.workoutCount > 0 && cell.isCurrentMonth) {
                Text(
                    text = cell.workoutCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Generate calendar data for a specific month with complete weeks
 */
private fun generateMonthCalendarData(
    monthDate: LocalDate,
    dataMap: Map<LocalDate, Int>,
    maxWorkouts: Int
): List<CalendarCell> {
    val firstDayOfMonth = LocalDate(monthDate.year, monthDate.month, 1)
    val lastDayOfMonth = LocalDate(monthDate.year, monthDate.month, monthDate.month.length(false))
    
    // Get the first day of the week containing the first day of the month
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Sunday = 0, Monday = 1, etc.
    val calendarStartDate = firstDayOfMonth.minus(DatePeriod(days = firstDayOfWeek))
    
    // Calculate total days needed for complete weeks
    val daysInMonth = lastDayOfMonth.dayOfMonth
    val totalDaysNeeded = ((daysInMonth + firstDayOfWeek + 6) / 7) * 7 // Round up to complete weeks
    
    return (0 until totalDaysNeeded).map { dayOffset ->
        val date = calendarStartDate.plus(DatePeriod(days = dayOffset))
        val isCurrentMonth = date.month == monthDate.month
        val workoutCount = if (isCurrentMonth) {
            dataMap[date] ?: 0
        } else {
            0 // Days outside current month have no workout count
        }
        val intensity = if (maxWorkouts > 0 && workoutCount > 0) {
            workoutCount.toFloat() / maxWorkouts
        } else {
            0f
        }
        
        CalendarCell(
            date = date,
            workoutCount = workoutCount,
            intensity = intensity,
            isCurrentMonth = isCurrentMonth
        )
    }
}

/**
 * Heatmap legend showing intensity levels
 */
@Composable
private fun HeatmapLegend(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Less",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // Legend squares
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val intensities = listOf(0.1f, 0.3f, 0.5f, 0.7f, 1.0f)
            intensities.forEach { intensity ->
                val backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = intensity)
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(backgroundColor)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = "More",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

/**
 * Data class representing a single cell in the calendar
 */
private data class CalendarCell(
    val date: LocalDate,
    val workoutCount: Int,
    val intensity: Float,
    val isCurrentMonth: Boolean = true
) 