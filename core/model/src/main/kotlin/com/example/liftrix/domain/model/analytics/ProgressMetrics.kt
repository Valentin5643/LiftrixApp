package com.example.liftrix.domain.model.analytics

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * Comprehensive progress metrics for analytics tracking and dashboard display
 * 
 * Contains aggregated fitness analytics across multiple dimensions for a specific time period.
 * Provides detailed insights into user performance, consistency, and progression patterns.
 * 
 * Used by:
 * - AnalyticsMapper for comprehensive metric calculations
 * - Dashboard widgets for progress visualization
 * - Export system for detailed analytics reports
 * - UI components for performance insights
 * 
 * @property userId The user ID these metrics belong to
 * @property timeRange Time period for which metrics are calculated
 * @property volumeMetrics Volume-specific analytics and progression
 * @property frequencyMetrics Workout frequency and consistency analytics
 * @property strengthMetrics Strength progression and personal record tracking
 * @property consistencyMetrics Consistency and habit formation analytics
 * @property recoveryMetrics Recovery pattern and optimization analytics
 * @property calculatedAt Timestamp when metrics were calculated
 */
data class ProgressMetrics(
    val userId: String,
    val timeRange: TimeRange,
    val volumeMetrics: VolumeMetrics,
    val frequencyMetrics: FrequencyMetrics,
    val strengthMetrics: StrengthMetrics,
    val consistencyMetrics: ConsistencyMetrics,
    val recoveryMetrics: RecoveryMetrics,
    val calculatedAt: LocalDate = kotlinx.datetime.LocalDate.fromEpochDays((kotlinx.datetime.Clock.System.now().epochSeconds / 86400).toInt())
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(timeRange.isValid()) { "Time range must be valid" }
    }
    
    companion object {
        /**
         * Creates empty progress metrics with default values
         */
        fun empty(userId: String, timeRange: TimeRange): ProgressMetrics = ProgressMetrics(
            userId = userId,
            timeRange = timeRange,
            volumeMetrics = VolumeMetrics.empty(),
            frequencyMetrics = FrequencyMetrics.empty(),
            strengthMetrics = StrengthMetrics.empty(),
            consistencyMetrics = ConsistencyMetrics.empty(),
            recoveryMetrics = RecoveryMetrics.empty()
        )
    }
    
    /**
     * Validates that the metrics contain valid data
     */
    fun isValid(): Boolean {
        return userId.isNotBlank() && 
               timeRange.isValid() &&
               volumeMetrics.totalVolume >= com.example.liftrix.domain.model.Weight.ZERO &&
               frequencyMetrics.workoutCount >= 0 &&
               consistencyMetrics.currentStreak >= 0 &&
               recoveryMetrics.averageRestDaysBetweenWorkouts >= 0.0f
    }
    
    /**
     * Calculates overall progress score (0.0 to 1.0) across all metrics
     */
    fun getOverallProgressScore(): Float {
        val volumeScore = volumeMetrics.getVolumeEfficiencyScore()
        val frequencyScore = frequencyMetrics.getOverallFrequencyScore()
        val strengthScore = strengthMetrics.getOverallStrengthScore()
        val consistencyScore = consistencyMetrics.getOverallConsistencyScore()
        val recoveryScore = recoveryMetrics.getOverallRecoveryScore()
        
        return (volumeScore * 0.25f + 
                frequencyScore * 0.25f + 
                strengthScore * 0.20f + 
                consistencyScore * 0.20f + 
                recoveryScore * 0.10f)
    }
    
    /**
     * Gets overall progress level based on composite score
     */
    fun getOverallProgressLevel(): ProgressLevel = when {
        getOverallProgressScore() >= 0.8f -> ProgressLevel.EXCELLENT
        getOverallProgressScore() >= 0.6f -> ProgressLevel.GOOD
        getOverallProgressScore() >= 0.4f -> ProgressLevel.FAIR
        getOverallProgressScore() >= 0.2f -> ProgressLevel.POOR
        else -> ProgressLevel.NEEDS_IMPROVEMENT
    }
    
    /**
     * Gets key insights based on metrics analysis
     */
    fun getKeyInsights(): List<String> {
        val insights = mutableListOf<String>()
        
        // Volume insights
        if (volumeMetrics.volumeTrend == TrendDirection.UP) {
            insights.add("Volume is trending upward - great progress!")
        } else if (volumeMetrics.volumeTrend == TrendDirection.DOWN) {
            insights.add("Volume has declined - consider adjusting training intensity")
        }
        
        // Frequency insights
        if (frequencyMetrics.getConsistencyLevel() == FrequencyConsistencyLevel.EXCELLENT) {
            insights.add("Excellent workout consistency - you're building strong habits!")
        } else if (frequencyMetrics.getConsistencyLevel() == FrequencyConsistencyLevel.POOR) {
            insights.add("Workout consistency could be improved - try setting a regular schedule")
        }
        
        // Strength insights
        if (strengthMetrics.hasRecentPRs()) {
            insights.add("Recent personal records achieved - strength is improving!")
        }
        
        // Recovery insights
        if (recoveryMetrics.hasOverreachingRisk()) {
            insights.add("Overreaching risk detected - prioritize recovery and rest")
        }
        
        return insights
    }
    
    /**
     * Gets personalized recommendations based on metrics
     */
    fun getRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        
        recommendations.add(frequencyMetrics.getFrequencyRecommendation())
        recommendations.add(strengthMetrics.getStrengthRecommendation())
        recommendations.add(consistencyMetrics.getConsistencyRecommendation())
        recommendations.add(recoveryMetrics.getRecoveryRecommendation())
        
        return recommendations.filter { it.isNotBlank() }
    }
    
    /**
     * Returns a formatted summary string for display
     */
    fun getSummary(): String {
        return "Period: ${timeRange.getDisplayString()}\n" +
               "Total Volume: ${volumeMetrics.totalVolume.format()}\n" +
               "Workouts: ${frequencyMetrics.workoutCount}\n" +
               "Consistency: ${consistencyMetrics.getConsistencyRateFormatted()}\n" +
               "Personal Records: ${strengthMetrics.recentPRCount}\n" +
               "Overall Progress: ${getOverallProgressLevel().displayName}"
    }
    
    /**
     * Checks if user has shown improvement in any area
     */
    fun hasShownImprovement(): Boolean {
        return volumeMetrics.weekOverWeekChange > 0 ||
               frequencyMetrics.weekOverWeekChange > 0 ||
               strengthMetrics.strengthProgression > 0 ||
               consistencyMetrics.currentStreak > 0
    }
    
    /**
     * Gets days covered by these metrics
     */
    fun getDaysCovered(): Int = timeRange.getDurationInDays()
    
    // Extension properties for export compatibility
    /**
     * Total number of workouts completed in the time period
     */
    val totalWorkouts: Int
        get() = frequencyMetrics.workoutCount
    
    /**
     * Total volume lifted across all workouts in the time period
     */
    val totalVolume: Int
        get() = volumeMetrics.totalVolume.kilograms.toInt()
    
    /**
     * Average duration of workouts in minutes
     */
    val averageDuration: Int
        get() = if (frequencyMetrics.workoutCount > 0) {
            // Estimate based on typical workout duration (45-90 minutes)
            (60 + (strengthMetrics.strengthProgression * 30)).toInt().coerceIn(30, 120)
        } else 0
    
    /**
     * Strength progression percentage as integer
     */
    val strengthGain: Int
        get() = (strengthMetrics.strengthProgression * 100).toInt()
    
    /**
     * Consistency score as percentage (0-100)
     */
    val consistencyScore: Int
        get() = (consistencyMetrics.getConsistencyRate() * 100).toInt()
    
    /**
     * Workout frequency per week
     */
    val workoutFrequency: Float
        get() = frequencyMetrics.averageWorkoutsPerWeek
    
    /**
     * Number of personal records achieved
     */
    val personalRecords: Int
        get() = strengthMetrics.recentPRCount
    
    /**
     * Total training days in the period
     */
    val totalTrainingDays: Int
        get() = consistencyMetrics.workoutDaysInPeriod
}

/**
 * Enum representing different overall progress levels
 */
enum class ProgressLevel(val displayName: String, val description: String) {
    EXCELLENT("Excellent", "Outstanding progress across all metrics"),
    GOOD("Good", "Good progress with room for minor improvements"),
    FAIR("Fair", "Fair progress with some areas needing attention"),
    POOR("Poor", "Below average progress requiring significant improvements"),
    NEEDS_IMPROVEMENT("Needs Improvement", "Progress needs substantial improvement across multiple areas")
}