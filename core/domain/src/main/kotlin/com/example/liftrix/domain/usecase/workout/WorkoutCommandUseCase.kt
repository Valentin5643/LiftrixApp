package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.example.liftrix.domain.util.DomainLogger as Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.Result

/**
 * Consolidated use case for all workout command (mutation) operations.
 *
 * Consolidates the following use cases:
 * - UpdateWorkoutSessionUseCase (125 lines)
 * - AddExerciseToWorkoutUseCase (310 lines)
 *
 * Responsibilities:
 * - Update workout session data
 * - Add exercises to existing workouts
 * - Validate workout data integrity
 * - Maintain workout timestamps
 * - Ensure user scoping for security
 *
 * Business Rules:
 * - All operations enforce user_id scoping
 * - Session data validated before saving
 * - Timestamps preserved for creation, updated for modifications
 * - Exercise library references validated
 * - Default set structures created for new exercises
 *
 * Architecture:
 * - Command operations only (mutations)
 * - Delegates to WorkoutRepository for data persistence
 * - Delegates to ExerciseRepository for exercise validation
 * - Uses GetCurrentUserIdUseCase for authentication
 * - Maintains backward compatibility with existing ViewModels
 */
class WorkoutCommandUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val authQueryUseCase: AuthQueryUseCase
) {

    private suspend fun requireAuthenticatedUserId(): String {
        val userId = authQueryUseCase(waitForAuth = false).getOrThrow().value
        if (userId.isBlank()) {
            throw LiftrixError.AuthenticationError(
                errorMessage = "User not authenticated",
                errorCode = "AUTH_REQUIRED"
            )
        }
        return userId
    }

    // ============== UPDATE SESSION ==============

    /**
     * Update an existing workout session.
     *
     * Replaces: UpdateWorkoutSessionUseCase.invoke()
     *
     * Updates completed workout session records with:
     * - Direct database record modification with timestamp updates
     * - Data validation preventing corruption of historical records
     * - User scoping for security and data isolation
     * - Firebase sync compatibility for edited historical data
     *
     * @param updatedSession The updated workout data
     * @param originalCreatedAt Optional original creation timestamp to preserve
     * @return LiftrixResult with updated workout or error
     */
    suspend fun updateSession(
        updatedSession: Workout,
        originalCreatedAt: Instant? = null
    ): LiftrixResult<Workout> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                is IllegalStateException -> LiftrixError.AuthenticationError(
                    errorMessage = "User not authenticated",
                    errorCode = "AUTH_REQUIRED",
                    analyticsContext = mapOf(
                        "operation" to "UPDATE_WORKOUT_SESSION",
                        "workout_id" to updatedSession.id.value
                    )
                )
                is SecurityException -> LiftrixError.AuthenticationError(
                    errorMessage = throwable.message ?: "Authorization failed",
                    errorCode = "UNAUTHORIZED_UPDATE",
                    analyticsContext = mapOf(
                        "operation" to "UPDATE_WORKOUT_SESSION",
                        "workout_id" to updatedSession.id.value,
                        "user_id" to updatedSession.userId
                    )
                )
                is IllegalArgumentException -> LiftrixError.ValidationError(
                    field = "workout_session",
                    violations = listOf(throwable.message ?: "Validation failed"),
                    analyticsContext = mapOf(
                        "operation" to "UPDATE_WORKOUT_SESSION",
                        "workout_id" to updatedSession.id.value
                    )
                )
                else -> LiftrixError.BusinessLogicError(
                    code = "UPDATE_SESSION_FAILED",
                    errorMessage = "Failed to update workout session: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "UPDATE_WORKOUT_SESSION",
                        "workout_id" to updatedSession.id.value,
                        "user_id" to updatedSession.userId
                    )
                )
            }
        }
    ) {
        // Get current user ID for security validation
        val currentUserId = requireAuthenticatedUserId()

        // Validate that the user owns this session
        if (updatedSession.userId != currentUserId) {
            throw SecurityException("Cannot update session belonging to another user")
        }

        // Validate session data integrity
        val validationResult = validateSessionData(updatedSession)
        if (validationResult != null) {
            throw validationResult
        }

        // Ensure we preserve original creation data while updating modification time
        val sessionToUpdate = updatedSession.copy(
            updatedAt = Instant.now(),
            // Preserve original creation timestamp and user ID
            createdAt = originalCreatedAt ?: updatedSession.createdAt,
            userId = currentUserId
        )

        Timber.d("Updating workout session for user: $currentUserId")

        val updateResult = workoutRepository.updateWorkout(sessionToUpdate)
        val updatedWorkout = updateResult.getOrThrow()

        Timber.i("Successfully updated workout session")
        updatedWorkout
    }

    /**
     * Validates session data to prevent corruption.
     */
    private fun validateSessionData(session: Workout): Exception? {
        // Validate session has required fields
        if (session.name.isBlank()) {
            return IllegalArgumentException("Session name cannot be empty")
        }

        // Validate exercises and sets
        if (session.exercises.isEmpty()) {
            return IllegalArgumentException("Session must contain at least one exercise")
        }

        // Validate each exercise has valid data
        session.exercises.forEach { exercise ->
            if (exercise.sets.isEmpty()) {
                return IllegalArgumentException("Exercise must have at least one set")
            }

            // Validate set data
            exercise.sets.forEach { set ->
                if (set.reps?.count?.let { it < 0 } == true) {
                    return IllegalArgumentException("Reps cannot be negative")
                }

                if (set.weight?.toPounds()?.let { it < 0 } == true) {
                    return IllegalArgumentException("Weight cannot be negative")
                }
            }
        }

        // Validate session timing if present
        val startTime = session.startTime
        val endTime = session.endTime
        if (startTime != null && endTime != null) {
            if (endTime.isBefore(startTime)) {
                return IllegalArgumentException("Session end time cannot be before start time")
            }
        }

        return null // No validation errors
    }

    // ============== ADD EXERCISE ==============

    /**
     * Add a single exercise to an existing workout by exercise library ID.
     *
     * Replaces: AddExerciseToWorkoutUseCase.invoke()
     *
     * Handles adding exercises to stored workout records with:
     * - Exercise library reference validation
     * - Default set structure creation
     * - Smart defaults based on exercise type
     * - Workout update timestamp maintenance
     * - User scoping for security
     *
     * @param workoutId The ID of the workout to add the exercise to
     * @param exerciseLibraryId The ID of the exercise from the exercise library
     * @param initialSets Number of blank sets to create (default 3)
     * @return LiftrixResult with updated workout or error
     */
    suspend fun addExercise(
        workoutId: WorkoutId,
        exerciseLibraryId: String,
        initialSets: Int = 3
    ): LiftrixResult<Workout> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "ADD_EXERCISE_TO_WORKOUT_FAILED",
                    errorMessage = "Failed to add exercise to workout: ${throwable.message}",
                    analyticsContext = mapOf(
                        "workout_id" to workoutId.value,
                        "exercise_library_id" to exerciseLibraryId,
                        "operation" to "ADD_EXERCISE_TO_WORKOUT"
                    )
                )
            }
        }
    ) {
        val userId = requireAuthenticatedUserId()

        // Validate inputs
        if (initialSets < 1 || initialSets > 10) {
            throw IllegalArgumentException("Initial sets must be between 1 and 10")
        }

        val workoutResult = workoutRepository.getWorkoutById(workoutId, userId)
        val existingWorkout = workoutResult.fold(
            onSuccess = { workout ->
                workout ?: throw IllegalArgumentException("Workout not found or access denied")
            },
            onFailure = { error ->
                throw Exception("Failed to retrieve workout: ${error.message}")
            }
        )

        val existingExercise = existingWorkout.exercises.find {
            it.libraryExercise.id == exerciseLibraryId
        }
        if (existingExercise != null) {
            throw IllegalArgumentException("Exercise already exists in this workout")
        }

        val exercisesResult = exerciseRepository.getAllExercises().first()
        val allExercises = exercisesResult.fold(
            onSuccess = { it },
            onFailure = { error ->
                throw Exception("Failed to load exercise library: ${error.message}")
            }
        )

        val libraryExercise = allExercises.find { it.id == exerciseLibraryId }
            ?: throw IllegalArgumentException("Exercise not found in library")

        val newExercise = createExerciseWithSets(
            libraryExercise = libraryExercise,
            workoutId = workoutId,
            orderIndex = existingWorkout.exercises.size,
            initialSets = initialSets
        )

        // Update workout with new exercise
        val updatedWorkout = existingWorkout.copy(
            exercises = existingWorkout.exercises + newExercise,
            updatedAt = Instant.now()
        )

        // Save updated workout
        val saveResult = workoutRepository.updateWorkout(updatedWorkout)
        val savedWorkout = saveResult.fold(
            onSuccess = { it },
            onFailure = { error ->
                throw Exception("Failed to save workout: ${error.message}")
            }
        )

        savedWorkout
    }

    /**
     * Add multiple exercises to an existing workout.
     *
     * Replaces: AddExerciseToWorkoutUseCase.invokeMultiple()
     *
     * @param workoutId The ID of the workout to add exercises to
     * @param exerciseLibraryIds List of exercise library IDs to add
     * @param initialSets Number of blank sets to create for each exercise (default 3)
     * @return LiftrixResult with updated workout or error
     */
    suspend fun addMultipleExercises(
        workoutId: WorkoutId,
        exerciseLibraryIds: List<String>,
        initialSets: Int = 3
    ): LiftrixResult<Workout> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "ADD_MULTIPLE_EXERCISES_FAILED",
                    errorMessage = "Failed to add exercises to workout: ${throwable.message}",
                    analyticsContext = mapOf(
                        "workout_id" to workoutId.value,
                        "exercise_count" to exerciseLibraryIds.size.toString(),
                        "operation" to "ADD_MULTIPLE_EXERCISES_TO_WORKOUT"
                    )
                )
            }
        }
    ) {
        if (exerciseLibraryIds.isEmpty()) {
            throw IllegalArgumentException("No exercises to add")
        }

        val userId = requireAuthenticatedUserId()

        // Validate inputs
        if (initialSets < 1 || initialSets > 10) {
            throw IllegalArgumentException("Initial sets must be between 1 and 10")
        }

        val currentWorkout = workoutRepository.getWorkoutById(workoutId, userId).fold(
            onSuccess = { it ?: throw IllegalArgumentException("Workout not found") },
            onFailure = { error -> throw Exception("Failed to retrieve workout: ${error.message}") }
        )

        val exercisesResult = exerciseRepository.getAllExercises().first()
        val allExercises = exercisesResult.fold(
            onSuccess = { it },
            onFailure = { error ->
                throw Exception("Failed to load exercise library: ${error.message}")
            }
        )

        // Build a lookup map for O(1) exercise library access
        val exerciseLibraryMap = allExercises.associateBy { it.id }

        // Check for existing exercises to avoid duplicates
        val existingExerciseIds = currentWorkout.exercises.map { it.libraryExercise.id }.toSet()
        val uniqueNewExerciseIds = exerciseLibraryIds.filter { it !in existingExerciseIds }

        if (uniqueNewExerciseIds.isEmpty()) {
            return@liftrixCatching currentWorkout
        }

        val newExercises = withContext(Dispatchers.Default) {
            uniqueNewExerciseIds.mapIndexed { index, exerciseId ->
                async {
                    val libraryExercise = exerciseLibraryMap[exerciseId]
                        ?: throw IllegalArgumentException("Exercise $exerciseId not found in library")

                    createExerciseWithSets(
                        libraryExercise = libraryExercise,
                        workoutId = workoutId,
                        orderIndex = currentWorkout.exercises.size + index,
                        initialSets = initialSets
                    )
                }
            }.awaitAll()
        }

        val updatedWorkout = currentWorkout.copy(
            exercises = currentWorkout.exercises + newExercises,
            updatedAt = Instant.now()
        )

        val saveResult = workoutRepository.updateWorkout(updatedWorkout)
        val savedWorkout = saveResult.fold(
            onSuccess = { it },
            onFailure = { error ->
                throw Exception("Failed to save workout: ${error.message}")
            }
        )

        savedWorkout
    }

    /**
     * Creates an exercise with default sets.
     */
    private fun createExerciseWithSets(
        libraryExercise: ExerciseLibrary,
        workoutId: WorkoutId,
        orderIndex: Int,
        initialSets: Int
    ): Exercise {
        val defaultSets = (1..initialSets).map { setNumber ->
            // Apply smart defaults based on exercise type
            val defaultReps = when {
                libraryExercise.name.contains("plank", ignoreCase = true) ||
                libraryExercise.name.contains("hold", ignoreCase = true) ||
                libraryExercise.primaryMuscleGroup == ExerciseCategory.CORE -> null
                libraryExercise.name.contains("run", ignoreCase = true) ||
                libraryExercise.name.contains("walk", ignoreCase = true) ||
                libraryExercise.name.contains("cycle", ignoreCase = true) -> null
                else -> 10
            }

            val defaultTime = when {
                (libraryExercise.name.contains("plank", ignoreCase = true) ||
                 libraryExercise.name.contains("hold", ignoreCase = true) ||
                 libraryExercise.primaryMuscleGroup == ExerciseCategory.CORE) && defaultReps == null -> {
                    java.time.Duration.ofSeconds(30)
                }
                else -> null
            }

            val defaultDistance = when {
                (libraryExercise.name.contains("run", ignoreCase = true) ||
                 libraryExercise.name.contains("walk", ignoreCase = true) ||
                 libraryExercise.name.contains("cycle", ignoreCase = true)) &&
                 defaultReps == null && defaultTime == null -> {
                    Distance(1000.0f)
                }
                else -> null
            }

            ExerciseSet(
                id = ExerciseSetId(UUID.randomUUID().toString()),
                setNumber = setNumber,
                reps = defaultReps?.let { Reps(it) },
                weight = null,
                time = defaultTime,
                distance = defaultDistance,
                completedAt = null
            )
        }

        return Exercise(
            id = ExerciseId(UUID.randomUUID().toString()),
            workoutId = workoutId,
            libraryExercise = libraryExercise,
            orderIndex = orderIndex,
            targetSets = initialSets,
            targetReps = null,
            targetWeight = null,
            targetTime = null,
            targetDistance = null,
            sets = defaultSets,
            notes = null,
            createdAt = Instant.now()
        )
    }

    /**
     * Add a custom exercise (not from library) to an existing workout.
     *
     * Replaces: AddExerciseToWorkoutUseCase.invokeCustom()
     *
     * @param workoutId The ID of the workout to add the exercise to
     * @param exerciseName Name of the custom exercise
     * @param muscleGroup Primary muscle group
     * @param initialSets Number of blank sets to create (default 3)
     * @return LiftrixResult with updated workout or error
     */
    suspend fun addCustomExercise(
        workoutId: WorkoutId,
        exerciseName: String,
        muscleGroup: ExerciseCategory,
        initialSets: Int = 3
    ): LiftrixResult<Workout> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ADD_CUSTOM_EXERCISE_FAILED",
                errorMessage = "Failed to add custom exercise: ${throwable.message}",
                analyticsContext = mapOf(
                    "workout_id" to workoutId.value,
                    "exercise_name" to exerciseName,
                    "operation" to "ADD_CUSTOM_EXERCISE_TO_WORKOUT"
                )
            )
        }
    ) {
        if (exerciseName.isBlank()) {
            throw IllegalArgumentException("Exercise name cannot be blank")
        }

        if (exerciseName.length > 100) {
            throw IllegalArgumentException("Exercise name too long (max 100 characters)")
        }

        // Create a custom exercise library entry
        val customExerciseLibrary = ExerciseLibrary(
            id = UUID.randomUUID().toString(),
            name = exerciseName.trim(),
            primaryMuscleGroup = muscleGroup,
            equipment = Equipment.BODYWEIGHT_ONLY, // Default for custom exercises
            secondaryMuscleGroups = emptyList(),
            movementPattern = "Custom",
            difficultyLevel = 1, // Beginner level
            instructions = "Custom exercise created during workout editing",
            isCompound = false, // Default for custom exercises
            searchableTerms = listOf(exerciseName.trim().lowercase())
        )

        // Use the addExercise method with the custom exercise
        return addExercise(workoutId, customExerciseLibrary.id, initialSets)
    }

    // ============== SAVE WORKOUT ==============

    /**
     * Save an existing workout with updated data.
     *
     * Replaces: SaveWorkoutUseCase (if it existed)
     *
     * Persists workout changes with:
     * - Data validation to prevent corruption
     * - User scoping for security
     * - Timestamp updates for modification tracking
     * - Sync coordination for Firebase synchronization
     *
     * @param workout The workout to save
     * @return LiftrixResult with saved workout or error
     */
    suspend fun saveWorkout(workout: Workout): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "SAVE_WORKOUT_FAILED",
                    errorMessage = "Failed to save workout: ${throwable.message}",
                    analyticsContext = mapOf(
                        "workout_id" to workout.id.value,
                        "workout_name" to workout.name,
                        "user_id" to workout.userId,
                        "operation" to "SAVE_WORKOUT"
                    )
                )
            }
        }
    ) {
        val userId = requireAuthenticatedUserId()

        // Validate user ownership
        if (workout.userId != userId) {
            throw SecurityException("Cannot save workout belonging to another user")
        }

        // Validate workout data
        val validationError = validateSessionData(workout)
        if (validationError != null) {
            throw validationError
        }

        // Update modification timestamp
        val workoutToSave = workout.copy(updatedAt = Instant.now())

        // Save to repository
        val saveResult = workoutRepository.updateWorkout(workoutToSave)
        saveResult.fold(
            onSuccess = { Timber.i("Successfully saved workout: ${workout.name}") },
            onFailure = { error -> throw Exception("Repository save failed: ${error.message}") }
        )
    }

    // ============== CREATE WORKOUT ==============

    /**
     * Create a new workout with comprehensive validation.
     *
     * Replaces: CreateWorkoutUseCase.invoke()
     *
     * Creates workouts with:
     * - Business rule validation (unique name, active workout check)
     * - User scoping enforcement
     * - Automatic timestamp generation
     * - Status-based initialization (planned vs in-progress)
     *
     * @param request The workout creation request
     * @return LiftrixResult with created workout or error
     */
    suspend fun createWorkout(request: CreateWorkoutRequest): LiftrixResult<Workout> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "CREATE_WORKOUT_FAILED",
                    errorMessage = "Failed to create workout: ${throwable.message}",
                    analyticsContext = mapOf(
                        "workout_name" to request.name,
                        "user_id" to request.userId,
                        "status" to request.status.toString(),
                        "operation" to "CREATE_WORKOUT"
                    )
                )
            }
        }
    ) {
        val userId = requireAuthenticatedUserId()

        // Validate request matches authenticated user
        if (request.userId != userId) {
            throw SecurityException("Cannot create workout for another user")
        }

        // Validate workout name
        if (request.name.isBlank()) {
            throw IllegalArgumentException("Workout name is required")
        }

        if (request.name.length > Workout.MAX_NAME_LENGTH) {
            throw IllegalArgumentException("Workout name cannot exceed ${Workout.MAX_NAME_LENGTH} characters")
        }

        // Validate date
        if (request.date.isAfter(java.time.LocalDate.now().plusDays(1))) {
            throw IllegalArgumentException("Cannot create workouts more than 1 day in the future")
        }

        // Validate exercises for active workouts
        if (request.status == WorkoutStatus.IN_PROGRESS && request.exercises.isEmpty()) {
            throw IllegalArgumentException("Active workouts must have at least one exercise")
        }

        // Validate exercise count
        if (request.exercises.size > Workout.MAX_EXERCISES) {
            throw IllegalArgumentException("Cannot have more than ${Workout.MAX_EXERCISES} exercises")
        }

        // Validate notes length
        request.notes?.let { notes ->
            if (notes.length > Workout.MAX_NOTES_LENGTH) {
                throw IllegalArgumentException("Notes cannot exceed ${Workout.MAX_NOTES_LENGTH} characters")
            }
        }

        // Check for existing active workout if creating an active workout
        if (request.status == WorkoutStatus.IN_PROGRESS) {
            val activeWorkoutResult = workoutRepository.getActiveWorkout(userId)
            val existingActiveWorkout = activeWorkoutResult.fold(
                onSuccess = { it },
                onFailure = { error -> throw Exception("Failed to check for active workout: ${error.message}") }
            )

            if (existingActiveWorkout != null) {
                throw IllegalStateException("Cannot create active workout: user already has an active workout (ID: ${existingActiveWorkout.id.value})")
            }
        }

        // Check for duplicate workout name on the same date
        val workoutsOnDate = workoutRepository.getWorkoutsByDate(
            request.date.toKotlinxLocalDate(),
            userId
        ).first()

        val duplicateName = workoutsOnDate.any { workout ->
            workout.name.equals(request.name, ignoreCase = true)
        }

        if (duplicateName) {
            throw IllegalArgumentException("A workout with this name already exists on ${request.date}")
        }

        // Build workout
        val now = Instant.now()
        val newWorkout = Workout(
            userId = userId,
            id = WorkoutId.generate(),
            name = request.name,
            date = request.date,
            exercises = request.exercises,
            status = request.status,
            startTime = if (request.status == WorkoutStatus.IN_PROGRESS) now else null,
            endTime = null,
            notes = request.notes,
            templateId = request.templateId,
            createdAt = now,
            updatedAt = now
        )

        // Create workout in repository
        val createResult = workoutRepository.createWorkout(newWorkout)
        val createdWorkout = createResult.fold(
            onSuccess = { it },
            onFailure = { error -> throw Exception("Repository create failed: ${error.message}") }
        )

        Timber.i("Successfully created workout: ${createdWorkout.name} (ID: ${createdWorkout.id.value})")
        createdWorkout
    }

    // ============== CREATE WORKOUT WITH EXERCISES ==============

    /**
     * Create a new workout and populate it with exercises in a single transaction.
     *
     * Replaces: CreateWorkoutWithExercisesUseCase (deleted in previous session)
     *
     * Combines workout creation with exercise population for:
     * - Atomic workout + exercises creation
     * - Template-based workout instantiation
     * - Bulk exercise addition with default sets
     *
     * @param request The workout creation request with exercises
     * @return LiftrixResult with created and populated workout or error
     */
    suspend fun createWithExercises(
        request: CreateWorkoutWithExercisesRequest
    ): LiftrixResult<Workout> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CREATE_WORKOUT_WITH_EXERCISES_FAILED",
                errorMessage = "Failed to create workout with exercises: ${throwable.message}",
                analyticsContext = mapOf(
                    "workout_name" to request.name,
                    "user_id" to request.userId,
                    "exercise_count" to request.exerciseLibraryIds.size.toString(),
                    "operation" to "CREATE_WORKOUT_WITH_EXERCISES"
                )
            )
        }
    ) {
        // Validate exercise list
        if (request.exerciseLibraryIds.isEmpty()) {
            throw IllegalArgumentException("Must provide at least one exercise")
        }

        // Create base workout request
        val createWorkoutRequest = CreateWorkoutRequest(
            userId = request.userId,
            name = request.name,
            date = request.date,
            exercises = emptyList(), // Start with no exercises
            status = request.status,
            notes = request.notes,
            templateId = request.templateId
        )

        // Create the workout
        val createResult = createWorkout(createWorkoutRequest)
        val createdWorkout = createResult.fold(
            onSuccess = { it },
            onFailure = { error -> throw Exception("Workout creation failed: ${error.message}") }
        )

        // Add exercises to the newly created workout
        val populateResult = addMultipleExercises(
            workoutId = createdWorkout.id,
            exerciseLibraryIds = request.exerciseLibraryIds,
            initialSets = request.initialSetsPerExercise
        )

        val populatedWorkout = populateResult.fold(
            onSuccess = { it },
            onFailure = { error ->
                // Workout was created but exercise addition failed
                // Log warning but return the created workout
                Timber.w("Workout created but some exercises failed to add: ${error.message}")
                createdWorkout
            }
        )

        Timber.i("Successfully created workout with ${request.exerciseLibraryIds.size} exercises: ${populatedWorkout.name}")
        populatedWorkout
    }

    /**
     * Extension function to convert java.time.LocalDate to kotlinx.datetime.LocalDate
     */
    private fun java.time.LocalDate.toKotlinxLocalDate(): kotlinx.datetime.LocalDate {
        return kotlinx.datetime.LocalDate(this.year, this.monthValue, this.dayOfMonth)
    }
}

/**
 * Request data class for creating workouts with exercises.
 *
 * @property userId The ID of the user creating the workout
 * @property name The name of the workout
 * @property date The date for the workout
 * @property exerciseLibraryIds List of exercise library IDs to include
 * @property status Initial status of the workout (default: PLANNED)
 * @property notes Optional notes for the workout
 * @property templateId Optional ID of the template this workout is based on
 * @property initialSetsPerExercise Number of sets to create per exercise (default: 3)
 */
data class CreateWorkoutWithExercisesRequest(
    val userId: String,
    val name: String,
    val date: java.time.LocalDate,
    val exerciseLibraryIds: List<String>,
    val status: WorkoutStatus = WorkoutStatus.PLANNED,
    val notes: String? = null,
    val templateId: WorkoutId? = null,
    val initialSetsPerExercise: Int = 3
)

/**
 * Request data class for creating workouts.
 * Extracted from CreateWorkoutUseCase for consolidation.
 *
 * @property userId The ID of the user creating the workout
 * @property name The name of the workout
 * @property date The date for the workout
 * @property exercises List of exercises to include
 * @property status Initial status of the workout (default: PLANNED)
 * @property notes Optional notes for the workout
 * @property templateId Optional ID of the template this workout is based on
 */
data class CreateWorkoutRequest(
    val userId: String,
    val name: String,
    val date: java.time.LocalDate,
    val exercises: List<Exercise> = emptyList(),
    val status: WorkoutStatus = WorkoutStatus.PLANNED,
    val notes: String? = null,
    val templateId: WorkoutId? = null
)
