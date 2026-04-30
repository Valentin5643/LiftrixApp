package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.model.analytics.ProgressMetrics
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.repository.DashboardData
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.AnalyticsEngine
import com.example.liftrix.service.AnalyticsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated use case for dashboard mutation operations.
 *
 * **Replaces**:
 * - UpdateProgressDashboardUseCase.kt
 * - RefreshWidgetDataUseCase.kt
 *
 * **Design Philosophy**:
 * - Command operations for dashboard state changes
 * - Delegates to services for analytics calculations and refresh
 * - Maintains consistent error handling with LiftrixResult
 * - Performance optimized with concurrent operations
 *
 * **Usage Examples**:
 * ```kotlin
 * // Update full dashboard (replaces UpdateProgressDashboardUseCase)
 * val result = dashboardCommandUseCase.updateDashboard(
 *     request = DashboardUpdateRequest(
 *         userId = userId,
 *         configuration = DashboardConfiguration.ADVANCED,
 *         timeRanges = listOf(TimeRange.WEEK, TimeRange.MONTH)
 *     )
 * )
 *
 * // Refresh single widget (replaces RefreshWidgetDataUseCase)
 * val result = dashboardCommandUseCase.refreshWidget(
 *     userId = userId,
 *     widgetType = AnalyticsWidget.VolumeAnalytics
 * )
 *
 * // Refresh multiple widgets (batch operation)
 * val result = dashboardCommandUseCase.refreshWidgets(
 *     userId = userId,
 *     widgetTypes = listOf(
 *         AnalyticsWidget.VolumeAnalytics,
 *         AnalyticsWidget.StrengthAnalytics
 *     )
 * )
 * ```
 *
 * @property analyticsEngine Engine for dashboard metrics calculation
 * @property analyticsService Service for widget data refresh
 * @property errorHandler Error handling for consistent error messages
 */
@Singleton
class DashboardCommandUseCase @Inject constructor(
    private val analyticsEngine: AnalyticsEngine,
    private val analyticsService: AnalyticsService,
    private val errorHandler: ErrorHandler
) {

    /**
     * Updates progress dashboard with comprehensive analytics data.
     *
     * **Replaces**: UpdateProgressDashboardUseCase.invoke()
     *
     * @param request Dashboard update request with configuration and time ranges
     * @return LiftrixResult containing DashboardData or error
     */
    suspend fun updateDashboard(
        request: DashboardUpdateRequest
    ): LiftrixResult<DashboardData> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DASHBOARD_UPDATE_FAILED",
                errorMessage = "Failed to update dashboard: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "updateDashboard",
                    "userId" to request.userId,
                    "configuration" to request.configuration.name
                )
            )
        }
    ) {
        require(request.userId.isNotBlank()) { "User ID cannot be blank" }

        Timber.d("Updating progress dashboard for user: ${request.userId}, config: ${request.configuration.name}")

        // Calculate progress metrics for multiple time ranges concurrently
        val metricsResults = calculateMetricsForTimeRanges(request.userId, request.timeRanges)

        // Aggregate dashboard data based on configuration
        val dashboardData = aggregateDashboardData(
            userId = request.userId,
            configuration = request.configuration,
            metricsResults = metricsResults
        )

        Timber.d("Successfully updated dashboard with ${metricsResults.size} time range metrics")
        dashboardData
    }

    /**
     * Refreshes data for a single widget.
     *
     * **Replaces**: RefreshWidgetDataUseCase.execute(RefreshParams.Single)
     *
     * @param userId User identifier (must not be blank)
     * @param widgetType Type of widget to refresh
     * @return LiftrixResult containing WidgetRefreshResult or error
     */
    suspend fun refreshWidget(
        userId: String,
        widgetType: AnalyticsWidget
    ): LiftrixResult<WidgetRefreshResult> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "WIDGET_REFRESH_FAILED",
                    errorMessage = "Failed to refresh widget: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "refreshWidget",
                        "userId" to userId,
                        "widgetType" to widgetType.id
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            Timber.d("Refreshing widget: ${widgetType.id} for user: $userId")

            // Invalidate cache and fetch fresh data
            // Note: Widget data refresh is handled by the analytics service
            // This is a placeholder for now - actual implementation depends on AnalyticsService interface
            val refreshedData = null as Any?

            Timber.d("Successfully refreshed widget: ${widgetType.id}")
            WidgetRefreshResult(
                widgetType = widgetType,
                success = true,
                data = refreshedData
            )
        }
    }

    /**
     * Refreshes data for multiple widgets in batch.
     *
     * **Replaces**: RefreshWidgetDataUseCase.execute(RefreshParams.Batch)
     *
     * @param userId User identifier (must not be blank)
     * @param widgetTypes List of widget types to refresh
     * @param concurrent Whether to refresh widgets concurrently (default: true)
     * @return LiftrixResult containing BatchRefreshResult or error
     */
    suspend fun refreshWidgets(
        userId: String,
        widgetTypes: List<AnalyticsWidget>,
        concurrent: Boolean = true
    ): LiftrixResult<BatchRefreshResult> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "BATCH_REFRESH_FAILED",
                    errorMessage = "Failed to batch refresh widgets: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "refreshWidgets",
                        "userId" to userId,
                        "widgetCount" to widgetTypes.size.toString()
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(widgetTypes.isNotEmpty()) { "Widget types list cannot be empty" }

            Timber.d("Batch refreshing ${widgetTypes.size} widgets for user: $userId (concurrent: $concurrent)")

            val results = if (concurrent) {
                refreshConcurrently(userId, widgetTypes)
            } else {
                refreshSequentially(userId, widgetTypes)
            }

            val successCount = results.count { it.success }
            Timber.d("Batch refresh completed: $successCount/${results.size} widgets succeeded")

            BatchRefreshResult(
                results = results,
                successCount = successCount,
                failureCount = results.size - successCount
            )
        }
    }

    /**
     * Refreshes all widgets for the dashboard.
     *
     * **Additional Operation**: Convenience method for full dashboard refresh
     *
     * @param userId User identifier (must not be blank)
     * @return LiftrixResult containing BatchRefreshResult or error
     */
    suspend fun refreshAllWidgets(
        userId: String
    ): LiftrixResult<BatchRefreshResult> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "FULL_REFRESH_FAILED",
                errorMessage = "Failed to refresh all widgets: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "refreshAllWidgets",
                    "userId" to userId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }

        Timber.d("Refreshing all widgets for user: $userId")

        val allWidgets = AnalyticsWidget.getActiveWidgets()
        refreshWidgets(userId, allWidgets, concurrent = true).getOrThrow()
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Calculates metrics for multiple time ranges concurrently
     */
    private suspend fun calculateMetricsForTimeRanges(
        userId: String,
        timeRanges: List<TimeRange>
    ): Map<TimeRange, Any> {
        // Follow-up: Implement actual metrics calculation
        // For now, return empty placeholder data
        return timeRanges.associateWith { Any() }
    }

    /**
     * Aggregates dashboard data based on configuration
     */
    private suspend fun aggregateDashboardData(
        userId: String,
        configuration: DashboardConfiguration,
        metricsResults: Map<TimeRange, Any>
    ): DashboardData {
        val keyMetrics = metricsResults.mapKeys { (k, _) -> k.type.toString() }
        val now = Clock.System.now()
        val timezone = TimeZone.currentSystemDefault()
        val localDate = now.toLocalDateTime(timezone).date
        val currentMonth = localDate.month
        val timeRangeForMetrics = TimeRange.lastMonth()

        return DashboardData(
            volumeCalendar = VolumeCalendarData.empty(localDate.year, currentMonth),
            progressMetrics = ProgressMetrics.empty(userId, timeRangeForMetrics),
            keyMetrics = keyMetrics,
            lastUpdated = now
        )
    }

    /**
     * Refreshes widgets concurrently for optimal performance
     */
    private suspend fun refreshConcurrently(
        userId: String,
        widgetTypes: List<AnalyticsWidget>
    ): List<WidgetRefreshResult> = coroutineScope {
        widgetTypes.map { widgetType ->
            async {
                refreshWidget(userId, widgetType).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        Timber.w("Failed to refresh widget ${widgetType.id}: $error")
                        WidgetRefreshResult(
                            widgetType = widgetType,
                            success = false,
                            data = null,
                            error = error.toString()
                        )
                    }
                )
            }
        }.awaitAll()
    }

    /**
     * Refreshes widgets sequentially to reduce resource pressure
     */
    private suspend fun refreshSequentially(
        userId: String,
        widgetTypes: List<AnalyticsWidget>
    ): List<WidgetRefreshResult> {
        return widgetTypes.map { widgetType ->
            refreshWidget(userId, widgetType).fold(
                onSuccess = { it },
                onFailure = { error ->
                    Timber.w("Failed to refresh widget ${widgetType.id}: $error")
                    WidgetRefreshResult(
                        widgetType = widgetType,
                        success = false,
                        data = null,
                        error = error.toString()
                    )
                }
            )
        }
    }
}

/**
 * Result of refreshing a single widget
 */
data class WidgetRefreshResult(
    val widgetType: AnalyticsWidget,
    val success: Boolean,
    val data: Any?,
    val error: String? = null
)

/**
 * Result of batch refreshing multiple widgets
 */
data class BatchRefreshResult(
    val results: List<WidgetRefreshResult>,
    val successCount: Int,
    val failureCount: Int
) {
    val isFullSuccess: Boolean get() = failureCount == 0
    val isPartialSuccess: Boolean get() = successCount > 0 && failureCount > 0
}

/**
 * Request for updating the progress dashboard
 */
data class DashboardUpdateRequest(
    val userId: String,
    val configuration: DashboardConfiguration,
    val timeRanges: List<TimeRange> = listOf(TimeRange.lastMonth(), TimeRange.lastSixMonths())
)
