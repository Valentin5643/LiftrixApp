package com.example.liftrix.domain.model.analytics

import java.util.Date
import java.util.Calendar

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
        val startStr = android.text.format.DateFormat.format("MMM dd, yyyy", startDate)
        val endStr = android.text.format.DateFormat.format("MMM dd, yyyy", endDate)
        return "$startStr - $endStr"
    }
    
    companion object {
        /**
         * Creates a TimeRange for the last week
         */
        fun lastWeek(): TimeRange {
            val calendar = Calendar.getInstance()
            val endDate = calendar.time
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            val startDate = calendar.time
            return TimeRange(startDate, endDate, TimeRangeType.WEEK)
        }
        
        /**
         * Creates a TimeRange for the last month
         */
        fun lastMonth(): TimeRange {
            val calendar = Calendar.getInstance()
            val endDate = calendar.time
            calendar.add(Calendar.MONTH, -1)
            val startDate = calendar.time
            return TimeRange(startDate, endDate, TimeRangeType.MONTH)
        }
        
        /**
         * Creates a TimeRange for the last quarter
         */
        fun lastQuarter(): TimeRange {
            val calendar = Calendar.getInstance()
            val endDate = calendar.time
            calendar.add(Calendar.MONTH, -3)
            val startDate = calendar.time
            return TimeRange(startDate, endDate, TimeRangeType.QUARTER)
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
         * Creates a TimeRange for the last year
         */
        fun lastYear(): TimeRange {
            val calendar = Calendar.getInstance()
            val endDate = calendar.time
            calendar.add(Calendar.YEAR, -1)
            val startDate = calendar.time
            return TimeRange(startDate, endDate, TimeRangeType.YEAR)
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
    }
}