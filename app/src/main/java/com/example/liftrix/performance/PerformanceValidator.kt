package com.example.liftrix.performance

import android.view.Choreographer
import com.example.liftrix.domain.service.AnalyticsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Performance validator for comprehensive 60fps verification and PRD success metrics validation.
 * 
 * Implements Choreographer API for frame-accurate measurement, task completion time tracking,
 * and cognitive load assessment based on interaction patterns and error rates.
 * 
 * Key features:
 * - Real-time 60fps monitoring during card interactions and screen transitions
 * - Task completion time measurement with cognitive load assessment
 * - Automated performance regression detection for CI/CD integration
 * - Analytics integration for PRD success metrics validation
 */
@Singleton
class PerformanceValidator @Inject constructor(
    private val choreographer: Choreographer,
    private val analyticsService: AnalyticsService
) {
    
    companion object {
        private const val TARGET_FPS = 60f
        private const val FRAME_TIME_TARGET_NS = 16_666_667L // 60fps = 16.67ms per frame
        internal const val ACCEPTABLE_FRAME_DROP_PERCENTAGE = 5.0 // 5% frame drops allowed
        internal const val MIN_ACCEPTABLE_FPS = TARGET_FPS * 0.9f // 54fps minimum
        
        // Task completion performance thresholds
        private const val TASK_COMPLETION_EXCELLENT_MS = 2000L
        private const val TASK_COMPLETION_GOOD_MS = 5000L
        private const val TASK_COMPLETION_POOR_MS = 10000L
        
        // Cognitive load thresholds based on interaction patterns
        private const val LOW_COGNITIVE_LOAD_INTERACTIONS = 3
        private const val MEDIUM_COGNITIVE_LOAD_INTERACTIONS = 7
        private const val HIGH_COGNITIVE_LOAD_INTERACTIONS = 15
    }
    
    private val activeMonitoringSessions = mutableMapOf<String, PerformanceMonitoringSession>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Start frame rate monitoring for a specific interaction or screen transition
     * @param sessionId Unique identifier for the monitoring session
     * @param interactionType Type of interaction being monitored (card_press, screen_transition, etc.)
     */
    fun startFrameMonitoring(sessionId: String, interactionType: String) {
        val session = PerformanceMonitoringSession(
            sessionId = sessionId,
            interactionType = interactionType,
            startTime = System.nanoTime()
        )
        
        activeMonitoringSessions[sessionId] = session
        choreographer.postFrameCallback(session.frameCallback)
        
        Timber.d("PerformanceValidator: Started monitoring $interactionType (session: $sessionId)")
    }
    
    /**
     * Stop frame rate monitoring and generate performance report
     * @param sessionId Session identifier to stop monitoring
     * @return Performance report with 60fps validation results
     */
    fun stopFrameMonitoring(sessionId: String): PerformanceReport {
        val session = activeMonitoringSessions.remove(sessionId)
            ?: return PerformanceReport.createErrorReport(sessionId, "Session not found")
        
        choreographer.removeFrameCallback(session.frameCallback)
        val report = session.generateReport()
        
        // Log analytics event for performance tracking
        coroutineScope.launch {
            analyticsService.logEvent("performance_validation", mapOf(
                "session_id" to sessionId,
                "interaction_type" to session.interactionType,
                "average_fps" to report.averageFps,
                "frame_drops" to report.frameDropCount,
                "frame_drop_percentage" to report.frameDropPercentage,
                "meets_60fps_target" to report.meets60FpsTarget,
                "duration_ms" to report.durationMs
            ))
        }
        
        if (!report.meets60FpsTarget) {
            Timber.w("PerformanceValidator: 60fps validation FAILED for ${session.interactionType} - " +
                    "${report.averageFps.roundToInt()}fps (${report.frameDropPercentage.roundToInt()}% drops)")
        } else {
            Timber.d("PerformanceValidator: 60fps validation PASSED for ${session.interactionType} - " +
                    "${report.averageFps.roundToInt()}fps")
        }
        
        return report
    }
    
    /**
     * Validate 60fps performance during card press animations and screen transitions
     * @param componentName Name of the component being validated
     * @param sessionId Monitoring session identifier
     * @return True if performance meets 60fps target
     */
    fun validate60FpsPerformance(componentName: String, sessionId: String): Boolean {
        val session = activeMonitoringSessions[sessionId] ?: return false
        val currentMetrics = session.getCurrentMetrics()
        
        val meets60FpsTarget = currentMetrics.averageFps >= MIN_ACCEPTABLE_FPS &&
                currentMetrics.frameDropPercentage <= ACCEPTABLE_FRAME_DROP_PERCENTAGE
        
        if (!meets60FpsTarget) {
            Timber.w("PerformanceValidator: $componentName performance below target - " +
                    "${currentMetrics.averageFps.roundToInt()}fps, " +
                    "${currentMetrics.frameDropPercentage.roundToInt()}% drops")
            
            // Log performance issue for analytics
            coroutineScope.launch {
                analyticsService.logEvent("performance_issue", mapOf(
                    "component_name" to componentName,
                    "issue_type" to "60fps_validation_failure",
                    "actual_fps" to currentMetrics.averageFps,
                    "frame_drop_percentage" to currentMetrics.frameDropPercentage,
                    "target_fps" to TARGET_FPS
                ))
            }
        }
        
        return meets60FpsTarget
    }
    
    /**
     * Start task completion time measurement for workflow efficiency tracking
     * @param taskId Unique task identifier
     * @param taskType Type of task (workout_creation, session_start, etc.)
     * @param workflowId Associated workflow identifier for UX metrics
     */
    fun startTaskCompletionTracking(taskId: String, taskType: String, workflowId: String? = null): TaskCompletionTracker {
        val tracker = TaskCompletionTracker(
            taskId = taskId,
            taskType = taskType,
            workflowId = workflowId,
            startTime = System.currentTimeMillis()
        )
        
        // Log workflow start if associated with UX metrics
        workflowId?.let {
            coroutineScope.launch {
                analyticsService.logUxWorkflowStart(
                    workflowId = it,
                    workflowType = taskType,
                    userId = "current_user" // TODO: Get actual user ID from auth
                )
            }
        }
        
        Timber.d("PerformanceValidator: Started task completion tracking - $taskType ($taskId)")
        return tracker
    }
    
    /**
     * Complete task completion tracking and calculate performance metrics
     * @param tracker Task completion tracker instance
     * @param successful Whether the task was completed successfully
     * @param interactionCount Number of user interactions during task
     * @param errorCount Number of errors encountered during task
     */
    fun completeTaskTracking(
        tracker: TaskCompletionTracker,
        successful: Boolean,
        interactionCount: Int,
        errorCount: Int = 0
    ) {
        val completionTime = System.currentTimeMillis() - tracker.startTime
        val efficiencyScore = calculateEfficiencyScore(completionTime, successful, interactionCount)
        val cognitiveLoadScore = calculateCognitiveLoadScore(interactionCount, errorCount, completionTime)
        
        // Log task completion metrics
        coroutineScope.launch {
            analyticsService.logTaskCompletionMetrics(
                taskId = tracker.taskId,
                taskType = tracker.taskType,
                completionStatus = if (successful) "completed" else "failed",
                completionTimeMs = completionTime,
                errorCount = errorCount,
                retryCount = if (errorCount > 0) 1 else 0
            )
            
            // Log workflow completion if associated
            tracker.workflowId?.let { workflowId ->
                analyticsService.logUxWorkflowCompletion(
                    workflowId = workflowId,
                    completionTimeMs = completionTime,
                    totalInteractions = interactionCount,
                    successful = successful,
                    efficiencyScore = efficiencyScore,
                    cognitiveLoadScore = cognitiveLoadScore
                )
            }
        }
        
        val performanceLevel = when {
            completionTime <= TASK_COMPLETION_EXCELLENT_MS -> "excellent"
            completionTime <= TASK_COMPLETION_GOOD_MS -> "good"
            completionTime <= TASK_COMPLETION_POOR_MS -> "acceptable"
            else -> "poor"
        }
        
        Timber.i("PerformanceValidator: Task completed - ${tracker.taskType} " +
                "($performanceLevel: ${completionTime}ms, efficiency: $efficiencyScore, " +
                "cognitive load: $cognitiveLoadScore)")
    }
    
    /**
     * Calculate efficiency score based on completion time, success rate, and interaction count
     * @param completionTimeMs Time taken to complete task
     * @param successful Whether task was completed successfully
     * @param interactionCount Number of user interactions
     * @return Efficiency score from 0.0 (poor) to 1.0 (excellent)
     */
    private fun calculateEfficiencyScore(
        completionTimeMs: Long,
        successful: Boolean,
        interactionCount: Int
    ): Double {
        if (!successful) return 0.0
        
        // Base score from completion time (0.0 to 0.6)
        val timeScore = when {
            completionTimeMs <= TASK_COMPLETION_EXCELLENT_MS -> 0.6
            completionTimeMs <= TASK_COMPLETION_GOOD_MS -> 0.4
            completionTimeMs <= TASK_COMPLETION_POOR_MS -> 0.2
            else -> 0.0
        }
        
        // Interaction efficiency score (0.0 to 0.4)
        val interactionScore = when {
            interactionCount <= LOW_COGNITIVE_LOAD_INTERACTIONS -> 0.4
            interactionCount <= MEDIUM_COGNITIVE_LOAD_INTERACTIONS -> 0.3
            interactionCount <= HIGH_COGNITIVE_LOAD_INTERACTIONS -> 0.2
            else -> 0.1
        }
        
        return (timeScore + interactionScore).coerceIn(0.0, 1.0)
    }
    
    /**
     * Calculate cognitive load score based on interaction patterns and error rates
     * @param interactionCount Number of user interactions
     * @param errorCount Number of errors encountered
     * @param completionTimeMs Time taken to complete task
     * @return Cognitive load score from 0.0 (low load) to 1.0 (high load)
     */
    private fun calculateCognitiveLoadScore(
        interactionCount: Int,
        errorCount: Int,
        completionTimeMs: Long
    ): Double {
        // Base cognitive load from interaction count (0.0 to 0.5)
        val interactionLoad = when {
            interactionCount <= LOW_COGNITIVE_LOAD_INTERACTIONS -> 0.1
            interactionCount <= MEDIUM_COGNITIVE_LOAD_INTERACTIONS -> 0.3
            interactionCount <= HIGH_COGNITIVE_LOAD_INTERACTIONS -> 0.5
            else -> 0.7
        }.coerceIn(0.0, 0.5)
        
        // Error-based cognitive load (0.0 to 0.3)
        val errorLoad = (errorCount * 0.1).coerceIn(0.0, 0.3)
        
        // Time-based cognitive load (0.0 to 0.2)
        val timeLoad = when {
            completionTimeMs <= TASK_COMPLETION_EXCELLENT_MS -> 0.0
            completionTimeMs <= TASK_COMPLETION_GOOD_MS -> 0.1
            completionTimeMs <= TASK_COMPLETION_POOR_MS -> 0.15
            else -> 0.2
        }
        
        return (interactionLoad + errorLoad + timeLoad).coerceIn(0.0, 1.0)
    }
    
    /**
     * Get performance summary for all active monitoring sessions
     * @return Map of session IDs to current performance metrics
     */
    fun getActiveMonitoringSummary(): Map<String, PerformanceMetrics> {
        return activeMonitoringSessions.mapValues { (_, session) ->
            session.getCurrentMetrics()
        }
    }
    
    /**
     * Clear all active monitoring sessions (useful for testing)
     */
    fun clearAllSessions() {
        activeMonitoringSessions.values.forEach { session ->
            choreographer.removeFrameCallback(session.frameCallback)
        }
        activeMonitoringSessions.clear()
        Timber.d("PerformanceValidator: Cleared all monitoring sessions")
    }
}

/**
 * Performance monitoring session for tracking frame rate during interactions
 */
private class PerformanceMonitoringSession(
    val sessionId: String,
    val interactionType: String,
    val startTime: Long
) {
    private val frameMetrics = mutableListOf<Long>()
    private var lastFrameTime = startTime
    
    val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val frameDuration = frameTimeNanos - lastFrameTime
            frameMetrics.add(frameDuration)
            lastFrameTime = frameTimeNanos
        }
    }
    
    fun getCurrentMetrics(): PerformanceMetrics {
        if (frameMetrics.isEmpty()) {
            return PerformanceMetrics(0f, 0, 0.0, 0L)
        }
        
        val frameTimesMs = frameMetrics.map { it / 1_000_000.0 }
        val averageFrameTime = frameTimesMs.average()
        val averageFps = 1000.0 / averageFrameTime
        
        val frameDrops = frameTimesMs.count { it > 16.67 } // 60fps = 16.67ms per frame
        val frameDropPercentage = (frameDrops.toDouble() / frameTimesMs.size) * 100.0
        val duration = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms
        
        return PerformanceMetrics(
            averageFps = averageFps.toFloat(),
            frameDropCount = frameDrops,
            frameDropPercentage = frameDropPercentage,
            durationMs = duration
        )
    }
    
    fun generateReport(): PerformanceReport {
        val metrics = getCurrentMetrics()
        val meets60FpsTarget = metrics.averageFps >= PerformanceValidator.MIN_ACCEPTABLE_FPS &&
                metrics.frameDropPercentage <= PerformanceValidator.ACCEPTABLE_FRAME_DROP_PERCENTAGE
        
        return PerformanceReport(
            sessionId = sessionId,
            interactionType = interactionType,
            averageFps = metrics.averageFps,
            frameDropCount = metrics.frameDropCount,
            frameDropPercentage = metrics.frameDropPercentage,
            durationMs = metrics.durationMs,
            meets60FpsTarget = meets60FpsTarget
        )
    }
}

/**
 * Performance metrics data class for current monitoring state
 */
data class PerformanceMetrics(
    val averageFps: Float,
    val frameDropCount: Int,
    val frameDropPercentage: Double,
    val durationMs: Long
)

/**
 * Performance report for completed monitoring sessions
 */
data class PerformanceReport(
    val sessionId: String,
    val interactionType: String,
    val averageFps: Float,
    val frameDropCount: Int,
    val frameDropPercentage: Double,
    val durationMs: Long,
    val meets60FpsTarget: Boolean,
    val error: String? = null
) {
    companion object {
        fun createErrorReport(sessionId: String, error: String): PerformanceReport {
            return PerformanceReport(
                sessionId = sessionId,
                interactionType = "unknown",
                averageFps = 0f,
                frameDropCount = 0,
                frameDropPercentage = 0.0,
                durationMs = 0L,
                meets60FpsTarget = false,
                error = error
            )
        }
    }
}

/**
 * Task completion tracker for measuring workflow efficiency and cognitive load
 */
data class TaskCompletionTracker(
    val taskId: String,
    val taskType: String,
    val workflowId: String?,
    val startTime: Long
)