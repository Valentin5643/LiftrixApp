package com.example.liftrix.domain.model.analytics

/**
 * Categories for fitness insights and analytics recommendations
 * Used to classify different types of workout insights and coaching recommendations
 */
enum class InsightCategory(
    val displayName: String,
    val description: String,
    val iconName: String,
    val priority: Int,
    val actionable: Boolean = true
) {
    /**
     * Performance improvements and strength gains
     */
    PERFORMANCE(
        displayName = "Performance",
        description = "Strength gains, PRs, and performance improvements",
        iconName = "trending_up",
        priority = 1
    ),
    
    /**
     * Recovery and rest recommendations
     */
    RECOVERY(
        displayName = "Recovery",
        description = "Rest day recommendations and recovery insights",
        iconName = "hotel",
        priority = 2
    ),
    
    /**
     * Exercise form and technique suggestions
     */
    TECHNIQUE(
        displayName = "Technique",
        description = "Form improvements and exercise technique tips",
        iconName = "school",
        priority = 3
    ),
    
    /**
     * Progressive overload and training progression
     */
    PROGRESSION(
        displayName = "Progression",
        description = "Progressive overload and training advancement",
        iconName = "stairs",
        priority = 4
    ),
    
    /**
     * Nutrition and diet recommendations
     */
    NUTRITION(
        displayName = "Nutrition",
        description = "Dietary recommendations and nutrition insights",
        iconName = "restaurant",
        priority = 5
    ),
    
    /**
     * Workout consistency and habits
     */
    CONSISTENCY(
        displayName = "Consistency",
        description = "Workout frequency and habit formation insights",
        iconName = "event_repeat",
        priority = 6
    ),
    
    /**
     * Exercise variety and program diversity
     */
    VARIETY(
        displayName = "Variety",
        description = "Exercise selection and program diversity",
        iconName = "shuffle",
        priority = 7
    ),
    
    /**
     * Injury prevention and safety
     */
    SAFETY(
        displayName = "Safety",
        description = "Injury prevention and workout safety tips",
        iconName = "health_and_safety",
        priority = 2 // High priority for safety
    ),
    
    /**
     * Goal achievement and milestones
     */
    GOALS(
        displayName = "Goals",
        description = "Goal progress and achievement insights",
        iconName = "flag",
        priority = 8
    ),
    
    /**
     * Goal progress tracking - alias for GOALS
     */
    GOAL_PROGRESS(
        displayName = "Goal Progress",
        description = "Goal progress and achievement insights",
        iconName = "flag",
        priority = 8
    ),
    
    /**
     * Motivation and mental wellness
     */
    MOTIVATION(
        displayName = "Motivation",
        description = "Motivational insights and mental wellness",
        iconName = "psychology",
        priority = 9,
        actionable = false
    ),
    
    /**
     * Equipment and gear recommendations
     */
    EQUIPMENT(
        displayName = "Equipment",
        description = "Equipment recommendations and gear insights",
        iconName = "fitness_center",
        priority = 10
    ),
    
    /**
     * Time management and workout efficiency
     */
    EFFICIENCY(
        displayName = "Efficiency",
        description = "Time management and workout optimization",
        iconName = "timer",
        priority = 11
    ),
    
    /**
     * Social and community insights
     */
    SOCIAL(
        displayName = "Social",
        description = "Community engagement and social fitness insights",
        iconName = "people",
        priority = 12,
        actionable = false
    ),
    
    /**
     * General fitness education and tips
     */
    EDUCATION(
        displayName = "Education",
        description = "General fitness education and knowledge",
        iconName = "menu_book",
        priority = 13,
        actionable = false
    ),
    
    /**
     * Data analysis and metrics interpretation
     */
    ANALYTICS(
        displayName = "Analytics",
        description = "Data interpretation and metrics insights",
        iconName = "analytics",
        priority = 14,
        actionable = false
    ),
    
    /**
     * Trend analysis - alias for ANALYTICS
     */
    TREND_ANALYSIS(
        displayName = "Trend Analysis",
        description = "Data interpretation and metrics insights",
        iconName = "analytics",
        priority = 14,
        actionable = false
    );
    
    /**
     * Gets the urgency level for this insight category
     */
    fun getUrgencyLevel(): UrgencyLevel = when (this) {
        SAFETY -> UrgencyLevel.HIGH
        RECOVERY -> UrgencyLevel.HIGH
        PERFORMANCE, PROGRESSION -> UrgencyLevel.MEDIUM
        CONSISTENCY, TECHNIQUE -> UrgencyLevel.MEDIUM
        GOALS, GOAL_PROGRESS, NUTRITION -> UrgencyLevel.LOW
        else -> UrgencyLevel.LOW
    }
    
    /**
     * Gets the typical frequency this insight should be shown
     */
    fun getFrequency(): InsightFrequency = when (this) {
        SAFETY -> InsightFrequency.IMMEDIATE
        RECOVERY -> InsightFrequency.DAILY
        PERFORMANCE, PROGRESSION -> InsightFrequency.WEEKLY
        CONSISTENCY -> InsightFrequency.WEEKLY
        TECHNIQUE, NUTRITION -> InsightFrequency.WEEKLY
        VARIETY, EFFICIENCY -> InsightFrequency.BIWEEKLY
        GOALS, GOAL_PROGRESS -> InsightFrequency.MONTHLY
        else -> InsightFrequency.MONTHLY
    }
    
    /**
     * Gets the expected engagement level for this category
     */
    fun getEngagementLevel(): EngagementLevel = when {
        actionable && priority <= 4 -> EngagementLevel.HIGH
        actionable && priority <= 8 -> EngagementLevel.MEDIUM
        actionable -> EngagementLevel.LOW
        else -> EngagementLevel.INFORMATIONAL
    }
    
    /**
     * Gets the typical insight duration (how long it stays relevant)
     */
    fun getInsightDuration(): InsightDuration = when (this) {
        SAFETY -> InsightDuration.PERSISTENT // Always relevant
        RECOVERY -> InsightDuration.SHORT_TERM // 1-2 days
        PERFORMANCE, PROGRESSION -> InsightDuration.MEDIUM_TERM // 1-2 weeks
        TECHNIQUE, CONSISTENCY -> InsightDuration.MEDIUM_TERM
        NUTRITION, VARIETY -> InsightDuration.LONG_TERM // 1+ months
        GOALS, GOAL_PROGRESS -> InsightDuration.LONG_TERM
        else -> InsightDuration.MEDIUM_TERM
    }
    
    /**
     * Gets related categories that often provide complementary insights
     */
    fun getRelatedCategories(): List<InsightCategory> = when (this) {
        PERFORMANCE -> listOf(PROGRESSION, TECHNIQUE, NUTRITION)
        RECOVERY -> listOf(SAFETY, CONSISTENCY, NUTRITION)
        TECHNIQUE -> listOf(SAFETY, PERFORMANCE, PROGRESSION)
        PROGRESSION -> listOf(PERFORMANCE, TECHNIQUE, GOALS)
        NUTRITION -> listOf(PERFORMANCE, RECOVERY, GOALS)
        CONSISTENCY -> listOf(MOTIVATION, GOALS, EFFICIENCY)
        VARIETY -> listOf(PROGRESSION, MOTIVATION, TECHNIQUE)
        SAFETY -> listOf(TECHNIQUE, RECOVERY, EQUIPMENT)
        GOALS, GOAL_PROGRESS -> listOf(PERFORMANCE, PROGRESSION, MOTIVATION)
        MOTIVATION -> listOf(GOALS, CONSISTENCY, SOCIAL)
        EQUIPMENT -> listOf(SAFETY, TECHNIQUE, EFFICIENCY)
        EFFICIENCY -> listOf(CONSISTENCY, VARIETY, EQUIPMENT)
        SOCIAL -> listOf(MOTIVATION, GOALS, EDUCATION)
        EDUCATION -> listOf(TECHNIQUE, SAFETY, NUTRITION)
        ANALYTICS, TREND_ANALYSIS -> listOf(PERFORMANCE, GOALS, EFFICIENCY)
    }
    
    companion object {
        /**
         * Gets categories ordered by priority (most important first)
         */
        fun getByPriority(): List<InsightCategory> = values().sortedBy { it.priority }
        
        /**
         * Gets actionable categories that users can act upon
         */
        fun getActionableCategories(): List<InsightCategory> = values().filter { it.actionable }
        
        /**
         * Gets informational categories for education and awareness
         */
        fun getInformationalCategories(): List<InsightCategory> = values().filter { !it.actionable }
        
        /**
         * Gets high-priority categories that should be shown first
         */
        fun getHighPriorityCategories(): List<InsightCategory> = values().filter { it.priority <= 4 }
        
        /**
         * Gets categories by urgency level
         */
        fun getByUrgency(urgency: UrgencyLevel): List<InsightCategory> = values().filter { 
            it.getUrgencyLevel() == urgency 
        }
        
        /**
         * Gets categories suitable for daily insights
         */
        fun getDailyCategories(): List<InsightCategory> = values().filter { 
            it.getFrequency() in listOf(InsightFrequency.IMMEDIATE, InsightFrequency.DAILY) 
        }
        
        /**
         * Gets core categories that form the foundation of fitness insights
         */
        fun getCoreCategories(): List<InsightCategory> = listOf(
            PERFORMANCE, RECOVERY, TECHNIQUE, PROGRESSION, SAFETY
        )
    }
}

/**
 * Urgency levels for insights
 */
enum class UrgencyLevel {
    HIGH,    // Immediate attention needed
    MEDIUM,  // Important but not urgent
    LOW      // Nice to know, low priority
}

/**
 * Frequency for showing insights
 */
enum class InsightFrequency {
    IMMEDIATE,  // Show as soon as detected
    DAILY,      // Once per day maximum
    WEEKLY,     // Once per week maximum
    BIWEEKLY,   // Every two weeks
    MONTHLY     // Once per month maximum
}

/**
 * Expected user engagement levels
 */
enum class EngagementLevel {
    HIGH,           // High interaction expected
    MEDIUM,         // Moderate interaction
    LOW,            // Low interaction
    INFORMATIONAL   // Read-only, no action expected
}

/**
 * How long an insight remains relevant
 */
enum class InsightDuration {
    SHORT_TERM,     // 1-3 days
    MEDIUM_TERM,    // 1-4 weeks
    LONG_TERM,      // 1+ months
    PERSISTENT      // Always relevant
}