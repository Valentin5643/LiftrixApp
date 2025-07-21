package com.example.liftrix.domain.model.analytics

/**
 * Widget categories for dashboard organization and progressive disclosure
 * 
 * Defines the four main categories of analytics widgets available in the dashboard.
 * Each category groups related widgets together for better organization and
 * user experience in the customization interface.
 * 
 * Categories are ordered by complexity and user value:
 * - METRICS: Essential single-value metrics for quick insights
 * - CHARTS: Visual trend analysis and pattern recognition
 * - PROGRESS: Goal tracking and achievement visualization
 * - ANALYTICS: Advanced analytics with complex calculations
 */
enum class WidgetCategory(
    val displayName: String,
    val description: String,
    val iconName: String,
    val priority: Int
) {
    /**
     * Basic metrics widgets showing single values or simple comparisons
     * Essential for daily progress monitoring and quick insights
     */
    METRICS(
        displayName = "Metrics",
        description = "Essential single-value metrics for quick insights",
        iconName = "analytics",
        priority = 1
    ),
    
    /**
     * Chart-based widgets for trend visualization and pattern analysis
     * Help users understand progression over time periods
     */
    CHARTS(
        displayName = "Charts", 
        description = "Visual trend analysis and pattern recognition",
        iconName = "trending_up",
        priority = 2
    ),
    
    /**
     * Progress tracking widgets focused on goals and achievements
     * Motivational widgets showing advancement toward targets
     */
    PROGRESS(
        displayName = "Progress",
        description = "Goal tracking and achievement visualization", 
        iconName = "track_changes",
        priority = 3
    ),
    
    /**
     * Advanced analytics widgets with complex calculations
     * Deep insights for experienced users and detailed analysis
     */
    ANALYTICS(
        displayName = "Analytics",
        description = "Advanced analytics with complex calculations",
        iconName = "insights",
        priority = 4
    );
    
    /**
     * Gets the default maximum number of widgets for this category
     * Based on complexity and typical user engagement patterns
     */
    fun getDefaultMaxWidgets(): Int = when (this) {
        METRICS -> 6     // High value, simple widgets
        CHARTS -> 4      // Moderate complexity, visual focus
        PROGRESS -> 3    // Goal-oriented, motivational widgets
        ANALYTICS -> 2   // Complex widgets, advanced users
    }
    
    /**
     * Gets the recommended user level for this category
     */
    fun getRecommendedUserLevel(): UserLevel = when (this) {
        METRICS -> UserLevel.BEGINNER
        CHARTS -> UserLevel.INTERMEDIATE
        PROGRESS -> UserLevel.INTERMEDIATE
        ANALYTICS -> UserLevel.ADVANCED
    }
    
    /**
     * Gets the category color for UI theming
     */
    fun getCategoryColor(): String = when (this) {
        METRICS -> "primary"
        CHARTS -> "secondary" 
        PROGRESS -> "tertiary"
        ANALYTICS -> "surface"
    }
    
    /**
     * Checks if this category should be collapsed by default for new users
     */
    fun isCollapsedByDefault(userLevel: UserLevel): Boolean = when (this) {
        METRICS -> false          // Always expanded for core metrics
        CHARTS -> userLevel == UserLevel.BEGINNER
        PROGRESS -> userLevel == UserLevel.BEGINNER
        ANALYTICS -> userLevel != UserLevel.ADVANCED
    }
    
    companion object {
        /**
         * Gets categories ordered by priority (most important first)
         */
        fun getByPriority(): List<WidgetCategory> = values().sortedBy { it.priority }
        
        /**
         * Gets categories suitable for a specific user level
         */
        fun getForUserLevel(userLevel: UserLevel): List<WidgetCategory> = when (userLevel) {
            UserLevel.BEGINNER -> listOf(METRICS)
            UserLevel.INTERMEDIATE -> listOf(METRICS, CHARTS, PROGRESS)
            UserLevel.ADVANCED -> listOf(METRICS, CHARTS, PROGRESS, ANALYTICS)
        }
        
        /**
         * Gets the total recommended widget count across all categories
         */
        fun getTotalRecommendedWidgets(userLevel: UserLevel): Int {
            return getForUserLevel(userLevel).sumOf { it.getDefaultMaxWidgets() }
        }
    }
}