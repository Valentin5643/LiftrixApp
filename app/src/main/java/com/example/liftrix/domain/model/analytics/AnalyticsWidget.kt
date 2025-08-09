package com.example.liftrix.domain.model.analytics

import kotlinx.serialization.Serializable

/**
 * Comprehensive analytics widget system for dynamic dashboard customization
 * 
 * Provides type-safe widget definitions with exactly 15 analytics widgets across 4 categories.
 * Uses sealed class approach for extensibility while maintaining enum-like exhaustiveness.
 * Each widget includes complexity-based refresh rates, category organization, and metadata
 * for optimal dashboard performance and user experience.
 * 
 * Features:
 * - Type-safe widget identification with unique IDs
 * - Complexity-based refresh rate optimization  
 * - Category-based dashboard organization
 * - Metadata for UI customization and performance tuning
 * - Extensible architecture for future widget additions
 */
@Serializable
sealed class AnalyticsWidget(
    val id: String,
    val displayName: String,
    val description: String,
    val category: WidgetCategory,
    val complexity: WidgetComplexity,
    val defaultEnabled: Boolean = true,
    val priority: WidgetPriority = WidgetPriority.STANDARD,
    val isDeprecated: Boolean = false
) {
    /**
     * Basic workout frequency tracking widget
     * Shows workouts per week/month with simple visualization
     */
    @Serializable
    data object WorkoutFrequency : AnalyticsWidget(
        id = "workout_frequency",
        displayName = "Workout Frequency",
        description = "Track your workout consistency over time",
        category = WidgetCategory.METRICS,
        complexity = WidgetComplexity.SIMPLE,
        priority = WidgetPriority.FIXED_BEGINNER
    )
    
    /**
     * Total volume tracking widget
     * Displays cumulative volume lifted with trend analysis
     */
    @Serializable
    data object TotalVolume : AnalyticsWidget(
        id = "total_volume",
        displayName = "Total Volume",
        description = "Monitor your total weight lifted progress",
        category = WidgetCategory.METRICS,
        complexity = WidgetComplexity.SIMPLE,
        priority = WidgetPriority.FIXED_BEGINNER
    )
    
    /**
     * Volume calendar widget
     * Monthly calendar view with daily volume color coding
     */
    @Serializable
    data object VolumeCalendar : AnalyticsWidget(
        id = "volume_calendar",
        displayName = "Volume Calendar",
        description = "Visual calendar showing daily workout volume",
        category = WidgetCategory.CHARTS,
        complexity = WidgetComplexity.MODERATE,
        priority = WidgetPriority.STANDARD
    )
    
    /**
     * Strength progression widget
     * Tracks personal records and strength gains over time
     */
    @Serializable
    data object StrengthProgress : AnalyticsWidget(
        id = "strength_progress",
        displayName = "Strength Progress",
        description = "Track personal records and strength improvements",
        category = WidgetCategory.PROGRESS,
        complexity = WidgetComplexity.MODERATE,
        priority = WidgetPriority.STANDARD
    )
    
    
    /**
     * Volume chart widget
     * Visual chart displaying volume progression over time
     */
    @Serializable
    data object VolumeChart : AnalyticsWidget(
        id = "volume_chart",
        displayName = "Volume Chart",
        description = "Visual chart showing volume progression over time",
        category = WidgetCategory.CHARTS,
        complexity = WidgetComplexity.MODERATE,
        priority = WidgetPriority.STANDARD
    )
    
    /**
     * Frequency chart widget
     * Visual chart displaying workout frequency patterns
     */
    @Serializable
    data object FrequencyChart : AnalyticsWidget(
        id = "frequency_chart",
        displayName = "Frequency Chart",
        description = "Visual chart showing workout frequency patterns",
        category = WidgetCategory.CHARTS,
        complexity = WidgetComplexity.MODERATE
    )
    
    /**
     * Workout streak widget
     * Tracks current and historical workout streaks
     */
    @Serializable
    data object WorkoutStreak : AnalyticsWidget(
        id = "workout_streak",
        displayName = "Workout Streak",
        description = "Track your current and historical workout streaks",
        category = WidgetCategory.METRICS,
        complexity = WidgetComplexity.SIMPLE,
        priority = WidgetPriority.ESSENTIAL
    )
    
    /**
     * Personal records widget
     * Displays recent and all-time personal records
     */
    @Serializable
    data object PersonalRecords : AnalyticsWidget(
        id = "personal_records",
        displayName = "Personal Records",
        description = "View your recent and all-time personal records",
        category = WidgetCategory.PROGRESS,
        complexity = WidgetComplexity.MODERATE
    )
    
    /**
     * Volume trends analysis widget
     * Advanced trend analysis with projections
     */
    @Serializable
    data object VolumeTrends : AnalyticsWidget(
        id = "volume_trends",
        displayName = "Volume Trends",
        description = "Advanced volume trend analysis with projections",
        category = WidgetCategory.ANALYTICS,
        complexity = WidgetComplexity.COMPLEX
    )
    
    /**
     * Recovery metrics widget
     * Tracks rest days, recovery patterns, and recommendations
     */
    @Serializable
    data object RecoveryMetrics : AnalyticsWidget(
        id = "recovery_metrics",
        displayName = "Recovery Metrics",
        description = "Monitor rest patterns and recovery recommendations",
        category = WidgetCategory.ANALYTICS,
        complexity = WidgetComplexity.COMPLEX,
        priority = WidgetPriority.ADVANCED
    )
    
    
    /**
     * Average duration widget
     * Tracks average workout duration with trends
     */
    @Serializable
    data object AverageDuration : AnalyticsWidget(
        id = "average_duration",
        displayName = "Average Duration",
        description = "Track average workout duration with trends",
        category = WidgetCategory.METRICS,
        complexity = WidgetComplexity.SIMPLE,
        priority = WidgetPriority.ESSENTIAL
    )
    
    /**
     * Volume load progression widget
     * Advanced volume progression tracking with load analysis
     */
    @Serializable
    data object VolumeLoadProgression : AnalyticsWidget(
        id = "volume_load_progression",
        displayName = "Volume Progression",
        description = "Track volume progression with intensity load analysis",
        category = WidgetCategory.PROGRESS,
        complexity = WidgetComplexity.MODERATE
    )
    
    /**
     * Progress chart widget
     * Comprehensive progress visualization chart
     */
    @Serializable
    data object ProgressChart : AnalyticsWidget(
        id = "progress_chart",
        displayName = "Progress Chart",
        description = "Comprehensive progress visualization chart",
        category = WidgetCategory.CHARTS,
        complexity = WidgetComplexity.MODERATE
    )
    
    /**
     * One rep max progression widget
     * Track estimated and actual one rep max progression
     */
    @Serializable
    data object OneRMProgression : AnalyticsWidget(
        id = "one_rm_progression",
        displayName = "1RM Progression",
        description = "Track estimated and actual one rep max progression",
        category = WidgetCategory.PROGRESS,
        complexity = WidgetComplexity.MODERATE
    )
    
    /**
     * Muscle group distribution widget
     * Shows distribution of training across muscle groups
     */
    @Serializable
    data object MuscleGroupDistribution : AnalyticsWidget(
        id = "muscle_group_distribution",
        displayName = "Muscle Group Distribution",
        description = "View training distribution across muscle groups",
        category = WidgetCategory.ANALYTICS,
        complexity = WidgetComplexity.MODERATE
    )
    
    /**
     * Monthly summary widget
     * Comprehensive monthly performance overview
     */
    @Serializable
    data object MonthlySummary : AnalyticsWidget(
        id = "monthly_summary",
        displayName = "Monthly Summary",
        description = "Comprehensive monthly performance overview",
        category = WidgetCategory.PROGRESS,
        complexity = WidgetComplexity.MODERATE
    )
    
    /**
     * Gets the widget priority for layout ordering (lower = higher priority)
     */
    fun getLayoutPriority(): Int = when (this) {
        WorkoutFrequency -> 1
        TotalVolume -> 2
        WorkoutStreak -> 3
        AverageDuration -> 4
        
        VolumeChart -> 5
        FrequencyChart -> 6
        VolumeCalendar -> 7
        ProgressChart -> 8
        
        StrengthProgress -> 9
        PersonalRecords -> 10
        VolumeLoadProgression -> 11
        OneRMProgression -> 12
        MonthlySummary -> 13
        
        VolumeTrends -> 14
        RecoveryMetrics -> 15
        MuscleGroupDistribution -> 16
    }
    
    /**
     * Checks if this widget is suitable for real-time updates
     */
    fun isRealTimeCompatible(): Boolean = complexity == WidgetComplexity.SIMPLE
    
    /**
     * Gets the refresh interval based on complexity
     */
    fun getRefreshIntervalMinutes(): Int = complexity.defaultRefreshIntervalMinutes
    
    /**
     * Gets the widget data type for type-safe data handling
     */
    fun getDataType(): WidgetDataType = when (category) {
        WidgetCategory.METRICS -> WidgetDataType.METRIC
        WidgetCategory.CHARTS -> WidgetDataType.CHART
        WidgetCategory.PROGRESS -> WidgetDataType.PROGRESS
        WidgetCategory.ANALYTICS -> WidgetDataType.ANALYTICS
    }
    
    /**
     * Gets the minimum data points required for this widget to display
     */
    fun getMinimumDataPoints(): Int = complexity.getBatchSize() / 10
    
    /**
     * Gets the recommended widget size for UI layout
     */
    fun getRecommendedSize(): WidgetSize = when (complexity) {
        WidgetComplexity.SIMPLE -> WidgetSize.SMALL
        WidgetComplexity.MODERATE -> WidgetSize.MEDIUM
        WidgetComplexity.COMPLEX -> WidgetSize.LARGE
    }
    
    /**
     * Checks if this widget should be enabled by default for user level
     */
    fun isDefaultEnabledForLevel(userLevel: UserLevel): Boolean {
        return defaultEnabled && category.getRecommendedUserLevel() <= userLevel
    }
    
    /**
     * Gets estimated memory usage for this widget
     */
    fun getEstimatedMemoryUsageMB(): Float = complexity.getEstimatedMemoryUsageMB()
    
    companion object {
        /**
         * All available analytics widgets - exactly 15 widgets per specification
         */
        fun getAllWidgets(): List<AnalyticsWidget> = listOf(
            // METRICS Category (4 widgets)
            WorkoutFrequency, TotalVolume, WorkoutStreak, AverageDuration,
            
            // CHARTS Category (4 widgets)
            VolumeChart, FrequencyChart, VolumeCalendar, ProgressChart,
            
            // PROGRESS Category (5 widgets)
            StrengthProgress, PersonalRecords, VolumeLoadProgression, 
            OneRMProgression, MonthlySummary,
            
            // ANALYTICS Category (3 widgets)
            VolumeTrends, RecoveryMetrics, MuscleGroupDistribution
        )
        
        /**
         * Gets widgets by category
         */
        fun getByCategory(category: WidgetCategory): List<AnalyticsWidget> = 
            getAllWidgets().filter { it.category == category }
        
        /**
         * Gets widgets suitable for a specific complexity level
         */
        fun getByComplexity(complexity: WidgetComplexity): List<AnalyticsWidget> = 
            getAllWidgets().filter { it.complexity == complexity }
        
        /**
         * Gets widgets that support real-time updates
         */
        fun getRealTimeWidgets(): List<AnalyticsWidget> = 
            getAllWidgets().filter { it.isRealTimeCompatible() }
        
        /**
         * Gets widgets ordered by layout priority
         */
        fun getByPriority(): List<AnalyticsWidget> = 
            getAllWidgets().sortedBy { it.getLayoutPriority() }
        
        /**
         * Gets widgets suitable for a specific user level
         */
        fun getForUserLevel(userLevel: UserLevel): List<AnalyticsWidget> {
            return getAllWidgets().filter { widget ->
                widget.isDefaultEnabledForLevel(userLevel)
            }
        }
        
        /**
         * Gets widget by unique ID with legacy format support
         * 
         * Supports lookup by:
         * - Canonical snake_case IDs (e.g., "workout_frequency")
         * - Legacy PascalCase IDs (e.g., "WorkoutFrequency")
         * - Display names (e.g., "Workout Frequency")
         * 
         * @param id Widget identifier in any supported format
         * @return AnalyticsWidget instance or null if not found
         */
        fun getById(id: String): AnalyticsWidget? {
            val allWidgets = getAllWidgets()
            
            // First try exact match with canonical ID
            allWidgets.find { it.id == id }?.let { return it }
            
            // Try legacy format mapping
            val legacyMappings = mapOf(
                // PascalCase legacy IDs to canonical snake_case
                "WorkoutFrequency" to "workout_frequency",
                "TotalVolume" to "total_volume",
                "ProgressChart" to "progress_chart",
                "VolumeCalendar" to "volume_calendar",
                "StrengthProgress" to "strength_progress",
                "PersonalRecords" to "personal_records",
                "VolumeTrends" to "volume_trends",
                "RecoveryMetrics" to "recovery_metrics",
                "AverageDuration" to "average_duration",
                "VolumeLoadProgression" to "volume_load_progression",
                "OneRMProgression" to "one_rm_progression",
                "MuscleGroupDistribution" to "muscle_group_distribution",
                "MonthlySummary" to "monthly_summary",
                "VolumeChart" to "volume_chart",
                "FrequencyChart" to "frequency_chart",
                "WorkoutStreak" to "workout_streak",
                
                // Display name mappings
                "Workout Frequency" to "workout_frequency",
                "Total Volume" to "total_volume",
                "Progress Chart" to "progress_chart",
                "Volume Calendar" to "volume_calendar",
                "Strength Progress" to "strength_progress",
                "Personal Records" to "personal_records",
                "Volume Trends" to "volume_trends",
                "Recovery Metrics" to "recovery_metrics",
                "Average Duration" to "average_duration",
                "Volume Progression" to "volume_load_progression",
                "1RM Progression" to "one_rm_progression",
                "Muscle Group Distribution" to "muscle_group_distribution",
                "Monthly Summary" to "monthly_summary",
                "Volume Chart" to "volume_chart",
                "Frequency Chart" to "frequency_chart",
                "Workout Streak" to "workout_streak"
            )
            
            // Try legacy mapping lookup
            legacyMappings[id]?.let { canonicalId ->
                return allWidgets.find { it.id == canonicalId }
            }
            
            // Fallback: try case-insensitive display name match
            allWidgets.find { it.displayName.equals(id, ignoreCase = true) }?.let { return it }
            
            return null
        }
        
        /**
         * Gets recommended widget configuration for user level
         */
        fun getRecommendedConfiguration(userLevel: UserLevel): List<AnalyticsWidget> {
            val availableCategories = WidgetCategory.getForUserLevel(userLevel)
            return availableCategories.flatMap { category ->
                getByCategory(category).take(category.getDefaultMaxWidgets())
            }.sortedBy { it.getLayoutPriority() }
        }
        
        /**
         * Validates widget selection for performance
         */
        fun validateSelection(widgets: List<AnalyticsWidget>): SelectionValidationResult {
            val complexityCounts = widgets.groupingBy { it.complexity }.eachCount()
            val totalMemory = WidgetComplexity.calculateTotalMemoryUsage(
                complexityCounts.mapKeys { it.key }.mapValues { it.value }
            )
            
            return when {
                widgets.size > 10 -> SelectionValidationResult.TOO_MANY_WIDGETS
                totalMemory > 50.0f -> SelectionValidationResult.MEMORY_LIMIT_EXCEEDED
                (complexityCounts[WidgetComplexity.COMPLEX] ?: 0) > 2 -> 
                    SelectionValidationResult.TOO_MANY_COMPLEX_WIDGETS
                else -> SelectionValidationResult.VALID
            }
        }
        
        /**
         * Deprecated widget IDs that have been removed from the system.
         * 
         * These widgets were removed during the analytics system modernization
         * to focus on strength-based metrics and reduce complexity.
         */
        val DEPRECATED_WIDGET_IDS: Set<String> = setOf(
            "calories_burned",           // Deprecated in favor of external nutrition apps
            "daily_calories",            // Deprecated - insufficient value for strength focus
            "weekly_calorie_trend",      // Deprecated - advanced feature with low usage
            "consistency_streak",        // Deprecated - subjective metric removed
            "duration_chart",            // Deprecated - merged into other metrics
            "set_completion_rate",       // Deprecated - complex visualization with low usage
            "exercise_variety",          // Deprecated - replaced by monthly_summary
            "training_intensity",        // Deprecated - merged into workout_streak
            "goal_achievement",          // Deprecated - replaced by muscle_group_distribution
            "weekly_trends",             // Deprecated - advanced metric with low adoption
            "time_of_day_analysis",      // Deprecated - advanced feature with low usage
            "recovery_patterns",         // Deprecated - advanced metric with low adoption
            "performance_analysis"       // Deprecated - advanced metric with low adoption
        )

        /**
         * Gets only active widgets - all 15 widgets are active (no deprecated ones)
         */
        fun getActiveWidgets(): List<AnalyticsWidget> {
            return getAllWidgets().filterNot { it.isDeprecated }
        }
        
        /**
         * Gets total widget count
         */
        fun getTotalCount(): Int = getAllWidgets().size
        
        /**
         * Gets category distribution
         */
        fun getCategoryDistribution(): Map<WidgetCategory, Int> {
            return getAllWidgets().groupingBy { it.category }.eachCount()
        }
    }
}

/**
 * Recommended widget sizes for UI layout
 */
enum class WidgetSize(val displayName: String, val aspectRatio: Float) {
    SMALL("Small", 1.0f),      // 1:1 square
    MEDIUM("Medium", 1.5f),    // 3:2 rectangle
    LARGE("Large", 2.0f)       // 2:1 wide rectangle
}

/**
 * Widget priority levels for dashboard configuration
 */
enum class WidgetPriority(val configurationLevel: Int) {
    FIXED_BEGINNER(0),  // Fixed widgets that appear for all levels, not configurable for beginners
    ESSENTIAL(1),       // Configurable widgets for intermediate/advanced 
    STANDARD(2),        // Additional configurable widgets
    ADVANCED(3)         // Advanced configurable widgets
}

/**
 * Widget data type enumeration for type-safe data handling
 */
enum class WidgetDataType {
    METRIC,
    CHART,
    PROGRESS,
    ANALYTICS
}

/**
 * Widget selection validation results
 */
enum class SelectionValidationResult(val message: String, val isValid: Boolean) {
    VALID("Widget selection is valid", true),
    TOO_MANY_WIDGETS("Maximum 10 widgets allowed for optimal performance", false),
    TOO_MANY_COMPLEX_WIDGETS("Maximum 2 complex widgets allowed", false),
    MEMORY_LIMIT_EXCEEDED("Widget selection exceeds memory limits", false)
}