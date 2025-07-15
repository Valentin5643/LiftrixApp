package com.example.liftrix.data.repository.workout

import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber
import java.time.LocalDate
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of WorkoutRepository focused on data mapping and persistence only.
 * 
 * Responsibilities:
 * - Data mapping between domain models and entities
 * - Database operations through WorkoutDao
 * - Error handling with LiftrixError hierarchy
 * - User-scoped data access for multi-tenancy
 * 
 * Does NOT contain:
 * - Business logic (delegated to use cases)
 * - Validation logic (handled in domain layer)
 * - Sync operations (handled by dedicated sync repositories)
 */
@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val workoutMapper: WorkoutMapper
) : WorkoutRepository {

    override suspend fun createWorkout(workout: Workout): LiftrixResult<Workout> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to create workout: ${workout.name}",
                    operation = "CREATE",
                    table = "workouts",
                    analyticsContext = mapOf(
                        "workout_name" to workout.name,
                        "user_id" to workout.userId,
                        "exercise_count" to workout.exercises.size.toString()
                    )
                )
            }
        ) {
            val entity = workoutMapper.toEntity(workout, isSynced = false)
            val insertedId = workoutDao.insertWorkout(entity)
            
            if (insertedId > 0) {
                workout.copy(id = WorkoutId(insertedId.toString()))
            } else {
                throw RuntimeException("Workout insert operation returned invalid ID: $insertedId")
            }
        }
    }

    override suspend fun getWorkoutById(id: WorkoutId, userId: String): LiftrixResult<Workout?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve workout by ID",
                    operation = "READ",
                    table = "workouts",
                    analyticsContext = mapOf(
                        "workout_id" to id.value,
                        "user_id" to userId
                    )
                )
            }
        ) {
            val entity = workoutDao.getWorkoutByIdForUser(id.value, userId)
            entity?.let { workoutMapper.toDomain(it) }
        }
    }

    override fun getWorkoutsByUser(userId: String): Flow<LiftrixResult<List<Workout>>> {
        return workoutDao.getAllWorkoutsForUser(userId)
            .map { entities ->
                try {
                    val workouts = entities.map { workoutMapper.toDomain(it) }
                    LiftrixResult.success(workouts)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map workout entities to domain models for user: $userId")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to retrieve workouts for user",
                            operation = "READ",
                            table = "workouts",
                            analyticsContext = mapOf("user_id" to userId)
                        )
                    )
                }
            }
            .catch { throwable ->
                Timber.e(throwable, "Database flow error for user workouts: $userId")
                emit(
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Database connection error while retrieving workouts",
                            operation = "READ",
                            table = "workouts",
                            analyticsContext = mapOf("user_id" to userId)
                        )
                    )
                )
            }
    }

    override fun getWorkoutsByDate(date: LocalDate, userId: String): Flow<LiftrixResult<List<Workout>>> {
        return workoutDao.getWorkoutsByDateForUser(date.toString(), userId)
            .map { entities ->
                try {
                    val workouts = entities.map { workoutMapper.toDomain(it) }
                    LiftrixResult.success(workouts)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map workout entities for date: $date, user: $userId")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to retrieve workouts for date",
                            operation = "READ",
                            table = "workouts",
                            analyticsContext = mapOf(
                                "date" to date.toString(),
                                "user_id" to userId
                            )
                        )
                    )
                }
            }
            .catch { throwable ->
                Timber.e(throwable, "Database flow error for workouts by date: $date, user: $userId")
                emit(
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Database connection error while retrieving workouts by date",
                            operation = "READ",
                            table = "workouts",
                            analyticsContext = mapOf(
                                "date" to date.toString(),
                                "user_id" to userId
                            )
                        )
                    )
                )
            }
    }

    override suspend fun updateWorkout(workout: Workout): LiftrixResult<Workout> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update workout: ${workout.name}",
                    operation = "UPDATE",
                    table = "workouts",
                    analyticsContext = mapOf(
                        "workout_id" to workout.id.value,
                        "workout_name" to workout.name,
                        "user_id" to workout.userId
                    )
                )
            }
        ) {
            val entity = workoutMapper.toEntity(workout, isSynced = false)
            val updatedRows = workoutDao.updateWorkout(entity)
            
            if (updatedRows > 0) {
                workout
            } else {
                throw RuntimeException("Workout update operation affected 0 rows for ID: ${workout.id.value}")
            }
        }
    }

    override suspend fun deleteWorkout(workoutId: WorkoutId, userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to delete workout",
                    operation = "DELETE",
                    table = "workouts",
                    analyticsContext = mapOf(
                        "workout_id" to workoutId.value,
                        "user_id" to userId
                    )
                )
            }
        ) {
            val deletedRows = workoutDao.deleteWorkoutByIdForUser(workoutId.value, userId)
            
            if (deletedRows == 0) {
                throw RuntimeException("No workout found to delete with ID: ${workoutId.value} for user: $userId")
            }
        }
    }

    override suspend fun getActiveWorkout(userId: String): LiftrixResult<Workout?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve active workout",
                    operation = "READ",
                    table = "workouts",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val entity = workoutDao.getActiveWorkoutForUser(userId)
            entity?.let { workoutMapper.toDomain(it) }
        }
    }

    override fun getRecentWorkouts(userId: String, limit: Int): Flow<LiftrixResult<List<Workout>>> {
        return workoutDao.getRecentCompletedWorkouts(userId, limit)
            .map { entities ->
                try {
                    val workouts = entities.map { workoutMapper.toDomain(it) }
                    LiftrixResult.success(workouts)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map recent workout entities for user: $userId, limit: $limit")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to retrieve recent workouts",
                            operation = "READ",
                            table = "workouts",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "limit" to limit.toString()
                            )
                        )
                    )
                }
            }
            .catch { throwable ->
                Timber.e(throwable, "Database flow error for recent workouts: user: $userId, limit: $limit")
                emit(
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Database connection error while retrieving recent workouts",
                            operation = "READ",
                            table = "workouts",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "limit" to limit.toString()
                            )
                        )
                    )
                )
            }
    }

    override suspend fun workoutExists(workoutId: WorkoutId, userId: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check workout existence",
                    operation = "READ",
                    table = "workouts",
                    analyticsContext = mapOf(
                        "workout_id" to workoutId.value,
                        "user_id" to userId
                    )
                )
            }
        ) {
            workoutDao.workoutExistsByIdAndUser(workoutId.value, userId)
        }
    }

    override suspend fun getWorkoutCount(userId: String): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get workout count",
                    operation = "READ",
                    table = "workouts",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            workoutDao.getWorkoutCountForUser(userId)
        }
    }

    override suspend fun deleteAllWorkouts(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to delete all workouts for user",
                    operation = "DELETE",
                    table = "workouts",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val deletedRows = workoutDao.deleteAllWorkoutsForUser(userId)
            Timber.i("Deleted $deletedRows workouts for user: $userId")
        }
    }

    // ============== LEGACY COMPATIBILITY METHODS IMPLEMENTATION ==============

    override suspend fun saveWorkout(workout: Workout): Result<Unit> {
        return try {
            val result = if (workout.id.value.isBlank()) {
                createWorkout(workout)
            } else {
                updateWorkout(workout)
            }
            
            result.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { throwable -> Result.failure(throwable) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserWorkoutHistory(userId: String, limit: Int, offset: Int): LiftrixResult<List<WorkoutSummary>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get workout history",
                    operation = "READ",
                    table = "workouts",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "limit" to limit.toString(),
                        "offset" to offset.toString()
                    )
                )
            }
        ) {
            // Simplified implementation - return empty list for now
            // This should be implemented based on your actual WorkoutSummary mapping logic
            emptyList<WorkoutSummary>()
        }
    }

    override suspend fun getWorkoutHistoryCount(userId: String): LiftrixResult<Int> {
        return getWorkoutCount(userId) // Delegate to existing method
    }

    override fun getAllWorkoutsForUser(userId: String): Flow<List<Workout>> {
        return getWorkoutsByUser(userId).map { result ->
            result.fold(
                onSuccess = { workouts -> workouts },
                onFailure = { throwable -> 
                    Timber.e("Error getting workouts for user: ${throwable.message}")
                    emptyList<Workout>()
                }
            )
        }
    }

    override suspend fun getUnsyncedCount(userId: String): LiftrixResult<Int> {
        return getUnsyncedCountForUser(userId)
    }

    override suspend fun getUnsyncedCountForUser(userId: String): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get unsynced count",
                    operation = "READ",
                    table = "workouts",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            // Placeholder implementation - return 0 for now
            // This should query for workouts with unsynced flag
            0
        }
    }

    override suspend fun queueSync(workoutId: WorkoutId, userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to queue workout for sync",
                    operation = "UPDATE",
                    table = "workouts",
                    analyticsContext = mapOf(
                        "workout_id" to workoutId.value,
                        "user_id" to userId
                    )
                )
            }
        ) {
            // Placeholder implementation
            // This should mark workout as needing sync
            Timber.d("Queued workout ${workoutId.value} for sync")
        }
    }

    override suspend fun syncNow(userId: String): LiftrixResult<Unit> {
        return syncNowForUser(userId)
    }

    override suspend fun syncNowForUser(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to trigger sync",
                    operation = "SYNC",
                    table = "workouts",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            // Placeholder implementation
            // This should trigger immediate sync
            Timber.d("Triggered sync for user: $userId")
        }
    }

    override suspend fun getFeedWorkouts(userId: String, limit: Int): LiftrixResult<List<FeedWorkout>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get feed workouts",
                    operation = "READ",
                    table = "workouts",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "limit" to limit.toString()
                    )
                )
            }
        ) {
            // Get personal completed workouts from database
            val workoutEntities = workoutDao.getRecentCompletedWorkouts(userId, limit)
                .catch { exception ->
                    Timber.e(exception, "Error getting personal workouts for feed")
                    emit(emptyList())
                }
                .first() // Get current value from Flow
            
            // Map entities to FeedWorkout domain models
            workoutEntities.map { entity ->
                val workout = workoutMapper.toDomain(entity)
                FeedWorkout.forPersonalWorkout(workout)
            }
        }
    }

    override suspend fun getWorkoutStats(userId: String): LiftrixResult<WorkoutStats> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get workout stats",
                    operation = "READ",
                    table = "workouts",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            // Get all completed workouts for stats calculation
            val allWorkouts = workoutDao.getRecentCompletedWorkouts(userId, 1000) // Get a large sample
                .catch { exception ->
                    Timber.e(exception, "Error getting workouts for stats calculation")
                    emit(emptyList())
                }
                .first()
                .map { entity -> workoutMapper.toDomain(entity) }
            
            if (allWorkouts.isEmpty()) {
                return@liftrixCatching WorkoutStats(
                    totalWorkouts = 0,
                    currentStreak = 0,
                    weeklyVolume = Duration.ZERO,
                    averageWorkoutDuration = Duration.ZERO
                )
            }
            
            // Calculate total workouts
            val totalWorkouts = allWorkouts.size
            
            // Calculate current streak (consecutive days with workouts)
            val currentStreak = calculateCurrentStreak(allWorkouts)
            
            // Calculate average workout duration
            val totalDuration = allWorkouts.mapNotNull { workout ->
                workout.getDuration()
            }.fold(Duration.ZERO) { acc, duration -> acc.plus(duration) }
            
            val averageWorkoutDuration = if (totalWorkouts > 0 && totalDuration != Duration.ZERO) {
                totalDuration.dividedBy(totalWorkouts.toLong())
            } else {
                Duration.ZERO
            }
            
            // Calculate weekly volume (workouts in last 7 days)
            val now = LocalDate.now()
            val weekAgo = now.minusDays(7)
            val weeklyWorkoutCount = allWorkouts.count { workout ->
                workout.date.isAfter(weekAgo) || workout.date.isEqual(weekAgo)
            }
            val weeklyVolume = Duration.ofHours(weeklyWorkoutCount.toLong())
            
            WorkoutStats(
                totalWorkouts = totalWorkouts,
                currentStreak = currentStreak,
                weeklyVolume = weeklyVolume,
                averageWorkoutDuration = averageWorkoutDuration
            )
        }
    }
    
    /**
     * Calculates the current workout streak (consecutive days with completed workouts)
     */
    private fun calculateCurrentStreak(workouts: List<Workout>): Int {
        if (workouts.isEmpty()) return 0
        
        // Sort workouts by date (most recent first)
        val sortedWorkouts = workouts.sortedByDescending { it.date }
        val today = LocalDate.now()
        
        // Check if the most recent workout was today or yesterday
        val mostRecentDate = sortedWorkouts.first().date
        if (mostRecentDate.isBefore(today.minusDays(1))) {
            return 0 // Streak broken if last workout was more than 1 day ago
        }
        
        var streak = 0
        var currentDate = today
        
        // Count consecutive days with workouts, working backwards from today
        for (workout in sortedWorkouts) {
            if (workout.date.isEqual(currentDate) || workout.date.isEqual(currentDate.minusDays(1))) {
                streak++
                currentDate = workout.date.minusDays(1)
            } else {
                break
            }
        }
        
        return streak
    }
}