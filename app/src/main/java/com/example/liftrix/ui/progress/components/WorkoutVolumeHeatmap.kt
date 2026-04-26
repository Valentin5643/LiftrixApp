package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.analytics.CalendarDay
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.theme.LiftrixColorsV2
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/**
 * Modern workout volume heatmap component with full navigation and enhanced accessibility.
 * 
 * Features professional calendar-style layout with LiftrixV2 colors, month navigation,
 * and improved accessibility. Displays workout volume in a modern heatmap format
 * with intensity indicators based on daily volume.
 * 
 * Key improvements:
 * - LiftrixCard system with 24dp border radius
 * - Enhanced color coding with LiftrixColorsV2.Teal gradient system
 * - Professional calendar layout with modern styling
 * - Full month navigation (3 months back)
 * - Non-scrollable day grid showing complete month
 * - Volume-based intensity calculations for better insights
 * - Better accessibility support with proper contrast ratios
 * 
 * @param data VolumeCalendarData containing daily volumes and intensity calculations
 * @param isLoading Whether the chart is currently loading data
 * @param modifier Modifier for styling the chart container
 */
@Composable
fun WorkoutVolumeHeatmap(
    data: VolumeCalendarData?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // DEBUG: Log component loading state
    LaunchedEffect(isLoading, data?.dailyVolumes?.size) {
        timber.log.Timber.d("🔍 VOLUME-HEATMAP-DEBUG: isLoading=$isLoading, data.size=${data?.dailyVolumes?.size ?: 0}")
    }
    
    // FLASH FIX: Only show loading for genuine delays, not brief flashes
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        timber.log.Timber.d("🔍 VOLUME-HEATMAP-DEBUG: LaunchedEffect triggered - isLoading=$isLoading")
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
        contentDescription = "Workout volume calendar heatmap with navigation"
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
                        text = "Volume Calendar",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Daily workout volume patterns",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "Volume calendar",
                    tint = LiftrixColorsV2.Teal,
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
                data == null || !data.hasWorkoutData() -> {
                    // Show zero-value chart instead of empty state
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
                    ) {
                        // Enhanced volume calendar with zero data
                        ModernNavigableVolumeCalendar(
                            data = getZeroVolumeCalendarData(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Modern legend with improved accessibility
                        ModernVolumeLegend(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                else -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
                    ) {
                        // Enhanced volume calendar
                        ModernNavigableVolumeCalendar(
                            data = data,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Modern legend with improved accessibility
                        ModernVolumeLegend(
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Volume statistics summary
                        VolumeStatsSummary(
                            data = data,
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
            color = LiftrixColorsV2.Teal,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = "Loading volume data...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Modern navigable volume calendar - full navigation like frequency calendar
 */
@Composable
private fun ModernNavigableVolumeCalendar(
    data: VolumeCalendarData,
    modifier: Modifier = Modifier
) {
    // Create a map of dates to workout volumes for quick lookup
    val volumeMap = remember(data) {
        data.dailyVolumes
    }
    
    // Calculate the maximum volume for intensity scaling
    val maxVolume = remember(data) {
        data.maxVolume
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
        ModernVolumeCalendarGrid(
            currentDate = currentDate,
            volumeMap = volumeMap,
            maxVolume = maxVolume,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Modern calendar grid with volume-based coloring and LiftrixV2 design
 */
@Composable
private fun ModernVolumeCalendarGrid(
    currentDate: LocalDate,
    volumeMap: Map<LocalDate, com.example.liftrix.domain.model.Volume>,
    maxVolume: com.example.liftrix.domain.model.Volume,
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
        
        // Calendar days grid with improved spacing - NON-SCROLLABLE
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(240.dp), // Increased height to show all 31 days
            horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1),
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing1),
            userScrollEnabled = false // DISABLE SCROLLING - key requirement!
        ) {
            // Generate calendar days for current selected month
            val daysInCalendar = generateCalendarDays(currentDate)
            
            items(daysInCalendar.size) { index ->
                val dayData = daysInCalendar[index]
                val volume = volumeMap[dayData.date] ?: com.example.liftrix.domain.model.Volume.ZERO
                val volumeIntensity = if (maxVolume.kilograms > 0.0) {
                    (volume.kilograms / maxVolume.kilograms).toFloat().coerceIn(0.0f, 1.0f)
                } else 0.0f
                
                ModernVolumeCalendarDay(
                    dayData = dayData,
                    volume = volume,
                    volumeIntensity = volumeIntensity,
                    isCurrentMonth = dayData.isCurrentMonth
                )
            }
        }
    }
}

/**
 * Individual calendar day with modern styling and volume-based coloring
 */
@Composable
private fun ModernVolumeCalendarDay(
    dayData: CalendarDay,
    volume: com.example.liftrix.domain.model.Volume,
    volumeIntensity: Float,
    isCurrentMonth: Boolean
) {
    val backgroundColor = getVolumeIntensityColor(volumeIntensity, isCurrentMonth)
    val textColor = if (volumeIntensity > 0.5f) {
        Color.White // High contrast on intense colors
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
                fontWeight = if (volume.kilograms > 0.0) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

/**
 * Modern heatmap legend with LiftrixV2 colors
 */
@Composable
private fun ModernVolumeLegend(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        Text(
            text = "Volume Intensity",
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
                            .background(getVolumeIntensityColor(intensity, true))
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
 * Volume statistics summary for the calendar month
 */
@Composable
private fun VolumeStatsSummary(
    data: VolumeCalendarData,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        Text(
            text = "Monthly Summary",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Total Volume",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${data.getTotalMonthVolume().kilograms.toInt()}kg",
                    style = MaterialTheme.typography.titleMedium,
                    color = LiftrixColorsV2.Teal,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Column {
                Text(
                    text = "Avg Volume",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${data.averageVolume.kilograms.toInt()}kg",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Column {
                Text(
                    text = "Workout Days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${data.getWorkoutDaysCount()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Generate calendar days for the given month (simplified approach)
 */
private fun generateCalendarDays(currentDate: LocalDate): List<CalendarDay> {
    // Simple approach: generate all days for the current month plus padding
    val year = currentDate.year
    val month = currentDate.month
    val firstDay = LocalDate(year, month, 1)
    
    // Get days in month (simplified)
    val daysInMonth = when (month) {
        kotlinx.datetime.Month.FEBRUARY -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        kotlinx.datetime.Month.APRIL, kotlinx.datetime.Month.JUNE, 
        kotlinx.datetime.Month.SEPTEMBER, kotlinx.datetime.Month.NOVEMBER -> 30
        else -> 31
    }
    
    val days = mutableListOf<CalendarDay>()
    
    // Add padding days from previous month to align with Sunday start
    val firstDayOfWeek = firstDay.dayOfWeek.value % 7 // Convert to 0-6 (Sunday = 0)
    val prevMonth = if (month.ordinal == 0) {
        kotlinx.datetime.Month.DECEMBER
    } else {
        kotlinx.datetime.Month.values()[month.ordinal - 1]
    }
    val prevYear = if (month == kotlinx.datetime.Month.JANUARY) year - 1 else year
    val daysInPrevMonth = when (prevMonth) {
        kotlinx.datetime.Month.FEBRUARY -> if (prevYear % 4 == 0 && (prevYear % 100 != 0 || prevYear % 400 == 0)) 29 else 28
        kotlinx.datetime.Month.APRIL, kotlinx.datetime.Month.JUNE, 
        kotlinx.datetime.Month.SEPTEMBER, kotlinx.datetime.Month.NOVEMBER -> 30
        else -> 31
    }
    
    for (i in (daysInPrevMonth - firstDayOfWeek + 1)..daysInPrevMonth) {
        days.add(CalendarDay(
            date = LocalDate(prevYear, prevMonth, i),
            isCurrentMonth = false
        ))
    }
    
    // Add current month days
    for (day in 1..daysInMonth) {
        days.add(CalendarDay(
            date = LocalDate(year, month, day),
            isCurrentMonth = true
        ))
    }
    
    // Add padding days from next month to complete the grid (42 days total)
    val nextMonth = if (month.ordinal == 11) {
        kotlinx.datetime.Month.JANUARY
    } else {
        kotlinx.datetime.Month.values()[month.ordinal + 1]
    }
    val nextYear = if (month == kotlinx.datetime.Month.DECEMBER) year + 1 else year
    
    val remainingDays = 42 - days.size
    for (day in 1..remainingDays) {
        days.add(CalendarDay(
            date = LocalDate(nextYear, nextMonth, day),
            isCurrentMonth = false
        ))
    }
    
    return days
}

/**
 * Get volume intensity color with LiftrixV2 Teal gradient system
 */
private fun getVolumeIntensityColor(intensity: Float, isCurrentMonth: Boolean): Color {
    return if (!isCurrentMonth) {
        Color.Transparent
    } else {
        when {
            intensity == 0f -> LiftrixColorsV2.Teal.copy(alpha = 0.15f) // Much brighter base for 0kg days
            intensity <= 0.1f -> LiftrixColorsV2.Teal.copy(alpha = 0.25f) // Very low volume still visible
            intensity <= 0.3f -> LiftrixColorsV2.Teal.copy(alpha = 0.4f) // Reduced sensitivity - takes more volume to get bright
            intensity <= 0.5f -> LiftrixColorsV2.Teal.copy(alpha = 0.6f) // More gradual progression
            intensity <= 0.7f -> LiftrixColorsV2.Teal.copy(alpha = 0.75f) // Smoother transition
            intensity <= 0.9f -> LiftrixColorsV2.Teal.copy(alpha = 0.9f) // Near maximum
            else -> LiftrixColorsV2.Teal // Full intensity only for maximum volume
        }
    }
}

/**
 * Generate zero-value sample data for empty state (current month)
 */
private fun getZeroVolumeCalendarData(): VolumeCalendarData {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return VolumeCalendarData.empty(today.year, today.month)
}
