package com.example.liftrix.monitoring

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.rollback.RollbackStrategy
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI Redesign Monitor for comprehensive performance and user behavior tracking.
 * 
 * Monitors UI redesign performance impact, user interaction patterns, and system health
 * to ensure successful rollout and enable automatic rollback if issues are detected.
 * Integrates with existing AnalyticsService and triggers RollbackStrategy when necessary.
 * 
 * Key Monitoring Areas:
 * - UI interaction response times and performance degradation
 * - User behavior changes compared to baseline
 * - 60fps rendering compliance during redesign usage  
 * - Memory usage and system resource impact
 * - Error rates and crash frequency
 * - User satisfaction indicators (feedback, usage patterns)
 * 
 * Performance Thresholds:
 * - Critical: >1000ms interaction response time (triggers rollback evaluation)
 * - Warning: >500ms response time (increases monitoring frequency)
 * - Memory: >20% increase in memory usage (performance alert)
 * - FPS: <55fps average (performance degradation alert)
 * 
 * Integration:
 * - Works with existing AnalyticsServiceImpl for event tracking
 * - Triggers RollbackStrategy for automatic issue response
 * - Provides data for A/B testing effectiveness measurement
 */
@Singleton
class UiRedesignMonitor @Inject constructor(
    private val analyticsService: AnalyticsService,
    private val rollbackStrategy: RollbackStrategy,
    private val coroutineScope: CoroutineScope
) {
    
    companion object {
        // Performance threshold constants
        private const val CRITICAL_RESPONSE_TIME_MS = 1000L // 1 second - triggers rollback evaluation
        private const val WARNING_RESPONSE_TIME_MS = 500L  // 500ms - increases monitoring
        private const val TARGET_FPS = 60f
        private const val MINIMUM_FPS_THRESHOLD = 55f
        private const val MEMORY_INCREASE_THRESHOLD = 0.20 // 20% memory increase limit
        
        // Monitoring session configuration
        private const val SESSION_TIMEOUT_MS = 30000L // 30 seconds
        private const val BATCH_SIZE = 50 // Events to batch before sending
        private const val MONITORING_FREQUENCY_MS = 5000L // 5 seconds between checks
        
        // Analytics event names
        private const val EVENT_UI_REDESIGN_INTERACTION = "ui_redesign_interaction"
        private const val EVENT_UI_REDESIGN_PERFORMANCE = "ui_redesign_performance"
        private const val EVENT_UI_REDESIGN_BEHAVIOR_CHANGE = "ui_redesign_behavior_change"
        private const val EVENT_UI_REDESIGN_SESSION_SUMMARY = "ui_redesign_session_summary"
        private const val EVENT_UI_REDESIGN_ERROR = "ui_redesign_error"
        private const val EVENT_UI_REDESIGN_MEMORY_USAGE = "ui_redesign_memory_usage"
        private const val EVENT_UI_REDESIGN_FPS_MEASUREMENT = "ui_redesign_fps_measurement"
    }
    
    // Monitoring state
    private var isMonitoring = false
    private var currentSession: MonitoringSession? = null
    private val performanceMetrics = mutableListOf<PerformanceMetric>()
    private var baselineMetrics: BaselineMetrics? = null
    
    /**
     * Start monitoring UI redesign performance for a user session.
     * 
     * @param userId User identifier for session tracking
     * @param sessionId Unique session identifier
     * @param isUsingRedesign Whether user is in redesign group
     * @return Result indicating monitoring start success
     */
    suspend fun startMonitoring(
        userId: String,
        sessionId: String,
        isUsingRedesign: Boolean
    ): LiftrixResult<Unit> {
        return try {
            if (isMonitoring) {
                stopMonitoring() // Stop previous session
            }
            
            currentSession = MonitoringSession(
                userId = userId,
                sessionId = sessionId,
                isUsingRedesign = isUsingRedesign,
                startTime = System.currentTimeMillis()
            )
            
            isMonitoring = true
            performanceMetrics.clear()
            
            // Start background monitoring
            startBackgroundMonitoring()
            
            analyticsService.logEvent("ui_redesign_monitoring_started", mapOf(
                "user_id" to userId,
                "session_id" to sessionId,
                "using_redesign" to isUsingRedesign,
                "timestamp" to System.currentTimeMillis()
            ))
            
            Timber.d("UI redesign monitoring started for session: $sessionId (redesign: $isUsingRedesign)")
            Result.success(Unit)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to start UI redesign monitoring")
            Result.failure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Track a UI interaction with performance measurement.
     * 
     * @param interactionType Type of interaction (button_press, screen_transition, etc.)
     * @param responseTime Response time in milliseconds
     * @param successful Whether interaction completed successfully
     * @param componentName Name of UI component involved
     * @param additionalData Any additional context data
     */
    suspend fun trackUiInteraction(
        interactionType: String,
        responseTime: Long,
        successful: Boolean,
        componentName: String = "unknown",
        additionalData: Map<String, Any> = emptyMap()
    ): LiftrixResult<Unit> {
        return try {
            val session = currentSession ?: return Result.success(Unit)
            
            // Record performance metric
            val metric = PerformanceMetric(
                timestamp = System.currentTimeMillis(),
                interactionType = interactionType,
                responseTime = responseTime,
                successful = successful,
                componentName = componentName
            )
            performanceMetrics.add(metric)
            
            // Log interaction event
            analyticsService.logEvent(EVENT_UI_REDESIGN_INTERACTION, buildMap {
                put("user_id", session.userId)
                put("session_id", session.sessionId)
                put("using_redesign", session.isUsingRedesign)
                put("interaction_type", interactionType)
                put("response_time_ms", responseTime)
                put("successful", successful)
                put("component_name", componentName)
                put("timestamp", System.currentTimeMillis())
                putAll(additionalData)
            })
            
            // Check for performance issues
            when {
                responseTime > CRITICAL_RESPONSE_TIME_MS -> {
                    handleCriticalPerformanceIssue(interactionType, responseTime, session)
                }
                responseTime > WARNING_RESPONSE_TIME_MS -> {
                    handlePerformanceWarning(interactionType, responseTime, session)
                }
            }
            
            Timber.v("UI interaction tracked: $interactionType (${responseTime}ms, success: $successful)")
            Result.success(Unit)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track UI interaction")
            Result.failure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Track FPS performance during UI interactions.
     * 
     * @param averageFps Average frames per second measured
     * @param frameDrops Number of dropped frames
     * @param measurementDuration Duration of measurement in milliseconds
     * @param interactionContext Context of when measurement was taken
     */
    suspend fun trackFpsPerformance(
        averageFps: Float,
        frameDrops: Int,
        measurementDuration: Long,
        interactionContext: String
    ): LiftrixResult<Unit> {
        return try {
            val session = currentSession ?: return Result.success(Unit)
            
            analyticsService.logEvent(EVENT_UI_REDESIGN_FPS_MEASUREMENT, mapOf(
                "user_id" to session.userId,
                "session_id" to session.sessionId,
                "using_redesign" to session.isUsingRedesign,
                "average_fps" to averageFps,
                "frame_drops" to frameDrops,
                "measurement_duration_ms" to measurementDuration,
                "interaction_context" to interactionContext,
                "meets_target" to (averageFps >= TARGET_FPS),
                "timestamp" to System.currentTimeMillis()
            ))
            
            // Check for FPS performance issues
            if (averageFps < MINIMUM_FPS_THRESHOLD) {
                rollbackStrategy.evaluateRollbackNeed(
                    reason = "FPS performance below threshold: ${averageFps}fps (target: ${TARGET_FPS}fps)",
                    severity = if (averageFps < 45f) "critical" else "warning",
                    additionalContext = mapOf(
                        "measured_fps" to averageFps,
                        "target_fps" to TARGET_FPS,
                        "interaction_context" to interactionContext
                    )
                )
            }
            
            Result.success(Unit)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track FPS performance")
            Result.failure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Track memory usage changes during UI redesign usage.
     * 
     * @param memoryUsageMb Current memory usage in MB
     * @param baselineMemoryMb Baseline memory usage for comparison
     */
    suspend fun trackMemoryUsage(
        memoryUsageMb: Float,
        baselineMemoryMb: Float
    ): LiftrixResult<Unit> {
        return try {
            val session = currentSession ?: return Result.success(Unit)
            
            val memoryIncrease = (memoryUsageMb - baselineMemoryMb) / baselineMemoryMb
            
            analyticsService.logEvent(EVENT_UI_REDESIGN_MEMORY_USAGE, mapOf(
                "user_id" to session.userId,
                "session_id" to session.sessionId,
                "using_redesign" to session.isUsingRedesign,
                "current_memory_mb" to memoryUsageMb,
                "baseline_memory_mb" to baselineMemoryMb,
                "memory_increase_percentage" to memoryIncrease * 100,
                "exceeds_threshold" to (memoryIncrease > MEMORY_INCREASE_THRESHOLD),
                "timestamp" to System.currentTimeMillis()
            ))
            
            // Alert if memory usage exceeds threshold
            if (memoryIncrease > MEMORY_INCREASE_THRESHOLD) {
                rollbackStrategy.evaluateRollbackNeed(
                    reason = "Memory usage increased by ${(memoryIncrease * 100).toInt()}% (threshold: ${(MEMORY_INCREASE_THRESHOLD * 100).toInt()}%)",
                    severity = "warning",
                    additionalContext = mapOf(
                        "memory_usage_mb" to memoryUsageMb,
                        "baseline_memory_mb" to baselineMemoryMb,
                        "increase_percentage" to memoryIncrease * 100
                    )
                )
            }
            
            Result.success(Unit)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track memory usage")
            Result.failure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Track user behavior changes compared to baseline.
     * 
     * @param behaviorMetrics Current behavior metrics
     * @param comparisonBaseline Baseline metrics for comparison
     */
    suspend fun trackBehaviorChanges(
        behaviorMetrics: BehaviorMetrics,
        comparisonBaseline: BehaviorMetrics?
    ): LiftrixResult<Unit> {
        return try {
            val session = currentSession ?: return Result.success(Unit)
            
            val behaviorData = mutableMapOf<String, Any>(
                "user_id" to session.userId,
                "session_id" to session.sessionId,
                "using_redesign" to session.isUsingRedesign,
                "session_duration_ms" to behaviorMetrics.sessionDuration,
                "screen_transitions" to behaviorMetrics.screenTransitions,
                "error_count" to behaviorMetrics.errorCount,
                "task_completion_rate" to behaviorMetrics.taskCompletionRate,
                "timestamp" to System.currentTimeMillis()
            )
            
            // Add comparison data if baseline available
            comparisonBaseline?.let { baseline ->
                val durationChange = ((behaviorMetrics.sessionDuration - baseline.sessionDuration) / baseline.sessionDuration.toDouble()) * 100
                val errorChange = behaviorMetrics.errorCount - baseline.errorCount
                val completionChange = behaviorMetrics.taskCompletionRate - baseline.taskCompletionRate
                
                behaviorData.putAll(mapOf(
                    "baseline_session_duration_ms" to baseline.sessionDuration,
                    "baseline_error_count" to baseline.errorCount,
                    "baseline_completion_rate" to baseline.taskCompletionRate,
                    "session_duration_change_percent" to durationChange,
                    "error_count_change" to errorChange,
                    "completion_rate_change" to completionChange
                ))
            }
            
            analyticsService.logEvent(EVENT_UI_REDESIGN_BEHAVIOR_CHANGE, behaviorData)
            
            Result.success(Unit)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to track behavior changes")
            Result.failure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Stop monitoring and generate session summary.
     */
    suspend fun stopMonitoring(): LiftrixResult<MonitoringSessionSummary> {
        return try {
            val session = currentSession ?: return Result.failure(
                LiftrixError.ValidationError("session", listOf("No active monitoring session"))
            )
            
            isMonitoring = false
            val endTime = System.currentTimeMillis()
            val sessionDuration = endTime - session.startTime
            
            // Generate session summary
            val summary = generateSessionSummary(session, sessionDuration)
            
            // Log session summary
            analyticsService.logEvent(EVENT_UI_REDESIGN_SESSION_SUMMARY, mapOf(
                "user_id" to session.userId,
                "session_id" to session.sessionId,
                "using_redesign" to session.isUsingRedesign,
                "session_duration_ms" to sessionDuration,
                "total_interactions" to performanceMetrics.size,
                "average_response_time_ms" to summary.averageResponseTime,
                "successful_interactions" to summary.successfulInteractions,
                "performance_issues" to summary.performanceIssues,
                "timestamp" to endTime
            ))
            
            // Clean up
            currentSession = null
            performanceMetrics.clear()
            
            Timber.i("UI redesign monitoring stopped for session: ${session.sessionId}")
            Result.success(summary)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to stop monitoring")
            Result.failure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    // Private helper methods
    
    private suspend fun handleCriticalPerformanceIssue(
        interactionType: String,
        responseTime: Long,
        session: MonitoringSession
    ) {
        rollbackStrategy.evaluateRollbackNeed(
            reason = "Critical performance degradation detected: $interactionType took ${responseTime}ms",
            severity = "critical",
            additionalContext = mapOf(
                "interaction_type" to interactionType,
                "response_time_ms" to responseTime,
                "threshold_ms" to CRITICAL_RESPONSE_TIME_MS,
                "session_id" to session.sessionId,
                "using_redesign" to session.isUsingRedesign
            )
        )
        
        Timber.w("Critical UI performance issue: $interactionType took ${responseTime}ms (threshold: ${CRITICAL_RESPONSE_TIME_MS}ms)")
    }
    
    private suspend fun handlePerformanceWarning(
        interactionType: String,
        responseTime: Long,
        session: MonitoringSession
    ) {
        analyticsService.logEvent("ui_redesign_performance_warning", mapOf(
            "user_id" to session.userId,
            "session_id" to session.sessionId,
            "interaction_type" to interactionType,
            "response_time_ms" to responseTime,
            "threshold_ms" to WARNING_RESPONSE_TIME_MS,
            "timestamp" to System.currentTimeMillis()
        ))
        
        Timber.w("UI performance warning: $interactionType took ${responseTime}ms")
    }
    
    private fun startBackgroundMonitoring() {
        coroutineScope.launch {
            while (isMonitoring) {
                try {
                    // Perform periodic monitoring checks
                    delay(MONITORING_FREQUENCY_MS)
                    // Could check memory usage, FPS, etc. here
                } catch (exception: Exception) {
                    Timber.e(exception, "Error in background monitoring")
                }
            }
        }
    }
    
    private fun generateSessionSummary(
        session: MonitoringSession,
        sessionDuration: Long
    ): MonitoringSessionSummary {
        val totalInteractions = performanceMetrics.size
        val successfulInteractions = performanceMetrics.count { it.successful }
        val averageResponseTime = if (performanceMetrics.isNotEmpty()) {
            performanceMetrics.map { it.responseTime }.average()
        } else 0.0
        val performanceIssues = performanceMetrics.count { it.responseTime > WARNING_RESPONSE_TIME_MS }
        
        return MonitoringSessionSummary(
            sessionId = session.sessionId,
            userId = session.userId,
            isUsingRedesign = session.isUsingRedesign,
            sessionDuration = sessionDuration,
            totalInteractions = totalInteractions,
            successfulInteractions = successfulInteractions,
            averageResponseTime = averageResponseTime,
            performanceIssues = performanceIssues
        )
    }
}

// Data classes for monitoring

data class MonitoringSession(
    val userId: String,
    val sessionId: String,
    val isUsingRedesign: Boolean,
    val startTime: Long
)

data class PerformanceMetric(
    val timestamp: Long,
    val interactionType: String,
    val responseTime: Long,
    val successful: Boolean,
    val componentName: String
)

data class BaselineMetrics(
    val averageResponseTime: Double,
    val averageSessionDuration: Long,
    val errorRate: Double,
    val completionRate: Double
)

data class BehaviorMetrics(
    val sessionDuration: Long,
    val screenTransitions: Int,
    val errorCount: Int,
    val taskCompletionRate: Double
)

data class MonitoringSessionSummary(
    val sessionId: String,
    val userId: String,
    val isUsingRedesign: Boolean,
    val sessionDuration: Long,
    val totalInteractions: Int,
    val successfulInteractions: Int,
    val averageResponseTime: Double,
    val performanceIssues: Int
)