package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.WorkoutMapper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@HiltWorker
class WorkoutSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val workoutDao: WorkoutDao,
    private val workoutMapper: WorkoutMapper,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "workout_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        private const val FIRESTORE_COLLECTION = "workouts"
    }

    override suspend fun doWork(): Result {
        return try {
            val currentUserId = getCurrentUserId()
                ?: return Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "User not authenticated")
                        .build()
                )

            val unsyncedWorkouts = workoutDao.getUnsyncedWorkouts()
            
            if (unsyncedWorkouts.isEmpty()) {
                Timber.d("No unsynced workouts found")
                return Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            Timber.d("Found ${unsyncedWorkouts.size} unsynced workouts")
            
            val syncedWorkoutIds = mutableListOf<String>()
            var hasError = false
            var lastError: Exception? = null

            for (workoutEntity in unsyncedWorkouts) {
                try {
                    val workout = workoutMapper.toDomain(workoutEntity)
                    val workoutDto = workoutMapper.toFirestoreDto(workout, currentUserId)
                    
                    // Upload to Firestore
                    val documentRef = firestore
                        .collection(FIRESTORE_COLLECTION)
                        .document(workoutEntity.id)
                    
                    documentRef.set(workoutDto).await()
                    
                    // Mark as synced in local database
                    workoutDao.updateSyncStatus(
                        id = workoutEntity.id,
                        isSynced = true,
                        version = System.currentTimeMillis()
                    )
                    
                    syncedWorkoutIds.add(workoutEntity.id)
                    Timber.d("Successfully synced workout: ${workoutEntity.id}")
                    
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync workout: ${workoutEntity.id}")
                    hasError = true
                    lastError = e
                    
                    // Continue trying to sync other workouts
                    continue
                }
            }


            val totalSynced = syncedWorkoutIds.size
            val totalUnsyncedAttempted = unsyncedWorkouts.size

            return when {
                totalSynced > 0 && !hasError -> {
                    // All workouts synced successfully
                    Result.success(
                        Data.Builder()
                            .putInt(KEY_SYNC_COUNT, syncedWorkoutIds.size)
                            .build()
                    )
                }
                totalSynced > 0 && hasError -> {
                    // Some workouts synced, some failed - partial success
                    val failedCount = totalUnsyncedAttempted - totalSynced
                    Timber.w("Partial sync completed. Synced: $totalSynced, Failed: $failedCount")
                    Result.success(
                        Data.Builder()
                            .putInt(KEY_SYNC_COUNT, syncedWorkoutIds.size)
                            .putString(KEY_ERROR_MESSAGE, "Partial sync: ${lastError?.message}")
                            .build()
                    )
                }
                else -> {
                    // All workouts failed to sync
                    val errorMessage = lastError?.message ?: "Unknown sync error"
                    Timber.e("All workouts failed to sync: $errorMessage")
                    
                    if (runAttemptCount < MAX_RETRY_COUNT) {
                        Result.retry()
                    } else {
                        Result.failure(
                            Data.Builder()
                                .putString(KEY_ERROR_MESSAGE, errorMessage)
                                .putInt(KEY_SYNC_COUNT, 0)
                                .build()
                        )
                    }
                }
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

    private fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
} 