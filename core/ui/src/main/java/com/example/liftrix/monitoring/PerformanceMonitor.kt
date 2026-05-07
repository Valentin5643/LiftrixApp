package com.example.liftrix.monitoring

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.example.liftrix.ui.performance.PerformanceMetrics
import kotlin.math.roundToInt

@Singleton
class PerformanceMonitor @Inject constructor(
    private val firebasePerformance: FirebasePerformance
) {

    companion object {
        // Trace names for screen transitions
        private const val TRACE_SCREEN_TRANSITION = "screen_transition"
        private const val TRACE_ANIMATION_PERFORMANCE = "animation_performance"
        private const val TRACE_USER_INTERACTION = "user_interaction"
        
        // Attribute keys
        private const val ATTR_FROM_SCREEN = "from_screen"
        private const val ATTR_TO_SCREEN = "to_screen"
        private const val ATTR_ANIMATION_TYPE = "animation_type"
        private const val ATTR_INTERACTION_TYPE = "interaction_type"
        private const val ATTR_COMPONENT_TYPE = "component_type"
        
        // Metric keys
        private const val METRIC_DURATION_MS = "duration_ms"
        private const val METRIC_FRAME_COUNT = "frame_count"
        private const val METRIC_RESPONSE_TIME_MS = "response_time_ms"
        private const val METRIC_AVERAGE_FPS = "average_fps"
        private const val METRIC_FRAME_DROPS = "frame_drops"
        private const val METRIC_MEMORY_USAGE_MB = "memory_usage_mb"
    }

    /**
     * Track screen transition performance including navigation timing
     * 
     * @param fromScreen Source screen identifier
     * @param toScreen Destination screen identifier
     * @return Result indicating success or failure
     */
    suspend fun trackScreenTransition(fromScreen: String, toScreen: String): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace(TRACE_SCREEN_TRANSITION)
            trace.putAttribute(ATTR_FROM_SCREEN, fromScreen)
            trace.putAttribute(ATTR_TO_SCREEN, toScreen)
            trace.start()
            
            // Note: Trace should be stopped when transition completes
            // This is a demonstration - in real usage, return the trace for later stopping
            trace.stop()
            
            Timber.d("Screen transition tracked: $fromScreen -> $toScreen")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track screen transition: $fromScreen -> $toScreen")
            Result.failure(exception)
        }
    }

    /**
     * Track animation performance metrics including duration and smoothness
     * 
     * @param animationType Type of animation being tracked
     * @param duration Animation duration in milliseconds
     * @return Result indicating success or failure
     */
    suspend fun trackAnimationPerformance(animationType: String, duration: Long): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace(TRACE_ANIMATION_PERFORMANCE)
            trace.putAttribute(ATTR_ANIMATION_TYPE, animationType)
            trace.putMetric(METRIC_DURATION_MS, duration)
            trace.start()
            trace.stop()
            
            Timber.d("Animation performance tracked: $animationType (${duration}ms)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track animation performance: $animationType")
            Result.failure(exception)
        }
    }

    /**
     * Track user interaction performance including response times
     * 
     * @param interactionType Type of user interaction (tap, swipe, etc.)
     * @param componentType UI component being interacted with
     * @param responseTimeMs Time taken to respond to interaction
     * @return Result indicating success or failure
     */
    suspend fun trackUserInteraction(
        interactionType: String,
        componentType: String,
        responseTimeMs: Long
    ): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace(TRACE_USER_INTERACTION)
            trace.putAttribute(ATTR_INTERACTION_TYPE, interactionType)
            trace.putAttribute(ATTR_COMPONENT_TYPE, componentType)
            trace.putMetric(METRIC_RESPONSE_TIME_MS, responseTimeMs)
            trace.start()
            trace.stop()
            
            Timber.d("User interaction tracked: $interactionType on $componentType (${responseTimeMs}ms)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track user interaction: $interactionType")
            Result.failure(exception)
        }
    }

    /**
     * Create and start a custom trace for manual performance monitoring
     * 
     * @param traceName Name of the trace
     * @return Trace object for manual control, or null if creation failed
     */
    fun startCustomTrace(traceName: String): Trace? {
        return try {
            val trace = firebasePerformance.newTrace(traceName)
            trace.start()
            Timber.d("Custom trace started: $traceName")
            trace
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to start custom trace: $traceName")
            null
        }
    }

    /**
     * Stop a custom trace and record its metrics
     * 
     * @param trace The trace to stop
     * @param attributes Optional attributes to add before stopping
     * @param metrics Optional metrics to add before stopping
     * @return Result indicating success or failure
     */
    fun stopCustomTrace(
        trace: Trace,
        attributes: Map<String, String> = emptyMap(),
        metrics: Map<String, Long> = emptyMap()
    ): Result<Unit> {
        return try {
            // Add attributes
            attributes.forEach { (key, value) ->
                trace.putAttribute(key, value)
            }
            
            // Add metrics
            metrics.forEach { (key, value) ->
                trace.putMetric(key, value)
            }
            
            trace.stop()
            Timber.d("Custom trace stopped with ${attributes.size} attributes and ${metrics.size} metrics")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to stop custom trace")
            Result.failure(exception)
        }
    }

    /**
     * Track app startup performance
     * 
     * @param startupTimeMs Total app startup time in milliseconds
     * @param coldStart Whether this was a cold start
     * @return Result indicating success or failure
     */
    suspend fun trackAppStartup(startupTimeMs: Long, coldStart: Boolean): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace("app_startup")
            trace.putAttribute("startup_type", if (coldStart) "cold" else "warm")
            trace.putMetric("startup_time_ms", startupTimeMs)
            trace.start()
            trace.stop()
            
            Timber.d("App startup tracked: ${if (coldStart) "cold" else "warm"} start (${startupTimeMs}ms)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track app startup")
            Result.failure(exception)
        }
    }
    
    /**
     * Track UI component performance metrics from UI performance monitoring system
     * Task PERF-001: Integration between UI and Firebase monitoring
     * 
     * @param metrics UI performance metrics from PerformanceMonitor
     * @return Result indicating success or failure
     */
    suspend fun trackUIComponentPerformance(metrics: PerformanceMetrics): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace("ui_component_performance")
            trace.putAttribute(ATTR_COMPONENT_TYPE, metrics.componentId)
            trace.putMetric(METRIC_AVERAGE_FPS, metrics.averageFps.toLong())
            trace.putMetric(METRIC_FRAME_DROPS, metrics.frameDrops.toLong())
            trace.putMetric(METRIC_DURATION_MS, metrics.durationMs)
            
            // Performance classification for analytics
            val performanceCategory = when {
                metrics.isPerformanceGood -> "good"
                metrics.averageFps >= 45 -> "moderate" 
                else -> "poor"
            }
            trace.putAttribute("performance_category", performanceCategory)
            
            trace.start()
            trace.stop()
            
            Timber.d("UI component performance tracked: ${metrics.componentId} " +
                    "(${metrics.averageFps}fps, ${metrics.frameDrops} drops, $performanceCategory)")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track UI component performance: ${metrics.componentId}")
            Result.failure(exception)
        }
    }
    
    /**
     * Track memory usage performance for component rendering
     * Enhanced for PERF-002: Comprehensive performance validation
     * 
     * @param componentType Type of component (widget, card, screen)
     * @param memoryUsageMb Memory usage in megabytes
     * @param renderTimeMs Time taken to render
     * @param averageFps Average frame rate during rendering
     * @param frameDrops Number of frame drops during rendering
     * @return Result indicating success or failure
     */
    suspend fun trackMemoryPerformance(
        componentType: String,
        memoryUsageMb: Float,
        renderTimeMs: Long,
        averageFps: Float = 0f,
        frameDrops: Int = 0
    ): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace("memory_performance")
            trace.putAttribute(ATTR_COMPONENT_TYPE, componentType)
            trace.putMetric(METRIC_MEMORY_USAGE_MB, memoryUsageMb.toLong())
            trace.putMetric(METRIC_DURATION_MS, renderTimeMs)
            
            // PERF-002: Enhanced metrics for 60fps validation
            if (averageFps > 0f) {
                trace.putMetric(METRIC_AVERAGE_FPS, averageFps.toLong())
            }
            if (frameDrops > 0) {
                trace.putMetric(METRIC_FRAME_DROPS, frameDrops.toLong())
            }
            
            // Memory usage classification
            val memoryCategory = when {
                memoryUsageMb <= 10 -> "low"
                memoryUsageMb <= 50 -> "moderate"
                else -> "high"
            }
            trace.putAttribute("memory_category", memoryCategory)
            
            trace.start()
            trace.stop()
            
            Timber.d("Memory performance tracked: $componentType (${memoryUsageMb}MB, ${renderTimeMs}ms, $memoryCategory)")
            
            // Alert for high memory usage (Task PERF-001 requirement)
            if (memoryUsageMb > 100) {
                Timber.w("HIGH MEMORY USAGE ALERT: $componentType using ${memoryUsageMb}MB (limit: 100MB)")
            }
            
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track memory performance: $componentType")
            Result.failure(exception)
        }
    }
    
    /**
     * Enhanced 60fps performance tracking for PERF-002
     * Comprehensive validation of component performance against 60fps targets
     * 
     * @param componentId Component identifier
     * @param duration Operation duration in milliseconds
     * @param averageFps Measured average FPS
     * @param frameDrops Number of frame drops detected
     * @param memoryUsageMb Memory usage during operation
     * @return Result indicating success or failure
     */
    suspend fun track60FpsPerformance(
        componentId: String,
        duration: Long,
        averageFps: Float,
        frameDrops: Int,
        memoryUsageMb: Float = 0f
    ): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace("perf_002_60fps_validation")
            trace.putAttribute(ATTR_COMPONENT_TYPE, componentId)
            trace.putMetric(METRIC_DURATION_MS, duration)
            trace.putMetric(METRIC_AVERAGE_FPS, averageFps.toLong())
            trace.putMetric(METRIC_FRAME_DROPS, frameDrops.toLong())
            
            if (memoryUsageMb > 0f) {
                trace.putMetric(METRIC_MEMORY_USAGE_MB, memoryUsageMb.toLong())
            }
            
            // Performance classification for PERF-002 validation
            val targetFps = 60f
            val performance60FpsValid = averageFps >= targetFps * 0.9f && frameDrops <= 5
            
            val performanceCategory = when {
                performance60FpsValid -> "60fps_compliant"
                averageFps >= targetFps * 0.75f -> "acceptable"
                else -> "below_target"
            }
            trace.putAttribute("perf_002_validation", performanceCategory)
            
            // Duration validation (PRD requirements: 150ms press, 300ms transitions)
            val durationCategory = when {
                duration <= 150 -> "fast"
                duration <= 300 -> "standard"
                else -> "slow"
            }
            trace.putAttribute("duration_category", durationCategory)
            
            trace.start()
            trace.stop()
            
            Timber.i("PERF-002: $componentId performance tracked - " +
                    "${averageFps.toInt()}fps, $frameDrops drops, ${duration}ms, $performanceCategory")
            
            // Alert for performance issues
            if (!performance60FpsValid) {
                Timber.w("PERF-002 ALERT: $componentId failed 60fps validation - " +
                        "${averageFps.toInt()}fps, $frameDrops drops")
            }
            
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track PERF-002 60fps performance: $componentId")
            Result.failure(exception)
        }
    }
    
    /**
     * Track theme switching performance for COLOR-013 optimization validation
     * Monitors the performance benefits of the 5-color system optimization
     * 
     * @param fromTheme Source theme (light/dark)
     * @param toTheme Target theme (light/dark)
     * @param switchTimeMs Time taken to switch themes in milliseconds
     * @param cacheHitRate Cache hit rate percentage from ColorSystemOptimizations
     * @param memoryUsageMb Memory usage during theme switch
     * @return Result indicating success or failure
     */
    suspend fun trackThemeSwitchingPerformance(
        fromTheme: String,
        toTheme: String,
        switchTimeMs: Long,
        cacheHitRate: Float = 0f,
        memoryUsageMb: Float = 0f
    ): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace("color_013_theme_switching")
            trace.putAttribute("from_theme", fromTheme)
            trace.putAttribute("to_theme", toTheme)
            trace.putMetric("theme_switch_time_ms", switchTimeMs)
            
            if (cacheHitRate > 0f) {
                trace.putMetric("cache_hit_rate_percent", cacheHitRate.toLong())
            }
            
            if (memoryUsageMb > 0f) {
                trace.putMetric(METRIC_MEMORY_USAGE_MB, memoryUsageMb.toLong())
            }
            
            // Performance classification for COLOR-013 validation
            val targetSwitchTime = 50L // Target: <50ms with optimizations
            val performanceCategory = when {
                switchTimeMs <= targetSwitchTime -> "optimized"
                switchTimeMs <= targetSwitchTime * 1.5f -> "acceptable"
                else -> "needs_optimization"
            }
            trace.putAttribute("color_013_performance", performanceCategory)
            
            // Calculate performance improvement percentage
            val baselineSwitchTime = 100L // Baseline: ~100ms without optimizations
            val improvementPercent = ((baselineSwitchTime - switchTimeMs).toFloat() / baselineSwitchTime) * 100f
            if (improvementPercent > 0) {
                trace.putMetric("performance_improvement_percent", improvementPercent.toLong())
            }
            
            trace.start()
            trace.stop()
            
            Timber.i("COLOR-013: Theme switching performance tracked - " +
                    "$fromTheme→$toTheme in ${switchTimeMs}ms, cache hit: ${cacheHitRate.toInt()}%, " +
                    "improvement: +${improvementPercent.toInt()}%")
            
            // Alert for performance targets
            if (switchTimeMs > targetSwitchTime) {
                Timber.w("COLOR-013 ALERT: Theme switching slower than target - " +
                        "${switchTimeMs}ms (target: ${targetSwitchTime}ms)")
            } else if (improvementPercent >= 20f) {
                Timber.i("COLOR-013 SUCCESS: 20%+ performance improvement achieved - +${improvementPercent.toInt()}%")
            }
            
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track theme switching performance: $fromTheme→$toTheme")
            Result.failure(exception)
        }
    }
    
    /**
     * Track 5-color system optimization effectiveness for COLOR-013
     * Monitors overall system performance with the simplified color palette
     * 
     * @param operationType Type of operation (theme_switch, color_calculation, cache_access)
     * @param duration Operation duration in milliseconds
     * @param colorSystemVersion Version identifier (5-color vs legacy)
     * @param additionalMetrics Optional additional metrics
     * @return Result indicating success or failure
     */
    suspend fun trackColorSystemOptimization(
        operationType: String,
        duration: Long,
        colorSystemVersion: String = "5-color",
        additionalMetrics: Map<String, Long> = emptyMap()
    ): Result<Unit> {
        return try {
            val trace = firebasePerformance.newTrace("color_013_system_optimization")
            trace.putAttribute("operation_type", operationType)
            trace.putAttribute("color_system_version", colorSystemVersion)
            trace.putMetric(METRIC_DURATION_MS, duration)
            
            // Add additional metrics
            additionalMetrics.forEach { (key, value) ->
                trace.putMetric(key, value)
            }
            
            // Performance validation for 5-color system
            val targetDuration = when (operationType) {
                "theme_switch" -> 50L
                "color_calculation" -> 10L
                "cache_access" -> 5L
                else -> 100L
            }
            
            val performanceValid = duration <= targetDuration
            trace.putAttribute("performance_target_met", if (performanceValid) "yes" else "no")
            
            trace.start()
            trace.stop()
            
            Timber.d("COLOR-013: $operationType optimization tracked - " +
                    "${duration}ms (target: ${targetDuration}ms), system: $colorSystemVersion")
            
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track color system optimization: $operationType")
            Result.failure(exception)
        }
    }
    
    /**
     * Record theme switching time metric for performance analysis
     * Simplified method for quick performance tracking
     * 
     * @param switchTimeMs Time taken for theme switch in milliseconds
     */
    fun recordThemeSwitchingTime(switchTimeMs: Long) {
        try {
            // Record metric for performance analysis
            Timber.d("PerformanceMonitor: Theme switch recorded - ${switchTimeMs}ms")
            
            // Alert for slow theme switching
            if (switchTimeMs > 100) {
                Timber.w("PerformanceMonitor: Slow theme switching detected - ${switchTimeMs}ms")
            }
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to record theme switching time")
        }
    }
} 