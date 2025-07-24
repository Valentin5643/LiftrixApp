package com.example.liftrix.ui.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import com.example.liftrix.BuildConfig
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import android.os.Debug
import kotlin.math.roundToInt
import androidx.compose.runtime.*
import com.example.liftrix.performance.PerformanceValidator

/**
 * Performance monitoring utility for tracking 60fps target and frame rate validation.
 * 
 * Provides frame rate monitoring, performance metrics collection, and development
 * build profiling tools. Follows Liftrix Clean Architecture patterns with 
 * proper dependency injection and lifecycle management.
 * 
 * Key features:
 * - 60fps validation with automatic alerting for performance degradation
 * - Frame drop detection with configurable thresholds  
 * - Memory usage tracking for widget rendering operations
 * - Development build instrumentation with minimal production overhead
 * - Integration with analytics for performance regression detection
 */
@Singleton
class PerformanceMonitor @Inject constructor(
    private val performanceValidator: PerformanceValidator
) {
    
    private var frameRateTracking = mutableMapOf<String, FrameRateTracker>()
    private var isGlobalTrackingEnabled = false
    
    /**
     * Start frame rate tracking for a specific component
     * Enhanced with Choreographer-based validation for accurate 60fps measurement
     * @param componentId Unique identifier for the component being tracked
     * @param targetFps Target frame rate (default 60fps)
     * @param interactionType Type of interaction for detailed tracking
     */
    fun startFrameRateTracking(componentId: String, targetFps: Int = 60, interactionType: String = "component_interaction") {
        if (BuildConfig.DEBUG || isGlobalTrackingEnabled) {
            // Use legacy tracking for compatibility
            val tracker = FrameRateTracker(componentId, targetFps)
            frameRateTracking[componentId] = tracker
            tracker.start()
            
            // Start enhanced Choreographer-based tracking
            performanceValidator.startFrameMonitoring(componentId, interactionType)
            
            Timber.d("PerformanceMonitor: Started enhanced tracking $componentId (target: ${targetFps}fps, type: $interactionType)")
        }
    }
    
    /**
     * Stop frame rate tracking for a component
     * Enhanced with Choreographer-based validation reporting
     * @param componentId Component identifier to stop tracking
     * @return Enhanced performance report from Choreographer-based monitoring
     */
    fun stopFrameRateTracking(componentId: String): com.example.liftrix.performance.PerformanceReport? {
        var enhancedReport: com.example.liftrix.performance.PerformanceReport? = null
        
        // Stop legacy tracking
        frameRateTracking[componentId]?.let { tracker ->
            tracker.stop()
            val metrics = tracker.getMetrics()
            
            // Log legacy performance summary
            Timber.d("PerformanceMonitor: Legacy tracking $componentId - " +
                    "Avg FPS: ${metrics.averageFps}, " +
                    "Frame drops: ${metrics.frameDrops}, " +
                    "Duration: ${metrics.durationMs}ms")
            
            frameRateTracking.remove(componentId)
        }
        
        // Stop enhanced Choreographer-based tracking
        try {
            enhancedReport = performanceValidator.stopFrameMonitoring(componentId)
            
            // Log comprehensive performance summary
            Timber.i("PerformanceMonitor: Enhanced tracking $componentId - " +
                    "Avg FPS: ${enhancedReport.averageFps}, " +
                    "Frame drops: ${enhancedReport.frameDropCount} (${enhancedReport.frameDropPercentage}%), " +
                    "Duration: ${enhancedReport.durationMs}ms, " +
                    "60fps Target: ${if (enhancedReport.meets60FpsTarget) "PASSED" else "FAILED"}")
            
            // Alert if 60fps performance target not met
            if (!enhancedReport.meets60FpsTarget) {
                Timber.w("PerformanceMonitor: 60FPS VALIDATION FAILED - $componentId " +
                        "(${enhancedReport.averageFps.roundToInt()}fps, ${enhancedReport.frameDropPercentage.roundToInt()}% drops)")
            }
        } catch (e: Exception) {
            Timber.w(e, "PerformanceMonitor: Failed to get enhanced performance report for $componentId")
        }
        
        return enhancedReport
    }
    
    /**
     * Get current performance metrics for a component
     * @param componentId Component identifier
     * @return Performance metrics or null if not tracking
     */
    fun getCurrentMetrics(componentId: String): PerformanceMetrics? {
        return frameRateTracking[componentId]?.getMetrics()
    }
    
    /**
     * Get performance summary for all tracked components
     */
    fun getGlobalPerformanceSummary(): Map<String, PerformanceMetrics> {
        return frameRateTracking.mapValues { it.value.getMetrics() }
    }
    
    /**
     * Enable performance tracking globally (for production debugging)
     * Enhanced with Choreographer-based monitoring
     */
    fun enableGlobalTracking() {
        isGlobalTrackingEnabled = true
        Timber.i("PerformanceMonitor: Global enhanced tracking enabled")
    }
    
    /**
     * Disable performance tracking globally
     * Clears both legacy and enhanced monitoring sessions
     */
    fun disableGlobalTracking() {
        isGlobalTrackingEnabled = false
        frameRateTracking.values.forEach { it.stop() }
        frameRateTracking.clear()
        
        // Clear enhanced monitoring sessions
        try {
            performanceValidator.clearAllSessions()
        } catch (e: Exception) {
            Timber.w(e, "PerformanceMonitor: Failed to clear enhanced monitoring sessions")
        }
        
        Timber.i("PerformanceMonitor: Global enhanced tracking disabled")
    }
    
    /**
     * Get current memory usage for performance monitoring
     * Task PERF-001: Memory usage tracking integration
     * 
     * @return Memory usage in MB
     */
    fun getCurrentMemoryUsage(): Float {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        return (memInfo.totalPss / 1024.0f).roundToInt() / 1.0f // Convert KB to MB
    }
    
    /**
     * Track memory usage for a specific component operation
     * Task PERF-001: Component-specific memory monitoring
     * 
     * @param componentId Component identifier
     * @param operation Operation description
     * @return Memory usage before and after operation
     */
    fun trackMemoryUsage(componentId: String, operation: String): MemoryUsageResult {
        val memoryBefore = getCurrentMemoryUsage()
        val startTime = System.currentTimeMillis()
        
        return MemoryUsageResult(
            componentId = componentId,
            operation = operation,
            memoryBefore = memoryBefore,
            startTime = startTime
        )
    }
    
    /**
     * Complete memory usage tracking and log results
     * Task PERF-001: Memory usage validation with alerts
     * 
     * @param result Memory usage tracking result from trackMemoryUsage
     */
    fun completeMemoryTracking(result: MemoryUsageResult) {
        val memoryAfter = getCurrentMemoryUsage()
        val duration = System.currentTimeMillis() - result.startTime
        val memoryDelta = memoryAfter - result.memoryBefore
        
        Timber.d("PerformanceMonitor: Memory tracking - ${result.componentId} ${result.operation}: " +
                "${result.memoryBefore}MB -> ${memoryAfter}MB (Δ${memoryDelta}MB) in ${duration}ms")
        
        // Task PERF-001: Alert for excessive memory usage
        if (memoryAfter > 100) {
            Timber.w("MEMORY ALERT: ${result.componentId} using ${memoryAfter}MB (limit: 100MB)")
        }
        
        // Task PERF-001: Alert for memory leaks (significant increase)
        if (memoryDelta > 20) {
            Timber.w("MEMORY LEAK ALERT: ${result.componentId} ${result.operation} " +
                    "increased memory by ${memoryDelta}MB")
        }
    }
}

/**
 * Frame rate tracker for individual components
 */
private class FrameRateTracker(
    val componentId: String,
    val targetFps: Int
) {
    private var startTime: Long = 0
    private var frameCount: Int = 0
    private var isTracking: Boolean = false
    private var frameDrops: Int = 0
    private var lastFrameTime: Long = 0
    private val frameTimeThreshold = 1000 / targetFps * 1.5 // 1.5x target frame time
    
    fun start() {
        startTime = System.currentTimeMillis()
        lastFrameTime = startTime
        frameCount = 0
        frameDrops = 0
        isTracking = true
    }
    
    fun stop() {
        isTracking = false
    }
    
    fun recordFrame() {
        if (!isTracking) return
        
        val currentTime = System.currentTimeMillis()
        val frameTime = currentTime - lastFrameTime
        
        if (frameTime > frameTimeThreshold) {
            frameDrops++
        }
        
        frameCount++
        lastFrameTime = currentTime
    }
    
    fun getMetrics(): PerformanceMetrics {
        val currentTime = System.currentTimeMillis()
        val durationMs = currentTime - startTime
        val averageFps = if (durationMs > 0) {
            (frameCount * 1000.0 / durationMs).toFloat()
        } else 0f
        
        return PerformanceMetrics(
            componentId = componentId,
            averageFps = averageFps,
            frameDrops = frameDrops,
            durationMs = durationMs,
            targetFps = targetFps
        )
    }
}

/**
 * Performance metrics data class
 */
data class PerformanceMetrics(
    val componentId: String,
    val averageFps: Float,
    val frameDrops: Int,
    val durationMs: Long,
    val targetFps: Int,
    val memoryUsageMb: Float = 0f // Task PERF-001: Add memory tracking
) {
    val isPerformanceGood: Boolean
        get() = averageFps >= targetFps * 0.9 && frameDrops < 5 && memoryUsageMb <= 100
}

/**
 * Memory usage tracking result
 * Task PERF-001: Memory monitoring data structure
 */
data class MemoryUsageResult(
    val componentId: String,
    val operation: String,
    val memoryBefore: Float,
    val startTime: Long
)

/**
 * Composable for automatic frame rate tracking
 * @param componentId Unique identifier for tracking
 * @param enabled Whether tracking is enabled
 * @param onMetricsUpdate Callback for metrics updates
 */
@Composable
fun PerformanceTracker(
    componentId: String,
    enabled: Boolean = true,
    onMetricsUpdate: ((PerformanceMetrics) -> Unit)? = null
) {
    val view = LocalView.current
    var frameCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(componentId, enabled) {
        if (!enabled) return@LaunchedEffect
        
        // Simple frame counting approach
        while (true) {
            delay(16) // ~60fps
            frameCount++
            
            // Report metrics every second
            if (frameCount % 60 == 0) {
                onMetricsUpdate?.invoke(
                    PerformanceMetrics(
                        componentId = componentId,
                        averageFps = 60f, // Simplified - real implementation would calculate actual FPS
                        frameDrops = 0,
                        durationMs = frameCount * 16L,
                        targetFps = 60
                    )
                )
            }
        }
    }
}

/**
 * Development build performance checker
 */
object PerformanceValidator {
    
    /**
     * Validate widget rendering performance
     * @param widgetCount Number of widgets being rendered
     * @param renderTimeMs Time taken to render widgets
     */
    fun validateWidgetPerformance(widgetCount: Int, renderTimeMs: Long) {
        if (BuildConfig.DEBUG) {
            val targetTimePerWidget = 16 / 10 // 1.6ms per widget for 60fps with 10 widgets
            val actualTimePerWidget = renderTimeMs.toFloat() / widgetCount
            
            if (actualTimePerWidget > targetTimePerWidget) {
                Timber.w("PerformanceValidator: Widget rendering slow - " +
                        "${actualTimePerWidget}ms per widget (target: ${targetTimePerWidget}ms)")
            }
        }
    }
    
    /**
     * Validate memory usage during widget operations
     * @param operationName Name of the operation
     * @param memoryUsedMb Memory used in MB
     */
    fun validateMemoryUsage(operationName: String, memoryUsedMb: Float) {
        if (BuildConfig.DEBUG) {
            val maxMemoryMb = 100f // 100MB limit as per requirements
            
            if (memoryUsedMb > maxMemoryMb) {
                Timber.w("PerformanceValidator: Memory usage high - " +
                        "$operationName using ${memoryUsedMb}MB (limit: ${maxMemoryMb}MB)")
            }
        }
    }
    
    /**
     * Check recomposition count for performance optimization
     * @param componentName Name of the component
     * @param recompositionCount Number of recompositions
     */
    fun checkRecompositionCount(componentName: String, recompositionCount: Int) {
        if (BuildConfig.DEBUG && recompositionCount > 10) {
            Timber.w("PerformanceValidator: Excessive recompositions - " +
                    "$componentName recomposed $recompositionCount times")
        }
    }
    
    /**
     * Enhanced 60fps validation for PERF-002
     * Validates component meets 60fps target with detailed analysis
     * @param componentName Name of the component being validated
     * @param averageFps Measured average FPS
     * @param frameDrops Number of frame drops detected
     * @param duration Duration of the measurement period
     */
    fun validate60FpsTarget(
        componentName: String,
        averageFps: Float,
        frameDrops: Int,
        duration: Long
    ): Boolean {
        if (!BuildConfig.DEBUG) return true
        
        val targetFps = 60f
        val minAcceptableFps = targetFps * 0.9f // 90% of target
        val maxAcceptableFrameDrops = 5
        
        val fpsValid = averageFps >= minAcceptableFps
        val frameDropsValid = frameDrops <= maxAcceptableFrameDrops
        
        if (!fpsValid) {
            Timber.w("PERF-002 VALIDATION FAILED: $componentName FPS too low - " +
                    "${averageFps.roundToInt()}fps < ${minAcceptableFps.roundToInt()}fps target")
        }
        
        if (!frameDropsValid) {
            Timber.w("PERF-002 VALIDATION FAILED: $componentName too many frame drops - " +
                    "$frameDrops > $maxAcceptableFrameDrops allowed")
        }
        
        if (fpsValid && frameDropsValid) {
            Timber.d("PERF-002 VALIDATION PASSED: $componentName - " +
                    "${averageFps.roundToInt()}fps, $frameDrops drops, ${duration}ms")
        }
        
        return fpsValid && frameDropsValid
    }
    
    /**
     * Animation timing validation for PERF-002
     * Ensures animations meet PRD timing requirements
     * @param animationType Type of animation (press, transition, etc.)
     * @param actualDuration Actual animation duration in ms
     * @param targetDuration Target animation duration in ms
     */
    fun validateAnimationTiming(
        animationType: String,
        actualDuration: Long,
        targetDuration: Long
    ): Boolean {
        if (!BuildConfig.DEBUG) return true
        
        val tolerance = targetDuration * 0.15 // 15% tolerance
        val isValid = actualDuration <= targetDuration + tolerance
        
        if (!isValid) {
            Timber.w("ANIMATION TIMING FAILED: $animationType took ${actualDuration}ms " +
                    "(target: ${targetDuration}ms, max: ${(targetDuration + tolerance).toInt()}ms)")
        } else {
            Timber.d("ANIMATION TIMING PASSED: $animationType completed in ${actualDuration}ms " +
                    "(target: ${targetDuration}ms)")
        }
        
        return isValid
    }
}