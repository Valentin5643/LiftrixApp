package com.example.liftrix.domain.model.analytics

/**
 * Enum for different metrics used to rank exercises in performance analysis
 * 
 * Used in exercise ranking detail views to determine how exercises
 * are scored and ordered in performance analysis.
 */
enum class RankingMetric(
    val displayName: String,
    val description: String
) {
    /**
     * Performance score based on combined volume and strength growth
     */
    PERFORMANCE_SCORE("Performance Score", "Combined volume and strength growth metric"),
    
    /**
     * Rank by total volume progression
     */
    VOLUME_GROWTH("Volume Growth", "Percentage increase in total volume over time"),
    
    /**
     * Rank by 1RM strength progression
     */
    STRENGTH_GROWTH("Strength Growth", "Percentage increase in estimated 1RM"),
    
    /**
     * Rank by workout frequency
     */
    FREQUENCY("Frequency", "How often the exercise is performed"),
    
    /**
     * Rank by consistency of performance
     */
    CONSISTENCY("Consistency", "Variance in performance across sessions"),
    
    /**
     * Rank by recent performance trends
     */
    RECENT_TREND("Recent Trend", "Performance trajectory over last 4 weeks");
    
    /**
     * Gets the weight/importance of this metric in combined scoring
     */
    fun getWeight(): Float {
        return when (this) {
            PERFORMANCE_SCORE -> 1.0f
            VOLUME_GROWTH -> 0.4f
            STRENGTH_GROWTH -> 0.4f
            FREQUENCY -> 0.2f
            CONSISTENCY -> 0.3f
            RECENT_TREND -> 0.5f
        }
    }
    
    /**
     * Checks if higher values are better for this metric
     */
    fun isHigherBetter(): Boolean {
        return when (this) {
            PERFORMANCE_SCORE, VOLUME_GROWTH, STRENGTH_GROWTH, 
            FREQUENCY, RECENT_TREND -> true
            CONSISTENCY -> false // Lower variance is better
        }
    }
    
    companion object {
        /**
         * Get metrics suitable for primary ranking
         */
        fun getPrimaryMetrics(): List<RankingMetric> {
            return listOf(PERFORMANCE_SCORE, VOLUME_GROWTH, STRENGTH_GROWTH)
        }
        
        /**
         * Get metrics for detailed analysis
         */
        fun getDetailMetrics(): List<RankingMetric> {
            return values().toList()
        }
    }
}