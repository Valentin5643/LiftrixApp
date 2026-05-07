package com.example.liftrix.analytics

import com.example.liftrix.data.service.ProfileCleanupService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects and reports metrics for cleanup operations.
 * 
 * This service provides:
 * - Detailed logging of cleanup operations
 * - Performance metrics collection
 * - Success/failure rate tracking
 * - Impact analysis for cleanup operations
 * - Historical cleanup data for trend analysis
 * 
 * Metrics are logged in a structured format for easy parsing and analysis.
 */
@Singleton
class CleanupMetricsCollector @Inject constructor() {
    
    companion object {
        // Structured log tags for easy filtering
        private const val CLEANUP_METRICS_TAG = "CLEANUP_METRICS"
        private const val CLEANUP_PERFORMANCE_TAG = "CLEANUP_PERFORMANCE"
        private const val CLEANUP_IMPACT_TAG = "CLEANUP_IMPACT"
        private const val CLEANUP_ERROR_TAG = "CLEANUP_ERROR"
    }
    
    /**
     * Metrics data class for structured logging
     *
     * IMPORTANT: Distinguishes between:
     * - trueOrphansFound: Server-verified deleted Auth accounts (authoritative)
     * - unverifiedFound: Client cannot verify due to security rules (not orphans)
     */
    data class CleanupMetrics(
        val operation: String,
        val userId: String?,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val durationMs: Long,
        val trueOrphansFound: Int,       // Server-verified orphans
        val unverifiedFound: Int,         // Client-limited (cannot verify)
        val orphanedRemoved: Int,
        val firestoreRemoved: Int,
        val roomRemoved: Int,
        val errorCount: Int,
        val errors: List<String>,
        val trigger: String, // "startup", "sync_failure", "manual", etc.
        val success: Boolean
    )
    
    /**
     * Records the start of a cleanup operation
     */
    fun recordCleanupStart(operation: String, userId: String?, trigger: String) {
        Timber.tag(CLEANUP_METRICS_TAG).i(
            "🧹 CLEANUP_START | operation=$operation | user_id=${userId ?: "all"} | trigger=$trigger | timestamp=${System.currentTimeMillis()}"
        )
    }
    
    /**
     * Records the completion of a cleanup operation with full metrics
     */
    fun recordCleanupCompletion(
        operation: String,
        userId: String?,
        trigger: String,
        result: ProfileCleanupService.CleanupResult,
        startTimeMs: Long
    ) {
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTimeMs
        val success = result.errors.isEmpty()
        
        val metrics = CleanupMetrics(
            operation = operation,
            userId = userId,
            startTimeMs = startTimeMs,
            endTimeMs = endTime,
            durationMs = duration,
            trueOrphansFound = result.trueOrphansFound,
            unverifiedFound = result.unverifiedProfilesFound,
            orphanedRemoved = result.orphanedProfilesRemoved,
            firestoreRemoved = result.firestoreDocumentsRemoved,
            roomRemoved = result.roomRecordsRemoved,
            errorCount = result.errors.size,
            errors = result.errors,
            trigger = trigger,
            success = success
        )

        // Log completion summary with proper categorization
        Timber.tag(CLEANUP_METRICS_TAG).i(
            "🧹 CLEANUP_COMPLETE | " +
            "operation=$operation | " +
            "user_id=${userId ?: "all"} | " +
            "trigger=$trigger | " +
            "duration_ms=$duration | " +
            "true_orphans=${result.trueOrphansFound} | " +
            "unverified=${result.unverifiedProfilesFound} | " +
            "removed=${result.orphanedProfilesRemoved} | " +
            "success=$success | " +
            "timestamp=$endTime"
        )
        
        // Log performance metrics
        recordPerformanceMetrics(metrics)
        
        // Log impact analysis
        recordImpactMetrics(metrics)
        
        // Log errors if any
        if (result.errors.isNotEmpty()) {
            recordErrorMetrics(metrics)
        }
        
        // Log success metrics for trend analysis
        if (success) {
            recordSuccessMetrics(metrics)
        }
    }
    
    /**
     * Records performance-specific metrics
     */
    private fun recordPerformanceMetrics(metrics: CleanupMetrics) {
        val totalProfilesChecked = metrics.trueOrphansFound + metrics.unverifiedFound
        val throughput = if (metrics.durationMs > 0) {
            (totalProfilesChecked * 1000) / metrics.durationMs // profiles per second
        } else 0

        Timber.tag(CLEANUP_PERFORMANCE_TAG).d(
            "🧹 CLEANUP_PERFORMANCE | " +
            "operation=${metrics.operation} | " +
            "duration_ms=${metrics.durationMs} | " +
            "true_orphans=${metrics.trueOrphansFound} | " +
            "unverified=${metrics.unverifiedFound} | " +
            "throughput_profiles_per_sec=$throughput | " +
            "firestore_ops=${metrics.firestoreRemoved} | " +
            "room_ops=${metrics.roomRemoved} | " +
            "timestamp=${metrics.endTimeMs}"
        )

        // Performance warning thresholds
        when {
            metrics.durationMs > 30000 -> { // > 30 seconds
                Timber.tag(CLEANUP_PERFORMANCE_TAG).w(
                    "🧹 SLOW_CLEANUP | operation=${metrics.operation} took ${metrics.durationMs}ms - consider optimization"
                )
            }
            metrics.trueOrphansFound > 100 -> {
                Timber.tag(CLEANUP_PERFORMANCE_TAG).w(
                    "🧹 HIGH_ORPHAN_COUNT | Found ${metrics.trueOrphansFound} TRUE orphaned profiles - investigate root cause"
                )
            }
            metrics.unverifiedFound > 100 -> {
                Timber.tag(CLEANUP_PERFORMANCE_TAG).i(
                    "🧹 HIGH_UNVERIFIED_COUNT | Found ${metrics.unverifiedFound} unverified profiles - client security-limited, server validation recommended"
                )
            }
        }
    }
    
    /**
     * Records impact analysis metrics
     */
    private fun recordImpactMetrics(metrics: CleanupMetrics) {
        val impactLevel = when {
            metrics.orphanedRemoved == 0 -> "NONE"
            metrics.orphanedRemoved <= 5 -> "LOW"
            metrics.orphanedRemoved <= 20 -> "MEDIUM"
            metrics.orphanedRemoved <= 50 -> "HIGH"
            else -> "CRITICAL"
        }
        
        val totalFound = metrics.trueOrphansFound + metrics.unverifiedFound
        val cleanupEfficiency = if (totalFound > 0) {
            (metrics.orphanedRemoved * 100) / totalFound
        } else 100
        
        Timber.tag(CLEANUP_IMPACT_TAG).i(
            "🧹 CLEANUP_IMPACT | " +
            "operation=${metrics.operation} | " +
            "impact_level=$impactLevel | " +
            "profiles_removed=${metrics.orphanedRemoved} | " +
            "cleanup_efficiency_percent=$cleanupEfficiency | " +
            "data_freed_firestore=${metrics.firestoreRemoved} | " +
            "data_freed_room=${metrics.roomRemoved} | " +
            "trigger=${metrics.trigger} | " +
            "timestamp=${metrics.endTimeMs}"
        )
        
        // Impact warnings
        if (impactLevel == "CRITICAL") {
            Timber.tag(CLEANUP_IMPACT_TAG).w(
                "🧹 CRITICAL_CLEANUP | Removed ${metrics.orphanedRemoved} orphaned profiles - investigate data integrity issues"
            )
        }
    }
    
    /**
     * Records error-specific metrics
     */
    private fun recordErrorMetrics(metrics: CleanupMetrics) {
        Timber.tag(CLEANUP_ERROR_TAG).e(
            "🧹 CLEANUP_ERRORS | " +
            "operation=${metrics.operation} | " +
            "error_count=${metrics.errorCount} | " +
            "timestamp=${metrics.endTimeMs}"
        )
        
        // Log individual errors for detailed analysis
        metrics.errors.forEachIndexed { index, error ->
            Timber.tag(CLEANUP_ERROR_TAG).e(
                "🧹 CLEANUP_ERROR_DETAIL | " +
                "operation=${metrics.operation} | " +
                "error_index=$index | " +
                "error_message=\"$error\" | " +
                "timestamp=${metrics.endTimeMs}"
            )
        }
    }
    
    /**
     * Records success-specific metrics for trend analysis
     */
    private fun recordSuccessMetrics(metrics: CleanupMetrics) {
        Timber.tag(CLEANUP_METRICS_TAG).i(
            "🧹 CLEANUP_SUCCESS | " +
            "operation=${metrics.operation} | " +
            "profiles_cleaned=${metrics.orphanedRemoved} | " +
            "duration_ms=${metrics.durationMs} | " +
            "trigger=${metrics.trigger} | " +
            "user_id=${metrics.userId ?: "system"} | " +
            "timestamp=${metrics.endTimeMs}"
        )
    }
    
    /**
     * Records sync worker failure that triggered cleanup
     */
    fun recordSyncWorkerCleanupTrigger(
        workerName: String,
        userId: String,
        failureReason: String,
        orphanDetected: Boolean
    ) {
        Timber.tag(CLEANUP_METRICS_TAG).w(
            "🧹 SYNC_CLEANUP_TRIGGER | " +
            "worker=$workerName | " +
            "user_id=$userId | " +
            "failure_reason=\"$failureReason\" | " +
            "orphan_detected=$orphanDetected | " +
            "timestamp=${System.currentTimeMillis()}"
        )
        
        // Special logging for PERMISSION_DENIED errors
        if (failureReason.contains("PERMISSION_DENIED", ignoreCase = true)) {
            recordPermissionDeniedError(workerName, userId)
        }
    }
    
    /**
     * Records PERMISSION_DENIED errors that indicate need for server-side cleanup
     */
    fun recordPermissionDeniedError(workerName: String, userId: String) {
        Timber.tag(CLEANUP_ERROR_TAG).e(
            "🧹 PERMISSION_DENIED_DETECTED | " +
            "worker=$workerName | " +
            "user_id=$userId | " +
            "requires_server_cleanup=true | " +
            "client_action=stopped_retries | " +
            "timestamp=${System.currentTimeMillis()}"
        )
        
        // Log server-side cleanup recommendation
        Timber.tag(CLEANUP_METRICS_TAG).w(
            "🧹 SERVER_CLEANUP_REQUIRED | " +
            "reason=PERMISSION_DENIED | " +
            "affected_user=$userId | " +
            "worker=$workerName | " +
            "recommendation=\"Implement Cloud Functions cleanup or run Admin SDK script\" | " +
            "documentation=\"See ServerSideCleanupGuide.kt for implementation details\" | " +
            "timestamp=${System.currentTimeMillis()}"
        )
    }
    
    /**
     * Records startup cleanup metrics
     */
    fun recordStartupCleanup(
        userId: String,
        cleanupTriggered: Boolean,
        result: ProfileCleanupService.CleanupResult?
    ) {
        Timber.tag(CLEANUP_METRICS_TAG).i(
            "🧹 STARTUP_CLEANUP | " +
            "user_id=$userId | " +
            "cleanup_triggered=$cleanupTriggered | " +
            "profiles_removed=${result?.orphanedProfilesRemoved ?: 0} | " +
            "timestamp=${System.currentTimeMillis()}"
        )
    }
    
    /**
     * Records system health metrics related to cleanup operations
     */
    fun recordSystemHealthMetrics(
        totalUsers: Int,
        activeUsers: Int,
        orphanedProfiles: Int,
        healthScore: Int,
        criticalIssues: List<String>
    ) {
        Timber.tag(CLEANUP_METRICS_TAG).i(
            "🧹 SYSTEM_HEALTH | " +
            "total_users=$totalUsers | " +
            "active_users=$activeUsers | " +
            "orphaned_profiles=$orphanedProfiles | " +
            "health_score=$healthScore | " +
            "critical_issues=${criticalIssues.size} | " +
            "timestamp=${System.currentTimeMillis()}"
        )
        
        // Log critical issues individually
        criticalIssues.forEachIndexed { index, issue ->
            Timber.tag(CLEANUP_METRICS_TAG).w(
                "🧹 CRITICAL_ISSUE | " +
                "issue_index=$index | " +
                "issue=\"$issue\" | " +
                "timestamp=${System.currentTimeMillis()}"
            )
        }
    }
    
    /**
     * Records manual cleanup operations triggered by developers/admins
     */
    fun recordManualCleanupRequest(
        requestedBy: String,
        operation: String,
        targetUserId: String?,
        dryRun: Boolean
    ) {
        Timber.tag(CLEANUP_METRICS_TAG).i(
            "🧹 MANUAL_CLEANUP_REQUEST | " +
            "requested_by=$requestedBy | " +
            "operation=$operation | " +
            "target_user=${targetUserId ?: "all"} | " +
            "dry_run=$dryRun | " +
            "timestamp=${System.currentTimeMillis()}"
        )
    }
    
    /**
     * Generates a summary report for cleanup operations over a time period
     * This could be called periodically to generate aggregated metrics
     */
    fun generateCleanupSummaryReport() {
        val timestamp = System.currentTimeMillis()
        
        Timber.tag(CLEANUP_METRICS_TAG).i(
            "🧹 CLEANUP_SUMMARY_REPORT | " +
            "report_type=summary | " +
            "timestamp=$timestamp | " +
            "note=\"Use log analysis tools to aggregate cleanup metrics by timestamp range\""
        )
        
        // In a production app, this could:
        // 1. Query cleanup metrics from the past week/month
        // 2. Calculate aggregated statistics
        // 3. Generate alerts for concerning trends
        // 4. Export metrics to external monitoring systems
    }
}