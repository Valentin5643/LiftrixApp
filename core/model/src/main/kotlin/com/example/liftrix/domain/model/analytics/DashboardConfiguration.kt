package com.example.liftrix.domain.model.analytics

/**
 * Dashboard configuration levels for analytics display customization
 * 
 * Defines different levels of dashboard complexity to match user experience levels:
 * - Beginner: Simple metrics focused on basic progress tracking (4 widgets)
 * - Intermediate: Balanced view with essential analytics and trends (7 widgets)
 * - Advanced: Comprehensive analytics with detailed insights (10 widgets)
 * - Custom: User-defined widget selection based on preferences
 * 
 * This configuration system now integrates with WidgetResolver for dynamic
 * widget selection instead of hardcoded lists, supporting proper user level
 * progression and CUSTOM layout mode.
 */
sealed class DashboardConfiguration(
    val name: String,
    val description: String,
    val maxWidgets: Int
) {
    
    constructor() : this("", "", 0)
    
    /**
     * Beginner configuration for new users
     * Focus on simple, encouraging metrics
     */
    data object Beginner : DashboardConfiguration(
        name = "Beginner",
        description = "Essential metrics",
        maxWidgets = 4
    )
    
    /**
     * Intermediate configuration for regular users
     * Balanced analytics with trend analysis
     */
    data object Intermediate : DashboardConfiguration(
        name = "Intermediate", 
        description = "",
        maxWidgets = 7
    )
    
    /**
     * Advanced configuration for experienced users
     * Comprehensive analytics with detailed insights
     */
    data object Advanced : DashboardConfiguration(
        name = "Advanced",
        description = "",
        maxWidgets = 10
    )
    
    /**
     * Custom configuration for user-defined widget selection
     * Allows users to choose their own widgets within their level constraints
     */
    data object Custom : DashboardConfiguration(
        name = "Custom",
        description = "",
        maxWidgets = 10 // Same as Advanced but user-controlled
    )
    
    /**
     * Gets the
     * user level associated with this configuration
     */
    fun getUserLevel(): UserLevel = when (this) {
        is Beginner -> UserLevel.BEGINNER
        is Intermediate -> UserLevel.INTERMEDIATE
        is Advanced -> UserLevel.ADVANCED
        is Custom -> UserLevel.ADVANCED // Custom allows advanced-level widgets
    }
    
    /**
     * Checks if this configuration supports custom widget selection
     */
    fun supportsCustomization(): Boolean = when (this) {
        is Custom -> true
        else -> false
    }
    
    /**
     * Gets the priority level of this configuration (1-4, higher = more advanced)
     */
    fun getPriorityLevel(): Int = when (this) {
        is Beginner -> 1
        is Intermediate -> 2
        is Advanced -> 3
        is Custom -> 4
    }
    
    /**
     * Checks if this configuration level supports real-time updates
     */
    fun supportsRealTimeUpdates(): Boolean = when (this) {
        is Beginner -> true     // Simple metrics update quickly
        is Intermediate -> true // Balanced updates
        is Advanced -> false    // Complex calculations may not update real-time
        is Custom -> false      // User-defined layouts may have complex widgets
    }
    
    companion object {
        /**
         * Gets all available dashboard configurations
         */
        fun getAllConfigurations(): List<DashboardConfiguration> = listOf(
            Beginner,
            Intermediate, 
            Advanced,
            Custom
        )
        
        /**
         * Gets the recommended configuration for a user based on experience
         */
        fun getRecommendedConfiguration(
            totalWorkouts: Int,
            daysSinceFirstWorkout: Int
        ): DashboardConfiguration = when {
            totalWorkouts < 10 || daysSinceFirstWorkout < 14 -> Beginner
            totalWorkouts < 50 || daysSinceFirstWorkout < 90 -> Intermediate
            else -> Advanced
        }
        
        /**
         * Gets configuration by name (case-insensitive)
         */
        fun getByName(name: String): DashboardConfiguration? = when (name.lowercase()) {
            "beginner" -> Beginner
            "intermediate" -> Intermediate
            "advanced" -> Advanced
            "custom" -> Custom
            else -> null
        }
        
        /**
         * Gets configuration from user level (excludes Custom)
         */
        fun fromUserLevel(userLevel: UserLevel): DashboardConfiguration = when (userLevel) {
            UserLevel.BEGINNER -> Beginner
            UserLevel.INTERMEDIATE -> Intermediate
            UserLevel.ADVANCED -> Advanced
        }
        
        /**
         * Creates a configuration from user level and layout mode
         */
        fun fromUserLevelAndLayout(
            userLevel: UserLevel, 
            layoutMode: DashboardLayoutMode
        ): DashboardConfiguration = when {
            layoutMode == DashboardLayoutMode.CUSTOM -> Custom
            else -> fromUserLevel(userLevel)
        }
    }
}