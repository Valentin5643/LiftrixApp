package com.example.liftrix.sync

import android.content.Context
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import androidx.work.WorkInfo
import androidx.work.Operation
import com.example.liftrix.data.model.SyncPayloadFactory
import com.example.liftrix.data.sync.RealtimeSyncService
import com.example.liftrix.data.sync.OfflineQueueManager
import com.example.liftrix.data.service.ProfileCleanupService
import com.example.liftrix.domain.repository.SyncStatusRepository
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized coordinator for all sync operations in the Liftrix app.
 * 
 * This coordinator manages:
 * - Periodic background sync scheduling for all entity types
 * - On-demand sync triggering for immediate data synchronization
 * - Real-time sync activation for active workout sessions
 * - Sync operation orchestration and dependency management
 * - Sync status tracking and reporting
 * 
 * Architecture:
 * - Uses WorkManager for reliable background sync execution
 * - Implements retry policies with exponential backoff
 * - Provides both individual entity sync and coordinated multi-entity sync
 * - Integrates with RealtimeSyncService for live workout updates
 * - Manages sync dependencies and ordering (Profile → Workouts → Templates → Achievements)
 * 
 * Sync Strategies:
 * - Periodic Sync: Every 15 minutes for all entities (when device conditions are met)
 * - Immediate Sync: On-demand for user-triggered actions or critical updates
 * - Real-time Sync: Live updates for active workout sessions
 * - Smart Sync: Only syncs entities that have unsynced changes
 */
@Singleton
class SyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val realtimeSyncService: RealtimeSyncService,
    private val offlineQueueManager: OfflineQueueManager,
    private val syncStatusRepository: SyncStatusRepository,
    private val profileCleanupService: ProfileCleanupService,
    private val startupRestoreGate: StartupRestoreGate
) {
    // Use standard WorkManager instance (initialized manually in Application.onCreate)
    private val workManager: WorkManager
        get() {
            val instance = WorkManager.getInstance(context)
            Timber.v("SyncCoordinator accessing WorkManager instance: $instance, factory: ${instance.configuration.workerFactory}")
            return instance
        }
    
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startupSyncGuard = Any()
    private val startupSyncInFlightUsers = mutableSetOf<String>()
    private val workMonitorGuard = Any()
    private val workMonitors = mutableMapOf<String, WorkMonitorRegistration>()
    private var nextWorkMonitorGeneration = 0L

    private data class WorkMonitorRegistration(
        val generation: Long,
        val job: Job
    )
    
    companion object {
        private const val UNIFIED_SYNC_WORK_NAME = "liftrix_unified_sync"
        private const val STARTUP_SYNC_WORK_NAME = "startup_sync"
        private const val SYNC_INTERVAL_MINUTES = 15L
        private const val WORKER_INSTANTIATION_CHECK_DELAY_MS = 1_000L // 1 second
        private const val WORK_STATE_POLL_INTERVAL_MS = 500L
        private val STARTUP_FETCH_ENTITY_TYPES = listOf(
            SyncOperationManager.ENTITY_PROFILE,
            "USER_PUBLIC",
            SyncOperationManager.ENTITY_WORKOUT,
            SyncOperationManager.ENTITY_TEMPLATE,
            SyncOperationManager.ENTITY_FOLLOW_RELATIONSHIP,
            SyncOperationManager.ENTITY_WORKOUT_POST,
            SyncOperationManager.ENTITY_ACHIEVEMENT
        )
    }
    
    /**
     * Schedules periodic background sync for all entities.
     * This should be called once during app initialization.
     * 
     * ENHANCED: Now uses UnifiedSyncWorker when enabled, with fallback to legacy workers
     * 
     * @param userId The user ID to sync data for
     */
    fun schedulePeriodicSync(userId: String) {
        Timber.d("SyncCoordinator: Scheduling unified periodic sync for user $userId")
        scheduleUnifiedPeriodicSync(userId)
        scheduleChatPeriodicSync(userId)

        scheduleDeadLetterCleanup(userId)
        scheduleDatabaseIntegrityCheck(userId)
    }
    
    /**
     * Schedule periodic sync using UnifiedSyncWorker
     */
    private fun scheduleUnifiedPeriodicSync(userId: String) {
        val periodicSyncRequest = PeriodicWorkRequestBuilder<UnifiedSyncWorker>(
            SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setInputData(workDataOf(
                "userId" to userId,
                "sync_type" to "all",
                "max_priority" to SyncOperationManager.PRIORITY_LOW
            ))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresDeviceIdle(false)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .addTag("periodic_sync")
            .addTag("unified_sync")
            .addTag("user_$userId")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "${UNIFIED_SYNC_WORK_NAME}_periodic_$userId",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
        
        Timber.d("SyncCoordinator: Unified periodic sync scheduled for user $userId")
    }

    private fun scheduleChatPeriodicSync(userId: String) {
        workManager.enqueueUniquePeriodicWork(
            ChatSyncWorker.getPeriodicWorkName(userId),
            ExistingPeriodicWorkPolicy.KEEP,
            ChatSyncWorker.createPeriodicWorkRequest(userId)
        )

        Timber.d("SyncCoordinator: Chat periodic sync scheduled for user $userId")
    }
    
    private fun scheduleDeadLetterCleanup(userId: String) {
        val cleanupRequest = DeadLetterCleanupWorker.createPeriodicWorkRequest(userId)

        workManager.enqueueUniquePeriodicWork(
            DeadLetterCleanupWorker.getWorkName(userId),
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )

        Timber.d("SyncCoordinator: Dead letter cleanup scheduled for user $userId")
    }

    private fun scheduleDatabaseIntegrityCheck(userId: String) {
        val integrityRequest = DatabaseIntegrityWorker.createPeriodicWorkRequest(userId)

        workManager.enqueueUniquePeriodicWork(
            DatabaseIntegrityWorker.getWorkName(userId),
            ExistingPeriodicWorkPolicy.KEEP,
            integrityRequest
        )

        Timber.d("SyncCoordinator: Database integrity check scheduled for user $userId")
    }
    
    /**
     * Triggers immediate sync of all entities for a user.
     * This bypasses the periodic schedule and syncs immediately.
     * 
     * ENHANCED: Now uses UnifiedSyncWorker when enabled, with fallback to legacy workers
     * 
     * @param userId The user ID to sync data for
     * @return LiftrixResult indicating success or failure
     */
    suspend fun triggerImmediateSync(userId: String): LiftrixResult<Unit> {
        return try {
            if (!startupRestoreGate.isRestoreComplete(userId)) {
                Timber.tag("StartupRestoreFix").w(
                    "operation=IMMEDIATE_SYNC_BLOCKED userId=$userId gateState=${startupRestoreGate.currentState(userId)} reason=restore_not_complete timestamp=${System.currentTimeMillis()}"
                )
                return liftrixFailure(
                    LiftrixError.BusinessLogicError(
                        code = "RESTORE_IN_PROGRESS",
                        errorMessage = "Immediate sync is unavailable until startup restore completes",
                        analyticsContext = mapOf(
                            "user_id" to userId,
                            "restore_state" to startupRestoreGate.currentState(userId).name
                        )
                    )
                )
            }
            Timber.tag("FreshLoginRestoreDebug").d(
                "operation=SYNC_COORDINATOR_IMMEDIATE_START userId=$userId direction=queue_driven_unified timestamp=${System.currentTimeMillis()}"
            )
            Timber.d("SyncCoordinator: Triggering unified immediate sync for user $userId")
            
            // Update sync status to indicate sync in progress
            syncStatusRepository.updateSyncStatus(userId, isInProgress = true)
            
            triggerUnifiedImmediateSync(userId)
            
        } catch (e: Exception) {
            Timber.e(e, "SyncCoordinator: Failed to trigger immediate sync for user $userId")
            
            coordinatorScope.launch {
                syncStatusRepository.updateSyncStatus(userId, isInProgress = false, hasError = true)
            }
            
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "IMMEDIATE_SYNC_FAILED",
                    errorMessage = "Failed to trigger immediate sync: ${e.message}",
                    analyticsContext = mapOf("user_id" to userId)
                )
            )
        }
    }
    
    /**
     * Trigger immediate sync using UnifiedSyncWorker
     */
    private suspend fun triggerUnifiedImmediateSync(userId: String): LiftrixResult<Unit> {
        val chatSync = ChatSyncWorker.createWorkRequest(userId)
        val immediateSync = UnifiedSyncWorker.createImmediateWorkRequest(userId)
        val workName = "${UNIFIED_SYNC_WORK_NAME}_immediate_$userId"

        val operation = workManager.beginUniqueWork(
            workName,
            ExistingWorkPolicy.KEEP,
            chatSync
        ).then(immediateSync).enqueue()
        Timber.tag("FreshLoginRestoreDebug").i(
            "operation=SYNC_COORDINATOR_IMMEDIATE_ENQUEUED userId=$userId uniqueWork=$workName policy=KEEP workers=ChatSyncWorker->UnifiedSyncWorker timestamp=${System.currentTimeMillis()}"
        )
        
        startWorkMonitoring(
            operation = operation,
            workName = workName,
            workId = immediateSync.id,
            userId = userId,
            startupWork = false
        )
        
        Timber.d("SyncCoordinator: Unified immediate sync work enqueued for user $userId")
        return liftrixSuccess(Unit)
    }
    
    /**
     * Triggers sync for a specific entity type only.
     * Useful for targeted sync after specific user actions.
     * 
     * ENHANCED: Now uses UnifiedSyncWorker when enabled, with fallback to specialized workers
     * 
     * @param userId The user ID to sync data for
     * @param entityType The specific entity type to sync ("workout", "template", "achievement", "profile", "social")
     * @return LiftrixResult indicating success or failure
     */
    suspend fun triggerEntitySync(userId: String, entityType: String): LiftrixResult<Unit> {
        return try {
            Timber.d("SyncCoordinator: Triggering unified $entityType sync for user $userId")
            triggerUnifiedEntitySync(userId, entityType)
            
        } catch (e: Exception) {
            Timber.e(e, "SyncCoordinator: Failed to trigger $entityType sync for user $userId")
            
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "ENTITY_SYNC_FAILED",
                    errorMessage = "Failed to trigger $entityType sync: ${e.message}",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "entity_type" to entityType
                    )
                )
            )
        }
    }
    
    /**
     * Trigger entity sync using UnifiedSyncWorker
     */
    private suspend fun triggerUnifiedEntitySync(userId: String, entityType: String): LiftrixResult<Unit> {
        if (entityType.equals("chat", ignoreCase = true)) {
            enqueueChatEntitySync(userId, forceSync = true)
            return liftrixSuccess(Unit)
        }
        val (syncType, priority) = syncRequestForEntity(entityType) ?: return liftrixFailure(
            LiftrixError.ValidationError(
                field = "entityType",
                violations = listOf("Unknown entity type: $entityType")
            )
        )
        
        val workRequest = UnifiedSyncWorker.createWorkRequest(
            userId = userId,
            syncType = syncType,
            maxPriority = priority,
            forceSync = true
        )
        
        val uniqueWorkName = UnifiedSyncWorker.getWorkName(userId, "${entityType}_$syncType")
        
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        
        Timber.d("SyncCoordinator: Unified $entityType sync work enqueued for user $userId")
        return liftrixSuccess(Unit)
    }

    fun enqueueEntitySync(userId: String, entityType: String, forceSync: Boolean = true) {
        if (entityType.equals("chat", ignoreCase = true)) {
            enqueueChatEntitySync(userId, forceSync)
            return
        }
        val (syncType, priority) = syncRequestForEntity(entityType) ?: run {
            Timber.w("SyncCoordinator: Ignoring unknown entity sync type $entityType for user $userId")
            return
        }
        val workRequest = UnifiedSyncWorker.createWorkRequest(
            userId = userId,
            syncType = syncType,
            maxPriority = priority,
            forceSync = forceSync
        )
        val uniqueWorkName = UnifiedSyncWorker.getWorkName(userId, "${entityType}_${syncType}")

        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            workRequest
        )

        Timber.d("SyncCoordinator: Unified $entityType sync work enqueued for user $userId")
    }

    private fun enqueueChatEntitySync(userId: String, forceSync: Boolean) {
        val workName = "${ChatSyncWorker.WORK_NAME}_entity_$userId"
        workManager.enqueueUniqueWork(
            workName,
            if (forceSync) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            ChatSyncWorker.createWorkRequest(userId)
        )
        Timber.d("SyncCoordinator: Chat entity sync work enqueued for user $userId")
    }
    
    /**
     * Enables real-time sync for an active workout session.
     * This provides live updates during workout execution.
     * 
     * @param workoutId The ID of the active workout
     * @param userId The user ID performing the workout
     */
    fun enableRealtimeWorkoutSync(workoutId: String, userId: String) {
        Timber.d("SyncCoordinator: Enabling real-time sync for workout $workoutId (user: $userId)")
        
        coordinatorScope.launch {
            try {
                realtimeSyncService.startRealtimeWorkoutSync(workoutId, userId)
                Timber.d("SyncCoordinator: Real-time workout sync enabled for $workoutId")
            } catch (e: Exception) {
                Timber.e(e, "SyncCoordinator: Failed to enable real-time sync for workout $workoutId")
            }
        }
    }
    
    /**
     * Disables real-time sync for a completed or cancelled workout.
     * 
     * @param workoutId The ID of the workout to stop syncing
     * @param userId The user ID
     */
    fun disableRealtimeWorkoutSync(workoutId: String, userId: String) {
        Timber.d("SyncCoordinator: Disabling real-time sync for workout $workoutId (user: $userId)")
        
        coordinatorScope.launch {
            try {
                realtimeSyncService.stopRealtimeSync()
                
                // Trigger final sync to ensure workout completion is saved
                triggerEntitySync(userId, "workout")
                
                Timber.d("SyncCoordinator: Real-time workout sync disabled for $workoutId")
            } catch (e: Exception) {
                Timber.e(e, "SyncCoordinator: Failed to disable real-time sync for workout $workoutId")
            }
        }
    }
    
    /**
     * Cancels all sync operations for a user.
     * Useful during user logout or app cleanup.
     * 
     * ENHANCED: Now handles both unified and legacy workers
     * 
     * @param userId The user ID to cancel sync for
     */
    fun cancelSyncForUser(userId: String) {
        Timber.d("SyncCoordinator: Cancelling all sync operations for user $userId")
        
        try {
            // Cancel unified sync operations
            workManager.cancelUniqueWork("${UNIFIED_SYNC_WORK_NAME}_periodic_$userId")
            workManager.cancelUniqueWork("${UNIFIED_SYNC_WORK_NAME}_immediate_$userId")
            workManager.cancelUniqueWork(ChatSyncWorker.getPeriodicWorkName(userId))
            
            // Cancel legacy sync operations (for backward compatibility)
            workManager.cancelUniqueWork("liftrix_periodic_sync_$userId")
            workManager.cancelUniqueWork("liftrix_immediate_sync_$userId")
            workManager.cancelUniqueWork("startup_sync_$userId")
            
            // Cancel entity-specific syncs (both unified and legacy)
            workManager.cancelUniqueWork("workout_sync_$userId")
            workManager.cancelUniqueWork("template_sync_$userId")
            workManager.cancelUniqueWork("achievement_sync_$userId")
            workManager.cancelUniqueWork("profile_sync_$userId")
            
            // Cancel by tag for any additional work (this handles all user-specific work)
            workManager.cancelAllWorkByTag("user_$userId")
            workManager.cancelAllWorkByTag("unified_sync")
            
            coordinatorScope.launch {
                // Stop any real-time sync
                realtimeSyncService.stopRealtimeSync()
                
                // Reset sync status
                syncStatusRepository.resetSyncStatus(userId)
            }
            
            Timber.d("SyncCoordinator: All sync operations cancelled for user $userId")
            
        } catch (e: Exception) {
            Timber.e(e, "SyncCoordinator: Error cancelling sync operations for user $userId")
        }
    }

    fun cancelAllSync() {
        Timber.d("SyncCoordinator: Cancelling all WorkManager sync operations")

        try {
            workManager.cancelAllWorkByTag("unified_sync")
            workManager.cancelAllWorkByTag("periodic_sync")
            workManager.cancelAllWorkByTag("startup_sync")
            workManager.cancelAllWorkByTag(ChatSyncWorker.WORK_NAME)

            coordinatorScope.launch {
                realtimeSyncService.stopRealtimeSync()
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncCoordinator: Error cancelling all sync operations")
        }
    }
    
    /**
     * Gets the current sync status for a user.
     * 
     * @param userId The user ID to check status for
     * @return Flow of sync status updates
     */
    fun getSyncStatus(userId: String) = syncStatusRepository.getSyncStatus(userId)
    
    /**
     * 🔥 NEW: Triggers full bidirectional sync on app start or login.
     * This ensures workouts are always synchronized across devices and recovers after app restarts or cache clears.
     * 
     * Process:
     * 1. Performs startup cleanup to remove orphaned profiles
     * 2. Fetches remote workouts and merges them with local database
     * 3. Resolves conflicts in favor of most recently updated workout
     * 4. Uploads any local changes to Firebase
     * 5. Provides comprehensive logging for sync operations
     * 
     * @param userId The user ID to perform startup sync for
     * @return LiftrixResult indicating success or failure
     */
    suspend fun triggerStartupSync(
        userId: String,
        source: String = "unknown",
        force: Boolean = false
    ): LiftrixResult<Unit> {
        return try {
            val workName = "startup_sync_$userId"
            val gateStateAtRequest = startupRestoreGate.currentState(userId)
            Timber.tag("StartupRestoreFix").i(
                "[TEMPLATE-LOAD] operation=STARTUP_SYNC_REQUESTED userId=$userId source=$source force=$force gateState=$gateStateAtRequest timestamp=${System.currentTimeMillis()}"
            )

            if (!force && gateStateAtRequest == StartupRestoreState.RESTORE_COMPLETE) {
                Timber.tag("StartupRestoreFix").i(
                    "[TEMPLATE-LOAD] operation=STARTUP_SYNC_SKIPPED_RESTORE_COMPLETE userId=$userId source=$source gateState=$gateStateAtRequest timestamp=${System.currentTimeMillis()}"
                )
                return liftrixSuccess(Unit)
            }

            synchronized(startupSyncGuard) {
                if (!force && userId in startupSyncInFlightUsers) {
                    Timber.tag("StartupRestoreFix").w(
                        "[TEMPLATE-LOAD] operation=STARTUP_SYNC_DUPLICATE_SUPPRESSED userId=$userId source=$source reason=in_memory_guard timestamp=${System.currentTimeMillis()}"
                    )
                    return liftrixSuccess(Unit)
                }
                startupSyncInFlightUsers.add(userId)
            }

            val existingWorkInfos = withContext(Dispatchers.IO) {
                workManager.getWorkInfosForUniqueWork(workName).get()
            }
            val existingStates = existingWorkInfos.joinToString(separator = "|") { it.state.name }
            val hasActiveExistingWork = existingWorkInfos.any {
                it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.BLOCKED
            }
            Timber.tag("StartupRestoreFix").i(
                "[TEMPLATE-LOAD] operation=STARTUP_SYNC_EXISTING_WORK_STATE userId=$userId source=$source workName=$workName states=${existingStates.ifBlank { "none" }} active=$hasActiveExistingWork timestamp=${System.currentTimeMillis()}"
            )
            if (!force && hasActiveExistingWork) {
                synchronized(startupSyncGuard) {
                    startupSyncInFlightUsers.remove(userId)
                }
                Timber.tag("StartupRestoreFix").w(
                    "[TEMPLATE-LOAD] operation=STARTUP_SYNC_SKIPPED_ALREADY_RUNNING userId=$userId source=$source reason=workmanager_active states=${existingStates.ifBlank { "none" }} timestamp=${System.currentTimeMillis()}"
                )
                Timber.tag("StartupRestoreFix").w(
                    "[TEMPLATE-LOAD] operation=STARTUP_SYNC_DUPLICATE_SUPPRESSED userId=$userId source=$source reason=workmanager_active timestamp=${System.currentTimeMillis()}"
                )
                return liftrixSuccess(Unit)
            }

            startupRestoreGate.transition(
                userId = userId,
                state = StartupRestoreState.RESTORING_FROM_FIREBASE,
                reason = "triggerStartupSync"
            )
            Timber.tag("StartupRestoreFix").i(
                "[TEMPLATE-LOAD] operation=RESTORE_GATE_STATE_AT_ENQUEUE userId=$userId source=$source gateState=${startupRestoreGate.currentState(userId)} timestamp=${System.currentTimeMillis()}"
            )
            Timber.tag("FreshLoginRestoreDebug").i(
                "operation=SYNC_COORDINATOR_STARTUP_START userId=$userId direction=Firebase->Room_then_Room->Firebase timestamp=${System.currentTimeMillis()}"
            )
            Timber.i("SyncCoordinator: Starting full bidirectional sync for user $userId (startup/login)")
            
            // 🧹 CLEANUP: Add delay to allow onboarding completion before cleanup validation
            Timber.d("SyncCoordinator: Waiting briefly to allow onboarding completion before cleanup check")
            kotlinx.coroutines.delay(2000L) // 2-second grace period for onboarding completion
            
            // 🧹 CLEANUP: Perform startup cleanup to remove orphaned profiles (non-blocking)
            Timber.d("SyncCoordinator: Performing startup cleanup before sync for user $userId")
            try {
                val cleanupResult = profileCleanupService.performStartupCleanup(userId)
                when {
                    cleanupResult.isSuccess -> {
                        val result = cleanupResult.getOrNull()
                        if (result != null && result.orphanedProfilesRemoved > 0) {
                            Timber.i("🧹 SyncCoordinator: Startup cleanup removed ${result.orphanedProfilesRemoved} orphaned profiles")
                        } else {
                            Timber.d("🧹 SyncCoordinator: No orphaned profiles found during startup cleanup")
                        }
                    }
                    cleanupResult.isFailure -> {
                        Timber.w("🧹 SyncCoordinator: Startup cleanup failed but continuing with sync: ${cleanupResult.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "🧹 SyncCoordinator: Startup cleanup threw exception but continuing with sync")
            }
            
            // Update sync status to indicate startup sync in progress
            syncStatusRepository.updateSyncStatus(userId, isInProgress = true)
            
            queueStartupFetchItems(userId)

            val startupUnifiedSync = UnifiedSyncWorker.createWorkRequest(
                userId = userId,
                syncType = "startup",
                maxPriority = SyncOperationManager.PRIORITY_LOW,
                forceSync = true,
                startupSync = true
            )
            val startupChatSync = ChatSyncWorker.createWorkRequest(userId)

            val operation = workManager.beginUniqueWork(
                workName,
                ExistingWorkPolicy.KEEP,
                startupChatSync
            ).then(startupUnifiedSync).enqueue()
            Timber.tag("StartupRestoreFix").i(
                "[TEMPLATE-LOAD] operation=STARTUP_SYNC_ENQUEUE_POLICY userId=$userId source=$source workName=$workName policy=KEEP previousPolicy=REPLACE reason=preserve_running_restore timestamp=${System.currentTimeMillis()}"
            )
            Timber.tag("StartupRestoreFix").i(
                "[TEMPLATE-LOAD] operation=STARTUP_SYNC_ENQUEUED_ONCE userId=$userId source=$source workName=$workName workers=ChatSyncWorker->UnifiedSyncWorker queuedFetchTypes=${STARTUP_FETCH_ENTITY_TYPES.joinToString(",")} timestamp=${System.currentTimeMillis()}"
            )
            Timber.tag("FreshLoginRestoreDebug").i(
                "operation=SYNC_COORDINATOR_STARTUP_ENQUEUED userId=$userId uniqueWork=startup_sync_$userId policy=KEEP workers=ChatSyncWorker->UnifiedSyncWorker timestamp=${System.currentTimeMillis()}"
            )
            
            // Observe the durable startup work without taking ownership of its lifetime.
            startWorkMonitoring(
                operation = operation,
                workName = "startup_sync_$userId",
                workId = startupUnifiedSync.id,
                userId = userId,
                startupWork = true
            )
            
            Timber.i("SyncCoordinator: Startup sync work enqueued for user $userId")
            
            liftrixSuccess(Unit)
            
        } catch (e: Exception) {
            synchronized(startupSyncGuard) {
                startupSyncInFlightUsers.remove(userId)
            }
            startupRestoreGate.transition(
                userId = userId,
                state = StartupRestoreState.RESTORE_FAILED,
                reason = "triggerStartupSync_exception"
            )
            Timber.e(e, "SyncCoordinator: Failed to trigger startup sync for user $userId")
            
            coordinatorScope.launch {
                syncStatusRepository.updateSyncStatus(userId, isInProgress = false, hasError = true)
            }
            
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "STARTUP_SYNC_FAILED",
                    errorMessage = "Failed to trigger startup sync: ${e.message}",
                    analyticsContext = mapOf("user_id" to userId, "sync_type" to "startup")
                )
            )
        }
    }
    
    /**
     * Forces a complete re-sync of all data for a user.
     * This marks all local data as unsynced and triggers a full sync.
     * Use with caution as this can be resource-intensive.
     * 
     * @param userId The user ID to perform full sync for
     */
    suspend fun forceFullResync(userId: String): LiftrixResult<Unit> {
        return try {
            Timber.w("SyncCoordinator: Forcing full resync for user $userId")
            
            // Mark all data as unsynced (this would require DAO methods)
            // For now, we'll just trigger immediate sync with force flag
            
            val result = triggerImmediateSync(userId)
            
            if (result.isSuccess) {
                Timber.d("SyncCoordinator: Full resync initiated for user $userId")
            }
            
            result
            
        } catch (e: Exception) {
            Timber.e(e, "SyncCoordinator: Failed to force full resync for user $userId")
            
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "FULL_RESYNC_FAILED",
                    errorMessage = "Failed to force full resync: ${e.message}",
                    analyticsContext = mapOf("user_id" to userId)
                )
            )
        }
    }

    private suspend fun queueStartupFetchItems(userId: String) {
        STARTUP_FETCH_ENTITY_TYPES.forEach { entityType ->
            offlineQueueManager.queueOperation(
                userId = userId,
                entityType = entityType,
                entityId = "startup_fetch_${entityType.lowercase()}",
                operation = "FETCH",
                data = SyncPayloadFactory.createFetchPayload(
                    userId = userId,
                    entityType = entityType,
                    lastSyncTimestamp = 0L,
                    fetchAll = true
                )
            ).onFailure { error ->
                Timber.w(
                    error,
                    "SyncCoordinator: Failed to queue startup fetch item userId=$userId entityType=$entityType"
                )
            }
        }
        Timber.i(
            "SyncCoordinator: Queued startup fetch items userId=$userId " +
                "entityTypes=${STARTUP_FETCH_ENTITY_TYPES.joinToString(",")}"
        )
    }

    private fun syncRequestForEntity(entityType: String): Pair<String, Int>? {
        return when (entityType.lowercase()) {
            "workout", "workouts" -> "workouts" to SyncOperationManager.PRIORITY_HIGH
            "template", "templates" -> "templates" to SyncOperationManager.PRIORITY_HIGH
            "achievement", "achievements", "analytics" -> "analytics" to SyncOperationManager.PRIORITY_LOW
            "profile", "user_public" -> "profile" to SyncOperationManager.PRIORITY_CRITICAL
            "social",
            "social_profile",
            "follow_relationship",
            "workout_post",
            "gym_buddy",
            "engagement" -> "social" to SyncOperationManager.PRIORITY_MEDIUM
            "settings" -> "settings" to SyncOperationManager.PRIORITY_MEDIUM
            "chat" -> "chat" to SyncOperationManager.PRIORITY_LOW
            else -> null
        }
    }
    
    /**
     * Observes durable WorkManager work without owning its lifetime. Replacing a monitor only
     * detaches the older in-process observer; it never cancels the underlying unique work.
     */
    private fun startWorkMonitoring(
        operation: Operation,
        workName: String,
        workId: UUID,
        userId: String,
        startupWork: Boolean
    ) {
        val (registration, previousRegistration) = synchronized(workMonitorGuard) {
            val generation = ++nextWorkMonitorGeneration
            val job = coordinatorScope.launch(start = CoroutineStart.LAZY) {
                monitorWork(
                    operation = operation,
                    workName = workName,
                    workId = workId,
                    userId = userId,
                    startupWork = startupWork,
                    generation = generation
                )
            }
            val currentRegistration = WorkMonitorRegistration(generation, job)
            currentRegistration to workMonitors.put(workName, currentRegistration)
        }

        previousRegistration?.job?.cancel(
            CancellationException("Superseded by monitor generation ${registration.generation}")
        )
        registration.job.start()
    }

    private suspend fun monitorWork(
        operation: Operation,
        workName: String,
        workId: UUID,
        userId: String,
        startupWork: Boolean,
        generation: Long
    ) {
        try {
            try {
                operation.result.get()
                Timber.d("SyncCoordinator: Work enqueue successful for $workName")
            } catch (e: Exception) {
                Timber.e("SyncCoordinator: Work enqueue failed for $workName: ${e.message}")
                if (!isCurrentWorkMonitor(workName, generation)) return
                markMonitoringFailure(
                    userId = userId,
                    startupWork = startupWork,
                    reason = "work_enqueue_failed",
                    errorMessage = "Enqueue failed: ${e.message}"
                )
                return
            }

            delay(WORKER_INSTANTIATION_CHECK_DELAY_MS)
            var monitoredWorkId = workId

            while (true) {
                val workInfo = workManager.getWorkInfoById(monitoredWorkId).get()
                    ?: workManager.getWorkInfosForUniqueWork(workName).get().firstOrNull {
                        it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.BLOCKED ||
                            it.state == WorkInfo.State.RUNNING
                    }

                if (workInfo == null) {
                    Timber.e("SyncCoordinator: No WorkInfo found for $workName - worker may have failed to instantiate")
                    if (!isCurrentWorkMonitor(workName, generation)) return
                    markMonitoringFailure(
                        userId = userId,
                        startupWork = startupWork,
                        reason = "work_info_missing",
                        errorMessage = "Worker not found"
                    )
                    return
                }
                monitoredWorkId = workInfo.id

                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        if (!isCurrentWorkMonitor(workName, generation)) return
                        Timber.d("SyncCoordinator: Sync completed successfully for $workName")
                        syncStatusRepository.updateSyncStatus(
                            userId,
                            isInProgress = false,
                            hasError = false,
                            lastSyncTime = System.currentTimeMillis(),
                            syncedCount = workInfo.outputData.getInt("sync_count", 0)
                        )
                        if (startupWork) {
                            startupRestoreGate.transition(
                                userId = userId,
                                state = StartupRestoreState.RESTORE_COMPLETE,
                                reason = "startup_work_succeeded"
                            )
                        }
                        return
                    }

                    WorkInfo.State.FAILED -> {
                        if (!isCurrentWorkMonitor(workName, generation)) return
                        Timber.w("SyncCoordinator: Sync failed for $workName")
                        markMonitoringFailure(
                            userId = userId,
                            startupWork = startupWork,
                            reason = "startup_work_failed",
                            errorMessage = workInfo.outputData.getString("error_message")
                                ?: "Sync worker failed"
                        )
                        return
                    }

                    WorkInfo.State.CANCELLED -> {
                        if (!isCurrentWorkMonitor(workName, generation)) return
                        Timber.w("SyncCoordinator: Sync cancelled for $workName")
                        markMonitoringFailure(
                            userId = userId,
                            startupWork = startupWork,
                            reason = "startup_work_cancelled",
                            errorMessage = "Sync worker was cancelled"
                        )
                        return
                    }

                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED,
                    WorkInfo.State.RUNNING -> {
                        Timber.v(
                            "SyncCoordinator: Work remains pending workName=$workName " +
                                "workId=$monitoredWorkId state=${workInfo.state} generation=$generation"
                        )
                        delay(WORK_STATE_POLL_INTERVAL_MS)
                    }
                }
            }
        } catch (e: CancellationException) {
            Timber.d(
                "SyncCoordinator: Detached local observer workName=$workName workId=$workId " +
                    "generation=$generation; durable work remains unchanged"
            )
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SyncCoordinator: Error monitoring work $workName")
            if (isCurrentWorkMonitor(workName, generation)) {
                syncStatusRepository.updateSyncStatus(
                    userId,
                    isInProgress = true,
                    hasError = false,
                    errorMessage = null
                )
            }
        } finally {
            val releasedCurrentGeneration = synchronized(workMonitorGuard) {
                if (workMonitors[workName]?.generation == generation) {
                    workMonitors.remove(workName)
                    true
                } else {
                    false
                }
            }
            if (startupWork && releasedCurrentGeneration) {
                synchronized(startupSyncGuard) {
                    startupSyncInFlightUsers.remove(userId)
                }
                Timber.tag("StartupRestoreFix").d(
                    "[TEMPLATE-LOAD] operation=STARTUP_SYNC_GUARD_RELEASED userId=$userId " +
                        "workName=$workName generation=$generation timestamp=${System.currentTimeMillis()}"
                )
            }
        }
    }

    private fun isCurrentWorkMonitor(workName: String, generation: Long): Boolean {
        return synchronized(workMonitorGuard) {
            workMonitors[workName]?.generation == generation
        }
    }

    private suspend fun markMonitoringFailure(
        userId: String,
        startupWork: Boolean,
        reason: String,
        errorMessage: String
    ) {
        syncStatusRepository.updateSyncStatus(
            userId,
            isInProgress = false,
            hasError = true,
            errorMessage = errorMessage
        )
        if (startupWork) {
            startupRestoreGate.transition(
                userId = userId,
                state = StartupRestoreState.RESTORE_FAILED,
                reason = reason
            )
        }
    }
    
}
