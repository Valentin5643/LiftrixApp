package com.example.liftrix.domain.model

/**
 * Priority levels for fitness goals
 * Used to prioritize goals in UI and determine focus areas for recommendations
 */
enum class GoalPriority(
    val displayName: String,
    val description: String,
    val colorCode: String,
    val weight: Int,
    val urgencyLevel: Int
) {
    /**
     * Critical priority goals that require immediate attention
     * Health-related or time-sensitive goals
     */
    CRITICAL(
        displayName = "Critical",
        description = "Urgent health or time-sensitive goals",
        colorCode = "#D32F2F", // Red
        weight = 10,
        urgencyLevel = 1
    ),
    
    /**
     * High priority goals that are important to the user
     * Primary fitness objectives
     */
    HIGH(
        displayName = "High",
        description = "Primary fitness objectives",
        colorCode = "#F57C00", // Orange
        weight = 8,
        urgencyLevel = 2
    ),
    
    /**
     * Medium priority goals that support main objectives
     * Secondary fitness goals
     */
    MEDIUM(
        displayName = "Medium",
        description = "Secondary fitness goals",
        colorCode = "#FBC02D", // Yellow
        weight = 5,
        urgencyLevel = 3
    ),
    
    /**
     * Normal priority - alias for MEDIUM for backward compatibility
     * Secondary fitness goals
     */
    NORMAL(
        displayName = "Normal",
        description = "Secondary fitness goals",
        colorCode = "#FBC02D", // Yellow - same as MEDIUM
        weight = 5,
        urgencyLevel = 3
    ),
    
    /**
     * Low priority goals for long-term improvement
     * Nice-to-have objectives
     */
    LOW(
        displayName = "Low",
        description = "Long-term improvement goals",
        colorCode = "#388E3C", // Green
        weight = 3,
        urgencyLevel = 4
    ),
    
    /**
     * Optional goals without specific timeline
     * Aspirational objectives
     */
    OPTIONAL(
        displayName = "Optional",
        description = "Aspirational goals without timeline",
        colorCode = "#7B1FA2", // Purple
        weight = 1,
        urgencyLevel = 5
    );
    
    /**
     * Gets the recommended check-in frequency for this priority level
     */
    fun getCheckInFrequency(): CheckInFrequency = when (this) {
        CRITICAL -> CheckInFrequency.DAILY
        HIGH -> CheckInFrequency.WEEKLY
        MEDIUM, NORMAL -> CheckInFrequency.BIWEEKLY
        LOW -> CheckInFrequency.MONTHLY
        OPTIONAL -> CheckInFrequency.QUARTERLY
    }
    
    /**
     * Gets the maximum number of goals recommended for this priority
     */
    fun getMaxRecommendedGoals(): Int = when (this) {
        CRITICAL -> 2  // Focus on urgent items
        HIGH -> 3      // Main objectives
        MEDIUM, NORMAL -> 5    // Supporting goals
        LOW -> 8       // Many long-term goals ok
        OPTIONAL -> 10 // Unlimited aspirational goals
    }
    
    /**
     * Gets the notification importance level for this priority
     */
    fun getNotificationImportance(): NotificationImportance = when (this) {
        CRITICAL -> NotificationImportance.URGENT
        HIGH -> NotificationImportance.HIGH
        MEDIUM, NORMAL -> NotificationImportance.NORMAL
        LOW -> NotificationImportance.LOW
        OPTIONAL -> NotificationImportance.MINIMAL
    }
    
    /**
     * Gets the recommended time allocation percentage for this priority
     */
    fun getTimeAllocationPercentage(): Int = when (this) {
        CRITICAL -> 40  // 40% of workout time
        HIGH -> 35      // 35% of workout time
        MEDIUM, NORMAL -> 20    // 20% of workout time
        LOW -> 5        // 5% of workout time
        OPTIONAL -> 0   // No dedicated time
    }
    
    /**
     * Gets the escalation threshold (days without progress before escalating)
     */
    fun getEscalationThreshold(): Int = when (this) {
        CRITICAL -> 3   // 3 days
        HIGH -> 7       // 1 week
        MEDIUM, NORMAL -> 14    // 2 weeks
        LOW -> 30       // 1 month
        OPTIONAL -> 90  // 3 months
    }
    
    /**
     * Checks if this priority requires immediate action
     */
    fun requiresImmediateAction(): Boolean = this in listOf(CRITICAL, HIGH)
    
    /**
     * Checks if this priority supports long-term development
     */
    fun isLongTermFocused(): Boolean = this in listOf(LOW, OPTIONAL)
    
    /**
     * Gets the stress level associated with this priority
     */
    fun getStressLevel(): StressLevel = when (this) {
        CRITICAL -> StressLevel.HIGH
        HIGH -> StressLevel.MEDIUM
        MEDIUM, NORMAL -> StressLevel.LOW
        LOW -> StressLevel.MINIMAL
        OPTIONAL -> StressLevel.NONE
    }
    
    companion object {
        /**
         * Gets priorities ordered by urgency (most urgent first)
         */
        fun getByUrgency(): List<GoalPriority> = values().sortedBy { it.urgencyLevel }
        
        /**
         * Gets priorities ordered by weight (highest impact first)
         */
        fun getByWeight(): List<GoalPriority> = values().sortedByDescending { it.weight }
        
        /**
         * Gets active priorities that require regular attention
         */
        fun getActivePriorities(): List<GoalPriority> = listOf(CRITICAL, HIGH, MEDIUM, NORMAL)
        
        /**
         * Gets passive priorities for background goals
         */
        fun getPassivePriorities(): List<GoalPriority> = listOf(LOW, OPTIONAL)
        
        /**
         * Gets the default priority for new goals
         */
        fun getDefaultPriority(): GoalPriority = NORMAL
        
        /**
         * Gets priorities suitable for beginners
         */
        fun getBeginnerPriorities(): List<GoalPriority> = listOf(HIGH, MEDIUM, NORMAL, LOW)
        
        /**
         * Gets priorities suitable for advanced users
         */
        fun getAdvancedPriorities(): List<GoalPriority> = values().toList()
        
        /**
         * Determines priority based on goal type and user context
         */
        fun suggestPriority(
            goalType: String,
            isHealthRelated: Boolean,
            hasDeadline: Boolean,
            userExperience: String
        ): GoalPriority = when {
            isHealthRelated -> CRITICAL
            hasDeadline && goalType.contains("competition", ignoreCase = true) -> HIGH
            hasDeadline -> MEDIUM
            goalType.contains("strength", ignoreCase = true) -> HIGH
            goalType.contains("weight", ignoreCase = true) -> MEDIUM
            userExperience == "BEGINNER" -> MEDIUM
            else -> LOW
        }
        
        /**
         * Gets the total weight for a list of priorities
         */
        fun getTotalWeight(priorities: List<GoalPriority>): Int = priorities.sumOf { it.weight }
        
        /**
         * Validates that priority distribution is balanced
         */
        fun isBalancedDistribution(priorities: List<GoalPriority>): Boolean {
            val highPriorityCount = priorities.count { it.requiresImmediateAction() }
            val totalCount = priorities.size
            
            // No more than 50% should be high priority to avoid overwhelm
            return totalCount == 0 || (highPriorityCount.toDouble() / totalCount) <= 0.5
        }
    }
}

/**
 * Check-in frequency for goal tracking
 */
enum class CheckInFrequency(val displayName: String, val days: Int) {
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
    BIWEEKLY("Bi-weekly", 14),
    MONTHLY("Monthly", 30),
    QUARTERLY("Quarterly", 90)
}

/**
 * Notification importance levels
 */
enum class NotificationImportance {
    URGENT,    // Critical alerts
    HIGH,      // Important notifications
    NORMAL,    // Standard notifications
    LOW,       // Low priority notifications
    MINIMAL    // Very infrequent notifications
}

/**
 * Stress levels associated with goal priorities
 */
enum class StressLevel {
    HIGH,      // High stress/pressure
    MEDIUM,    // Moderate stress
    LOW,       // Low stress
    MINIMAL,   // Very low stress
    NONE       // No stress/pressure
}