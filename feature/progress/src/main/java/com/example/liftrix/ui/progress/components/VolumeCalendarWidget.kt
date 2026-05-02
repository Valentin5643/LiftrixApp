package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.layouts.GridSystem
import kotlinx.datetime.LocalDate
import java.time.YearMonth

/**
 * Volume calendar widget component for monthly workout analytics view
 *
 * Displays a Material 3 volume calendar with color-coded intensity showing daily workout volumes
 * in a 7-column grid layout. Features smooth animations, monthly navigation, and comprehensive
 * accessibility support.
 *
 * Key features:
 * - LazyVerticalGrid calendar implementation (7 columns × 6 rows)
 * - LiftrixColors gradient system for volume intensity visualization
 * - Material 3 theming with 8pt grid spacing
 * - Smooth month transition animations
 * - Accessibility compliance with TalkBack support
 * - 48dp minimum touch targets with proper spacing
 *
 * @param calendarData Volume calendar data containing daily volumes and intensity calculations
 * @param onDateClick Callback invoked when a calendar day is clicked
 * @param modifier Modifier for styling the widget
 */
@Composable
fun VolumeCalendarWidget(
    calendarData: VolumeCalendarData,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    LiftrixCard(
        modifier = modifier.fillMaxWidth(),
        contentDescription = "Volume calendar for ${calendarData.getFormattedMonthYear()}"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
        ) {
            VolumeCalendarNavigation(
                currentMonth = YearMonth.of(calendarData.year, calendarData.month.ordinal + 1),
                onMonthChange = { /* Future: Navigate to new month */ }
            )
            
            CalendarMonthHeader(
                month = YearMonth.of(calendarData.year, calendarData.month.ordinal + 1)
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1),
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
            ) {
                items(calendarData.getDaysInCalendarGrid()) { calendarDay ->
                    CalendarDayCard(
                        date = calendarDay.date,
                        volume = calendarData.getVolumeForDate(calendarDay.date),
                        intensity = calendarData.getVolumeIntensity(calendarDay.date),
                        isCurrentMonth = calendarDay.isCurrentMonth,
                        onClick = { onDateClick(calendarDay.date) }
                    )
                }
            }
            
            // Volume statistics summary
            VolumeStatsSummary(
                totalVolume = calendarData.getTotalMonthVolume(),
                averageVolume = calendarData.averageVolume,
                workoutDays = calendarData.getWorkoutDaysCount(),
                workoutFrequency = calendarData.getWorkoutFrequency()
            )
        }
    }
}

/**
 * Volume statistics summary for the calendar month
 */
@Composable
private fun VolumeStatsSummary(
    totalVolume: com.example.liftrix.domain.model.Volume,
    averageVolume: com.example.liftrix.domain.model.Volume,
    workoutDays: Int,
    workoutFrequency: Float
) {
    // Monthly summary stats removed per user request
}

/**
 * Calendar weekday header labels
 */
@Composable
private fun CalendarMonthHeader(month: YearMonth) {
    val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
    ) {
        items(weekdays) { weekday ->
            Text(
                text = weekday,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}