package com.example.liftrix.domain.model.analytics

import java.util.Date
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.DatePeriod

/**
 * Time range model for analytics queries and exports
 * 
 * Represents a specific time period for analytics calculations.
 * Used by the analytics export system to filter data for reports.
 * 
 * @property startDate Start date of the time range (inclusive)
 * @property endDate End date of the time range (inclusive)
 * @property type The categorized type of this time range
 */
data class TimeRange(
    val startDate: Date,
    val endDate: Date,
    val type: TimeRangeType = TimeRangeType.getClosestType(((endDate.time - startDate.time) / (24 * 60 * 60 * 1000)).toInt())
) {
    /**
     * Validates that the time range is valid
     */
    fun isValid(): Boolean {
        return startDate.before(endDate) || startDate == endDate
    }
    
    /**
     * Returns the duration of the time range in days
     */
    fun getDurationInDays(): Int {
        val diffInMillis = endDate.time - startDate.time
        return (diffInMillis / (24 * 60 * 60 * 1000)).toInt()
    }
    
    /**
     * Returns the duration of the time range in weeks
     */
    fun getDurationInWeeks(): Int {
        return getDurationInDays() / 7
    }
    
    /**
     * Returns the duration of the time range in months (approximate)
     */
    fun getDurationInMonths(): Int {
        return getDurationInDays() / 30
    }
    
    /**
     * Checks if the time range contains a specific date
     */
    fun contains(date: Date): Boolean {
        return (date.after(startDate) || date == startDate) && 
               (date.before(endDate) || date == endDate)
    }
    
    /**
     * Returns a formatted display string for the time range
     */
    fun getDisplayString(): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val startStr = formatter.format(startDate)
        val endStr = formatter.format(endDate)
        return "$startStr - $endStr"
    }
    
    companion object {
        /**
         * Creates a TimeRange for the last month
         */
        fun lastMonth(): TimeRange {
            val calendar = Calendar.getInstance()
            // FIX: Extend end date slightly into future to catch workouts with minor timestamp issues
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val endDate = calendar.time
            calendar.add(Calendar.MONTH, -1)
            calendar.add(Calendar.DAY_OF_MONTH, -1) // Back to actual last month
            val startDate = calendar.time
            return TimeRange(startDate, endDate, TimeRangeType.MONTH)
        }
        
        /**
         * Creates a TimeRange for the last six months
         */
        fun lastSixMonths(): TimeRange {
            val calendar = Calendar.getInstance()
            val endDate = calendar.time
            calendar.add(Calendar.MONTH, -6)
            val startDate = calendar.time
            return TimeRange(startDate, endDate, TimeRangeType.SIX_MONTHS)
        }
        
        /**
         * Creates a TimeRange for all time (from epoch to now)
         */
        fun allTime(): TimeRange {
            val calendar = Calendar.getInstance()
            val endDate = calendar.time
            val startDate = Date(0L) // Epoch time
            return TimeRange(startDate, endDate, TimeRangeType.ALL_TIME)
        }
        
        /**
         * Creates a TimeRange with LocalDate for analytics from TimeRangeType
         */
        fun fromType(timeRangeType: TimeRangeType): AnalyticsTimeRange {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            
            return when (timeRangeType) {
                TimeRangeType.MONTH -> AnalyticsTimeRange(
                    startDate = now.minus(DatePeriod(days = 30)),
                    endDate = now,
                    type = timeRangeType
                )
                TimeRangeType.SIX_MONTHS -> AnalyticsTimeRange(
                    startDate = now.minus(DatePeriod(months = 6)),
                    endDate = now,
                    type = timeRangeType
                )
                TimeRangeType.ALL_TIME -> AnalyticsTimeRange(
                    startDate = LocalDate(2020, 1, 1), // Reasonable start date for workout tracking
                    endDate = now,
                    type = timeRangeType
                )
            }
        }
    }
}

/**
 * Analytics-specific time range using kotlinx.datetime.LocalDate
 */
data class AnalyticsTimeRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val type: TimeRangeType
) {
    /**
     * Validates that the time range is valid
     */
    fun isValid(): Boolean {
        return startDate <= endDate
    }
    
    /**
     * Returns the duration of the time range in days
     */
    fun getDurationInDays(): Int {
        return endDate.toEpochDays() - startDate.toEpochDays()
    }
}
