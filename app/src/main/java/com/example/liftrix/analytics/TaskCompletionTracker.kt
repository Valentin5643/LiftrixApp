package com.example.liftrix.analytics

import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.core.time.TimeProvider
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Task Completion Tracker for measuring workflow completion rates and success metrics.
 * 
 * This tracker implements measurement for PRD success criteria:
 * - 30% task completion rate improvement tracking
 * - Task success/failure analytics with detailed context
 * - Baseline comparison metrics for pre/post redesign measurement
 * 
 * Key Features:
 * - Task lifecycle tracking (start, progress, completion)
 * - Completion rate calculation by task type and time period
 * - Error tracking and retry pattern analysis
 * - Success metric validation for PRD compliance
 * - Integration with analytics backend for historical comparison
 */
@Singleton
class TaskCompletionTracker @Inject constructor(
    private val analyticsService: AnalyticsService,
    private val timeProvider: TimeProvider
) {
    
    companion object {
        // Task completion events
        private const val EVENT_TASK_STARTED = "task_completion_started"
        private const val EVENT_TASK_PROGRESS = "task_completion_progress"
        private const val EVENT_TASK_COMPLETED = "task_completion_finished"
        private const val EVENT_TASK_ABANDONED = "task_completion_abandoned"
        
        // Task completion parameters
        private const val PARAM_TASK_ID = "task_id"
        private const val PARAM_TASK_TYPE = "task_type"
        private const val PARAM_COMPLETION_STATUS = "completion_status"
        private const val PARAM_COMPLETION_TIME_MS = "completion_time_ms"
        private const val PARAM_ERROR_COUNT = "error_count"
        private const val PARAM_RETRY_COUNT = "retry_count"
        private const val PARAM_PROGRESS_PERCENTAGE = "progress_percentage"
        private const val PARAM_ABANDONMENT_REASON = "abandonment_reason"
        
        // Completion status values
        const val STATUS_SUCCESS = "success"
        const val STATUS_PARTIAL_SUCCESS = "partial_success"
        const val STATUS_FAILURE = "failure"
        const val STATUS_ABANDONED = "abandoned"
        
        // Task types for PRD success measurement
        const val TASK_WORKOUT_CREATION = "workout_creation"
        const val TASK_WORKOUT_START = "workout_start"
        const val TASK_ACTIVE_SESSION = "active_session_management"
        const val TASK_HISTORICAL_EDITING = "historical_data_editing"
        const val TASK_NAVIGATION_FLOW = "navigation_flow"
    }
    
    private val activeTaskData = mutableMapOf<String, TaskData>()
    
    /**
     * Starts tracking for a new task.
     * Records task start time and initializes completion tracking.
     * 
     * @param taskId Unique identifier for this specific task instance
     * @param taskType Type of task being performed (e.g., TASK_WORKOUT_CREATION)
     */
    fun trackTaskStart(taskId: String, taskType: String) {
        val currentTime = timeProvider.currentTimeMillis()
        val taskData = TaskData(
            taskId = taskId,
            taskType = taskType,
            startTime = currentTime,
            errorCount = 0,
            retryCount = 0
        )
        
        activeTaskData[taskId] = taskData
        
        Timber.d("Started task completion tracking: $taskType ($taskId) at $currentTime")
        
        GlobalScope.launch {
            analyticsService.logEvent(
                eventName = EVENT_TASK_STARTED,
                parameters = mapOf(
                    PARAM_TASK_ID to taskId,
                    PARAM_TASK_TYPE to taskType,
                    "timestamp" to currentTime
                )
            )
        }
    }
    
    /**
     * Tracks task progress updates.
     * Used for measuring partial completion and abandonment points.
     * 
     * @param taskId The task being tracked
     * @param progressPercentage Completion percentage (0-100)
     */
    fun trackTaskProgress(taskId: String, progressPercentage: Int) {
        val taskData = activeTaskData[taskId]
        if (taskData == null) {
            Timber.w("Attempting to track progress for unknown task: $taskId")
            return
        }
        
        Timber.v("Task progress for ${taskData.taskType}: $progressPercentage%")
        
        GlobalScope.launch {
            analyticsService.logEvent(
                eventName = EVENT_TASK_PROGRESS,
                parameters = mapOf(
                    PARAM_TASK_ID to taskId,
                    PARAM_TASK_TYPE to taskData.taskType,
                    PARAM_PROGRESS_PERCENTAGE to progressPercentage
                )
            )
        }
    }
    
    /**
     * Tracks task retry attempts.
     * Used for measuring workflow friction and cognitive load.
     * 
     * @param taskId The task being tracked
     * @param retryReason Reason for the retry
     */
    fun trackTaskRetry(taskId: String, retryReason: String) {
        val taskData = activeTaskData[taskId] ?: return
        taskData.retryCount++
        
        Timber.i("Task retry for ${taskData.taskType}: $retryReason (retry #${taskData.retryCount})")
        
        GlobalScope.launch {
            analyticsService.logEvent(
                eventName = "task_retry",
                parameters = mapOf(
                    PARAM_TASK_ID to taskId,
                    PARAM_TASK_TYPE to taskData.taskType,
                    PARAM_RETRY_COUNT to taskData.retryCount,
                    "retry_reason" to retryReason
                )
            )
        }
    }
    
    /**
     * Tracks task errors.
     * Used for measuring workflow friction and failure points.
     * 
     * @param taskId The task being tracked
     * @param errorType Type of error encountered
     */
    fun trackTaskError(taskId: String, errorType: String) {
        val taskData = activeTaskData[taskId] ?: return
        taskData.errorCount++
        
        Timber.w("Task error for ${taskData.taskType}: $errorType (error #${taskData.errorCount})")
        
        GlobalScope.launch {
            analyticsService.logEvent(
                eventName = "task_error",
                parameters = mapOf(
                    PARAM_TASK_ID to taskId,
                    PARAM_TASK_TYPE to taskData.taskType,
                    PARAM_ERROR_COUNT to taskData.errorCount,
                    "error_type" to errorType
                )
            )
        }
    }
    
    /**
     * Completes task tracking with final result.
     * Records completion metrics and calculates success indicators.
     * 
     * @param taskId The task being completed
     * @param taskType Task type for validation
     * @param completionResult Final result of the task
     */
    fun trackTaskCompletion(taskId: String, taskType: String, completionResult: TaskCompletionResult) {
        val taskData = activeTaskData[taskId]
        if (taskData == null) {
            Timber.w("Attempting to complete unknown task: $taskId")
            return
        }
        
        val completionTime = timeProvider.currentTimeMillis() - taskData.startTime
        
        Timber.i("Completed task ${taskData.taskType}: ${completionResult.status} in ${completionTime}ms with ${taskData.errorCount} errors and ${taskData.retryCount} retries")
        
        GlobalScope.launch {
            analyticsService.logEvent(
                eventName = EVENT_TASK_COMPLETED,
                parameters = mapOf(
                    PARAM_TASK_ID to taskId,
                    PARAM_TASK_TYPE to taskType,
                    PARAM_COMPLETION_STATUS to completionResult.status.name.lowercase(),
                    PARAM_COMPLETION_TIME_MS to completionTime,
                    PARAM_ERROR_COUNT to taskData.errorCount,
                    PARAM_RETRY_COUNT to taskData.retryCount
                )
            )
        }
        
        // Clean up tracking data
        activeTaskData.remove(taskId)
    }
    
    /**
     * Tracks task abandonment.
     * Important for measuring completion rate and identifying friction points.
     * 
     * @param taskId The task being abandoned
     * @param reason Reason for abandonment
     * @param progressPercentage How far the user got before abandoning
     */
    fun trackTaskAbandonment(taskId: String, reason: String, progressPercentage: Int = 0) {
        val taskData = activeTaskData[taskId]
        if (taskData == null) {
            Timber.w("Attempting to abandon unknown task: $taskId")
            return
        }
        
        val timeSpent = timeProvider.currentTimeMillis() - taskData.startTime
        
        Timber.w("Task abandoned: ${taskData.taskType} after ${timeSpent}ms at $progressPercentage% - Reason: $reason")
        
        GlobalScope.launch {
            analyticsService.logEvent(
                eventName = EVENT_TASK_ABANDONED,
                parameters = mapOf(
                    PARAM_TASK_ID to taskId,
                    PARAM_TASK_TYPE to taskData.taskType,
                    PARAM_ABANDONMENT_REASON to reason,
                    PARAM_PROGRESS_PERCENTAGE to progressPercentage,
                    "time_spent_ms" to timeSpent,
                    PARAM_ERROR_COUNT to taskData.errorCount,
                    PARAM_RETRY_COUNT to taskData.retryCount
                )
            )
        }
        
        // Clean up tracking data
        activeTaskData.remove(taskId)
    }
    
    /**
     * Calculates task completion rates for analytics and PRD validation.
     * This would typically query analytics backend for historical data.
     * 
     * @param taskType Type of task to calculate completion rate for
     * @param timeRange Time period to analyze
     * @return Task completion rate data
     */
    suspend fun getTaskCompletionRate(taskType: String, timeRange: TimeRange): LiftrixResult<TaskCompletionRate> {
        return try {
            // In a real implementation, this would query Firebase Analytics or local database
            // For now, returning mock data structure to demonstrate the concept
            
            Timber.d("Calculating completion rate for $taskType over $timeRange")
            
            // This would be replaced with actual analytics query
            val mockResult = TaskCompletionRate(
                taskType = taskType,
                totalAttempts = 100, // Would come from analytics query
                successfulCompletions = 85, // Would come from analytics query
                averageCompletionTime = 45000L, // Would come from analytics query
                timeRange = timeRange
            )
            
            // Analytics call within suspend function is already properly handled
            analyticsService.logEvent(
                eventName = "completion_rate_calculated",
                parameters = mapOf(
                    PARAM_TASK_TYPE to taskType,
                    "total_attempts" to mockResult.totalAttempts,
                    "successful_completions" to mockResult.successfulCompletions,
                    "completion_percentage" to mockResult.completionPercentage,
                    "time_range" to timeRange.toString()
                )
            )
            
            Result.success(mockResult)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate completion rate for $taskType")
            Result.failure(Exception("Failed to calculate completion rate: ${e.message}"))
        }
    }
    
    /**
     * Gets current active task metrics for monitoring.
     * 
     * @param taskId Task to get metrics for
     * @return Current task metrics or null if not active
     */
    fun getActiveTaskMetrics(taskId: String): Map<String, Any>? {
        val taskData = activeTaskData[taskId] ?: return null
        val currentTime = timeProvider.currentTimeMillis()
        val elapsedTime = currentTime - taskData.startTime
        
        return mapOf(
            "task_id" to taskData.taskId,
            "task_type" to taskData.taskType,
            "elapsed_time_ms" to elapsedTime,
            "error_count" to taskData.errorCount,
            "retry_count" to taskData.retryCount,
            "is_active" to true
        )
    }
}

/**
 * Data class for tracking task completion results.
 */
data class TaskCompletionResult(
    val status: CompletionStatus,
    val completionTime: Long,
    val errorCount: Int,
    val retryCount: Int,
    val additionalData: Map<String, Any> = emptyMap()
)

/**
 * Enumeration of possible task completion statuses.
 */
enum class CompletionStatus {
    SUCCESS,        // Task completed successfully
    PARTIAL_SUCCESS, // Task partially completed but user achieved main goal
    FAILURE,        // Task failed due to errors
    ABANDONED       // User abandoned task before completion
}

/**
 * Data class for task completion rate analysis.
 */
data class TaskCompletionRate(
    val taskType: String,
    val totalAttempts: Int,
    val successfulCompletions: Int,
    val averageCompletionTime: Long,
    val timeRange: TimeRange
) {
    /**
     * Calculates completion percentage for PRD success metric validation.
     */
    val completionPercentage: Double
        get() = if (totalAttempts > 0) {
            (successfulCompletions.toDouble() / totalAttempts.toDouble()) * 100.0
        } else {
            0.0
        }
    
    /**
     * Determines if this completion rate meets PRD target of 30% improvement.
     * Requires baseline comparison data.
     */
    fun meetsImprovementTarget(baselinePercentage: Double): Boolean {
        val improvementPercentage = completionPercentage - baselinePercentage
        return improvementPercentage >= 30.0
    }
}

/**
 * Data class for time range specification.
 */
data class TimeRange(
    val startTime: Long,
    val endTime: Long
) {
    override fun toString(): String {
        return "TimeRange(${endTime - startTime}ms)"
    }
}

/**
 * Internal data class for tracking active tasks.
 */
private data class TaskData(
    val taskId: String,
    val taskType: String,
    val startTime: Long,
    var errorCount: Int,
    var retryCount: Int
)