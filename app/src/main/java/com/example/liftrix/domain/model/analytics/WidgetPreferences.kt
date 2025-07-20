package com.example.liftrix.domain.model.analytics

import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Domain model representing user widget preferences and dashboard configuration.
 * 
 * This model encapsulates all widget-related user preferences including visibility settings,
 * layout preferences, and customization options. It provides a clean abstraction for
 * persisting widget configurations across app sessions and device restarts.
 * 
 * @property userId The unique identifier for the user
 * @property visibleWidgets Set of widget names that should be displayed
 * @property widgetOrder List of widget names in the preferred display order
 * @property dashboardLayout Preferred dashboard layout mode
 * @property userLevel User experience level for widget configuration
 * @property collapsedSections Set of section names that are collapsed
 * @property widgetSizes Map of widget names to their preferred sizes
 * @property enableAutoRefresh Whether widgets should auto-refresh data
 * @property refreshIntervalMinutes Refresh interval in minutes for auto-refresh
 * @property lastModified Timestamp of the last modification
 */
data class WidgetPreferences(
    val userId: String,
    val visibleWidgets: Set<String> = getDefaultVisibleWidgets(),
    val widgetOrder: List<String> = getDefaultWidgetOrder(),
    val dashboardLayout: DashboardLayoutMode = DashboardLayoutMode.SECTIONS,
    val userLevel: UserLevel = UserLevel.BEGINNER,
    val collapsedSections: Set<String> = emptySet(),
    val widgetSizes: Map<String, WidgetDisplaySize> = emptyMap(),
    val enableAutoRefresh: Boolean = true,
    val refreshIntervalMinutes: Int = 5,
    val lastModified: Instant = Clock.System.now()
) {
    
    companion object {
        
        /**
         * Creates default widget preferences for a new user.
         * 
         * @param userId The user's unique identifier
         * @param userLevel Optional user experience level (defaults to BEGINNER)
         * @return WidgetPreferences with default values
         */
        fun createDefault(
            userId: String, 
            userLevel: UserLevel = UserLevel.BEGINNER
        ): WidgetPreferences {
            val defaultConfiguration = when (userLevel) {
                UserLevel.BEGINNER -> DashboardConfiguration.Beginner
                UserLevel.INTERMEDIATE -> DashboardConfiguration.Intermediate
                UserLevel.ADVANCED -> DashboardConfiguration.Advanced
            }
            
            return WidgetPreferences(
                userId = userId,
                visibleWidgets = getWidgetsFromConfiguration(defaultConfiguration).map { it.id }.toSet(),
                widgetOrder = getWidgetsFromConfiguration(defaultConfiguration).map { it.id },
                dashboardLayout = DashboardLayoutMode.SECTIONS,
                userLevel = userLevel,
                collapsedSections = emptySet(),
                widgetSizes = createDefaultSizes(getWidgetsFromConfiguration(defaultConfiguration)),
                enableAutoRefresh = true,
                refreshIntervalMinutes = 5,
                lastModified = Clock.System.now()
            )
        }
        
        /**
         * Get default visible widgets for first-time users
         */
        private fun getDefaultVisibleWidgets(): Set<String> {
            return setOf(
                AnalyticsWidget.TotalVolume.id,
                AnalyticsWidget.WorkoutFrequency.id,
                AnalyticsWidget.ConsistencyStreak.id,
                AnalyticsWidget.CaloriesBurned.id
            )
        }
        
        /**
         * Get default widget order for consistent layout
         */
        private fun getDefaultWidgetOrder(): List<String> {
            return listOf(
                AnalyticsWidget.TotalVolume.id,
                AnalyticsWidget.WorkoutFrequency.id,
                AnalyticsWidget.ConsistencyStreak.id,
                AnalyticsWidget.CaloriesBurned.id
            )
        }
        
        /**
         * Get widgets from dashboard configuration using proper widget selection logic.
         * 
         * NOTE: This method now uses the same logic as WidgetResolver for consistency.
         * In practice, WidgetResolver should be used directly instead of this method.
         */
        private fun getWidgetsFromConfiguration(config: DashboardConfiguration): List<AnalyticsWidget> {
            val allWidgets = AnalyticsWidget.getAllWidgets()
            
            return when (config) {
                is DashboardConfiguration.Beginner -> {
                    // 4 essential widgets for building workout habits
                    allWidgets
                        .filter { it.priority == WidgetPriority.ESSENTIAL }
                        .sortedWith(compareBy({ it.complexity }, { it.getLayoutPriority() }))
                        .take(4)
                        .ifEmpty {
                            // Fallback: select simplest widgets if no essential ones
                            allWidgets
                                .filter { it.complexity == WidgetComplexity.SIMPLE }
                                .sortedBy { it.getLayoutPriority() }
                                .take(4)
                        }
                }
                is DashboardConfiguration.Intermediate -> {
                    // 7 widgets: essential + some trend analysis
                    val essential = allWidgets.filter { it.priority == WidgetPriority.ESSENTIAL }
                    val trends = allWidgets.filter { 
                        it.category == WidgetCategory.CHARTS && 
                        it.complexity != WidgetComplexity.COMPLEX 
                    }
                    val metrics = allWidgets.filter { 
                        it.category == WidgetCategory.METRICS && 
                        it.priority == WidgetPriority.STANDARD 
                    }
                    
                    (essential + trends + metrics)
                        .distinctBy { it.id }
                        .sortedBy { it.getLayoutPriority() }
                        .take(7)
                }
                is DashboardConfiguration.Advanced -> {
                    // 10 widgets: comprehensive analytics with balanced selection across categories
                    val byCategory = allWidgets.groupBy { it.category }
                    val selected = mutableListOf<AnalyticsWidget>()
                    
                    // Ensure essential widgets are included
                    val essential = allWidgets.filter { it.priority == WidgetPriority.ESSENTIAL }
                    selected.addAll(essential.take(3))
                    
                    // Add widgets from each category
                    byCategory.forEach { (category, categoryWidgets) ->
                        val remaining = 10 - selected.size
                        if (remaining > 0) {
                            val toAdd = categoryWidgets
                                .filter { !selected.contains(it) }
                                .sortedWith(compareBy({ it.complexity }, { it.getLayoutPriority() }))
                                .take(minOf(remaining / byCategory.size + 1, 3))
                            selected.addAll(toAdd)
                        }
                    }
                    
                    selected.take(10).sortedBy { it.getLayoutPriority() }
                }
                is DashboardConfiguration.Custom -> {
                    // Custom configuration allows all widgets up to advanced level
                    allWidgets.sortedBy { it.getLayoutPriority() }.take(10)
                }
            }
        }
        
        /**
         * Create default widget sizes based on widget types
         */
        private fun createDefaultSizes(widgets: List<AnalyticsWidget>): Map<String, WidgetDisplaySize> {
            return widgets.associate { widget ->
                widget.id to when (widget.getRecommendedSize()) {
                    com.example.liftrix.domain.model.analytics.WidgetSize.SMALL -> WidgetDisplaySize.COMPACT
                    com.example.liftrix.domain.model.analytics.WidgetSize.MEDIUM -> WidgetDisplaySize.STANDARD
                    com.example.liftrix.domain.model.analytics.WidgetSize.LARGE -> WidgetDisplaySize.EXPANDED
                }
            }
        }
    }
    
    /**
     * Validates that the preferences are in a valid state.
     * 
     * @throws IllegalArgumentException if the preferences are invalid
     */
    fun validate() {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(refreshIntervalMinutes in 1..60) { "Refresh interval must be between 1 and 60 minutes" }
        require(visibleWidgets.isNotEmpty()) { "At least one widget must be visible" }
        require(widgetOrder.containsAll(visibleWidgets)) { 
            val missingWidgets = visibleWidgets - widgetOrder.toSet()
            "Widget order must include all visible widgets. Missing: ${missingWidgets.joinToString(", ")}"
        }
    }
    
    /**
     * Repairs inconsistent widget preferences by ensuring widget order includes all visible widgets.
     * 
     * @return WidgetPreferences with consistent widget order
     */
    fun repairConsistency(): WidgetPreferences {
        // Get available widget names for validation
        val availableWidgetNames = try {
            AnalyticsWidget.getAllWidgets().map { it.id }.toSet()
        } catch (e: Exception) {
            // Fallback if widget access fails
            emptySet<String>()
        }
        
        // Filter out invalid widget names
        val validVisibleWidgets = if (availableWidgetNames.isNotEmpty()) {
            visibleWidgets.filter { it in availableWidgetNames }.toSet()
        } else {
            visibleWidgets
        }
        
        // Ensure widget order contains all visible widgets
        val existingValidOrder = widgetOrder.filter { it in validVisibleWidgets }
        val missingFromOrder = validVisibleWidgets - existingValidOrder.toSet()
        val repairedWidgetOrder = existingValidOrder + missingFromOrder.toList()
        
        return copy(
            visibleWidgets = validVisibleWidgets,
            widgetOrder = repairedWidgetOrder,
            lastModified = Clock.System.now()
        )
    }
    
    /**
     * Creates a copy of the preferences with an updated timestamp.
     * 
     * @return WidgetPreferences with current timestamp
     */
    fun withUpdatedTimestamp(): WidgetPreferences = copy(lastModified = Clock.System.now())
    
    /**
     * Updates visible widgets and recalculates order if needed.
     * 
     * @param newVisibleWidgets Set of widget names to display
     * @return Updated WidgetPreferences
     */
    fun updateVisibleWidgets(newVisibleWidgets: Set<String>): WidgetPreferences {
        val newOrder = widgetOrder.filter { it in newVisibleWidgets } + 
                      newVisibleWidgets.filter { it !in widgetOrder }
        
        return copy(
            visibleWidgets = newVisibleWidgets,
            widgetOrder = newOrder,
            lastModified = Clock.System.now()
        )
    }
    
    /**
     * Updates widget order while maintaining visible widgets.
     * 
     * @param newOrder List of widget names in preferred order
     * @return Updated WidgetPreferences
     */
    fun updateWidgetOrder(newOrder: List<String>): WidgetPreferences {
        val validOrder = newOrder.filter { it in visibleWidgets }
        val missingWidgets = visibleWidgets.filter { it !in validOrder }
        
        return copy(
            widgetOrder = validOrder + missingWidgets,
            lastModified = Clock.System.now()
        )
    }
    
    /**
     * Updates dashboard layout mode.
     * 
     * @param newLayout New dashboard layout mode
     * @return Updated WidgetPreferences
     */
    fun updateLayout(newLayout: DashboardLayoutMode): WidgetPreferences {
        return copy(
            dashboardLayout = newLayout,
            lastModified = Clock.System.now()
        )
    }
    
    /**
     * Updates user level and recalculates default configurations.
     * 
     * @param newLevel New user experience level
     * @return Updated WidgetPreferences
     */
    fun updateUserLevel(newLevel: UserLevel): WidgetPreferences {
        val newConfiguration = when (newLevel) {
            UserLevel.BEGINNER -> DashboardConfiguration.Beginner
            UserLevel.INTERMEDIATE -> DashboardConfiguration.Intermediate
            UserLevel.ADVANCED -> DashboardConfiguration.Advanced
        }
        
        return copy(
            userLevel = newLevel,
            visibleWidgets = getWidgetsFromConfiguration(newConfiguration).map { it.id }.toSet(),
            widgetOrder = getWidgetsFromConfiguration(newConfiguration).map { it.id },
            lastModified = Clock.System.now()
        )
    }
    
    /**
     * Toggles widget visibility.
     * 
     * @param widgetName Name of the widget to toggle
     * @return Updated WidgetPreferences
     */
    fun toggleWidget(widgetName: String): WidgetPreferences {
        val newVisibleWidgets = if (widgetName in visibleWidgets) {
            // Don't allow removing all widgets
            if (visibleWidgets.size > 1) {
                visibleWidgets - widgetName
            } else {
                visibleWidgets
            }
        } else {
            visibleWidgets + widgetName
        }
        
        return updateVisibleWidgets(newVisibleWidgets)
    }
    
    /**
     * Toggles section collapsed state.
     * 
     * @param sectionName Name of the section to toggle
     * @return Updated WidgetPreferences
     */
    fun toggleSection(sectionName: String): WidgetPreferences {
        val newCollapsedSections = if (sectionName in collapsedSections) {
            collapsedSections - sectionName
        } else {
            collapsedSections + sectionName
        }
        
        return copy(
            collapsedSections = newCollapsedSections,
            lastModified = Clock.System.now()
        )
    }
    
    /**
     * Updates widget size preference.
     * 
     * @param widgetName Name of the widget
     * @param size New display size
     * @return Updated WidgetPreferences
     */
    fun updateWidgetSize(widgetName: String, size: WidgetDisplaySize): WidgetPreferences {
        return copy(
            widgetSizes = widgetSizes + (widgetName to size),
            lastModified = Clock.System.now()
        )
    }
    
    /**
     * Updates auto-refresh settings.
     * 
     * @param enabled Whether auto-refresh should be enabled
     * @param intervalMinutes Refresh interval in minutes (1-60)
     * @return Updated WidgetPreferences
     */
    fun updateAutoRefresh(enabled: Boolean, intervalMinutes: Int = refreshIntervalMinutes): WidgetPreferences {
        require(intervalMinutes in 1..60) { "Refresh interval must be between 1 and 60 minutes" }
        
        return copy(
            enableAutoRefresh = enabled,
            refreshIntervalMinutes = intervalMinutes,
            lastModified = Clock.System.now()
        )
    }
    
    /**
     * Gets ordered visible widgets for UI display.
     * 
     * @return List of visible widgets in preferred order
     */
    fun getOrderedVisibleWidgets(): List<String> {
        return widgetOrder.filter { it in visibleWidgets }
    }
    
    /**
     * Checks if a widget is visible.
     * 
     * @param widgetName Name of the widget to check
     * @return Boolean indicating if widget is visible
     */
    fun isWidgetVisible(widgetName: String): Boolean {
        return widgetName in visibleWidgets
    }
    
    /**
     * Checks if a section is collapsed.
     * 
     * @param sectionName Name of the section to check
     * @return Boolean indicating if section is collapsed
     */
    fun isSectionCollapsed(sectionName: String): Boolean {
        return sectionName in collapsedSections
    }
    
    /**
     * Gets widget display size or default.
     * 
     * @param widgetName Name of the widget
     * @return Widget display size
     */
    fun getWidgetSize(widgetName: String): WidgetDisplaySize {
        return widgetSizes[widgetName] ?: WidgetDisplaySize.STANDARD
    }
}

/**
 * Dashboard layout modes for different display preferences
 */
enum class DashboardLayoutMode(val displayName: String, val description: String) {
    GRID("Grid", "Traditional grid layout with equal-sized widgets"),
    SECTIONS("Sections", "Organized sections with collapsible categories"),
    LIST("List", "Vertical list layout optimized for scrolling"),
    CUSTOM("Custom", "User-defined custom layout configuration")
}

/**
 * Widget display sizes for UI customization
 */
enum class WidgetDisplaySize(val displayName: String, val heightMultiplier: Float) {
    COMPACT("Compact", 0.8f),
    STANDARD("Standard", 1.0f),
    EXPANDED("Expanded", 1.4f)
}

/**
 * User experience levels for widget configuration compatibility
 */
enum class UserLevel(val displayName: String, val description: String) {
    BEGINNER("Beginner", "Essential metrics for building workout consistency"),
    INTERMEDIATE("Intermediate", "Enhanced metrics for optimizing training progress"), 
    ADVANCED("Advanced", "Comprehensive analytics for advanced training optimization")
}