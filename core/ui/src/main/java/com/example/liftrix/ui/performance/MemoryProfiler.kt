package com.example.liftrix.ui.performance

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.liftrix.core.ui.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memory profiling utility for widget memory usage tracking and optimization.
 * 
 * Monitors memory consumption during widget rendering, detects memory leaks,
 * and provides optimization recommendations. Tracks memory usage against
 * the 100MB target specified in performance requirements.
 * 
 * Key features:
 * - Real-time memory usage monitoring for widget operations
 * - Memory leak detection with automatic alerting
 * - Widget-specific memory profiling and optimization recommendations
 * - Integration with performance monitoring for comprehensive analysis
 * - Development build instrumentation with minimal production impact
 */
@Singleton
class MemoryProfiler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memorySnapshots = mutableMapOf<String, MutableList<MemorySnapshot>>()
    private var isProfilingEnabled = BuildConfig.DEBUG
    private val memoryThresholdMb = 100f // 100MB target as per requirements
    
    /**
     * Start memory profiling for a specific widget operation
     * @param operationId Unique identifier for the operation
     * @param description Human-readable description
     */
    fun startProfiling(operationId: String, description: String = "") {
        if (!isProfilingEnabled) return
        
        val initialMemory = getCurrentMemoryUsage()
        val snapshots = memorySnapshots.getOrPut(operationId) { mutableListOf() }
        
        snapshots.add(
            MemorySnapshot(
                timestamp = System.currentTimeMillis(),
                memoryUsedMb = initialMemory,
                operationId = operationId,
                description = "Start: $description",
                type = SnapshotType.START
            )
        )
        
        Timber.d("MemoryProfiler: Started profiling $operationId - Initial memory: ${initialMemory}MB")
    }
    
    /**
     * Record memory usage during an operation
     * @param operationId Operation identifier
     * @param checkpoint Description of the checkpoint
     */
    fun recordCheckpoint(operationId: String, checkpoint: String) {
        if (!isProfilingEnabled) return
        
        val currentMemory = getCurrentMemoryUsage()
        val snapshots = memorySnapshots[operationId] ?: return
        
        snapshots.add(
            MemorySnapshot(
                timestamp = System.currentTimeMillis(),
                memoryUsedMb = currentMemory,
                operationId = operationId,
                description = checkpoint,
                type = SnapshotType.CHECKPOINT
            )
        )
        
        // Check for memory threshold violations
        if (currentMemory > memoryThresholdMb) {
            Timber.w("MemoryProfiler: MEMORY THRESHOLD EXCEEDED - $operationId using ${currentMemory}MB " +
                    "(threshold: ${memoryThresholdMb}MB) at checkpoint: $checkpoint")
        }
    }
    
    /**
     * Stop profiling and generate memory usage report
     * @param operationId Operation identifier
     * @return Memory usage report
     */
    fun stopProfiling(operationId: String): MemoryReport? {
        if (!isProfilingEnabled) return null
        
        val snapshots = memorySnapshots[operationId] ?: return null
        val finalMemory = getCurrentMemoryUsage()
        
        // Add final snapshot
        snapshots.add(
            MemorySnapshot(
                timestamp = System.currentTimeMillis(),
                memoryUsedMb = finalMemory,
                operationId = operationId,
                description = "End",
                type = SnapshotType.END
            )
        )
        
        val report = generateMemoryReport(operationId, snapshots)
        
        // Log report
        Timber.i("MemoryProfiler: $operationId completed - " +
                "Peak: ${report.peakMemoryMb}MB, " +
                "Delta: ${report.memoryDeltaMb}MB, " +
                "Duration: ${report.durationMs}ms")
        
        // Alert for performance issues
        if (report.peakMemoryMb > memoryThresholdMb) {
            Timber.w("MemoryProfiler: MEMORY PERFORMANCE ISSUE - $operationId exceeded " +
                    "memory threshold (${report.peakMemoryMb}MB > ${memoryThresholdMb}MB)")
        }
        
        if (report.memoryDeltaMb > 10f) {
            Timber.w("MemoryProfiler: POTENTIAL MEMORY LEAK - $operationId " +
                    "increased memory by ${report.memoryDeltaMb}MB")
        }
        
        // Clean up snapshots to prevent memory buildup
        memorySnapshots.remove(operationId)
        
        return report
    }
    
    /**
     * Get current memory usage in MB
     */
    fun getCurrentMemoryUsage(): Float {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        // Get app's memory usage
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory / (1024f * 1024f) // Convert to MB
    }
    
    /**
     * Check if memory usage is within acceptable limits
     */
    fun isMemoryUsageHealthy(): Boolean {
        return getCurrentMemoryUsage() <= memoryThresholdMb
    }
    
    /**
     * Generate memory usage report for an operation
     */
    private fun generateMemoryReport(operationId: String, snapshots: List<MemorySnapshot>): MemoryReport {
        if (snapshots.isEmpty()) {
            return MemoryReport(
                operationId = operationId,
                initialMemoryMb = 0f,
                peakMemoryMb = 0f,
                finalMemoryMb = 0f,
                memoryDeltaMb = 0f,
                durationMs = 0L,
                checkpoints = emptyList()
            )
        }
        
        val firstSnapshot = snapshots.first()
        val lastSnapshot = snapshots.last()
        val peakMemory = snapshots.maxOf { it.memoryUsedMb }
        
        return MemoryReport(
            operationId = operationId,
            initialMemoryMb = firstSnapshot.memoryUsedMb,
            peakMemoryMb = peakMemory,
            finalMemoryMb = lastSnapshot.memoryUsedMb,
            memoryDeltaMb = lastSnapshot.memoryUsedMb - firstSnapshot.memoryUsedMb,
            durationMs = lastSnapshot.timestamp - firstSnapshot.timestamp,
            checkpoints = snapshots.filter { it.type == SnapshotType.CHECKPOINT }
        )
    }
    
    /**
     * Enable or disable memory profiling
     */
    fun setProfilingEnabled(enabled: Boolean) {
        isProfilingEnabled = enabled && BuildConfig.DEBUG
        if (!isProfilingEnabled) {
            memorySnapshots.clear()
        }
    }
    
    /**
     * Get memory optimization recommendations
     */
    fun getOptimizationRecommendations(): List<String> {
        val currentMemory = getCurrentMemoryUsage()
        val recommendations = mutableListOf<String>()
        
        when {
            currentMemory > memoryThresholdMb * 1.2f -> {
                recommendations.add("Critical: Memory usage ${currentMemory}MB exceeds target by 20%")
                recommendations.add("Consider widget lazy loading and data virtualization")
                recommendations.add("Review widget cache sizes and implement LRU eviction")
                recommendations.add("Check for memory leaks in widget state management")
            }
            currentMemory > memoryThresholdMb -> {
                recommendations.add("Warning: Memory usage ${currentMemory}MB above ${memoryThresholdMb}MB target")
                recommendations.add("Optimize widget data loading and caching strategies")
                recommendations.add("Consider reducing widget preview data size")
            }
            else -> {
                recommendations.add("Memory usage within acceptable limits (${currentMemory}MB)")
            }
        }
        
        return recommendations
    }
}

/**
 * Memory snapshot data class
 */
data class MemorySnapshot(
    val timestamp: Long,
    val memoryUsedMb: Float,
    val operationId: String,
    val description: String,
    val type: SnapshotType
)

/**
 * Types of memory snapshots
 */
enum class SnapshotType {
    START, CHECKPOINT, END
}

/**
 * Memory usage report
 */
data class MemoryReport(
    val operationId: String,
    val initialMemoryMb: Float,
    val peakMemoryMb: Float,
    val finalMemoryMb: Float,
    val memoryDeltaMb: Float,
    val durationMs: Long,
    val checkpoints: List<MemorySnapshot>
) {
    val isMemoryHealthy: Boolean
        get() = peakMemoryMb <= 100f && memoryDeltaMb <= 10f
        
    val hasMemoryLeak: Boolean
        get() = memoryDeltaMb > 5f // 5MB increase suggests potential leak
}

/**
 * Widget memory profiler for tracking widget-specific memory usage
 */
object WidgetMemoryProfiler {
    
    private val widgetMemoryTracking = mutableMapOf<String, WidgetMemoryData>()
    
    /**
     * Track memory usage for widget rendering
     * @param widgetId Widget identifier
     * @param widgetType Type of widget
     * @param memoryProfiler Memory profiler instance
     */
    fun trackWidgetMemory(
        widgetId: String,
        widgetType: String,
        memoryProfiler: MemoryProfiler
    ) {
        if (BuildConfig.DEBUG) {
            val memoryBefore = memoryProfiler.getCurrentMemoryUsage()
            
            widgetMemoryTracking[widgetId] = WidgetMemoryData(
                widgetId = widgetId,
                widgetType = widgetType,
                initialMemoryMb = memoryBefore,
                startTime = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Complete memory tracking for a widget
     * @param widgetId Widget identifier
     * @param memoryProfiler Memory profiler instance
     */
    fun completeWidgetTracking(
        widgetId: String,
        memoryProfiler: MemoryProfiler
    ) {
        if (BuildConfig.DEBUG) {
            val widgetData = widgetMemoryTracking[widgetId] ?: return
            val memoryAfter = memoryProfiler.getCurrentMemoryUsage()
            val memoryDelta = memoryAfter - widgetData.initialMemoryMb
            val duration = System.currentTimeMillis() - widgetData.startTime
            
            if (memoryDelta > 1f) { // 1MB increase per widget is concerning
                Timber.w("WidgetMemoryProfiler: High memory usage - ${widgetData.widgetType} " +
                        "widget used ${memoryDelta}MB in ${duration}ms")
            }
            
            widgetMemoryTracking.remove(widgetId)
        }
    }
}

/**
 * Widget memory tracking data
 */
private data class WidgetMemoryData(
    val widgetId: String,
    val widgetType: String,
    val initialMemoryMb: Float,
    val startTime: Long
)

/**
 * Composable for automatic memory tracking during widget lifecycle
 * @param componentName Name of the component
 * @param memoryProfiler Memory profiler instance
 */
@Composable
fun MemoryTracker(
    componentName: String,
    memoryProfiler: MemoryProfiler? = null
) {
    if (BuildConfig.DEBUG && memoryProfiler != null) {
        var isTracking by remember { mutableStateOf(false) }
        
        DisposableEffect(componentName) {
            if (!isTracking) {
                memoryProfiler.startProfiling(componentName, "Compose lifecycle")
                isTracking = true
            }
            
            onDispose {
                if (isTracking) {
                    memoryProfiler.stopProfiling(componentName)
                    isTracking = false
                }
            }
        }
        
        // Periodic memory checkpoints
        LaunchedEffect(componentName) {
            while (isTracking) {
                delay(1000) // Check every second
                memoryProfiler.recordCheckpoint(componentName, "Periodic check")
            }
        }
    }
}
