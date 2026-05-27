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
     * Monthly time range (30 days)
     * Primary time range for most analytics displays and comparisons
     */
    MONTH("Month", 30),
    
    /**
     * Six month time range (180 days)
     * Used for extended progress tracking and bi-annual comparisons
     */
    SIX_MONTHS("6 Months", 180),
    
    /**
     * All time range (no limit)
     * Used for complete historical data analysis across entire user history
     */
    ALL_TIME("All Time", Int.MAX_VALUE);
    
    /**
     * Gets a shortened display name for compact UI elements
     */
    fun getShortDisplayName(): String = when (this) {
        MONTH -> "1M"
        SIX_MONTHS -> "6M"
        ALL_TIME -> "All"
    }
    
    /**
     * Checks if this time range type is suitable for real-time updates
     */
    fun isRealTimeCompatible(): Boolean = when (this) {
        MONTH -> true
        SIX_MONTHS, ALL_TIME -> false
    }
    
    /**
     * Gets the recommended update frequency in minutes for this time range
     */
    fun getUpdateFrequencyMinutes(): Int = when (this) {
        MONTH -> 60     // Update every hour for monthly data
        SIX_MONTHS -> 720  // Update every 12 hours for six-month data
        ALL_TIME -> 2880  // Update every 2 days for all-time data
    }
    
    companion object {
        /**
         * Gets all time range types suitable for dashboard display
         */
        fun getDashboardTypes(): List<TimeRangeType> = listOf(MONTH, SIX_MONTHS, ALL_TIME)
        
        /**
         * Gets all time range types suitable for export functionality
         */
        fun getExportTypes(): List<TimeRangeType> = values().toList()
        
        /**
         * Gets the most appropriate time range type for a given duration in days
         */
        fun getClosestType(durationInDays: Int): TimeRangeType = when {
            durationInDays <= 90 -> MONTH
            durationInDays <= 270 -> SIX_MONTHS
            else -> ALL_TIME
        }

        fun fromConfig(value: String?): TimeRangeType {
            val normalized = value
                ?.trim()
                ?.replace("-", "_")
                ?.replace(" ", "_")
                ?.uppercase()
                ?: return MONTH

            return when (normalized) {
                "1M", "ONE_MONTH", "MONTH" -> MONTH
                "6M", "SIX_MONTH", "SIX_MONTHS", "6_MONTH", "6_MONTHS" -> SIX_MONTHS
                "ALL", "ALL_TIME", "ALLTIME" -> ALL_TIME
                else -> entries.firstOrNull {
                    it.name == normalized ||
                        it.displayName.replace(" ", "_").uppercase() == normalized ||
                        it.getShortDisplayName().uppercase() == normalized
                } ?: MONTH
            }
        }
    }
}
