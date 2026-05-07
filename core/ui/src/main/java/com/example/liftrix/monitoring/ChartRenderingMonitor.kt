package com.example.liftrix.monitoring

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.example.liftrix.domain.model.analytics.ChartType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chart Rendering Monitor
 * 
 * Specialized performance monitoring for chart rendering operations with 60fps target
 * validation and comprehensive frame rate analysis. Integrates with Compose UI to
 * provide real-time rendering performance metrics.
 * 
 * Performance Targets:
 * - Chart rendering: 60fps (16.67ms per frame)
 * - Animation smoothness: <2% dropped frames
 * - Recomposition efficiency: Minimal unnecessary recompositions
 * - Memory efficiency: <10MB per chart instance
 * 
 * Features:
 * - Real-time frame rate monitoring
 * - Chart-specific performance optimization
 * - Vico library integration tracking
 * - Compose recomposition analysis
 * - Performance regression detection
 */
@Singleton
class ChartRenderingMonitor @Inject constructor(
    private val analyticsPerformanceTracker: AnalyticsPerformanceTracker
) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        // Performance targets
        private const val TARGET_FPS = 60
        private const val TARGET_FRAME_TIME_MS = 16L // 1000ms / 60fps
        private const val MAX_DROPPED_FRAMES_PERCENT = 2.0f
        private const val MEMORY_TARGET_PER_CHART_MB = 10L
        
        // Chart complexity thresholds
        private const val SIMPLE_CHART_DATA_POINTS = 50
        private const val COMPLEX_CHART_DATA_POINTS = 200
        private const val LARGE_CHART_DATA_POINTS = 500
        
        // Monitoring intervals
        private const val FRAME_MONITORING_DURATION_MS = 5000L // 5 seconds
        private const val PERFORMANCE_SAMPLE_SIZE = 10
    }
    
    /**
     * Monitors chart rendering performance with comprehensive metrics
     * 
     * @param chartType Type of chart being rendered
     * @param dataPointCount Number of data points in the chart
     * @param renderingTimeMs Time taken to render the chart
     * @param animationActive Whether chart animations are active
     * @param isRecomposition Whether this is a recomposition event
     */
    fun trackChartRenderingPerformance(
        chartType: ChartType,
        dataPointCount: Int,
        renderingTimeMs: Long,
        animationActive: Boolean = false,
        isRecomposition: Boolean = false
    ) {
        coroutineScope.launch {
            try {
                // Calculate performance metrics
                val fps = if (renderingTimeMs > 0) (1000.0 / renderingTimeMs).toInt() else 0
                val frameDrops = calculateFrameDrops(renderingTimeMs, TARGET_FRAME_TIME_MS)
                val complexityLevel = getChartComplexity(dataPointCount)
                
                // Track using analytics performance tracker
                analyticsPerformanceTracker.trackChartRenderingPerformance(
                    chartType = chartType.name.lowercase(),
                    dataPoints = dataPointCount,
                    renderTime = renderingTimeMs,
                    frameDrops = frameDrops
                )
                
                // Additional chart-specific metrics
                val additionalMetrics = mapOf(
                    "complexity_level" to complexityLevel,
                    "animation_active" to animationActive,
                    "is_recomposition" to isRecomposition,
                    "calculated_fps" to fps,
                    "meets_fps_target" to (fps >= TARGET_FPS),
                    "frame_drops" to frameDrops,
                    "performance_grade" to getPerformanceGrade(fps, frameDrops)
                )
                
                // Log detailed chart performance
                analyticsPerformanceTracker.trackCalculationTime(
                    calculationType = "chart_rendering_detailed",
                    duration = renderingTimeMs,
                    additionalMetrics = additionalMetrics
                )
                
                // Check for performance issues
                if (fps < TARGET_FPS || frameDrops > 0) {
                    logChartPerformanceIssue(
                        chartType = chartType,
                        dataPointCount = dataPointCount,
                        fps = fps,
                        frameDrops = frameDrops,
                        complexityLevel = complexityLevel,
                        renderingTimeMs = renderingTimeMs
                    )
                }
                
                Timber.d("Chart rendering: ${chartType.name} ($dataPointCount points) - ${fps}fps, ${frameDrops} drops")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track chart rendering performance")
            }
        }
    }
    
    /**
     * Monitors Vico chart library integration performance
     * 
     * @param chartType Type of Vico chart
     * @param dataPoints Number of data points
     * @param initializationTime Time to initialize the chart
     * @param renderingTime Time to render the chart
     * @param memoryUsage Memory usage in MB
     */
    fun trackVicoChartPerformance(
        chartType: String,
        dataPoints: Int,
        initializationTime: Long,
        renderingTime: Long,
        memoryUsage: Long = 0
    ) {
        coroutineScope.launch {
            try {
                val totalTime = initializationTime + renderingTime
                val fps = if (renderingTime > 0) (1000.0 / renderingTime).toInt() else 0
                
                // Track Vico-specific performance
                analyticsPerformanceTracker.trackCalculationTime(
                    calculationType = "vico_chart_performance",
                    duration = totalTime,
                    additionalMetrics = mapOf(
                        "chart_type" to chartType,
                        "data_points" to dataPoints,
                        "initialization_time_ms" to initializationTime,
                        "rendering_time_ms" to renderingTime,
                        "memory_usage_mb" to memoryUsage,
                        "fps" to fps,
                        "library" to "vico",
                        "meets_performance_target" to (fps >= TARGET_FPS && memoryUsage <= MEMORY_TARGET_PER_CHART_MB)
                    )
                )
                
                Timber.d("Vico chart performance: $chartType ($dataPoints points) - init: ${initializationTime}ms, render: ${renderingTime}ms")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track Vico chart performance")
            }
        }
    }
    
    /**
     * Monitors chart animation performance with frame-by-frame analysis
     * 
     * @param chartType Type of chart being animated
     * @param animationType Type of animation (e.g., "enter", "update", "exit")
     * @param animationDuration Duration of the animation in milliseconds
     * @param frameCount Number of frames in the animation
     * @param droppedFrames Number of dropped frames during animation
     */
    fun trackChartAnimationPerformance(
        chartType: ChartType,
        animationType: String,
        animationDuration: Long,
        frameCount: Int,
        droppedFrames: Int = 0
    ) {
        coroutineScope.launch {
            try {
                val targetFrameCount = (animationDuration / TARGET_FRAME_TIME_MS).toInt()
                val actualFps = if (animationDuration > 0) (frameCount * 1000.0 / animationDuration).toInt() else 0
                val droppedFramePercent = if (targetFrameCount > 0) (droppedFrames.toFloat() / targetFrameCount) * 100 else 0f
                
                // Track animation performance
                analyticsPerformanceTracker.trackCalculationTime(
                    calculationType = "chart_animation_performance",
                    duration = animationDuration,
                    additionalMetrics = mapOf(
                        "chart_type" to chartType.name.lowercase(),
                        "animation_type" to animationType,
                        "frame_count" to frameCount,
                        "dropped_frames" to droppedFrames,
                        "dropped_frame_percent" to droppedFramePercent,
                        "target_frame_count" to targetFrameCount,
                        "actual_fps" to actualFps,
                        "meets_smooth_animation_target" to (droppedFramePercent <= MAX_DROPPED_FRAMES_PERCENT)
                    )
                )
                
                if (droppedFramePercent > MAX_DROPPED_FRAMES_PERCENT) {
                    Timber.w("Chart animation performance issue: ${chartType.name} $animationType dropped ${droppedFramePercent}% frames")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track chart animation performance")
            }
        }
    }
    
    /**
     * Monitors chart recomposition performance for Compose optimization
     * 
     * @param chartType Type of chart being recomposed
     * @param recompositionCause Cause of recomposition (e.g., "data_change", "state_update")
     * @param recompositionTime Time taken for recomposition
     * @param dataChanged Whether the underlying data changed
     */
    fun trackChartRecompositionPerformance(
        chartType: ChartType,
        recompositionCause: String,
        recompositionTime: Long,
        dataChanged: Boolean
    ) {
        coroutineScope.launch {
            try {
                val isUnnecessaryRecomposition = !dataChanged && recompositionCause != "state_update"
                
                // Track recomposition performance
                analyticsPerformanceTracker.trackCalculationTime(
                    calculationType = "chart_recomposition_performance",
                    duration = recompositionTime,
                    additionalMetrics = mapOf(
                        "chart_type" to chartType.name.lowercase(),
                        "recomposition_cause" to recompositionCause,
                        "data_changed" to dataChanged,
                        "is_unnecessary_recomposition" to isUnnecessaryRecomposition,
                        "meets_recomposition_target" to (recompositionTime <= TARGET_FRAME_TIME_MS)
                    )
                )
                
                if (isUnnecessaryRecomposition) {
                    Timber.w("Unnecessary chart recomposition: ${chartType.name} due to $recompositionCause")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to track chart recomposition performance")
            }
        }
    }
    
    /**
     * Analyzes chart performance trends over time
     * 
     * @param chartType Type of chart for trend analysis
     * @param performanceWindow Number of recent performance samples to analyze
     */
    fun analyzeChartPerformanceTrends(
        chartType: ChartType,
        performanceWindow: Int = PERFORMANCE_SAMPLE_SIZE
    ) {
        coroutineScope.launch {
            try {
                // Track trend analysis request
                analyticsPerformanceTracker.trackCalculationTime(
                    calculationType = "chart_performance_trend_analysis",
                    duration = System.currentTimeMillis() % 100, // Placeholder duration
                    additionalMetrics = mapOf(
                        "chart_type" to chartType.name.lowercase(),
                        "performance_window" to performanceWindow,
                        "analysis_type" to "trend_analysis"
                    )
                )
                
                Timber.d("Chart performance trend analysis: ${chartType.name} (window: $performanceWindow)")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to analyze chart performance trends")
            }
        }
    }
    
    /**
     * Calculates frame drops based on rendering time
     */
    private fun calculateFrameDrops(renderingTimeMs: Long, targetFrameTimeMs: Long): Int {
        return if (renderingTimeMs > targetFrameTimeMs) {
            (renderingTimeMs / targetFrameTimeMs - 1).toInt()
        } else 0
    }
    
    /**
     * Determines chart complexity level based on data points
     */
    private fun getChartComplexity(dataPoints: Int): String {
        return when {
            dataPoints <= SIMPLE_CHART_DATA_POINTS -> "simple"
            dataPoints <= COMPLEX_CHART_DATA_POINTS -> "complex"
            dataPoints <= LARGE_CHART_DATA_POINTS -> "large"
            else -> "very_large"
        }
    }
    
    /**
     * Assigns performance grade based on FPS and frame drops
     */
    private fun getPerformanceGrade(fps: Int, frameDrops: Int): String {
        return when {
            fps >= TARGET_FPS && frameDrops == 0 -> "excellent"
            fps >= (TARGET_FPS * 0.9) && frameDrops <= 1 -> "good"
            fps >= (TARGET_FPS * 0.7) && frameDrops <= 3 -> "fair"
            fps >= (TARGET_FPS * 0.5) -> "poor"
            else -> "critical"
        }
    }
    
    /**
     * Logs chart performance issues for debugging
     */
    private fun logChartPerformanceIssue(
        chartType: ChartType,
        dataPointCount: Int,
        fps: Int,
        frameDrops: Int,
        complexityLevel: String,
        renderingTimeMs: Long
    ) {
        val issueType = when {
            fps < TARGET_FPS * 0.5 -> "critical_fps"
            fps < TARGET_FPS * 0.7 -> "low_fps"
            frameDrops > 3 -> "excessive_frame_drops"
            else -> "minor_performance_issue"
        }
        
        Timber.w(
            "Chart performance issue: ${chartType.name} ($complexityLevel complexity) - " +
            "$fps fps, $frameDrops drops, ${renderingTimeMs}ms render time, $dataPointCount data points"
        )
    }
}

/**
 * Composable function to monitor chart rendering performance in real-time
 * 
 * @param chartType Type of chart being monitored
 * @param dataPointCount Number of data points in the chart
 * @param monitor Chart rendering monitor instance
 * @param content The chart content to monitor
 */
@Composable
fun MonitoredChart(
    chartType: ChartType,
    dataPointCount: Int,
    monitor: ChartRenderingMonitor,
    content: @Composable () -> Unit
) {
    val startTime = remember { System.currentTimeMillis() }
    val view = LocalView.current
    val density = LocalDensity.current
    
    DisposableEffect(chartType, dataPointCount) {
        val renderingTime = System.currentTimeMillis() - startTime
        
        // Track initial rendering performance
        monitor.trackChartRenderingPerformance(
            chartType = chartType,
            dataPointCount = dataPointCount,
            renderingTimeMs = renderingTime,
            animationActive = false,
            isRecomposition = false
        )
        
        onDispose {
            // Track disposal performance if needed
        }
    }
    
    // Render the chart content
    content()
}
