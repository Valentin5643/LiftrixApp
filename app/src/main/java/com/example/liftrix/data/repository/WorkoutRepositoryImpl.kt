package com.example.liftrix.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.sync.WorkoutSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val workoutMapper: WorkoutMapper,
    private val workManager: WorkManager
) : WorkoutRepository {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }

    // User-scoped methods implementation
    override fun getAllWorkoutsForUser(userId: String): Flow<List<Workout>> {
        return workoutDao.getAllWorkoutsForUser(userId).map { entities ->
            entities.map { workoutMapper.toDomain(it) }
        }
    }

    override fun getAllWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWorkouts().map { entities ->
            entities.map { workoutMapper.toDomain(it) }
        }
    }

    override suspend fun getWorkoutByIdForUser(id: WorkoutId, userId: String): Workout? {
        return try {
            workoutDao.getWorkoutByIdForUser(id.value, userId)?.let { entity ->
                workoutMapper.toDomain(entity)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get workout by ID for user: ${id.value}, user: $userId")
            null
        }
    }

    override suspend fun getWorkoutById(id: WorkoutId): Workout? {
        return try {
            workoutDao.getWorkoutById(id.value)?.let { entity ->
                workoutMapper.toDomain(entity)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get workout by ID: ${id.value}")
            null
        }
    }

    override fun getWorkoutsByDateForUser(date: LocalDate, userId: String): Flow<List<Workout>> {
        val dateString = date.format(DATE_FORMATTER)
        return workoutDao.getWorkoutsByDateForUser(dateString, userId).map { entities ->
            entities.map { workoutMapper.toDomain(it) }
        }
    }

    override fun getWorkoutsByDate(date: LocalDate): Flow<List<Workout>> {
        val dateString = date.format(DATE_FORMATTER)
        return workoutDao.getWorkoutsByDate(dateString).map { entities ->
            entities.map { workoutMapper.toDomain(it) }
        }
    }

    override suspend fun getActiveWorkoutForUser(userId: String): Workout? {
        return try {
            workoutDao.getActiveWorkoutForUser(userId)?.let { entity ->
                workoutMapper.toDomain(entity)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get active workout for user: $userId")
            null
        }
    }

    override suspend fun getUnsyncedCountForUser(userId: String): Int {
        return try {
            workoutDao.getUnsyncedCountForUser(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unsynced count for user: $userId")
            0
        }
    }

    override suspend fun getUnsyncedWorkoutsForUser(userId: String): List<Workout> {
        return try {
            workoutDao.getUnsyncedWorkoutsForUser(userId).map { entity ->
                workoutMapper.toDomain(entity)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unsynced workouts for user: $userId")
            emptyList()
        }
    }

    override suspend fun saveWorkout(workout: Workout): Result<Unit> {
        return try {
            // Validate workout has userId
            require(workout.userId.isNotBlank()) { "Workout must have a valid user ID" }
            
            // Save to local database first (offline-first approach)
            val entity = workoutMapper.toEntity(workout, isSynced = false)
            workoutDao.insertWorkout(entity)
            
            // Queue sync in background for this user
            queueSyncForUser(workout.userId)
            
            Timber.d("Workout saved successfully: ${workout.id.value} for user: ${workout.userId}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to save workout: ${workout.id.value}")
            Result.failure(e)
        }
    }

    override suspend fun updateWorkout(workout: Workout): Result<Unit> {
        return try {
            // Validate workout has userId
            require(workout.userId.isNotBlank()) { "Workout must have a valid user ID" }
            
            // Update in local database
            val entity = workoutMapper.toEntity(workout, isSynced = false)
            workoutDao.updateWorkout(entity)
            
            // Queue sync in background for this user
            queueSyncForUser(workout.userId)
            
            Timber.d("Workout updated successfully: ${workout.id.value} for user: ${workout.userId}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update workout: ${workout.id.value}")
            Result.failure(e)
        }
    }

    override suspend fun deleteWorkoutForUser(workoutId: WorkoutId, userId: String): Result<Unit> {
        return try {
            workoutDao.deleteWorkoutByIdForUser(workoutId.value, userId)
            
            Timber.d("Workout deleted successfully: ${workoutId.value} for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete workout: ${workoutId.value} for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun deleteAllWorkoutsForUser(userId: String): Result<Unit> {
        return try {
            workoutDao.deleteAllWorkoutsForUser(userId)
            
            // Cancel any pending sync work for this user
            workManager.cancelUniqueWork("${WorkoutSyncWorker.WORK_NAME}_$userId")
            
            Timber.d("All workouts cleared successfully for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear all workouts for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun deleteWorkout(workoutId: WorkoutId): Result<Unit> {
        return try {
            workoutDao.deleteWorkoutById(workoutId.value)
            
            Timber.d("Workout deleted successfully: ${workoutId.value}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete workout: ${workoutId.value}")
            Result.failure(e)
        }
    }

    override suspend fun queueSyncForUser(userId: String): Result<Unit> {
        return try {
            val unsyncedCount = workoutDao.getUnsyncedCountForUser(userId)
            
            if (unsyncedCount > 0) {
                val syncWorkRequest = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putString("userId", userId)
                            .build()
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    "${WorkoutSyncWorker.WORK_NAME}_$userId",
                    ExistingWorkPolicy.REPLACE,
                    syncWorkRequest
                )
                
                Timber.d("Queued sync for $unsyncedCount unsynced workouts for user: $userId")
            } else {
                Timber.d("No unsynced workouts to queue for user: $userId")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue sync for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun syncNowForUser(userId: String): Result<Unit> {
        return try {
            val unsyncedCount = workoutDao.getUnsyncedCountForUser(userId)
            
            if (unsyncedCount > 0) {
                val immediateSync = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putString("userId", userId)
                            .build()
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    "${WorkoutSyncWorker.WORK_NAME}_immediate_$userId",
                    ExistingWorkPolicy.REPLACE,
                    immediateSync
                )
                
                Timber.d("Initiated immediate sync for $unsyncedCount workouts for user: $userId")
            } else {
                Timber.d("No unsynced workouts for immediate sync for user: $userId")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initiate immediate sync for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun markWorkoutsAsSyncedForUser(workoutIds: List<String>, userId: String): Result<Unit> {
        return try {
            workoutDao.markWorkoutsAsSyncedForUser(workoutIds, userId, System.currentTimeMillis())
            Timber.d("Marked ${workoutIds.size} workouts as synced for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark workouts as synced for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun queueSync(): Result<Unit> {
        return try {
            val unsyncedCount = workoutDao.getUnsyncedCount()
            
            if (unsyncedCount > 0) {
                val syncWorkRequest = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    WorkoutSyncWorker.WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    syncWorkRequest
                )
                
                Timber.d("Queued sync for $unsyncedCount unsynced workouts")
            } else {
                Timber.d("No unsynced workouts to queue")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue sync")
            Result.failure(e)
        }
    }

    override suspend fun getUnsyncedCount(): Int {
        return try {
            workoutDao.getUnsyncedCount()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unsynced count")
            0
        }
    }

    override suspend fun syncNow(): Result<Unit> {
        return try {
            val unsyncedCount = workoutDao.getUnsyncedCount()
            
            if (unsyncedCount > 0) {
                val immediateSync = OneTimeWorkRequestBuilder<WorkoutSyncWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    "${WorkoutSyncWorker.WORK_NAME}_immediate",
                    ExistingWorkPolicy.REPLACE,
                    immediateSync
                )
                
                Timber.d("Initiated immediate sync for $unsyncedCount workouts")
            } else {
                Timber.d("No unsynced workouts for immediate sync")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initiate immediate sync")
            Result.failure(e)
        }
    }

    override suspend fun clearAllWorkouts(): Result<Unit> {
        return try {
            workoutDao.deleteAllWorkouts()
            
            // Cancel any pending sync work
            workManager.cancelUniqueWork(WorkoutSyncWorker.WORK_NAME)
            
            Timber.d("All workouts cleared successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear all workouts")
            Result.failure(e)
        }
    }

    /**
     * Internal method to handle sync retries and failures
     */
    internal suspend fun handleSyncResult(workoutIds: List<String>, userId: String, success: Boolean) {
        try {
            if (success) {
                workoutDao.markWorkoutsAsSyncedForUser(workoutIds, userId, System.currentTimeMillis())
                Timber.d("Marked ${workoutIds.size} workouts as synced for user: $userId")
            } else {
                Timber.w("Sync failed for ${workoutIds.size} workouts for user: $userId")
                // Could implement exponential backoff or other retry strategies here
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle sync result for user: $userId")
        }
    }
} 