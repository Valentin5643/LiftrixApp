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
import com.example.liftrix.data.local.dao.WorkoutDao
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.service.sync.ConflictResolver
import com.example.liftrix.service.sync.ResolutionStrategy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.time.Instant
import com.google.firebase.Timestamp
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import com.example.liftrix.data.model.ExerciseDto
import com.example.liftrix.data.model.WorkoutSyncDto
import com.example.liftrix.data.model.SyncPayload
import com.example.liftrix.data.model.EnhancedWorkoutData
import com.example.liftrix.data.model.DirectExerciseListWrapper
import com.example.liftrix.data.model.LegacyExerciseWrapper
import com.example.liftrix.data.remote.dto.WorkoutDto

@HiltWorker
class WorkoutSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val workoutDao: WorkoutDao,
    private val workoutMapper: WorkoutMapper,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val conflictResolver: ConflictResolver
) : BaseSyncWorker(context, params) {
    
    // 🔧 HOTFIX: Fallback constructor for when Hilt factory generation fails
    // This allows WorkManager to instantiate the worker via reflection
    // TEMPORARY: Remove once Hilt assisted factories are confirmed working
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        WorkerServiceLocator.getWorkoutSyncDependencies(context).run {
            Timber.w("⚠️ WorkoutSyncWorker using FALLBACK constructor - Hilt factory failed!")
            return@run this
        }
    )
    
    // Helper constructor to unpack the dependency structure
    private constructor(
        context: Context,
        params: WorkerParameters,
        deps: WorkerServiceLocator.WorkoutSyncDependencies
    ) : this(
        context, params,
        deps.workoutDao, deps.workoutMapper, deps.firestore,
        deps.auth, deps.conflictResolver
    )

    init {
        val processName = getProcessName()
        Timber.d("✅ WorkoutSyncWorker constructed with Hilt dependency injection in process: $processName")
    }
    
    private fun getProcessName(): String {
        return try {
            val pid = android.os.Process.myPid()
            val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: "unknown"
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    override val workerName: String = "WorkoutSyncWorker"
    
    companion object {
        const val WORK_NAME = "workout_sync_work"
        private const val BATCH_SIZE = 20
        
        fun createWorkRequest(userId: String): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                .setInputData(workDataOf(KEY_USER_ID to userId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag("workout_sync")
                .addTag("user_$userId") // 🔥 NEW: User-specific tagging for job management
                .build()
        }
        
        /**
         * 🔥 NEW: Get unique work name per user to prevent job conflicts
         */
        fun getWorkName(userId: String): String = "${WORK_NAME}_$userId"
    }

    override suspend fun performSync(userId: String): Result {
        try {
            // Check cancellation before starting
            checkCancellation()
            
            val syncStartTime = System.currentTimeMillis()
            Timber.d("[SYNC-BIDIRECTIONAL] WorkoutSync started for user $userId at $syncStartTime")
            
            // Validate authentication before sync operations
            val currentUser = auth.currentUser
            if (currentUser == null || currentUser.uid != userId) {
                Timber.e("WorkoutSyncWorker: User not authenticated or user ID mismatch. Current: ${currentUser?.uid}, Expected: $userId")
                return Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "User not authenticated for sync operation")
                        .build()
                )
            }
            
            val authDisplayName = currentUser.displayName
            Timber.d("WorkoutSyncWorker: Authentication validated for user $userId")
            
            // 🔥 NEW: First, fetch and merge remote workouts (bidirectional sync)
            val fetchResult = fetchAndMergeRemoteWorkouts(userId)
            if (!fetchResult) {
                Timber.w("[SYNC-BIDIRECTIONAL] Remote workout fetch failed, continuing with local sync")
            }

            // 🔍 ENHANCED DEBUGGING: Check database state after remote fetch
            val totalWorkoutCount = workoutDao.getWorkoutCountForUser(userId)
            val unsyncedCount = workoutDao.getUnsyncedCountForUser(userId)
            val syncedCount = workoutDao.getSyncedCountForUser(userId)
            
 
            
            // 🔍 Get sample workout data to verify user ID matching
            if (totalWorkoutCount > 0) {
                try {
                    val allWorkoutsFlow = workoutDao.getAllWorkoutsForUser(userId)
                    val sampleWorkouts = allWorkoutsFlow.first().take(3)
                    sampleWorkouts.forEachIndexed { index, workout ->
                    }
                } catch (e: Exception) {
                    Timber.w("Failed to get sample workouts: ${e.message}")
                }
            }

            // Get unsynced workouts for this specific user
            val unsyncedWorkouts = workoutDao.getUnsyncedWorkoutsForUser(userId)
            
            if (unsyncedWorkouts.isEmpty()) {
                val syncEndTime = System.currentTimeMillis()
                Timber.d("[SYNC-BIDIRECTIONAL] WorkoutSync completed (no workouts) for user $userId in ${syncEndTime - syncStartTime}ms")
                
                // 🔍 Additional debugging when no unsynced workouts found
                if (totalWorkoutCount > 0) {
                    Timber.w("WARNING: User has $totalWorkoutCount total workouts but 0 unsynced - all workouts already synced?")
                } else {
                    Timber.w("WARNING: User has no workouts at all in database")
                }
                
                return Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            Timber.d("Found ${unsyncedWorkouts.size} unsynced workouts for user $userId")
            
            var successCount = 0
            var failureCount = 0
            
            // Use batch processing with cancellation checks
            processBatchesWithCancellation(
                items = unsyncedWorkouts,
                batchSize = BATCH_SIZE
            ) { batch ->
                val firestoreBatch = firestore.batch()
                
                batch.forEach { workout ->
                    val docRef = firestore
                        .collection("users")
                        .document(userId)
                        .collection("workouts")
                        .document(workout.id)
                    
                    try {
                        // Check for remote version for conflict resolution
                        val remoteDoc = docRef.get().await()
                        
                        val dataToSync = if (remoteDoc.exists()) {
                            // Handle conflict resolution using proper DTO
                            val remoteWorkout = remoteDoc.toObject(WorkoutDto::class.java)
                            if (remoteWorkout != null) {
                                // Simple last-write-wins for now - can be enhanced with ConflictResolver
                                val remoteUpdatedAtMillis = remoteWorkout.updatedAt?.toDate()?.time ?: 0L
                                val localUpdatedAtMillis = workout.updatedAt.epochSecond * 1000
                                if (localUpdatedAtMillis > remoteUpdatedAtMillis) {
                                    workoutMapper.toFirestoreDto(workoutMapper.toDomain(workout), userId)
                                } else {
                                    // Remote is newer, update local
                                    workoutDao.updateSyncStatusForUser(
                                        id = workout.id,
                                        userId = userId,
                                        isSynced = true,
                                        version = System.currentTimeMillis()
                                    )
                                    null // Skip this item in batch
                                }
                            } else {
                                workoutMapper.toFirestoreDto(workoutMapper.toDomain(workout), userId)
                            }
                        } else {
                            workoutMapper.toFirestoreDto(workoutMapper.toDomain(workout), userId)
                        }
                        
                        if (dataToSync != null) {
                            // 🔥 ENHANCED: Comprehensive logging for upload operations
                            Timber.d("[SYNC-UPLOAD] 📤 Preparing workout for upload: ${workout.id}")
                            Timber.d("[SYNC-UPLOAD]   - Name: '${workout.name}'")
                            Timber.d("[SYNC-UPLOAD]   - Status: ${workout.status}")
                            Timber.d("[SYNC-UPLOAD]   - Date: ${workout.date}")
                            Timber.d("[SYNC-UPLOAD]   - Last modified: ${workout.updatedAt}")
                            Timber.d("[SYNC-UPLOAD]   - Is synced: ${workout.isSynced}")
                            
                            val exercisesList = try {
                                if (workout.exercisesJson.isNullOrBlank()) {
                                    Timber.d("[SYNC-UPLOAD]   - No exercises JSON data")
                                    emptyList<ExerciseDto>()
                                } else {
                                    // Handle both wrapped and unwrapped formats
                                    val json = Json { ignoreUnknownKeys = true }
                                    val exercises = try {
                                        val wrapper = json.decodeFromString<LegacyExerciseWrapper>(workout.exercisesJson)
                                        wrapper.exercises
                                    } catch (e: Exception) {
                                        json.decodeFromString<List<ExerciseDto>>(workout.exercisesJson)
                                    }
                                    
                                    Timber.d("[SYNC-UPLOAD]   - Parsed ${exercises.size} exercises successfully")
                                    exercises.forEachIndexed { index, exercise ->
                                        Timber.v("[SYNC-UPLOAD]     - Exercise $index: '${exercise.name}' (${exercise.sets.size} sets)")
                                    }
                                    exercises
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "[SYNC-UPLOAD] ⚠️ Failed to parse exercises JSON for workout ${workout.id}, using empty list")
                                emptyList<ExerciseDto>()
                            }
                            
                            val exercisesForFirestore = exercisesList.map { exercise ->
                                mapOf(
                                    "id" to exercise.id,
                                    "name" to exercise.name,
                                    "muscleGroup" to exercise.muscleGroup,
                                    "orderIndex" to exercise.orderIndex,
                                    "notes" to exercise.notes,
                                    "sets" to exercise.sets.map { set ->
                                        mapOf(
                                            "setNumber" to set.setNumber,
                                            "targetReps" to set.targetReps,
                                            "actualReps" to set.actualReps,
                                            "targetWeight" to set.targetWeight,
                                            "actualWeight" to set.actualWeight,
                                            "completed" to set.completed,
                                            "rpe" to set.rpe
                                        )
                                    }
                                )
                            }
                            
                            val firestoreData = mapOf(
                                "id" to workout.id,
                                "userId" to userId,
                                "name" to workout.name,
                                "date" to Timestamp(workout.date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)),
                                "status" to workout.status.name,
                                "startTime" to workout.startTime?.epochSecond,
                                "endTime" to workout.endTime?.epochSecond,
                                "exercises" to exercisesForFirestore,
                                "notes" to workout.notes,
                                "templateId" to workout.templateId,
                                "createdAt" to Timestamp(workout.createdAt),
                                "syncVersion" to System.currentTimeMillis(),
                                "lastModified" to Timestamp.now(),
                                "isSynced" to true,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            
                            firestoreBatch.set(docRef, firestoreData, SetOptions.merge())
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "Error preparing workout ${workout.id} for batch sync")
                        failureCount++
                    }
                }
                
                try {
                    // 🔥 ENHANCED: Log batch commit attempt
                    Timber.d("[SYNC-BATCH] 🚀 Committing batch of ${batch.size} workouts to Firestore")
                    val batchCommitStart = System.currentTimeMillis()
                    
                    firestoreBatch.commit().await()
                    
                    val batchCommitDuration = System.currentTimeMillis() - batchCommitStart
                    Timber.i("[SYNC-BATCH] ✅ Batch commit successful in ${batchCommitDuration}ms")
                    
                    // Mark all workouts in this batch as synced
                    batch.forEach { workout ->
                        try {
                            workoutDao.updateSyncStatusForUser(
                                id = workout.id,
                                userId = userId,
                                isSynced = true,
                                version = System.currentTimeMillis()
                            )
                            Timber.d("[SYNC-BATCH] ✅ Marked workout as synced: ${workout.id}")
                        } catch (e: Exception) {
                            Timber.w(e, "[SYNC-BATCH] ⚠️ Failed to mark workout as synced: ${workout.id}")
                        }
                    }
                    successCount += batch.size
                    Timber.i("[SYNC-BATCH] 📊 Successfully synced batch of ${batch.size} workouts")
                    
                } catch (e: Exception) {
                    Timber.e(e, "[SYNC-BATCH] ❌ Batch sync failed for ${batch.size} workouts")
                    Timber.e("[SYNC-BATCH]   - Error type: ${e.javaClass.simpleName}")
                    Timber.e("[SYNC-BATCH]   - Error message: ${e.message}")
                    batch.forEach { workout ->
                        Timber.w("[SYNC-BATCH]   - Failed workout: ${workout.id} ('${workout.name}')")
                    }
                    failureCount += batch.size
                }
            }
            
            val syncEndTime = System.currentTimeMillis()
            val syncDuration = syncEndTime - syncStartTime
            
            val finalAuthUser = auth.currentUser
            val finalAuthDisplayName = finalAuthUser?.displayName
            
            if (finalAuthUser?.uid != userId) {
                Timber.e("[SYNC-BIDIRECTIONAL] Auth changed during workout sync: userId=$userId vs finalAuth=${finalAuthUser?.uid}")
            } else if (finalAuthDisplayName != authDisplayName) {
                Timber.w("[SYNC-BIDIRECTIONAL] Display name changed during workout sync: '$authDisplayName' -> '$finalAuthDisplayName'")
            }
            
            Timber.d("[SYNC-BIDIRECTIONAL] Complete bidirectional sync finished - Local→Remote: $successCount, Duration: ${syncDuration}ms")
            
            Timber.d("Workout sync complete - Success: $successCount, Failed: $failureCount")
            
            return if (failureCount > 0 && successCount == 0) {
                // All failed - let base class handle retry
                throw Exception("All workouts failed to sync")
            } else {
                // Some or all succeeded
                Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, successCount)
                        .putString(KEY_ERROR_MESSAGE, if (failureCount > 0) "Partial sync completed" else null)
                        .build()
                )
            }
            
        } catch (e: CancellationException) {
            // Re-throw cancellation to maintain cancellation chain
            throw e
        } catch (e: Exception) {
            // Let base class handle the error
            throw e
        }
    }

    /**
     * 🔥 NEW: Fetches remote workouts from Firestore and merges them with local database.
     * This implements the missing remote-to-local sync functionality.
     */
    private suspend fun fetchAndMergeRemoteWorkouts(userId: String): Boolean {
        return try {
            Timber.d("[SYNC-FETCH] Fetching remote workouts for user $userId")
            
            val remoteWorkouts = firestore
                .collection("users")
                .document(userId)
                .collection("workouts")
                .get()
                .await()
            
            var mergedCount = 0
            var conflictCount = 0
            
            for (doc in remoteWorkouts.documents) {
                try {
                    val remoteWorkout = doc.toObject(WorkoutDto::class.java)
                    if (remoteWorkout != null) {
                        val mergeResult = mergeRemoteWorkoutWithLocal(remoteWorkout, userId)
                        when (mergeResult) {
                            "MERGED" -> mergedCount++
                            "CONFLICT_RESOLVED" -> conflictCount++
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "[SYNC-FETCH] Failed to process remote workout ${doc.id}")
                }
            }
            
            Timber.d("[SYNC-FETCH] Remote fetch complete - Merged: $mergedCount, Conflicts: $conflictCount")
            true
            
        } catch (e: Exception) {
            Timber.e(e, "[SYNC-FETCH] Failed to fetch remote workouts for user $userId")
            false
        }
    }
    
    /**
     * 🔥 ENHANCED: Merges a remote workout with local database using sophisticated conflict resolution.
     * Favors most recently updated workout with comprehensive logging and edge case handling.
     */
    private suspend fun mergeRemoteWorkoutWithLocal(remoteWorkout: WorkoutDto, userId: String): String {
        val remoteId = remoteWorkout.id ?: run {
            Timber.w("[SYNC-MERGE] Remote workout has no ID, skipping merge")
            return "SKIPPED"
        }
        
        val localWorkout = workoutDao.getWorkoutByIdForUser(remoteId, userId)
        
        return if (localWorkout == null) {
            // Remote workout doesn't exist locally - insert it
            try {
                val domainWorkout = workoutMapper.fromFirestoreDto(remoteWorkout)
                val localEntity = workoutMapper.toEntity(domainWorkout, isSynced = true)
                workoutDao.insertWorkout(localEntity)
                
                Timber.i("[SYNC-MERGE] ✅ Successfully inserted new remote workout: $remoteId (name: '${remoteWorkout.name}')")
                Timber.d("[SYNC-MERGE]   - Remote created: ${remoteWorkout.createdAt?.toDate()}")
                Timber.d("[SYNC-MERGE]   - Remote updated: ${remoteWorkout.updatedAt?.toDate()}")
                Timber.d("[SYNC-MERGE]   - Exercise count: ${remoteWorkout.exercises?.size ?: 0}")
                
                "MERGED"
            } catch (e: Exception) {
                Timber.e(e, "[SYNC-MERGE] ❌ Failed to insert remote workout: $remoteId")
                "FAILED"
            }
        } else {
            // 🔥 ENHANCED: Sophisticated conflict resolution with comprehensive logging
            val remoteUpdatedAt = remoteWorkout.updatedAt?.toDate()?.time ?: 0L
            val localUpdatedAt = localWorkout.updatedAt.epochSecond * 1000
            val timeDifferenceMs = kotlin.math.abs(remoteUpdatedAt - localUpdatedAt)
            
            // 🔥 MIGRATION FIX: Handle workouts with missing/zero updatedAt timestamps
            if (remoteUpdatedAt == 0L) {
                Timber.w("[SYNC-MIGRATION] 🔧 Remote workout $remoteId has missing updatedAt (epoch=0), forcing local version update")
                try {
                    // Force update the remote workout with current timestamp
                    val docRef = firestore
                        .collection("users")
                        .document(userId)
                        .collection("workouts")
                        .document(remoteId)
                    
                    docRef.update("updatedAt", FieldValue.serverTimestamp()).await()
                    Timber.i("[SYNC-MIGRATION] ✅ Fixed missing updatedAt for workout $remoteId")
                    
                    // Keep local version since remote had invalid timestamp
                    return "MIGRATION_FIXED"
                } catch (e: Exception) {
                    Timber.e(e, "[SYNC-MIGRATION] ❌ Failed to fix updatedAt for workout $remoteId")
                    // Continue with normal conflict resolution
                }
            }
            
            Timber.d("[SYNC-MERGE] 🔍 Conflict detected for workout: $remoteId")
            Timber.d("[SYNC-MERGE]   - Local name: '${localWorkout.name}' vs Remote name: '${remoteWorkout.name}'")
            Timber.d("[SYNC-MERGE]   - Local updated: ${java.util.Date(localUpdatedAt)} (${localUpdatedAt})")
            Timber.d("[SYNC-MERGE]   - Remote updated: ${java.util.Date(remoteUpdatedAt)} (${remoteUpdatedAt})")
            Timber.d("[SYNC-MERGE]   - Time difference: ${timeDifferenceMs}ms")
            Timber.d("[SYNC-MERGE]   - Local synced: ${localWorkout.isSynced}")
            Timber.d("[SYNC-MERGE]   - Local status: ${localWorkout.status}")
            
            when {
                // Remote is significantly newer (>1 second difference to account for precision)
                remoteUpdatedAt > localUpdatedAt + 1000 -> {
                    try {
                        val domainWorkout = workoutMapper.fromFirestoreDto(remoteWorkout)
                        val updatedEntity = workoutMapper.toEntity(domainWorkout, isSynced = true)
                        workoutDao.insertWorkout(updatedEntity) // Uses REPLACE strategy
                        
                        Timber.i("[SYNC-MERGE] ⬇️ Updated local workout with newer remote: $remoteId")
                        Timber.d("[SYNC-MERGE]   - Overwrote local data with remote (remote was ${timeDifferenceMs}ms newer)")
                        "CONFLICT_RESOLVED"
                    } catch (e: Exception) {
                        Timber.e(e, "[SYNC-MERGE] ❌ Failed to update local workout with remote: $remoteId")
                        "FAILED"
                    }
                }
                
                // Local is significantly newer and unsynced - will be uploaded in regular sync
                localUpdatedAt > remoteUpdatedAt + 1000 && !localWorkout.isSynced -> {
                    Timber.i("[SYNC-MERGE] ⬆️ Local workout is newer and unsynced: $remoteId")
                    Timber.d("[SYNC-MERGE]   - Will upload local changes (local was ${timeDifferenceMs}ms newer)")
                    "MERGED"
                }
                
                // Local is newer but already synced - potential data loss scenario
                localUpdatedAt > remoteUpdatedAt + 1000 && localWorkout.isSynced -> {
                    Timber.w("[SYNC-MERGE] ⚠️ Local workout is newer but already synced: $remoteId")
                    Timber.w("[SYNC-MERGE]   - This suggests a sync race condition or clock skew")
                    Timber.w("[SYNC-MERGE]   - Keeping local version to prevent data loss")
                    "KEPT_LOCAL"
                }
                
                // Timestamps are very close (within 1 second) - check other factors
                timeDifferenceMs <= 1000 -> {
                    if (!localWorkout.isSynced) {
                        Timber.i("[SYNC-MERGE] 🔄 Timestamps similar, local unsynced: $remoteId")
                        Timber.d("[SYNC-MERGE]   - Will upload local changes as authoritative")
                        "MERGED"
                    } else {
                        Timber.d("[SYNC-MERGE] 🟰 Timestamps similar, both synced: $remoteId")
                        Timber.d("[SYNC-MERGE]   - No action needed, data is synchronized")
                        "NO_ACTION"
                    }
                }
                
                // Default case - should not reach here
                else -> {
                    Timber.w("[SYNC-MERGE] ❓ Unexpected conflict resolution scenario for: $remoteId")
                    Timber.w("[SYNC-MERGE]   - Keeping local version as fallback")
                    "KEPT_LOCAL"
                }
            }
        }
    }
    
    // WorkoutDto moved to proper data class file:
    // app/src/main/java/com/example/liftrix/data/remote/dto/WorkoutDto.kt
    // This resolves Firestore field mapping warnings for isSynced and other fields
} 