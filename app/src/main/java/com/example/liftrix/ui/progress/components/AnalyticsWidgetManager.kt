package com.example.liftrix.ui.progress.components

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.WidgetCategory
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.WidgetPriority
import com.example.liftrix.domain.model.analytics.TrendDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized management system for analytics dashboard widgets.
 * 
 * Handles widget configuration, data coordination, and personalization
 * following Hevy-inspired collapsible sections with Material 3 design.
 * 
 * Key Responsibilities:
 * - Configuration management for Beginner/Intermediate/Advanced users
 * - Widget data loading and caching coordination  
 * - Personalization and settings persistence
 * - Performance optimization through lazy loading
 */
@Singleton
class AnalyticsWidgetManager @Inject constructor() {
    
    /**
     * Get widget configuration based on user experience level
     * 
     * @param userLevel Experience level determining widget complexity
     * @return List of widgets appropriate for the user level
     */
    fun getConfigurationForLevel(userLevel: UserLevel): DashboardConfiguration {
        return when (userLevel) {
            UserLevel.BEGINNER -> DashboardConfiguration.Beginner
            UserLevel.INTERMEDIATE -> DashboardConfiguration.Intermediate  
            UserLevel.ADVANCED -> DashboardConfiguration.Advanced
        }
    }
    
    /**
     * Get widgets filtered by priority level for progressive disclosure
     * 
     * @param priority Minimum priority level to include
     * @return List of widgets at or above the specified priority
     */
    fun getWidgetsByPriority(priority: WidgetPriority): List<AnalyticsWidget> {
        return AnalyticsWidget.getAllWidgets().filter { widget ->
            when (priority) {
                WidgetPriority.ESSENTIAL -> true
                WidgetPriority.INTERMEDIATE -> widget.priority != WidgetPriority.ADVANCED
                WidgetPriority.ADVANCED -> widget.priority == WidgetPriority.ADVANCED
            }
        }
    }
    
    /**
     * Get widgets organized by category for logical grouping
     * 
     * @return Map of categories to their respective widgets
     */
    fun getWidgetsByCategory(): Map<WidgetCategory, List<AnalyticsWidget>> {
        return AnalyticsWidget.getAllWidgets().groupBy { it.category }
    }
    
    /**
     * Get recommended widgets based on user usage patterns
     * 
     * @param userId User identifier for personalized recommendations
     * @param usageHistory Historical widget interaction data
     * @return List of widgets optimized for user preferences
     */
    fun getRecommendedWidgets(
        userId: String,
        usageHistory: WidgetUsageHistory = WidgetUsageHistory()
    ): List<AnalyticsWidget> {
        // Base recommendation on usage patterns
        val frequentlyUsed = usageHistory.getMostUsedWidgets(limit = 6)
        val essentialWidgets = AnalyticsWidget.getWidgetsByPriority(WidgetPriority.ESSENTIAL)
        
        // Combine frequently used with essential, ensuring essentials are included
        val recommended = mutableSetOf<AnalyticsWidget>()
        recommended.addAll(essentialWidgets)
        recommended.addAll(frequentlyUsed)
        
        return recommended.toList().take(8) // Maximum 8 widgets for optimal UX
    }
    
    /**
     * Check if a widget should be visible based on data availability
     * 
     * @param widget Widget to check for visibility
     * @param hasWorkoutData Whether user has sufficient workout data
     * @param dataAge Number of days since last workout
     * @return Boolean indicating if widget should be displayed
     */
    fun shouldShowWidget(
        widget: AnalyticsWidget,
        hasWorkoutData: Boolean,
        dataAge: Int = 0
    ): Boolean {
        // Always show essential widgets for motivation even without data
        if (widget.priority == WidgetPriority.ESSENTIAL) {
            return true
        }
        
        // Show intermediate widgets if user has recent data (within 7 days)
        if (widget.priority == WidgetPriority.INTERMEDIATE) {
            return hasWorkoutData && dataAge <= 7
        }
        
        // Show advanced widgets only with substantial data (within 3 days)
        return hasWorkoutData && dataAge <= 3
    }
    
    /**
     * Get empty state configuration when no data is available
     * 
     * @return Minimal widget set for new users or empty states
     */
    fun getEmptyStateConfiguration(): List<AnalyticsWidget> {
        return listOf(
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.ConsistencyStreak
        )
    }
    
    /**
     * Validate widget configuration for performance and UX
     * 
     * @param widgets List of widgets to validate
     * @return Validation result with recommendations
     */
    fun validateConfiguration(widgets: List<AnalyticsWidget>): ConfigurationValidation {
        val issues = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        // Check widget count for optimal performance
        when {
            widgets.size > 10 -> {
                issues.add("Too many widgets may impact performance")
                recommendations.add("Consider reducing to 8 or fewer widgets")
            }
            widgets.size < 3 -> {
                issues.add("Too few widgets may not provide sufficient insights")
                recommendations.add("Consider adding at least 4 widgets for better value")
            }
        }
        
        // Ensure essential widgets are included
        val essentialWidgets = widgets.filter { it.priority == WidgetPriority.ESSENTIAL }
        if (essentialWidgets.size < 2) {
            issues.add("Missing essential widgets for core functionality")
            recommendations.add("Include TotalVolume and WorkoutFrequency for best experience")
        }
        
        // Check category distribution for balanced insights
        val categories = widgets.map { it.category }.distinct()
        if (categories.size < 3) {
            recommendations.add("Consider widgets from more categories for comprehensive insights")
        }
        
        return ConfigurationValidation(
            isValid = issues.isEmpty(),
            issues = issues,
            recommendations = recommendations,
            optimalWidgetCount = (4..8),
            currentWidgetCount = widgets.size
        )
    }
    
    /**
     * Get widget layout configuration for responsive design
     * 
     * @param widgets List of widgets to arrange
     * @param screenWidth Available screen width in dp
     * @return Layout configuration with grid specifications
     */
    fun getLayoutConfiguration(
        widgets: List<AnalyticsWidget>,
        screenWidth: Int
    ): WidgetLayoutConfiguration {
        val columnsCount = when {
            screenWidth >= 600 -> 3 // Tablet landscape
            screenWidth >= 400 -> 2 // Phone landscape / large phone
            else -> 1 // Phone portrait
        }
        
        // Group widgets by layout priority
        val primaryWidgets = widgets.filter { it.priority == WidgetPriority.ESSENTIAL }
        val secondaryWidgets = widgets.filter { it.priority != WidgetPriority.ESSENTIAL }
        
        return WidgetLayoutConfiguration(
            columnsCount = columnsCount,
            primaryWidgets = primaryWidgets,
            secondaryWidgets = secondaryWidgets,
            useStaggeredGrid = screenWidth >= 400,
            enableCollapsibleSections = widgets.size > 6
        )
    }
}

/**
 * User experience level for widget configuration
 */
enum class UserLevel(val displayName: String, val description: String) {
    BEGINNER("Beginner", "Essential metrics for building workout consistency"),
    INTERMEDIATE("Intermediate", "Enhanced metrics for optimizing training progress"), 
    ADVANCED("Advanced", "Comprehensive analytics for advanced training optimization")
}

/**
 * Widget usage tracking for personalized recommendations
 */
data class WidgetUsageHistory(
    val viewCounts: Map<String, Int> = emptyMap(),
    val interactionCounts: Map<String, Int> = emptyMap(),
    val lastUsedTimestamps: Map<String, Long> = emptyMap()
) {
    /**
     * Get most frequently used widgets
     */
    fun getMostUsedWidgets(limit: Int = 5): List<AnalyticsWidget> {
        val totalUsage = viewCounts.mapValues { (widgetId, views) ->
            views + (interactionCounts[widgetId] ?: 0) * 2 // Weight interactions higher
        }
        
        return totalUsage
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
            .mapNotNull { (widgetId, _) -> 
                AnalyticsWidget.getWidgetById(widgetId)
            }
    }
}

/**
 * Configuration validation result
 */
data class ConfigurationValidation(
    val isValid: Boolean,
    val issues: List<String>,
    val recommendations: List<String>,
    val optimalWidgetCount: IntRange,
    val currentWidgetCount: Int
) {
    /**
     * Get validation score from 0-100
     */
    val score: Int
        get() {
            var score = 100
            score -= issues.size * 20
            
            // Penalty for suboptimal widget count
            if (currentWidgetCount !in optimalWidgetCount) {
                score -= 15
            }
            
            return maxOf(0, score)
        }
}

/**
 * Widget layout configuration for responsive design
 */
data class WidgetLayoutConfiguration(
    val columnsCount: Int,
    val primaryWidgets: List<AnalyticsWidget>,
    val secondaryWidgets: List<AnalyticsWidget>,
    val useStaggeredGrid: Boolean,
    val enableCollapsibleSections: Boolean
)