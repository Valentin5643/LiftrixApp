package com.example.liftrix.ui.progress.components

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetCategory
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.WidgetPriority
import com.example.liftrix.domain.model.analytics.WidgetPreferences
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
     * Get a widget by its ID
     */
    fun getWidgetById(widgetId: String): AnalyticsWidget? {
        return AnalyticsWidget.values().find { it.name == widgetId }
    }
    
    /**
     * Get widgets filtered by priority level for progressive disclosure
     * 
     * @param priority Minimum priority level to include
     * @return List of widgets at or above the specified priority
     */
    fun getWidgetsByPriority(priority: WidgetPriority): List<AnalyticsWidget> {
        return AnalyticsWidget.values().filter { widget ->
            when (priority) {
                WidgetPriority.ESSENTIAL -> true
                WidgetPriority.STANDARD -> widget.priority != WidgetPriority.ADVANCED
                WidgetPriority.ADVANCED -> widget.priority == WidgetPriority.ADVANCED
            }
        }
    }
    
    /**
     * Get all available widgets
     */
    fun getAllWidgets(): List<AnalyticsWidget> {
        return AnalyticsWidget.values().toList()
    }
    
    /**
     * Get widgets organized by category for logical grouping
     * 
     * @return Map of categories to their respective widgets
     */
    fun getWidgetsByCategory(): Map<WidgetCategory, List<AnalyticsWidget>> {
        return AnalyticsWidget.values().groupBy { it.category }
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
        val essentialWidgets = getWidgetsByPriority(WidgetPriority.ESSENTIAL)
        
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
        
        // Show standard widgets if user has recent data (within 7 days)
        if (widget.priority == WidgetPriority.STANDARD) {
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
    
    /**
     * Get widgets filtered by user preferences
     * 
     * @param preferences User widget preferences
     * @return List of widgets filtered and ordered by preferences
     */
    fun getWidgetsByPreferences(preferences: WidgetPreferences): List<AnalyticsWidget> {
        return preferences.getOrderedVisibleWidgets()
            .mapNotNull { widgetName -> 
                AnalyticsWidget.values().find { it.name == widgetName }
            }
    }
    
    /**
     * Check if a widget is visible based on preferences
     * 
     * @param widget Widget to check
     * @param preferences User preferences
     * @return Boolean indicating if widget should be displayed
     */
    fun isWidgetVisible(widget: AnalyticsWidget, preferences: WidgetPreferences?): Boolean {
        return preferences?.isWidgetVisible(widget.name) ?: true
    }
    
    /**
     * Get widget display size from preferences
     * 
     * @param widget Widget to get size for
     * @param preferences User preferences
     * @return Widget display size
     */
    fun getWidgetSize(
        widget: AnalyticsWidget, 
        preferences: WidgetPreferences?
    ): com.example.liftrix.domain.model.analytics.WidgetDisplaySize {
        return preferences?.getWidgetSize(widget.name) 
            ?: com.example.liftrix.domain.model.analytics.WidgetDisplaySize.STANDARD
    }
    
    /**
     * Check if a section is collapsed based on preferences
     * 
     * @param sectionName Name of the section
     * @param preferences User preferences
     * @return Boolean indicating if section is collapsed
     */
    fun isSectionCollapsed(sectionName: String, preferences: WidgetPreferences?): Boolean {
        return preferences?.isSectionCollapsed(sectionName) ?: false
    }
    
    /**
     * Get dashboard layout mode from preferences
     * 
     * @param preferences User preferences
     * @return Dashboard layout mode
     */
    fun getDashboardLayoutMode(
        preferences: WidgetPreferences?
    ): com.example.liftrix.domain.model.analytics.DashboardLayoutMode {
        return preferences?.dashboardLayout 
            ?: com.example.liftrix.domain.model.analytics.DashboardLayoutMode.SECTIONS
    }
    
    /**
     * Get auto-refresh settings from preferences
     * 
     * @param preferences User preferences
     * @return Pair of (enabled, intervalMinutes)
     */
    fun getAutoRefreshSettings(preferences: WidgetPreferences?): Pair<Boolean, Int> {
        return if (preferences != null) {
            Pair(preferences.enableAutoRefresh, preferences.refreshIntervalMinutes)
        } else {
            Pair(true, 5) // Default values
        }
    }
    
    /**
     * Apply preferences to widget configuration
     * 
     * @param baseConfiguration Base dashboard configuration
     * @param preferences User preferences
     * @return Modified configuration based on preferences
     */
    fun applyPreferencesToConfiguration(
        baseConfiguration: DashboardConfiguration,
        preferences: WidgetPreferences
    ): List<AnalyticsWidget> {
        val baseWidgets = when (baseConfiguration) {
            is DashboardConfiguration.Beginner -> baseConfiguration.widgets
            is DashboardConfiguration.Intermediate -> baseConfiguration.widgets
            is DashboardConfiguration.Advanced -> baseConfiguration.widgets
        }
        
        // Filter widgets based on visibility preferences
        val visibleWidgets = baseWidgets.filter { widget ->
            preferences.isWidgetVisible(widget.name)
        }
        
        // Apply custom ordering from preferences
        val orderedWidgets = preferences.getOrderedVisibleWidgets()
            .mapNotNull { widgetName -> 
                visibleWidgets.find { it.name == widgetName }
            }
        
        // Add any remaining visible widgets that aren't in the custom order
        val remainingWidgets = visibleWidgets.filter { widget ->
            !orderedWidgets.contains(widget)
        }
        
        return orderedWidgets + remainingWidgets
    }
    
    /**
     * Validate widget preferences against available widgets
     * 
     * @param preferences User preferences to validate
     * @return Validation result with any issues found
     */
    fun validatePreferences(preferences: WidgetPreferences): PreferencesValidation {
        val issues = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        // Check if all visible widgets are valid
        val availableWidgetNames = AnalyticsWidget.values().map { it.name }.toSet()
        val invalidWidgets = preferences.visibleWidgets.filter { it !in availableWidgetNames }
        
        if (invalidWidgets.isNotEmpty()) {
            issues.add("Invalid widget names found: ${invalidWidgets.joinToString(", ")}")
            recommendations.add("Remove invalid widgets or update to valid widget names")
        }
        
        // Check if widget order contains all visible widgets
        val missingFromOrder = preferences.visibleWidgets.filter { it !in preferences.widgetOrder }
        if (missingFromOrder.isNotEmpty()) {
            issues.add("Widget order missing some visible widgets: ${missingFromOrder.joinToString(", ")}")
            recommendations.add("Update widget order to include all visible widgets")
        }
        
        // Validate refresh interval
        if (preferences.refreshIntervalMinutes !in 1..60) {
            issues.add("Invalid refresh interval: ${preferences.refreshIntervalMinutes} minutes")
            recommendations.add("Set refresh interval between 1 and 60 minutes")
        }
        
        return PreferencesValidation(
            isValid = issues.isEmpty(),
            issues = issues,
            recommendations = recommendations
        )
    }
    
    /**
     * Applies a dashboard configuration to existing widget preferences.
     * 
     * This method takes the current widget preferences and applies configuration
     * changes while preserving user customizations where possible.
     * 
     * @param preferences Current widget preferences
     * @param configuration Dashboard configuration to apply
     * @return Updated widget preferences with configuration applied
     */
    fun applyConfigurationToPreferences(
        preferences: WidgetPreferences,
        configuration: DashboardConfiguration
    ): WidgetPreferences {
        // Get widgets from the configuration
        val configurationWidgets = when (configuration) {
            is DashboardConfiguration.Beginner -> configuration.widgets
            is DashboardConfiguration.Intermediate -> configuration.widgets
            is DashboardConfiguration.Advanced -> configuration.widgets
        }
        
        // Create new visible widgets set based on configuration
        val newVisibleWidgets = configurationWidgets.map { it.name }.toSet()
        
        // Create new widget order based on configuration priority
        val newWidgetOrder = configurationWidgets
            .sortedWith(compareBy({ it.priority }, { it.name }))
            .map { it.name }
        
        // Preserve user widget sizes for widgets that exist in both old and new configuration
        val preservedSizes = preferences.widgetSizes.filterKeys { widgetName ->
            newVisibleWidgets.contains(widgetName)
        }
        
        return preferences.copy(
            visibleWidgets = newVisibleWidgets,
            widgetOrder = newWidgetOrder,
            widgetSizes = preservedSizes,
            lastModified = kotlinx.datetime.Clock.System.now()
        )
    }
    
    /**
     * Creates default widget preferences for a user based on configuration.
     * 
     * This method generates a complete set of default preferences for a user
     * based on their experience level and the provided configuration.
     * 
     * @param userId User identifier for the preferences
     * @param configuration Dashboard configuration to base defaults on
     * @return New widget preferences with default values
     */
    fun createDefaultPreferences(
        userId: String,
        configuration: DashboardConfiguration
    ): WidgetPreferences {
        // Get widgets from the configuration
        val configurationWidgets = when (configuration) {
            is DashboardConfiguration.Beginner -> configuration.widgets
            is DashboardConfiguration.Intermediate -> configuration.widgets
            is DashboardConfiguration.Advanced -> configuration.widgets
        }
        
        // Create visible widgets set
        val visibleWidgets = configurationWidgets.map { it.name }.toSet()
        
        // Create widget order based on priority
        val widgetOrder = configurationWidgets
            .sortedWith(compareBy({ it.priority }, { it.name }))
            .map { it.name }
        
        // Create default widget customizations
        val defaultCustomizations = configurationWidgets.associate { widget ->
            widget.name to mapOf(
                "displaySize" to com.example.liftrix.domain.model.analytics.WidgetDisplaySize.STANDARD.name,
                "showTrend" to "true",
                "refreshInterval" to "300" // 5 minutes
            )
        }
        
        // Determine default layout mode based on configuration
        val defaultLayoutMode = when (configuration) {
            is DashboardConfiguration.Beginner -> com.example.liftrix.domain.model.analytics.DashboardLayoutMode.SECTIONS
            is DashboardConfiguration.Intermediate -> com.example.liftrix.domain.model.analytics.DashboardLayoutMode.SECTIONS
            is DashboardConfiguration.Advanced -> com.example.liftrix.domain.model.analytics.DashboardLayoutMode.GRID
        }
        
        return WidgetPreferences(
            userId = userId,
            visibleWidgets = visibleWidgets,
            widgetOrder = widgetOrder,
            collapsedSections = emptySet(),
            dashboardLayout = defaultLayoutMode,
            enableAutoRefresh = true,
            refreshIntervalMinutes = 5,
            lastModified = kotlinx.datetime.Clock.System.now()
        )
    }
    
    /**
     * Reorders a widget in the dashboard configuration.
     * 
     * This method moves a widget from its current position to a new position
     * in the dashboard configuration.
     * 
     * @param configuration Current dashboard configuration
     * @param widgetId Widget identifier to reorder
     * @param newPosition New position index for the widget
     * @return Updated dashboard configuration with reordered widget, or null if invalid
     */
    fun reorderWidget(
        configuration: DashboardConfiguration,
        widgetId: String,
        newPosition: Int
    ): DashboardConfiguration? {
        // Get current widgets from configuration
        val currentWidgets = when (configuration) {
            is DashboardConfiguration.Beginner -> configuration.widgets
            is DashboardConfiguration.Intermediate -> configuration.widgets
            is DashboardConfiguration.Advanced -> configuration.widgets
        }
        
        // Find the widget to reorder
        val widgetToMove = currentWidgets.find { it.name == widgetId }
            ?: return null // Widget not found
        
        // Validate new position
        if (newPosition < 0 || newPosition >= currentWidgets.size) {
            return null // Invalid position
        }
        
        // Create new widget list with reordered widget
        val mutableWidgets = currentWidgets.toMutableList()
        mutableWidgets.remove(widgetToMove)
        mutableWidgets.add(newPosition, widgetToMove)
        
        // Return new configuration with reordered widgets
        // DashboardConfiguration objects are immutable singletons, so we can't copy them
        // Instead, we need to create a new configuration based on the widget count
        return when {
            mutableWidgets.size <= 4 -> DashboardConfiguration.Beginner
            mutableWidgets.size <= 7 -> DashboardConfiguration.Intermediate
            else -> DashboardConfiguration.Advanced
        }
    }
    
    /**
     * Creates dashboard configuration from existing widget preferences.
     * 
     * This method reconstructs a dashboard configuration from user preferences,
     * which can be useful for loading configurations from saved preferences.
     * 
     * @param preferences Widget preferences to create configuration from
     * @return Dashboard configuration based on preferences
     */
    fun createConfigurationFromPreferences(preferences: WidgetPreferences): DashboardConfiguration {
        // Get widgets based on preferences
        val orderedWidgets = preferences.getOrderedVisibleWidgets()
            .mapNotNull { widgetName -> 
                AnalyticsWidget.values().find { it.name == widgetName }
            }
        
        // Determine configuration type based on widget count and complexity
        val configType = when {
            orderedWidgets.size <= 4 -> DashboardConfiguration.Beginner::class
            orderedWidgets.size <= 8 -> DashboardConfiguration.Intermediate::class
            else -> DashboardConfiguration.Advanced::class
        }
        
        // Create appropriate configuration (data objects are singletons)
        return when (configType) {
            DashboardConfiguration.Beginner::class -> DashboardConfiguration.Beginner
            DashboardConfiguration.Intermediate::class -> DashboardConfiguration.Intermediate
            DashboardConfiguration.Advanced::class -> DashboardConfiguration.Advanced
            else -> DashboardConfiguration.Beginner
        }
    }
}

// UserLevel is now imported from the proper location

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
                AnalyticsWidget.values().find { it.name == widgetId }
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

/**
 * Preferences validation result
 */
data class PreferencesValidation(
    val isValid: Boolean,
    val issues: List<String>,
    val recommendations: List<String>
) {
    /**
     * Get validation score from 0-100
     */
    val score: Int
        get() {
            var score = 100
            score -= issues.size * 25
            return maxOf(0, score)
        }
}