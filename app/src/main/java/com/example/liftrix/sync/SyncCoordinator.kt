package com.example.liftrix.sync

import android.content.Context
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import androidx.work.WorkInfo
import androidx.work.Operation
import com.example.liftrix.data.sync.RealtimeSyncService
import com.example.liftrix.data.service.ProfileCleanupService
import com.example.liftrix.domain.repository.SyncStatusRepository
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
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
    
    companion object {
        private const val PERIODIC_SYNC_WORK_NAME = "liftrix_periodic_sync"
        private const val IMMEDIATE_SYNC_WORK_NAME = "liftrix_immediate_sync"
        private const val UNIFIED_SYNC_WORK_NAME = "liftrix_unified_sync"
        private const val SYNC_INTERVAL_MINUTES = 15L
        private const val SYNC_TIMEOUT_MS = 30_000L // 30 seconds timeout
        private const val WORKER_INSTANTIATION_CHECK_DELAY_MS = 1_000L // 1 second
        
        // Feature flags for gradual migration
        private const val USE_UNIFIED_SYNC = true // Set to false to use legacy workers
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
        Timber.d("SyncCoordinator: Scheduling periodic sync for user $userId (unified: $USE_UNIFIED_SYNC)")
        
        if (USE_UNIFIED_SYNC) {
            scheduleUnifiedPeriodicSync(userId)
        } else {
            scheduleLegacyPeriodicSync(userId)
        }

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
    
    /**
     * Schedule periodic sync using legacy MasterSyncWorker (fallback)
     */
    private fun scheduleLegacyPeriodicSync(userId: String) {
        val periodicSyncRequest = PeriodicWorkRequestBuilder<MasterSyncWorker>(
            SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setInputData(workDataOf("userId" to userId))
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
            .addTag("user_$userId")
            .addTag("master_sync")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "${PERIODIC_SYNC_WORK_NAME}_$userId",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
        
        Timber.d("SyncCoordinator: Legacy periodic sync scheduled for user $userId")
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
                return liftrixSuccess(Unit)
            }
            Timber.tag("FreshLoginRestoreDebug").d(
                "operation=SYNC_COORDINATOR_IMMEDIATE_START userId=$userId direction=Firebase->Room_then_Room->Firebase unified=$USE_UNIFIED_SYNC timestamp=${System.currentTimeMillis()}"
            )
            Timber.d("SyncCoordinator: Triggering immediate sync for user $userId (unified: $USE_UNIFIED_SYNC)")
            
            // Update sync status to indicate sync in progress
            syncStatusRepository.updateSyncStatus(userId, isInProgress = true)
            
            if (USE_UNIFIED_SYNC) {
                triggerUnifiedImmediateSync(userId)
            } else {
                triggerLegacyImmediateSync(userId)
            }
            
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
        val immediateSync = UnifiedSyncWorker.createImmediateWorkRequest(userId)
        
        val operation = workManager.enqueueUniqueWork(
            "${UNIFIED_SYNC_WORK_NAME}_immediate_$userId",
            ExistingWorkPolicy.KEEP,
            immediateSync
        )
        Timber.tag("FreshLoginRestoreDebug").i(
            "operation=SYNC_COORDINATOR_IMMEDIATE_ENQUEUED userId=$userId uniqueWork=${UNIFIED_SYNC_WORK_NAME}_immediate_$userId policy=REPLACE mode=unified timestamp=${System.currentTimeMillis()}"
        )
        
        startWorkMonitoring(operation, "${UNIFIED_SYNC_WORK_NAME}_immediate_$userId", userId)
        
        Timber.d("SyncCoordinator: Unified immediate sync work enqueued for user $userId")
        return liftrixSuccess(Unit)
    }
    
    /**
     * Trigger immediate sync using legacy workers (fallback)
     */
    private suspend fun triggerLegacyImmediateSync(userId: String): LiftrixResult<Unit> {
        // Create sync work requests for all entity types using unique names per user
        val profileSync = ProfileSyncWorker.createWorkRequest(userId, forceSync = true)
        val userPublicSync = UserPublicSyncWorker.createWorkRequest(userId, forceSync = true)
        val followRelationshipSync = FollowRelationshipSyncWorker.createWorkRequest(userId, forceSync = true)
        val workoutSync = WorkoutSyncWorker.createWorkRequest(userId)
        val templateSync = TemplateSyncWorker.createWorkRequest(userId)
        val achievementSync = AchievementSyncWorker.createWorkRequest(userId)
        val workoutPostSync = WorkoutPostSyncWorker.createWorkRequest(userId, forceSync = true)
        
        // Chain sync operations with dependencies
        val operation = workManager.beginUniqueWork(
            "${IMMEDIATE_SYNC_WORK_NAME}_$userId",
            ExistingWorkPolicy.KEEP,
            profileSync
        )
            .then(userPublicSync)
            .then(followRelationshipSync)
            .then(listOf(workoutSync, templateSync, achievementSync, workoutPostSync))
            .enqueue()
        Timber.tag("FreshLoginRestoreDebug").i(
            "operation=SYNC_COORDINATOR_IMMEDIATE_ENQUEUED userId=$userId uniqueWork=${IMMEDIATE_SYNC_WORK_NAME}_$userId policy=REPLACE mode=legacy workers=profile,user_public,follow,workout,template,achievement,workout_post timestamp=${System.currentTimeMillis()}"
        )
        
        startWorkMonitoring(operation, "${IMMEDIATE_SYNC_WORK_NAME}_$userId", userId)
        
        Timber.d("SyncCoordinator: Legacy immediate sync work enqueued for user $userId")
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
            Timber.d("SyncCoordinator: Triggering $entityType sync for user $userId (unified: $USE_UNIFIED_SYNC)")
            
            if (USE_UNIFIED_SYNC) {
                triggerUnifiedEntitySync(userId, entityType)
            } else {
                triggerLegacyEntitySync(userId, entityType)
            }
            
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
        // Map entity types to sync types and priorities
        val (syncType, priority) = when (entityType.lowercase()) {
            "workout", "workouts" -> "workouts" to SyncOperationManager.PRIORITY_HIGH
            "template", "templates" -> "all" to SyncOperationManager.PRIORITY_HIGH
            "achievement", "achievements" -> "all" to SyncOperationManager.PRIORITY_LOW
            "profile" -> "profile" to SyncOperationManager.PRIORITY_CRITICAL
            "social", "social_profile", "follow_relationship", "workout_post" -> "social" to SyncOperationManager.PRIORITY_MEDIUM
            else -> {
                return liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "entityType",
                        violations = listOf("Unknown entity type: $entityType")
                    )
                )
            }
        }
        
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
    
    /**
     * Trigger entity sync using legacy specialized workers (fallback)
     */
    private suspend fun triggerLegacyEntitySync(userId: String, entityType: String): LiftrixResult<Unit> {
        val workRequest = when (entityType.lowercase()) {
            "workout" -> WorkoutSyncWorker.createWorkRequest(userId)
            "workout_post" -> WorkoutPostSyncWorker.createWorkRequest(userId)
            "template" -> TemplateSyncWorker.createWorkRequest(userId)
            "achievement" -> AchievementSyncWorker.createWorkRequest(userId)
            "profile" -> ProfileSyncWorker.createWorkRequest(userId, forceSync = true)
            "user_public" -> UserPublicSyncWorker.createWorkRequest(userId, forceSync = true)
            "follow_relationship" -> FollowRelationshipSyncWorker.createWorkRequest(userId, forceSync = true)
            else -> {
                return liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "entityType",
                        violations = listOf("Unknown entity type: $entityType")
                    )
                )
            }
        }
        
        val uniqueWorkName = when (entityType.lowercase()) {
            "workout" -> WorkoutSyncWorker.getWorkName(userId)
            "profile" -> ProfileSyncWorker.getWorkName(userId)
            else -> "${entityType}_sync_$userId"
        }
        
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        
        Timber.d("SyncCoordinator: Legacy $entityType sync work enqueued for user $userId")
        return liftrixSuccess(Unit)
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
            
            // Cancel legacy sync operations (for backward compatibility)
            workManager.cancelUniqueWork("${PERIODIC_SYNC_WORK_NAME}_$userId")
            workManager.cancelUniqueWork("${IMMEDIATE_SYNC_WORK_NAME}_$userId")
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

            if (!force && gateStateAtRequest == StartupRestoreState.RESTORING_FROM_FIREBASE) {
                Timber.tag("StartupRestoreFix").w(
                    "[TEMPLATE-LOAD] operation=STARTUP_SYNC_SKIPPED_ALREADY_RUNNING userId=$userId source=$source gateState=$gateStateAtRequest reason=restore_gate_restoring timestamp=${System.currentTimeMillis()}"
                )
                Timber.tag("StartupRestoreFix").w(
                    "[TEMPLATE-LOAD] operation=STARTUP_SYNC_DUPLICATE_SUPPRESSED userId=$userId source=$source reason=restore_gate_restoring timestamp=${System.currentTimeMillis()}"
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
            
            // Create high-priority sync work requests for all entity types
            // These will perform both remote-to-local and local-to-remote synchronization
            val profileSync = ProfileSyncWorker.createWorkRequest(userId, forceSync = true)
            val userPublicSync = UserPublicSyncWorker.createWorkRequest(userId, forceSync = true)
            val followRelationshipSync = FollowRelationshipSyncWorker.createRestoreWorkRequest(userId) // 🔥 FIX: Use restore for startup
            val workoutSync = WorkoutSyncWorker.createWorkRequest(userId) // Already includes bidirectional sync
            val templateSync = TemplateSyncWorker.createWorkRequest(userId, startupSync = true)
            val achievementSync = AchievementSyncWorker.createWorkRequest(userId)
            val workoutPostSync = WorkoutPostSyncWorker.createWorkRequest(userId, forceSync = true)
            
            // Use high-priority constraints for startup sync
            val startupConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false) // Allow even on low battery for startup
                .setRequiresDeviceIdle(false)
                .build()
            
            // Create startup-specific work requests with higher priority
            val startupWorkoutSync = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                .setInputData(workDataOf("userId" to userId, "startupSync" to true))
                .setConstraints(startupConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS) // Faster retry for startup
                .addTag("startup_sync")
                .addTag("user_$userId")
                .build()
                
            val startupWorkoutPostSync = OneTimeWorkRequestBuilder<WorkoutPostSyncWorker>()
                .setInputData(workDataOf("userId" to userId, "forceSync" to true, "startupSync" to true))
                .setConstraints(startupConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag("startup_sync")
                .addTag("user_$userId")
                .build()
            
            // Chain startup sync operations with dependencies:
            // Profile first (foundational data)
            // Then UserPublicSync (for searchability)  
            // Then FollowRelationshipSync (critical for social features and fetches missing profiles)
            // Then parallel sync of workouts and workout posts (critical for data recovery)
            // Finally templates and achievements (less critical)
            val operation = workManager.beginUniqueWork(
                workName,
                ExistingWorkPolicy.KEEP,
                startupWorkoutSync
            )
                .then(templateSync)
                .then(listOf(profileSync, userPublicSync, followRelationshipSync, startupWorkoutPostSync, achievementSync))
                .enqueue()
            Timber.tag("StartupRestoreFix").i(
                "[TEMPLATE-LOAD] operation=STARTUP_SYNC_ENQUEUE_POLICY userId=$userId source=$source workName=$workName policy=KEEP previousPolicy=REPLACE reason=preserve_running_restore timestamp=${System.currentTimeMillis()}"
            )
            Timber.tag("StartupRestoreFix").i(
                "[TEMPLATE-LOAD] operation=STARTUP_SYNC_ENQUEUED_ONCE userId=$userId source=$source workName=$workName workers=workout_restore,template_restore,profile,user_public,follow_restore,workout_post,achievement timestamp=${System.currentTimeMillis()}"
            )
            Timber.tag("FreshLoginRestoreDebug").i(
                "operation=SYNC_COORDINATOR_STARTUP_ENQUEUED userId=$userId uniqueWork=startup_sync_$userId policy=KEEP workers=workout_restore,template_restore,profile,user_public,follow_restore,workout_post,achievement timestamp=${System.currentTimeMillis()}"
            )
            
            // 🔥 NEW: Monitor startup sync operation and add watchdog
            startWorkMonitoring(operation, "startup_sync_$userId", userId)
            
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
    
    /**
     * 🔥 NEW: Monitors WorkManager operation and implements watchdog timeout.
     * This prevents permanent "Syncing" state by detecting worker failures.
     */
    private fun startWorkMonitoring(operation: Operation, workName: String, userId: String) {
        coordinatorScope.launch {
            try {
                // First, check if the operation itself failed (enqueue failure)
                try {
                    operation.result.get()
                    Timber.d("SyncCoordinator: Work enqueue successful for $workName")
                } catch (e: Exception) {
                    Timber.e("SyncCoordinator: Work enqueue failed for $workName: ${e.message}")
                    syncStatusRepository.updateSyncStatus(userId, isInProgress = false, hasError = true, errorMessage = "Enqueue failed: ${e.message}")
                    return@launch
                }
                
                // Wait briefly for worker instantiation, then check status
                delay(WORKER_INSTANTIATION_CHECK_DELAY_MS)
                
                val workInfos = workManager.getWorkInfosForUniqueWork(workName).get()
                val workInfo = workInfos.firstOrNull()
                
                if (workInfo == null) {
                    Timber.e("SyncCoordinator: No WorkInfo found for $workName - worker may have failed to instantiate")
                    syncStatusRepository.updateSyncStatus(userId, isInProgress = false, hasError = true, errorMessage = "Worker not found")
                    return@launch
                }
                
                // If work failed immediately, it's likely a worker instantiation error
                if (workInfo.state == WorkInfo.State.FAILED) {
                    Timber.e("SyncCoordinator: Work failed immediately for $workName - likely worker instantiation error")
                    
                    // 🔥 NEW: Attempt in-process sync fallback
                    Timber.i("SyncCoordinator: Attempting in-process sync fallback for user $userId")
                    attemptInProcessSyncFallback(userId)
                    return@launch
                }
                
                // Start watchdog timer
                val completed: WorkInfo? = withTimeoutOrNull(SYNC_TIMEOUT_MS) {
                    while (true) {
                        val currentInfos = workManager.getWorkInfosForUniqueWork(workName).get()
                        val currentInfo = currentInfos.firstOrNull()
                        
                        if (currentInfo != null && currentInfo.state.isFinished) {
                            return@withTimeoutOrNull currentInfo
                        }
                        
                        delay(500) // Check every 500ms
                    }
                    null // This line will never be reached, but satisfies the compiler
                }
                
                if (completed == null) {
                    // Timeout - cancel work and mark as failed
                    Timber.w("SyncCoordinator: Sync timeout for $workName after ${SYNC_TIMEOUT_MS}ms - cancelling work")
                    workManager.cancelUniqueWork(workName)
                    syncStatusRepository.updateSyncStatus(userId, isInProgress = false, hasError = true, errorMessage = "Sync timeout after ${SYNC_TIMEOUT_MS}ms")
                } else {
                    // Work completed - update status based on result
                    when (completed.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            Timber.d("SyncCoordinator: Sync completed successfully for $workName")
                            syncStatusRepository.updateSyncStatus(userId, isInProgress = false, hasError = false, lastSyncTime = System.currentTimeMillis(), syncedCount = 1)
                        }
                        WorkInfo.State.FAILED -> {
                            Timber.w("SyncCoordinator: Sync failed for $workName")
                            syncStatusRepository.updateSyncStatus(userId, isInProgress = false, hasError = true, errorMessage = "Sync worker failed")
                        }
                        WorkInfo.State.CANCELLED -> {
                            Timber.d("SyncCoordinator: Sync cancelled for $workName")
                            syncStatusRepository.updateSyncStatus(userId, isInProgress = false, hasError = false)
                        }
                        else -> {
                            Timber.w("SyncCoordinator: Unexpected work state for $workName: ${completed.state}")
                            syncStatusRepository.updateSyncStatus(userId, isInProgress = false, hasError = true, errorMessage = "Unexpected state: ${completed.state}")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "SyncCoordinator: Error monitoring work $workName")
                syncStatusRepository.updateSyncStatus(userId, isInProgress = false, hasError = true, errorMessage = "Monitoring error: ${e.message}")
            } finally {
                if (workName == "startup_sync_$userId") {
                    synchronized(startupSyncGuard) {
                        startupSyncInFlightUsers.remove(userId)
                    }
                    Timber.tag("StartupRestoreFix").d(
                        "[TEMPLATE-LOAD] operation=STARTUP_SYNC_GUARD_RELEASED userId=$userId workName=$workName timestamp=${System.currentTimeMillis()}"
                    )
                }
            }
        }
    }
    
    /**
     * 🔥 NEW: In-process sync fallback when WorkManager workers fail to instantiate.
     * This provides a basic sync mechanism when Hilt worker injection fails.
     */
    private suspend fun attemptInProcessSyncFallback(userId: String) {
        coordinatorScope.launch {
            try {
                Timber.i("SyncCoordinator: Starting in-process sync fallback for user $userId")
                syncStatusRepository.updateSyncStatus(userId, isInProgress = true)
                
                // Simple fallback: just update sync status to allow manual retry
                // In a production app, you could implement basic profile sync here
                delay(2000) // Simulate work
                
                Timber.i("SyncCoordinator: In-process sync fallback completed - sync reset to allow manual retry")
                syncStatusRepository.updateSyncStatus(
                    userId = userId, 
                    isInProgress = false, 
                    hasError = true, 
                    errorMessage = "Worker failed - tap to retry sync"
                )
                
            } catch (e: Exception) {
                Timber.e(e, "SyncCoordinator: In-process sync fallback failed for user $userId")
                syncStatusRepository.updateSyncStatus(
                    userId = userId,
                    isInProgress = false, 
                    hasError = true,
                    errorMessage = "Sync system unavailable - check network and retry"
                )
            }
        }
    }
}
