package com.example.liftrix.domain.model.analytics

import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Type-safe widget data interface hierarchy for comprehensive analytics display
 * 
 * Provides a type-safe foundation for all widget data with:
 * - Polymorphic data structures for different widget types
 * - Freshness validation based on complexity-specific refresh rates
 * - Serialization support for caching and network transport
 * - Performance optimization with lazy loading capabilities
 * - Error state management and recovery mechanisms
 */
sealed interface WidgetData {
    val widgetType: AnalyticsWidget
    val lastUpdated: Instant
    val isValid: Boolean
    val error: WidgetError?
    
    /**
     * Checks if the data is fresh enough based on widget complexity
     */
    fun isFresh(): Boolean {
        val now = Clock.System.now()
        val maxAgeMillis = widgetType.complexity.defaultRefreshIntervalMinutes * 60 * 1000L
        return (now - lastUpdated).inWholeMilliseconds <= maxAgeMillis
    }
    
    /**
     * Gets display-ready formatted data for UI consumption
     */
    fun getDisplayData(): DisplayData
    
    /**
     * Validates data integrity and business rules
     */
    fun validate(): WidgetValidationResult
}

/**
 * Metric widget data for single-value displays with trends
 * Used by: CaloriesBurned, TotalVolume, WorkoutFrequency, ConsistencyStreak, etc.
 */
@Serializable
data class MetricWidgetData(
    override val widgetType: AnalyticsWidget,
    override val lastUpdated: Instant,
    val primaryValue: String,
    val secondaryValue: String? = null,
    val unit: String,
    val trend: TrendDirection = TrendDirection.STABLE,
    val trendPercentage: Float = 0.0f,
    val comparisonPeriod: String = "vs last week",
    val isLoading: Boolean = false,
    override val error: WidgetError? = null
) : WidgetData {
    
    override val isValid: Boolean = primaryValue.isNotBlank() && error == null
    
    override fun getDisplayData(): DisplayData = DisplayData.Metric(
        primaryValue = primaryValue,
        secondaryValue = secondaryValue,
        unit = unit,
        trend = trend,
        trendText = trend.getPercentageDescription(trendPercentage),
        comparisonText = comparisonPeriod
    )
    
    override fun validate(): WidgetValidationResult = when {
        primaryValue.isBlank() -> WidgetValidationResult.INVALID_PRIMARY_VALUE
        unit.isBlank() -> WidgetValidationResult.INVALID_UNIT
        !isFresh() -> WidgetValidationResult.STALE_DATA
        else -> WidgetValidationResult.VALID
    }
    
    companion object {
        fun loading(widgetType: AnalyticsWidget): MetricWidgetData = MetricWidgetData(
            widgetType = widgetType,
            lastUpdated = Clock.System.now(),
            primaryValue = "",
            unit = "",
            isLoading = true
        )
        
        fun error(widgetType: AnalyticsWidget, error: WidgetError): MetricWidgetData = MetricWidgetData(
            widgetType = widgetType,
            lastUpdated = Clock.System.now(),
            primaryValue = "",
            unit = "",
            error = error
        )
    }
}

/**
 * Chart widget data for trend visualization and pattern analysis
 * Used by: VolumeChart, DurationChart, FrequencyChart, WeeklyCalorieTrend, etc.
 */
@Serializable
data class ChartWidgetData(
    override val widgetType: AnalyticsWidget,
    override val lastUpdated: Instant,
    val chartType: ChartType,
    val dataPoints: List<DataPoint>,
    val xAxisLabel: String,
    val yAxisLabel: String,
    val timeRange: String,
    val summary: ChartSummary,
    val isLoading: Boolean = false,
    override val error: WidgetError? = null
) : WidgetData {
    
    override val isValid: Boolean = dataPoints.isNotEmpty() && error == null
    
    override fun getDisplayData(): DisplayData = DisplayData.Chart(
        chartType = chartType,
        dataPoints = dataPoints,
        xAxisLabel = xAxisLabel,
        yAxisLabel = yAxisLabel,
        timeRange = timeRange,
        summaryText = summary.getFormattedSummary()
    )
    
    override fun validate(): WidgetValidationResult = when {
        dataPoints.isEmpty() -> WidgetValidationResult.INSUFFICIENT_DATA
        dataPoints.size < widgetType.complexity.getBatchSize() / 10 -> WidgetValidationResult.INSUFFICIENT_DATA
        !isFresh() -> WidgetValidationResult.STALE_DATA
        else -> WidgetValidationResult.VALID
    }
    
    companion object {
        fun loading(widgetType: AnalyticsWidget): ChartWidgetData = ChartWidgetData(
            widgetType = widgetType,
            lastUpdated = Clock.System.now(),
            chartType = ChartType.LINE,
            dataPoints = emptyList(),
            xAxisLabel = "",
            yAxisLabel = "",
            timeRange = "",
            summary = ChartSummary.empty(),
            isLoading = true
        )
    }
}

/**
 * Progress widget data for goal tracking and achievement display
 * Used by: PersonalRecords, ProgressChart, GoalProgress, OneRMProgression, etc.
 */
@Serializable
data class ProgressWidgetData(
    override val widgetType: AnalyticsWidget,
    override val lastUpdated: Instant,
    val currentValue: Float,
    val targetValue: Float,
    val progressPercentage: Float,
    val unit: String,
    val milestones: List<Milestone>,
    val timeToTarget: String? = null,
    val recentAchievements: List<Achievement> = emptyList(),
    val isLoading: Boolean = false,
    override val error: WidgetError? = null
) : WidgetData {
    
    override val isValid: Boolean = currentValue >= 0 && targetValue > 0 && error == null
    
    override fun getDisplayData(): DisplayData = DisplayData.Progress(
        currentValue = currentValue,
        targetValue = targetValue,
        progressPercentage = progressPercentage.coerceIn(0.0f, 100.0f),
        unit = unit,
        milestones = milestones,
        timeToTarget = timeToTarget,
        achievements = recentAchievements
    )
    
    override fun validate(): WidgetValidationResult = when {
        currentValue < 0 -> WidgetValidationResult.INVALID_CURRENT_VALUE
        targetValue <= 0 -> WidgetValidationResult.INVALID_TARGET_VALUE
        !isFresh() -> WidgetValidationResult.STALE_DATA
        else -> WidgetValidationResult.VALID
    }
}

/**
 * Basic widget data interface for simple widget types
 * Provides default implementation for basic widgets without specific data requirements
 */
typealias BasicWidgetData = MetricWidgetData

/**
 * Analytics widget data for complex insights and recommendations
 * Used by: PerformanceAnalysis, RecoveryMetrics, VolumeTrends, WeeklyTrends, etc.
 */
@Serializable 
data class AnalyticsWidgetData(
    override val widgetType: AnalyticsWidget,
    override val lastUpdated: Instant,
    val insights: List<Insight>,
    val recommendations: List<Recommendation>,
    val metrics: Map<String, String>,
    val confidence: Float,
    val timeRange: String,
    val isLoading: Boolean = false,
    override val error: WidgetError? = null
) : WidgetData {
    
    override val isValid: Boolean = insights.isNotEmpty() && confidence > 0.5f && error == null
    
    override fun getDisplayData(): DisplayData = DisplayData.Analytics(
        insights = insights,
        recommendations = recommendations,
        metrics = metrics,
        confidence = confidence,
        timeRange = timeRange
    )
    
    override fun validate(): WidgetValidationResult = when {
        insights.isEmpty() -> WidgetValidationResult.INSUFFICIENT_INSIGHTS
        confidence < 0.5f -> WidgetValidationResult.LOW_CONFIDENCE
        !isFresh() -> WidgetValidationResult.STALE_DATA
        else -> WidgetValidationResult.VALID
    }
}

/**
 * Display-ready data structures for UI consumption
 */
sealed class DisplayData {
    data class Metric(
        val primaryValue: String,
        val secondaryValue: String?,
        val unit: String,
        val trend: TrendDirection,
        val trendText: String,
        val comparisonText: String
    ) : DisplayData()
    
    data class Chart(
        val chartType: ChartType,
        val dataPoints: List<DataPoint>,
        val xAxisLabel: String,
        val yAxisLabel: String,
        val timeRange: String,
        val summaryText: String
    ) : DisplayData()
    
    data class Progress(
        val currentValue: Float,
        val targetValue: Float,
        val progressPercentage: Float,
        val unit: String,
        val milestones: List<Milestone>,
        val timeToTarget: String?,
        val achievements: List<Achievement>
    ) : DisplayData()
    
    data class Analytics(
        val insights: List<Insight>,
        val recommendations: List<Recommendation>,
        val metrics: Map<String, String>,
        val confidence: Float,
        val timeRange: String
    ) : DisplayData()
}

/**
 * Chart configuration and metadata
 */
enum class ChartType(val displayName: String) {
    LINE("Line Chart"),
    BAR("Bar Chart"),
    PIE("Pie Chart"),
    AREA("Area Chart"),
    SCATTER("Scatter Plot")
}

/**
 * Individual data point for chart visualization
 */
@Serializable
data class DataPoint(
    val x: Float,
    val y: Float,
    val label: String,
    val timestamp: Instant? = null
)

/**
 * Chart summary statistics and insights
 */
@Serializable
data class ChartSummary(
    val trend: TrendDirection,
    val changePercentage: Float,
    val average: Float,
    val peak: Float,
    val unit: String
) {
    fun getFormattedSummary(): String = 
        "${trend.displayName} trend (${trend.getPercentageDescription(changePercentage)})"
    
    companion object {
        fun empty(): ChartSummary = ChartSummary(
            trend = TrendDirection.UNKNOWN,
            changePercentage = 0.0f,
            average = 0.0f,
            peak = 0.0f,
            unit = ""
        )
    }
}

/**
 * Progress milestones for goal tracking
 */
@Serializable
data class Milestone(
    val value: Float,
    val label: String,
    val isAchieved: Boolean,
    val achievedDate: Instant? = null
)

/**
 * Achievement representation for progress widgets
 */
@Serializable
data class Achievement(
    val title: String,
    val description: String,
    val value: String,
    val achievedDate: Instant,
    val badgeIcon: String? = null
)

/**
 * Analytics insights with confidence levels
 */
@Serializable
data class Insight(
    val title: String,
    val description: String,
    val confidence: Float,
    val category: InsightCategory,
    val actionable: Boolean = false
)

/**
 * Actionable recommendations for users
 */
@Serializable
data class Recommendation(
    val title: String,
    val description: String,
    val priority: RecommendationPriority,
    val estimatedImpact: String,
    val actionSteps: List<String> = emptyList()
)

/**
 * Insight categorization for organization
 */
enum class InsightCategory(val displayName: String) {
    PERFORMANCE("Performance"),
    CONSISTENCY("Consistency"),
    RECOVERY("Recovery"),
    GOAL_PROGRESS("Goal Progress"),
    TREND_ANALYSIS("Trend Analysis")
}

/**
 * Recommendation priority levels
 */
enum class RecommendationPriority(val displayName: String, val urgency: Int) {
    LOW("Low Priority", 1),
    MEDIUM("Medium Priority", 2),
    HIGH("High Priority", 3),
    CRITICAL("Critical", 4)
}

/**
 * Widget-specific error types
 */
@Serializable
data class WidgetError(
    val code: ErrorCode,
    val message: String,
    val recoverable: Boolean = true,
    val retryAfterSeconds: Int? = null
)

enum class ErrorCode(val description: String) {
    NETWORK_ERROR("Network connection failed"),
    DATA_NOT_FOUND("Required data not available"),
    CALCULATION_TIMEOUT("Calculation exceeded timeout"),
    INSUFFICIENT_DATA("Not enough data for calculation"),
    PERMISSION_DENIED("Access to data denied"),
    UNKNOWN_ERROR("Unknown error occurred")
}

/**
 * Data validation results
 */
enum class WidgetValidationResult(val message: String, val isValid: Boolean) {
    VALID("Data is valid", true),
    INVALID_PRIMARY_VALUE("Primary value is invalid", false),
    INVALID_UNIT("Unit is invalid", false),
    INVALID_CURRENT_VALUE("Current value is invalid", false),
    INVALID_TARGET_VALUE("Target value is invalid", false),
    INSUFFICIENT_DATA("Insufficient data points", false),
    INSUFFICIENT_INSIGHTS("Insufficient insights generated", false),
    LOW_CONFIDENCE("Confidence level too low", false),
    STALE_DATA("Data is stale and needs refresh", false)
}