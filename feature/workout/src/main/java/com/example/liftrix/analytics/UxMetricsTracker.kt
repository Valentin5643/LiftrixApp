package com.example.liftrix.analytics

import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.core.time.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UX Metrics Tracker for measuring user interaction efficiency and cognitive load.
 * 
 * This tracker implements measurement for PRD success criteria:
 * - 40% cognitive load reduction through interaction pattern analysis
 * - 30% task completion rate improvement through workflow efficiency tracking
 * - User satisfaction measurement through workflow success rates
 * 
 * Key Features:
 * - Workflow timing and interaction counting
 * - Cognitive load calculation based on interaction patterns
 * - Efficiency scoring for task completion optimization
 * - Integration with Firebase Analytics for data collection
 * - Real-time metrics collection during user workflows
 */
@Singleton
class UxMetricsTracker @Inject constructor(
    private val analyticsService: AnalyticsService,
    private val timeProvider: TimeProvider,
    private val applicationScope: CoroutineScope
) {
    
    companion object {
        // Events for UX metrics tracking
        private const val EVENT_WORKFLOW_STARTED = "ux_workflow_started"
        private const val EVENT_WORKFLOW_INTERACTION = "ux_workflow_interaction" 
        private const val EVENT_WORKFLOW_COMPLETED = "ux_workflow_completed"
        private const val EVENT_COGNITIVE_LOAD_MEASUREMENT = "ux_cognitive_load_measured"
        
        // Parameters
        private const val PARAM_WORKFLOW_ID = "workflow_id"
        private const val PARAM_INTERACTION_TYPE = "interaction_type"
        private const val PARAM_INTERACTION_COUNT = "interaction_count"
        private const val PARAM_COMPLETION_TIME_MS = "completion_time_ms"
        private const val PARAM_TOTAL_INTERACTIONS = "total_interactions"
        private const val PARAM_SUCCESSFUL = "successful"
        private const val PARAM_EFFICIENCY_SCORE = "efficiency_score"
        private const val PARAM_COGNITIVE_LOAD_SCORE = "cognitive_load_score"
        private const val PARAM_LOAD_CATEGORY = "load_category"
        
        // Cognitive load thresholds
        private const val LOW_COGNITIVE_LOAD_THRESHOLD = 2.0
        private const val MEDIUM_COGNITIVE_LOAD_THRESHOLD = 4.0
        
        // Efficiency calculation constants
        private const val TIME_WEIGHT = 0.1
        private const val INTERACTION_WEIGHT = 0.2
        private const val FAILURE_MULTIPLIER = 1.5
    }
    
    private val workflowStartTimes = mutableMapOf<String, Long>()
    private val interactionCounts = mutableMapOf<String, Int>()
    private val errorCounts = mutableMapOf<String, Int>()
    private val workflowLock = Any()
    
    /**
     * Starts tracking for a new workflow.
     * Records start time and initializes interaction counters.
     * 
     * @param workflowId Unique identifier for the workflow (e.g., "workout_creation", "active_session")
     */
    fun startWorkflowTracking(workflowId: String) {
        val currentTime = timeProvider.currentTimeMillis()
        synchronized(workflowLock) {
            workflowStartTimes[workflowId] = currentTime
            interactionCounts[workflowId] = 0
            errorCounts[workflowId] = 0
        }
        
        Timber.d("Started UX tracking for workflow: $workflowId at $currentTime")
        
        // Log workflow start event
        applicationScope.launch {
            analyticsService.logEvent(
                eventName = EVENT_WORKFLOW_STARTED,
                parameters = mapOf(
                    PARAM_WORKFLOW_ID to workflowId,
                    "timestamp" to currentTime
                )
            )
        }
    }
    
    /**
     * Tracks a user interaction within a workflow.
     * Increments interaction count and logs the interaction type.
     * 
     * @param workflowId The workflow being tracked
     * @param interactionType Type of interaction (e.g., "button_press", "text_input", "navigation")
     */
    fun trackInteraction(workflowId: String, interactionType: String) {
        val currentCount = synchronized(workflowLock) {
            val updated = interactionCounts.getOrDefault(workflowId, 0) + 1
            interactionCounts[workflowId] = updated
            updated
        }
        
        Timber.v("UX interaction in $workflowId: $interactionType (count: $currentCount)")
        
        applicationScope.launch {
            analyticsService.logEvent(
                eventName = EVENT_WORKFLOW_INTERACTION,
                parameters = mapOf(
                    PARAM_WORKFLOW_ID to workflowId,
                    PARAM_INTERACTION_TYPE to interactionType,
                    PARAM_INTERACTION_COUNT to currentCount
                )
            )
        }
    }
    
    /**
     * Tracks an error or retry within a workflow.
     * Used for cognitive load calculation.
     * 
     * @param workflowId The workflow being tracked
     * @param errorType Type of error encountered
     */
    fun trackError(workflowId: String, errorType: String) {
        val currentErrorCount = synchronized(workflowLock) {
            val updated = errorCounts.getOrDefault(workflowId, 0) + 1
            errorCounts[workflowId] = updated
            updated
        }
        
        Timber.w("UX error in $workflowId: $errorType (error count: $currentErrorCount)")
        
        applicationScope.launch {
            analyticsService.logEvent(
                eventName = "ux_workflow_error",
                parameters = mapOf(
                    PARAM_WORKFLOW_ID to workflowId,
                    "error_type" to errorType,
                    "error_count" to currentErrorCount
                )
            )
        }
    }
    
    /**
     * Completes workflow tracking and calculates metrics.
     * Logs comprehensive workflow completion data including efficiency and cognitive load scores.
     * 
     * @param workflowId The workflow being completed
     * @param successful Whether the workflow completed successfully
     */
    fun completeWorkflowTracking(workflowId: String, successful: Boolean) {
        val (startTime, totalInteractions, totalErrors) = synchronized(workflowLock) {
            Triple(
                workflowStartTimes[workflowId],
                interactionCounts[workflowId] ?: 0,
                errorCounts[workflowId] ?: 0
            )
        }
        if (startTime == null) {
            Timber.w("Attempting to complete workflow $workflowId that was never started")
            return
        }
        
        val completionTime = timeProvider.currentTimeMillis() - startTime
        
        // Calculate metrics
        val efficiencyScore = calculateEfficiencyScore(completionTime, totalInteractions, totalErrors)
        val cognitiveLoadScore = calculateCognitiveLoad(completionTime, totalInteractions, totalErrors, successful)
        val loadCategory = categorizeCognitiveLoad(cognitiveLoadScore)
        
        Timber.i("Completed UX tracking for $workflowId - Time: ${completionTime}ms, Interactions: $totalInteractions, Efficiency: $efficiencyScore, Cognitive Load: $cognitiveLoadScore")
        
        // Log workflow completion
        applicationScope.launch {
            analyticsService.logEvent(
                eventName = EVENT_WORKFLOW_COMPLETED,
                parameters = mapOf(
                    PARAM_WORKFLOW_ID to workflowId,
                    PARAM_COMPLETION_TIME_MS to completionTime,
                    PARAM_TOTAL_INTERACTIONS to totalInteractions,
                    PARAM_SUCCESSFUL to successful,
                    PARAM_EFFICIENCY_SCORE to efficiencyScore,
                    "error_count" to totalErrors
                )
            )
        }
        
        // Log cognitive load measurement
        applicationScope.launch {
            analyticsService.logEvent(
                eventName = EVENT_COGNITIVE_LOAD_MEASUREMENT,
                parameters = mapOf(
                    PARAM_WORKFLOW_ID to workflowId,
                    PARAM_COGNITIVE_LOAD_SCORE to cognitiveLoadScore,
                    PARAM_LOAD_CATEGORY to loadCategory
                )
            )
        }
        
        // Clean up tracking data
        synchronized(workflowLock) {
            workflowStartTimes.remove(workflowId)
            interactionCounts.remove(workflowId)
            errorCounts.remove(workflowId)
        }
    }
    
    /**
     * Calculates efficiency score based on completion time, interactions, and errors.
     * Higher score indicates more efficient workflow completion.
     * 
     * @param completionTime Time to complete workflow in milliseconds
     * @param interactions Total number of user interactions
     * @param errors Number of errors encountered
     * @return Efficiency score (higher is better)
     */
    private fun calculateEfficiencyScore(
        completionTime: Long, 
        interactions: Int, 
        errors: Int
    ): Double {
        // Base efficiency calculation
        val timeScore = 1.0 / (completionTime / 1000.0) // Inverse of seconds
        val interactionScore = 1.0 / maxOf(interactions.toDouble(), 1.0) // Fewer interactions is better
        val errorPenalty = errors * 0.1 // Each error reduces efficiency
        
        val baseScore = (timeScore + interactionScore) / 2.0
        val finalScore = maxOf(baseScore - errorPenalty, 0.1) // Minimum score of 0.1
        
        return String.format("%.3f", finalScore).toDouble()
    }
    
    /**
     * Calculates cognitive load score based on workflow complexity indicators.
     * Higher score indicates higher cognitive load (worse user experience).
     * 
     * @param completionTime Time to complete workflow in milliseconds
     * @param interactions Total number of user interactions
     * @param errors Number of errors encountered
     * @param successful Whether workflow completed successfully
     * @return Cognitive load score (lower is better)
     */
    private fun calculateCognitiveLoad(
        completionTime: Long, 
        interactions: Int, 
        errors: Int, 
        successful: Boolean
    ): Double {
        // Base cognitive load calculation
        val timeLoad = (completionTime / 1000.0) * TIME_WEIGHT
        val interactionLoad = interactions * INTERACTION_WEIGHT
        val errorLoad = errors * 0.3 // Errors significantly increase cognitive load
        
        val baseLoad = timeLoad + interactionLoad + errorLoad
        val failureMultiplier = if (successful) 1.0 else FAILURE_MULTIPLIER
        
        val finalLoad = baseLoad * failureMultiplier
        
        return String.format("%.3f", finalLoad).toDouble()
    }
    
    /**
     * Categorizes cognitive load score into human-readable categories.
     * 
     * @param score Cognitive load score
     * @return Category string ("low", "medium", "high")
     */
    private fun categorizeCognitiveLoad(score: Double): String {
        return when {
            score < LOW_COGNITIVE_LOAD_THRESHOLD -> "low"
            score < MEDIUM_COGNITIVE_LOAD_THRESHOLD -> "medium"
            else -> "high"
        }
    }
    
    /**
     * Gets current workflow metrics for debugging or real-time monitoring.
     * 
     * @param workflowId The workflow to get metrics for
     * @return Map of current metrics or null if workflow not active
     */
    fun getCurrentWorkflowMetrics(workflowId: String): Map<String, Any>? {
        val (startTime, interactions, errors) = synchronized(workflowLock) {
            Triple(
                workflowStartTimes[workflowId],
                interactionCounts[workflowId] ?: 0,
                errorCounts[workflowId] ?: 0
            )
        }
        if (startTime == null) return null
        val currentTime = timeProvider.currentTimeMillis()
        val elapsedTime = currentTime - startTime
        
        return mapOf(
            "workflow_id" to workflowId,
            "elapsed_time_ms" to elapsedTime,
            "interaction_count" to interactions,
            "error_count" to errors,
            "is_active" to true
        )
    }
}
