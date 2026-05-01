package com.example.liftrix.domain.model.analytics

/**
 * Frequency metrics for workout frequency and consistency analysis
 * 
 * Provides comprehensive frequency analytics including:
 * - Total workout count and average frequency calculations
 * - Week-over-week frequency progression tracking
 * - Target frequency achievement rate monitoring
 * - Consistency scoring based on workout distribution
 * - Gap analysis between workouts for pattern identification
 * 
 * Used by:
 * - AnalyticsMapper for frequency calculations
 * - FrequencyMetricCard for UI display
 * - ProgressMetrics for comprehensive analytics
 * - Dashboard widgets for frequency visualization
 */
data class FrequencyMetrics(
    val workoutCount: Int,
    val averageWorkoutsPerWeek: Float,
    val weekOverWeekChange: Float, // Percentage change (-1.0 to 1.0+)
    val targetFrequencyAchievement: Float, // Achievement rate (0.0 to 1.0+)
    val consistencyScore: Float, // Consistency score (0.0 to 1.0)
    val longestGap: Int, // Longest gap between workouts in days
    val shortestGap: Int // Shortest gap between workouts in days
) {
    init {
        require(workoutCount >= 0) { "Workout count cannot be negative: $workoutCount" }
        require(averageWorkoutsPerWeek >= 0.0f) { "Average workouts per week cannot be negative: $averageWorkoutsPerWeek" }
        require(weekOverWeekChange >= -1.0f) { "Week over week change cannot be less than -100%: $weekOverWeekChange" }
        require(targetFrequencyAchievement >= 0.0f) { "Target frequency achievement cannot be negative: $targetFrequencyAchievement" }
        require(consistencyScore in 0.0f..1.0f) { "Consistency score must be between 0.0 and 1.0: $consistencyScore" }
        require(longestGap >= 0) { "Longest gap cannot be negative: $longestGap" }
        require(shortestGap >= 0) { "Shortest gap cannot be negative: $shortestGap" }
        if (workoutCount > 1) {
            require(shortestGap <= longestGap) { "Shortest gap cannot be greater than longest gap: $shortestGap > $longestGap" }
        }
    }
    
    companion object {
        const val DEFAULT_TARGET_WORKOUTS_PER_WEEK = 3.0f
        const val EXCELLENT_CONSISTENCY_THRESHOLD = 0.8f
        const val GOOD_CONSISTENCY_THRESHOLD = 0.6f
        val IDEAL_WORKOUT_GAP_DAYS = 1..3
        
        /**
         * Creates empty frequency metrics with zero values
         */
        fun empty(): FrequencyMetrics = FrequencyMetrics(
            workoutCount = 0,
            averageWorkoutsPerWeek = 0.0f,
            weekOverWeekChange = 0.0f,
            targetFrequencyAchievement = 0.0f,
            consistencyScore = 0.0f,
            longestGap = 0,
            shortestGap = 0
        )
    }
    
    /**
     * Gets frequency level based on workouts per week
     */
    fun getFrequencyLevel(): FrequencyLevel = when {
        averageWorkoutsPerWeek >= 5.0f -> FrequencyLevel.VERY_HIGH
        averageWorkoutsPerWeek >= 4.0f -> FrequencyLevel.HIGH
        averageWorkoutsPerWeek >= 3.0f -> FrequencyLevel.MODERATE
        averageWorkoutsPerWeek >= 2.0f -> FrequencyLevel.LOW
        averageWorkoutsPerWeek >= 1.0f -> FrequencyLevel.VERY_LOW
        else -> FrequencyLevel.INACTIVE
    }
    
    /**
     * Gets consistency level based on consistency score
     */
    fun getConsistencyLevel(): FrequencyConsistencyLevel = when {
        consistencyScore >= EXCELLENT_CONSISTENCY_THRESHOLD -> FrequencyConsistencyLevel.EXCELLENT
        consistencyScore >= GOOD_CONSISTENCY_THRESHOLD -> FrequencyConsistencyLevel.GOOD
        consistencyScore >= 0.4f -> FrequencyConsistencyLevel.FAIR
        consistencyScore >= 0.2f -> FrequencyConsistencyLevel.POOR
        else -> FrequencyConsistencyLevel.VERY_POOR
    }
    
    /**
     * Calculates the percentage change as a formatted string
     */
    fun getWeekOverWeekChangeFormatted(): String = formatPercentageChange(weekOverWeekChange)
    
    /**
     * Gets target achievement as a formatted percentage
     */
    fun getTargetAchievementFormatted(): String = "${(targetFrequencyAchievement * 100).toInt()}%"
    
    /**
     * Calculates average gap between workouts
     */
    fun getAverageGap(): Float = if (workoutCount <= 1) 0.0f else (longestGap + shortestGap) / 2.0f
    
    /**
     * Checks if workout gaps are within ideal range
     */
    fun hasIdealWorkoutGaps(): Boolean = longestGap in IDEAL_WORKOUT_GAP_DAYS && shortestGap in IDEAL_WORKOUT_GAP_DAYS
    
    /**
     * Gets frequency recommendation based on current metrics
     */
    fun getFrequencyRecommendation(): String = when {
        averageWorkoutsPerWeek < 2.0f -> "Consider increasing workout frequency to at least 2-3 times per week"
        averageWorkoutsPerWeek > 6.0f -> "Consider adding rest days to prevent overtraining"
        longestGap > 5 -> "Try to maintain shorter gaps between workouts for better consistency"
        consistencyScore < GOOD_CONSISTENCY_THRESHOLD -> "Focus on maintaining a more regular workout schedule"
        else -> "Great job maintaining a consistent workout frequency!"
    }
    
    /**
     * Calculates workout frequency score (0.0 to 1.0) considering multiple factors
     */
    fun getOverallFrequencyScore(): Float {
        val frequencyScore = (averageWorkoutsPerWeek / 5.0f).coerceIn(0.0f, 1.0f)
        val achievementScore = targetFrequencyAchievement.coerceIn(0.0f, 1.0f)
        val gapScore = if (longestGap > 0) (7.0f / longestGap).coerceIn(0.0f, 1.0f) else 1.0f
        
        return (frequencyScore * 0.4f + achievementScore * 0.3f + consistencyScore * 0.2f + gapScore * 0.1f)
    }
    
    /**
     * Formats percentage change with proper sign and percentage symbol
     */
    private fun formatPercentageChange(change: Float): String {
        val percentage = (change * 100).toInt()
        return when {
            percentage > 0 -> "+$percentage%"
            percentage < 0 -> "$percentage%"
            else -> "0%"
        }
    }
}

/**
 * Enum representing different frequency levels
 */
enum class FrequencyLevel(val displayName: String, val description: String) {
    INACTIVE("Inactive", "Less than 1 workout per week"),
    VERY_LOW("Very Low", "1-2 workouts per week"),
    LOW("Low", "2-3 workouts per week"),
    MODERATE("Moderate", "3-4 workouts per week"),
    HIGH("High", "4-5 workouts per week"),
    VERY_HIGH("Very High", "5+ workouts per week")
}

/**
 * Enum representing different frequency consistency levels
 */
enum class FrequencyConsistencyLevel(val displayName: String, val description: String) {
    VERY_POOR("Very Poor", "Highly irregular workout schedule"),
    POOR("Poor", "Inconsistent workout schedule"),
    FAIR("Fair", "Somewhat consistent workout schedule"),
    GOOD("Good", "Consistent workout schedule"),
    EXCELLENT("Excellent", "Highly consistent workout schedule")
}