package com.example.liftrix.domain.model.analytics

import com.example.liftrix.domain.model.Weight
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number

/**
 * Enhanced volume calendar data structure for monthly workout analytics view
 * 
 * Provides comprehensive monthly volume tracking with:
 * - Daily volume aggregation with efficient lookup
 * - Volume intensity calculations for color-coded visualizations  
 * - Calendar-specific utilities for UI rendering
 * - Performance-optimized data structures for real-time updates
 * 
 * Used by:
 * - VolumeCalendarWidget for UI display
 * - Analytics calculation engine for monthly summaries
 * - Progress tracking dashboard widgets
 */
data class VolumeCalendarData(
    val year: Int,
    val month: Month,
    val dailyVolumes: Map<LocalDate, Weight>,
    val maxVolume: Weight,
    val averageVolume: Weight
) {
    init {
        require(year >= MIN_YEAR && year <= MAX_YEAR) { 
            "Year must be between $MIN_YEAR and $MAX_YEAR: $year" 
        }
        require(dailyVolumes.keys.all { date -> 
            date.year == year && date.month == month 
        }) { 
            "All dates must be within the specified year ($year) and month ($month)" 
        }
        require(maxVolume >= Weight.ZERO) { "Max volume cannot be negative: $maxVolume" }
        require(averageVolume >= Weight.ZERO) { "Average volume cannot be negative: $averageVolume" }
        
        // Validate max volume is actually the maximum
        val actualMax = dailyVolumes.values.maxWithOrNull(compareBy { it.kilograms }) ?: Weight.ZERO
        require(maxVolume >= actualMax) { 
            "Max volume ($maxVolume) must be >= actual maximum ($actualMax)" 
        }
    }
    
    companion object {
        const val MIN_YEAR: Int = 2020
        const val MAX_YEAR: Int = 2040
        const val DAYS_IN_CALENDAR_GRID: Int = 42 // 6 weeks × 7 days
        
        /**
         * Creates empty calendar data for a specific month
         */
        fun empty(year: Int, month: Month): VolumeCalendarData = VolumeCalendarData(
            year = year,
            month = month,
            dailyVolumes = emptyMap(),
            maxVolume = Weight.ZERO,
            averageVolume = Weight.ZERO
        )
    }
    
    /**
     * Calculates volume intensity for color-coding (0.0 to 1.0)
     * 
     * Used by UI components to determine gradient color intensity:
     * - 0.0 = No volume (transparent/minimal color)
     * - 1.0 = Maximum volume (full color intensity)
     * 
     * @param date Target date for intensity calculation
     * @return Float between 0.0 and 1.0 representing volume intensity
     */
    fun getVolumeIntensity(date: LocalDate): Float {
        val volume = dailyVolumes[date] ?: Weight.ZERO
        return if (maxVolume.kilograms > 0.0) {
            (volume.kilograms / maxVolume.kilograms).toFloat().coerceIn(0.0f, 1.0f)
        } else 0.0f
    }
    
    /**
     * Gets volume for a specific date with safe fallback
     */
    fun getVolumeForDate(date: LocalDate): Weight = dailyVolumes[date] ?: Weight.ZERO
    
    /**
     * Generates all days to display in calendar grid (42 days)
     * 
     * Includes:
     * - Previous month days (gray overlay)
     * - Current month days (full display)  
     * - Next month days (gray overlay)
     * 
     * Used by LazyVerticalGrid for consistent 6×7 calendar layout
     */
    fun getDaysInCalendarGrid(): List<CalendarDay> {
        val daysInMonth = month.length(isLeapYear(year))
        val firstDayOfMonth = LocalDate(year, month, 1)
        val startDayOfWeek = firstDayOfMonth.dayOfWeek.ordinal + 1 // Monday = 1, Sunday = 7
        
        val calendarDays = mutableListOf<CalendarDay>()
        
        // Previous month trailing days
        val previousMonthDays = if (startDayOfWeek == 7) 0 else startDayOfWeek
        val previousMonth = if (month.ordinal == 0) Month.DECEMBER else Month(month.ordinal)
        val previousYear = if (month.ordinal == 0) year - 1 else year
        val daysInPreviousMonth = previousMonth.length(isLeapYear(previousYear))
        
        for (day in (daysInPreviousMonth - previousMonthDays + 1)..daysInPreviousMonth) {
            val date = LocalDate(previousYear, previousMonth, day)
            calendarDays.add(CalendarDay(date, isCurrentMonth = false))
        }
        
        // Current month days
        for (day in 1..daysInMonth) {
            val date = LocalDate(year, month, day)
            calendarDays.add(CalendarDay(date, isCurrentMonth = true))
        }
        
        // Next month leading days
        val remainingDays = DAYS_IN_CALENDAR_GRID - calendarDays.size
        val nextMonth = if (month.ordinal == 11) Month.JANUARY else Month(month.ordinal + 2)
        val nextYear = if (month.ordinal == 11) year + 1 else year
        
        for (day in 1..remainingDays) {
            val date = LocalDate(nextYear, nextMonth, day)
            calendarDays.add(CalendarDay(date, isCurrentMonth = false))
        }
        
        return calendarDays
    }
    
    /**
     * Calculates total volume for the entire month
     */
    fun getTotalMonthVolume(): Weight = dailyVolumes.values.fold(Weight.ZERO) { acc, volume -> acc + volume }
    
    /**
     * Gets number of workout days in month (days with volume > 0)
     */
    fun getWorkoutDaysCount(): Int = dailyVolumes.values.count { it > Weight.ZERO }
    
    /**
     * Calculates workout frequency rate for the month (0.0 to 1.0)
     */
    fun getWorkoutFrequency(): Float {
        val daysInMonth = month.length(isLeapYear(year))
        return if (daysInMonth > 0) {
            getWorkoutDaysCount().toFloat() / daysInMonth.toFloat()
        } else 0.0f
    }
    
    /**
     * Checks if current month has any workout data
     */
    fun hasWorkoutData(): Boolean = dailyVolumes.isNotEmpty() && dailyVolumes.values.any { it > Weight.ZERO }
    
    /**
     * Gets formatted month display name
     */
    fun getFormattedMonthYear(): String = "${month.name.lowercase().replaceFirstChar { it.uppercase() }} $year"
}

/**
 * Represents a single day in the calendar grid
 * Used by UI components for proper styling and interaction
 */
data class CalendarDay(
    val date: LocalDate,
    val isCurrentMonth: Boolean
) {
    /**
     * Checks if this day is today
     */
    fun isToday(): Boolean {
        val now = Clock.System.now()
        val timezone = TimeZone.currentSystemDefault()
        val todayDate = now.toLocalDateTime(timezone).date
        return date == todayDate
    }
    
    /**
     * Gets day of month number
     */
    fun dayOfMonth(): Int = date.dayOfMonth
}

/**
 * Helper function to determine if a year is a leap year
 */
private fun isLeapYear(year: Int): Boolean {
    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}