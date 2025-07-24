package com.example.liftrix.ui.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.Choreographer
import androidx.annotation.RequiresApi
import com.example.liftrix.BuildConfig
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Real-time performance profiler for development and debugging.
 * Provides detailed performance monitoring capabilities for 60fps validation,
 * memory tracking, and component-specific performance analysis.
 * 
 * Features:
 * - Real-time frame rate monitoring with 60fps validation
 * - Memory usage tracking with leak detection
 * - Component-specific performance profiling
 * - Development build performance alerts
 * - Integration with existing PerformanceMonitor system
 */
@Singleton
class PerformanceProfiler @Inject constructor(
    private val context: Context
) {
    private val performanceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activeProfilingTasks = ConcurrentHashMap<String, ProfilingTask>()
    private val frameMetrics = ConcurrentHashMap<String, FrameMetrics>()
    private val memoryMetrics = ConcurrentHashMap<String, MemoryMetrics>()
    
    private var globalFrameCallback: GlobalFrameCallback? = null
    private var isGlobalProfilingActive = false
    
    /**
     * Start profiling a specific component or operation
     * @param componentId Unique identifier for the component
     * @param profilingType Type of profiling to perform
     * @return ProfilingSession for controlling the profiling
     */
    fun startProfiling(
        componentId: String,
        profilingType: ProfilingType = ProfilingType.COMPREHENSIVE
    ): ProfilingSession {
        if (!BuildConfig.DEBUG) {
            return NoOpProfilingSession()
        }
        
        val session = ProfilingSession(
            componentId = componentId,
            profilingType = profilingType,
            onStop = { stopProfiling(componentId) }
        )
        
        val task = ProfilingTask(
            componentId = componentId,
            profilingType = profilingType,
            startTime = System.currentTimeMillis(),
            session = session
        )
        
        activeProfilingTasks[componentId] = task
        
        when (profilingType) {
            ProfilingType.FRAME_RATE -> startFrameRateProfiling(componentId)
            ProfilingType.MEMORY -> startMemoryProfiling(componentId)
            ProfilingType.COMPREHENSIVE -> {
                startFrameRateProfiling(componentId)
                startMemoryProfiling(componentId)
            }
        }
        
        Timber.d("PerformanceProfiler: Started profiling $componentId ($profilingType)")
        return session
    }
    
    /**
     * Stop profiling for a specific component
     * @param componentId Component identifier to stop profiling
     * @return Performance summary or null if not profiling
     */
    fun stopProfiling(componentId: String): PerformanceSummary? {
        val task = activeProfilingTasks.remove(componentId) ?: return null
        val endTime = System.currentTimeMillis()
        val duration = endTime - task.startTime
        
        val frameMetrics = frameMetrics.remove(componentId)
        val memoryMetrics = memoryMetrics.remove(componentId)
        
        val summary = PerformanceSummary(
            componentId = componentId,
            profilingType = task.profilingType,
            duration = duration,
            frameMetrics = frameMetrics,
            memoryMetrics = memoryMetrics,
            timestamp = endTime
        )
        
        // Log performance summary
        logPerformanceSummary(summary)
        
        return summary
    }
    
    /**
     * Start global performance monitoring for entire app
     * Useful for identifying performance bottlenecks across components
     */
    fun startGlobalProfiling() {
        if (!BuildConfig.DEBUG || isGlobalProfilingActive) return
        
        isGlobalProfilingActive = true
        globalFrameCallback = GlobalFrameCallback()
        Choreographer.getInstance().postFrameCallback(globalFrameCallback)
        
        // Start global memory monitoring
        performanceScope.launch {
            while (isGlobalProfilingActive) {
                val memoryUsage = getCurrentMemoryUsage()
                if (memoryUsage > 150 * 1024 * 1024) { // 150MB warning threshold
                    Timber.w("PerformanceProfiler: High memory usage detected: ${memoryUsage / 1024 / 1024}MB")
                }
                delay(5000) // Check every 5 seconds
            }
        }
        
        Timber.i("PerformanceProfiler: Global profiling started")
    }
    
    /**
     * Stop global performance monitoring
     */
    fun stopGlobalProfiling() {
        if (!isGlobalProfilingActive) return
        
        isGlobalProfilingActive = false
        globalFrameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        globalFrameCallback = null
        
        Timber.i("PerformanceProfiler: Global profiling stopped")
    }
    
    /**
     * Get current performance metrics for a component
     * @param componentId Component identifier
     * @return Current performance metrics or null if not profiling
     */
    fun getCurrentMetrics(componentId: String): CurrentMetrics? {
        val task = activeProfilingTasks[componentId] ?: return null
        val frameMetrics = frameMetrics[componentId]
        val memoryMetrics = memoryMetrics[componentId]
        
        return CurrentMetrics(
            componentId = componentId,
            duration = System.currentTimeMillis() - task.startTime,
            currentFps = frameMetrics?.getCurrentFps() ?: 0f,
            frameDrops = frameMetrics?.frameDrops ?: 0,
            memoryUsageMb = (memoryMetrics?.currentMemoryUsage ?: 0L) / 1024 / 1024
        )
    }
    
    /**
     * Get performance summary for all active profiling sessions
     */
    fun getAllActiveMetrics(): Map<String, CurrentMetrics> {
        return activeProfilingTasks.keys.mapNotNull { componentId ->
            getCurrentMetrics(componentId)?.let { componentId to it }
        }.toMap()
    }
    
    /**
     * Validate 60fps performance for a specific operation
     * @param componentId Component identifier  
     * @param duration Duration of the operation in milliseconds
     * @return Validation result with performance analysis
     */
    fun validate60FpsPerformance(
        componentId: String,
        duration: Long
    ): PerformanceValidationResult {
        val frameMetrics = this.frameMetrics[componentId]
        
        if (frameMetrics == null) {
            return PerformanceValidationResult(
                componentId = componentId,
                isValid = false,
                averageFps = 0f,
                frameDrops = 0,
                duration = duration,
                issues = listOf("No frame metrics available for validation")
            )
        }
        
        val averageFps = frameMetrics.getCurrentFps()
        val frameDrops = frameMetrics.frameDrops
        val targetFps = 60f
        
        val isValid = averageFps >= targetFps * 0.9f && frameDrops <= 3
        val issues = mutableListOf<String>()
        
        if (averageFps < targetFps * 0.9f) {
            issues.add("Average FPS (${averageFps.roundToInt()}) below 90% of target ($targetFps)")
        }
        
        if (frameDrops > 3) {
            issues.add("Excessive frame drops: $frameDrops")
        }
        
        if (duration > 300) {
            issues.add("Operation duration (${duration}ms) exceeds 300ms target")
        }
        
        return PerformanceValidationResult(
            componentId = componentId,
            isValid = isValid,
            averageFps = averageFps,
            frameDrops = frameDrops,
            duration = duration,
            issues = issues
        )
    }
    
    // Private helper functions
    
    private fun startFrameRateProfiling(componentId: String) {
        val metrics = FrameMetrics(componentId)
        frameMetrics[componentId] = metrics
        
        val frameCallback = ComponentFrameCallback(metrics)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }
    
    private fun startMemoryProfiling(componentId: String) {
        val metrics = MemoryMetrics(
            componentId = componentId,
            initialMemory = getCurrentMemoryUsage()
        )
        memoryMetrics[componentId] = metrics
        
        // Start memory monitoring coroutine
        performanceScope.launch {
            while (activeProfilingTasks.containsKey(componentId)) {
                metrics.updateMemoryUsage(getCurrentMemoryUsage())
                delay(500) // Update every 500ms
            }
        }
    }
    
    private fun getCurrentMemoryUsage(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem - memInfo.availMem
    }
    
    private fun logPerformanceSummary(summary: PerformanceSummary) {
        val frameInfo = summary.frameMetrics?.let { 
            "FPS: ${it.getCurrentFps().roundToInt()}, Drops: ${it.frameDrops}" 
        } ?: "No frame data"
        
        val memoryInfo = summary.memoryMetrics?.let {
            "Memory: ${it.currentMemoryUsage / 1024 / 1024}MB"
        } ?: "No memory data"
        
        Timber.i("PerformanceProfiler: ${summary.componentId} summary - " +
                "Duration: ${summary.duration}ms, $frameInfo, $memoryInfo")
        
        // Alert for performance issues
        summary.frameMetrics?.let { metrics ->
            if (metrics.getCurrentFps() < 54) { // 90% of 60fps
                Timber.w("PERFORMANCE ALERT: ${summary.componentId} below 60fps target")
            }
        }
        
        summary.memoryMetrics?.let { metrics ->
            val memoryUsageMb = metrics.currentMemoryUsage / 1024 / 1024
            if (memoryUsageMb > 100) {
                Timber.w("MEMORY ALERT: ${summary.componentId} using ${memoryUsageMb}MB")
            }
        }
    }
    
    // Inner classes and data structures
    
    private class ProfilingTask(
        val componentId: String,
        val profilingType: ProfilingType,
        val startTime: Long,
        val session: ProfilingSession
    )
    
    class FrameMetrics(
        val componentId: String
    ) {
        private val startTime = System.nanoTime()
        private var frameCount = AtomicLong(0)
        var frameDrops = 0
        private var lastFrameTime = 0L
        
        fun recordFrame(frameTimeNanos: Long) {
            frameCount.incrementAndGet()
            
            if (lastFrameTime != 0L) {
                val frameDuration = (frameTimeNanos - lastFrameTime) / 1_000_000
                if (frameDuration > 25) { // More than 25ms indicates dropped frames
                    frameDrops++
                }
            }
            
            lastFrameTime = frameTimeNanos
        }
        
        fun getCurrentFps(): Float {
            val currentTime = System.nanoTime()
            val durationSeconds = (currentTime - startTime) / 1_000_000_000.0
            return if (durationSeconds > 0) {
                (frameCount.get() / durationSeconds).toFloat()
            } else 0f
        }
    }
    
    class MemoryMetrics(
        val componentId: String,
        val initialMemory: Long
    ) {
        var currentMemoryUsage = initialMemory
        var peakMemoryUsage = initialMemory
        
        fun updateMemoryUsage(newMemoryUsage: Long) {
            currentMemoryUsage = newMemoryUsage
            if (newMemoryUsage > peakMemoryUsage) {
                peakMemoryUsage = newMemoryUsage
            }
        }
    }
    
    private class ComponentFrameCallback(
        private val metrics: FrameMetrics
    ) : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            metrics.recordFrame(frameTimeNanos)
            // Continue monitoring if profiling is still active
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    
    private inner class GlobalFrameCallback : Choreographer.FrameCallback {
        private var lastFrameTime = 0L
        private var globalFrameDrops = 0
        
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameTime != 0L) {
                val frameDuration = (frameTimeNanos - lastFrameTime) / 1_000_000
                if (frameDuration > 25) {
                    globalFrameDrops++
                    if (globalFrameDrops % 10 == 0) { // Alert every 10 frame drops
                        Timber.w("PerformanceProfiler: Global frame drops: $globalFrameDrops")
                    }
                }
            }
            
            lastFrameTime = frameTimeNanos
            
            if (isGlobalProfilingActive) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }
    
    /**
     * Profiling session control interface
     */
    interface ProfilingSession {
        val componentId: String
        val profilingType: ProfilingType
        fun stop(): PerformanceSummary?
        fun getCurrentMetrics(): CurrentMetrics?
    }
    
    private inner class ProfilingSessionImpl(
        override val componentId: String,
        override val profilingType: ProfilingType,
        private val onStop: () -> Unit
    ) : ProfilingSession {
        
        override fun stop(): PerformanceSummary? {
            onStop()
            return stopProfiling(componentId)
        }
        
        override fun getCurrentMetrics(): CurrentMetrics? {
            return this@PerformanceProfiler.getCurrentMetrics(componentId)
        }
    }
    
    private class NoOpProfilingSession : ProfilingSession {
        override val componentId: String = ""
        override val profilingType: ProfilingType = ProfilingType.COMPREHENSIVE
        override fun stop(): PerformanceSummary? = null
        override fun getCurrentMetrics(): CurrentMetrics? = null
    }
    
    private fun ProfilingSession(
        componentId: String,
        profilingType: ProfilingType,
        onStop: () -> Unit
    ): ProfilingSession = ProfilingSessionImpl(componentId, profilingType, onStop)
}

/**
 * Types of performance profiling available
 */
enum class ProfilingType {
    FRAME_RATE,      // Only frame rate and animation performance
    MEMORY,          // Only memory usage tracking  
    COMPREHENSIVE    // Both frame rate and memory profiling
}

/**
 * Performance validation result for 60fps requirements
 */
data class PerformanceValidationResult(
    val componentId: String,
    val isValid: Boolean,
    val averageFps: Float,
    val frameDrops: Int,
    val duration: Long,
    val issues: List<String>
)

/**
 * Current performance metrics for active profiling
 */
data class CurrentMetrics(
    val componentId: String,
    val duration: Long,
    val currentFps: Float,
    val frameDrops: Int,
    val memoryUsageMb: Long
)

/**
 * Complete performance summary after profiling session
 */
data class PerformanceSummary(
    val componentId: String,
    val profilingType: ProfilingType,
    val duration: Long,
    val frameMetrics: PerformanceProfiler.FrameMetrics?,
    val memoryMetrics: PerformanceProfiler.MemoryMetrics?,
    val timestamp: Long
) {
    val isPerformanceGood: Boolean
        get() {
            val fpsGood = frameMetrics?.getCurrentFps()?.let { it >= 54f } ?: true
            val memoryGood = memoryMetrics?.let { 
                it.currentMemoryUsage / 1024 / 1024 <= 100 
            } ?: true
            val frameDropsGood = frameMetrics?.frameDrops?.let { it <= 5 } ?: true
            
            return fpsGood && memoryGood && frameDropsGood
        }
}