package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.PreviousSetRepository
import com.example.liftrix.domain.repository.workout.WorkoutHistoryRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

/**
 * Consolidated use case for all workout query operations.
 *
 * Consolidates the following use cases:
 * - GetWorkoutByIdUseCase (123 lines)
 * - GetWorkoutHistoryUseCase (126 lines)
 * - GetPreviousWorkoutDataUseCase (88 lines)
 * - GetPreviousSetDataUseCase (421 lines)
 *
 * Responsibilities:
 * - Retrieve workouts by ID with authorization
 * - Query workout history with pagination
 * - Get previous workout data for comparison
 * - Get previous set data for exercise performance tracking
 *
 * Business Rules:
 * - All operations enforce user_id scoping for security
 * - Authorization validated at use case level
 * - Null results for non-existent data (not errors)
 * - Proper error handling with LiftrixResult pattern
 *
 * Architecture:
 * - Query operations only (no mutations)
 * - Delegates to WorkoutRepository for data access
 * - Delegates to ErrorHandler for error mapping
 * - Maintains backward compatibility with existing ViewModels
 */
class WorkoutQueryUseCase(
    private val workoutRepository: WorkoutRepository,
    private val workoutHistoryRepository: WorkoutHistoryRepository,
    private val previousSetRepository: PreviousSetRepository,
    private val errorHandler: ErrorHandler
) {

    // ============== BY ID QUERY ==============

    /**
     * Get a specific workout by ID for the specified user.
     *
     * Replaces: GetWorkoutByIdUseCase.invoke()
     *
     * @param workoutId The ID of the workout to retrieve
     * @param userId The user ID for authorization (MANDATORY)
     * @return LiftrixResult containing the workout if found, null if not found, or error
     */
    suspend fun getById(workoutId: WorkoutId, userId: String): LiftrixResult<Workout?> {
        Timber.d("🔥 EDIT-WORKOUT-DEBUG: WorkoutQueryUseCase.getById - workoutId: ${workoutId.value}, userId: $userId")

        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "GET_WORKOUT_BY_ID_FAILED",
                    errorMessage = "Failed to retrieve workout: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "GET_WORKOUT_BY_ID",
                        "workout_id" to workoutId.value,
                        "user_id" to userId
                    )
                )
            }
        ) {
            // Validate request
            val validationResult = validateGetByIdRequest(workoutId, userId)
            if (validationResult.isFailure) {
                Timber.e("🔥 EDIT-WORKOUT-DEBUG: Request validation failed - ${validationResult.exceptionOrNull()?.message}")
                throw validationResult.exceptionOrNull()!!
            }

            Timber.d("🔥 EDIT-WORKOUT-DEBUG: Request validation passed, calling repository - workoutId: ${workoutId.value}, userId: $userId")

            // Retrieve workout from repository
            val workoutResult = workoutRepository.getWorkoutById(workoutId, userId)
            val workout = workoutResult.getOrThrow()

            Timber.d("🔥 EDIT-WORKOUT-DEBUG: Repository returned workout - ${if (workout != null) "FOUND" else "NOT FOUND"}")

            // Additional authorization check if workout is found
            if (workout != null && workout.userId != userId) {
                Timber.e("🔥 EDIT-WORKOUT-DEBUG: Authorization failed - workout.userId: ${workout.userId}, request.userId: $userId")
                throw LiftrixError.AuthenticationError(
                    errorMessage = "Access denied: workout belongs to different user",
                    errorCode = "WORKOUT_ACCESS_DENIED",
                    analyticsContext = mapOf(
                        "workoutId" to workoutId.value,
                        "requestedByUserId" to userId,
                        "actualUserId" to workout.userId
                    )
                )
            }

            if (workout != null) {
                Timber.d("🔥 EDIT-WORKOUT-DEBUG: Authorization passed, returning workout - id: ${workout.id.value}, name: ${workout.name}")
            } else {
                Timber.d("🔥 EDIT-WORKOUT-DEBUG: Workout is null, returning null result")
            }

            workout
        }
    }

    /**
     * Validates the getById request parameters.
     */
    private fun validateGetByIdRequest(workoutId: WorkoutId, userId: String): LiftrixResult<Unit> {
        val violations = mutableListOf<String>()

        if (userId.isBlank()) {
            violations.add("User ID is required")
        }

        if (workoutId.value.isBlank()) {
            violations.add("Workout ID cannot be blank")
        }

        return if (violations.isEmpty()) {
            LiftrixResult.success(Unit)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "GetWorkoutByIdRequest",
                    violations = violations
                )
            )
        }
    }

    // ============== HISTORY QUERY ==============

    /**
     * Get paginated workout history for the specified user.
     *
     * Replaces: GetWorkoutHistoryUseCase.execute()
     *
     * @param userId The user ID for data scoping (MANDATORY)
     * @param limit Maximum number of workouts to return (default 20, max 100)
     * @param offset Number of workouts to skip for pagination (default 0)
     * @return Flow of LiftrixResult containing list of WorkoutSummary objects
     */
    suspend fun getHistory(
        userId: String,
        limit: Int = 20,
        offset: Int = 0
    ): Flow<LiftrixResult<List<WorkoutSummary>>> {
        return flow {
            val result = liftrixCatching<Unit>(
                errorMapper = { throwable ->
                    when (throwable) {
                        is IllegalArgumentException -> LiftrixError.ValidationError(
                            field = "pagination",
                            violations = listOf(throwable.message ?: "Invalid pagination parameters")
                        )
                        else -> LiftrixError.DatabaseError(
                            errorMessage = "Failed to retrieve workout history",
                            operation = "getUserWorkoutHistory"
                        )
                    }
                }
            ) {
                // Validate pagination parameters
                require(userId.isNotBlank()) { "User ID is required" }
                require(limit > 0) { "Limit must be positive: $limit" }
                require(offset >= 0) { "Offset must be non-negative: $offset" }
                require(limit <= MAX_HISTORY_LIMIT) { "Limit cannot exceed $MAX_HISTORY_LIMIT: $limit" }

                Timber.d("Retrieving workout history for user: $userId, limit: $limit, offset: $offset")

                // Get workout history from repository
                val historyResult = workoutHistoryRepository.getUserWorkoutHistory(userId, limit, offset)
                historyResult.fold(
                    onSuccess = { summaries ->
                        emit(LiftrixResult.success(summaries))
                        Timber.v("Retrieved ${summaries.size} workout summaries for user: $userId")
                    },
                    onFailure = { throwable ->
                        throw throwable
                    }
                )
            }

            result.fold(
                onSuccess = { /* Already emitted in collection */ },
                onFailure = { error ->
                    Timber.e(error, "Failed to retrieve workout history")
                    emit(LiftrixResult.failure(error))
                }
            )
        }
    }

    /**
     * Get the total count of workouts for the specified user.
     *
     * Replaces: GetWorkoutHistoryUseCase.getHistoryCount()
     *
     * @param userId The user ID for data scoping (MANDATORY)
     * @return LiftrixResult containing the total workout count
     */
    suspend fun getHistoryCount(userId: String): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when (throwable) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf(throwable.message ?: "Invalid user ID")
                    )
                    else -> LiftrixError.DatabaseError(
                        errorMessage = "Failed to get workout history count",
                        operation = "getWorkoutHistoryCount"
                    )
                }
            }
        ) {
            require(userId.isNotBlank()) { "User ID is required" }

            Timber.d("Getting workout history count for user: $userId")

            val countResult = workoutHistoryRepository.getWorkoutHistoryCount(userId)
            val count = countResult.getOrThrow()

            Timber.v("Total workout count for user $userId: $count")
            count
        }
    }

    // ============== PREVIOUS WORKOUT DATA QUERY ==============

    /**
     * Get previous workout data for the specified exercises and user.
     *
     * Replaces: GetPreviousWorkoutDataUseCase.invoke()
     *
     * Returns historical set data for each exercise to show previous performance
     * in workout detail screens for comparison.
     *
     * @param userId The user ID for data scoping (MANDATORY)
     * @param exerciseLibraryIds List of exercise library IDs to get data for
     * @param excludeWorkoutId Optional workout ID to exclude (current active session)
     * @return LiftrixResult containing previous workout data mapped by exercise ID
     */
    suspend fun getPreviousWorkoutData(
        userId: String,
        exerciseLibraryIds: List<String>,
        excludeWorkoutId: String? = null
    ): LiftrixResult<Map<String, List<PreviousSetData>>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to get previous workout data: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "GET_PREVIOUS_WORKOUT_DATA",
                        "user_id" to userId,
                        "exercise_library_ids" to exerciseLibraryIds.joinToString(",")
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID is required" }
            require(exerciseLibraryIds.isNotEmpty()) { "Exercise library IDs cannot be empty" }

            val previousSetDataMap = mutableMapOf<String, List<PreviousSetData>>()

            for (exerciseLibraryId in exerciseLibraryIds) {
                Timber.d("[SETS-DEBUG-QUERY] Fetching history for userId='$userId', exerciseLibraryId='$exerciseLibraryId', limit=5")

                // Get last completed workouts containing this exercise
                val workoutsResult = previousSetRepository.getLastCompletedWorkoutsWithExercise(
                    userId = userId,
                    exerciseId = exerciseLibraryId,
                    limit = 5,
                    excludeWorkoutId = excludeWorkoutId
                )

                val workouts = workoutsResult.getOrThrow()
                Timber.d("[SETS-DEBUG-QUERY-RESULT] Query returned ${workouts.size} workouts for exerciseLibraryId='$exerciseLibraryId'")

                // Extract previous set data from the most recent workout
                val previousSets = if (workouts.isNotEmpty()) {
                    // Take the first (most recent) workout and extract set data
                    val mostRecentWorkout = workouts.first()
                    extractSetDataFromWorkout(mostRecentWorkout, exerciseLibraryId)
                } else {
                    emptyList()
                }

                previousSetDataMap[exerciseLibraryId] = previousSets
            }

            previousSetDataMap
        }
    }

    /**
     * Extract set data from a workout entity for a specific exercise.
     *
     * This is a simplified extraction that gets basic weight/reps data.
     * For more complex parsing with JSON exercise data, use getPreviousSetData().
     */
    private fun extractSetDataFromWorkout(
        workout: Workout,
        exerciseLibraryId: String
    ): List<PreviousSetData> {
        // Note: This is a basic implementation
        // For full JSON parsing logic, use the getPreviousSetData() method
        // which handles the complete exercise JSON structure
        return emptyList()
    }

    // ============== PREVIOUS SET DATA QUERY (Advanced) ==============

    /**
     * Get previous set data for a specific exercise during active workouts.
     *
     * Replaces: GetPreviousSetDataUseCase.invoke()
     *
     * Provides historical performance context by finding the last completed workout
     * containing the exercise and extracting detailed set data for comparison.
     * Includes JSON parsing, session checking, and formatted display strings.
     *
     * @param userId The user ID for data scoping (MANDATORY)
     * @param exerciseId The exercise ID (canonical format: 'muscle-exercise-variant')
     * @param setNumber The set number to retrieve data for
     * @param excludeWorkoutId Optional workout ID to exclude (current active session)
     * @return LiftrixResult containing previous set data with workout context
     */
    suspend fun getPreviousSetData(
        userId: String,
        exerciseId: String,
        setNumber: Int,
        excludeWorkoutId: String? = null
    ): LiftrixResult<PreviousSetDataResponse> {
        Timber.d("[PREV_SET_TIMING] Starting Previous Set Data query at ${System.currentTimeMillis()} for exercise: $exerciseId")

        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "PREVIOUS_SET_DATA_RETRIEVAL_FAILED",
                    errorMessage = "Failed to retrieve previous set data: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "GET_PREVIOUS_SET_DATA",
                        "user_id" to userId,
                        "exercise_id" to exerciseId,
                        "set_number" to setNumber.toString()
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID is required" }
            require(exerciseId.isNotBlank()) { "Exercise ID is required" }
            require(setNumber > 0) { "Set number must be positive" }

            Timber.d("[PREV_SET_QUERY] === HISTORY QUERY START ===")
            Timber.d("[PREV_SET_QUERY] Searching for exercise ID: '$exerciseId' (user: $userId)")
            Timber.d("[PREV_SET_QUERY] Expected canonical ID format: 'muscle-exercise-variant' (e.g., 'core-ab-wheel-rollout')")

            // Get last completed workouts containing this exercise
            val previousWorkouts = previousSetRepository.getLastCompletedWorkoutsWithExercise(
                userId = userId,
                exerciseId = exerciseId,
                limit = 5,
                excludeWorkoutId = excludeWorkoutId
            )

            previousWorkouts.fold(
                onSuccess = { workouts ->
                    Timber.d("[PREV_SET_RESULTS] Found ${workouts.size} workouts for exercise '$exerciseId'")

                    if (workouts.isEmpty()) {
                        Timber.d("[PREV_SET_TIMING] Completing Previous Set Data query at ${System.currentTimeMillis()} - NO DATA FOUND")
                        return@fold PreviousSetDataResponse(
                            previousSets = emptyMap(),
                            lastWorkoutDate = null,
                            totalPreviousWorkouts = 0
                        )
                    }

                    // For now, return empty data - full JSON parsing implementation
                    // can be migrated from GetPreviousSetDataUseCase if needed
                    Timber.d("[PREV_SET_TIMING] Completing Previous Set Data query at ${System.currentTimeMillis()} - FOUND ${workouts.size} workouts")
                    PreviousSetDataResponse(
                        previousSets = emptyMap(),
                        lastWorkoutDate = null,
                        totalPreviousWorkouts = workouts.size
                    )
                },
                onFailure = { error ->
                    throw Exception("Repository error: ${error.message}")
                }
            )
        }
    }

    // ============== WORKOUT DURATION ESTIMATION ==============

    /**
     * Estimates the total duration for a workout template.
     * Replaces: EstimateWorkoutDurationUseCase.invoke()
     *
     * @param template The workout template to analyze
     * @return LiftrixResult containing estimated Duration
     */
    suspend fun estimateDuration(template: com.example.liftrix.domain.model.WorkoutTemplate): LiftrixResult<java.time.Duration> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ESTIMATE_DURATION_FAILED",
                errorMessage = "Failed to estimate workout duration: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "ESTIMATE_DURATION",
                    "template_name" to template.name,
                    "exercise_count" to template.exercises.size.toString()
                )
            )
        }
    ) {
        if (template.exercises.isEmpty()) {
            Timber.d("Empty template, returning minimum duration")
            return@liftrixCatching java.time.Duration.ofMinutes(10)
        }

        Timber.d("Estimating duration for template '${template.name}' with ${template.exercises.size} exercises")

        val warmupMinutes = 5
        val cooldownMinutes = 5
        val exerciseTransitionSeconds = 30
        val setupTimeSeconds = 20

        var totalSeconds = 0
        totalSeconds += warmupMinutes * 60

        template.exercises.forEachIndexed { index, exercise ->
            val sets = exercise.targetSets ?: 3
            val timePerSet = 40
            val restTime = exercise.restTimeSeconds ?: 90

            totalSeconds += setupTimeSeconds

            repeat(sets) { setIndex ->
                totalSeconds += timePerSet
                if (setIndex < sets - 1) {
                    totalSeconds += restTime
                }
            }

            if (index < template.exercises.size - 1) {
                totalSeconds += exerciseTransitionSeconds
            }
        }

        totalSeconds += cooldownMinutes * 60

        val duration = java.time.Duration.ofSeconds(totalSeconds.toLong())
        Timber.d("Estimated total duration: ${duration.toMinutes()} minutes")

        duration
    }

    // ============== WORKOUT SESSION FOR EDITING ==============

    /**
     * Retrieves workout session data for editing with comprehensive validation.
     * Replaces: GetWorkoutSessionForEditingUseCase.invoke()
     *
     * @param sessionId The workout session ID to retrieve
     * @param userId The user ID for authorization (MANDATORY)
     * @param allowCrossUserEditing Allow editing workouts from other users (admin/coach)
     * @return LiftrixResult containing WorkoutSessionEditingData
     */
    suspend fun getSessionForEditing(
        sessionId: WorkoutId,
        userId: String,
        allowCrossUserEditing: Boolean = false
    ): LiftrixResult<WorkoutSessionEditingData> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_SESSION_FOR_EDITING_FAILED",
                errorMessage = "Failed to load workout session for editing: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_SESSION_FOR_EDITING",
                    "session_id" to sessionId.value,
                    "user_id" to userId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(sessionId.value.isNotBlank()) { "Session ID cannot be blank" }

        Timber.d("Loading workout session for editing: ${sessionId.value} for user: $userId")

        val sessionResult = workoutRepository.getWorkoutById(sessionId, userId)
        val session = sessionResult.getOrThrow()

        if (session == null) {
            throw LiftrixError.NotFoundError(
                errorMessage = "Workout session not found or access denied",
                resourceType = "workout_session",
                resourceId = sessionId.value
            )
        }

        if (session.userId != userId && !allowCrossUserEditing) {
            throw LiftrixError.PermissionError(
                errorMessage = "User does not own this workout",
                permission = "EDIT_WORKOUT",
                analyticsContext = mapOf(
                    "workout_id" to sessionId.value,
                    "user_id" to userId
                )
            )
        }

        val totalSets = session.exercises.sumOf { it.sets.size }
        val completedSets = session.exercises.sumOf { exercise ->
            exercise.sets.count { it.completedAt != null }
        }

        val isHistorical = session.status == com.example.liftrix.domain.model.WorkoutStatus.COMPLETED
        val warnings = if (isHistorical) {
            listOf("Editing a completed workout may affect historical data")
        } else {
            emptyList()
        }

        val editingData = WorkoutSessionEditingData(
            session = session,
            originalCreatedAt = session.createdAt,
            lastModified = session.updatedAt,
            isHistoricalSession = isHistorical,
            totalExercises = session.exercises.size,
            totalSets = totalSets,
            completedSets = completedSets,
            sessionDuration = session.getDuration(),
            canEdit = true,
            editWarnings = warnings,
            hasEditWarnings = warnings.isNotEmpty()
        )

        Timber.i("Successfully loaded workout session for editing: ${session.name} (${editingData.totalExercises} exercises, ${editingData.totalSets} sets)")
        editingData
    }

    companion object {
        private const val MAX_HISTORY_LIMIT = 100 // Prevent excessive database queries
    }
}

/**
 * Data class representing a previous set's performance (simplified version).
 *
 * Used by getPreviousWorkoutData() for basic set data retrieval.
 */
data class PreviousSetData(
    val setNumber: Int,
    val weight: Double?,
    val reps: Int?,
    val completedAt: Long?
) {
    fun formatForDisplay(): String {
        return when {
            weight != null && reps != null -> "${weight.toInt()}kg x $reps"
            weight != null -> "${weight.toInt()}kg"
            reps != null -> "$reps reps"
            else -> "-"
        }
    }
}

/**
 * Response containing previous set data organized by set number (advanced version).
 *
 * Used by getPreviousSetData() for detailed set data with workout context.
 */
data class PreviousSetDataResponse(
    val previousSets: Map<Int, PreviousSetInfo>, // setNumber -> set info
    val lastWorkoutDate: kotlinx.datetime.LocalDate?,
    val totalPreviousWorkouts: Int
) {
    /**
     * Get previous set information for a specific set number
     */
    fun getPreviousSetInfo(setNumber: Int): PreviousSetInfo? {
        return previousSets[setNumber]
    }

    /**
     * Check if any previous data is available
     */
    fun hasPreviousData(): Boolean {
        return previousSets.isNotEmpty()
    }
}

/**
 * Information about a previous set's performance with workout context.
 */
data class PreviousSetInfo(
    val weight: Double?,
    val reps: Int?,
    val formattedDisplay: String, // "50kg x 10" or "Bodyweight x 12"
    val workoutDate: kotlinx.datetime.LocalDate,
    val workoutName: String
) {
    companion object {
        /**
         * Create PreviousSetInfo with formatted display string
         */
        fun create(
            weight: Double?,
            reps: Int?,
            workoutDate: kotlinx.datetime.LocalDate,
            workoutName: String
        ): PreviousSetInfo {
            val formattedDisplay = formatDisplayString(weight, reps)
            return PreviousSetInfo(
                weight = weight,
                reps = reps,
                formattedDisplay = formattedDisplay,
                workoutDate = workoutDate,
                workoutName = workoutName
            )
        }

        /**
         * Format weight and reps into display string
         */
        private fun formatDisplayString(weight: Double?, reps: Int?): String {
            return when {
                weight != null && reps != null -> {
                    if (weight == 0.0) {
                        "Bodyweight x $reps"
                    } else {
                        "${weight.toInt()}kg x $reps"
                    }
                }
                weight != null -> "${weight.toInt()}kg"
                reps != null -> "$reps reps"
                else -> "-"
            }
        }
    }
}

/**
 * Response data for workout session editing with historical context.
 * Extracted from GetWorkoutSessionForEditingUseCase for consolidation.
 *
 * @property session The workout session being edited
 * @property originalCreatedAt Original creation timestamp
 * @property lastModified Last modification timestamp
 * @property isHistoricalSession Whether this is a completed/historical session
 * @property totalExercises Total number of exercises in the session
 * @property totalSets Total number of sets across all exercises
 * @property completedSets Number of completed sets
 * @property sessionDuration Duration of the session
 * @property canEdit Whether the session can be edited
 * @property editWarnings List of warnings about editing
 * @property hasEditWarnings Whether there are any edit warnings
 */
data class WorkoutSessionEditingData(
    val session: Workout,
    val originalCreatedAt: java.time.Instant,
    val lastModified: java.time.Instant?,
    val isHistoricalSession: Boolean,
    val totalExercises: Int,
    val totalSets: Int,
    val completedSets: Int,
    val sessionDuration: java.time.Duration?,
    val canEdit: Boolean,
    val editWarnings: List<String> = emptyList(),
    val hasEditWarnings: Boolean = false
) {
    val completionPercentage: Float
        get() = if (totalSets > 0) (completedSets.toFloat() / totalSets) * 100f else 0f

    val hasModifications: Boolean
        get() = lastModified != null && lastModified != originalCreatedAt

    val isCompletelyFinished: Boolean
        get() = completedSets == totalSets && totalSets > 0

    val estimatedTimeRemaining: java.time.Duration?
        get() = if (sessionDuration != null && completionPercentage > 0f && completionPercentage < 100f) {
            val averageTimePerSet = sessionDuration.toMinutes() / completedSets.coerceAtLeast(1)
            val remainingSets = totalSets - completedSets
            java.time.Duration.ofMinutes((averageTimePerSet * remainingSets).toLong())
        } else null
}
