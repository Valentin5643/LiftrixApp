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
import com.example.liftrix.domain.model.common.LiftrixResult
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
    private val offlineQueueManager: OfflineQueueManager
) : BaseSyncWorker(context, params) {
    
    // Fallback constructor for when Hilt factory generation fails
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        WorkerServiceLocator.getUnifiedSyncDependencies(context).run {
            Timber.w("⚠️ UnifiedSyncWorker using FALLBACK constructor - Hilt factory failed!")
            return@run this
        }
    )
    
    // Helper constructor to unpack the dependency structure
    private constructor(
        context: Context,
        params: WorkerParameters,
        deps: WorkerServiceLocator.UnifiedSyncDependencies
    ) : this(
        context, 
        params,
        deps.syncOperationManager, 
        deps.offlineQueueManager
    )

    override val workerName: String = "UnifiedSyncWorker"
    override val maxRetryCount: Int = 5
    
    companion object {
        const val WORK_NAME = "unified_sync_work"
        
        // Input parameter keys
        const val KEY_SYNC_TYPE = "sync_type"
        const val KEY_MAX_PRIORITY = "max_priority"
        const val KEY_FORCE_SYNC = "force_sync"
        const val KEY_STARTUP_SYNC = "startup_sync"
        
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
            
            Timber.d("$workerName: Starting sync - type: $syncType, maxPriority: $maxPriority, force: $forceSync, startup: $startupSync")
            
            // Check authentication
            if (!syncOperationManager.validateAuthentication(userId)) {
                Timber.e("$workerName: User not authenticated for sync: $userId")
                return Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "User authentication failed")
                        .build()
                )
            }
            
            var totalOperations = 0
            var successfulOperations = 0
            
            // Step 1: Process offline queue first
            try {
                checkCancellation()
                
                Timber.d("$workerName: Processing offline queue for user $userId")
                val queueResult = offlineQueueManager.processPendingQueue(userId)
                
                queueResult.fold(
                    onSuccess = { syncResult ->
                        totalOperations += (syncResult.successful + syncResult.failed + syncResult.conflicts)
                        successfulOperations += syncResult.successful
                        
                        Timber.d("$workerName: Offline queue processed - successful: ${syncResult.successful}, failed: ${syncResult.failed}, conflicts: ${syncResult.conflicts}")
                    },
                    onFailure = { error ->
                        Timber.w("$workerName: Failed to process offline queue: $error")
                        // Continue with regular sync even if offline queue fails
                    }
                )
            } catch (e: Exception) {
                Timber.w(e, "$workerName: Error processing offline queue, continuing with regular sync")
            }
            
            // Step 2: Process sync operations through SyncOperationManager
            try {
                checkCancellation()
                
                Timber.d("$workerName: Processing sync operations via SyncOperationManager")
                val operationResults = syncOperationManager.processSyncOperations(
                    userId = userId,
                    operationType = syncType,
                    maxPriority = maxPriority,
                    cancellationCheck = { checkCancellation() }
                )
                
                operationResults.fold(
                    onSuccess = { results ->
                        totalOperations += results.size
                        successfulOperations += results.count { it.success }
                        
                        val failedResults = results.filter { !it.success }
                        if (failedResults.isNotEmpty()) {
                            Timber.w("$workerName: Some operations failed:")
                            failedResults.forEach { result ->
                                Timber.w("  - ${result.operation.entityType}:${result.operation.entityId} - ${result.error}")
                            }
                        }
                        
                        Timber.d("$workerName: Sync operations completed - successful: ${results.count { it.success }}, failed: ${failedResults.size}")
                    },
                    onFailure = { error ->
                        Timber.e("$workerName: Sync operations failed: $error")
                        throw Exception("Sync operations failed: $error")
                    }
                )
                
            } catch (e: CancellationException) {
                // Re-throw cancellation
                throw e
            } catch (e: Exception) {
                Timber.e(e, "$workerName: Error during sync operations")
                throw e
            }
            
            val syncEndTime = System.currentTimeMillis()
            val syncDuration = syncEndTime - syncStartTime
            
            // Determine success criteria
            val isSuccessful = when {
                totalOperations == 0 -> {
                    Timber.d("$workerName: No operations to sync for user $userId")
                    true // No operations is considered success
                }
                successfulOperations == totalOperations -> {
                    Timber.d("$workerName: All operations successful for user $userId")
                    true // All operations succeeded
                }
                successfulOperations > 0 -> {
                    Timber.w("$workerName: Partial success - $successfulOperations/$totalOperations operations successful")
                    true // Partial success is acceptable
                }
                else -> {
                    Timber.e("$workerName: All operations failed for user $userId")
                    false // Complete failure
                }
            }
            
            Timber.i("$workerName: Sync completed for user $userId - Duration: ${syncDuration}ms, Operations: $successfulOperations/$totalOperations successful")
            
            return if (isSuccessful) {
                Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, successfulOperations)
                        .putLong("sync_duration_ms", syncDuration)
                        .putInt("total_operations", totalOperations)
                        .putString("sync_type", syncType)
                        .build()
                )
            } else {
                // Let base class handle retry logic
                throw Exception("All sync operations failed")
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
    
}
