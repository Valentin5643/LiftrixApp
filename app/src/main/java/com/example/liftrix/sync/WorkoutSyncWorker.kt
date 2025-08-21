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
            
            Timber.d("WorkoutSyncWorker: Authentication validated for user $userId")

            // Get unsynced workouts for this specific user
            val unsyncedWorkouts = workoutDao.getUnsyncedWorkoutsForUser(userId)
            
            if (unsyncedWorkouts.isEmpty()) {
                Timber.d("No unsynced workouts found for user $userId")
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
                            // Handle conflict resolution
                            val remoteWorkout = remoteDoc.toObject(WorkoutFirestoreDto::class.java)
                            if (remoteWorkout != null) {
                                // Simple last-write-wins for now - can be enhanced with ConflictResolver
                                if (workout.updatedAt.epochSecond > (remoteWorkout.updatedAt ?: 0)) {
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
                            // Parse exercises JSON to list for security rule validation
                            val exercisesList = try {
                                if (workout.exercisesJson.isNullOrBlank()) {
                                    emptyList<ExerciseDto>()
                                } else {
                                    // Parse JSON string to list of properly typed DTOs
                                    Json { ignoreUnknownKeys = true }.decodeFromString<List<ExerciseDto>>(workout.exercisesJson)
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to parse exercises JSON for workout ${workout.id}, using empty list")
                                emptyList<ExerciseDto>()
                            }
                            
                            // Convert ExerciseDto to Map for Firestore
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
                                // Required sync metadata fields for security rules
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

    // Helper data class for Firestore mapping with proper serialization
    @Serializable
    private data class WorkoutFirestoreDto(
        val id: String = "",
        val userId: String = "",
        val name: String = "",
        val updatedAt: Long? = null,
        val status: String = "",
        val exercises: List<ExerciseDto> = emptyList()
    )
} 