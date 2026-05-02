package com.example.liftrix.domain.model.analytics

/**
 * Enum for different ways to group volume data in analytics
 * 
 * Used in volume analysis detail views to determine how to aggregate
 * and display volume metrics across different dimensions.
 */
enum class VolumeGrouping(
    val displayName: String,
    val description: String
) {
    /**
     * Group by total volume across all exercises
     */
    TOTAL("Total Volume", "Combined volume from all exercises"),
    
    /**
     * Group by individual exercises
     */
    BY_EXERCISE("By Exercise", "Volume broken down by specific exercises"),
    
    /**
     * Group by muscle groups
     */
    BY_MUSCLE_GROUP("By Muscle Group", "Volume grouped by primary muscle groups"),
    
    /**
     * Group by workout sessions
     */
    BY_SESSION("By Session", "Volume per individual workout session"),
    
    /**
     * Group by weekly periods
     */
    BY_WEEK("By Week", "Volume aggregated weekly"),
    
    /**
     * Group by monthly periods
     */
    BY_MONTH("By Month", "Volume aggregated monthly");
    
    companion object {
        /**
         * Get groupings suitable for chart display
         */
        fun getChartGroupings(): List<VolumeGrouping> {
            return listOf(TOTAL, BY_EXERCISE, BY_MUSCLE_GROUP)
        }
        
        /**
         * Get time-based groupings
         */
        fun getTimeBasedGroupings(): List<VolumeGrouping> {
            return listOf(BY_SESSION, BY_WEEK, BY_MONTH)
        }
    }
}