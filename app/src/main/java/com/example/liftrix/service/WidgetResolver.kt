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
        // Legacy camelCase names → correct IDs
        "workoutFrequency" to AnalyticsWidget.WorkoutFrequency.id,
        "totalVolume" to AnalyticsWidget.TotalVolume.id,
        "consistencyStreak" to AnalyticsWidget.ConsistencyStreak.id,
        "caloriesBurned" to AnalyticsWidget.CaloriesBurned.id,
        "averageDuration" to AnalyticsWidget.AverageDuration.id,
        "dailyCalories" to AnalyticsWidget.DailyCalories.id,
        "progressChart" to AnalyticsWidget.ProgressChart.id,
        "volumeLoadProgression" to AnalyticsWidget.VolumeLoadProgression.id,
        "oneRMProgression" to AnalyticsWidget.OneRMProgression.id,
        "weeklyCalorieTrend" to AnalyticsWidget.WeeklyCalorieTrend.id,
        "volumeChart" to AnalyticsWidget.VolumeChart.id,
        "durationChart" to AnalyticsWidget.DurationChart.id,
        "frequencyChart" to AnalyticsWidget.FrequencyChart.id,
        "volumeCalendar" to AnalyticsWidget.VolumeCalendar.id,
        "strengthProgress" to AnalyticsWidget.StrengthProgress.id,
        "personalRecords" to AnalyticsWidget.PersonalRecords.id,
        "workoutStreak" to AnalyticsWidget.WorkoutStreak.id,
        "volumeTrends" to AnalyticsWidget.VolumeTrends.id,
        "recoveryMetrics" to AnalyticsWidget.RecoveryMetrics.id,
        "performanceAnalysis" to AnalyticsWidget.PerformanceAnalysis.id,
        "weeklyTrends" to AnalyticsWidget.WeeklyTrends.id,
        "muscleGroupDistribution" to AnalyticsWidget.MuscleGroupDistribution.id,
        "recoveryPatterns" to AnalyticsWidget.RecoveryPatterns.id,
        "trainingIntensity" to AnalyticsWidget.TrainingIntensity.id,
        "exerciseVariety" to AnalyticsWidget.ExerciseVariety.id,
        "timeOfDayAnalysis" to AnalyticsWidget.TimeOfDayAnalysis.id,
        "setCompletionRate" to AnalyticsWidget.SetCompletionRate.id,
        "monthlySummary" to AnalyticsWidget.MonthlySummary.id,
        "goalAchievement" to AnalyticsWidget.GoalAchievement.id,
        
        // Incorrect names used in forceAdvancedUserLevel() → correct IDs
        "WorkoutFrequency" to AnalyticsWidget.WorkoutFrequency.id,
        "TotalVolume" to AnalyticsWidget.TotalVolume.id,
        "AverageDuration" to AnalyticsWidget.AverageDuration.id,
        "ConsistencyStreak" to AnalyticsWidget.ConsistencyStreak.id,
        "VolumeLoadProgression" to AnalyticsWidget.VolumeLoadProgression.id,
        "OneRMProgression" to AnalyticsWidget.OneRMProgression.id,
        "ProgressChart" to AnalyticsWidget.ProgressChart.id,
        "WorkoutStreak" to AnalyticsWidget.WorkoutStreak.id,
        "DailyCalories" to AnalyticsWidget.DailyCalories.id,
        "VolumeCalendar" to AnalyticsWidget.VolumeCalendar.id,
        "StrengthProgress" to AnalyticsWidget.StrengthProgress.id,
        "VolumeChart" to AnalyticsWidget.VolumeChart.id,
        "DurationChart" to AnalyticsWidget.DurationChart.id,
        "FrequencyChart" to AnalyticsWidget.FrequencyChart.id,
        "PersonalRecords" to AnalyticsWidget.PersonalRecords.id,
        "WeeklyCalorieTrend" to AnalyticsWidget.WeeklyCalorieTrend.id,
        "MuscleGroupDistribution" to AnalyticsWidget.MuscleGroupDistribution.id,
        "VolumeTrends" to AnalyticsWidget.VolumeTrends.id,
        "RecoveryMetrics" to AnalyticsWidget.RecoveryMetrics.id,
        "PerformanceAnalysis" to AnalyticsWidget.PerformanceAnalysis.id,
        "WeeklyTrends" to AnalyticsWidget.WeeklyTrends.id,
        "RecoveryPatterns" to AnalyticsWidget.RecoveryPatterns.id,
        
        // Display name variations → correct IDs
        "Workout Frequency" to AnalyticsWidget.WorkoutFrequency.id,
        "Total Volume" to AnalyticsWidget.TotalVolume.id,
        "Consistency Streak" to AnalyticsWidget.ConsistencyStreak.id,
        "Calories Burned" to AnalyticsWidget.CaloriesBurned.id,
        "Average Duration" to AnalyticsWidget.AverageDuration.id,
        "Today's Calories" to AnalyticsWidget.DailyCalories.id,
        "Progress Chart" to AnalyticsWidget.ProgressChart.id,
        "Volume Progression" to AnalyticsWidget.VolumeLoadProgression.id,
        "1RM Progression" to AnalyticsWidget.OneRMProgression.id,
        "Weekly Calorie Trend" to AnalyticsWidget.WeeklyCalorieTrend.id
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
        layoutMode: DashboardLayoutMode = DashboardLayoutMode.SECTIONS,
        preferences: WidgetPreferences? = null
    ): List<AnalyticsWidget> {
        return when (layoutMode) {
            DashboardLayoutMode.CUSTOM -> {
                resolveCustomWidgets(preferences, userLevel)
            }
            else -> {
                resolveStandardWidgets(userLevel)
            }
        }
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
    private fun resolveStandardWidgets(userLevel: UserLevel): List<AnalyticsWidget> {
        val allWidgets = AnalyticsWidget.getAllWidgets()
        
        val selectedWidgets = when (userLevel) {
            UserLevel.BEGINNER -> {
                // 4 essential widgets for building workout habits
                selectWidgetsByStrategy(
                    widgets = allWidgets,
                    maxCount = 4,
                    strategy = SelectionStrategy.ESSENTIAL_ONLY
                )
            }
            UserLevel.INTERMEDIATE -> {
                // 7 widgets: essential + some trend analysis
                selectWidgetsByStrategy(
                    widgets = allWidgets,
                    maxCount = 7,
                    strategy = SelectionStrategy.ESSENTIAL_AND_TRENDS
                )
            }
            UserLevel.ADVANCED -> {
                // 10 widgets: comprehensive analytics
                selectWidgetsByStrategy(
                    widgets = allWidgets,
                    maxCount = 10,
                    strategy = SelectionStrategy.COMPREHENSIVE
                )
            }
        }
        
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
        
        val allWidgets = AnalyticsWidget.getAllWidgets()
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
            UserLevel.ADVANCED -> 10
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
        val allWidgets = AnalyticsWidget.getAllWidgets()
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
        layoutMode: DashboardLayoutMode = DashboardLayoutMode.SECTIONS
    ): WidgetPreferences {
        val defaultWidgets = resolveStandardWidgets(userLevel)
        val widgetIds = defaultWidgets.map { it.id }.toSet()
        val widgetOrder = defaultWidgets.map { it.id }
        
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