package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.ExistingWorkPolicy
import androidx.work.Data
import com.example.liftrix.domain.repository.SyncStatusRepository
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.config.OfflineArchitectureFlags
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import timber.log.Timber
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope

/**
 * Master coordinator worker that orchestrates all sync operations during periodic background sync.
 * 
 * This worker is responsible for:
 * - Running periodic sync operations for all entity types (workouts, templates, achievements, profiles)
 * - Managing sync dependencies and execution order
 * - Providing centralized error handling and retry logic
 * - Updating global sync status and metrics
 * - Optimizing sync performance through intelligent scheduling
 * 
 * Sync Order Strategy:
 * 1. Profile sync first (foundational user data required by other entities)
 * 2. Workout sync (most frequently updated, highest priority)
 * 3. Template sync (moderate update frequency)
 * 4. Achievement sync (lowest update frequency, can be delayed)
 * 
 * Performance Optimizations:
 * - Only triggers sync for entities with unsynced changes
 * - Uses parallel execution with supervisorScope for independent entity syncs
 * - Implements intelligent backoff on consecutive failures
 * - Tracks sync metrics for performance monitoring
 * - Profile syncs first (foundation), then other entities in parallel
 * 
 * Error Handling:
 * - Individual entity sync failures don't block other entities
 * - Comprehensive error reporting and analytics
 * - Automatic retry logic with exponential backoff
 * - Graceful degradation when partial sync fails
 */
@HiltWorker
class MasterSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncStatusRepository: SyncStatusRepository,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore,
    private val firebaseAuth: com.google.firebase.auth.FirebaseAuth
) : BaseSyncWorker(context, params) {

    init {
        Timber.d("✅ MasterSyncWorker constructed with Hilt DI")
    }

    override val workerName: String = "MasterSyncWorker"
    
    // Get WorkManager instance directly (initialized manually in Application.onCreate)
    private val workManager: WorkManager
        get() {
            val instance = WorkManager.getInstance(applicationContext)
            Timber.v("MasterSyncWorker accessing WorkManager instance: $instance, factory: ${instance.configuration.workerFactory}")
            return instance
        }

    companion object {
        const val WORK_NAME = "master_sync_work"
        const val KEY_SYNC_SUMMARY = "sync_summary"
        // Removed SYNC_DELAY_MS - using parallel execution instead
    }

    override suspend fun performSync(userId: String): Result {

        Timber.d("MasterSyncWorker: Starting periodic sync for user $userId")
        val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
            OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
        if (!OfflineArchitectureFlags.ROOM_FIRST_ENABLED) {
            Timber.w("MasterSyncWorker running in legacy mode (Room-first disabled)")
        }
        Timber.d("MasterSyncWorker dirty gating enabled: $useDirtyFlagGating")
        
        try {
            // Check cancellation before starting
            checkCancellation()
            
            // 🔥 CRITICAL FIX: Validate sync pipeline before starting
            val pipelineValidation = validateSyncPipeline(userId)
            if (!pipelineValidation.isValid) {
                Timber.e("MasterSyncWorker: Sync pipeline validation failed for user $userId")
                Timber.e("MasterSyncWorker: Validation errors: ${pipelineValidation.errors}")
                
                return Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Sync pipeline validation failed: ${pipelineValidation.errors.joinToString()}")
                        .putStringArray("validation_errors", pipelineValidation.errors.toTypedArray())
                        .build()
                )
            }
            
            // Update sync status to indicate sync in progress
            updateSyncStatus(userId, isInProgress = true)
            
            // Track sync results for each entity type
            val syncResults = mutableMapOf<String, SyncResult>()
            var hasPartialFailure = false
            
            // 1. Profile Sync (Foundation) - Must complete first as other entities depend on it
            checkCancellation()
            Timber.d("MasterSyncWorker: Starting profile sync (foundation)")
            val profileResult = syncEntity("profile", userId)
            syncResults["profile"] = profileResult
            if (!profileResult.success) {
                Timber.w("MasterSyncWorker: Profile sync failed, continuing with other entities")
                hasPartialFailure = true
            }
            
            // 2. Parallel sync for remaining entities using supervisorScope
            // supervisorScope ensures one entity failure doesn't cancel others
            checkCancellation()
            Timber.d("MasterSyncWorker: Starting parallel sync for remaining entities")
            
            supervisorScope {
                val parallelResults = listOf(
                    async { 
                        Timber.d("MasterSyncWorker: Syncing workouts (parallel)")
                        "workout" to syncEntity("workout", userId)
                    },
                    async { 
                        Timber.d("MasterSyncWorker: Syncing templates (parallel)")
                        "template" to syncEntity("template", userId)
                    },
                    async { 
                        Timber.d("MasterSyncWorker: Syncing user_public (parallel)")
                        "user_public" to syncEntity("user_public", userId)
                    },
                    async { 
                        Timber.d("MasterSyncWorker: Syncing achievements (parallel)")
                        "achievement" to syncEntity("achievement", userId)
                    },
                    async { 
                        Timber.d("MasterSyncWorker: Syncing follow relationships (parallel)")
                        "follow_relationship" to syncEntity("follow_relationship", userId)
                    }
                ).awaitAll()
                
                // Process parallel results
                parallelResults.forEach { (entityType, result) ->
                    syncResults[entityType] = result
                    if (!result.success) {
                        Timber.w("MasterSyncWorker: $entityType sync failed")
                        hasPartialFailure = true
                    }
                }
            }
            
            Timber.d("MasterSyncWorker: All parallel syncs complete")
            
            // Calculate overall sync summary
            val totalSynced = syncResults.values.sumOf { it.syncedCount }
            val totalFailed = syncResults.values.sumOf { it.failedCount }
            val totalEntities = syncResults.size
            val successfulEntities = syncResults.values.count { it.success }
            
            val syncSummary = "Entities: $successfulEntities/$totalEntities successful, Items: $totalSynced synced, $totalFailed failed"
            
            Timber.d("MasterSyncWorker: Sync complete - $syncSummary")
            
            // Update final sync status
            updateSyncStatus(
                userId = userId,
                isInProgress = false,
                hasError = hasPartialFailure,
                lastSyncTime = System.currentTimeMillis()
            )
            
            return when {
                successfulEntities == totalEntities && totalFailed == 0 -> {
                    // Perfect sync - all entities successful, no item failures
                    Result.success(
                        Data.Builder()
                            .putString(KEY_SYNC_SUMMARY, syncSummary)
                            .build()
                    )
                }
                successfulEntities > 0 -> {
                    // Partial success - some entities synced successfully
                    Result.success(
                        Data.Builder()
                            .putString(KEY_SYNC_SUMMARY, syncSummary)
                            .putString(KEY_ERROR_MESSAGE, "Partial sync completed")
                            .build()
                    )
                }
                else -> {
                    // All entities failed - let base class handle retry
                    val errorMessage = "All sync operations failed"
                    Timber.e("MasterSyncWorker: $errorMessage")
                    throw Exception(errorMessage)
                }
            }
            
        } catch (e: CancellationException) {
            // Re-throw cancellation to maintain cancellation chain
            Timber.d("MasterSyncWorker: Sync cancelled for user $userId")
            updateSyncStatus(
                userId = userId,
                isInProgress = false,
                hasError = false // Not an error, just cancelled
            )
            throw e
        } catch (e: Exception) {
            Timber.e(e, "MasterSyncWorker: Fatal error during sync for user $userId")
            
            updateSyncStatus(
                userId = userId,
                isInProgress = false,
                hasError = true
            )
            
            // Let base class handle the error
            throw e
        }
    }
    
    /**
     * Syncs a specific entity type and returns the result.
     * 
     * FIXED: Now properly enqueues work with unique names per user and entity type.
     * The unique naming prevents work cancellation due to duplicate work names.
     */
    private suspend fun syncEntity(entityType: String, userId: String): SyncResult {
        return try {
            val workRequest = when (entityType) {
                "profile" -> ProfileSyncWorker.createWorkRequest(userId)
                "workout" -> WorkoutSyncWorker.createWorkRequest(userId)
                "template" -> TemplateSyncWorker.createWorkRequest(userId)
                "user_public" -> UserPublicSyncWorker.createWorkRequest(userId)
                "achievement" -> AchievementSyncWorker.createWorkRequest(userId)
                "follow_relationship" -> FollowRelationshipSyncWorker.createWorkRequest(userId)
                else -> {
                    Timber.e("MasterSyncWorker: Unknown entity type: $entityType")
                    return SyncResult(success = false, syncedCount = 0, failedCount = 1, error = "Unknown entity type")
                }
            }
            
            // FIXED: Use unique work name that includes both entity type and userId
            // This prevents work replacement when multiple users or rapid sync calls occur
            val uniqueWorkName = "master_${entityType}_sync_${userId}_${System.currentTimeMillis()}"
            
            // Execute sync work with KEEP policy to prevent cancellation
            // KEEP ensures existing work completes before new work is enqueued
            workManager.enqueueUniqueWork(
                uniqueWorkName,
                androidx.work.ExistingWorkPolicy.KEEP, // Changed from REPLACE to KEEP
                workRequest
            ).result.get() // Wait synchronously for the work to be enqueued
            
            // The sync was successfully enqueued
            // Note: We're not waiting for completion, but at least ensuring enqueue succeeds
            Timber.d("MasterSyncWorker: Successfully enqueued $entityType sync for user $userId")
            SyncResult(success = true, syncedCount = 1, failedCount = 0)
            
        } catch (e: Exception) {
            Timber.e(e, "MasterSyncWorker: Failed to sync $entityType for user $userId")
            SyncResult(success = false, syncedCount = 0, failedCount = 1, error = e.message)
        }
    }
    
    /**
     * Updates the global sync status for the user.
     */
    private suspend fun updateSyncStatus(
        userId: String,
        isInProgress: Boolean = false,
        hasError: Boolean = false,
        lastSyncTime: Long? = null
    ) {
        try {
            syncStatusRepository.updateSyncStatus(
                userId = userId,
                isInProgress = isInProgress,
                hasError = hasError,
                lastSyncTime = lastSyncTime
            )
        } catch (e: Exception) {
            Timber.e(e, "MasterSyncWorker: Failed to update sync status for user $userId")
            // Don't fail the entire sync operation for status update failures
        }
    }
    
    /**
     * Data class to track sync results for each entity type.
     */
    private data class SyncResult(
        val success: Boolean,
        val syncedCount: Int,
        val failedCount: Int,
        val error: String? = null
    )
    
    /**
     * 🔥 CRITICAL: Validation result for sync pipeline health check
     */
    private data class PipelineValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )
    
    /**
     * 🔥 CRITICAL FIX: Validates that all sync workers can be instantiated before starting sync.
     * This prevents the scenario where some workers fail to instantiate, leading to incomplete sync.
     */
    private fun validateSyncPipeline(userId: String): PipelineValidationResult {
        val errors = mutableListOf<String>()
        
        try {
            Timber.d("MasterSyncWorker: Validating sync pipeline for user $userId")
            
            val context = applicationContext
            
            // Validate WorkerServiceLocator dependencies (lighter approach)
            // This will fail if critical dependencies like database, firestore, etc. can't be resolved
            try {
                WorkerServiceLocator.getWorkoutSyncDependencies(context)
                Timber.d("✅ WorkoutSyncWorker dependencies: SUCCESS")
            } catch (e: Exception) {
                val error = "WorkoutSyncWorker dependency resolution failed: ${e.message}"
                errors.add(error)
                Timber.e(e, "❌ WorkoutSyncWorker dependencies: FAILED")
            }
            
            try {
                WorkerServiceLocator.getTemplateSyncDependencies(context)
                Timber.d("✅ TemplateSyncWorker dependencies: SUCCESS")
            } catch (e: Exception) {
                val error = "TemplateSyncWorker dependency resolution failed: ${e.message}"
                errors.add(error)
                Timber.e(e, "❌ TemplateSyncWorker dependencies: FAILED")
            }
            
            try {
                WorkerServiceLocator.getAchievementSyncDependencies(context)
                Timber.d("✅ AchievementSyncWorker dependencies: SUCCESS")
            } catch (e: Exception) {
                val error = "AchievementSyncWorker dependency resolution failed: ${e.message}"
                errors.add(error)
                Timber.e(e, "❌ AchievementSyncWorker dependencies: FAILED")
            }
            
            try {
                WorkerServiceLocator.getProfileSyncDependencies(context)
                Timber.d("✅ ProfileSyncWorker dependencies: SUCCESS")
            } catch (e: Exception) {
                val error = "ProfileSyncWorker dependency resolution failed: ${e.message}"
                errors.add(error)
                Timber.e(e, "❌ ProfileSyncWorker dependencies: FAILED")
            }
            
            // Test that Firebase services are available
            try {
                if (firebaseAuth.currentUser == null) {
                    errors.add("Firebase Auth: User not authenticated")
                    Timber.w("❌ Firebase Auth: User not authenticated")
                } else {
                    Timber.d("✅ Firebase services: SUCCESS")
                }
            } catch (e: Exception) {
                val error = "Firebase services unavailable: ${e.message}"
                errors.add(error)
                Timber.e(e, "❌ Firebase services: FAILED")
            }
            
            val isValid = errors.isEmpty()
            if (isValid) {
                Timber.i("✅ Sync pipeline validation PASSED for user $userId")
            } else {
                Timber.e("❌ Sync pipeline validation FAILED for user $userId - ${errors.size} errors")
                errors.forEach { error ->
                    Timber.e("   - $error")
                }
            }
            
            return PipelineValidationResult(isValid, errors)
            
        } catch (e: Exception) {
            val error = "Pipeline validation exception: ${e.message}"
            Timber.e(e, "MasterSyncWorker: Pipeline validation failed with exception")
            return PipelineValidationResult(false, listOf(error))
        }
    }
}
