package com.example.liftrix.domain.model.analytics

/**
 * Enumeration of supported time range types for analytics calculations
 * 
 * Defines standard time periods used throughout the analytics system for:
 * - Progress metrics calculation and aggregation
 * - Dashboard display categorization
 * - Export system time range selection
 * - Consistent UI labeling and navigation
 * 
 * Each type represents a standardized period that the analytics engine
 * can efficiently calculate and cache for optimal performance.
 */
enum class TimeRangeType(
    val displayName: String,
    val durationInDays: Int
) {
    /**
     * Weekly time range (7 days)
     * Used for short-term progress tracking and immediate feedback
     */
    WEEK("Week", 7),
    
    /**
     * Monthly time range (30 days)
     * Primary time range for most analytics displays and comparisons
     */
    MONTH("Month", 30),
    
    /**
     * Quarterly time range (90 days)
     * Used for medium-term progress evaluation and trend analysis
     */
    QUARTER("Quarter", 90),
    
    /**
     * Yearly time range (365 days)
     * Used for long-term progress assessment and annual summaries
     */
    YEAR("Year", 365);
    
    /**
     * Gets a shortened display name for compact UI elements
     */
    fun getShortDisplayName(): String = when (this) {
        WEEK -> "1W"
        MONTH -> "1M"
        QUARTER -> "3M"
        YEAR -> "1Y"
    }
    
    /**
     * Checks if this time range type is suitable for real-time updates
     */
    fun isRealTimeCompatible(): Boolean = when (this) {
        WEEK, MONTH -> true
        QUARTER, YEAR -> false
    }
    
    /**
     * Gets the recommended update frequency in minutes for this time range
     */
    fun getUpdateFrequencyMinutes(): Int = when (this) {
        WEEK -> 15      // Update every 15 minutes for weekly data
        MONTH -> 60     // Update every hour for monthly data
        QUARTER -> 240  // Update every 4 hours for quarterly data
        YEAR -> 1440    // Update daily for yearly data
    }
    
    companion object {
        /**
         * Gets all time range types suitable for dashboard display
         */
        fun getDashboardTypes(): List<TimeRangeType> = listOf(WEEK, MONTH, QUARTER)
        
        /**
         * Gets all time range types suitable for export functionality
         */
        fun getExportTypes(): List<TimeRangeType> = values().toList()
        
        /**
         * Gets the most appropriate time range type for a given duration in days
         */
        fun getClosestType(durationInDays: Int): TimeRangeType = when {
            durationInDays <= 10 -> WEEK
            durationInDays <= 45 -> MONTH
            durationInDays <= 180 -> QUARTER
            else -> YEAR
        }
    }
}