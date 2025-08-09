package com.example.liftrix.data.repository.workout

import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.toSummary
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.workout.ExercisePerformanceData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlinx.datetime.LocalDate as KotlinxLocalDate
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
        // Validate user ID
        if (workout.userId.isBlank()) {
            return LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "userId",
                    violations = listOf("User ID cannot be blank when creating workout"),
                    errorMessage = "User ID is required"
                )
            )
        }
        
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
                // 🔥 CRITICAL FIX: Preserve the original workout ID instead of overwriting with auto-increment
                Timber.d("WORKOUT-DEBUG: Saved ${workout.name} (${workout.id.value}) for user: ${workout.userId}")
                
                // Verify the workout can be retrieved immediately
                val verifyEntity = workoutDao.getWorkoutByIdForUser(workout.id.value, workout.userId)
                if (verifyEntity == null) {
                    Timber.d("WORKOUT-DEBUG: CRITICAL - Workout not found immediately after insert!")
                }
                
                workout // Return original workout with preserved ID
            } else {
                throw RuntimeException("Workout insert operation returned invalid ID: $insertedId")
            }
        }
    }

    override suspend fun getWorkoutById(id: WorkoutId, userId: String): LiftrixResult<Workout?> {
        // Validate user ID
        if (userId.isBlank()) {
            return LiftrixResult.failure(
                LiftrixError.ValidationError(
                    field = "userId",
                    violations = listOf("User ID cannot be blank when retrieving workout"),
                    errorMessage = "User ID is required"
                )
            )
        }
        
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

    override fun getWorkoutsByDate(date: KotlinxLocalDate, userId: String): Flow<LiftrixResult<List<Workout>>> {
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
            Timber.d("🔥 UPDATE-WORKOUT-DEBUG: Attempting to update workout - ID: ${workout.id.value}, Name: ${workout.name}, Status: ${workout.status}")
            
            // First, check if workout exists in database
            val existsResult = workoutExists(workout.id, workout.userId)
            val workoutExists = existsResult.fold(
                onSuccess = { exists -> exists },
                onFailure = { 
                    Timber.w("🔥 UPDATE-WORKOUT-DEBUG: Failed to check workout existence, proceeding with update attempt")
                    true // Assume exists to proceed with update
                }
            )
            
            if (!workoutExists) {
                Timber.w("🔥 UPDATE-WORKOUT-DEBUG: Workout ${workout.id.value} does not exist in database, falling back to create")
                // Fallback to create if workout doesn't exist
                return@liftrixCatching createWorkout(workout).fold(
                    onSuccess = { createdWorkout ->
                        Timber.i("🔥 UPDATE-WORKOUT-DEBUG: Successfully created workout as fallback - ID: ${createdWorkout.id.value}")
                        createdWorkout
                    },
                    onFailure = { error ->
                        Timber.e("🔥 UPDATE-WORKOUT-DEBUG: Fallback create also failed")
                        throw RuntimeException("Workout update failed: not found in database, and fallback create failed: ${error.message}")
                    }
                )
            }
            
            val entity = workoutMapper.toEntity(workout, isSynced = false)
            Timber.d("🔥 UPDATE-WORKOUT-DEBUG: Updating entity with ID: ${entity.id}, Status: ${entity.status}")
            
            val updatedRows = workoutDao.updateWorkout(entity)
            
            if (updatedRows > 0) {
                Timber.i("🔥 UPDATE-WORKOUT-DEBUG: Successfully updated workout - ID: ${workout.id.value}, Rows affected: $updatedRows")
                workout
            } else {
                Timber.e("🔥 UPDATE-WORKOUT-DEBUG: Update affected 0 rows for workout ID: ${workout.id.value}")
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
        // Check total workout count on startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val totalCount = workoutDao.getWorkoutCountForUser(userId)
                Timber.d("WORKOUT-DEBUG: Total workouts for user: $totalCount")
                
                if (totalCount > 0) {
                    val allWorkoutsFlow = workoutDao.getAllWorkoutsForUser(userId)
                    allWorkoutsFlow.first().let { allEntities ->
                        allEntities.forEach { entity ->
                            Timber.d("WORKOUT-DEBUG: Found entity - ID: ${entity.id.take(8)}..., Status: ${entity.status}")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.d("WORKOUT-DEBUG: Error during verification - ${e.message}")
            }
        }
        
        return workoutDao.getRecentCompletedWorkouts(userId, limit)
            .map { entities ->
                try {
                    Timber.d("Found ${entities.size} recent completed workouts")
                    
                    val workouts = entities.map { entity ->
                        workoutMapper.toDomain(entity).also { workout ->
                            Timber.d("🔥 RECENT-WORKOUTS-DEBUG: Mapped workout - id: ${workout.id.value}, name: ${workout.name}, status: ${workout.status}")
                        }
                    }
                    
                    Timber.d("🔥 RECENT-WORKOUTS-DEBUG: Successfully mapped ${workouts.size} workouts")
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
            // Check if workout already exists in database
            val existingWorkout = getWorkoutById(workout.id, workout.userId).getOrNull()
            
            val result = if (workout.id.value.isBlank() || existingWorkout == null) {
                // Create new workout if ID is blank OR workout doesn't exist
                createWorkout(workout)
            } else {
                // Only update if workout actually exists
                updateWorkoutWithRetry(workout)
            }
            
            result.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { throwable -> Result.failure(throwable) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update workout with automatic retry logic for recoverable database errors
     */
    private suspend fun updateWorkoutWithRetry(
        workout: Workout,
        maxAttempts: Int = 3,
        baseDelayMs: Long = 1000L
    ): LiftrixResult<Workout> {
        var lastError: LiftrixError? = null
        
        repeat(maxAttempts) { attempt ->
            Timber.d("🔥 RETRY-DEBUG: Attempt ${attempt + 1}/$maxAttempts for workout ${workout.id.value}")
            
            val result = updateWorkout(workout)
            
            result.fold(
                onSuccess = { updatedWorkout ->
                    Timber.i("🔥 RETRY-DEBUG: Update succeeded on attempt ${attempt + 1}")
                    return LiftrixResult.success(updatedWorkout)
                },
                onFailure = { error ->
                    lastError = error as? LiftrixError
                    
                    if (error is LiftrixError.DatabaseError && error.isRecoverable && attempt < maxAttempts - 1) {
                        val delayMs = error.retryAfter ?: (baseDelayMs * (1L shl attempt)) // Exponential backoff
                        Timber.w("🔥 RETRY-DEBUG: Attempt ${attempt + 1} failed, retrying in ${delayMs}ms. Error: ${error.message}")
                        
                        kotlinx.coroutines.delay(delayMs)
                    } else {
                        Timber.e("🔥 RETRY-DEBUG: Attempt ${attempt + 1} failed, not retrying. Error: ${error.message}")
                        return LiftrixResult.failure(error)
                    }
                }
            )
        }
        
        return LiftrixResult.failure(lastError ?: LiftrixError.UnknownError("Update failed after $maxAttempts attempts"))
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
            // Get paginated workout history from database
            val workoutEntities = workoutDao.getWorkoutHistoryPaginated(userId, limit, offset)
                .catch { exception ->
                    Timber.e(exception, "Error getting workout history for user: $userId")
                    emit(emptyList())
                }
                .first() // Get current value from Flow
            
            // Convert entities to domain models then to WorkoutSummary
            val workoutSummaries = workoutEntities.map { entity ->
                val workout = workoutMapper.toDomain(entity)
                workout.toSummary()
            }
            
            Timber.d("Retrieved ${workoutSummaries.size} workout summaries for user: $userId (limit: $limit, offset: $offset)")
            workoutSummaries
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
            // Query for workouts with unsynced flag
            val unsyncedCount = workoutDao.getUnsyncedCountForUser(userId)
            Timber.d("Unsynced workout count for user $userId: $unsyncedCount")
            unsyncedCount
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
            Timber.d("🔥 FEED-WORKOUTS-DEBUG: Getting feed workouts (legacy method) for user: $userId, limit: $limit")
            
            // Get personal completed workouts from database
            val workoutEntities = workoutDao.getRecentCompletedWorkouts(userId, limit)
                .catch { exception ->
                    Timber.e(exception, "Error getting personal workouts for feed")
                    emit(emptyList())
                }
                .first() // Get current value from Flow
            
            Timber.d("🔥 FEED-WORKOUTS-DEBUG: Found ${workoutEntities.size} completed workout entities")
            workoutEntities.forEachIndexed { index, entity ->
                Timber.d("🔥 FEED-WORKOUTS-DEBUG: Entity[$index] - id: ${entity.id}, name: ${entity.name}, status: ${entity.status}")
            }
            
            // Map entities to FeedWorkout domain models
            val feedWorkouts = workoutEntities.map { entity ->
                val workout = workoutMapper.toDomain(entity)
                FeedWorkout.forPersonalWorkout(workout)
            }
            
            Timber.d("🔥 FEED-WORKOUTS-DEBUG: Successfully mapped ${feedWorkouts.size} feed workouts")
            feedWorkouts
        }
    }

    override fun getFeedWorkoutsReactive(userId: String, limit: Int): Flow<LiftrixResult<List<FeedWorkout>>> {
        Timber.d("🔥 FEED-WORKOUTS-DEBUG: Setting up reactive feed workouts for user: $userId, limit: $limit")
        
        return workoutDao.getRecentCompletedWorkouts(userId, limit)
            .map { entities ->
                try {
                    Timber.d("🔥 FEED-WORKOUTS-DEBUG: Received ${entities.size} completed workout entities from reactive flow")
                    entities.forEachIndexed { index, entity ->
                        Timber.d("🔥 FEED-WORKOUTS-DEBUG: Entity[$index] - id: ${entity.id}, name: ${entity.name}, status: ${entity.status}")
                    }
                    
                    // Map entities to FeedWorkout domain models
                    val feedWorkouts = entities.map { entity ->
                        val workout = workoutMapper.toDomain(entity)
                        FeedWorkout.forPersonalWorkout(workout)
                    }
                    
                    Timber.d("🔥 FEED-WORKOUTS-DEBUG: Successfully mapped ${feedWorkouts.size} feed workouts")
                    LiftrixResult.success(feedWorkouts)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "🔥 FEED-WORKOUTS-DEBUG: Error mapping workout entities to feed workouts")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to map feed workouts",
                            operation = "READ",
                            table = "workouts",
                            analyticsContext = mapOf(
                                "user_id" to userId,
                                "limit" to limit.toString(),
                                "entity_count" to entities.size.toString()
                            )
                        )
                    )
                }
            }
            .catch { throwable ->
                Timber.e(throwable, "🔥 FEED-WORKOUTS-DEBUG: Database flow error for feed workouts")
                emit(
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Database connection error while retrieving feed workouts",
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
    
    override suspend fun getExercisePerformanceData(
        userId: String,
        startDate: KotlinxLocalDate,
        endDate: KotlinxLocalDate
    ): LiftrixResult<List<ExercisePerformanceData>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get exercise performance data",
                    operation = "READ",
                    table = "workouts",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "start_date" to startDate.toString(),
                        "end_date" to endDate.toString()
                    )
                )
            }
        ) {
            // Placeholder implementation
            // In a real implementation, this would query the database for exercise data
            // and aggregate it by exercise across the specified date range
            Timber.d("Getting exercise performance data for user $userId from $startDate to $endDate")
            emptyList<ExercisePerformanceData>()
        }
    }
}