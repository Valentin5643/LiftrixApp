package com.example.liftrix.domain.model.analytics

import kotlinx.serialization.Serializable

/**
 * Comprehensive analytics widget system for dynamic dashboard customization
 * 
 * Provides type-safe widget definitions with 25+ analytics widgets across 4 categories.
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
    val priority: WidgetPriority = WidgetPriority.STANDARD
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
     * Consistency streak widget
     * Shows current and longest workout streaks
     */
    @Serializable
    data object ConsistencyStreak : AnalyticsWidget(
        id = "consistency_streak",
        displayName = "Consistency Streak",
        description = "Monitor your workout consistency streaks",
        category = WidgetCategory.METRICS,
        complexity = WidgetComplexity.SIMPLE,
        priority = WidgetPriority.FIXED_BEGINNER
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
     * Duration chart widget
     * Visual chart displaying workout duration trends
     */
    @Serializable
    data object DurationChart : AnalyticsWidget(
        id = "duration_chart",
        displayName = "Duration Chart",
        description = "Visual chart showing workout duration trends",
        category = WidgetCategory.CHARTS,
        complexity = WidgetComplexity.MODERATE
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
     * Performance analysis widget
     * Comprehensive performance analytics with detailed insights
     */
    @Serializable
    data object PerformanceAnalysis : AnalyticsWidget(
        id = "performance_analysis",
        displayName = "Performance Analysis",
        description = "Detailed performance insights and recommendations",
        category = WidgetCategory.ANALYTICS,
        complexity = WidgetComplexity.COMPLEX,
        priority = WidgetPriority.ADVANCED
    )
    
    /**
     * Calories burned widget
     * Shows daily calories burned from workouts with trend visualization
     */
    @Serializable
    data object CaloriesBurned : AnalyticsWidget(
        id = "calories_burned",
        displayName = "Calories Burned",
        description = "Track daily calories burned from your workouts",
        category = WidgetCategory.METRICS,
        complexity = WidgetComplexity.SIMPLE,
        priority = WidgetPriority.FIXED_BEGINNER
    )
    
    /**
     * Daily calories widget
     * Displays today's calorie burn with goal comparison
     */
    @Serializable
    data object DailyCalories : AnalyticsWidget(
        id = "daily_calories",
        displayName = "Today's Calories",
        description = "View calories burned today compared to your goals",
        category = WidgetCategory.METRICS,
        complexity = WidgetComplexity.SIMPLE,
        priority = WidgetPriority.ESSENTIAL
    )
    
    /**
     * Weekly calorie trend widget
     * Weekly calorie burn trends with pattern analysis
     */
    @Serializable
    data object WeeklyCalorieTrend : AnalyticsWidget(
        id = "weekly_calorie_trend",
        displayName = "Weekly Calorie Trend",
        description = "Analyze your weekly calorie burn patterns and trends",
        category = WidgetCategory.CHARTS,
        complexity = WidgetComplexity.MODERATE
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
     * Weekly trends analysis widget
     * Advanced weekly trend analysis with pattern recognition
     */
    @Serializable
    data object WeeklyTrends : AnalyticsWidget(
        id = "weekly_trends",
        displayName = "Weekly Trends",
        description = "Analyze weekly workout trends and patterns",
        category = WidgetCategory.ANALYTICS,
        complexity = WidgetComplexity.COMPLEX
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
     * Recovery patterns widget
     * Tracks recovery patterns and recommendations
     */
    @Serializable
    data object RecoveryPatterns : AnalyticsWidget(
        id = "recovery_patterns",
        displayName = "Recovery Patterns",
        description = "Monitor recovery patterns and recommendations",
        category = WidgetCategory.ANALYTICS,
        complexity = WidgetComplexity.COMPLEX
    )
    
    // Additional widgets to reach 25+ target
    
    /**
     * Training intensity widget
     * Monitors workout intensity levels and RPE trends
     */
    @Serializable
    data object TrainingIntensity : AnalyticsWidget(
        id = "training_intensity",
        displayName = "Training Intensity",
        description = "Monitor workout intensity levels and RPE trends",
        category = WidgetCategory.METRICS,
        complexity = WidgetComplexity.MODERATE
    )
    
    /**
     * Exercise variety widget
     * Tracks exercise diversity and movement patterns
     */
    @Serializable
    data object ExerciseVariety : AnalyticsWidget(
        id = "exercise_variety",
        displayName = "Exercise Variety",
        description = "Track exercise diversity and movement patterns",
        category = WidgetCategory.ANALYTICS,
        complexity = WidgetComplexity.MODERATE
    )
    
    /**
     * Time of day analysis widget
     * Analyzes optimal workout timing based on performance
     */
    @Serializable
    data object TimeOfDayAnalysis : AnalyticsWidget(
        id = "time_of_day_analysis",
        displayName = "Optimal Timing",
        description = "Analyze optimal workout timing based on performance",
        category = WidgetCategory.ANALYTICS,
        complexity = WidgetComplexity.COMPLEX
    )
    
    /**
     * Set completion rate widget
     * Tracks completion rates across different exercises
     */
    @Serializable
    data object SetCompletionRate : AnalyticsWidget(
        id = "set_completion_rate",
        displayName = "Set Completion Rate",
        description = "Track completion rates across different exercises",
        category = WidgetCategory.METRICS,
        complexity = WidgetComplexity.SIMPLE
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
     * Goal achievement widget
     * Tracks progress toward user-defined fitness goals
     */
    @Serializable
    data object GoalAchievement : AnalyticsWidget(
        id = "goal_achievement",
        displayName = "Goal Achievement",
        description = "Track progress toward user-defined fitness goals",
        category = WidgetCategory.PROGRESS,
        complexity = WidgetComplexity.MODERATE
    )
    
    /**
     * Gets the widget priority for layout ordering (lower = higher priority)
     */
    fun getLayoutPriority(): Int = when (this) {
        WorkoutFrequency -> 1
        TotalVolume -> 2
        ConsistencyStreak -> 3
        CaloriesBurned -> 1
        DailyCalories -> 2
        AverageDuration -> 4
        SetCompletionRate -> 3
        WorkoutStreak -> 3
        
        VolumeChart -> 5
        DurationChart -> 6
        FrequencyChart -> 7
        VolumeCalendar -> 5
        ProgressChart -> 5
        WeeklyCalorieTrend -> 6
        
        StrengthProgress -> 4
        PersonalRecords -> 4
        VolumeLoadProgression -> 6
        OneRMProgression -> 7
        GoalAchievement -> 5
        MonthlySummary -> 8
        
        VolumeTrends -> 8
        RecoveryMetrics -> 9
        PerformanceAnalysis -> 10
        WeeklyTrends -> 8
        MuscleGroupDistribution -> 9
        RecoveryPatterns -> 10
        TrainingIntensity -> 7
        ExerciseVariety -> 8
        TimeOfDayAnalysis -> 10
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
         * All available analytics widgets
         */
        fun getAllWidgets(): List<AnalyticsWidget> = listOf(
            // METRICS Category (6 widgets)
            WorkoutFrequency, TotalVolume, ConsistencyStreak, CaloriesBurned, 
            DailyCalories, AverageDuration, WorkoutStreak, SetCompletionRate,
            
            // CHARTS Category (5 widgets)
            VolumeChart, DurationChart, FrequencyChart, VolumeCalendar, 
            ProgressChart, WeeklyCalorieTrend,
            
            // PROGRESS Category (4 widgets)
            StrengthProgress, PersonalRecords, VolumeLoadProgression, 
            OneRMProgression, GoalAchievement, MonthlySummary,
            
            // ANALYTICS Category (6 widgets)
            VolumeTrends, RecoveryMetrics, PerformanceAnalysis, WeeklyTrends,
            MuscleGroupDistribution, RecoveryPatterns, TrainingIntensity,
            ExerciseVariety, TimeOfDayAnalysis
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
                "CaloriesBurned" to "calories_burned",
                "ConsistencyStreak" to "consistency_streak",
                "ProgressChart" to "progress_chart",
                "VolumeCalendar" to "volume_calendar",
                "StrengthProgress" to "strength_progress",
                "PersonalRecords" to "personal_records",
                "VolumeTrends" to "volume_trends",
                "RecoveryMetrics" to "recovery_metrics",
                "PerformanceAnalysis" to "performance_analysis",
                "DailyCalories" to "daily_calories",
                "WeeklyCalorieTrend" to "weekly_calorie_trend",
                "AverageDuration" to "average_duration",
                "VolumeLoadProgression" to "volume_load_progression",
                "OneRMProgression" to "one_rm_progression",
                "WeeklyTrends" to "weekly_trends",
                "MuscleGroupDistribution" to "muscle_group_distribution",
                "RecoveryPatterns" to "recovery_patterns",
                "TrainingIntensity" to "training_intensity",
                "ExerciseVariety" to "exercise_variety",
                "TimeOfDayAnalysis" to "time_of_day_analysis",
                "SetCompletionRate" to "set_completion_rate",
                "MonthlySummary" to "monthly_summary",
                "GoalAchievement" to "goal_achievement",
                "VolumeChart" to "volume_chart",
                "DurationChart" to "duration_chart",
                "FrequencyChart" to "frequency_chart",
                "WorkoutStreak" to "workout_streak",
                
                // Display name mappings
                "Workout Frequency" to "workout_frequency",
                "Total Volume" to "total_volume",
                "Calories Burned" to "calories_burned",
                "Consistency Streak" to "consistency_streak",
                "Progress Chart" to "progress_chart",
                "Volume Calendar" to "volume_calendar",
                "Strength Progress" to "strength_progress",
                "Personal Records" to "personal_records",
                "Volume Trends" to "volume_trends",
                "Recovery Metrics" to "recovery_metrics",
                "Performance Analysis" to "performance_analysis",
                "Today's Calories" to "daily_calories",
                "Daily Calories" to "daily_calories",
                "Weekly Calorie Trend" to "weekly_calorie_trend",
                "Average Duration" to "average_duration",
                "Volume Progression" to "volume_load_progression",
                "1RM Progression" to "one_rm_progression",
                "Weekly Trends" to "weekly_trends",
                "Muscle Group Distribution" to "muscle_group_distribution",
                "Recovery Patterns" to "recovery_patterns",
                "Training Intensity" to "training_intensity",
                "Exercise Variety" to "exercise_variety",
                "Optimal Timing" to "time_of_day_analysis",
                "Set Completion Rate" to "set_completion_rate",
                "Monthly Summary" to "monthly_summary",
                "Goal Achievement" to "goal_achievement",
                "Volume Chart" to "volume_chart",
                "Duration Chart" to "duration_chart",
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