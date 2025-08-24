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
import com.example.liftrix.data.remote.dto.WorkoutFirestoreDto

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
                .build()
        }
    }

    override suspend fun performSync(userId: String): Result {
        try {
            // Check cancellation before starting
            checkCancellation()
            
            val syncStartTime = System.currentTimeMillis()
            Timber.d("[SYNC-CONFLICT] WorkoutSync started for user $userId at $syncStartTime")
            
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

            // 🔍 ENHANCED DEBUGGING: Check database state before sync
            val totalWorkoutCount = workoutDao.getWorkoutCountForUser(userId)
            val unsyncedCount = workoutDao.getUnsyncedCountForUser(userId)
            val syncedCount = workoutDao.getSyncedCountForUser(userId)
            
            Timber.d("[SYNC-DEBUG] Database state for user $userId:")
            Timber.d("[SYNC-DEBUG]   - Total workouts: $totalWorkoutCount")
            Timber.d("[SYNC-DEBUG]   - Unsynced workouts: $unsyncedCount") 
            Timber.d("[SYNC-DEBUG]   - Synced workouts: $syncedCount")
            
            // 🔍 Get sample workout data to verify user ID matching
            if (totalWorkoutCount > 0) {
                try {
                    val allWorkoutsFlow = workoutDao.getAllWorkoutsForUser(userId)
                    val sampleWorkouts = allWorkoutsFlow.first().take(3)
                    Timber.d("[SYNC-DEBUG] Sample workouts for user verification:")
                    sampleWorkouts.forEachIndexed { index, workout ->
                        Timber.d("[SYNC-DEBUG]   Sample[$index]: id=${workout.id.take(8)}..., userId=${workout.userId}, name=${workout.name}, synced=${workout.isSynced}, status=${workout.status}")
                    }
                } catch (e: Exception) {
                    Timber.w("[SYNC-DEBUG] Failed to get sample workouts: ${e.message}")
                }
            }

            // Get unsynced workouts for this specific user
            val unsyncedWorkouts = workoutDao.getUnsyncedWorkoutsForUser(userId)
            
            if (unsyncedWorkouts.isEmpty()) {
                val syncEndTime = System.currentTimeMillis()
                Timber.d("[SYNC-CONFLICT] WorkoutSync completed (no workouts) for user $userId in ${syncEndTime - syncStartTime}ms")
                Timber.d("[SYNC-DEBUG] No unsynced workouts found for user $userId")
                
                // 🔍 Additional debugging when no unsynced workouts found
                if (totalWorkoutCount > 0) {
                    Timber.w("[SYNC-DEBUG] WARNING: User has $totalWorkoutCount total workouts but 0 unsynced - all workouts already synced?")
                } else {
                    Timber.w("[SYNC-DEBUG] WARNING: User has no workouts at all in database")
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
                            val remoteWorkout = remoteDoc.toObject(WorkoutFirestoreDto::class.java)
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
                            val exercisesList = try {
                                if (workout.exercisesJson.isNullOrBlank()) {
                                    emptyList<ExerciseDto>()
                                } else {
                                    // Handle both wrapped and unwrapped formats
                                    val json = Json { ignoreUnknownKeys = true }
                                    try {
                                        val wrapper = json.decodeFromString<LegacyExerciseWrapper>(workout.exercisesJson)
                                        wrapper.exercises
                                    } catch (e: Exception) {
                                        json.decodeFromString<List<ExerciseDto>>(workout.exercisesJson)
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to parse exercises JSON for workout ${workout.id}, using empty list")
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
                    firestoreBatch.commit().await()
                    
                    // Mark all workouts in this batch as synced
                    batch.forEach { workout ->
                        workoutDao.updateSyncStatusForUser(
                            id = workout.id,
                            userId = userId,
                            isSynced = true,
                            version = System.currentTimeMillis()
                        )
                    }
                    successCount += batch.size
                    Timber.d("Successfully synced batch of ${batch.size} workouts")
                    
                } catch (e: Exception) {
                    Timber.e(e, "Batch sync failed for ${batch.size} workouts")
                    failureCount += batch.size
                }
            }
            
            val syncEndTime = System.currentTimeMillis()
            val syncDuration = syncEndTime - syncStartTime
            
            val finalAuthUser = auth.currentUser
            val finalAuthDisplayName = finalAuthUser?.displayName
            
            if (finalAuthUser?.uid != userId) {
                Timber.e("[SYNC-CONFLICT] Auth changed during workout sync: userId=$userId vs finalAuth=${finalAuthUser?.uid}")
            } else if (finalAuthDisplayName != authDisplayName) {
                Timber.w("[SYNC-CONFLICT] Display name changed during workout sync: '$authDisplayName' -> '$finalAuthDisplayName'")
            }
            
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

    // WorkoutFirestoreDto moved to proper data class file:
    // app/src/main/java/com/example/liftrix/data/remote/dto/WorkoutFirestoreDto.kt
    // This resolves Firestore field mapping warnings for isSynced and other fields
} 