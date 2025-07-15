package com.example.liftrix.domain.model.analytics

import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.Weight

/**
 * Strength metrics for comprehensive strength progression tracking and analysis
 * 
 * Provides detailed strength analytics including:
 * - Personal record tracking with chronological progression
 * - Overall strength progression scoring and trend analysis
 * - Recent PR achievement counts for motivation
 * - Volume load progression for training load optimization
 * - One-rep max estimates for strength benchmarking
 * 
 * Used by:
 * - AnalyticsMapper for strength calculations
 * - StrengthMetricCard for UI display
 * - ProgressMetrics for comprehensive analytics
 * - Dashboard widgets for strength visualization
 */
data class StrengthMetrics(
    val personalRecords: List<PersonalRecord>,
    val strengthProgression: Float, // Overall strength progression percentage
    val recentPRCount: Int, // Number of PRs in recent period
    val volumeLoadProgression: Float, // Volume load progression percentage
    val oneRepMaxEstimates: Map<ExerciseId, Weight> // Exercise ID to estimated 1RM
) {
    init {
        require(strengthProgression >= -1.0f) { "Strength progression cannot be less than -100%: $strengthProgression" }
        require(recentPRCount >= 0) { "Recent PR count cannot be negative: $recentPRCount" }
        require(volumeLoadProgression >= -1.0f) { "Volume load progression cannot be less than -100%: $volumeLoadProgression" }
        require(oneRepMaxEstimates.values.all { it >= Weight.ZERO }) { "One rep max estimates cannot be negative" }
    }
    
    companion object {
        const val EXCELLENT_STRENGTH_PROGRESSION_THRESHOLD = 0.1f // 10%
        const val GOOD_STRENGTH_PROGRESSION_THRESHOLD = 0.05f // 5%
        const val RECENT_PR_PERIOD_DAYS = 30
        
        /**
         * Creates empty strength metrics with zero values
         */
        fun empty(): StrengthMetrics = StrengthMetrics(
            personalRecords = emptyList(),
            strengthProgression = 0.0f,
            recentPRCount = 0,
            volumeLoadProgression = 0.0f,
            oneRepMaxEstimates = emptyMap()
        )
    }
    
    /**
     * Gets strength progression level based on progression percentage
     */
    fun getStrengthProgressionLevel(): StrengthProgressionLevel = when {
        strengthProgression >= EXCELLENT_STRENGTH_PROGRESSION_THRESHOLD -> StrengthProgressionLevel.EXCELLENT
        strengthProgression >= GOOD_STRENGTH_PROGRESSION_THRESHOLD -> StrengthProgressionLevel.GOOD
        strengthProgression >= 0.01f -> StrengthProgressionLevel.MODERATE
        strengthProgression >= -0.01f -> StrengthProgressionLevel.STABLE
        strengthProgression >= -0.05f -> StrengthProgressionLevel.SLIGHT_DECLINE
        else -> StrengthProgressionLevel.SIGNIFICANT_DECLINE
    }
    
    /**
     * Gets volume load progression level
     */
    fun getVolumeLoadProgressionLevel(): VolumeLoadProgressionLevel = when {
        volumeLoadProgression >= 0.15f -> VolumeLoadProgressionLevel.VERY_HIGH
        volumeLoadProgression >= 0.08f -> VolumeLoadProgressionLevel.HIGH
        volumeLoadProgression >= 0.03f -> VolumeLoadProgressionLevel.MODERATE
        volumeLoadProgression >= -0.03f -> VolumeLoadProgressionLevel.STABLE
        volumeLoadProgression >= -0.08f -> VolumeLoadProgressionLevel.DECLINING
        else -> VolumeLoadProgressionLevel.SIGNIFICANTLY_DECLINING
    }
    
    /**
     * Calculates the strength progression as a formatted string
     */
    fun getStrengthProgressionFormatted(): String = formatPercentageChange(strengthProgression)
    
    /**
     * Calculates the volume load progression as a formatted string
     */
    fun getVolumeLoadProgressionFormatted(): String = formatPercentageChange(volumeLoadProgression)
    
    /**
     * Gets the most recent personal record
     */
    fun getMostRecentPR(): PersonalRecord? = personalRecords.maxByOrNull { it.achievedDate }
    
    /**
     * Gets personal records for a specific exercise
     */
    fun getPRsForExercise(exerciseId: ExerciseId): List<PersonalRecord> = 
        personalRecords.filter { it.exerciseId == exerciseId }
    
    /**
     * Calculates total estimated 1RM across all exercises
     */
    fun getTotalEstimated1RM(): Weight = oneRepMaxEstimates.values.fold(Weight.ZERO) { acc, weight -> acc + weight }
    
    /**
     * Gets the strongest lift based on 1RM estimates
     */
    fun getStrongestLift(): Pair<ExerciseId, Weight>? = oneRepMaxEstimates.maxByOrNull { (_, weight: Weight) -> weight.kilograms }?.toPair()
    
    /**
     * Calculates average weight per PR
     */
    fun getAveragePRWeight(): Weight = if (personalRecords.isNotEmpty()) {
        val totalWeight = personalRecords.fold(Weight.ZERO) { acc, pr -> acc + pr.weight }
        Weight(totalWeight.kilograms / personalRecords.size)
    } else Weight.ZERO
    
    /**
     * Checks if user has achieved recent PRs
     */
    fun hasRecentPRs(): Boolean = recentPRCount > 0
    
    /**
     * Gets strength improvement recommendation
     */
    fun getStrengthRecommendation(): String = when (getStrengthProgressionLevel()) {
        StrengthProgressionLevel.EXCELLENT -> "Outstanding strength progress! Keep up the excellent work."
        StrengthProgressionLevel.GOOD -> "Great strength gains! Consider progressive overload for continued improvement."
        StrengthProgressionLevel.MODERATE -> "Steady progress. Focus on compound movements and consistency."
        StrengthProgressionLevel.STABLE -> "Strength is maintaining. Consider varying your training stimulus."
        StrengthProgressionLevel.SLIGHT_DECLINE -> "Minor strength decline. Review your training program and recovery."
        StrengthProgressionLevel.SIGNIFICANT_DECLINE -> "Significant strength decline. Consider deload week and program adjustment."
    }
    
    /**
     * Calculates overall strength score (0.0 to 1.0) considering multiple factors
     */
    fun getOverallStrengthScore(): Float {
        val progressionScore = ((strengthProgression + 0.2f) / 0.4f).coerceIn(0.0f, 1.0f) // Normalize -20% to +20%
        val volumeLoadScore = ((volumeLoadProgression + 0.3f) / 0.6f).coerceIn(0.0f, 1.0f) // Normalize -30% to +30%
        val prScore = (recentPRCount / 5.0f).coerceIn(0.0f, 1.0f) // Up to 5 PRs = max score
        val consistencyScore = if (personalRecords.isNotEmpty()) 1.0f else 0.0f
        
        return (progressionScore * 0.4f + volumeLoadScore * 0.3f + prScore * 0.2f + consistencyScore * 0.1f)
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
 * Enum representing different strength progression levels
 */
enum class StrengthProgressionLevel(val displayName: String, val description: String) {
    EXCELLENT("Excellent", "Outstanding strength gains (10%+)"),
    GOOD("Good", "Strong progression (5-10%)"),
    MODERATE("Moderate", "Steady improvement (1-5%)"),
    STABLE("Stable", "Maintaining strength (±1%)"),
    SLIGHT_DECLINE("Slight Decline", "Minor strength loss (1-5%)"),
    SIGNIFICANT_DECLINE("Significant Decline", "Notable strength loss (5%+)")
}

/**
 * Enum representing different volume load progression levels
 */
enum class VolumeLoadProgressionLevel(val displayName: String, val description: String) {
    VERY_HIGH("Very High", "Exceptional volume load increase (15%+)"),
    HIGH("High", "High volume load increase (8-15%)"),
    MODERATE("Moderate", "Moderate volume load increase (3-8%)"),
    STABLE("Stable", "Stable volume load (±3%)"),
    DECLINING("Declining", "Declining volume load (3-8%)"),
    SIGNIFICANTLY_DECLINING("Significantly Declining", "Major volume load decline (8%+)")
}