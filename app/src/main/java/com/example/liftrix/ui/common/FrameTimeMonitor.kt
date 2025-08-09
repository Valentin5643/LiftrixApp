package com.example.liftrix.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import android.view.Choreographer
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

/**
 * FrameTimeMonitor - Monitors frame rendering times to ensure 60fps target
 * 
 * Provides real-time frame time measurement and performance monitoring
 * to validate that chart animations meet the 60fps requirement.
 */
class FrameTimeMonitor {
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameTime = 0L
    private val frameTimesBuffer = mutableListOf<Long>()
    private val bufferSize = 60 // Track last 60 frames (1 second at 60fps)
    
    // Performance metrics
    var averageFrameTime = 0.0
        private set
    var maxFrameTime = 0L
        private set
    var minFrameTime = Long.MAX_VALUE
        private set
    var droppedFrames = 0
        private set
    var isMonitoring = false
        private set
    
    companion object {
        const val TARGET_FRAME_TIME_MS = 16L // 60fps target
        const val FRAME_DROP_THRESHOLD_MS = 32L // Frame considered dropped if > 32ms
    }
    
    /**
     * Start monitoring frame times
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isMonitoring) return
                
                val frameTimeMs = frameTimeNanos / 1_000_000
                
                if (lastFrameTime != 0L) {
                    val frameDuration = frameTimeMs - lastFrameTime
                    recordFrameTime(frameDuration)
                }
                
                lastFrameTime = frameTimeMs
                
                // Schedule next frame callback
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        
        Choreographer.getInstance().postFrameCallback(frameCallback)
        Timber.d("FrameTimeMonitor: Started monitoring")
    }
    
    /**
     * Stop monitoring frame times
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        isMonitoring = false
        frameCallback?.let {
            Choreographer.getInstance().removeFrameCallback(it)
        }
        frameCallback = null
        Timber.d("FrameTimeMonitor: Stopped monitoring - Avg: ${averageFrameTime}ms, Dropped: $droppedFrames")
    }
    
    /**
     * Record a frame time measurement
     */
    private fun recordFrameTime(frameTimeMs: Long) {
        // Add to buffer
        frameTimesBuffer.add(frameTimeMs)
        if (frameTimesBuffer.size > bufferSize) {
            frameTimesBuffer.removeAt(0)
        }
        
        // Update metrics
        updateMetrics()
        
        // Check for dropped frame
        if (frameTimeMs > FRAME_DROP_THRESHOLD_MS) {
            droppedFrames++
            Timber.w("FrameTimeMonitor: Dropped frame detected - ${frameTimeMs}ms")
        }
    }
    
    /**
     * Update performance metrics based on buffer
     */
    private fun updateMetrics() {
        if (frameTimesBuffer.isEmpty()) return
        
        averageFrameTime = frameTimesBuffer.average()
        maxFrameTime = frameTimesBuffer.maxOrNull() ?: 0L
        minFrameTime = frameTimesBuffer.minOrNull() ?: Long.MAX_VALUE
    }
    
    /**
     * Get current performance status
     */
    fun getPerformanceStatus(): PerformanceStatus {
        return when {
            averageFrameTime <= TARGET_FRAME_TIME_MS -> PerformanceStatus.EXCELLENT
            averageFrameTime <= TARGET_FRAME_TIME_MS * 1.5 -> PerformanceStatus.GOOD
            averageFrameTime <= FRAME_DROP_THRESHOLD_MS -> PerformanceStatus.ACCEPTABLE
            else -> PerformanceStatus.POOR
        }
    }
    
    /**
     * Reset all metrics
     */
    fun reset() {
        frameTimesBuffer.clear()
        averageFrameTime = 0.0
        maxFrameTime = 0L
        minFrameTime = Long.MAX_VALUE
        droppedFrames = 0
        lastFrameTime = 0L
    }
    
    /**
     * Get formatted performance report
     */
    fun getPerformanceReport(): String {
        return buildString {
            appendLine("=== Frame Time Performance Report ===")
            appendLine("Status: ${getPerformanceStatus()}")
            appendLine("Average Frame Time: %.2fms".format(averageFrameTime))
            appendLine("Min Frame Time: ${minFrameTime}ms")
            appendLine("Max Frame Time: ${maxFrameTime}ms")
            appendLine("Dropped Frames: $droppedFrames")
            appendLine("Sample Size: ${frameTimesBuffer.size} frames")
            appendLine("Target: ${TARGET_FRAME_TIME_MS}ms (60fps)")
        }
    }
}

/**
 * Performance status levels
 */
enum class PerformanceStatus {
    EXCELLENT,  // Consistently hitting 60fps
    GOOD,       // Mostly hitting 60fps with occasional drops
    ACCEPTABLE, // Some frame drops but still usable
    POOR        // Significant performance issues
}

/**
 * Composable helper to monitor frame times
 */
@Composable
fun rememberFrameTimeMonitor(): FrameTimeMonitor {
    val monitor = remember { FrameTimeMonitor() }
    
    DisposableEffect(Unit) {
        monitor.startMonitoring()
        
        onDispose {
            monitor.stopMonitoring()
            Timber.d(monitor.getPerformanceReport())
        }
    }
    
    return monitor
}

/**
 * Extension function to measure frame time for a specific operation
 */
inline fun <T> measureFrameTime(
    tag: String = "FrameTime",
    block: () -> T
): T {
    val startTime = System.nanoTime()
    val result = block()
    val endTime = System.nanoTime()
    
    val frameTimeMs = (endTime - startTime) / 1_000_000
    
    if (frameTimeMs > FrameTimeMonitor.TARGET_FRAME_TIME_MS) {
        Timber.w("$tag: Operation took ${frameTimeMs}ms (target: ${FrameTimeMonitor.TARGET_FRAME_TIME_MS}ms)")
    } else {
        Timber.v("$tag: Operation took ${frameTimeMs}ms")
    }
    
    return result
}