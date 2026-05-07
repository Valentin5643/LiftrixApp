package com.example.liftrix.monitoring

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Architecture Performance Monitor
 * 
 * Monitors performance across architectural components to ensure quality targets:
 * - Navigation performance: <10ms route resolution
 * - Error handling overhead: <5ms processing time
 * - State update performance: <1ms for state transitions
 * - Memory impact: <10MB for abstraction layers
 * 
 * Integrates with Firebase Performance Monitoring and Analytics for comprehensive
 * performance tracking and proactive issue detection.
 */
@Singleton
class ArchitecturePerformanceMonitor @Inject constructor(
    private val firebasePerformance: FirebasePerformance,
    private val analyticsService: AnalyticsService
) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        // Performance targets in milliseconds
        private const val NAVIGATION_TARGET_MS = 10L
        private const val ERROR_HANDLING_TARGET_MS = 5L
        private const val STATE_UPDATE_TARGET_MS = 1L
        
        // Memory target in bytes
        private const val MEMORY_TARGET_BYTES = 10L * 1024L * 1024L // 10MB
        
        // Firebase Performance trace names
        private const val TRACE_NAVIGATION = "liftrix_navigation_performance"
        private const val TRACE_ERROR_HANDLING = "liftrix_error_handling_performance"
        private const val TRACE_STATE_UPDATE = "liftrix_state_update_performance"
        
        // Analytics event names
        private const val EVENT_PERFORMANCE_VIOLATION = "architecture_performance_violation"
        private const val EVENT_PERFORMANCE_METRICS = "architecture_performance_metrics"
    }
    
    /**
     * Tracks navigation performance for type-safe route resolution
     * 
     * @param route The LiftrixRoute being navigated to
     * @param duration Navigation duration in milliseconds
     */
    fun trackNavigationPerformance(route: LiftrixRoute, duration: Long) {
        coroutineScope.launch {
            try {
                // Create Firebase Performance trace
                val trace = firebasePerformance.newTrace(TRACE_NAVIGATION)
                trace.putAttribute("route_type", route::class.simpleName ?: "unknown")
                trace.putMetric("duration_ms", duration)
                trace.start()
                trace.stop()
                
                // Check performance target violation
                if (duration > NAVIGATION_TARGET_MS) {
                    logPerformanceViolation(
                        component = "navigation",
                        target = NAVIGATION_TARGET_MS,
                        actual = duration,
                        context = mapOf(
                            "route_type" to (route::class.simpleName ?: "unknown"),
                            "route_params" to getRouteParams(route)
                        )
                    )
                }
                
                // Log performance metrics for analytics
                analyticsService.logEvent(
                    eventName = EVENT_PERFORMANCE_METRICS,
                    parameters = mapOf(
                        "component" to "navigation",
                        "route_type" to (route::class.simpleName ?: "unknown"),
                        "duration_ms" to duration,
                        "meets_target" to (duration <= NAVIGATION_TARGET_MS)
                    )
                )
                
                Timber.d("Navigation performance: ${route::class.simpleName} took ${duration}ms")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track navigation performance")
            }
        }
    }
    
    /**
     * Tracks error handling performance to ensure minimal overhead
     * 
     * @param error The LiftrixError being processed
     * @param processingTime Error handling duration in milliseconds
     */
    fun trackErrorHandlingOverhead(error: LiftrixError, processingTime: Long) {
        coroutineScope.launch {
            try {
                // Create Firebase Performance trace
                val trace = firebasePerformance.newTrace(TRACE_ERROR_HANDLING)
                trace.putAttribute("error_type", error::class.simpleName ?: "unknown")
                trace.putAttribute("is_recoverable", error.isRecoverable.toString())
                trace.putMetric("processing_time_ms", processingTime)
                trace.start()
                trace.stop()
                
                // Check performance target violation
                if (processingTime > ERROR_HANDLING_TARGET_MS) {
                    logPerformanceViolation(
                        component = "error_handling",
                        target = ERROR_HANDLING_TARGET_MS,
                        actual = processingTime,
                        context = mapOf(
                            "error_type" to (error::class.simpleName ?: "unknown"),
                            "is_recoverable" to error.isRecoverable.toString(),
                            "has_retry_after" to (error.retryAfter != null).toString()
                        )
                    )
                }
                
                // Log performance metrics for analytics
                analyticsService.logEvent(
                    eventName = EVENT_PERFORMANCE_METRICS,
                    parameters = mapOf(
                        "component" to "error_handling",
                        "error_type" to (error::class.simpleName ?: "unknown"),
                        "processing_time_ms" to processingTime,
                        "is_recoverable" to error.isRecoverable,
                        "meets_target" to (processingTime <= ERROR_HANDLING_TARGET_MS)
                    )
                )
                
                Timber.d("Error handling performance: ${error::class.simpleName} took ${processingTime}ms")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track error handling performance")
            }
        }
    }
    
    /**
     * Tracks state update performance for MVI pattern compliance
     * 
     * @param stateType The type of state being updated (ViewModel class name)
     * @param duration State update duration in milliseconds
     */
    fun trackStateUpdatePerformance(stateType: String, duration: Long) {
        coroutineScope.launch {
            try {
                // Create Firebase Performance trace
                val trace = firebasePerformance.newTrace(TRACE_STATE_UPDATE)
                trace.putAttribute("state_type", stateType)
                trace.putMetric("duration_ms", duration)
                trace.start()
                trace.stop()
                
                // Check performance target violation
                if (duration > STATE_UPDATE_TARGET_MS) {
                    logPerformanceViolation(
                        component = "state_update",
                        target = STATE_UPDATE_TARGET_MS,
                        actual = duration,
                        context = mapOf(
                            "state_type" to stateType
                        )
                    )
                }
                
                // Log performance metrics for analytics
                analyticsService.logEvent(
                    eventName = EVENT_PERFORMANCE_METRICS,
                    parameters = mapOf(
                        "component" to "state_update",
                        "state_type" to stateType,
                        "duration_ms" to duration,
                        "meets_target" to (duration <= STATE_UPDATE_TARGET_MS)
                    )
                )
                
                Timber.d("State update performance: $stateType took ${duration}ms")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track state update performance")
            }
        }
    }
    
    /**
     * Reports comprehensive performance metrics for dashboard analysis
     */
    fun reportPerformanceMetrics() {
        coroutineScope.launch {
            try {
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                
                // Memory performance analysis
                val memoryMetrics = mapOf(
                    "used_memory_mb" to (usedMemory / (1024 * 1024)),
                    "max_memory_mb" to (maxMemory / (1024 * 1024)),
                    "memory_usage_percent" to ((usedMemory.toDouble() / maxMemory) * 100).toInt(),
                    "meets_memory_target" to (usedMemory <= MEMORY_TARGET_BYTES)
                )
                
                // Log comprehensive metrics
                analyticsService.logEvent(
                    eventName = "architecture_performance_report",
                    parameters = memoryMetrics + mapOf(
                        "navigation_target_ms" to NAVIGATION_TARGET_MS,
                        "error_handling_target_ms" to ERROR_HANDLING_TARGET_MS,
                        "state_update_target_ms" to STATE_UPDATE_TARGET_MS,
                        "memory_target_mb" to (MEMORY_TARGET_BYTES / (1024 * 1024))
                    )
                )
                
                Timber.i("Performance metrics reported: memory=${usedMemory / (1024 * 1024)}MB")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to report performance metrics")
            }
        }
    }
    
    /**
     * Logs performance target violations for proactive monitoring
     */
    private suspend fun logPerformanceViolation(
        component: String,
        target: Long,
        actual: Long,
        context: Map<String, String>
    ) {
        try {
            analyticsService.logEvent(
                eventName = EVENT_PERFORMANCE_VIOLATION,
                parameters = mapOf(
                    "component" to component,
                    "target_ms" to target,
                    "actual_ms" to actual,
                    "violation_percent" to ((actual.toDouble() / target) * 100).toInt()
                ) + context
            )
            
            Timber.w("Performance violation: $component exceeded ${target}ms target (actual: ${actual}ms)")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to log performance violation")
        }
    }
    
    /**
     * Extracts route parameters for analytics context
     */
    private fun getRouteParams(route: LiftrixRoute): String {
        return when (route) {
            is LiftrixRoute.WorkoutDetails -> "workoutId=${route.workoutId}"
            is LiftrixRoute.ExerciseSelection -> "templateId=${route.templateId},isForTemplate=${route.isForTemplate}"
            is LiftrixRoute.ActiveWorkout -> "templateId=${route.templateId},isBlankWorkout=${route.isBlankWorkout}"
            is LiftrixRoute.ExerciseDetails -> "exerciseId=${route.exerciseId}"
            else -> "no_params"
        }
    }
}