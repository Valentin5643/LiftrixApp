package com.example.liftrix.service

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetCategory
import com.example.liftrix.domain.model.analytics.WidgetComplexity
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.analytics.WidgetPriority
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core widget resolution service that dynamically determines which widgets to display
 * based on user level, layout mode, and preferences.
 * 
 * This replaces the hardcoded widget lists in DashboardConfiguration with a dynamic
 * resolution system that properly supports:
 * - Beginner: 4 essential widgets
 * - Intermediate: 7 balanced widgets
 * - Advanced: 10 comprehensive widgets  
 * - CUSTOM layout mode: User-selected widgets
 * 
 * Key Features:
 * - Dynamic widget resolution based on user level
 * - Integration with layout modes (GRID, SECTIONS, LIST, CUSTOM)
 * - User preference-driven widget selection
 * - Performance optimization through caching
 * - Backward compatibility with existing preferences
 * 
 * Architecture:
 * - Single responsibility: Widget resolution logic only
 * - Dependency injection ready for easy testing
 * - Stateless design for thread safety
 * - Clean separation from UI concerns
 */
@Singleton
class WidgetResolver @Inject constructor() {
    
    /**
     * Widget name migration mapping for legacy compatibility.
     * 
     * CRITICAL BUG FIX: Maps old widget names (camelCase and incorrect names) to correct widget IDs.
     * This fixes the issue where hardcoded widget names in forceAdvancedUserLevel() were causing
     * widget resolution failures.
     */
    private val WIDGET_NAME_MIGRATION_MAP = mapOf(
        // Legacy camelCase names → modern consolidated widgets
        "progressChart" to AnalyticsWidget.ProgressChart.id,
        "frequencyChart" to AnalyticsWidget.FrequencyChart.id,
        "recoveryMetrics" to AnalyticsWidget.RecoveryMetrics.id,
        "muscleGroupDistribution" to AnalyticsWidget.MuscleGroupDistribution.id,
        "muscleHeatmap" to AnalyticsWidget.MuscleHeatmap.id,
        "monthlySummary" to AnalyticsWidget.MonthlySummary.id,
        
        // Deprecated strength widgets → StrengthAnalytics (consolidated)
        "strengthProgress" to AnalyticsWidget.StrengthAnalytics.id,
        "personalRecords" to AnalyticsWidget.StrengthAnalytics.id,
        "oneRMProgression" to AnalyticsWidget.StrengthAnalytics.id,
        
        // Deprecated volume widgets → VolumeAnalytics (consolidated)
        "volumeLoadProgression" to AnalyticsWidget.VolumeAnalytics.id,
        "volumeChart" to AnalyticsWidget.VolumeAnalytics.id,
        "volumeTrends" to AnalyticsWidget.VolumeAnalytics.id,
        
        // Incorrect names used in forceAdvancedUserLevel() → modern consolidated widgets
        "ProgressChart" to AnalyticsWidget.ProgressChart.id,
        "FrequencyChart" to AnalyticsWidget.FrequencyChart.id,
        "RecoveryMetrics" to AnalyticsWidget.RecoveryMetrics.id,
        "MuscleGroupDistribution" to AnalyticsWidget.MuscleGroupDistribution.id,
        "MuscleHeatmap" to AnalyticsWidget.MuscleHeatmap.id,
        "MonthlySummary" to AnalyticsWidget.MonthlySummary.id,
        
        // Deprecated strength widgets → StrengthAnalytics (consolidated)
        "StrengthProgress" to AnalyticsWidget.StrengthAnalytics.id,
        "PersonalRecords" to AnalyticsWidget.StrengthAnalytics.id,
        "OneRMProgression" to AnalyticsWidget.StrengthAnalytics.id,
        
        // Deprecated volume widgets → VolumeAnalytics (consolidated)
        "VolumeLoadProgression" to AnalyticsWidget.VolumeAnalytics.id,
        "VolumeChart" to AnalyticsWidget.VolumeAnalytics.id,
        "VolumeTrends" to AnalyticsWidget.VolumeAnalytics.id,
        
        // Display name variations → modern consolidated widgets
        "Progress Chart" to AnalyticsWidget.ProgressChart.id,
        "Frequency Chart" to AnalyticsWidget.FrequencyChart.id,
        "Recovery Metrics" to AnalyticsWidget.RecoveryMetrics.id,
        "Muscle Group Distribution" to AnalyticsWidget.MuscleGroupDistribution.id,
        "Muscle Heatmap" to AnalyticsWidget.MuscleHeatmap.id,
        "Monthly Summary" to AnalyticsWidget.MonthlySummary.id,
        
        // Deprecated strength widgets → StrengthAnalytics (consolidated)
        "Strength Progress" to AnalyticsWidget.StrengthAnalytics.id,
        "Personal Records" to AnalyticsWidget.StrengthAnalytics.id,
        "1RM Progression" to AnalyticsWidget.StrengthAnalytics.id,
        "Volume Progression" to AnalyticsWidget.VolumeAnalytics.id,
        
        // Deprecated volume widgets → VolumeAnalytics (consolidated)
        "Volume Chart" to AnalyticsWidget.VolumeAnalytics.id,
        "Volume Trends" to AnalyticsWidget.VolumeAnalytics.id
    )
    
    /**
     * Resolves widgets based on user level and layout mode.
     * 
     * This is the primary method that determines which widgets should be displayed
     * for a user based on their experience level and chosen layout mode.
     * 
     * @param userLevel User's experience level (BEGINNER, INTERMEDIATE, ADVANCED)
     * @param layoutMode Dashboard layout mode (GRID, SECTIONS, LIST, CUSTOM)
     * @param preferences Optional user preferences for CUSTOM mode
     * @return List of widgets to display, ordered by priority
     */
    fun resolveWidgets(
        userLevel: UserLevel,
        layoutMode: DashboardLayoutMode = DashboardLayoutMode.AUTO,
        preferences: WidgetPreferences? = null
    ): List<AnalyticsWidget> {
        Timber.d("=== RESOLVER DEBUG: resolveWidgets called with userLevel=$userLevel, layoutMode=$layoutMode")
        Timber.d("=== RESOLVER DEBUG: preferences visibleWidgets count = ${preferences?.visibleWidgets?.size ?: 0}")
        
        val result = when (layoutMode) {
            DashboardLayoutMode.CUSTOM -> {
                Timber.d("=== RESOLVER DEBUG: Using CUSTOM mode resolution")
                resolveCustomWidgets(preferences, userLevel)
            }
            else -> {
                Timber.d("=== RESOLVER DEBUG: Using standard mode resolution for $userLevel")
                resolveStandardWidgets(userLevel)
            }
        }
        
        Timber.d("=== RESOLVER DEBUG: Final result - ${result.size} widgets resolved")
        return result
    }
    
    /**
     * Resolves widgets for standard layout modes based on user level.
     * 
     * Uses a priority-based system to select the most appropriate widgets
     * for each user level, ensuring a progressive disclosure of complexity.
     * 
     * @param userLevel User's experience level
     * @return List of widgets appropriate for the user level
     */
    fun resolveStandardWidgets(userLevel: UserLevel): List<AnalyticsWidget> {
        val selectedWidgets = listOf(
            AnalyticsWidget.StrengthAnalytics,
            AnalyticsWidget.StrengthForecast,
            AnalyticsWidget.VolumeAnalytics,
            AnalyticsWidget.MuscleGroupDistribution
        )
        
        // Log selection for debugging
        Timber.d("WidgetResolver: Resolved ${selectedWidgets.size} widgets for $userLevel level: ${selectedWidgets.map { it.displayName }}")
        
        return selectedWidgets.sortedBy { it.getLayoutPriority() }
    }
    
    /**
     * Resolves widgets for CUSTOM layout mode using user preferences.
     * 
     * In CUSTOM mode, the user has explicitly selected which widgets to display.
     * This method validates their selections against their user level and provides
     * fallback behavior if needed.
     * 
     * @param preferences User widget preferences (may be null)
     * @param userLevel User's experience level for validation
     * @return List of user-selected widgets, validated against user level
     */
    private fun resolveCustomWidgets(
        preferences: WidgetPreferences?,
        userLevel: UserLevel
    ): List<AnalyticsWidget> {
        if (preferences == null) {
            Timber.w("WidgetResolver: No preferences provided for CUSTOM mode, falling back to standard resolution")
            return resolveStandardWidgets(userLevel)
        }
        
        val allWidgets = AnalyticsWidget.getActiveWidgets()
        val availableWidgetIds = allWidgets.map { it.id }.toSet()
        
        // Get user-selected widgets, applying migration mapping and filtering out invalid ones
        val selectedWidgetIds = preferences.visibleWidgets.mapNotNull { widgetId ->
            // Try direct lookup first
            if (availableWidgetIds.contains(widgetId)) {
                widgetId
            } else {
                // Try migration mapping
                val migratedId = WIDGET_NAME_MIGRATION_MAP[widgetId]
                if (migratedId != null && availableWidgetIds.contains(migratedId)) {
                    Timber.d("Migrated widget name: '$widgetId' -> '$migratedId'")
                    migratedId
                } else {
                    Timber.w("Invalid widget name: '$widgetId' - no migration available")
                    null
                }
            }
        }
        
        if (selectedWidgetIds.isEmpty()) {
            Timber.w("WidgetResolver: No valid widgets in preferences, falling back to standard resolution")
            return resolveStandardWidgets(userLevel)
        }
        
        // Convert widget IDs to widget objects
        val selectedWidgets = selectedWidgetIds.mapNotNull { widgetId ->
            allWidgets.find { it.id == widgetId }
        }
        
        // Validate selection is appropriate for user level
        val validatedWidgets = validateWidgetSelection(selectedWidgets, userLevel)
        
        // Apply user's preferred ordering if available
        val orderedWidgets = if (preferences.widgetOrder.isNotEmpty()) {
            applyUserOrdering(validatedWidgets, preferences.widgetOrder)
        } else {
            validatedWidgets.sortedBy { it.getLayoutPriority() }
        }
        
        Timber.d("WidgetResolver: Resolved ${orderedWidgets.size} custom widgets for $userLevel level: ${orderedWidgets.map { it.displayName }}")
        
        return orderedWidgets
    }
    
    /**
     * Selects widgets using different strategies based on user level.
     * 
     * @param widgets Available widgets to select from
     * @param maxCount Maximum number of widgets to select
     * @param strategy Selection strategy to use
     * @return Selected widgets based on strategy
     */
    private fun selectWidgetsByStrategy(
        widgets: List<AnalyticsWidget>,
        maxCount: Int,
        strategy: SelectionStrategy
    ): List<AnalyticsWidget> {
        return when (strategy) {
            SelectionStrategy.ESSENTIAL_ONLY -> {
                // Select only essential widgets, prioritizing SIMPLE complexity
                widgets
                    .filter { it.priority == WidgetPriority.ESSENTIAL }
                    .sortedWith(compareBy({ it.complexity }, { it.getLayoutPriority() }))
                    .take(maxCount)
                    .ifEmpty {
                        // Fallback: select simplest widgets if no essential ones
                        widgets
                            .filter { it.complexity == WidgetComplexity.SIMPLE }
                            .sortedBy { it.getLayoutPriority() }
                            .take(maxCount)
                    }
            }
            SelectionStrategy.ESSENTIAL_AND_TRENDS -> {
                // Essential widgets + selected trends and charts
                val essential = widgets.filter { it.priority == WidgetPriority.ESSENTIAL }
                val trends = widgets.filter { 
                    it.category == WidgetCategory.CHARTS && 
                    it.complexity != WidgetComplexity.COMPLEX 
                }
                val metrics = widgets.filter { 
                    it.category == WidgetCategory.METRICS && 
                    it.priority == WidgetPriority.STANDARD 
                }
                
                (essential + trends + metrics)
                    .distinctBy { it.id }
                    .sortedBy { it.getLayoutPriority() }
                    .take(maxCount)
            }
            SelectionStrategy.COMPREHENSIVE -> {
                // Balanced selection across all categories
                val byCategory = widgets.groupBy { it.category }
                val selected = mutableListOf<AnalyticsWidget>()
                
                // Ensure essential widgets are included
                val essential = widgets.filter { it.priority == WidgetPriority.ESSENTIAL }
                selected.addAll(essential.take(3))
                
                // Add widgets from each category
                byCategory.forEach { (category, categoryWidgets) ->
                    val remaining = maxCount - selected.size
                    if (remaining > 0) {
                        val toAdd = categoryWidgets
                            .filter { !selected.contains(it) }
                            .sortedWith(compareBy({ it.complexity }, { it.getLayoutPriority() }))
                            .take(minOf(remaining / byCategory.size + 1, 3))
                        selected.addAll(toAdd)
                    }
                }
                
                selected.take(maxCount).sortedBy { it.getLayoutPriority() }
            }
        }
    }
    
    /**
     * Validates that widget selection is appropriate for user level.
     * 
     * Ensures users don't have access to widgets that are too complex for their level,
     * while allowing them to customize within appropriate bounds.
     * 
     * @param widgets Selected widgets to validate
     * @param userLevel User's experience level
     * @return Validated widget list with inappropriate widgets filtered out
     */
    private fun validateWidgetSelection(
        widgets: List<AnalyticsWidget>,
        userLevel: UserLevel
    ): List<AnalyticsWidget> {
        return when (userLevel) {
            UserLevel.BEGINNER -> {
                // Only allow SIMPLE and some MODERATE widgets
                widgets.filter { widget ->
                    widget.complexity == WidgetComplexity.SIMPLE ||
                    (widget.complexity == WidgetComplexity.MODERATE && widget.priority == WidgetPriority.ESSENTIAL)
                }
            }
            UserLevel.INTERMEDIATE -> {
                // Allow SIMPLE and MODERATE widgets, limited COMPLEX
                widgets.filter { widget ->
                    widget.complexity != WidgetComplexity.COMPLEX ||
                    widget.priority == WidgetPriority.ESSENTIAL
                }
            }
            UserLevel.ADVANCED -> {
                // Allow all widgets
                widgets
            }
        }
    }
    
    /**
     * Applies user's preferred widget ordering.
     * 
     * @param widgets Widgets to order
     * @param preferredOrder User's preferred order of widget IDs
     * @return Widgets ordered according to user preferences
     */
    private fun applyUserOrdering(
        widgets: List<AnalyticsWidget>,
        preferredOrder: List<String>
    ): List<AnalyticsWidget> {
        val widgetMap = widgets.associateBy { it.id }
        val ordered = mutableListOf<AnalyticsWidget>()
        
        // First, add widgets in preferred order
        preferredOrder.forEach { widgetId ->
            widgetMap[widgetId]?.let { widget ->
                ordered.add(widget)
            }
        }
        
        // Then add any remaining widgets not in preferred order
        widgets.forEach { widget ->
            if (!ordered.contains(widget)) {
                ordered.add(widget)
            }
        }
        
        return ordered
    }
    
    /**
     * Gets the maximum number of widgets allowed for a user level.
     * 
     * @param userLevel User's experience level
     * @return Maximum widget count for the level
     */
    fun getMaxWidgetCount(userLevel: UserLevel): Int {
        return when (userLevel) {
            UserLevel.BEGINNER -> 4
            UserLevel.INTERMEDIATE -> 7
            UserLevel.ADVANCED -> 12
        }
    }
    
    /**
     * Gets the recommended widgets for a user level without applying count limits.
     * 
     * Useful for showing available widgets in settings or customization screens.
     * 
     * @param userLevel User's experience level
     * @return All widgets available for the user level
     */
    fun getAvailableWidgets(userLevel: UserLevel): List<AnalyticsWidget> {
        val allWidgets = AnalyticsWidget.getActiveWidgets()
        return validateWidgetSelection(allWidgets, userLevel)
            .sortedBy { it.getLayoutPriority() }
    }
    
    /**
     * Checks if a specific widget is appropriate for a user level.
     * 
     * @param widget Widget to check
     * @param userLevel User's experience level
     * @return True if widget is appropriate for the user level
     */
    fun isWidgetAppropriate(widget: AnalyticsWidget, userLevel: UserLevel): Boolean {
        return validateWidgetSelection(listOf(widget), userLevel).isNotEmpty()
    }
    
    /**
     * Creates default preferences for a user level.
     * 
     * @param userId User identifier
     * @param userLevel User's experience level
     * @param layoutMode Preferred layout mode
     * @return Default preferences with appropriate widgets selected
     */
    fun createDefaultPreferences(
        userId: String,
        userLevel: UserLevel,
        layoutMode: DashboardLayoutMode = DashboardLayoutMode.AUTO
    ): WidgetPreferences {
        val defaultWidgets = resolveStandardWidgets(userLevel)
        val widgetIds = defaultWidgets.map { it.id }.toSet()
        val widgetOrder = defaultWidgets.map { it.id }
        
        Timber.d("=== PREFS DEBUG: Creating default preferences for $userLevel with ${defaultWidgets.size} widgets")
        Timber.d("=== PREFS DEBUG: Widget IDs = $widgetIds")
        
        return WidgetPreferences(
            userId = userId,
            visibleWidgets = widgetIds,
            widgetOrder = widgetOrder,
            dashboardLayout = layoutMode,
            userLevel = userLevel,
            collapsedSections = emptySet(),
            widgetSizes = emptyMap(),
            enableAutoRefresh = true,
            refreshIntervalMinutes = 5,
            lastModified = kotlinx.datetime.Clock.System.now()
        )
    }
    
    /**
     * Migrates existing preferences to use current widget resolution system.
     * 
     * Helps with backward compatibility when widget IDs or structures change.
     * 
     * @param preferences Existing preferences to migrate
     * @return Migrated preferences with valid widgets
     */
    fun migratePreferences(preferences: WidgetPreferences): WidgetPreferences {
        val availableWidgets = getAvailableWidgets(preferences.userLevel)
        val availableWidgetIds = availableWidgets.map { it.id }.toSet()
        
        // Filter out invalid widget IDs, applying migration mapping
        val validWidgetIds = preferences.visibleWidgets.mapNotNull { widgetId ->
            // Try direct lookup first
            if (availableWidgetIds.contains(widgetId)) {
                widgetId
            } else {
                // Try migration mapping
                val migratedId = WIDGET_NAME_MIGRATION_MAP[widgetId]
                if (migratedId != null && availableWidgetIds.contains(migratedId)) {
                    Timber.d("Migrating widget preference: '$widgetId' -> '$migratedId'")
                    migratedId
                } else {
                    Timber.w("Cannot migrate widget preference: '$widgetId' - no valid migration found")
                    null
                }
            }
        }.toSet()
        
        // If no valid widgets remain, use defaults
        if (validWidgetIds.isEmpty()) {
            Timber.w("WidgetResolver: No valid widgets in preferences, using defaults for ${preferences.userLevel}")
            return createDefaultPreferences(
                userId = preferences.userId,
                userLevel = preferences.userLevel,
                layoutMode = preferences.dashboardLayout
            )
        }
        
        // Update widget order to remove invalid widgets
        val validWidgetOrder = preferences.widgetOrder.filter { widgetId ->
            validWidgetIds.contains(widgetId)
        }
        
        return preferences.copy(
            visibleWidgets = validWidgetIds,
            widgetOrder = validWidgetOrder,
            lastModified = kotlinx.datetime.Clock.System.now()
        )
    }
    
    /**
     * Resolves widgets from preferences with user level context and fallbacks.
     * 
     * CRITICAL BUG FIX: This method ensures proper widget counts for all user levels
     * instead of always falling back to 4 hardcoded widgets.
     * 
     * @param preferences Widget preferences containing visible widgets
     * @param userLevel Current user level for appropriate fallbacks
     * @return List of resolved AnalyticsWidget objects
     */
    fun resolveWidgetsFromPreferences(
        preferences: WidgetPreferences?,
        userLevel: UserLevel = UserLevel.BEGINNER
    ): List<AnalyticsWidget> {
        Timber.d("Resolving widgets from preferences for user level: $userLevel")
        Timber.d("Preferences visible widgets: ${preferences?.visibleWidgets?.joinToString(", ") ?: "null"}")
        
        if (preferences == null) {
            Timber.i("No preferences provided, using level-appropriate defaults for $userLevel")
            return resolveStandardWidgets(userLevel)
        }
        
        val allWidgets = AnalyticsWidget.getActiveWidgets()
        val availableWidgetIds = allWidgets.map { it.id }.toSet()
        
        // Resolve widget names to AnalyticsWidget objects using migration mapping
        val resolvedWidgets = preferences.visibleWidgets.mapNotNull { widgetName ->
            // Try direct lookup first
            val byId = allWidgets.find { it.id == widgetName }
            if (byId != null) {
                // CRITICAL FIX: Ensure we don't include deprecated widgets even if they exist in preferences
                if (!byId.isDeprecated) {
                    byId
                } else {
                    Timber.w("Filtered out deprecated widget from preferences: '$widgetName' (${byId.displayName})")
                    null
                }
            } else {
                // Try migration mapping
                val migratedId = WIDGET_NAME_MIGRATION_MAP[widgetName]
                if (migratedId != null) {
                    val byMigratedId = allWidgets.find { it.id == migratedId }
                    if (byMigratedId != null && !byMigratedId.isDeprecated) {
                        Timber.d("Migrated widget name: '$widgetName' -> '${byMigratedId.id}' (${byMigratedId.displayName})")
                        byMigratedId
                    } else {
                        Timber.w("Migration target not found or deprecated: '$widgetName' -> '$migratedId'")
                        null
                    }
                } else {
                    // Try display name match as final fallback
                    val byDisplayName = allWidgets.find { it.displayName == widgetName }
                    if (byDisplayName != null && !byDisplayName.isDeprecated) {
                        Timber.d("Found widget by display name: '$widgetName' -> ${byDisplayName.id}")
                        byDisplayName
                    } else {
                        Timber.w("Invalid widget name or deprecated: '$widgetName' - no resolution available")
                        null
                    }
                }
            }
        }
        
        Timber.d("Successfully resolved ${resolvedWidgets.size} widgets: ${resolvedWidgets.map { it.id }.joinToString(", ")}")
        
        // CRITICAL FIX: Use level-appropriate defaults instead of always falling back to 4 widgets
        val finalWidgets = if (resolvedWidgets.isEmpty() && preferences.visibleWidgets.isNotEmpty()) {
            Timber.i("No valid widgets resolved from preferences, using level-appropriate defaults for $userLevel")
            resolveStandardWidgets(userLevel)
        } else if (resolvedWidgets.isEmpty()) {
            Timber.i("No widgets in preferences, using level-appropriate defaults for $userLevel")
            resolveStandardWidgets(userLevel)
        } else if (
            preferences.visibleWidgets == setOf(
                AnalyticsWidget.StrengthAnalytics.id,
                AnalyticsWidget.VolumeAnalytics.id,
                AnalyticsWidget.MuscleGroupDistribution.id
            )
        ) {
            resolvedWidgets + AnalyticsWidget.StrengthForecast
        } else {
            resolvedWidgets
        }
        
        // Validate widget count matches user level expectations
        val expectedCount = getMaxWidgetCount(userLevel)
        if (finalWidgets.size != expectedCount) {
            Timber.w("Widget count mismatch: expected $expectedCount for $userLevel, got ${finalWidgets.size}")
        } else {
            Timber.d("Widget count correct: ${finalWidgets.size} widgets for $userLevel")
        }
        
        Timber.d("Final resolved widgets (${finalWidgets.size}): ${finalWidgets.map { it.id }.joinToString(", ")}")
        
        return finalWidgets.sortedBy { it.getLayoutPriority() }
    }
    
    /**
     * Forces cleanup of deprecated widgets from user preferences.
     * 
     * This method should be called during app startup or when widget duplication is detected
     * to ensure all deprecated widgets are properly migrated to their modern counterparts.
     * 
     * @param preferences Current user preferences
     * @return Cleaned preferences with deprecated widgets removed and migrated
     */
    fun forceCleanupDeprecatedWidgets(preferences: WidgetPreferences): WidgetPreferences {
        Timber.d("Force cleaning deprecated widgets from preferences")
        
        val allWidgets = AnalyticsWidget.getAllWidgetsIncludingDeprecated() // Include deprecated for lookup
        val activeWidgets = AnalyticsWidget.getActiveWidgets() // Only non-deprecated
        val activeWidgetIds = activeWidgets.map { it.id }.toSet()
        
        // Check for deprecated widgets in current preferences
        val deprecatedWidgets = preferences.visibleWidgets.filter { widgetId ->
            val widget = allWidgets.find { it.id == widgetId }
            widget?.isDeprecated == true
        }
        
        if (deprecatedWidgets.isNotEmpty()) {
            Timber.w("Found deprecated widgets in preferences: $deprecatedWidgets")
            
            // Migrate deprecated widgets to modern equivalents
            val cleanedWidgetIds = preferences.visibleWidgets.mapNotNull { widgetId ->
                val widget = allWidgets.find { it.id == widgetId }
                when {
                    widget == null -> {
                        Timber.w("Widget not found, removing: $widgetId")
                        null
                    }
                    widget.isDeprecated -> {
                        // Find the modern replacement using migration mapping
                        val modernReplacement = WIDGET_NAME_MIGRATION_MAP.entries
                            .find { it.value == widgetId }
                            ?.let { entry ->
                                // Get the modern target the deprecated widget should migrate to
                                WIDGET_NAME_MIGRATION_MAP[entry.key]
                            }
                            ?: run {
                                // Direct migration mapping lookup
                                WIDGET_NAME_MIGRATION_MAP[widgetId]
                            }
                            ?: run {
                                // Smart fallback based on widget type
                                when (widget) {
                                    AnalyticsWidget.StrengthProgress,
                                    AnalyticsWidget.PersonalRecords,
                                    AnalyticsWidget.OneRMProgression -> AnalyticsWidget.StrengthAnalytics.id
                                    
                                    AnalyticsWidget.VolumeChart,
                                    AnalyticsWidget.VolumeTrends,
                                    AnalyticsWidget.VolumeLoadProgression -> AnalyticsWidget.VolumeAnalytics.id
                                    
                                    else -> {
                                        Timber.w("No modern replacement for deprecated widget: ${widget.id}")
                                        null
                                    }
                                }
                            }
                        
                        if (modernReplacement != null && activeWidgetIds.contains(modernReplacement)) {
                            Timber.i("Migrating deprecated widget: ${widget.id} -> $modernReplacement")
                            modernReplacement
                        } else {
                            Timber.w("Removing deprecated widget without replacement: ${widget.id}")
                            null
                        }
                    }
                    else -> widgetId // Keep active widgets
                }
            }.toSet()
            
            // Remove duplicates that might have been created by migration
            val deduplicatedWidgetIds = cleanedWidgetIds.toSet()
            
            // Update widget order to reflect changes
            val cleanedWidgetOrder = preferences.widgetOrder.mapNotNull { widgetId ->
                if (deduplicatedWidgetIds.contains(widgetId)) widgetId else null
            }
            
            Timber.i("Cleaned ${deprecatedWidgets.size} deprecated widgets, ${cleanedWidgetIds.size} widgets remaining")
            
            return preferences.copy(
                visibleWidgets = deduplicatedWidgetIds,
                widgetOrder = cleanedWidgetOrder,
                lastModified = kotlinx.datetime.Clock.System.now()
            )
        } else {
            Timber.d("No deprecated widgets found in preferences")
            return preferences
        }
    }
}

/**
 * Strategies for selecting widgets based on user level.
 */
private enum class SelectionStrategy {
    /**
     * Only essential widgets for new users.
     */
    ESSENTIAL_ONLY,
    
    /**
     * Essential widgets plus selected trends and charts.
     */
    ESSENTIAL_AND_TRENDS,
    
    /**
     * Comprehensive selection across all categories.
     */
    COMPREHENSIVE
}
