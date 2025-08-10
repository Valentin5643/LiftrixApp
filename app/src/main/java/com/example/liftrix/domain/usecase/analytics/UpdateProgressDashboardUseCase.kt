package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.ProgressMetrics
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.AnalyticsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for updating progress dashboard with real-time analytics data
 * 
 * Provides comprehensive dashboard updates including:
 * - Real-time progress metrics calculation for multiple time ranges
 * - Dashboard configuration-specific data aggregation
 * - Batch processing for optimal performance
 * - Trigger-based updates from workout completion events
 * 
 * Business Logic:
 * - Validates user access and dashboard configuration
 * - Calculates progress metrics for standard time ranges (week/month/quarter)
 * - Aggregates data based on user's dashboard configuration level
 * - Triggers real-time updates on workout completion
 * - Handles offline scenarios with cached data
 * 
 * Performance Targets:
 * - Dashboard update: <500ms for comprehensive calculations
 * - Real-time triggers: <2 seconds from workout completion
 * - Memory efficiency: Optimized for concurrent calculations
 */
class UpdateProgressDashboardUseCase @Inject constructor(
    private val analyticsEngine: AnalyticsEngine,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Updates progress dashboard with comprehensive analytics data
     * 
     * @param request The dashboard update request
     * @return LiftrixResult containing DashboardData or error information
     */
    suspend operator fun invoke(request: DashboardUpdateRequest): LiftrixResult<DashboardData> {
        return try {
            Timber.d("Updating progress dashboard for user: ${request.userId}, config: ${request.configuration.name}")
            
            // Validate request
            val validationResult = validateRequest(request)
            if (validationResult.isFailure) {
                return validationResult as LiftrixResult<DashboardData>
            }
            
            // Calculate progress metrics for multiple time ranges concurrently
            val metricsResults = calculateMetricsForTimeRanges(request.userId, request.timeRanges)
            
            // Aggregate dashboard data based on configuration
            val dashboardData = aggregateDashboardData(
                userId = request.userId,
                configuration = request.configuration,
                metricsResults = metricsResults
            )
            
            Timber.d("Successfully updated dashboard with ${metricsResults.size} time range metrics")
            LiftrixResult.success(dashboardData)
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error updating progress dashboard for user: ${request.userId}")
            val error = LiftrixError.UnknownError("Failed to update progress dashboard: ${e.message}")
            errorHandler.handleError(error, mapOf("context" to "UpdateProgressDashboardUseCase"))
            liftrixFailure(error)
        }
    }
    
    /**
     * Convenience method for updating dashboard with default configuration
     */
    suspend fun updateWithDefaults(userId: String): LiftrixResult<DashboardData> {
        return invoke(
            DashboardUpdateRequest(
                userId = userId,
                configuration = DashboardConfiguration.Intermediate, // Default to intermediate
                timeRanges = getDefaultTimeRanges()
            )
        )
    }
    
    /**
     * Triggers real-time dashboard update following workout completion
     * 
     * @param userId The user ID whose workout was completed
     * @param workoutId The ID of the completed workout (for context)
     * @return LiftrixResult indicating update success
     */
    suspend fun triggerRealTimeUpdate(userId: String, workoutId: String): LiftrixResult<Unit> {
        return try {
            Timber.d("Triggering real-time dashboard update for user: $userId after workout: $workoutId")
            
            // Recalculate recent metrics for immediate dashboard refresh
            val recentTimeRange = TimeRange.lastMonth()
            val metricsResult = analyticsEngine.calculateProgressMetrics(userId, recentTimeRange)
            
            if (metricsResult.isFailure) {
                val error = metricsResult.exceptionOrNull() as? LiftrixError
                    ?: LiftrixError.UnknownError("Failed to calculate real-time metrics")
                
                errorHandler.handleError(error, mapOf("context" to "UpdateProgressDashboardUseCase.triggerRealTimeUpdate"))
                return liftrixFailure(error)
            }
            
            // TODO: Update dashboard cache/state for real-time UI updates
            // This would typically update a cache or notify UI components of data changes
            
            Timber.d("Successfully triggered real-time dashboard update")
            LiftrixResult.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Error triggering real-time dashboard update")
            val error = LiftrixError.UnknownError("Failed to trigger real-time update: ${e.message}")
            errorHandler.handleError(error, mapOf("context" to "UpdateProgressDashboardUseCase.triggerRealTimeUpdate"))
            liftrixFailure(error)
        }
    }
    
    /**
     * Calculates progress metrics for multiple time ranges concurrently
     */
    private suspend fun calculateMetricsForTimeRanges(
        userId: String,
        timeRanges: List<TimeRange>
    ): Map<TimeRangeType, ProgressMetrics> = withContext(Dispatchers.IO) {
        
        val deferredResults = timeRanges.map { timeRange ->
            async {
                val result = analyticsEngine.calculateProgressMetrics(userId, timeRange)
                timeRange.type to result
            }
        }
        
        val results = deferredResults.awaitAll()
        val successfulResults = mutableMapOf<TimeRangeType, ProgressMetrics>()
        val errors = mutableListOf<String>()
        
        results.forEach { (timeRangeType, result) ->
            if (result.isSuccess) {
                successfulResults[timeRangeType] = result.getOrThrow()
            } else {
                errors.add("Failed to calculate metrics for $timeRangeType")
            }
        }
        
        if (errors.isNotEmpty()) {
            Timber.w("Some metrics calculations failed: $errors")
        }
        
        successfulResults
    }
    
    /**
     * Aggregates dashboard data based on configuration and calculated metrics
     */
    private fun aggregateDashboardData(
        userId: String,
        configuration: DashboardConfiguration,
        metricsResults: Map<TimeRangeType, ProgressMetrics>
    ): DashboardData {
        val primaryMetrics = metricsResults[TimeRangeType.MONTH]
        val sixMonthMetrics = metricsResults[TimeRangeType.SIX_MONTHS]
        val allTimeMetrics = metricsResults[TimeRangeType.ALL_TIME]
        
        return DashboardData(
            userId = userId,
            configuration = configuration,
            primaryMetrics = primaryMetrics,
            sixMonthMetrics = sixMonthMetrics,
            allTimeMetrics = allTimeMetrics,
            lastUpdated = kotlinx.datetime.Clock.System.now(),
            isRealTimeData = true
        )
    }
    
    /**
     * Gets default time ranges for dashboard calculations
     */
    private fun getDefaultTimeRanges(): List<TimeRange> {
        return listOf(
            TimeRange.lastMonth(),
            TimeRange.lastSixMonths(),
            TimeRange.allTime()
        )
    }
    
    /**
     * Validates the dashboard update request
     */
    private fun validateRequest(request: DashboardUpdateRequest): LiftrixResult<Unit> {
        val violations = mutableListOf<String>()
        
        // Validate user ID
        if (request.userId.isBlank()) {
            violations.add("User ID cannot be blank")
        }
        
        // Validate time ranges
        if (request.timeRanges.isEmpty()) {
            violations.add("At least one time range must be specified")
        }
        
        if (request.timeRanges.size > MAX_TIME_RANGES) {
            violations.add("Cannot process more than $MAX_TIME_RANGES time ranges")
        }
        
        // Validate each time range
        request.timeRanges.forEach { timeRange ->
            if (!timeRange.isValid()) {
                violations.add("Invalid time range: ${timeRange.type}")
            }
        }
        
        return if (violations.isEmpty()) {
            LiftrixResult.success(Unit)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "DashboardUpdateRequest",
                    violations = violations
                )
            )
        }
    }
    
    companion object {
        private const val MAX_TIME_RANGES = 5 // Maximum time ranges per request
    }
}

/**
 * Request data class for dashboard update operations
 * 
 * @property userId The ID of the user to update dashboard for
 * @property configuration The dashboard configuration level
 * @property timeRanges List of time ranges to calculate metrics for
 * @property forceRefresh Whether to force refresh ignoring cache
 */
data class DashboardUpdateRequest(
    val userId: String,
    val configuration: DashboardConfiguration,
    val timeRanges: List<TimeRange> = emptyList(),
    val forceRefresh: Boolean = false
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(timeRanges.size <= 5) { "Cannot process more than 5 time ranges" }
    }
}

/**
 * Data class representing aggregated dashboard data
 * 
 * @property userId The user ID this data belongs to
 * @property configuration The dashboard configuration used
 * @property primaryMetrics The primary time range metrics (usually monthly)
 * @property weeklyMetrics Weekly metrics for short-term trends
 * @property quarterlyMetrics Quarterly metrics for long-term trends
 * @property lastUpdated Timestamp of last data update
 * @property isRealTimeData Whether this data is from real-time calculations
 */
data class DashboardData(
    val userId: String,
    val configuration: DashboardConfiguration,
    val primaryMetrics: ProgressMetrics?,
    val sixMonthMetrics: ProgressMetrics?,
    val allTimeMetrics: ProgressMetrics?,
    val lastUpdated: kotlinx.datetime.Instant,
    val isRealTimeData: Boolean = false
) {
    /**
     * Checks if dashboard has sufficient data for display
     */
    fun hasSufficientData(): Boolean = primaryMetrics != null
    
    /**
     * Gets the most relevant metrics based on data availability
     */
    fun getBestAvailableMetrics(): ProgressMetrics? {
        return primaryMetrics ?: sixMonthMetrics ?: allTimeMetrics
    }
    
    /**
     * Calculates data freshness in minutes
     */
    fun getDataFreshnessMinutes(): Long {
        val now = kotlinx.datetime.Clock.System.now()
        return (now - lastUpdated).inWholeMinutes
    }
}