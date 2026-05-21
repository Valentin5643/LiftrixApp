package com.example.liftrix.data.repository.workout

import android.util.Log
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.data.mapper.ExerciseMapper
import com.example.liftrix.data.sync.OfflineQueueManager
import com.example.liftrix.data.model.SyncPayload
import com.example.liftrix.data.model.SyncPayloadFactory
import com.example.liftrix.data.model.WorkoutSyncDto
import com.example.liftrix.data.model.ExerciseDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.example.liftrix.domain.sync.SyncScheduler
import com.example.liftrix.data.service.KotlinxWorkoutSerializationService
import com.example.liftrix.data.service.CanonicalWorkoutJsonAdapter
import com.example.liftrix.data.service.ExerciseConsistencyValidator
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.toSummary
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.PreviousSetRepository
import com.example.liftrix.domain.repository.workout.WorkoutAnalyticsDataRepository
import com.example.liftrix.domain.repository.workout.WorkoutFeedDataRepository
import com.example.liftrix.domain.repository.workout.WorkoutHistoryRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.workout.WorkoutSyncStatusRepository
import com.example.liftrix.domain.repository.workout.ExercisePerformanceData
import com.example.liftrix.domain.usecase.analytics.PerformanceDataPoint
import com.example.liftrix.domain.usecase.analytics.WorkoutData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import timber.log.Timber
import com.example.liftrix.core.data.BuildConfig
import kotlinx.datetime.LocalDate as KotlinxLocalDate
import java.time.LocalDate
import java.time.Duration
import java.time.Instant
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
    private val workoutPostDao: WorkoutPostDao,
    private val followRelationshipDao: FollowRelationshipDao,
    private val exerciseDao: ExerciseDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val workoutMapper: WorkoutMapper,
    private val workoutPostMapper: WorkoutPostMapper,
    private val exerciseMapper: ExerciseMapper,
    private val syncScheduler: SyncScheduler,
    private val offlineQueueManager: OfflineQueueManager,
    private val canonicalJsonAdapter: CanonicalWorkoutJsonAdapter,
    private val kotlinxSerializer: KotlinxWorkoutSerializationService,
    private val exerciseConsistencyValidator: ExerciseConsistencyValidator,
    private val applicationScope: CoroutineScope
) : WorkoutRepository,
    WorkoutHistoryRepository,
    WorkoutAnalyticsDataRepository,
    WorkoutSyncStatusRepository,
    WorkoutFeedDataRepository,
    PreviousSetRepository {
    
    override suspend fun createWorkout(workout: Workout): LiftrixResult<Workout> {
        Timber.d("[WORKOUT-DEBUG] createWorkout requested id=${workout.id.value} userId=${workout.userId} name='${workout.name}' status=${workout.status} exercises=${workout.exercises.size}")
        val operationTimestamp = System.currentTimeMillis()
        val beforeCount = runCatching { workoutDao.getWorkoutCountForUser(workout.userId) }.getOrDefault(-1)
        Timber.tag("WorkoutSyncDebug").d(
            "[DATABASE-DEBUG] operation=REPOSITORY_CREATE_REQUEST source=Room userId=${workout.userId} workoutId=${workout.id.value} timestamp=$operationTimestamp beforeCount=$beforeCount status=${workout.status} exerciseCount=${workout.exercises.size}"
        )
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
        
        // 🔥 SETS-DEBUG: Enhanced debug logging for workout creation
        if (BuildConfig.DEBUG) {
            Timber.d("[SETS-DEBUG-CREATE] Creating workout '${workout.name}' with ${workout.exercises.size} exercises")
            workout.exercises.forEachIndexed { index, exercise ->
                Timber.d("[SETS-DEBUG-CREATE] Exercise $index: '${exercise.libraryExercise.name}' - targetSets=${exercise.targetSets}, actualSets=${exercise.sets.size}")
                if (exercise.sets.isEmpty() && (exercise.targetSets ?: 0) > 0) {
                    Timber.w("[SETS-DEBUG-CREATE] ⚠️ WARNING: Exercise '${exercise.libraryExercise.name}' has targetSets=${exercise.targetSets} but NO ACTUAL SETS!")
                    Timber.w("[SETS-DEBUG-CREATE] This will save as 'sets':[] and cause 0 volume calculations")
                }
                exercise.sets.forEach { set ->
                    Timber.d("[SETS-DEBUG-CREATE] Set ${set.setNumber}: reps=${set.reps?.count}, weight=${set.weight?.kilograms}, completed=${set.completedAt != null}")
                }
            }
        }
        
        return liftrixCatching(
            errorMapper = { throwable ->
                // 🔥 PRESERVE ACTUAL SQLITE EXCEPTION: Don't overwrite with generic message
                val actualError = throwable.cause ?: throwable
                val errorMessage = when {
                    actualError.message?.contains("constraint", ignoreCase = true) == true -> 
                        "Database constraint violation: ${actualError.message}"
                    actualError.message?.contains("datatype", ignoreCase = true) == true -> 
                        "Database datatype error: ${actualError.message}"
                    throwable.message?.startsWith("Failed to insert workout:") == true || 
                    throwable.message?.startsWith("Failed to update workout:") == true -> 
                        throwable.message!! // Preserve my detailed error message
                    else -> 
                        "Failed to create workout '${workout.name}': ${actualError.message}"
                }
                
                LiftrixError.DatabaseError(
                    errorMessage = errorMessage,
                    operation = "CREATE",
                    table = "workouts",
                    analyticsContext = mapOf(
                        "workout_name" to workout.name,
                        "user_id" to workout.userId,
                        "exercise_count" to workout.exercises.size.toString(),
                        "actual_exception_type" to (actualError::class.simpleName ?: "unknown"),
                        "actual_exception_message" to (actualError.message ?: "unknown")
                    )
                )
            }
        ) {
            // 🔥 SETS-FIX: Preserve original data integrity - don't create fake placeholder sets
            // The root issue is saving workouts with targetSets but no actual completed sets
            // This is EXPECTED BEHAVIOR for template workouts or in-progress workouts
            
            // Convert workout to entity for database storage (preserve original data)
            val entity = workoutMapper.toEntity(workout, isSynced = false)
            
            val insertResult = try {
                withContext(Dispatchers.IO) {
                    workoutDao.upsertLocal(entity)
                }
            } catch (e: Exception) {
                throw e
            }
            Timber.d("[WORKOUT-DEBUG] createWorkout storage write result=$insertResult id=${entity.id} userId=${entity.userId} isDirty=${entity.isDirty} isSynced=${entity.isSynced}")
            val afterCount = workoutDao.getWorkoutCountForUser(workout.userId)
            Timber.tag("WorkoutSyncDebug").d(
                "[DATABASE-DEBUG] operation=REPOSITORY_CREATE_WRITTEN source=Room userId=${workout.userId} workoutId=${workout.id.value} timestamp=${System.currentTimeMillis()} beforeCount=$beforeCount afterCount=$afterCount result=$insertResult isDirty=true isSynced=false"
            )
            
            if (insertResult > 0) {
                // Create ExerciseEntity and ExerciseSetEntity records for analytics queries
                workout.exercises.forEachIndexed { exerciseIndex, exercise ->
                    val exerciseWithCorrectWorkoutId = exercise.copy(
                        workoutId = workout.id,
                        orderIndex = exerciseIndex
                    )
                    
                    val exerciseEntity = exerciseMapper.toEntity(
                        exerciseWithCorrectWorkoutId,
                        workout.userId
                    )
                    val exerciseId = exerciseDao.insertExercise(exerciseEntity)
                    Log.d(
                        "PREV_SAVE",
                        "Saving exercise: workoutId=${workout.id.value} exerciseRowId=$exerciseId " +
                            "exerciseId=${exercise.libraryExercise.id} name=${exercise.libraryExercise.name}"
                    )
                    
                    exercise.sets.forEachIndexed { setIndex, set ->
                        Log.d(
                            "PREV_SAVE",
                            "Saving set: exerciseId=${exercise.libraryExercise.id} name=${exercise.libraryExercise.name} " +
                                "weight=${set.weight?.kilograms} reps=${set.reps?.count} completedAt=${set.completedAt}"
                        )
                        val setEntity = ExerciseSetEntity(
                            userId = workout.userId,
                            exerciseId = exerciseId,
                            setNumber = setIndex + 1,
                            reps = set.reps?.count,
                            weightKg = set.weight?.kilograms?.toFloat(),
                            timeSeconds = set.time?.seconds?.toInt(),
                            rpe = set.rpe?.value,
                            completedAt = set.completedAt?.toEpochMilli()
                        )
                        
                        val setRowId = exerciseSetDao.insertSet(setEntity)
                        Log.d(
                            "PREV_DB_WRITE",
                            "DB write completed for ${exercise.libraryExercise.id} setRowId=$setRowId " +
                                "weight=${setEntity.weightKg} reps=${setEntity.reps} completedAt=${setEntity.completedAt}"
                        )
                    }
                }
                validateExerciseConsistency(workout, entity.exercisesJson)
                // Queue workout for sync after successful creation (use original workout for sync, not placeholder version)
                queueWorkoutForSync(workout)
                
                workout // Return original workout with preserved ID
            } else {
                throw RuntimeException("Workout insert operation returned invalid result: $insertResult")
            }
        }
    }

    override suspend fun getWorkoutById(id: WorkoutId, userId: String): LiftrixResult<Workout?> {
        Timber.tag("WorkoutSyncDebug").d(
            "[DATABASE-DEBUG] operation=REPOSITORY_READ_BY_ID_REQUEST source=Room userId=$userId workoutId=${id.value} timestamp=${System.currentTimeMillis()}"
        )
        Timber.d("🔥 EDIT-WORKOUT-DEBUG: WorkoutRepository.getWorkoutById starting - workoutId: ${id.value}, userId: $userId")
        
        // Validate user ID
        if (userId.isBlank()) {
            Timber.e("🔥 EDIT-WORKOUT-DEBUG: User ID validation failed - userId is blank")
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
                Timber.e("🔥 EDIT-WORKOUT-DEBUG: Database error occurred - ${throwable.message}")
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
            Timber.d("🔥 EDIT-WORKOUT-DEBUG: Calling workoutDao.getWorkoutByIdForUser - id: ${id.value}, userId: $userId")
            val entity = workoutDao.getWorkoutByIdForUser(id.value, userId)
            
            if (entity != null) {
                Timber.tag("WorkoutSyncDebug").d(
                    "[DATABASE-DEBUG] operation=REPOSITORY_READ_BY_ID_RESULT source=Room userId=$userId workoutId=${id.value} timestamp=${System.currentTimeMillis()} found=true status=${entity.status} isDirty=${entity.isDirty} isSynced=${entity.isSynced} lastModified=${entity.lastModified} endTimePresent=${entity.endTime != null}"
                )
                Timber.d("🔥 EDIT-WORKOUT-DEBUG: Entity found - id: ${entity.id}, name: ${entity.name}, userId: ${entity.userId}, status: ${entity.status}")
                val domainWorkout = workoutMapper.toDomain(entity)
                Timber.d("🔥 EDIT-WORKOUT-DEBUG: Mapped to domain - id: ${domainWorkout.id.value}, name: ${domainWorkout.name}, userId: ${domainWorkout.userId}")
                domainWorkout
            } else {
                Timber.tag("WorkoutSyncDebug").w(
                    "[DATABASE-DEBUG] operation=REPOSITORY_READ_BY_ID_RESULT source=Room userId=$userId workoutId=${id.value} timestamp=${System.currentTimeMillis()} found=false"
                )
                Timber.w("🔥 EDIT-WORKOUT-DEBUG: Entity not found in database - workoutId: ${id.value}, userId: $userId")
                null
            }
        }
    }

    override fun getWorkoutsByUser(userId: String): Flow<LiftrixResult<List<Workout>>> {
        Timber.tag("FreshLoginRestoreDebug").d(
            "operation=ROOM_OBSERVE_WORKOUTS_REGISTERED layer=repository userId=$userId timestamp=${System.currentTimeMillis()}"
        )
        return workoutDao.getAllWorkoutsForUser(userId)
            .map { entities ->
                try {
                    val statusCounts = entities.groupingBy { it.status }.eachCount()
                    val completedWithoutEndTime = entities.count { it.status.name == "COMPLETED" && it.endTime == null }
                    Timber.tag("FreshLoginRestoreDebug").d(
                        "operation=ROOM_OBSERVE_WORKOUTS_EMIT layer=repository userId=$userId emittedCount=${entities.size} statusCounts=$statusCounts completedWithoutEndTime=$completedWithoutEndTime dirtyCount=${entities.count { it.isDirty }} unsyncedCount=${entities.count { !it.isSynced }} timestamp=${System.currentTimeMillis()}"
                    )
                    Timber.tag("WorkoutSyncDebug").d(
                        "[DATABASE-DEBUG] operation=REPOSITORY_READ_ALL_EMIT source=Room userId=$userId timestamp=${System.currentTimeMillis()} count=${entities.size} statusCounts=$statusCounts completedWithoutEndTime=$completedWithoutEndTime dirtyCount=${entities.count { it.isDirty }} unsyncedCount=${entities.count { !it.isSynced }}"
                    )
                    Timber.d("[WORKOUT-DEBUG] getWorkoutsByUser storage read entities=${entities.size} userId=$userId")
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
        Timber.d("[WORKOUT-DEBUG] updateWorkout requested id=${workout.id.value} userId=${workout.userId} name='${workout.name}' status=${workout.status} exercises=${workout.exercises.size}")
        val operationTimestamp = System.currentTimeMillis()
        val beforeCount = runCatching { workoutDao.getWorkoutCountForUser(workout.userId) }.getOrDefault(-1)
        Timber.tag("WorkoutSyncDebug").d(
            "[DATABASE-DEBUG] operation=REPOSITORY_UPDATE_REQUEST source=Room userId=${workout.userId} workoutId=${workout.id.value} timestamp=$operationTimestamp beforeCount=$beforeCount status=${workout.status} endTimePresent=${workout.endTime != null} exerciseCount=${workout.exercises.size}"
        )
        // 🔥 SETS-DEBUG: Enhanced debug logging for workout update
        if (BuildConfig.DEBUG) {
            Timber.d("[SETS-DEBUG-UPDATE] Updating workout '${workout.name}' with ${workout.exercises.size} exercises")
            workout.exercises.forEachIndexed { index, exercise ->
                Timber.d("[SETS-DEBUG-UPDATE] Exercise $index: '${exercise.libraryExercise.name}' - targetSets=${exercise.targetSets}, actualSets=${exercise.sets.size}")
                if (exercise.sets.isEmpty() && (exercise.targetSets ?: 0) > 0) {
                    Timber.w("[SETS-DEBUG-UPDATE] ⚠️ WARNING: Exercise '${exercise.libraryExercise.name}' has targetSets=${exercise.targetSets} but NO ACTUAL SETS!")
                    Timber.w("[SETS-DEBUG-UPDATE] This will save as 'sets':[] and cause 0 volume calculations")
                }
                exercise.sets.forEach { set ->
                    Timber.d("[SETS-DEBUG-UPDATE] Set ${set.setNumber}: reps=${set.reps?.count}, weight=${set.weight?.kilograms}, completed=${set.completedAt != null}")
                }
            }
        }
        
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
            Timber.d("🔥 UPDATE-WORKOUT-DEBUG: Attempting to update workout - ID: ${workout.id.value}, Name: ${workout.name}, Status: ${workout.status}, UserId: ${workout.userId}")
            
            // 🔥 SETS-FIX: Preserve original data integrity - this is correct behavior
            // Workouts with targetSets but no actual sets are VALID (templates, in-progress, etc.)
            
            // Convert workout to entity for database storage (preserve original data)
            val entity = workoutMapper.toEntity(workout, isSynced = false)
            Timber.d("🔥 UPDATE-WORKOUT-DEBUG: Updating entity with ID: ${entity.id}, Status: ${entity.status}, UserId: ${entity.userId}")
            
            // Use upsertLocal to mark workout dirty and refresh lastModified for sync
            // 🔥 DEBUG: Log before database update
            Timber.d("[WORKOUT-DB-UPDATE] About to update workout entity in database at ${System.currentTimeMillis()}")
            val insertedId = workoutDao.upsertLocal(entity)
            // 🔥 DEBUG: Log after database update
            Timber.d("[WORKOUT-DB-UPDATE] ✅ Database update completed at ${System.currentTimeMillis()}, result: $insertedId")
            
            Timber.d("[WORKOUT-DEBUG] updateWorkout storage write result=$insertedId id=${entity.id} userId=${entity.userId} isDirty=${entity.isDirty} isSynced=${entity.isSynced}")
            val afterCount = workoutDao.getWorkoutCountForUser(workout.userId)
            Timber.tag("WorkoutSyncDebug").d(
                "[DATABASE-DEBUG] operation=REPOSITORY_UPDATE_WRITTEN source=Room userId=${workout.userId} workoutId=${workout.id.value} timestamp=${System.currentTimeMillis()} beforeCount=$beforeCount afterCount=$afterCount result=$insertedId isDirty=true isSynced=false"
            )
            if (insertedId > 0) {
                Timber.i("🔥 UPDATE-WORKOUT-DEBUG: Successfully updated workout - ID: ${workout.id.value}, Insert result: $insertedId")
                
                // 🔥 FIX: Also handle exercises and sets during update 
                // Delete all existing exercises and sets for this workout first, then recreate them
                exerciseDao.deleteExercisesForWorkout(workout.id.value, workout.userId)
                exerciseSetDao.deleteSetsForWorkout(workout.id.value, workout.userId)
                
                workout.exercises.forEachIndexed { exerciseIndex, exercise ->
                    // Create a modified exercise with correct workoutId and orderIndex
                    val exerciseWithCorrectWorkoutId = exercise.copy(
                        workoutId = workout.id,
                        orderIndex = exerciseIndex
                    )
                    val exerciseEntity = try {
                        exerciseMapper.toEntity(exerciseWithCorrectWorkoutId, workout.userId)
                    } catch (e: Exception) {
                        throw RuntimeException("Failed to map exercise to entity during update: ${e.message}", e)
                    }
                    val exerciseId = exerciseDao.insertExercise(exerciseEntity)
                    Log.d(
                        "PREV_SAVE",
                        "Saving exercise: workoutId=${workout.id.value} exerciseRowId=$exerciseId " +
                            "exerciseId=${exercise.libraryExercise.id} name=${exercise.libraryExercise.name}"
                    )
                    
                    // Create ExerciseSetEntity records for each set
                    exercise.sets.forEachIndexed { setIndex, set ->
                        Log.d(
                            "PREV_SAVE",
                            "Saving set: exerciseId=${exercise.libraryExercise.id} name=${exercise.libraryExercise.name} " +
                                "weight=${set.weight?.kilograms} reps=${set.reps?.count} completedAt=${set.completedAt}"
                        )
                        val setEntity = ExerciseSetEntity(
                            userId = workout.userId,
                            exerciseId = exerciseId,
                            setNumber = setIndex + 1,
                            reps = set.reps?.count,
                            weightKg = set.weight?.kilograms?.toFloat(),
                            timeSeconds = set.time?.seconds?.toInt(),
                            rpe = set.rpe?.value,
                            completedAt = set.completedAt?.toEpochMilli()
                        )
                        
                        
                        val setRowId = exerciseSetDao.insertSet(setEntity)
                        Log.d(
                            "PREV_DB_WRITE",
                            "DB write completed for ${exercise.libraryExercise.id} setRowId=$setRowId " +
                                "weight=${setEntity.weightKg} reps=${setEntity.reps} completedAt=${setEntity.completedAt}"
                        )
                    }
                }
                validateExerciseConsistency(workout, entity.exercisesJson)
                // Queue workout for sync after successful update
                queueWorkoutForSync(workout)
                
                workout
            } else {
                Timber.e("🔥 UPDATE-WORKOUT-DEBUG: Insert/Update failed for workout ID: ${workout.id.value}")
                throw RuntimeException("Workout update operation failed for ID: ${workout.id.value}")
            }
        }
    }

    override suspend fun deleteWorkout(workoutId: WorkoutId, userId: String): LiftrixResult<Unit> {
        Timber.w("[WORKOUT-DEBUG] deleteWorkout requested id=${workoutId.value} userId=$userId")
        val beforeCount = runCatching { workoutDao.getWorkoutCountForUser(userId) }.getOrDefault(-1)
        Timber.tag("WorkoutSyncDebug").w(
            "[DATABASE-DEBUG] operation=REPOSITORY_DELETE_REQUEST source=Room userId=$userId workoutId=${workoutId.value} timestamp=${System.currentTimeMillis()} beforeCount=$beforeCount"
        )
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
            Timber.w("[WORKOUT-DEBUG] deleteWorkout storage delete rows=$deletedRows id=${workoutId.value} userId=$userId")
            val afterCount = workoutDao.getWorkoutCountForUser(userId)
            Timber.tag("WorkoutSyncDebug").w(
                "[DATABASE-DEBUG] operation=REPOSITORY_DELETE_WRITTEN source=Room userId=$userId workoutId=${workoutId.value} timestamp=${System.currentTimeMillis()} beforeCount=$beforeCount afterCount=$afterCount deletedRows=$deletedRows"
            )
            
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
        applicationScope.launch {
            try {
                val totalCount = workoutDao.getWorkoutCountForUser(userId)
                Timber.d("WORKOUT-DEBUG: Total workouts for user: $totalCount")
                
                if (totalCount > 0) {
                    val allWorkoutsFlow = workoutDao.getAllWorkoutsForUser(userId)
                    allWorkoutsFlow.first().let { allEntities ->
                        Timber.d("WORKOUT-DEBUG: All workouts query returned ${allEntities.size} entities")
                        allEntities.forEach { entity ->
                            val updatedAtMillis = entity.updatedAt.toEpochMilli()
                            val createdAtMillis = entity.createdAt.toEpochMilli()
                            Timber.d("WORKOUT-DEBUG: Entity - ID: ${entity.id.take(8)}..., Status: ${entity.status}, updatedAt: $updatedAtMillis, createdAt: $createdAtMillis")
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
                    Timber.d("WORKOUT-DEBUG: Found ${entities.size} workouts from getRecentCompletedWorkouts (limit: $limit)")
                    entities.forEach { entity ->
                        val updatedAtMillis = entity.updatedAt.toEpochMilli()
                        val createdAtMillis = entity.createdAt.toEpochMilli()
                        Timber.d("WORKOUT-DEBUG: Recent workout - ID: ${entity.id.take(8)}..., Status: ${entity.status}, updatedAt: $updatedAtMillis, createdAt: $createdAtMillis, isSynced: ${entity.isSynced}")
                    }
                    
                    val workouts = entities.map { entity ->
                        workoutMapper.toDomain(entity)
                    }
                    
                    Timber.d("WORKOUT-DEBUG: Successfully mapped ${workouts.size} workouts to domain models")
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
        Timber.w("[WORKOUT-DEBUG] deleteAllWorkouts requested userId=$userId")
        val beforeCount = runCatching { workoutDao.getWorkoutCountForUser(userId) }.getOrDefault(-1)
        Timber.tag("WorkoutSyncDebug").w(
            "[DATABASE-DEBUG] operation=REPOSITORY_DELETE_ALL_REQUEST source=Room userId=$userId timestamp=${System.currentTimeMillis()} beforeCount=$beforeCount"
        )
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
            Timber.w("[WORKOUT-DEBUG] deleteAllWorkouts storage delete rows=$deletedRows userId=$userId")
            val afterCount = workoutDao.getWorkoutCountForUser(userId)
            Timber.tag("WorkoutSyncDebug").w(
                "[DATABASE-DEBUG] operation=REPOSITORY_DELETE_ALL_WRITTEN source=Room userId=$userId timestamp=${System.currentTimeMillis()} beforeCount=$beforeCount afterCount=$afterCount deletedRows=$deletedRows"
            )
            Timber.i("Deleted $deletedRows workouts for user: $userId")
        }
    }

    // ============== LEGACY COMPATIBILITY METHODS IMPLEMENTATION ==============

    override suspend fun saveWorkout(workout: Workout): Result<Unit> {
        return try {
            // 🔥 DEBUG: Log save operation start
            Timber.d("[WORKOUT-SAVE-DEBUG] Starting saveWorkout for '${workout.name}' (${workout.id.value}) with ${workout.exercises.size} exercises")
            
            // Check if workout already exists in database
            val existingWorkout = getWorkoutById(workout.id, workout.userId).getOrNull()
            
            val result = if (workout.id.value.isBlank() || existingWorkout == null) {
                // Create new workout if ID is blank OR workout doesn't exist
                Timber.d("[WORKOUT-SAVE-DEBUG] Creating new workout (no existing found)")
                createWorkout(workout)
            } else {
                // Only update if workout already exists
                Timber.d("[WORKOUT-SAVE-DEBUG] Updating existing workout")
                updateWorkoutWithRetry(workout)
            }
            
            result.fold(
                onSuccess = { 
                    Timber.d("[WORKOUT-SAVE-DEBUG] ✅ Successfully saved workout '${workout.name}' to database")
                    Result.success(Unit) 
                },
                onFailure = { throwable -> 
                    Timber.e("[WORKOUT-SAVE-DEBUG] ❌ Failed to save workout '${workout.name}': $throwable")
                    Result.failure(throwable) 
                }
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
            // Get the workout to queue for sync
            val workoutEntity = workoutDao.getWorkoutByIdForUser(workoutId.value, userId)
            if (workoutEntity != null) {
                // Parse exercises JSON to properly typed DTOs
                val exercises = try {
                    if (workoutEntity.exercisesJson.isNullOrBlank()) {
                        emptyList<ExerciseDto>()
                    } else {
                        Json { ignoreUnknownKeys = true }.decodeFromString<List<ExerciseDto>>(workoutEntity.exercisesJson)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse exercises JSON for workout ${workoutEntity.id}, using empty list")
                    emptyList<ExerciseDto>()
                }
                
                // Create type-safe WorkoutSyncDto
                val workoutSyncDto = WorkoutSyncDto(
                    id = workoutEntity.id,
                    userId = workoutEntity.userId,
                    name = workoutEntity.name,
                    date = workoutEntity.date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
                    status = workoutEntity.status.name,
                    startTime = workoutEntity.startTime?.epochSecond,
                    endTime = workoutEntity.endTime?.epochSecond,
                    exercises = exercises,
                    notes = workoutEntity.notes,
                    templateId = workoutEntity.templateId,
                    createdAt = workoutEntity.createdAt.epochSecond,
                    updatedAt = workoutEntity.updatedAt.epochSecond,
                    syncVersion = workoutEntity.syncVersion,
                    isSynced = false
                )
                
                // Create type-safe payload
                val payload = SyncPayloadFactory.createWorkoutPayload(workoutSyncDto)
                
                offlineQueueManager.queueOperation(
                    userId = userId,
                    entityType = "WORKOUT",
                    entityId = workoutId.value,
                    operation = "UPSERT",
                    data = payload
                )
                
                // Also trigger sync coordinator for immediate sync attempt
                syncScheduler.triggerEntitySync(userId, "workout")
                
                Timber.d("Queued workout ${workoutId.value} for sync and triggered immediate sync")
            } else {
                throw IllegalArgumentException("Workout not found: ${workoutId.value}")
            }
        }
    }

    override suspend fun syncNow(userId: String): LiftrixResult<Unit> {
        return syncNowForUser(userId)
    }

    override suspend fun syncNowForUser(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "SYNC_TRIGGER_FAILED",
                    errorMessage = "Failed to trigger immediate sync: ${throwable.message}",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            // Trigger immediate sync through SyncCoordinator
            val syncResult = syncScheduler.triggerEntitySync(userId, "workout")
            
            when {
                syncResult.isSuccess -> {
                    Timber.d("Successfully triggered immediate workout sync for user: $userId")
                }
                syncResult.isFailure -> {
                    val error = syncResult.exceptionOrNull()
                    Timber.e("Sync coordinator failed to trigger sync: $error")
                    throw Exception("Sync trigger failed: ${error?.message ?: "Unknown error"}")
                }
            }
            
            // Also process any pending offline queue items
            val queueResult = offlineQueueManager.processPendingQueue(userId)
            when {
                queueResult.isSuccess -> {
                    Timber.d("Processed pending queue items for user: $userId - ${queueResult.getOrNull()}")
                }
                queueResult.isFailure -> {
                    val error = queueResult.exceptionOrNull()
                    Timber.w("Failed to process pending queue: ${error?.message}")
                    // Don't fail the entire operation for queue processing failure
                }
            }
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
            Timber.tag("WorkoutSyncDebug").d(
                "[DATABASE-DEBUG] operation=FEED_WORKOUTS_QUERY_RESULT source=Room userId=$userId timestamp=${System.currentTimeMillis()} inputLimit=$limit outputCount=${workoutEntities.size} statusCounts=${workoutEntities.groupingBy { it.status }.eachCount()} completedWithoutEndTime=${workoutEntities.count { it.status.name == "COMPLETED" && it.endTime == null }}"
            )
            
            
            // Map entities to FeedWorkout domain models
            val feedWorkouts = workoutEntities.map { entity ->
                val workout = workoutMapper.toDomain(entity)
                FeedWorkout.forPersonalWorkout(workout)
            }
            
            feedWorkouts
        }
    }

    override fun getFeedWorkoutsReactive(userId: String, limit: Int): Flow<LiftrixResult<List<FeedWorkout>>> {
        
        return workoutDao.getRecentCompletedWorkouts(userId, limit)
            .map { entities ->
                try {
                    Timber.tag("WorkoutSyncDebug").d(
                        "[DATABASE-DEBUG] operation=FEED_WORKOUTS_REACTIVE_RESULT source=Room userId=$userId timestamp=${System.currentTimeMillis()} inputLimit=$limit outputCount=${entities.size} statusCounts=${entities.groupingBy { it.status }.eachCount()} completedWithoutEndTime=${entities.count { it.status.name == "COMPLETED" && it.endTime == null }}"
                    )
                    
                    // Map entities to FeedWorkout domain models
                    val feedWorkouts = entities.map { entity ->
                        val workout = workoutMapper.toDomain(entity)
                        FeedWorkout.forPersonalWorkout(workout)
                    }
                    
                            LiftrixResult.success(feedWorkouts)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Error mapping workout entities to feed workouts")
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
                Timber.e(throwable, "Database flow error for feed workouts")
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
    
    override fun getRecentActivityFeed(userId: String, includeOthers: Boolean, limit: Int): Flow<LiftrixResult<List<FeedWorkout>>> {
        
        val json = Json { ignoreUnknownKeys = true }
        
        return if (includeOthers) {
            // EXPLORE TAB: Get all public workout posts
            workoutPostDao.getRecentPublicPosts(limit)
                .map { postEntities ->
                    val feedWorkouts = postEntities.mapNotNull { postEntity ->
                        runCatching {
                            val workoutEntity = workoutDao.getWorkoutByIdForUser(
                                postEntity.workoutId,
                                postEntity.userId
                            ) ?: return@runCatching null
                            val workout = workoutMapper.toDomain(workoutEntity)
                            val mediaUrls = parseStringList(json, postEntity.mediaUrls, "media URLs")
                            val mediaThumbnails = parseStringList(json, postEntity.mediaThumbnails, "media thumbnails")
                            val isPersonal = postEntity.userId == userId

                            if (isPersonal) {
                                FeedWorkout.forPersonalWorkout(
                                    workout = workout,
                                    mediaUrls = mediaUrls,
                                    mediaThumbnails = mediaThumbnails
                                )
                            } else {
                                val user = User.forSocialDisplay(
                                    uid = postEntity.userId,
                                    displayName = "User"
                                )
                                FeedWorkout.forFriendWorkout(
                                    workout = workout,
                                    friendUser = user,
                                    mediaUrls = mediaUrls,
                                    mediaThumbnails = mediaThumbnails
                                )
                            }
                        }.onFailure { throwable ->
                            Timber.e(throwable, "Skipping invalid explore feed post ${postEntity.id}")
                        }.getOrNull()
                    }

                    LiftrixResult.success(feedWorkouts)
                }
        } else {
            // FOLLOWING TAB: Get posts from user and people they follow
            // First get the followed user IDs, then query posts
            flow {
                val followedUserIds = followRelationshipDao.getFollowingUserIds(userId)
                val allUserIds = followedUserIds + userId

                workoutPostDao.getRecentPostsFromUsers(allUserIds, limit)
                    .collect { postEntities ->
                        val feedWorkouts = postEntities.mapNotNull { postEntity ->
                            runCatching {
                                val workoutEntity = workoutDao.getWorkoutByIdForUser(
                                    postEntity.workoutId,
                                    postEntity.userId
                                ) ?: return@runCatching null
                                val workout = workoutMapper.toDomain(workoutEntity)
                                val mediaUrls = parseStringList(json, postEntity.mediaUrls, "media URLs")
                                val mediaThumbnails = parseStringList(json, postEntity.mediaThumbnails, "media thumbnails")
                                val isPersonal = postEntity.userId == userId

                                if (isPersonal) {
                                    FeedWorkout.forPersonalWorkout(
                                        workout = workout,
                                        mediaUrls = mediaUrls,
                                        mediaThumbnails = mediaThumbnails
                                    )
                                } else {
                                    val user = User.forSocialDisplay(
                                        uid = postEntity.userId,
                                        displayName = "Friend"
                                    )
                                    FeedWorkout.forFriendWorkout(
                                        workout = workout,
                                        friendUser = user,
                                        mediaUrls = mediaUrls,
                                        mediaThumbnails = mediaThumbnails
                                    )
                                }
                            }.onFailure { throwable ->
                                Timber.e(throwable, "Skipping invalid following feed post ${postEntity.id}")
                            }.getOrNull()
                        }

                        emit(LiftrixResult.success(feedWorkouts))
                    }
            }
        }.catch { throwable ->
            Timber.e(throwable, "Database flow error for recent activity")
            emit(
                LiftrixResult.failure(
                    LiftrixError.DatabaseError(
                        errorMessage = "Database connection error while retrieving recent activity",
                        operation = "READ",
                        table = "workout_posts",
                        analyticsContext = mapOf(
                            "user_id" to userId,
                            "include_others" to includeOthers.toString(),
                            "limit" to limit.toString()
                        )
                    )
                )
            )
        }
    }

    private fun parseStringList(json: Json, rawJson: String?, label: String): List<String> {
        return rawJson?.let {
            try {
                json.decodeFromString(ListSerializer(String.serializer()), it)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse $label")
                emptyList()
            }
        } ?: emptyList()
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
            val completedWorkouts = withContext(Dispatchers.IO) {
                workoutDao.getCompletedWorkoutsForStats(userId, 1000)
            }
            
            if (completedWorkouts.isEmpty()) {
                return@liftrixCatching WorkoutStats(
                    totalWorkouts = 0,
                    currentStreak = 0,
                    weeklyVolume = Duration.ZERO,
                    averageWorkoutDuration = Duration.ZERO
                )
            }
            
            val totalWorkouts = completedWorkouts.size
            
            val currentStreak = calculateCurrentStreakFromDates(completedWorkouts.map { it.date })
            
            val totalDuration = completedWorkouts
                .map { workout -> calculateDuration(workout.startTime, workout.endTime) }
                .fold(Duration.ZERO) { acc, duration -> acc.plus(duration) }
            
            val averageWorkoutDuration = if (totalWorkouts > 0 && totalDuration != Duration.ZERO) {
                totalDuration.dividedBy(totalWorkouts.toLong())
            } else {
                Duration.ZERO
            }
            
            // Calculate weekly volume (workouts in last 7 days)
            val now = LocalDate.now()
            val weekAgo = now.minusDays(7)
            val weeklyWorkouts = completedWorkouts.filter { workout ->
                workout.date.isAfter(weekAgo) || workout.date.isEqual(weekAgo)
            }
            val weeklyVolume = weeklyWorkouts
                .map { workout -> calculateDuration(workout.startTime, workout.endTime) }
                .fold(Duration.ZERO) { acc, duration -> acc.plus(duration) }
            
            WorkoutStats(
                totalWorkouts = totalWorkouts,
                currentStreak = currentStreak,
                weeklyVolume = weeklyVolume,
                averageWorkoutDuration = averageWorkoutDuration,
                workoutsThisWeek = weeklyWorkouts.size,
                totalMinutesThisWeek = weeklyVolume.toMinutes().toInt(),
                weeklyWorkouts = weeklyWorkouts.size,
                workoutCount = totalWorkouts
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

    private fun calculateCurrentStreakFromDates(workoutDates: List<LocalDate>): Int {
        if (workoutDates.isEmpty()) return 0

        val sortedDates = workoutDates.distinct().sortedDescending()
        val today = LocalDate.now()
        val mostRecentDate = sortedDates.first()
        if (mostRecentDate.isBefore(today.minusDays(1))) {
            return 0
        }

        var streak = 0
        var expectedDate = if (mostRecentDate.isEqual(today)) today else today.minusDays(1)
        for (date in sortedDates) {
            if (date.isEqual(expectedDate)) {
                streak++
                expectedDate = expectedDate.minusDays(1)
            } else if (date.isBefore(expectedDate)) {
                break
            }
        }

        return streak
    }

    private fun calculateDuration(startTime: Instant?, endTime: Instant?): Duration {
        return if (startTime != null && endTime != null && endTime >= startTime) {
            Duration.between(startTime, endTime)
        } else {
            Duration.ZERO
        }
    }
    
    /**
     * Calculates workout duration in minutes from start and end time
     */
    private fun calculateDurationMinutes(startTime: Instant?, endTime: Instant?): Int {
        return if (startTime != null && endTime != null) {
            Duration.between(startTime, endTime).toMinutes().toInt().coerceAtLeast(0)
        } else {
            0
        }
    }
    
    /**
     * Calculates exercise count from exercisesJson field
     * This is a simplified calculation - in reality, you'd want to parse the JSON
     */
    private fun calculateExerciseCount(exercisesJson: String): Int {
        return try {
            // Simple heuristic: count occurrences of exercise objects in JSON
            // This is not perfect but avoids full JSON parsing for performance
            if (exercisesJson.isBlank() || exercisesJson == "[]") {
                0
            } else {
                // Count the number of exercise objects by counting opening braces after array start
                // This is a rough estimate that works for simple JSON structures
                val trimmed = exercisesJson.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    // Count commas + 1 for a simple array, but ensure at least 1 if not empty
                    val commaCount = exercisesJson.count { it == ',' }
                    if (trimmed == "[]") 0 else (commaCount + 1).coerceAtLeast(1)
                } else {
                    1 // Single exercise object
                }
            }
        } catch (e: Exception) {
            Timber.w("Failed to calculate exercise count from JSON: ${e.message}")
            0
        }
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
            withContext(Dispatchers.IO) {
                val performanceRows = exerciseSetDao.getExercisePerformanceData(
                    userId = userId,
                    startDate = startDate.toString(),
                    endDate = endDate.toString()
                )
                val historyByExercise = exerciseSetDao.getExercisePerformanceHistory(
                    userId = userId,
                    startDate = startDate.toString(),
                    endDate = endDate.toString()
                ).groupBy { it.exercise_library_id }

                performanceRows.map { row ->
                    val history = historyByExercise[row.exercise_library_id].orEmpty()
                    ExercisePerformanceData(
                        exerciseId = row.exercise_library_id,
                        exerciseName = row.exercise_name,
                        muscleGroup = row.primary_muscle_group,
                        totalVolume = row.total_volume,
                        totalSets = row.total_sets,
                        workoutDays = row.workout_days,
                        maxEstimated1RM = row.max_estimated_one_rm,
                        performanceScore = row.performance_score,
                        volumeHistory = history.mapNotNull { point ->
                            point.toVolumeDataPoint()
                        },
                        oneRmHistory = history.mapNotNull { point ->
                            point.toOneRmDataPoint()
                        }
                    )
                }
            }
        }
    }
    
    override suspend fun getWorkoutsInDateRange(
        userId: String,
        startDate: kotlinx.datetime.LocalDate,
        endDate: kotlinx.datetime.LocalDate
    ): List<WorkoutData> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get workouts in date range",
                    operation = "READ",
                    table = "workouts",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val startDateString = startDate.toString()
            val endDateString = endDate.toString()
            
            val workoutMetrics = workoutDao.getCompletedWorkoutMetricsInDateRange(
                userId = userId,
                startDate = startDateString,
                endDate = endDateString
            )
            
            workoutMetrics.map { row ->
                WorkoutData(
                    id = row.workoutId,
                    date = kotlinx.datetime.LocalDate.parse(row.workoutDate),
                    durationMinutes = row.durationMinutes,
                    exerciseCount = if (row.exerciseCount > 0) {
                        row.exerciseCount
                    } else {
                        calculateExerciseCount(row.exercisesJson)
                    }
                )
            }
        }.fold(
            onSuccess = { it },
            onFailure = { 
                Timber.e("Failed to get workouts in date range: $it")
                emptyList()
            }
        )
    }
    
    /**
     * 🔥 NEW: Triggers immediate bidirectional sync for a user.
     * This fetches remote workouts AND uploads local unsynced workouts.
     */
    suspend fun triggerBidirectionalSync(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "BIDIRECTIONAL_SYNC_FAILED",
                    errorMessage = "Failed to trigger bidirectional sync: ${throwable.message}",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            Timber.d("[BIDIRECTIONAL-SYNC] Triggering complete bidirectional sync for user $userId")
            
            // The WorkoutSyncWorker now handles both directions automatically
            val syncResult = syncScheduler.triggerEntitySync(userId, "workout")
            
            syncResult.fold(
                onSuccess = {
                    Timber.d("[BIDIRECTIONAL-SYNC] Successfully triggered bidirectional sync for user $userId")
                },
                onFailure = { error ->
                    Timber.e("[BIDIRECTIONAL-SYNC] Failed to trigger sync: $error")
                    throw Exception("Bidirectional sync failed: ${error.message}")
                }
            )
        }
    }
    
    /**
     * 🔥 NEW: Queues a FETCH operation for offline processing.
     * This ensures remote workouts are fetched when the device comes back online.
     */
    suspend fun queueRemoteFetch(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "QUEUE_FETCH_FAILED",
                    errorMessage = "Failed to queue remote fetch: ${throwable.message}",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            Timber.d("[REMOTE-FETCH-QUEUE] Queueing remote fetch operation for user $userId")
            
            // The offline queue manager will need to support FETCH operations
            // For now, we trigger immediate sync which includes fetch
            val syncResult = syncScheduler.triggerEntitySync(userId, "workout")
            
            syncResult.fold(
                onSuccess = {
                    Timber.d("[REMOTE-FETCH-QUEUE] Successfully queued remote fetch for user $userId")
                },
                onFailure = { error ->
                    Timber.w("[REMOTE-FETCH-QUEUE] Failed to queue fetch, will retry later: $error")
                    // Don't fail - this is a best-effort operation
                }
            )
        }
    }
    
    /**
     * Helper method to queue a workout for sync after creation or update
     */
    private suspend fun queueWorkoutForSync(workout: Workout) {
        try {
            // Convert to entity first to get a serializable format
            val workoutEntity = workoutMapper.toEntity(workout)
            
            // Parse exercises JSON to properly typed DTOs
            // The stored JSON can be in two formats:
            // 1. Workouts: {"exercises": [...], "totalVolume": ..., ...} - wrapped object with metadata
            // 2. Templates: [...] - direct array of exercises
            val exercises = try {
                if (workoutEntity.exercisesJson.isNullOrBlank()) {
                    emptyList<ExerciseDto>()
                } else {
                    // Use kotlinx.serialization for modern, type-safe deserialization
                    val domainExercises = try {
                        if (BuildConfig.DEBUG) {
                            Timber.d("🚀 KOTLINX-REPO: Deserializing exercises for workout ${workoutEntity.id}")
                        }

                        if (canonicalJsonAdapter.isCanonicalJson(workoutEntity.exercisesJson)) {
                            canonicalJsonAdapter.deserializeToDomain(
                                workoutEntity.exercisesJson,
                                WorkoutId(workoutEntity.id)
                            )
                        } else {
                            kotlinxSerializer.deserializeExercises(workoutEntity.exercisesJson)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "❌ SERIALIZATION-ERROR: Failed to deserialize exercises for workout ${workoutEntity.id}")
                        throw RuntimeException("Unable to load workout data. This workout uses an unsupported format. Please export and re-import your workouts.", e)
                    }
                    
                    // Convert Exercise domain models to ExerciseDto for sync
                    domainExercises.mapNotNull { exercise ->
                        try {
                            ExerciseDto(
                                id = exercise.id.value,
                                name = exercise.libraryExercise.name,
                                muscleGroup = exercise.libraryExercise.primaryMuscleGroup.name,
                                sets = exercise.sets.map { set ->
                                    com.example.liftrix.data.model.SetDto(
                                        setNumber = set.setNumber,
                                        targetReps = exercise.targetReps,  // From Exercise, not ExerciseSet
                                        actualReps = set.reps?.count,      // Reps object has count field
                                        targetWeight = exercise.targetWeight?.kilograms,  // From Exercise
                                        actualWeight = set.weight?.kilograms,  // Weight from set
                                        completed = set.isCompleted,
                                        notes = set.notes,
                                        rpe = set.rpe?.value?.toDouble(),
                                        dropSet = false,  // Not stored in ExerciseSet
                                        superSet = false  // Not stored in ExerciseSet
                                    )
                                },
                                notes = exercise.notes,
                                restTimeSeconds = null, // Not stored in Exercise domain model
                                orderIndex = exercise.orderIndex
                            )
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to convert exercise ${exercise.id.value} to DTO")
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse and convert exercises for workout ${workoutEntity.id}, using empty list")
                emptyList<ExerciseDto>()
            }
            
            // Create type-safe WorkoutSyncDto
            val workoutSyncDto = WorkoutSyncDto(
                id = workoutEntity.id,
                userId = workoutEntity.userId,
                name = workoutEntity.name,
                date = workoutEntity.date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
                status = workoutEntity.status.name,
                startTime = workoutEntity.startTime?.epochSecond,
                endTime = workoutEntity.endTime?.epochSecond,
                exercises = exercises,
                notes = workoutEntity.notes,
                templateId = workoutEntity.templateId,
                createdAt = workoutEntity.createdAt.epochSecond,
                updatedAt = workoutEntity.updatedAt.epochSecond,
                syncVersion = workoutEntity.syncVersion,
                isSynced = false
            )
            
            // Create type-safe payload
            val payload = SyncPayloadFactory.createWorkoutPayload(workoutSyncDto)
            
            
            offlineQueueManager.queueOperation(
                userId = workout.userId,
                entityType = "WORKOUT",
                entityId = workout.id.value,
                operation = "UPSERT",
                data = payload
            )
            
            
            // Trigger sync coordinator for background sync
            applicationScope.launch {
                val syncResult = syncScheduler.triggerEntitySync(workout.userId, "workout")
                
                // 🚀 CRITICAL FIX: Also trigger public profile sync to update workout count
                // This ensures that when users create/complete workouts, their workout stats become visible to other users
                val profileSyncResult = syncScheduler.triggerEntitySync(workout.userId, "user_public")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue workout ${workout.id.value} for sync")
            // Don't fail the entire operation for sync queueing failure
        }
    }

    private suspend fun validateExerciseConsistency(workout: Workout, exercisesJson: String) {
        val result = exerciseConsistencyValidator.validateWorkoutConsistency(
            workoutId = workout.id.value,
            userId = workout.userId,
            exercisesJson = exercisesJson
        )

        when (result) {
            ExerciseConsistencyValidator.ConsistencyResult.Valid -> Unit
            is ExerciseConsistencyValidator.ConsistencyResult.CountMismatch,
            is ExerciseConsistencyValidator.ConsistencyResult.IdMismatch,
            is ExerciseConsistencyValidator.ConsistencyResult.StaleTotals -> {
                val canSelfHeal = when (result) {
                    is ExerciseConsistencyValidator.ConsistencyResult.CountMismatch -> result.canSelfHeal
                    is ExerciseConsistencyValidator.ConsistencyResult.IdMismatch -> result.canSelfHeal
                    is ExerciseConsistencyValidator.ConsistencyResult.StaleTotals -> result.canSelfHeal
                    else -> false
                }
                if (canSelfHeal && OfflineArchitectureFlags.ENABLE_CANONICAL_JSON_FORMAT) {
                    val healedJson = exerciseConsistencyValidator.selfHealFromNormalized(
                        workoutId = workout.id.value,
                        userId = workout.userId
                    )
                    if (healedJson != exercisesJson) {
                        workoutDao.updateExercisesJsonForWorkout(
                            workoutId = workout.id.value,
                            userId = workout.userId,
                            exercisesJson = healedJson,
                            updatedAt = Instant.now(),
                            lastModified = System.currentTimeMillis()
                        )
                    }
                } else if (canSelfHeal) {
                    Timber.d("Exercise JSON self-heal skipped (canonical format disabled) for workout ${workout.id.value}")
                }
            }
            is ExerciseConsistencyValidator.ConsistencyResult.JsonParseError,
            is ExerciseConsistencyValidator.ConsistencyResult.FatalError -> {
                Timber.w("Exercise JSON consistency validation failed for workout ${workout.id.value}")
            }
        }
    }
    
    override suspend fun getLastCompletedWorkoutsWithExercise(
        userId: String,
        exerciseId: String,
        exerciseName: String?,
        limit: Int,
        excludeWorkoutId: String?
    ): LiftrixResult<List<Workout>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get last completed workouts with exercise",
                    operation = "READ",
                    table = "workouts",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "exercise_id" to exerciseId,
                        "exercise_name" to (exerciseName ?: "null"),
                        "limit" to limit.toString(),
                        "exclude_workout_id" to (excludeWorkoutId ?: "null")
                    )
                )
            }
        ) {
            withContext(Dispatchers.IO) {
                val normalizedExerciseName = exerciseName.normalizedExerciseIdentifierOrNull()
                Log.d(
                    "PREV_QUERY",
                    "Querying previous set for $exerciseId exerciseName=$exerciseName userId=$userId limit=$limit excludeWorkoutId=$excludeWorkoutId"
                )
                Timber.tag("PREV_SET_QUERY").d(
                    "SQL=WorkoutDao.getLastCompletedWorkoutsWithExercise userId=$userId exerciseId=$exerciseId exerciseName=$exerciseName limit=$limit excludeWorkoutId=$excludeWorkoutId"
                )

                val normalizedWorkoutEntities = workoutDao.getLastCompletedWorkoutsWithExercise(
                    userId = userId,
                    exerciseId = exerciseId,
                    limit = limit,
                    excludeWorkoutId = excludeWorkoutId
                )
                Timber.tag("PREV_SET_QUERY").d(
                    "canonicalRows=${normalizedWorkoutEntities.size} exerciseId=$exerciseId"
                )
                Log.d(
                    "PREV_QUERY",
                    "Canonical query rows=${normalizedWorkoutEntities.size} for $exerciseId"
                )

                val legacyNameWorkoutEntities = if (
                    normalizedExerciseName != null &&
                    normalizedExerciseName != exerciseId.normalizedExerciseIdentifierOrNull()
                ) {
                    Timber.tag("PREV_SET_QUERY").d(
                        "SQL=WorkoutDao.getLastCompletedWorkoutsWithExercise legacyName userId=$userId exerciseName=$exerciseName limit=$limit excludeWorkoutId=$excludeWorkoutId"
                    )
                    workoutDao.getLastCompletedWorkoutsWithExercise(
                        userId = userId,
                        exerciseId = exerciseName!!.trim(),
                        limit = limit,
                        excludeWorkoutId = excludeWorkoutId
                    ).also { rows ->
                        Log.d(
                            "PREV_QUERY",
                            "Fallback display-name query rows=${rows.size} for $exerciseName"
                        )
                        Timber.tag("PREV_SET_QUERY").d(
                            "legacyNameRows=${rows.size} exerciseName=$exerciseName"
                        )
                    }
                } else {
                    emptyList()
                }

                val directWorkoutEntities = (normalizedWorkoutEntities + legacyNameWorkoutEntities)
                    .distinctBy { entity -> entity.id }
                    .take(limit)

                val workoutEntities = if (directWorkoutEntities.size >= limit) {
                    directWorkoutEntities
                } else {
                    val normalizedIds = directWorkoutEntities.mapTo(mutableSetOf()) { it.id }
                    val fallbackLimit = ((limit - directWorkoutEntities.size) * 10)
                        .coerceAtLeast(20)
                    Timber.tag("PREV_SET_QUERY").d(
                        "SQL=WorkoutDao.getRecentCompletedWorkoutsForExerciseJsonFallback userId=$userId limit=$fallbackLimit excludeWorkoutId=$excludeWorkoutId"
                    )
                    val fallbackWorkoutEntities = workoutDao.getRecentCompletedWorkoutsForExerciseJsonFallback(
                        userId = userId,
                        limit = fallbackLimit,
                        excludeWorkoutId = excludeWorkoutId
                    ).filterNot { entity ->
                        entity.id in normalizedIds
                    }.filter { entity ->
                        entity.containsExerciseInJsonFallback(exerciseId, exerciseName)
                    }.also { rows ->
                        Log.d(
                            "PREV_QUERY",
                            "JSON fallback rows=${rows.size} for $exerciseId exerciseName=$exerciseName"
                        )
                        Timber.tag("PREV_SET_QUERY").d(
                            "jsonFallbackRows=${rows.size} exerciseId=$exerciseId exerciseName=$exerciseName"
                        )
                    }

                    (directWorkoutEntities + fallbackWorkoutEntities)
                        .distinctBy { entity -> entity.id }
                        .take(limit)
                }
                
                Timber.tag(if (workoutEntities.isEmpty()) "PREV_SET_EMPTY" else "PREV_SET_QUERY").d(
                    "rowsReturned=${workoutEntities.size} exerciseId=$exerciseId exerciseName=$exerciseName canonicalRows=${normalizedWorkoutEntities.size} legacyNameRows=${legacyNameWorkoutEntities.size}"
                )
                if (workoutEntities.isEmpty()) {
                    Log.d("PREV_QUERY_EMPTY", "No rows returned for $exerciseId exerciseName=$exerciseName userId=$userId")
                }
                workoutEntities.map { entity ->
                    workoutMapper.toDomain(entity).also { workout ->
                        workout.exercises
                            .firstOrNull { exercise ->
                                exercise.libraryExercise.id == exerciseId ||
                                    exercise.libraryExercise.name.normalizedExerciseIdentifierOrNull() == normalizedExerciseName
                            }
                            ?.sets
                            ?.firstOrNull { set -> set.completedAt != null && set.weight != null }
                            ?.let { set ->
                                Log.d(
                                    "PREV_QUERY_RESULT",
                                    "Found previous weight=${set.weight?.kilograms} for $exerciseId workoutId=${workout.id.value}"
                                )
                            }
                        val matched = workout.exercises.map { exercise ->
                            "${exercise.libraryExercise.id}|${exercise.libraryExercise.name}"
                        }
                        Timber.tag("PREV_SET_QUERY").d(
                            "matchedWorkout=${workout.id.value} matchedExerciseIdentifiers=$matched"
                        )
                    }
                }
            }
        }
    }

    private fun com.example.liftrix.data.local.dao.ExercisePerformanceHistoryResult.toVolumeDataPoint(): PerformanceDataPoint? {
        val parsedDate = runCatching { KotlinxLocalDate.parse(date) }.getOrNull() ?: return null
        return PerformanceDataPoint(
            date = parsedDate,
            volume = total_volume,
            weight = max_weight,
            reps = max_reps
        )
    }

    private fun com.example.liftrix.data.local.dao.ExercisePerformanceHistoryResult.toOneRmDataPoint(): PerformanceDataPoint? {
        val parsedDate = runCatching { KotlinxLocalDate.parse(date) }.getOrNull() ?: return null
        return PerformanceDataPoint(
            date = parsedDate,
            weight = max_weight,
            reps = max_reps,
            oneRm = max_estimated_one_rm
        )
    }

    private fun WorkoutEntity.containsExerciseInJsonFallback(exerciseId: String, exerciseName: String?): Boolean {
        return runCatching {
            val normalizedExerciseName = exerciseName.normalizedExerciseIdentifierOrNull()
            workoutMapper.toDomain(this).exercises.any { exercise ->
                exercise.libraryExercise.id == exerciseId ||
                    (normalizedExerciseName != null &&
                        exercise.libraryExercise.name.normalizedExerciseIdentifierOrNull() == normalizedExerciseName)
            }
        }.getOrElse { throwable ->
            Timber.w(throwable, "Failed to parse legacy workout JSON for previous-set fallback")
            false
        }
    }

    private fun String?.normalizedExerciseIdentifierOrNull(): String? =
        this?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

}
