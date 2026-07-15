package com.example.liftrix.sync

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.example.liftrix.data.sync.OfflineQueueManager
import com.example.liftrix.config.OfflineArchitectureFlags
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Unified sync worker that replaces all specialized sync workers.
 * 
 * This worker:
 * - Handles all entity types (workouts, profiles, templates, achievements, social data)
 * - Uses priority-based processing through SyncOperationManager
 * - Integrates with existing OfflineQueueManager for conflict resolution
 * - Provides configurable sync strategies via input parameters
 * - Maintains compatibility with existing SyncCoordinator interface
 * 
 * Supported Sync Types:
 * - "all": Syncs all entities by priority (default)
 * - "workouts": Workouts only
 * - "social": Social data only  
 * - "profile": User profile only
 * - "priority": Syncs by specified maximum priority level
 * 
 * Input Parameters:
 * - userId: Required user ID for sync operations
 * - sync_type: Type of sync to perform (default: "all")
 * - max_priority: Maximum priority level to sync (default: 4 = all)
 * - force_sync: Force sync even if recently completed
 * - startup_sync: Special handling for app startup sync
 */
@HiltWorker
class UnifiedSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncOperationManager: SyncOperationManager,
    private val offlineQueueManager: OfflineQueueManager,
    private val startupRestoreGate: StartupRestoreGate
) : BaseSyncWorker(context, params) {

    override val workerName: String = "UnifiedSyncWorker"
    override val maxRetryCount: Int = 5
    
    companion object {
        const val WORK_NAME = "unified_sync_work"
        
        // Input parameter keys
        const val KEY_SYNC_TYPE = "sync_type"
        const val KEY_MAX_PRIORITY = "max_priority"
        const val KEY_FORCE_SYNC = "force_sync"
        const val KEY_STARTUP_SYNC = "startup_sync"
        const val KEY_FAILED_COUNT = "failed_operations"
        const val KEY_CONFLICT_COUNT = "conflict_operations"
        const val KEY_REQUIRED_FAILURE_COUNT = "required_failure_count"
        const val KEY_FAILURE_CATEGORIES = "failure_categories"
        
        // Default values
        const val DEFAULT_SYNC_TYPE = "all"
        const val DEFAULT_MAX_PRIORITY = SyncOperationManager.PRIORITY_LOW
        
        /**
         * Create work request for all entities sync
         */
        fun createWorkRequest(userId: String): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<UnifiedSyncWorker>()
                .setInputData(workDataOf(
                    KEY_USER_ID to userId,
                    KEY_SYNC_TYPE to DEFAULT_SYNC_TYPE,
                    KEY_MAX_PRIORITY to DEFAULT_MAX_PRIORITY
                ))
                .setConstraints(createDefaultConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag("unified_sync")
                .addTag("user_$userId")
                .build()
        }
        
        /**
         * Create work request for specific sync type
         */
        fun createWorkRequest(
            userId: String,
            syncType: String,
            maxPriority: Int = DEFAULT_MAX_PRIORITY,
            forceSync: Boolean = false,
            startupSync: Boolean = false
        ): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<UnifiedSyncWorker>()
                .setInputData(workDataOf(
                    KEY_USER_ID to userId,
                    KEY_SYNC_TYPE to syncType,
                    KEY_MAX_PRIORITY to maxPriority,
                    KEY_FORCE_SYNC to forceSync,
                    KEY_STARTUP_SYNC to startupSync
                ))
                .setConstraints(createConstraints(forceSync, startupSync))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    if (startupSync) 10 else 15, // Faster retry for startup
                    TimeUnit.SECONDS
                )
                .addTag("unified_sync")
                .addTag("sync_type_$syncType")
                .addTag("user_$userId")
                .build()
        }
        
        /**
         * Create high-priority work request for immediate sync
         */
        fun createImmediateWorkRequest(userId: String): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<UnifiedSyncWorker>()
                .setInputData(workDataOf(
                    KEY_USER_ID to userId,
                    KEY_SYNC_TYPE to DEFAULT_SYNC_TYPE,
                    KEY_MAX_PRIORITY to DEFAULT_MAX_PRIORITY,
                    KEY_FORCE_SYNC to true
                ))
                .setConstraints(createImmediateConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    5,
                    TimeUnit.SECONDS
                )
                .addTag("unified_sync")
                .addTag("immediate_sync")
                .addTag("user_$userId")
                .build()
        }
        
        /**
         * Get unique work name for a user and sync type
         */
        fun getWorkName(userId: String, syncType: String = DEFAULT_SYNC_TYPE): String {
            return "${WORK_NAME}_${syncType}_$userId"
        }
        
        /**
         * Create default constraints for sync operations
         */
        private fun createDefaultConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(false)
                .build()
        }
        
        /**
         * Create constraints based on sync parameters
         */
        private fun createConstraints(forceSync: Boolean, startupSync: Boolean): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(!forceSync && !startupSync) // Allow on low battery for critical syncs
                .setRequiresDeviceIdle(false)
                .build()
        }
        
        /**
         * Create constraints for immediate sync (least restrictive)
         */
        private fun createImmediateConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .setRequiresDeviceIdle(false)
                .build()
        }
    }

    override suspend fun performSync(userId: String): Result {
        try {
            val syncStartTime = System.currentTimeMillis()
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
            if (!OfflineArchitectureFlags.ROOM_FIRST_ENABLED) {
                Timber.w("$workerName running in legacy mode (Room-first disabled)")
            }
            Timber.d("$workerName dirty gating enabled: $useDirtyFlagGating")
            
            // Extract sync parameters from input data
            val syncType = inputData.getString(KEY_SYNC_TYPE) ?: DEFAULT_SYNC_TYPE
            val maxPriority = inputData.getInt(KEY_MAX_PRIORITY, DEFAULT_MAX_PRIORITY)
            val forceSync = inputData.getBoolean(KEY_FORCE_SYNC, false)
            val startupSync = inputData.getBoolean(KEY_STARTUP_SYNC, false)
            val outcome = SyncOutcomeAggregate()
            
            Timber.d("$workerName: Starting sync - type: $syncType, maxPriority: $maxPriority, force: $forceSync, startup: $startupSync")
            
            // Check authentication
            if (!syncOperationManager.validateAuthentication(userId)) {
                Timber.e("$workerName: User not authenticated for sync: $userId")
                outcome.operationProcessingFailures++
                outcome.failureCategories.add("authentication")
                val outputData = buildOutcomeData(
                    outcome = outcome,
                    syncDuration = System.currentTimeMillis() - syncStartTime,
                    syncType = syncType,
                    errorMessage = "User authentication failed",
                    requiredFailureCount = 1
                )
                if (startupSync) {
                    startupRestoreGate.transition(
                        userId = userId,
                        state = StartupRestoreState.RESTORE_FAILED,
                        reason = "unified_startup_authentication_failed"
                    )
                }
                return Result.failure(outputData)
            }
            
            // Step 1: Process offline queue first
            try {
                checkCancellation()
                
                val queueEntityTypes = queueEntityTypesForSyncType(syncType)
                Timber.d(
                    "$workerName: Processing offline queue for user $userId " +
                        "syncType=$syncType entityTypes=${queueEntityTypes ?: "ALL"}"
                )
                val queueResult = offlineQueueManager.processPendingQueue(
                    userId = userId,
                    entityTypes = queueEntityTypes
                )
                
                queueResult.fold(
                    onSuccess = { syncResult ->
                        outcome.queueSuccessful += syncResult.successful
                        outcome.queueFailed += syncResult.failed
                        outcome.queueConflicts += syncResult.conflicts
                        if (syncResult.failed > 0) {
                            outcome.failureCategories.add("offline_queue_items")
                        }
                        
                        Timber.d("$workerName: Offline queue processed - successful: ${syncResult.successful}, failed: ${syncResult.failed}, conflicts: ${syncResult.conflicts}")
                    },
                    onFailure = { error ->
                        outcome.queueProcessingFailures++
                        outcome.failureCategories.add("offline_queue_processing")
                        Timber.w(error, "$workerName: Failed to process offline queue")
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                outcome.queueProcessingFailures++
                outcome.failureCategories.add("offline_queue_processing")
                Timber.w(e, "$workerName: Error processing offline queue")
            }
            
            // Step 2: Process sync operations through SyncOperationManager
            try {
                checkCancellation()
                
                Timber.d("$workerName: Processing sync operations via SyncOperationManager")
                val operationResults = syncOperationManager.processSyncOperations(
                    userId = userId,
                    operationType = operationManagerSyncType(syncType),
                    maxPriority = maxPriority,
                    cancellationCheck = { checkCancellation() }
                )
                
                operationResults.fold(
                    onSuccess = { results ->
                        val successfulResults = results.count { it.success }
                        val failedResults = results.filter { !it.success }
                        outcome.operationSuccessful += successfulResults
                        outcome.operationFailed += failedResults.size

                        if (failedResults.isNotEmpty()) {
                            failedResults.forEach { result ->
                                outcome.failureCategories.add(
                                    "sync_operation_${result.operation.entityType.lowercase()}"
                                )
                            }
                            Timber.w("$workerName: Some operations failed:")
                            failedResults.forEach { result ->
                                Timber.w("  - ${result.operation.entityType}:${result.operation.entityId} - ${result.error}")
                            }
                        }
                        
                        Timber.d("$workerName: Sync operations completed - successful: ${results.count { it.success }}, failed: ${failedResults.size}")
                    },
                    onFailure = { error ->
                        Timber.e("$workerName: Sync operations failed: $error")
                        outcome.operationProcessingFailures++
                        outcome.failureCategories.add("sync_operation_processing")
                    }
                )
                
            } catch (e: CancellationException) {
                // Re-throw cancellation
                throw e
            } catch (e: Exception) {
                Timber.e(e, "$workerName: Error during sync operations")
                throw e
            }
            
            val syncDuration = System.currentTimeMillis() - syncStartTime
            val requiredFailureCount = outcome.requiredFailureCount(startupSync)
            val hasAcceptedOutcome = outcome.totalOperations == 0 || outcome.completedOperations > 0
            val isAccepted = requiredFailureCount == 0 && hasAcceptedOutcome

            when {
                outcome.totalOperations == 0 -> {
                    Timber.d("$workerName: No operations to sync for user $userId")
                }
                outcome.failedOperations == 0 -> {
                    Timber.d("$workerName: All operations successful for user $userId")
                }
                isAccepted -> {
                    Timber.w(
                        "$workerName: Accepted partial outcome - " +
                            "${outcome.successfulOperations}/${outcome.totalOperations} operations successful"
                    )
                }
                else -> {
                    Timber.e(
                        "$workerName: Required sync outcome failed for user $userId " +
                            "requiredFailures=$requiredFailureCount categories=${outcome.failureCategories}"
                    )
                }
            }

            Timber.i(
                "$workerName: Sync completed for user $userId - Duration: ${syncDuration}ms, " +
                    "Operations: ${outcome.successfulOperations}/${outcome.totalOperations} successful, " +
                    "failed=${outcome.failedOperations}, conflicts=${outcome.queueConflicts}"
            )

            val outputData = buildOutcomeData(
                outcome = outcome,
                syncDuration = syncDuration,
                syncType = syncType,
                errorMessage = if (isAccepted) null else "Required sync operations failed",
                requiredFailureCount = requiredFailureCount.coerceAtLeast(
                    if (hasAcceptedOutcome) 0 else outcome.failedOperations.coerceAtLeast(1)
                )
            )

            if (startupSync && isAccepted) {
                startupRestoreGate.transition(
                    userId = userId,
                    state = StartupRestoreState.RESTORE_COMPLETE,
                    reason = "unified_startup_sync_complete"
                )
                Timber.tag("StartupRestoreFix").i(
                    "operation=UNIFIED_STARTUP_RESTORE_COMPLETE userId=$userId syncType=$syncType " +
                        "successful=${outcome.successfulOperations} total=${outcome.totalOperations} timestamp=${System.currentTimeMillis()}"
                )
            }
            
            return if (isAccepted) {
                Result.success(outputData)
            } else if (runAttemptCount < maxRetryCount) {
                Timber.w(
                    "$workerName: Retrying required sync failure for user $userId " +
                        "attempt=${runAttemptCount + 1}/$maxRetryCount " +
                        "successful=${outcome.successfulOperations} failed=${outcome.failedOperations} " +
                        "categories=${outcome.failureCategories.joinToString(",")}"
                )
                Result.retry()
            } else {
                if (startupSync) {
                    startupRestoreGate.transition(
                        userId = userId,
                        state = StartupRestoreState.RESTORE_FAILED,
                        reason = "unified_startup_required_operations_failed"
                    )
                }
                Result.failure(outputData)
            }
            
        } catch (e: CancellationException) {
            // Re-throw cancellation to maintain cancellation chain
            Timber.d("$workerName: Sync cancelled for user $userId")
            throw e
        } catch (e: Exception) {
            // Let base class handle the error and retry logic
            Timber.e(e, "$workerName: Sync failed for user $userId")
            throw e
        }
    }

    private fun buildOutcomeData(
        outcome: SyncOutcomeAggregate,
        syncDuration: Long,
        syncType: String,
        errorMessage: String?,
        requiredFailureCount: Int
    ): Data {
        return Data.Builder()
            .putInt(KEY_SYNC_COUNT, outcome.successfulOperations)
            .putInt(KEY_FAILED_COUNT, outcome.failedOperations)
            .putInt(KEY_CONFLICT_COUNT, outcome.queueConflicts)
            .putInt(KEY_REQUIRED_FAILURE_COUNT, requiredFailureCount)
            .putString(KEY_FAILURE_CATEGORIES, outcome.failureCategories.joinToString(","))
            .putLong("sync_duration_ms", syncDuration)
            .putInt("total_operations", outcome.totalOperations)
            .putInt("queue_successful", outcome.queueSuccessful)
            .putInt("queue_failed", outcome.queueFailed + outcome.queueProcessingFailures)
            .putInt("operation_successful", outcome.operationSuccessful)
            .putInt("operation_failed", outcome.operationFailed + outcome.operationProcessingFailures)
            .putString("sync_type", syncType)
            .apply {
                if (errorMessage != null) {
                    putString(KEY_ERROR_MESSAGE, errorMessage)
                }
            }
            .build()
    }

    private data class SyncOutcomeAggregate(
        var queueSuccessful: Int = 0,
        var queueFailed: Int = 0,
        var queueConflicts: Int = 0,
        var queueProcessingFailures: Int = 0,
        var operationSuccessful: Int = 0,
        var operationFailed: Int = 0,
        var operationProcessingFailures: Int = 0,
        val failureCategories: MutableSet<String> = linkedSetOf()
    ) {
        val successfulOperations: Int
            get() = queueSuccessful + operationSuccessful

        val failedOperations: Int
            get() = queueFailed + queueProcessingFailures + operationFailed + operationProcessingFailures

        val completedOperations: Int
            get() = successfulOperations + queueConflicts

        val totalOperations: Int
            get() = completedOperations + failedOperations

        fun requiredFailureCount(startupSync: Boolean): Int {
            return queueFailed +
                queueProcessingFailures +
                operationProcessingFailures +
                if (startupSync) operationFailed else 0
        }
    }

    private fun queueEntityTypesForSyncType(syncType: String): Set<String>? {
        return when (syncType.lowercase()) {
            "all", "priority", "startup" -> null
            "workout", "workouts" -> setOf(SyncOperationManager.ENTITY_WORKOUT)
            "template", "templates" -> setOf(SyncOperationManager.ENTITY_TEMPLATE)
            "profile" -> setOf(SyncOperationManager.ENTITY_PROFILE, SyncOperationManager.ENTITY_USER_PUBLIC)
            "social" -> setOf(
                SyncOperationManager.ENTITY_SOCIAL_PROFILE,
                SyncOperationManager.ENTITY_FOLLOW_RELATIONSHIP,
                SyncOperationManager.ENTITY_WORKOUT_POST,
                SyncOperationManager.ENTITY_GYM_BUDDY,
                SyncOperationManager.ENTITY_POST_LIKE,
                SyncOperationManager.ENTITY_SAVED_POST,
                SyncOperationManager.ENTITY_POST_COMMENT,
                SyncOperationManager.ENTITY_BLOCKED_USER,
                SyncOperationManager.ENTITY_CONTENT_REPORT
            )
            "settings" -> setOf(SyncOperationManager.ENTITY_SETTINGS)
            "analytics" -> setOf("ANALYTICS", SyncOperationManager.ENTITY_ACHIEVEMENT)
            "chat" -> setOf("CHAT")
            else -> null
        }
    }

    private fun operationManagerSyncType(syncType: String): String {
        return when (syncType.lowercase()) {
            "startup" -> "startup"
            "workout" -> "workouts"
            "template", "templates" -> "templates"
            "achievement", "achievements" -> "achievements"
            "analytics" -> "analytics"
            "settings" -> "settings"
            "chat" -> "chat"
            else -> syncType
        }
    }
    
}
