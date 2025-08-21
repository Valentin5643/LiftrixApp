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
import com.example.liftrix.data.sync.RealtimeSyncService
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
    private val syncStatusRepository: SyncStatusRepository
) {
    // Use standard WorkManager instance (initialized via Configuration.Provider)
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)
    
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val PERIODIC_SYNC_WORK_NAME = "liftrix_periodic_sync"
        private const val IMMEDIATE_SYNC_WORK_NAME = "liftrix_immediate_sync"
        private const val SYNC_INTERVAL_MINUTES = 15L
    }
    
    /**
     * Schedules periodic background sync for all entities.
     * This should be called once during app initialization.
     * 
     * @param userId The user ID to sync data for
     */
    fun schedulePeriodicSync(userId: String) {
        Timber.d("SyncCoordinator: Scheduling periodic sync for user $userId")
        
        val periodicSyncRequest = PeriodicWorkRequestBuilder<MasterSyncWorker>(
            SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setInputData(workDataOf("userId" to userId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresDeviceIdle(false) // Allow sync when device is active
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .addTag("periodic_sync")
            .addTag("user_$userId")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "${PERIODIC_SYNC_WORK_NAME}_$userId",
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicSyncRequest
        )
        
        Timber.d("SyncCoordinator: Periodic sync scheduled for user $userId")
    }
    
    /**
     * Triggers immediate sync of all entities for a user.
     * This bypasses the periodic schedule and syncs immediately.
     * 
     * @param userId The user ID to sync data for
     * @return LiftrixResult indicating success or failure
     */
    suspend fun triggerImmediateSync(userId: String): LiftrixResult<Unit> {
        return try {
            Timber.d("SyncCoordinator: Triggering immediate sync for user $userId")
            
            // Update sync status to indicate sync in progress
            syncStatusRepository.updateSyncStatus(userId, isInProgress = true)
            
            // Create sync work requests for all entity types
            val profileSync = ProfileSyncWorker.createWorkRequest(userId, forceSync = true)
            val userPublicSync = UserPublicSyncWorker.createWorkRequest(userId, forceSync = true)
            val workoutSync = WorkoutSyncWorker.createWorkRequest(userId)
            val templateSync = TemplateSyncWorker.createWorkRequest(userId)
            val achievementSync = AchievementSyncWorker.createWorkRequest(userId)
            
            // Chain sync operations with dependencies:
            // Profile first (foundational data)
            // Then UserPublicSync (for searchability)
            // Then parallel sync of workouts, templates, and achievements
            workManager.beginUniqueWork(
                "${IMMEDIATE_SYNC_WORK_NAME}_$userId",
                ExistingWorkPolicy.REPLACE,
                profileSync
            )
                .then(userPublicSync)
                .then(listOf(workoutSync, templateSync, achievementSync))
                .enqueue()
            
            Timber.d("SyncCoordinator: Immediate sync work enqueued for user $userId")
            
            liftrixSuccess(Unit)
            
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
     * Triggers sync for a specific entity type only.
     * Useful for targeted sync after specific user actions.
     * 
     * @param userId The user ID to sync data for
     * @param entityType The specific entity type to sync ("workout", "template", "achievement", "profile")
     * @return LiftrixResult indicating success or failure
     */
    suspend fun triggerEntitySync(userId: String, entityType: String): LiftrixResult<Unit> {
        return try {
            Timber.d("SyncCoordinator: Triggering $entityType sync for user $userId")
            
            val workRequest = when (entityType.lowercase()) {
                "workout" -> WorkoutSyncWorker.createWorkRequest(userId)
                "template" -> TemplateSyncWorker.createWorkRequest(userId)
                "achievement" -> AchievementSyncWorker.createWorkRequest(userId)
                "profile" -> ProfileSyncWorker.createWorkRequest(userId, forceSync = true)
                "user_public" -> UserPublicSyncWorker.createWorkRequest(userId, forceSync = true)
                else -> {
                    return liftrixFailure(
                        LiftrixError.ValidationError(
                            field = "entityType",
                            violations = listOf("Unknown entity type: $entityType")
                        )
                    )
                }
            }
            
            workManager.enqueueUniqueWork(
                "${entityType}_sync_$userId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            Timber.d("SyncCoordinator: $entityType sync work enqueued for user $userId")
            
            liftrixSuccess(Unit)
            
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
     * @param userId The user ID to cancel sync for
     */
    fun cancelSyncForUser(userId: String) {
        Timber.d("SyncCoordinator: Cancelling all sync operations for user $userId")
        
        try {
            // Cancel periodic sync
            workManager.cancelUniqueWork("${PERIODIC_SYNC_WORK_NAME}_$userId")
            
            // Cancel any pending immediate sync
            workManager.cancelUniqueWork("${IMMEDIATE_SYNC_WORK_NAME}_$userId")
            
            // Cancel entity-specific syncs
            workManager.cancelUniqueWork("workout_sync_$userId")
            workManager.cancelUniqueWork("template_sync_$userId")
            workManager.cancelUniqueWork("achievement_sync_$userId")
            workManager.cancelUniqueWork("profile_sync_$userId")
            
            // Cancel by tag for any additional work
            workManager.cancelAllWorkByTag("user_$userId")
            
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
}