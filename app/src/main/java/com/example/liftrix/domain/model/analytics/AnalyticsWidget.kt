package com.example.liftrix.domain.model.analytics

/**
 * Enumeration of available analytics widgets for dashboard display
 * 
 * Defines all available analytics widgets that can be displayed on the progress dashboard.
 * Each widget represents a specific type of analytics visualization or metric display
 * with standardized configuration and data requirements.
 * 
 * Widgets are categorized by complexity and target user experience level to ensure
 * appropriate dashboard configurations for different user types.
 */
enum class AnalyticsWidget(
    val displayName: String,
    val description: String,
    val complexity: WidgetComplexity,
    val updateFrequencyMinutes: Int,
    val category: WidgetCategory = WidgetCategory.METRICS,
    val priority: WidgetPriority = WidgetPriority.STANDARD
) {
    /**
     * Basic workout frequency tracking widget
     * Shows workouts per week/month with simple visualization
     */
    WORKOUT_FREQUENCY(
        displayName = "Workout Frequency",
        description = "Track your workout consistency over time",
        complexity = WidgetComplexity.SIMPLE,
        updateFrequencyMinutes = 60
    ),
    
    /**
     * Total volume tracking widget
     * Displays cumulative volume lifted with trend analysis
     */
    TOTAL_VOLUME(
        displayName = "Total Volume",
        description = "Monitor your total weight lifted progress",
        complexity = WidgetComplexity.SIMPLE,
        updateFrequencyMinutes = 30
    ),
    
    /**
     * Volume calendar widget
     * Monthly calendar view with daily volume color coding
     */
    VOLUME_CALENDAR(
        displayName = "Volume Calendar",
        description = "Visual calendar showing daily workout volume",
        complexity = WidgetComplexity.MODERATE,
        updateFrequencyMinutes = 120
    ),
    
    /**
     * Strength progression widget
     * Tracks personal records and strength gains over time
     */
    STRENGTH_PROGRESS(
        displayName = "Strength Progress",
        description = "Track personal records and strength improvements",
        complexity = WidgetComplexity.MODERATE,
        updateFrequencyMinutes = 240
    ),
    
    /**
     * Consistency streak widget
     * Shows current and longest workout streaks
     */
    CONSISTENCY_STREAK(
        displayName = "Consistency Streak",
        description = "Monitor your workout consistency streaks",
        complexity = WidgetComplexity.SIMPLE,
        updateFrequencyMinutes = 60
    ),
    
    /**
     * Volume chart widget
     * Visual chart displaying volume progression over time
     */
    VOLUME_CHART(
        displayName = "Volume Chart",
        description = "Visual chart showing volume progression over time",
        complexity = WidgetComplexity.MODERATE,
        updateFrequencyMinutes = 120,
        category = WidgetCategory.CHARTS
    ),
    
    /**
     * Duration chart widget
     * Visual chart displaying workout duration trends
     */
    DURATION_CHART(
        displayName = "Duration Chart",
        description = "Visual chart showing workout duration trends",
        complexity = WidgetComplexity.MODERATE,
        updateFrequencyMinutes = 120,
        category = WidgetCategory.CHARTS
    ),
    
    /**
     * Frequency chart widget
     * Visual chart displaying workout frequency patterns
     */
    FREQUENCY_CHART(
        displayName = "Frequency Chart",
        description = "Visual chart showing workout frequency patterns",
        complexity = WidgetComplexity.MODERATE,
        updateFrequencyMinutes = 120,
        category = WidgetCategory.CHARTS
    ),
    
    /**
     * Workout streak widget
     * Tracks current and historical workout streaks
     */
    WORKOUT_STREAK(
        displayName = "Workout Streak",
        description = "Track your current and historical workout streaks",
        complexity = WidgetComplexity.SIMPLE,
        updateFrequencyMinutes = 60
    ),
    
    /**
     * Personal records widget
     * Displays recent and all-time personal records
     */
    PERSONAL_RECORDS(
        displayName = "Personal Records",
        description = "View your recent and all-time personal records",
        complexity = WidgetComplexity.MODERATE,
        updateFrequencyMinutes = 180,
        category = WidgetCategory.PROGRESS
    ),
    
    /**
     * Volume trends analysis widget
     * Advanced trend analysis with projections
     */
    VOLUME_TRENDS(
        displayName = "Volume Trends",
        description = "Advanced analysis of volume trends and projections",
        complexity = WidgetComplexity.COMPLEX,
        updateFrequencyMinutes = 480
    ),
    
    /**
     * Recovery metrics widget
     * Tracks rest days, recovery patterns, and recommendations
     */
    RECOVERY_METRICS(
        displayName = "Recovery Metrics",
        description = "Monitor rest patterns and recovery recommendations",
        complexity = WidgetComplexity.COMPLEX,
        updateFrequencyMinutes = 360
    ),
    
    /**
     * Performance analysis widget
     * Comprehensive performance analytics with detailed insights
     */
    PERFORMANCE_ANALYSIS(
        displayName = "Performance Analysis",
        description = "Detailed performance insights and recommendations",
        complexity = WidgetComplexity.COMPLEX,
        updateFrequencyMinutes = 720
    ),
    
    /**
     * Calories burned widget
     * Shows daily calories burned from workouts with trend visualization
     */
    CALORIES_BURNED(
        displayName = "Calories Burned",
        description = "Track daily calories burned from your workouts",
        complexity = WidgetComplexity.SIMPLE,
        updateFrequencyMinutes = 30,
        category = WidgetCategory.METRICS,
        priority = WidgetPriority.ESSENTIAL
    ),
    
    /**
     * Daily calories widget
     * Displays today's calorie burn with goal comparison
     */
    DAILY_CALORIES(
        displayName = "Today's Calories",
        description = "View calories burned today compared to your goals",
        complexity = WidgetComplexity.SIMPLE,
        updateFrequencyMinutes = 15,
        category = WidgetCategory.METRICS,
        priority = WidgetPriority.STANDARD
    ),
    
    /**
     * Weekly calorie trend widget
     * Weekly calorie burn trends with pattern analysis
     */
    WEEKLY_CALORIE_TREND(
        displayName = "Weekly Calorie Trend",
        description = "Analyze your weekly calorie burn patterns and trends",
        complexity = WidgetComplexity.MODERATE,
        updateFrequencyMinutes = 240,
        category = WidgetCategory.CHARTS,
        priority = WidgetPriority.STANDARD
    ),
    
    /**
     * Total volume widget (alias for TOTAL_VOLUME)
     */
    TotalVolume(
        displayName = "Total Volume",
        description = "Monitor your total weight lifted progress",
        complexity = WidgetComplexity.SIMPLE,
        updateFrequencyMinutes = 30
    ),
    
    /**
     * Workout frequency widget (alias for WORKOUT_FREQUENCY)
     */
    WorkoutFrequency(
        displayName = "Workout Frequency",
        description = "Track your workout consistency over time",
        complexity = WidgetComplexity.SIMPLE,
        updateFrequencyMinutes = 60
    ),
    
    /**
     * Consistency streak widget (alias for CONSISTENCY_STREAK)
     */
    ConsistencyStreak(
        displayName = "Consistency Streak",
        description = "Monitor your workout consistency streaks",
        complexity = WidgetComplexity.SIMPLE,
        updateFrequencyMinutes = 60
    ),
    
    /**
     * Average duration widget
     */
    AverageDuration(
        displayName = "Average Duration",
        description = "Track average workout duration",
        complexity = WidgetComplexity.SIMPLE,
        updateFrequencyMinutes = 60
    ),
    
    /**
     * Volume load progression widget
     */
    VolumeLoadProgression(
        displayName = "Volume Progression",
        description = "Track volume progression over time",
        complexity = WidgetComplexity.MODERATE,
        updateFrequencyMinutes = 120
    ),
    
    /**
     * Progress chart widget
     */
    ProgressChart(
        displayName = "Progress Chart",
        description = "Visual progress tracking chart",
        complexity = WidgetComplexity.MODERATE,
        updateFrequencyMinutes = 120
    ),
    
    /**
     * One rep max progression widget
     */
    OneRMProgression(
        displayName = "1RM Progression",
        description = "Track one rep max progression",
        complexity = WidgetComplexity.MODERATE,
        updateFrequencyMinutes = 180
    ),
    
    /**
     * Weekly trends analysis widget
     * Advanced weekly trend analysis with pattern recognition
     */
    WeeklyTrends(
        displayName = "Weekly Trends",
        description = "Analyze weekly workout trends and patterns",
        complexity = WidgetComplexity.COMPLEX,
        updateFrequencyMinutes = 480,
        category = WidgetCategory.ANALYTICS
    ),
    
    /**
     * Muscle group distribution widget
     * Shows distribution of training across muscle groups
     */
    MuscleGroupDistribution(
        displayName = "Muscle Group Distribution",
        description = "View training distribution across muscle groups",
        complexity = WidgetComplexity.MODERATE,
        updateFrequencyMinutes = 240,
        category = WidgetCategory.ANALYTICS
    ),
    
    /**
     * Recovery patterns widget
     * Tracks recovery patterns and recommendations
     */
    RecoveryPatterns(
        displayName = "Recovery Patterns",
        description = "Monitor recovery patterns and recommendations",
        complexity = WidgetComplexity.COMPLEX,
        updateFrequencyMinutes = 360,
        category = WidgetCategory.ANALYTICS
    );
    
    /**
     * Gets the widget priority for layout ordering (lower = higher priority)
     */
    fun getLayoutPriority(): Int = when (this) {
        WORKOUT_FREQUENCY -> 1
        TOTAL_VOLUME -> 2
        CONSISTENCY_STREAK -> 3
        STRENGTH_PROGRESS -> 4
        VOLUME_CALENDAR -> 5
        VOLUME_TRENDS -> 6
        RECOVERY_METRICS -> 7
        PERFORMANCE_ANALYSIS -> 8
        CALORIES_BURNED -> 1
        DAILY_CALORIES -> 2
        WEEKLY_CALORIE_TREND -> 5
        VOLUME_CHART -> 6
        DURATION_CHART -> 7
        FREQUENCY_CHART -> 8
        WORKOUT_STREAK -> 3
        PERSONAL_RECORDS -> 4
        TotalVolume -> 2
        WorkoutFrequency -> 1
        ConsistencyStreak -> 3
        AverageDuration -> 4
        VolumeLoadProgression -> 6
        ProgressChart -> 5
        OneRMProgression -> 7
        WeeklyTrends -> 8
        MuscleGroupDistribution -> 9
        RecoveryPatterns -> 10
    }
    
    /**
     * Checks if this widget is suitable for real-time updates
     */
    fun isRealTimeCompatible(): Boolean = complexity == WidgetComplexity.SIMPLE
    
    /**
     * Gets the minimum data points required for this widget to display
     */
    fun getMinimumDataPoints(): Int = when (complexity) {
        WidgetComplexity.SIMPLE -> 1
        WidgetComplexity.MODERATE -> 3
        WidgetComplexity.COMPLEX -> 7
    }
    
    /**
     * Gets the recommended widget size for UI layout
     */
    fun getRecommendedSize(): WidgetSize = when (complexity) {
        WidgetComplexity.SIMPLE -> WidgetSize.SMALL
        WidgetComplexity.MODERATE -> WidgetSize.MEDIUM
        WidgetComplexity.COMPLEX -> WidgetSize.LARGE
    }
    
    companion object {
        /**
         * Gets widgets suitable for a specific complexity level
         */
        fun getWidgetsByComplexity(complexity: WidgetComplexity): List<AnalyticsWidget> = 
            values().filter { it.complexity == complexity }
        
        /**
         * Gets widgets that support real-time updates
         */
        fun getRealTimeWidgets(): List<AnalyticsWidget> = 
            values().filter { it.isRealTimeCompatible() }
        
        /**
         * Gets widgets ordered by layout priority
         */
        fun getWidgetsByPriority(): List<AnalyticsWidget> = 
            values().sortedBy { it.getLayoutPriority() }
    }
}

/**
 * Widget complexity levels affecting calculation requirements and update frequency
 */
enum class WidgetComplexity(val displayName: String) {
    SIMPLE("Simple"),
    MODERATE("Moderate"),
    COMPLEX("Complex")
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
 * Widget categories for dashboard organization
 */
enum class WidgetCategory(val displayName: String) {
    METRICS("Metrics"),
    CHARTS("Charts"),
    PROGRESS("Progress"),
    ANALYTICS("Analytics")
}

/**
 * Widget priority levels for dashboard configuration
 */
enum class WidgetPriority(val configurationLevel: Int) {
    ESSENTIAL(1),
    STANDARD(2),
    ADVANCED(3)
}

/**
 * Widget data interface for type-safe widget data handling
 */
interface WidgetData {
    val widgetType: AnalyticsWidget
    val lastUpdated: kotlinx.datetime.Instant
    val isValid: Boolean
    
    /**
     * Checks if the data is fresh enough based on widget update frequency
     */
    fun isFresh(): Boolean {
        val now = kotlinx.datetime.Clock.System.now()
        val maxAgeMillis = widgetType.updateFrequencyMinutes * 60 * 1000L
        return (now - lastUpdated).inWholeMilliseconds <= maxAgeMillis
    }
}

/**
 * Concrete implementation of WidgetData for basic widgets
 */
data class BasicWidgetData(
    override val widgetType: AnalyticsWidget,
    override val lastUpdated: kotlinx.datetime.Instant,
    val value: String,
    val subtitle: String,
    val trend: TrendDirection?
) : WidgetData {
    override val isValid: Boolean = value.isNotBlank()
}

/**
 * Enhanced WidgetData implementation for UI components
 */
data class UIWidgetData(
    val widget: AnalyticsWidget,
    val value: String,
    val subtitle: String = "",
    val trend: TrendDirection = TrendDirection.STABLE,
    val isLoading: Boolean = false
) : WidgetData {
    override val widgetType: AnalyticsWidget = widget
    override val lastUpdated: kotlinx.datetime.Instant = kotlinx.datetime.Clock.System.now()
    override val isValid: Boolean = value.isNotBlank()
    
    /**
     * Creates a copy with updated loading state
     */
    fun copy(isLoading: Boolean): UIWidgetData = copy(isLoading = isLoading)
}

// Widget data interface for analytics widgets - no alias needed