package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.service.sync.ConflictResolver
import com.example.liftrix.service.sync.ResolutionStrategy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class WorkoutSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val workoutDao: WorkoutDao,
    private val workoutMapper: WorkoutMapper,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val conflictResolver: ConflictResolver
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "workout_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        private const val BATCH_SIZE = 20
        
        fun createWorkRequest(userId: String): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                .setInputData(workDataOf("userId" to userId))
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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = inputData.getString("userId") ?: return@withContext Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, "User ID not provided")
                    .build()
            )

            // Get unsynced workouts for this specific user
            val unsyncedWorkouts = workoutDao.getUnsyncedWorkoutsForUser(userId)
            
            if (unsyncedWorkouts.isEmpty()) {
                Timber.d("No unsynced workouts found for user $userId")
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            Timber.d("Found ${unsyncedWorkouts.size} unsynced workouts for user $userId")
            
            // Batch process for efficiency
            val batches = unsyncedWorkouts.chunked(BATCH_SIZE)
            var successCount = 0
            var failureCount = 0
            
            batches.forEach { batch ->
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
                            val firestoreData = mapOf(
                                "id" to workout.id,
                                "userId" to userId,
                                "name" to workout.name,
                                "date" to workout.date.toString(),
                                "status" to workout.status.name,
                                "startTime" to workout.startTime?.epochSecond,
                                "endTime" to workout.endTime?.epochSecond,
                                "exercises" to workout.exercisesJson,
                                "notes" to workout.notes,
                                "templateId" to workout.templateId,
                                "createdAt" to workout.createdAt.epochSecond,
                                "syncVersion" to System.currentTimeMillis(),
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
            
            return@withContext if (failureCount > 0 && successCount == 0) {
                // All failed
                if (runAttemptCount < MAX_RETRY_COUNT) {
                    Result.retry()
                } else {
                    Result.failure(
                        Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, "All workouts failed to sync")
                            .putInt(KEY_SYNC_COUNT, successCount)
                            .build()
                    )
                }
            } else {
                // Some or all succeeded
                Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, successCount)
                        .putString(KEY_ERROR_MESSAGE, if (failureCount > 0) "Partial sync completed" else null)
                        .build()
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "WorkoutSyncWorker failed with exception")
            
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, e.message ?: "Unknown error")
                        .build()
                )
            }
        }
    }

    // Helper data class for Firestore mapping
    private data class WorkoutFirestoreDto(
        val id: String = "",
        val userId: String = "",
        val name: String = "",
        val updatedAt: Long? = null
        // Add other fields as needed
    )
} 