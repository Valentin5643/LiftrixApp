package com.example.liftrix.domain.model.analytics

/**
 * User experience levels for dashboard customization and widget selection.
 * 
 * Determines which widgets and features are available to users based on their
 * experience and engagement with the app. Supports progressive disclosure
 * of more complex features as users advance.
 */
enum class UserLevel(
    val displayName: String,
    val description: String
) {
    /**
     * Beginner level - new users with limited fitness tracking experience
     * Focus on essential metrics and simple visualizations
     */
    BEGINNER(
        displayName = "Beginner",
        description = "Essential metrics and simple progress tracking"
    ),
    
    /**
     * Intermediate level - regular users comfortable with basic analytics
     * Access to trend analysis and goal tracking features
     */
    INTERMEDIATE(
        displayName = "Intermediate", 
        description = "Trend analysis and goal tracking"
    ),
    
    /**
     * Advanced level - experienced users who want detailed analytics
     * Full access to all widgets and complex analysis features
     */
    ADVANCED(
        displayName = "Advanced",
        description = "Comprehensive analytics and advanced insights"
    );
    
    /**
     * Checks if this user level can access features of another level
     */
    fun canAccess(requiredLevel: UserLevel): Boolean {
        return this.ordinal >= requiredLevel.ordinal
    }
    
    /**
     * Gets the maximum number of widgets this user level can display
     */
    fun getMaxWidgets(): Int = when (this) {
        BEGINNER -> 4
        INTERMEDIATE -> 7
        ADVANCED -> 10
    }
    
    /**
     * Gets the recommended number of widgets for this user level
     */
    fun getRecommendedWidgets(): Int = when (this) {
        BEGINNER -> 3
        INTERMEDIATE -> 5
        ADVANCED -> 8
    }
    
    /**
     * Checks if this user level supports real-time widget updates
     */
    fun supportsRealTime(): Boolean = when (this) {
        BEGINNER -> true    // Simple widgets update quickly
        INTERMEDIATE -> true // Balanced complexity
        ADVANCED -> false   // Complex analytics may not be real-time
    }
    
    companion object {
        /**
         * Determines user level based on usage metrics
         */
        fun fromUsageMetrics(
            totalWorkouts: Int,
            daysSinceStart: Int,
            customizationCount: Int
        ): UserLevel = when {
            totalWorkouts < 10 || daysSinceStart < 14 -> BEGINNER
            totalWorkouts < 50 || daysSinceStart < 90 || customizationCount < 3 -> INTERMEDIATE
            else -> ADVANCED
        }
        
        /**
         * Gets the next level from current level
         */
        fun getNextLevel(currentLevel: UserLevel): UserLevel? = when (currentLevel) {
            BEGINNER -> INTERMEDIATE
            INTERMEDIATE -> ADVANCED
            ADVANCED -> null
        }
        
        /**
         * Gets user level by name (case-insensitive)
         */
        fun fromName(name: String): UserLevel? = when (name.lowercase()) {
            "beginner" -> BEGINNER
            "intermediate" -> INTERMEDIATE
            "advanced" -> ADVANCED
            else -> null
        }
    }
}