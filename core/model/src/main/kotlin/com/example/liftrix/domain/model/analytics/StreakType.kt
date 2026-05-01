package com.example.liftrix.domain.model.analytics

/**
 * Enum representing different types of streaks that can be tracked in the analytics system
 * 
 * Defines various streak categories for consistency and habit tracking:
 * - Workout-based streaks for training consistency
 * - Goal-based streaks for achievement tracking
 * - Habit-based streaks for lifestyle consistency
 * - Performance-based streaks for progression tracking
 * 
 * Used by:
 * - ConsistencyMetrics for streak type classification
 * - AnalyticsMapper for streak calculations
 * - UI components for streak display and motivation
 * - Dashboard widgets for consistency visualization
 */
enum class StreakType(
    val displayName: String,
    val description: String,
    val unit: String,
    val motivationThreshold: Int
) {
    /**
     * Consecutive workout days streak
     * Most common streak type for training consistency
     */
    WORKOUT_DAYS(
        displayName = "Workout Days",
        description = "Consecutive days with completed workouts",
        unit = "days",
        motivationThreshold = 7
    ),
    
    /**
     * Consecutive workout weeks streak
     * For tracking weekly consistency patterns
     */
    WORKOUT_WEEKS(
        displayName = "Workout Weeks",
        description = "Consecutive weeks with regular workouts",
        unit = "weeks",
        motivationThreshold = 4
    ),
    
    /**
     * Consecutive active days streak
     * For tracking any form of physical activity
     */
    ACTIVE_DAYS(
        displayName = "Active Days",
        description = "Consecutive days with any physical activity",
        unit = "days",
        motivationThreshold = 10
    ),
    
    /**
     * Consecutive goal achievement streak
     * For tracking consistent goal completion
     */
    GOAL_ACHIEVEMENT(
        displayName = "Goal Achievement",
        description = "Consecutive periods of goal achievement",
        unit = "periods",
        motivationThreshold = 3
    ),
    
    /**
     * Consecutive personal record streak
     * For tracking strength progression consistency
     */
    PERSONAL_RECORDS(
        displayName = "Personal Records",
        description = "Consecutive workouts with personal records",
        unit = "workouts",
        motivationThreshold = 3
    ),
    
    /**
     * Consecutive habit completion streak
     * For tracking lifestyle habit consistency
     */
    HABIT_COMPLETION(
        displayName = "Habit Completion",
        description = "Consecutive days of habit completion",
        unit = "days",
        motivationThreshold = 14
    ),
    
    /**
     * Consecutive workout completion streak
     * For tracking planned workout completion
     */
    WORKOUT_COMPLETION(
        displayName = "Workout Completion",
        description = "Consecutive completed planned workouts",
        unit = "workouts",
        motivationThreshold = 5
    ),
    
    /**
     * Consecutive training volume streak
     * For tracking consistent training volume
     */
    VOLUME_CONSISTENCY(
        displayName = "Volume Consistency",
        description = "Consecutive periods of consistent training volume",
        unit = "periods",
        motivationThreshold = 4
    );
    
    /**
     * Checks if a streak value meets the motivation threshold
     */
    fun isMotivationThresholdMet(streakValue: Int): Boolean = streakValue >= motivationThreshold
    
    /**
     * Gets motivation message based on streak value
     */
    fun getMotivationMessage(streakValue: Int): String = when {
        streakValue == 0 -> "Ready to start your ${displayName.lowercase()} streak?"
        streakValue < motivationThreshold -> "Keep going! You're building a ${displayName.lowercase()} streak!"
        streakValue == motivationThreshold -> "Congratulations! You've reached a ${motivationThreshold}-${unit} ${displayName.lowercase()} streak!"
        streakValue < motivationThreshold * 2 -> "Amazing! You're on a ${streakValue}-${unit} ${displayName.lowercase()} streak!"
        else -> "Incredible! You've maintained a ${streakValue}-${unit} ${displayName.lowercase()} streak!"
    }
    
    /**
     * Gets streak level based on streak value
     */
    fun getStreakLevel(streakValue: Int): StreakLevel = when {
        streakValue == 0 -> StreakLevel.NONE
        streakValue < motivationThreshold / 2 -> StreakLevel.STARTING
        streakValue < motivationThreshold -> StreakLevel.BUILDING
        streakValue < motivationThreshold * 2 -> StreakLevel.STRONG
        streakValue < motivationThreshold * 4 -> StreakLevel.EXCELLENT
        else -> StreakLevel.LEGENDARY
    }
    
    /**
     * Gets formatted streak description
     */
    fun getFormattedDescription(streakValue: Int): String = 
        "$streakValue ${if (streakValue == 1) unit.removeSuffix("s") else unit} of ${displayName.lowercase()}"
}

/**
 * Enum representing different streak achievement levels
 */
enum class StreakLevel(val displayName: String, val description: String) {
    NONE("None", "No active streak"),
    STARTING("Starting", "Beginning of streak"),
    BUILDING("Building", "Building momentum"),
    STRONG("Strong", "Strong streak established"),
    EXCELLENT("Excellent", "Excellent streak maintained"),
    LEGENDARY("Legendary", "Legendary streak achievement")
}