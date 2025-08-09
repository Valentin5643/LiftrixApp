package com.example.liftrix.domain.model.analytics

import kotlinx.datetime.LocalDate

/**
 * Date range data class for analytics and chart filtering
 * 
 * Represents a time period with start and end dates, providing utility methods
 * for date range operations throughout the analytics system.
 */
data class DateRange(
    val start: LocalDate,
    val end: LocalDate
) {
    /**
     * Checks if a given date falls within this date range (inclusive)
     */
    fun contains(date: LocalDate): Boolean = date >= start && date <= end
    
    /**
     * Calculates the total number of days in this date range (inclusive)
     */
    fun dayCount(): Int = (end.toJavaLocalDate().toEpochDay() - start.toJavaLocalDate().toEpochDay()).toInt() + 1
    
    /**
     * Checks if this date range is valid (start <= end)
     */
    fun isValid(): Boolean = start <= end
    
    /**
     * Gets the duration of this date range in weeks (rounded up)
     */
    fun weekCount(): Int = kotlin.math.ceil(dayCount() / 7.0).toInt()
    
    /**
     * Splits this date range into smaller ranges of the specified duration
     */
    fun splitByDays(dayInterval: Int): List<DateRange> {
        val ranges = mutableListOf<DateRange>()
        var currentStart = start
        
        while (currentStart < end) {
            val currentEnd = minOf(
                currentStart.plusDays(dayInterval - 1),
                end
            )
            ranges.add(DateRange(currentStart, currentEnd))
            currentStart = currentEnd.plusDays(1)
        }
        
        return ranges
    }
    
    companion object {
        /**
         * Creates a DateRange for the last N days from today
         */
        fun lastDays(days: Int, today: LocalDate? = null): DateRange {
            val actualToday = today ?: LocalDate(2025, 8, 8) // Temporary fallback
            return DateRange(
                start = actualToday.minusDays(days - 1),
                end = actualToday
            )
        }
        
        /**
         * Creates a DateRange for the current month
         */
        fun currentMonth(today: LocalDate? = null): DateRange {
            val actualToday = today ?: LocalDate(2025, 8, 8) // Temporary fallback
            val startOfMonth = LocalDate(actualToday.year, actualToday.month, 1)
            val endOfMonth = startOfMonth.plusMonths(1).minusDays(1)
            return DateRange(startOfMonth, endOfMonth)
        }
        
        /**
         * Creates a DateRange for a specific TimeRangeType
         */
        fun fromTimeRangeType(
            timeRangeType: TimeRangeType,
            endDate: LocalDate? = null
        ): DateRange {
            val actualEndDate = endDate ?: LocalDate(2025, 8, 8) // Temporary fallback
            val startDate = actualEndDate.minusDays(timeRangeType.durationInDays - 1)
            return DateRange(startDate, actualEndDate)
        }
    }
}

/**
 * Extension function to convert Java LocalDate to Kotlin LocalDate
 */
private fun LocalDate.toJavaLocalDate(): java.time.LocalDate = 
    java.time.LocalDate.of(this.year, this.monthNumber, this.dayOfMonth)

/**
 * Extension function to add days to LocalDate
 */
private fun LocalDate.plusDays(days: Int): LocalDate = 
    this.toJavaLocalDate().plusDays(days.toLong()).let {
        LocalDate(it.year, it.monthValue, it.dayOfMonth)
    }

/**
 * Extension function to subtract days from LocalDate  
 */
private fun LocalDate.minusDays(days: Int): LocalDate = 
    this.toJavaLocalDate().minusDays(days.toLong()).let {
        LocalDate(it.year, it.monthValue, it.dayOfMonth)
    }

/**
 * Extension function to add months to LocalDate
 */
private fun LocalDate.plusMonths(months: Int): LocalDate = 
    this.toJavaLocalDate().plusMonths(months.toLong()).let {
        LocalDate(it.year, it.monthValue, it.dayOfMonth)
    }