package com.example.liftrix.domain.model.analytics

/**
 * Consistency metrics for workout consistency and habit tracking analysis
 * 
 * Provides comprehensive consistency analytics including:
 * - Current and longest workout streak tracking
 * - Average rest days between workouts for pattern analysis
 * - Workout frequency within time period analysis
 * - Streak type classification for different consistency patterns
 * - Consistency scoring and habit formation insights
 * 
 * Used by:
 * - AnalyticsMapper for consistency calculations
 * - ConsistencyMetricCard for UI display
 * - ProgressMetrics for comprehensive analytics
 * - Dashboard widgets for consistency visualization
 */
data class ConsistencyMetrics(
    val currentStreak: Int, // Current consecutive workout streak
    val longestStreak: Int, // Longest consecutive workout streak achieved
    val averageRestDays: Float, // Average rest days between workouts
    val workoutDaysInPeriod: Int, // Number of workout days in the period
    val totalDaysInPeriod: Int, // Total days in the analysis period
    val streakType: StreakType // Type of streak being tracked
) {
    init {
        require(currentStreak >= 0) { "Current streak cannot be negative: $currentStreak" }
        require(longestStreak >= 0) { "Longest streak cannot be negative: $longestStreak" }
        require(longestStreak >= currentStreak) { "Longest streak must be >= current streak: $longestStreak < $currentStreak" }
        require(averageRestDays >= 0.0f) { "Average rest days cannot be negative: $averageRestDays" }
        require(workoutDaysInPeriod >= 0) { "Workout days in period cannot be negative: $workoutDaysInPeriod" }
        require(totalDaysInPeriod > 0) { "Total days in period must be positive: $totalDaysInPeriod" }
        require(workoutDaysInPeriod <= totalDaysInPeriod) { "Workout days cannot exceed total days: $workoutDaysInPeriod > $totalDaysInPeriod" }
    }
    
    companion object {
        const val EXCELLENT_CONSISTENCY_THRESHOLD = 0.8f
        const val GOOD_CONSISTENCY_THRESHOLD = 0.6f
        const val FAIR_CONSISTENCY_THRESHOLD = 0.4f
        val IDEAL_REST_DAYS_RANGE = 1..3
        const val STREAK_MILESTONE_WEEK = 7
        const val STREAK_MILESTONE_MONTH = 30
        
        /**
         * Creates empty consistency metrics with zero values
         */
        fun empty(): ConsistencyMetrics = ConsistencyMetrics(
            currentStreak = 0,
            longestStreak = 0,
            averageRestDays = 0.0f,
            workoutDaysInPeriod = 0,
            totalDaysInPeriod = 1,
            streakType = StreakType.WORKOUT_DAYS
        )
    }
    
    /**
     * Calculates consistency rate as a percentage (0.0 to 1.0)
     */
    fun getConsistencyRate(): Float = workoutDaysInPeriod.toFloat() / totalDaysInPeriod.toFloat()
    
    /**
     * Gets consistency level based on consistency rate
     */
    fun getConsistencyLevel(): ConsistencyLevel = when {
        getConsistencyRate() >= EXCELLENT_CONSISTENCY_THRESHOLD -> ConsistencyLevel.EXCELLENT
        getConsistencyRate() >= GOOD_CONSISTENCY_THRESHOLD -> ConsistencyLevel.GOOD
        getConsistencyRate() >= FAIR_CONSISTENCY_THRESHOLD -> ConsistencyLevel.FAIR
        getConsistencyRate() >= 0.2f -> ConsistencyLevel.POOR
        else -> ConsistencyLevel.VERY_POOR
    }
    
    /**
     * Gets rest pattern level based on average rest days
     */
    fun getRestPatternLevel(): RestPatternLevel = when {
        averageRestDays.toInt() in IDEAL_REST_DAYS_RANGE -> RestPatternLevel.OPTIMAL
        averageRestDays < 1.0f -> RestPatternLevel.TOO_FREQUENT
        averageRestDays > 5.0f -> RestPatternLevel.TOO_INFREQUENT
        averageRestDays > 3.0f -> RestPatternLevel.SLIGHTLY_INFREQUENT
        else -> RestPatternLevel.SLIGHTLY_FREQUENT
    }
    
    /**
     * Gets streak milestone level
     */
    fun getStreakMilestone(): StreakMilestone = when {
        currentStreak >= STREAK_MILESTONE_MONTH -> StreakMilestone.MONTH_PLUS
        currentStreak >= STREAK_MILESTONE_WEEK -> StreakMilestone.WEEK_PLUS
        currentStreak >= 3 -> StreakMilestone.SEVERAL_DAYS
        currentStreak >= 1 -> StreakMilestone.STARTED
        else -> StreakMilestone.NONE
    }
    
    /**
     * Calculates consistency rate as a formatted percentage
     */
    fun getConsistencyRateFormatted(): String = "${(getConsistencyRate() * 100).toInt()}%"
    
    /**
     * Calculates days since last workout (assumes today is end of period)
     */
    fun getDaysSinceLastWorkout(): Int = if (currentStreak > 0) 0 else 1
    
    /**
     * Checks if user is maintaining an active streak
     */
    fun hasActiveStreak(): Boolean = currentStreak > 0
    
    /**
     * Checks if user has achieved a significant streak milestone
     */
    fun hasSignificantStreak(): Boolean = currentStreak >= STREAK_MILESTONE_WEEK
    
    /**
     * Gets streak motivation message
     */
    fun getStreakMotivationMessage(): String = when (getStreakMilestone()) {
        StreakMilestone.MONTH_PLUS -> "Incredible! You've maintained a month-long streak!"
        StreakMilestone.WEEK_PLUS -> "Amazing! You've hit a week-long streak!"
        StreakMilestone.SEVERAL_DAYS -> "Great job! You're building a solid streak!"
        StreakMilestone.STARTED -> "Good start! Keep building your streak!"
        StreakMilestone.NONE -> "Ready to start a new streak? You've got this!"
    }
    
    /**
     * Gets consistency recommendation
     */
    fun getConsistencyRecommendation(): String = when (getConsistencyLevel()) {
        ConsistencyLevel.EXCELLENT -> "Outstanding consistency! You're building excellent habits."
        ConsistencyLevel.GOOD -> "Great consistency! Consider minor adjustments to reach excellence."
        ConsistencyLevel.FAIR -> "Fair consistency. Try to reduce gaps between workouts."
        ConsistencyLevel.POOR -> "Consistency needs improvement. Set realistic workout schedules."
        ConsistencyLevel.VERY_POOR -> "Focus on building a regular workout habit, even if it's just 2-3 days per week."
    }
    
    /**
     * Calculates overall consistency score (0.0 to 1.0) considering multiple factors
     */
    fun getOverallConsistencyScore(): Float {
        val consistencyRateScore = getConsistencyRate()
        val streakScore = (currentStreak / 30.0f).coerceIn(0.0f, 1.0f) // Up to 30 days = max score
        val restPatternScore = when (getRestPatternLevel()) {
            RestPatternLevel.OPTIMAL -> 1.0f
            RestPatternLevel.SLIGHTLY_FREQUENT, RestPatternLevel.SLIGHTLY_INFREQUENT -> 0.8f
            RestPatternLevel.TOO_FREQUENT, RestPatternLevel.TOO_INFREQUENT -> 0.5f
        }
        val milestoneScore = when (getStreakMilestone()) {
            StreakMilestone.MONTH_PLUS -> 1.0f
            StreakMilestone.WEEK_PLUS -> 0.8f
            StreakMilestone.SEVERAL_DAYS -> 0.6f
            StreakMilestone.STARTED -> 0.4f
            StreakMilestone.NONE -> 0.0f
        }
        
        return (consistencyRateScore * 0.4f + streakScore * 0.3f + restPatternScore * 0.2f + milestoneScore * 0.1f)
    }
}

/**
 * Enum representing different consistency levels
 */
enum class ConsistencyLevel(val displayName: String, val description: String) {
    EXCELLENT("Excellent", "Highly consistent workout schedule (80%+)"),
    GOOD("Good", "Consistent workout schedule (60-80%)"),
    FAIR("Fair", "Somewhat consistent workout schedule (40-60%)"),
    POOR("Poor", "Inconsistent workout schedule (20-40%)"),
    VERY_POOR("Very Poor", "Highly irregular workout schedule (<20%)")
}

/**
 * Enum representing different rest pattern levels
 */
enum class RestPatternLevel(val displayName: String, val description: String) {
    OPTIMAL("Optimal", "Ideal rest pattern (1-3 days between workouts)"),
    SLIGHTLY_FREQUENT("Slightly Frequent", "Working out slightly more frequently"),
    SLIGHTLY_INFREQUENT("Slightly Infrequent", "Working out slightly less frequently"),
    TOO_FREQUENT("Too Frequent", "Working out too frequently (risk of overtraining)"),
    TOO_INFREQUENT("Too Infrequent", "Working out too infrequently (gaps too long)")
}

/**
 * Enum representing different streak milestone levels
 */
enum class StreakMilestone(val displayName: String, val description: String) {
    NONE("None", "No active streak"),
    STARTED("Started", "Beginning of streak (1-2 days)"),
    SEVERAL_DAYS("Several Days", "Short streak (3-6 days)"),
    WEEK_PLUS("Week Plus", "Week-long streak (7-29 days)"),
    MONTH_PLUS("Month Plus", "Month-long streak (30+ days)")
}