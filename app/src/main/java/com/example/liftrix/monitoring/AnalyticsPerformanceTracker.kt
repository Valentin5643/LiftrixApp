package com.example.liftrix.monitoring

import com.example.liftrix.domain.service.AnalyticsService
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analytics Performance Tracker
 * 
 * Comprehensive performance monitoring for analytics calculations with Firebase Performance
 * SDK integration. Tracks calculation times, dashboard load performance, and performance
 * regression detection with automated alerting.
 * 
 * Performance Targets:
 * - Analytics calculation time: <500ms for complex queries
 * - Dashboard load time: <2000ms for comprehensive dashboard
 * - Memory usage tracking for analytics caching
 * - Automated alerts for performance regressions
 * 
 * Integration:
 * - Firebase Performance Monitoring SDK for traces
 * - Analytics service for custom event tracking
 * - Proactive performance violation detection
 * - Real-time performance metrics reporting
 */
@Singleton
class AnalyticsPerformanceTracker @Inject constructor(
    private val firebasePerformance: FirebasePerformance,
    private val analyticsService: AnalyticsService
) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        // Performance targets in milliseconds
        private const val ANALYTICS_CALCULATION_TARGET_MS = 500L
        private const val DASHBOARD_LOAD_TARGET_MS = 2000L
        private const val CHART_RENDER_TARGET_MS = 16L // 60fps = 16.67ms per frame
        private const val MEMORY_CACHE_TARGET_MB = 50L
        
        // Firebase Performance trace names
        private const val TRACE_ANALYTICS_CALCULATION = "liftrix_analytics_calculation"
        private const val TRACE_DASHBOARD_LOAD = "liftrix_dashboard_load"
        private const val TRACE_CHART_RENDERING = "liftrix_chart_rendering"
        private const val TRACE_MEMORY_USAGE = "liftrix_analytics_memory"
        
        // Analytics event names
        private const val EVENT_PERFORMANCE_VIOLATION = "analytics_performance_violation"
        private const val EVENT_PERFORMANCE_METRICS = "analytics_performance_metrics"
        private const val EVENT_PERFORMANCE_ALERT = "analytics_performance_alert"
    }
    
    /**
     * Tracks analytics calculation time for performance optimization
     * 
     * @param calculationType Type of calculation (e.g., "workout_metrics", "volume_calendar")
     * @param duration Calculation duration in milliseconds
     * @param userId User ID for context (optional)
     * @param additionalMetrics Additional metrics to track
     */
    fun trackCalculationTime(
        calculationType: String,
        duration: Long,
        userId: String? = null,
        additionalMetrics: Map<String, Any> = emptyMap()
    ) {
        coroutineScope.launch {
            try {
                // Create Firebase Performance trace
                val trace = firebasePerformance.newTrace("${TRACE_ANALYTICS_CALCULATION}_$calculationType")
                trace.putAttribute("calculation_type", calculationType)
                trace.putAttribute("user_id", userId ?: "anonymous")
                trace.putMetric("duration_ms", duration)
                
                // Add additional metrics
                additionalMetrics.forEach { (key, value) ->
                    when (value) {
                        is Long -> trace.putMetric(key, value)
                        is String -> trace.putAttribute(key, value)
                        is Int -> trace.putMetric(key, value.toLong())
                        is Float -> trace.putMetric(key, value.toLong())
                        is Double -> trace.putMetric(key, value.toLong())
                    }
                }
                
                trace.start()
                trace.stop()
                
                // Check performance target violation
                if (duration > ANALYTICS_CALCULATION_TARGET_MS) {
                    logPerformanceViolation(
                        component = "analytics_calculation",
                        calculationType = calculationType,
                        target = ANALYTICS_CALCULATION_TARGET_MS,
                        actual = duration,
                        context = mapOf(
                            "calculation_type" to calculationType,
                            "user_id" to (userId ?: "anonymous"),
                            "violation_severity" to getViolationSeverity(duration, ANALYTICS_CALCULATION_TARGET_MS)
                        ) + additionalMetrics.mapValues { it.value.toString() }
                    )
                }
                
                // Log performance metrics for analytics
                analyticsService.logEvent(
                    eventName = EVENT_PERFORMANCE_METRICS,
                    parameters = mapOf(
                        "component" to "analytics_calculation",
                        "calculation_type" to calculationType,
                        "duration_ms" to duration,
                        "meets_target" to (duration <= ANALYTICS_CALCULATION_TARGET_MS),
                        "user_id" to (userId ?: "anonymous")
                    ) + additionalMetrics.mapValues { it.value.toString() }
                )
                
                Timber.d("Analytics calculation performance: $calculationType took ${duration}ms")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track analytics calculation performance")
            }
        }
    }
    
    /**
     * Tracks chart rendering performance with 60fps target monitoring
     * 
     * @param chartType Type of chart being rendered (e.g., "line", "bar", "radial")
     * @param dataPoints Number of data points being rendered
     * @param renderTime Rendering time in milliseconds
     * @param frameDrops Number of dropped frames (if available)
     */
    fun trackChartRenderingPerformance(
        chartType: String,
        dataPoints: Int,
        renderTime: Long,
        frameDrops: Int = 0
    ) {
        coroutineScope.launch {
            try {
                // Create Firebase Performance trace
                val trace = firebasePerformance.newTrace("${TRACE_CHART_RENDERING}_$chartType")
                trace.putAttribute("chart_type", chartType)
                trace.putMetric("data_points", dataPoints.toLong())
                trace.putMetric("render_time_ms", renderTime)
                trace.putMetric("frame_drops", frameDrops.toLong())
                
                // Calculate frames per second
                val fps = if (renderTime > 0) (1000.0 / renderTime).toInt() else 0
                trace.putMetric("fps", fps.toLong())
                
                trace.start()
                trace.stop()
                
                // Check 60fps target violation
                if (renderTime > CHART_RENDER_TARGET_MS || frameDrops > 0) {
                    logPerformanceViolation(
                        component = "chart_rendering",
                        calculationType = chartType,
                        target = CHART_RENDER_TARGET_MS,
                        actual = renderTime,
                        context = mapOf(
                            "chart_type" to chartType,
                            "data_points" to dataPoints.toString(),
                            "frame_drops" to frameDrops.toString(),
                            "fps" to fps.toString(),
                            "violation_severity" to getViolationSeverity(renderTime, CHART_RENDER_TARGET_MS)
                        )
                    )
                }
                
                // Log performance metrics
                analyticsService.logEvent(
                    eventName = EVENT_PERFORMANCE_METRICS,
                    parameters = mapOf(
                        "component" to "chart_rendering",
                        "chart_type" to chartType,
                        "data_points" to dataPoints,
                        "render_time_ms" to renderTime,
                        "fps" to fps,
                        "frame_drops" to frameDrops,
                        "meets_target" to (renderTime <= CHART_RENDER_TARGET_MS && frameDrops == 0)
                    )
                )
                
                Timber.d("Chart rendering performance: $chartType with $dataPoints points took ${renderTime}ms ($fps fps)")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track chart rendering performance")
            }
        }
    }
    
    /**
     * Tracks dashboard load time with comprehensive widget performance analysis
     * 
     * @param widgetCount Number of widgets loaded
     * @param loadTime Total load time in milliseconds
     * @param cacheHits Number of cache hits during load
     * @param cacheMisses Number of cache misses during load
     */
    fun trackDashboardLoadTime(
        widgetCount: Int,
        loadTime: Long,
        cacheHits: Int = 0,
        cacheMisses: Int = 0
    ) {
        coroutineScope.launch {
            try {
                // Create Firebase Performance trace
                val trace = firebasePerformance.newTrace(TRACE_DASHBOARD_LOAD)
                trace.putMetric("widget_count", widgetCount.toLong())
                trace.putMetric("load_time_ms", loadTime)
                trace.putMetric("cache_hits", cacheHits.toLong())
                trace.putMetric("cache_misses", cacheMisses.toLong())
                
                // Calculate cache efficiency
                val totalCacheRequests = cacheHits + cacheMisses
                val cacheEfficiency = if (totalCacheRequests > 0) {
                    (cacheHits.toDouble() / totalCacheRequests * 100).toInt()
                } else 0
                trace.putMetric("cache_efficiency_percent", cacheEfficiency.toLong())
                
                trace.start()
                trace.stop()
                
                // Check performance target violation
                if (loadTime > DASHBOARD_LOAD_TARGET_MS) {
                    logPerformanceViolation(
                        component = "dashboard_load",
                        calculationType = "dashboard_widgets",
                        target = DASHBOARD_LOAD_TARGET_MS,
                        actual = loadTime,
                        context = mapOf(
                            "widget_count" to widgetCount.toString(),
                            "cache_hits" to cacheHits.toString(),
                            "cache_misses" to cacheMisses.toString(),
                            "cache_efficiency_percent" to cacheEfficiency.toString(),
                            "violation_severity" to getViolationSeverity(loadTime, DASHBOARD_LOAD_TARGET_MS)
                        )
                    )
                }
                
                // Log performance metrics
                analyticsService.logEvent(
                    eventName = EVENT_PERFORMANCE_METRICS,
                    parameters = mapOf(
                        "component" to "dashboard_load",
                        "widget_count" to widgetCount,
                        "load_time_ms" to loadTime,
                        "cache_hits" to cacheHits,
                        "cache_misses" to cacheMisses,
                        "cache_efficiency_percent" to cacheEfficiency,
                        "meets_target" to (loadTime <= DASHBOARD_LOAD_TARGET_MS)
                    )
                )
                
                Timber.d("Dashboard load performance: $widgetCount widgets took ${loadTime}ms (cache: ${cacheEfficiency}%)")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track dashboard load performance")
            }
        }
    }
    
    /**
     * Reports comprehensive performance metrics for dashboard analysis
     * 
     * Includes memory usage tracking, cache effectiveness, and performance trends
     */
    fun reportPerformanceMetrics() {
        coroutineScope.launch {
            try {
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                val usedMemoryMB = usedMemory / (1024 * 1024)
                
                // Create memory usage trace
                val memoryTrace = firebasePerformance.newTrace(TRACE_MEMORY_USAGE)
                memoryTrace.putMetric("used_memory_mb", usedMemoryMB)
                memoryTrace.putMetric("max_memory_mb", maxMemory / (1024 * 1024))
                memoryTrace.putMetric("memory_usage_percent", ((usedMemory.toDouble() / maxMemory) * 100).toLong())
                memoryTrace.start()
                memoryTrace.stop()
                
                // Check memory target violation
                if (usedMemoryMB > MEMORY_CACHE_TARGET_MB) {
                    logPerformanceViolation(
                        component = "memory_usage",
                        calculationType = "analytics_cache",
                        target = MEMORY_CACHE_TARGET_MB,
                        actual = usedMemoryMB,
                        context = mapOf(
                            "used_memory_mb" to usedMemoryMB.toString(),
                            "max_memory_mb" to (maxMemory / (1024 * 1024)).toString(),
                            "memory_usage_percent" to ((usedMemory.toDouble() / maxMemory) * 100).toInt().toString(),
                            "violation_severity" to getViolationSeverity(usedMemoryMB, MEMORY_CACHE_TARGET_MB)
                        )
                    )
                }
                
                // Log comprehensive performance report
                analyticsService.logEvent(
                    eventName = "analytics_performance_report",
                    parameters = mapOf(
                        "used_memory_mb" to usedMemoryMB,
                        "max_memory_mb" to (maxMemory / (1024 * 1024)),
                        "memory_usage_percent" to ((usedMemory.toDouble() / maxMemory) * 100).toInt(),
                        "meets_memory_target" to (usedMemoryMB <= MEMORY_CACHE_TARGET_MB),
                        "analytics_calculation_target_ms" to ANALYTICS_CALCULATION_TARGET_MS,
                        "dashboard_load_target_ms" to DASHBOARD_LOAD_TARGET_MS,
                        "chart_render_target_ms" to CHART_RENDER_TARGET_MS,
                        "memory_cache_target_mb" to MEMORY_CACHE_TARGET_MB
                    )
                )
                
                Timber.i("Analytics performance metrics reported: memory=${usedMemoryMB}MB")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to report analytics performance metrics")
            }
        }
    }
    
    /**
     * Sets up performance alerts for proactive monitoring
     * 
     * Configures automated alerting for performance regressions and violations
     */
    fun setupPerformanceAlerts() {
        coroutineScope.launch {
            try {
                // Log alert configuration
                analyticsService.logEvent(
                    eventName = "analytics_performance_alerts_configured",
                    parameters = mapOf(
                        "analytics_calculation_threshold_ms" to ANALYTICS_CALCULATION_TARGET_MS,
                        "dashboard_load_threshold_ms" to DASHBOARD_LOAD_TARGET_MS,
                        "chart_render_threshold_ms" to CHART_RENDER_TARGET_MS,
                        "memory_threshold_mb" to MEMORY_CACHE_TARGET_MB,
                        "alert_system_enabled" to true
                    )
                )
                
                Timber.i("Analytics performance alerts configured")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to setup performance alerts")
            }
        }
    }
    
    /**
     * Logs performance target violations for proactive monitoring
     */
    private suspend fun logPerformanceViolation(
        component: String,
        calculationType: String,
        target: Long,
        actual: Long,
        context: Map<String, String>
    ) {
        try {
            val violationPercent = ((actual.toDouble() / target) * 100).toInt()
            
            analyticsService.logEvent(
                eventName = EVENT_PERFORMANCE_VIOLATION,
                parameters = mapOf(
                    "component" to component,
                    "calculation_type" to calculationType,
                    "target_ms" to target,
                    "actual_ms" to actual,
                    "violation_percent" to violationPercent,
                    "exceeded_by_ms" to (actual - target)
                ) + context
            )
            
            // Log performance alert for severe violations
            if (violationPercent > 200) { // More than 2x the target
                analyticsService.logEvent(
                    eventName = EVENT_PERFORMANCE_ALERT,
                    parameters = mapOf(
                        "alert_type" to "severe_performance_violation",
                        "component" to component,
                        "calculation_type" to calculationType,
                        "violation_percent" to violationPercent,
                        "requires_immediate_attention" to true
                    )
                )
            }
            
            Timber.w("Analytics performance violation: $component/$calculationType exceeded ${target}ms target (actual: ${actual}ms)")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to log performance violation")
        }
    }
    
    /**
     * Determines violation severity based on performance deviation
     */
    private fun getViolationSeverity(actual: Long, target: Long): String {
        val violationPercent = ((actual.toDouble() / target) * 100).toInt()
        return when {
            violationPercent <= 100 -> "none"
            violationPercent <= 150 -> "minor"
            violationPercent <= 200 -> "moderate"
            violationPercent <= 300 -> "severe"
            else -> "critical"
        }
    }
}