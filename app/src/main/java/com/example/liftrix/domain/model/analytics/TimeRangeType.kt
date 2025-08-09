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
     * Three month time range (90 days)
     * Alternative naming for quarterly period, used in specific analytics contexts
     */
    THREE_MONTHS("3 Months", 90),
    
    /**
     * Six month time range (180 days)
     * Used for extended progress tracking and bi-annual comparisons
     */
    SIX_MONTHS("6 Months", 180),
    
    /**
     * Yearly time range (365 days)
     * Used for long-term progress assessment and annual summaries
     */
    YEAR("Year", 365),
    
    /**
     * All time range (no limit)
     * Used for complete historical data analysis across entire user history
     */
    ALL_TIME("All Time", Int.MAX_VALUE);
    
    /**
     * Gets a shortened display name for compact UI elements
     */
    fun getShortDisplayName(): String = when (this) {
        WEEK -> "1W"
        MONTH -> "1M"
        QUARTER -> "3M"
        THREE_MONTHS -> "3M"
        SIX_MONTHS -> "6M"
        YEAR -> "1Y"
        ALL_TIME -> "All"
    }
    
    /**
     * Checks if this time range type is suitable for real-time updates
     */
    fun isRealTimeCompatible(): Boolean = when (this) {
        WEEK, MONTH -> true
        QUARTER, THREE_MONTHS, SIX_MONTHS, YEAR, ALL_TIME -> false
    }
    
    /**
     * Gets the recommended update frequency in minutes for this time range
     */
    fun getUpdateFrequencyMinutes(): Int = when (this) {
        WEEK -> 15      // Update every 15 minutes for weekly data
        MONTH -> 60     // Update every hour for monthly data
        QUARTER, THREE_MONTHS -> 240  // Update every 4 hours for quarterly data
        SIX_MONTHS -> 720  // Update every 12 hours for six-month data
        YEAR -> 1440    // Update daily for yearly data
        ALL_TIME -> 2880  // Update every 2 days for all-time data
    }
    
    companion object {
        /**
         * Gets all time range types suitable for dashboard display
         */
        fun getDashboardTypes(): List<TimeRangeType> = listOf(WEEK, MONTH, QUARTER, THREE_MONTHS, SIX_MONTHS)
        
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
            durationInDays <= 120 -> QUARTER
            durationInDays <= 210 -> SIX_MONTHS
            else -> YEAR
        }
    }
}