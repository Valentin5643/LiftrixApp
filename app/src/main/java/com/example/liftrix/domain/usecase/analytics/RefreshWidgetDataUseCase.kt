package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetComplexity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.service.AnalyticsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for refreshing widget data with smart batch operations and cache invalidation.
 * 
 * This use case handles widget data refresh operations with performance optimization,
 * batch processing capabilities, and intelligent cache management. It coordinates
 * with the AnalyticsService to ensure fresh data retrieval while maintaining
 * optimal performance through smart refresh strategies.
 * 
 * Key Features:
 * - Batch refresh operations for multiple widgets
 * - Smart refresh intervals based on widget complexity
 * - Cache invalidation and fresh data retrieval
 * - Error handling with graceful degradation
 * - Performance optimization through concurrent operations
 * 
 * Smart Refresh Strategy:
 * - SIMPLE widgets: Immediate refresh, 15-30min intervals
 * - MODERATE widgets: Background refresh, 60-120min intervals
 * - COMPLEX widgets: Scheduled refresh, 240-720min intervals
 * 
 * Usage:
 * ```
 * // Single widget refresh
 * refreshWidgetDataUseCase.execute(RefreshParams.Single(userId, widgetType))
 * 
 * // Batch widget refresh
 * refreshWidgetDataUseCase.execute(RefreshParams.Batch(userId, widgetTypes))
 * ```
 */
@Singleton
class RefreshWidgetDataUseCase @Inject constructor(
    private val analyticsService: AnalyticsService
) {
    
    /**
     * Sealed class representing different refresh operation parameters
     */
    sealed class RefreshParams {
        abstract val userId: String
        
        /**
         * Single widget refresh parameters
         * @property userId User identifier for data refresh
         * @property widgetType Type of widget to refresh
         */
        data class Single(
            override val userId: String,
            val widgetType: AnalyticsWidget
        ) : RefreshParams()
        
        /**
         * Batch widget refresh parameters
         * @property userId User identifier for data refresh
         * @property widgetTypes List of widget types to refresh
         * @property concurrent Whether to refresh widgets concurrently (default: true)
         */
        data class Batch(
            override val userId: String,
            val widgetTypes: List<AnalyticsWidget>,
            val concurrent: Boolean = true
        ) : RefreshParams()
        
        /**
         * All widgets refresh parameters
         * @property userId User identifier for data refresh
         * @property complexityFilter Optional filter by widget complexity
         */
        data class All(
            override val userId: String,
            val complexityFilter: List<WidgetComplexity>? = null
        ) : RefreshParams()
    }
    
    /**
     * Refresh result containing success/failure information for batch operations
     */
    data class RefreshResult(
        val successfulWidgets: List<AnalyticsWidget>,
        val failedWidgets: Map<AnalyticsWidget, LiftrixError>,
        val totalRefreshed: Int
    ) {
        val isFullSuccess: Boolean get() = failedWidgets.isEmpty()
        val hasPartialSuccess: Boolean get() = successfulWidgets.isNotEmpty() && failedWidgets.isNotEmpty()
        val isCompleteFailure: Boolean get() = successfulWidgets.isEmpty() && failedWidgets.isNotEmpty()
    }
    
    /**
     * Executes widget data refresh based on the provided parameters.
     * 
     * Supports single widget refresh, batch refresh, and all widgets refresh
     * with intelligent cache invalidation and performance optimization.
     * 
     * @param params Refresh parameters (Single, Batch, or All)
     * @return LiftrixResult containing refresh results or error
     */
    suspend fun execute(params: RefreshParams): LiftrixResult<RefreshResult> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to refresh widget data for user ${params.userId}: ${throwable.message}",
                    operation = "refreshWidgetData",
                    retryable = true
                )
            }
        ) {
            when (params) {
                is RefreshParams.Single -> refreshSingleWidget(params)
                is RefreshParams.Batch -> refreshMultipleWidgets(params)
                is RefreshParams.All -> refreshAllWidgets(params)
            }
        }
    }
    
    /**
     * Refreshes a single widget with cache invalidation
     */
    private suspend fun refreshSingleWidget(params: RefreshParams.Single): RefreshResult {
        Timber.d("Refreshing single widget for user: ${params.userId}, widget: ${params.widgetType}")
        
        return try {
            // Force fresh data retrieval (AnalyticsService handles cache invalidation)
            val result = analyticsService.getWidgetData(params.userId, params.widgetType)
            
            result.fold(
                onSuccess = { widgetData ->
                    Timber.d("Successfully refreshed widget: ${params.widgetType}")
                    RefreshResult(
                        successfulWidgets = listOf(params.widgetType),
                        failedWidgets = emptyMap(),
                        totalRefreshed = 1
                    )
                },
                onFailure = { error ->
                    Timber.w("Failed to refresh widget: ${params.widgetType} - $error")
                    RefreshResult(
                        successfulWidgets = emptyList(),
                        failedWidgets = mapOf(params.widgetType to LiftrixError.DataRetrievalError(
                            errorMessage = "Failed to refresh widget: ${error.message}",
                            operation = "refreshWidget",
                            retryable = true
                        )),
                        totalRefreshed = 0
                    )
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error refreshing widget: ${params.widgetType}")
            RefreshResult(
                successfulWidgets = emptyList(),
                failedWidgets = mapOf(params.widgetType to LiftrixError.UnknownError("Refresh failed: ${e.message}")),
                totalRefreshed = 0
            )
        }
    }
    
    /**
     * Refreshes multiple widgets with optional concurrent processing
     */
    private suspend fun refreshMultipleWidgets(params: RefreshParams.Batch): RefreshResult {
        Timber.d("Refreshing ${params.widgetTypes.size} widgets for user: ${params.userId}, concurrent: ${params.concurrent}")
        
        val successfulWidgets = mutableListOf<AnalyticsWidget>()
        val failedWidgets = mutableMapOf<AnalyticsWidget, LiftrixError>()
        
        if (params.concurrent) {
            // Concurrent refresh for better performance
            coroutineScope {
                val refreshJobs = params.widgetTypes.map { widgetType ->
                    async {
                    try {
                        val result = analyticsService.getWidgetData(params.userId, widgetType)
                        result.fold(
                            onSuccess = { 
                                synchronized(successfulWidgets) { successfulWidgets.add(widgetType) }
                            },
                            onFailure = { error ->
                                val liftrixError = if (error is LiftrixError) error else LiftrixError.DataRetrievalError(
                                    errorMessage = "Failed to refresh widget: ${error.message}",
                                    operation = "refreshWidget",
                                    retryable = true
                                )
                                synchronized(failedWidgets) { failedWidgets[widgetType] = liftrixError }
                            }
                        )
                    } catch (e: Exception) {
                        synchronized(failedWidgets) { 
                            failedWidgets[widgetType] = LiftrixError.UnknownError("Refresh failed: ${e.message}")
                        }
                    }
                    }
                }
                
                // Wait for all refresh operations to complete
                refreshJobs.awaitAll()
            }
        } else {
            // Sequential refresh for lower resource usage
            params.widgetTypes.forEach { widgetType ->
                try {
                    val result = analyticsService.getWidgetData(params.userId, widgetType)
                    result.fold(
                        onSuccess = { successfulWidgets.add(widgetType) },
                        onFailure = { error -> 
                            val liftrixError = if (error is LiftrixError) error else LiftrixError.DataRetrievalError(
                                errorMessage = "Failed to refresh widget: ${error.message}",
                                operation = "refreshWidget",
                                retryable = true
                            )
                            failedWidgets[widgetType] = liftrixError
                        }
                    )
                } catch (e: Exception) {
                    failedWidgets[widgetType] = LiftrixError.UnknownError("Refresh failed: ${e.message}")
                }
            }
        }
        
        Timber.d("Batch refresh completed - Success: ${successfulWidgets.size}, Failed: ${failedWidgets.size}")
        
        return RefreshResult(
            successfulWidgets = successfulWidgets,
            failedWidgets = failedWidgets,
            totalRefreshed = successfulWidgets.size
        )
    }
    
    /**
     * Refreshes all available widgets with optional complexity filtering
     */
    private suspend fun refreshAllWidgets(params: RefreshParams.All): RefreshResult {
        val allWidgets = AnalyticsWidget.getAllWidgets()
        val filteredWidgets = params.complexityFilter?.let { filter ->
            allWidgets.filter { widget -> filter.contains(widget.complexity) }
        } ?: allWidgets
        
        Timber.d("Refreshing all widgets for user: ${params.userId}, total: ${filteredWidgets.size}")
        
        // Use batch refresh with concurrent processing for all widgets
        return refreshMultipleWidgets(
            RefreshParams.Batch(
                userId = params.userId,
                widgetTypes = filteredWidgets,
                concurrent = true
            )
        )
    }
    
    /**
     * Determines if a widget should be refreshed based on complexity and last update time
     * 
     * @param widgetType Widget to check
     * @param lastUpdateTime Time of last update in milliseconds
     * @return Boolean indicating if refresh is recommended
     */
    fun shouldRefreshWidget(widgetType: AnalyticsWidget, lastUpdateTime: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val dataAge = currentTime - lastUpdateTime
        val refreshInterval = widgetType.getRefreshIntervalMinutes() * 60 * 1000L
        
        return dataAge > refreshInterval
    }
    
    /**
     * Gets recommended widgets for refresh based on their staleness
     * 
     * @param userId User identifier
     * @param widgets List of widgets to check
     * @param lastUpdateTimes Map of widget to last update time
     * @return List of widgets that should be refreshed
     */
    fun getStaleWidgets(
        userId: String,
        widgets: List<AnalyticsWidget>,
        lastUpdateTimes: Map<AnalyticsWidget, Long>
    ): List<AnalyticsWidget> {
        return widgets.filter { widget ->
            val lastUpdate = lastUpdateTimes[widget] ?: 0L
            shouldRefreshWidget(widget, lastUpdate)
        }
    }
    
    /**
     * Estimates refresh operation time based on widget complexity and count
     * 
     * @param widgets Widgets to refresh
     * @param concurrent Whether refresh will be concurrent
     * @return Estimated time in milliseconds
     */
    fun estimateRefreshTime(widgets: List<AnalyticsWidget>, concurrent: Boolean): Long {
        val complexityCounts = widgets.groupingBy { it.complexity }.eachCount()
        
        val baseTime = when {
            concurrent -> {
                // Concurrent: time is dominated by slowest widget type
                complexityCounts.keys.maxOfOrNull { it.getEstimatedLoadTimeMs() } ?: 500L
            }
            else -> {
                // Sequential: sum of all widget load times
                complexityCounts.map { (complexity, count) ->
                    complexity.getEstimatedLoadTimeMs() * count
                }.sum()
            }
        }
        
        // Add 20% buffer for network overhead and processing
        return (baseTime * 1.2).toLong()
    }
}

/**
 * Extension functions for WidgetComplexity to support refresh operations
 */
private fun WidgetComplexity.getEstimatedLoadTimeMs(): Long = when (this) {
    WidgetComplexity.SIMPLE -> 200L      // 200ms for simple widgets
    WidgetComplexity.MODERATE -> 800L    // 800ms for moderate widgets  
    WidgetComplexity.COMPLEX -> 2000L    // 2s for complex widgets
}

