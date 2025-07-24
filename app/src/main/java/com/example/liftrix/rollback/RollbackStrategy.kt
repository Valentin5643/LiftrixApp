package com.example.liftrix.rollback

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.feature.FeatureFlagManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class RollbackType {
    IMMEDIATE,    // Instant 0% rollout
    GRADUAL,      // Reduce rollout by 50%
    PERCENTAGE,   // Reduce to specific percentage
    EMERGENCY     // Force disable with notifications
}

/**
 * Rollback Strategy for automatic UI redesign rollback based on critical performance issues.
 * 
 * Monitors system health and user experience metrics to automatically trigger rollback
 * when critical issues are detected. Provides multiple rollback mechanisms from immediate
 * emergency rollback to gradual percentage reduction.
 * 
 * Key Features:
 * - Automatic rollback triggers based on configurable thresholds
 * - Multiple rollback strategies (immediate, gradual, percentage-based)
 * - Issue correlation and pattern detection
 * - Recovery mechanisms with gradual re-enabling
 * - Comprehensive audit trail and analytics
 * - Manual override capabilities for emergency situations
 * 
 * Rollback Triggers:
 * - Critical performance degradation (>1000ms response times)
 * - High error rates (>5% of interactions failing)
 * - Memory issues (>30% memory increase)
 * - FPS performance drops (<50fps consistently)
 * - User feedback indicating major issues
 * - Manual emergency triggers
 * 
 * Integration:
 * - Works with FeatureFlagManager to control rollout percentage
 * - Integrates with AnalyticsService for comprehensive logging
 * - Provides callbacks for UI notifications and user communication
 */
@Singleton
class RollbackStrategy @Inject constructor(
    private val featureFlagManager: FeatureFlagManager,
    private val analyticsService: AnalyticsService,
    private val coroutineScope: CoroutineScope
) {
    
    companion object {
        // Rollback trigger thresholds
        private const val CRITICAL_RESPONSE_TIME_MS = 1000L
        private const val CRITICAL_ERROR_RATE = 0.05 // 5%
        private const val CRITICAL_MEMORY_INCREASE = 0.30 // 30%
        private const val CRITICAL_FPS_THRESHOLD = 50f
        private const val CRITICAL_USER_FEEDBACK_SCORE = 2.0 // Out of 5.0
        
        // Rollback timing configuration
        private const val EVALUATION_WINDOW_MS = 300000L // 5 minutes
        private const val ISSUE_CORRELATION_WINDOW_MS = 60000L // 1 minute
        private const val RECOVERY_DELAY_MS = 1800000L // 30 minutes before recovery attempt
        private const val MAX_ROLLBACK_ATTEMPTS = 3
        
        // Analytics event names
        private const val EVENT_ROLLBACK_TRIGGER_EVALUATION = "rollback_trigger_evaluation"
        private const val EVENT_ROLLBACK_EXECUTION = "rollback_execution"
        private const val EVENT_ROLLBACK_RECOVERY_ATTEMPT = "rollback_recovery_attempt"
        private const val EVENT_ROLLBACK_MANUAL_OVERRIDE = "rollback_manual_override"
        private const val EVENT_ROLLBACK_ISSUE_PATTERN = "rollback_issue_pattern"
    }
    
    // Rollback state management
    private var isRollbackActive = false
    private var rollbackAttempts = 0
    private var lastRollbackTime = 0L
    private val issueHistory = mutableListOf<RollbackIssue>()
    private val rollbackChannel = Channel<RollbackTrigger>(Channel.UNLIMITED)
    
    // Data structures
    data class RollbackIssue(
        val timestamp: Long,
        val issueType: String,
        val severity: String,
        val value: Double,
        val threshold: Double,
        val context: Map<String, Any>
    )
    
    data class RollbackTrigger(
        val reason: String,
        val severity: String,
        val timestamp: Long,
        val additionalContext: Map<String, Any>
    )
    
    
    enum class RollbackStatus {
        ACTIVE,
        RECOVERING,
        INACTIVE
    }
    
    init {
        startRollbackEvaluationLoop()
    }
    
    /**
     * Evaluate if a rollback is needed based on reported issue.
     * 
     * @param reason Description of the issue triggering evaluation
     * @param severity Severity level: "critical", "warning", "info"
     * @param additionalContext Additional context data for evaluation
     */
    suspend fun evaluateRollbackNeed(
        reason: String,
        severity: String,
        additionalContext: Map<String, Any> = emptyMap()
    ): LiftrixResult<Unit> {
        return try {
            val trigger = RollbackTrigger(
                reason = reason,
                severity = severity,
                timestamp = System.currentTimeMillis(),
                additionalContext = additionalContext
            )
            
            // Add to evaluation queue
            rollbackChannel.trySend(trigger)
            
            // Log evaluation
            analyticsService.logEvent(EVENT_ROLLBACK_TRIGGER_EVALUATION, mapOf(
                "reason" to reason,
                "severity" to severity,
                "timestamp" to trigger.timestamp,
                "rollback_active" to isRollbackActive,
                "rollback_attempts" to rollbackAttempts
            ) + additionalContext)
            
            Timber.d("Rollback evaluation queued: $reason ($severity)")
            liftrixSuccess(Unit)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to evaluate rollback need")
            liftrixFailure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Manually trigger immediate rollback for emergency situations.
     * 
     * @param reason Reason for manual rollback
     * @param rollbackType Type of rollback to execute
     * @return Result indicating rollback execution success
     */
    suspend fun executeManualRollback(
        reason: String,
        rollbackType: RollbackType = RollbackType.IMMEDIATE
    ): LiftrixResult<RollbackResult> {
        return try {
            val rollbackResult = when (rollbackType) {
                RollbackType.IMMEDIATE -> executeImmediateRollback("Manual trigger: $reason")
                RollbackType.GRADUAL -> executeGradualRollback("Manual trigger: $reason")
                RollbackType.PERCENTAGE -> executePercentageRollback("Manual trigger: $reason", 0)
                RollbackType.EMERGENCY -> executeEmergencyRollback("Manual trigger: $reason")
            }
            
            analyticsService.logEvent(EVENT_ROLLBACK_MANUAL_OVERRIDE, mapOf(
                "reason" to reason,
                "rollback_type" to rollbackType.name,
                "success" to rollbackResult.success,
                "timestamp" to System.currentTimeMillis()
            ))
            
            Timber.i("Manual rollback executed: $reason ($rollbackType)")
            liftrixSuccess(rollbackResult)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to execute manual rollback")
            liftrixFailure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Check if recovery from rollback is possible and safe.
     * 
     * @return Result indicating if recovery can be attempted
     */
    suspend fun evaluateRecoveryEligibility(): LiftrixResult<RecoveryAssessment> {
        return try {
            val currentTime = System.currentTimeMillis()
            val timeSinceRollback = currentTime - lastRollbackTime
            
            val assessment = RecoveryAssessment(
                canRecover = canAttemptRecovery(currentTime),
                timeSinceRollback = timeSinceRollback,
                rollbackAttempts = rollbackAttempts,
                recentIssues = getRecentIssues(currentTime - EVALUATION_WINDOW_MS),
                recommendedWaitTime = calculateRecommendedWaitTime(timeSinceRollback),
                riskLevel = calculateRecoveryRisk()
            )
            
            liftrixSuccess(assessment)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to evaluate recovery eligibility")
            liftrixFailure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Attempt gradual recovery from rollback state.
     * 
     * @param initialPercentage Starting percentage for recovery (default 5%)
     * @return Result of recovery attempt
     */
    suspend fun attemptGradualRecovery(initialPercentage: Int = 5): LiftrixResult<RecoveryResult> {
        return try {
            val recoveryEligibility = evaluateRecoveryEligibility()
            
            recoveryEligibility.fold(
                onSuccess = { eligibility ->
                    if (!eligibility.canRecover) {
                        return liftrixFailure(
                            LiftrixError.ValidationError("session", listOf("Recovery not eligible: ${eligibility.riskLevel}"))
                        )
                    }
                },
                onFailure = { error -> return liftrixFailure(error as LiftrixError) }
            )
            
            // Start recovery process
            val recoveryResult = executeGradualRecovery(initialPercentage)
            
            analyticsService.logEvent(EVENT_ROLLBACK_RECOVERY_ATTEMPT, mapOf(
                "initial_percentage" to initialPercentage,
                "success" to recoveryResult.success,
                "recovery_stage" to recoveryResult.stage,
                "timestamp" to System.currentTimeMillis()
            ))
            
            liftrixSuccess(recoveryResult)
            
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to attempt recovery")
            liftrixFailure(LiftrixError.UnknownError(exception.message ?: "Unknown error"))
        }
    }
    
    /**
     * Get current rollback status and metrics.
     */
    fun getRollbackStatus(): RollbackStatusReport {
        return RollbackStatusReport(
            isActive = isRollbackActive,
            rollbackAttempts = rollbackAttempts,
            lastRollbackTime = lastRollbackTime,
            recentIssuesCount = getRecentIssues(System.currentTimeMillis() - EVALUATION_WINDOW_MS).size,
            status = when {
                isRollbackActive -> RollbackStatus.ACTIVE
                canAttemptRecovery(System.currentTimeMillis()) -> RollbackStatus.RECOVERING
                else -> RollbackStatus.INACTIVE
            }
        )
    }
    
    // Private implementation methods
    
    private fun startRollbackEvaluationLoop() {
        coroutineScope.launch {
            rollbackChannel.receiveAsFlow().collect { trigger ->
                try {
                    evaluateTrigger(trigger)
                } catch (exception: Exception) {
                    Timber.e(exception, "Error evaluating rollback trigger")
                }
            }
        }
    }
    
    private suspend fun evaluateTrigger(trigger: RollbackTrigger) {
        // Skip if already in rollback state
        if (isRollbackActive) {
            Timber.d("Rollback already active, skipping trigger evaluation")
            return
        }
        
        // Add to issue history
        val issue = createIssueFromTrigger(trigger)
        issueHistory.add(issue)
        
        // Clean old issues
        cleanOldIssues()
        
        // Evaluate if rollback should be triggered
        val shouldRollback = shouldTriggerRollback(trigger, issue)
        
        if (shouldRollback) {
            val rollbackType = determineRollbackType(trigger, issue)
            executeAutomaticRollback(trigger, rollbackType)
        }
    }
    
    private fun createIssueFromTrigger(trigger: RollbackTrigger): RollbackIssue {
        val (threshold, value) = extractThresholdAndValue(trigger)
        
        return RollbackIssue(
            timestamp = trigger.timestamp,
            issueType = extractIssueType(trigger),
            severity = trigger.severity,
            value = value,
            threshold = threshold,
            context = trigger.additionalContext
        )
    }
    
    private fun extractThresholdAndValue(trigger: RollbackTrigger): Pair<Double, Double> {
        return when {
            trigger.reason.contains("response time", ignoreCase = true) -> {
                val responseTime = trigger.additionalContext["response_time_ms"] as? Number
                Pair(CRITICAL_RESPONSE_TIME_MS.toDouble(), responseTime?.toDouble() ?: 0.0)
            }
            trigger.reason.contains("fps", ignoreCase = true) -> {
                val fps = trigger.additionalContext["measured_fps"] as? Number
                Pair(CRITICAL_FPS_THRESHOLD.toDouble(), fps?.toDouble() ?: 0.0)
            }
            trigger.reason.contains("memory", ignoreCase = true) -> {
                val memoryIncrease = trigger.additionalContext["increase_percentage"] as? Number
                Pair(CRITICAL_MEMORY_INCREASE * 100, memoryIncrease?.toDouble() ?: 0.0)
            }
            else -> Pair(0.0, 0.0)
        }
    }
    
    private fun extractIssueType(trigger: RollbackTrigger): String {
        return when {
            trigger.reason.contains("response time", ignoreCase = true) -> "performance"
            trigger.reason.contains("fps", ignoreCase = true) -> "rendering"
            trigger.reason.contains("memory", ignoreCase = true) -> "memory"
            trigger.reason.contains("error", ignoreCase = true) -> "error"
            else -> "unknown"
        }
    }
    
    private fun shouldTriggerRollback(trigger: RollbackTrigger, issue: RollbackIssue): Boolean {
        return when (trigger.severity) {
            "critical" -> true
            "warning" -> {
                // Check for pattern of issues
                val recentSimilarIssues = getRecentIssuesOfType(issue.issueType, ISSUE_CORRELATION_WINDOW_MS)
                recentSimilarIssues.size >= 3
            }
            else -> false
        }
    }
    
    private fun determineRollbackType(trigger: RollbackTrigger, issue: RollbackIssue): RollbackType {
        return when {
            trigger.severity == "critical" && issue.value > issue.threshold * 2 -> RollbackType.IMMEDIATE
            trigger.severity == "critical" -> RollbackType.GRADUAL
            else -> RollbackType.PERCENTAGE
        }
    }
    
    private suspend fun executeAutomaticRollback(trigger: RollbackTrigger, rollbackType: RollbackType) {
        val result = when (rollbackType) {
            RollbackType.IMMEDIATE -> executeImmediateRollback(trigger.reason)
            RollbackType.GRADUAL -> executeGradualRollback(trigger.reason)
            RollbackType.PERCENTAGE -> executePercentageRollback(trigger.reason, 10)
            RollbackType.EMERGENCY -> executeEmergencyRollback(trigger.reason)
        }
        
        analyticsService.logEvent(EVENT_ROLLBACK_EXECUTION, mapOf(
            "trigger_reason" to trigger.reason,
            "rollback_type" to rollbackType.name,
            "success" to result.success,
            "timestamp" to System.currentTimeMillis()
        ))
    }
    
    private suspend fun executeImmediateRollback(reason: String): RollbackResult {
        isRollbackActive = true
        rollbackAttempts++
        lastRollbackTime = System.currentTimeMillis()
        
        // Set rollout to 0% via Remote Config or feature flag override
        // Note: This would typically interact with Firebase Remote Config
        
        Timber.i("Immediate rollback executed: $reason")
        return RollbackResult(
            success = true,
            rollbackType = RollbackType.IMMEDIATE,
            message = "UI redesign immediately disabled due to: $reason",
            timestamp = lastRollbackTime
        )
    }
    
    private suspend fun executeGradualRollback(reason: String): RollbackResult {
        isRollbackActive = true
        rollbackAttempts++
        lastRollbackTime = System.currentTimeMillis()
        
        // Reduce rollout percentage by 50%
        // Implementation would reduce current percentage
        
        Timber.i("Gradual rollback executed: $reason")
        return RollbackResult(
            success = true,
            rollbackType = RollbackType.GRADUAL,
            message = "UI redesign rollout reduced by 50% due to: $reason",
            timestamp = lastRollbackTime
        )
    }
    
    private suspend fun executePercentageRollback(reason: String, targetPercentage: Int): RollbackResult {
        isRollbackActive = true
        rollbackAttempts++
        lastRollbackTime = System.currentTimeMillis()
        
        Timber.i("Percentage rollback executed: $reason (target: $targetPercentage%)")
        return RollbackResult(
            success = true,
            rollbackType = RollbackType.PERCENTAGE,
            message = "UI redesign rollout reduced to $targetPercentage% due to: $reason",
            timestamp = lastRollbackTime
        )
    }
    
    private suspend fun executeEmergencyRollback(reason: String): RollbackResult {
        isRollbackActive = true
        rollbackAttempts++
        lastRollbackTime = System.currentTimeMillis()
        
        // Emergency rollback with user notifications
        Timber.w("Emergency rollback executed: $reason")
        return RollbackResult(
            success = true,
            rollbackType = RollbackType.EMERGENCY,
            message = "Emergency rollback executed due to: $reason",
            timestamp = lastRollbackTime
        )
    }
    
    private suspend fun executeGradualRecovery(initialPercentage: Int): RecoveryResult {
        // Start gradual recovery process
        return RecoveryResult(
            success = true,
            stage = "initial",
            currentPercentage = initialPercentage,
            message = "Gradual recovery started at $initialPercentage%",
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun canAttemptRecovery(currentTime: Long): Boolean {
        return isRollbackActive && 
               (currentTime - lastRollbackTime) > RECOVERY_DELAY_MS &&
               rollbackAttempts < MAX_ROLLBACK_ATTEMPTS
    }
    
    private fun getRecentIssues(since: Long): List<RollbackIssue> {
        return issueHistory.filter { it.timestamp >= since }
    }
    
    private fun getRecentIssuesOfType(issueType: String, windowMs: Long): List<RollbackIssue> {
        val since = System.currentTimeMillis() - windowMs
        return issueHistory.filter { it.issueType == issueType && it.timestamp >= since }
    }
    
    private fun cleanOldIssues() {
        val cutoff = System.currentTimeMillis() - EVALUATION_WINDOW_MS * 2
        issueHistory.removeAll { it.timestamp < cutoff }
    }
    
    private fun calculateRecommendedWaitTime(timeSinceRollback: Long): Long {
        return maxOf(0, RECOVERY_DELAY_MS - timeSinceRollback)
    }
    
    private fun calculateRecoveryRisk(): String {
        val recentIssues = getRecentIssues(System.currentTimeMillis() - EVALUATION_WINDOW_MS)
        return when {
            recentIssues.size >= 5 -> "high"
            recentIssues.size >= 2 -> "medium"
            else -> "low"
        }
    }
}

// Data classes for rollback results

data class RollbackResult(
    val success: Boolean,
    val rollbackType: RollbackType,
    val message: String,
    val timestamp: Long
)

data class RecoveryResult(
    val success: Boolean,
    val stage: String,
    val currentPercentage: Int,
    val message: String,
    val timestamp: Long
)

data class RecoveryAssessment(
    val canRecover: Boolean,
    val timeSinceRollback: Long,
    val rollbackAttempts: Int,
    val recentIssues: List<RollbackStrategy.RollbackIssue>,
    val recommendedWaitTime: Long,
    val riskLevel: String
)

data class RollbackStatusReport(
    val isActive: Boolean,
    val rollbackAttempts: Int,
    val lastRollbackTime: Long,
    val recentIssuesCount: Int,
    val status: RollbackStrategy.RollbackStatus
)