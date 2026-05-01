package com.example.liftrix.domain.model.analytics

/**
 * Enum representing different risk levels for training and recovery assessment
 * 
 * Defines risk categories for various aspects of training analysis:
 * - Overreaching and overtraining risk assessment
 * - Injury risk based on training patterns
 * - Recovery insufficiency risk evaluation
 * - Performance decline risk indicators
 * 
 * Used by:
 * - RecoveryMetrics for overreaching risk assessment
 * - AnalyticsMapper for risk level calculations
 * - UI components for risk level visualization and warnings
 * - Dashboard widgets for training optimization alerts
 */
enum class RiskLevel(
    val displayName: String,
    val description: String,
    val colorIndicator: String,
    val actionRequired: Boolean
) {
    /**
     * Low risk level - optimal training conditions
     */
    LOW(
        displayName = "Low",
        description = "Optimal training conditions with minimal risk",
        colorIndicator = "green",
        actionRequired = false
    ),
    
    /**
     * Medium risk level - caution recommended
     */
    MEDIUM(
        displayName = "Medium",
        description = "Moderate risk requiring monitoring and potential adjustments",
        colorIndicator = "yellow",
        actionRequired = true
    ),
    
    /**
     * High risk level - immediate action required
     */
    HIGH(
        displayName = "High",
        description = "High risk requiring immediate attention and intervention",
        colorIndicator = "red",
        actionRequired = true
    );
    
    /**
     * Gets risk assessment message based on risk level
     */
    fun getRiskAssessmentMessage(): String = when (this) {
        LOW -> "Your training pattern shows low risk. Keep up the good work!"
        MEDIUM -> "Moderate risk detected. Consider adjusting your training intensity or adding more recovery time."
        HIGH -> "High risk detected. Immediate action recommended: reduce training intensity and prioritize recovery."
    }
    
    /**
     * Gets specific recommendations based on risk level
     */
    fun getRecommendations(): List<String> = when (this) {
        LOW -> listOf(
            "Continue current training pattern",
            "Maintain consistent recovery schedule",
            "Monitor for any changes in fatigue levels"
        )
        MEDIUM -> listOf(
            "Monitor fatigue levels closely",
            "Consider reducing training intensity by 10-20%",
            "Add an extra rest day if possible",
            "Focus on sleep quality and nutrition",
            "Consider active recovery activities"
        )
        HIGH -> listOf(
            "Reduce training intensity by 30-50%",
            "Add 2-3 extra rest days this week",
            "Prioritize sleep (8+ hours nightly)",
            "Focus on stress management",
            "Consider scheduling a deload week",
            "Consult with a fitness professional if symptoms persist"
        )
    }
    
    /**
     * Gets warning signs associated with this risk level
     */
    fun getWarningSignsDescription(): String = when (this) {
        LOW -> "No significant warning signs. Training patterns are within healthy ranges."
        MEDIUM -> "Warning signs may include: slightly elevated fatigue, minor sleep disruption, or small decreases in performance."
        HIGH -> "Warning signs include: persistent fatigue, sleep disturbances, mood changes, performance decline, or increased injury susceptibility."
    }
    
    /**
     * Gets urgency level for addressing this risk
     */
    fun getUrgencyLevel(): RiskUrgencyLevel = when (this) {
        LOW -> RiskUrgencyLevel.NONE
        MEDIUM -> RiskUrgencyLevel.MODERATE
        HIGH -> RiskUrgencyLevel.URGENT
    }
    
    /**
     * Checks if this risk level requires immediate attention
     */
    fun requiresImmediateAttention(): Boolean = this == HIGH
    
    /**
     * Checks if this risk level requires monitoring
     */
    fun requiresMonitoring(): Boolean = this in listOf(MEDIUM, HIGH)
    
    /**
     * Gets risk level from a numeric score (0.0 to 1.0)
     */
    companion object {
        fun fromScore(score: Float): RiskLevel = when {
            score <= 0.3f -> LOW
            score <= 0.7f -> MEDIUM
            else -> HIGH
        }
        
        /**
         * Gets risk level based on multiple factors
         */
        fun fromFactors(
            overtrainingIndicators: Int,
            recoveryScore: Float,
            injuryHistory: Int,
            trainingLoad: Float
        ): RiskLevel {
            val riskScore = (overtrainingIndicators * 0.3f + 
                           (1.0f - recoveryScore) * 0.3f + 
                           injuryHistory * 0.2f + 
                           trainingLoad * 0.2f).coerceIn(0.0f, 1.0f)
            
            return fromScore(riskScore)
        }
    }
}

/**
 * Enum representing different urgency levels for risk response
 */
enum class RiskUrgencyLevel(val displayName: String, val description: String) {
    NONE("None", "No urgent action required"),
    MODERATE("Moderate", "Action recommended within a few days"),
    URGENT("Urgent", "Immediate action required")
}