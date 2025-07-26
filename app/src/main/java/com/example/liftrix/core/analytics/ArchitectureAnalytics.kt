package com.example.liftrix.core.analytics

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.hasData
import com.example.liftrix.ui.common.state.isLoading
import com.example.liftrix.ui.common.state.canRetry
import com.example.liftrix.ui.navigation.LiftrixRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Architecture Analytics
 * 
 * Specialized analytics service for tracking new architectural patterns and their usage
 * across the Liftrix application. Provides insights into error patterns, navigation usage,
 * state transitions, and overall architecture performance to enable data-driven decisions
 * for continued architecture improvements.
 * 
 * Key Features:
 * - Error pattern tracking for proactive issue resolution
 * - Navigation usage analytics for UX optimization insights
 * - State transition analytics for performance optimization
 * - Architecture metrics for development team insights
 * 
 * Integration Points:
 * - LiftrixError hierarchy for comprehensive error tracking
 * - LiftrixRoute system for type-safe navigation analytics
 * - UiState patterns for state management insights
 * - AnalyticsService for Firebase Analytics integration
 */
@Singleton
class ArchitectureAnalytics @Inject constructor(
    private val analyticsService: AnalyticsService
) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        // Architecture analytics event names
        private const val EVENT_ERROR_OCCURRED = "liftrix_error_occurred"
        private const val EVENT_NAVIGATION_USAGE = "liftrix_navigation_usage" 
        private const val EVENT_STATE_TRANSITION = "liftrix_state_transition"
        private const val EVENT_ARCHITECTURE_METRICS = "liftrix_architecture_metrics"
        private const val EVENT_ERROR_PATTERN_ANALYSIS = "liftrix_error_pattern_analysis"
        private const val EVENT_NAVIGATION_PATTERN_ANALYSIS = "liftrix_navigation_pattern_analysis"
        
        // Parameter names for consistent event tracking
        private const val PARAM_ERROR_TYPE = "error_type"
        private const val PARAM_ERROR_MESSAGE = "error_message"
        private const val PARAM_IS_RECOVERABLE = "is_recoverable"
        private const val PARAM_RETRY_AFTER_MS = "retry_after_ms"
        private const val PARAM_ANALYTICS_CONTEXT = "analytics_context"
        private const val PARAM_ROUTE_TYPE = "route_type"
        private const val PARAM_ROUTE_PARAMS = "route_parameters"
        private const val PARAM_NAVIGATION_SOURCE = "navigation_source"
        private const val PARAM_FROM_STATE_TYPE = "from_state_type"
        private const val PARAM_TO_STATE_TYPE = "to_state_type"
        private const val PARAM_STATE_TRANSITION_TYPE = "state_transition_type"
        private const val PARAM_HAS_DATA = "has_data"
        private const val PARAM_IS_LOADING = "is_loading"
        private const val PARAM_CAN_RETRY = "can_retry"
        
        // Architecture metrics parameter names
        private const val PARAM_METRIC_TYPE = "metric_type"
        private const val PARAM_METRIC_VALUE = "metric_value"
        private const val PARAM_METRIC_CATEGORY = "metric_category"
        private const val PARAM_METRIC_TIMESTAMP = "metric_timestamp"
    }
    
    /**
     * Tracks error occurrence with comprehensive context for pattern analysis
     * 
     * @param error The LiftrixError that occurred
     * @param context Additional context information for error analysis
     */
    fun trackErrorOccurrence(error: LiftrixError, context: Map<String, Any> = emptyMap()) {
        coroutineScope.launch {
            try {
                // Build comprehensive error parameters
                val errorParameters = mutableMapOf<String, Any>(
                    PARAM_ERROR_TYPE to (error::class.simpleName ?: "Unknown"),
                    PARAM_ERROR_MESSAGE to (error.message ?: "Unknown error"),
                    PARAM_IS_RECOVERABLE to error.isRecoverable,
                    "content_type" to "architecture_error"
                ).apply {
                    // Add retry information if available
                    error.retryAfter?.let { retryAfter ->
                        put(PARAM_RETRY_AFTER_MS, retryAfter)
                    }
                    
                    // Add analytics context from error
                    if (error.analyticsContext.isNotEmpty()) {
                        put(PARAM_ANALYTICS_CONTEXT, error.analyticsContext.toString())
                    }
                    
                    // Add additional context
                    putAll(context)
                    
                    // Add error-specific parameters based on type
                    when (error) {
                        is LiftrixError.NetworkError -> {
                            error.httpStatusCode?.let { put("http_status_code", it) }
                            error.networkType?.let { put("network_type", it) }
                        }
                        is LiftrixError.ValidationError -> {
                            put("validation_field", error.field)
                            put("validation_violations", error.violations.joinToString(","))
                        }
                        is LiftrixError.AuthenticationError -> {
                            error.authProvider?.let { put("auth_provider", it) }
                            error.errorCode?.let { put("auth_error_code", it) }
                        }
                        is LiftrixError.DatabaseError -> {
                            error.operation?.let { put("db_operation", it) }
                            error.table?.let { put("db_table", it) }
                            error.sqlErrorCode?.let { put("sql_error_code", it) }
                        }
                        is LiftrixError.BusinessLogicError -> {
                            put("business_logic_code", error.code)
                            put("business_context", error.analyticsContext.toString())
                        }
                        is LiftrixError.CalculationError -> {
                            error.operation?.let { put("calculation_operation", it) }
                            put("calculation_context", error.analyticsContext.toString())
                        }
                        is LiftrixError.DataRetrievalError -> {
                            error.operation?.let { put("data_retrieval_operation", it) }
                            put("data_retrieval_retryable", error.retryable.toString())
                        }
                        is LiftrixError.ConfigurationError -> {
                            error.configKey?.let { put("configuration_key", it) }
                            error.configValue?.let { put("configuration_value", it) }
                        }
                        is LiftrixError.ExportError -> {
                            error.operation?.let { put("export_operation", it) }
                            error.format?.let { put("export_format", it) }
                        }
                        is LiftrixError.FileSystemError -> {
                            error.operation?.let { put("file_operation", it) }
                            error.filePath?.let { put("file_path", it) }
                        }
                        is LiftrixError.NotFoundError -> {
                            error.resourceType?.let { put("resource_type", it) }
                            error.resourceId?.let { put("resource_id", it) }
                        }
                        is LiftrixError.PermissionError -> {
                            error.permission?.let { put("permission", it) }
                        }
                        is LiftrixError.CacheError -> {
                            error.operation?.let { put("cache_operation", it) }
                            error.cacheKey?.let { put("cache_key", it) }
                        }
                        is LiftrixError.UnknownError -> {
                            put("unknown_error_category", "unhandled")
                        }
                    }
                }
                
                // Log the error occurrence event
                analyticsService.logEvent(
                    eventName = EVENT_ERROR_OCCURRED,
                    parameters = errorParameters
                )
                
                Timber.d("Error occurrence tracked: ${error::class.simpleName} - ${error.message}")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track error occurrence: ${error::class.simpleName}")
            }
        }
    }
    
    /**
     * Tracks navigation usage patterns for UX optimization insights
     * 
     * @param route The LiftrixRoute being navigated to
     * @param source The source of navigation (e.g., "menu", "button", "deep_link", "back_button")
     */
    fun trackNavigationUsage(route: LiftrixRoute, source: String) {
        coroutineScope.launch {
            try {
                val navigationParameters = mapOf(
                    PARAM_ROUTE_TYPE to (route::class.simpleName ?: "Unknown"),
                    PARAM_ROUTE_PARAMS to getRouteParametersAsString(route),
                    PARAM_NAVIGATION_SOURCE to source,
                    "content_type" to "architecture_navigation"
                )
                
                analyticsService.logEvent(
                    eventName = EVENT_NAVIGATION_USAGE,
                    parameters = navigationParameters
                )
                
                Timber.d("Navigation usage tracked: ${route::class.simpleName} from $source")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track navigation usage: ${route::class.simpleName}")
            }
        }
    }
    
    /**
     * Tracks state transitions for performance optimization insights
     * 
     * @param fromState The previous UI state
     * @param toState The new UI state
     */
    fun trackStateTransition(fromState: UiState<*>, toState: UiState<*>) {
        coroutineScope.launch {
            try {
                val transitionType = determineTransitionType(fromState, toState)
                
                val stateParameters = mapOf(
                    PARAM_FROM_STATE_TYPE to getStateTypeName(fromState),
                    PARAM_TO_STATE_TYPE to getStateTypeName(toState),
                    PARAM_STATE_TRANSITION_TYPE to transitionType,
                    PARAM_HAS_DATA to toState.hasData(),
                    PARAM_IS_LOADING to toState.isLoading(),
                    PARAM_CAN_RETRY to toState.canRetry(),
                    "content_type" to "architecture_state"
                )
                
                analyticsService.logEvent(
                    eventName = EVENT_STATE_TRANSITION,
                    parameters = stateParameters
                )
                
                Timber.d("State transition tracked: ${getStateTypeName(fromState)} -> ${getStateTypeName(toState)} ($transitionType)")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track state transition")
            }
        }
    }
    
    /**
     * Tracks comprehensive architecture metrics for development insights
     * 
     * @param metrics Map of architecture metrics to track
     */
    fun trackArchitectureMetrics(metrics: Map<String, Any>) {
        coroutineScope.launch {
            try {
                val metricsParameters = mutableMapOf<String, Any>(
                    "content_type" to "architecture_metrics",
                    PARAM_METRIC_TIMESTAMP to System.currentTimeMillis()
                ).apply {
                    // Add all provided metrics with proper categorization
                    metrics.forEach { (key, value) ->
                        when (value) {
                            is Number -> {
                                put("${PARAM_METRIC_VALUE}_$key", value)
                                put("${PARAM_METRIC_TYPE}_$key", "numeric")
                            }
                            is Boolean -> {
                                put("${PARAM_METRIC_VALUE}_$key", value.toString())
                                put("${PARAM_METRIC_TYPE}_$key", "boolean")
                            }
                            is String -> {
                                put("${PARAM_METRIC_VALUE}_$key", value)
                                put("${PARAM_METRIC_TYPE}_$key", "string")
                            }
                            else -> {
                                put("${PARAM_METRIC_VALUE}_$key", value.toString())
                                put("${PARAM_METRIC_TYPE}_$key", "object")
                            }
                        }
                        
                        // Categorize metrics for better analysis
                        val category = categorizeMetric(key)
                        put("${PARAM_METRIC_CATEGORY}_$key", category)
                    }
                }
                
                analyticsService.logEvent(
                    eventName = EVENT_ARCHITECTURE_METRICS,
                    parameters = metricsParameters
                )
                
                Timber.d("Architecture metrics tracked: ${metrics.size} metrics")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track architecture metrics")
            }
        }
    }
    
    /**
     * Performs comprehensive error pattern analysis for proactive monitoring
     * 
     * @param errorPattern Identified error pattern information
     * @param frequency How often this pattern occurs
     * @param impactLevel The impact level of this error pattern
     */
    fun trackErrorPatternAnalysis(
        errorPattern: String,
        frequency: Int,
        impactLevel: String
    ) {
        coroutineScope.launch {
            try {
                val patternParameters = mapOf(
                    "error_pattern" to errorPattern,
                    "pattern_frequency" to frequency,
                    "impact_level" to impactLevel,
                    "analysis_timestamp" to System.currentTimeMillis(),
                    "content_type" to "architecture_error_pattern"
                )
                
                analyticsService.logEvent(
                    eventName = EVENT_ERROR_PATTERN_ANALYSIS,
                    parameters = patternParameters
                )
                
                Timber.d("Error pattern analysis tracked: $errorPattern (frequency: $frequency, impact: $impactLevel)")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track error pattern analysis")
            }
        }
    }
    
    /**
     * Performs navigation pattern analysis for UX optimization
     * 
     * @param navigationPattern Identified navigation pattern
     * @param usageFrequency How often this pattern is used
     * @param successRate Success rate of this navigation pattern
     */
    fun trackNavigationPatternAnalysis(
        navigationPattern: String,
        usageFrequency: Int,
        successRate: Double
    ) {
        coroutineScope.launch {
            try {
                val patternParameters = mapOf(
                    "navigation_pattern" to navigationPattern,
                    "usage_frequency" to usageFrequency,
                    "success_rate" to successRate,
                    "analysis_timestamp" to System.currentTimeMillis(),
                    "content_type" to "architecture_navigation_pattern"
                )
                
                analyticsService.logEvent(
                    eventName = EVENT_NAVIGATION_PATTERN_ANALYSIS,
                    parameters = patternParameters
                )
                
                Timber.d("Navigation pattern analysis tracked: $navigationPattern (frequency: $usageFrequency, success: $successRate)")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track navigation pattern analysis")
            }
        }
    }
    
    /**
     * Extracts route parameters as a string for analytics
     */
    private fun getRouteParametersAsString(route: LiftrixRoute): String {
        return when (route) {
            is LiftrixRoute.WorkoutDetails -> "workoutId=${route.workoutId}"
            is LiftrixRoute.ExerciseSelection -> "templateId=${route.templateId},isForTemplate=${route.isForTemplate}"
            is LiftrixRoute.ActiveWorkout -> "templateId=${route.templateId},isBlankWorkout=${route.isBlankWorkout}"
            is LiftrixRoute.ExerciseDetails -> "exerciseId=${route.exerciseId}"
            is LiftrixRoute.Home, 
            is LiftrixRoute.Workout, 
            is LiftrixRoute.Progress, 
            is LiftrixRoute.Coach, 
            is LiftrixRoute.Friends,
            is LiftrixRoute.TemplateCreation,
            is LiftrixRoute.Settings,
            is LiftrixRoute.Onboarding -> "no_params"
            else -> "unknown_route"
        }
    }
    
    /**
     * Gets a readable name for UI state types
     */
    private fun getStateTypeName(state: UiState<*>): String {
        return when (state) {
            is UiState.Loading -> "Loading"
            is UiState.Success -> "Success"
            is UiState.Error -> "Error"
            is UiState.Empty -> "Empty"
            else -> "Unknown"
        }
    }
    
    /**
     * Determines the type of state transition
     */
    private fun determineTransitionType(fromState: UiState<*>, toState: UiState<*>): String {
        return when {
            fromState is UiState.Loading && toState is UiState.Success -> "loading_to_success"
            fromState is UiState.Loading && toState is UiState.Error -> "loading_to_error"
            fromState is UiState.Loading && toState is UiState.Empty -> "loading_to_empty"
            fromState is UiState.Success && toState is UiState.Loading -> "success_to_loading"
            fromState is UiState.Success && toState is UiState.Error -> "success_to_error"
            fromState is UiState.Error && toState is UiState.Loading -> "error_to_loading"
            fromState is UiState.Error && toState is UiState.Success -> "error_to_success"
            fromState is UiState.Empty && toState is UiState.Loading -> "empty_to_loading"
            fromState is UiState.Empty && toState is UiState.Success -> "empty_to_success"
            fromState::class == toState::class -> "same_state_update"
            else -> "unknown_transition"
        }
    }
    
    /**
     * Categorizes metrics for better analysis organization
     */
    private fun categorizeMetric(metricKey: String): String {
        return when {
            metricKey.contains("navigation", ignoreCase = true) -> "navigation"
            metricKey.contains("error", ignoreCase = true) -> "error_handling"
            metricKey.contains("state", ignoreCase = true) -> "state_management"
            metricKey.contains("performance", ignoreCase = true) -> "performance"
            metricKey.contains("memory", ignoreCase = true) -> "memory"
            metricKey.contains("time", ignoreCase = true) || metricKey.contains("duration", ignoreCase = true) -> "timing"
            metricKey.contains("count", ignoreCase = true) || metricKey.contains("frequency", ignoreCase = true) -> "frequency"
            metricKey.contains("rate", ignoreCase = true) || metricKey.contains("percent", ignoreCase = true) -> "rate"
            else -> "general"
        }
    }
}