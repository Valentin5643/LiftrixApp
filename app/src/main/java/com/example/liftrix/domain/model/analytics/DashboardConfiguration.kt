package com.example.liftrix.domain.model.analytics

/**
 * Dashboard configuration levels for analytics display customization
 * 
 * Defines different levels of dashboard complexity to match user experience levels:
 * - Beginner: Simple metrics focused on basic progress tracking
 * - Intermediate: Balanced view with essential analytics and trends
 * - Advanced: Comprehensive analytics with detailed insights and customization
 * 
 * Each configuration determines which widgets are displayed, chart complexity,
 * and the level of detail in analytics calculations.
 */
sealed class DashboardConfiguration(
    val name: String,
    val description: String,
    val maxWidgets: Int
) {
    
    /**
     * Beginner configuration for new users
     * Focus on simple, encouraging metrics
     */
    data object Beginner : DashboardConfiguration(
        name = "Beginner",
        description = "Simple view focused on basic progress tracking",
        maxWidgets = 3
    ) {
        val widgets: List<AnalyticsWidget> = listOf(
            AnalyticsWidget.WORKOUT_FREQUENCY,
            AnalyticsWidget.TOTAL_VOLUME,
            AnalyticsWidget.CONSISTENCY_STREAK
        )
    }
    
    /**
     * Intermediate configuration for regular users
     * Balanced analytics with trend analysis
     */
    data object Intermediate : DashboardConfiguration(
        name = "Intermediate", 
        description = "Balanced view with essential analytics and trends",
        maxWidgets = 5
    ) {
        val widgets: List<AnalyticsWidget> = listOf(
            AnalyticsWidget.WORKOUT_FREQUENCY,
            AnalyticsWidget.TOTAL_VOLUME,
            AnalyticsWidget.VOLUME_CALENDAR,
            AnalyticsWidget.STRENGTH_PROGRESS,
            AnalyticsWidget.CONSISTENCY_STREAK
        )
    }
    
    /**
     * Advanced configuration for experienced users
     * Comprehensive analytics with detailed insights
     */
    data object Advanced : DashboardConfiguration(
        name = "Advanced",
        description = "Comprehensive analytics with detailed insights and customization",
        maxWidgets = 8
    ) {
        val widgets: List<AnalyticsWidget> = listOf(
            AnalyticsWidget.WORKOUT_FREQUENCY,
            AnalyticsWidget.TOTAL_VOLUME,
            AnalyticsWidget.VOLUME_CALENDAR,
            AnalyticsWidget.STRENGTH_PROGRESS,
            AnalyticsWidget.CONSISTENCY_STREAK,
            AnalyticsWidget.VOLUME_TRENDS,
            AnalyticsWidget.RECOVERY_METRICS,
            AnalyticsWidget.PERFORMANCE_ANALYSIS
        )
    }
    
    /**
     * Checks if this configuration supports a specific widget
     */
    fun supportsWidget(widget: AnalyticsWidget): Boolean = when (this) {
        is Beginner -> Beginner.widgets.contains(widget)
        is Intermediate -> Intermediate.widgets.contains(widget)
        is Advanced -> Advanced.widgets.contains(widget)
    }
    
    /**
     * Gets the priority level of this configuration (1-3, higher = more advanced)
     */
    fun getPriorityLevel(): Int = when (this) {
        is Beginner -> 1
        is Intermediate -> 2
        is Advanced -> 3
    }
    
    /**
     * Checks if this configuration level supports real-time updates
     */
    fun supportsRealTimeUpdates(): Boolean = when (this) {
        is Beginner -> true     // Simple metrics update quickly
        is Intermediate -> true // Balanced updates
        is Advanced -> false    // Complex calculations may not update real-time
    }
    
    companion object {
        /**
         * Gets all available dashboard configurations
         */
        fun getAllConfigurations(): List<DashboardConfiguration> = listOf(
            Beginner,
            Intermediate, 
            Advanced
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
            else -> null
        }
    }
}