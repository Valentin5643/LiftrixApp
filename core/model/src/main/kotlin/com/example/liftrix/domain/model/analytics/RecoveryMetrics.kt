package com.example.liftrix.domain.model.analytics

/**
 * Recovery metrics for workout recovery pattern analysis and optimization
 * 
 * Provides comprehensive recovery analytics including:
 * - Average rest days between workouts for pattern analysis
 * - Optimal rest day range recommendations based on training intensity
 * - Recovery pattern scoring for training optimization
 * - Overreaching risk assessment for injury prevention
 * - Under-recovery detection for performance optimization
 * - Personalized rest day recommendations
 * 
 * Used by:
 * - AnalyticsMapper for recovery calculations
 * - RecoveryMetricCard for UI display
 * - ProgressMetrics for comprehensive analytics
 * - Dashboard widgets for recovery optimization
 */
data class RecoveryMetrics(
    val averageRestDaysBetweenWorkouts: Float,
    val optimalRestDayRange: IntRange, // Recommended rest day range
    val recoveryPatternScore: Float, // Recovery pattern quality score (0.0 to 1.0)
    val overreachingRisk: RiskLevel, // Risk level for overreaching/overtraining
    val underrecoveryDays: Int, // Number of days with insufficient recovery
    val recommendedRestDays: Int // Recommended rest days based on current pattern
) {
    init {
        require(averageRestDaysBetweenWorkouts >= 0.0f) { "Average rest days cannot be negative: $averageRestDaysBetweenWorkouts" }
        require(optimalRestDayRange.first >= 0) { "Optimal rest day range start cannot be negative: ${optimalRestDayRange.first}" }
        require(optimalRestDayRange.last >= optimalRestDayRange.first) { "Optimal rest day range end must be >= start: ${optimalRestDayRange.last} < ${optimalRestDayRange.first}" }
        require(recoveryPatternScore in 0.0f..1.0f) { "Recovery pattern score must be between 0.0 and 1.0: $recoveryPatternScore" }
        require(underrecoveryDays >= 0) { "Under-recovery days cannot be negative: $underrecoveryDays" }
        require(recommendedRestDays >= 0) { "Recommended rest days cannot be negative: $recommendedRestDays" }
    }
    
    companion object {
        val DEFAULT_OPTIMAL_REST_RANGE = 1..3
        const val EXCELLENT_RECOVERY_THRESHOLD = 0.8f
        const val GOOD_RECOVERY_THRESHOLD = 0.6f
        const val FAIR_RECOVERY_THRESHOLD = 0.4f
        const val OVERREACHING_RISK_THRESHOLD = 0.3f
        const val HIGH_VOLUME_REST_MULTIPLIER = 1.2f
        
        /**
         * Creates empty recovery metrics with default values
         */
        fun empty(): RecoveryMetrics = RecoveryMetrics(
            averageRestDaysBetweenWorkouts = 0.0f,
            optimalRestDayRange = DEFAULT_OPTIMAL_REST_RANGE,
            recoveryPatternScore = 0.0f,
            overreachingRisk = RiskLevel.LOW,
            underrecoveryDays = 0,
            recommendedRestDays = 1
        )
    }
    
    /**
     * Gets recovery pattern level based on recovery pattern score
     */
    fun getRecoveryPatternLevel(): RecoveryPatternLevel = when {
        recoveryPatternScore >= EXCELLENT_RECOVERY_THRESHOLD -> RecoveryPatternLevel.EXCELLENT
        recoveryPatternScore >= GOOD_RECOVERY_THRESHOLD -> RecoveryPatternLevel.GOOD
        recoveryPatternScore >= FAIR_RECOVERY_THRESHOLD -> RecoveryPatternLevel.FAIR
        recoveryPatternScore >= 0.2f -> RecoveryPatternLevel.POOR
        else -> RecoveryPatternLevel.VERY_POOR
    }
    
    /**
     * Gets rest frequency level based on average rest days
     */
    fun getRestFrequencyLevel(): RestFrequencyLevel = when {
        averageRestDaysBetweenWorkouts < 0.5f -> RestFrequencyLevel.EXCESSIVE_TRAINING
        averageRestDaysBetweenWorkouts < 1.0f -> RestFrequencyLevel.VERY_FREQUENT
        averageRestDaysBetweenWorkouts.toInt() in optimalRestDayRange -> RestFrequencyLevel.OPTIMAL
        averageRestDaysBetweenWorkouts <= 5.0f -> RestFrequencyLevel.ADEQUATE
        averageRestDaysBetweenWorkouts <= 7.0f -> RestFrequencyLevel.INFREQUENT
        else -> RestFrequencyLevel.VERY_INFREQUENT
    }
    
    /**
     * Checks if current rest pattern is within optimal range
     */
    fun isRestPatternOptimal(): Boolean = averageRestDaysBetweenWorkouts.toInt() in optimalRestDayRange
    
    /**
     * Checks if there's risk of overreaching
     */
    fun hasOverreachingRisk(): Boolean = overreachingRisk in listOf(RiskLevel.MEDIUM, RiskLevel.HIGH)
    
    /**
     * Checks if there are under-recovery concerns
     */
    fun hasUnderrecoveryIssues(): Boolean = underrecoveryDays > 0
    
    /**
     * Gets recovery efficiency as a percentage
     */
    fun getRecoveryEfficiency(): Float = recoveryPatternScore * 100
    
    /**
     * Calculates under-recovery rate as a percentage
     */
    fun getUnderrecoveryRate(): Float = if (underrecoveryDays > 0) {
        (underrecoveryDays / 30.0f).coerceIn(0.0f, 1.0f) // Assuming 30-day analysis period
    } else 0.0f
    
    /**
     * Gets recovery recommendation based on current metrics
     */
    fun getRecoveryRecommendation(): String = when (getRecoveryPatternLevel()) {
        RecoveryPatternLevel.EXCELLENT -> "Excellent recovery pattern! Your rest schedule is optimal."
        RecoveryPatternLevel.GOOD -> "Good recovery pattern. Minor adjustments could optimize performance."
        RecoveryPatternLevel.FAIR -> "Fair recovery pattern. Consider adjusting rest days for better recovery."
        RecoveryPatternLevel.POOR -> "Poor recovery pattern. Increase rest days to prevent overtraining."
        RecoveryPatternLevel.VERY_POOR -> "Very poor recovery pattern. Take immediate action to improve rest and recovery."
    }
    
    /**
     * Gets specific recovery advice based on current pattern
     */
    fun getRecoveryAdvice(): String = when (getRestFrequencyLevel()) {
        RestFrequencyLevel.EXCESSIVE_TRAINING -> "You're training too frequently. Take at least 1-2 rest days between workouts."
        RestFrequencyLevel.VERY_FREQUENT -> "Consider taking more rest days to allow for proper recovery."
        RestFrequencyLevel.OPTIMAL -> "Your rest pattern is optimal. Continue this schedule for best results."
        RestFrequencyLevel.ADEQUATE -> "Your rest pattern is adequate. Consider slight adjustments for optimization."
        RestFrequencyLevel.INFREQUENT -> "You might benefit from more frequent training sessions."
        RestFrequencyLevel.VERY_INFREQUENT -> "Consider increasing workout frequency for better fitness gains."
    }
    
    /**
     * Gets overreaching risk warning if applicable
     */
    fun getOverreachingRiskWarning(): String? = when (overreachingRisk) {
        RiskLevel.HIGH -> "High overreaching risk detected. Consider deload week or extended rest."
        RiskLevel.MEDIUM -> "Moderate overreaching risk. Monitor fatigue levels and consider extra rest."
        RiskLevel.LOW -> null
    }
    
    /**
     * Calculates overall recovery score (0.0 to 1.0) considering multiple factors
     */
    fun getOverallRecoveryScore(): Float {
        val patternScore = recoveryPatternScore
        val restFrequencyScore = when (getRestFrequencyLevel()) {
            RestFrequencyLevel.OPTIMAL -> 1.0f
            RestFrequencyLevel.ADEQUATE -> 0.8f
            RestFrequencyLevel.VERY_FREQUENT, RestFrequencyLevel.INFREQUENT -> 0.6f
            RestFrequencyLevel.EXCESSIVE_TRAINING, RestFrequencyLevel.VERY_INFREQUENT -> 0.3f
        }
        val riskScore = when (overreachingRisk) {
            RiskLevel.LOW -> 1.0f
            RiskLevel.MEDIUM -> 0.6f
            RiskLevel.HIGH -> 0.2f
        }
        val underrecoveryScore = (1.0f - getUnderrecoveryRate()).coerceIn(0.0f, 1.0f)
        
        return (patternScore * 0.4f + restFrequencyScore * 0.3f + riskScore * 0.2f + underrecoveryScore * 0.1f)
    }
    
    /**
     * Gets formatted average rest days
     */
    fun getFormattedAverageRestDays(): String = "%.1f days".format(averageRestDaysBetweenWorkouts)
    
    /**
     * Gets formatted optimal rest range
     */
    fun getFormattedOptimalRange(): String = "${optimalRestDayRange.first}-${optimalRestDayRange.last} days"
}

/**
 * Enum representing different recovery pattern levels
 */
enum class RecoveryPatternLevel(val displayName: String, val description: String) {
    EXCELLENT("Excellent", "Optimal recovery pattern with perfect balance"),
    GOOD("Good", "Good recovery pattern with minor room for improvement"),
    FAIR("Fair", "Fair recovery pattern with some adjustments needed"),
    POOR("Poor", "Poor recovery pattern requiring significant changes"),
    VERY_POOR("Very Poor", "Very poor recovery pattern with high risk of overtraining")
}

/**
 * Enum representing different rest frequency levels
 */
enum class RestFrequencyLevel(val displayName: String, val description: String) {
    EXCESSIVE_TRAINING("Excessive Training", "Training too frequently without adequate rest"),
    VERY_FREQUENT("Very Frequent", "Training very frequently with minimal rest"),
    OPTIMAL("Optimal", "Optimal training frequency with proper rest"),
    ADEQUATE("Adequate", "Adequate training frequency with reasonable rest"),
    INFREQUENT("Infrequent", "Training less frequently than optimal"),
    VERY_INFREQUENT("Very Infrequent", "Training very infrequently with excessive rest")
}