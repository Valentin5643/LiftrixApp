package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import kotlinx.coroutines.flow.first
import timber.log.Timber
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
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {

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
     * @return Result with updated workout or error
     */
    suspend fun updateSession(
        updatedSession: Workout,
        originalCreatedAt: Instant? = null
    ): Result<Workout> {
        return Result.runCatching {
            // Get current user ID for security validation
            val currentUserId = getCurrentUserIdUseCase()
            if (currentUserId.isNullOrBlank()) {
                throw IllegalStateException("User not authenticated")
            }

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
                if (set.reps != null && set.reps!!.count < 0) {
                    return IllegalArgumentException("Reps cannot be negative")
                }

                if (set.weight != null && set.weight!!.toPounds() < 0) {
                    return IllegalArgumentException("Weight cannot be negative")
                }
            }
        }

        // Validate session timing if present
        if (session.startTime != null && session.endTime != null) {
            if (session.endTime!!.isBefore(session.startTime)) {
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
            LiftrixError.BusinessLogicError(
                code = "ADD_EXERCISE_TO_WORKOUT_FAILED",
                errorMessage = "Failed to add exercise to workout: ${throwable.message}",
                analyticsContext = mapOf(
                    "workout_id" to workoutId.value,
                    "exercise_library_id" to exerciseLibraryId,
                    "operation" to "ADD_EXERCISE_TO_WORKOUT"
                )
            )
        }
    ) {
        Timber.d("🔥 ADD-EXERCISE-DEBUG: Adding exercise $exerciseLibraryId to workout ${workoutId.value}")

        val userId = getCurrentUserIdUseCase()
        if (userId.isNullOrBlank()) {
            throw IllegalStateException("User not authenticated")
        }

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

        // Create new exercise with smart default sets
        val newExerciseId = ExerciseId(UUID.randomUUID().toString())
        val defaultSets = (1..initialSets).map { setNumber ->
            // Apply smart defaults based on exercise type
            val defaultReps = when {
                // Time-based exercises (planks, holds, core) - no reps needed if time is provided
                libraryExercise.name.contains("plank", ignoreCase = true) ||
                libraryExercise.name.contains("hold", ignoreCase = true) ||
                libraryExercise.primaryMuscleGroup == ExerciseCategory.CORE -> null

                // Distance-based exercises - no reps needed if distance provided
                libraryExercise.name.contains("run", ignoreCase = true) ||
                libraryExercise.name.contains("walk", ignoreCase = true) ||
                libraryExercise.name.contains("cycle", ignoreCase = true) -> null

                // Standard strength exercises - default to 10 reps
                else -> 10
            }

            val defaultTime = when {
                // Time-based exercises get default time if no reps
                (libraryExercise.name.contains("plank", ignoreCase = true) ||
                 libraryExercise.name.contains("hold", ignoreCase = true) ||
                 libraryExercise.primaryMuscleGroup == ExerciseCategory.CORE) && defaultReps == null -> {
                    java.time.Duration.ofSeconds(30)
                }
                else -> null
            }

            val defaultDistance = when {
                // Distance-based exercises get default distance if no reps or time
                (libraryExercise.name.contains("run", ignoreCase = true) ||
                 libraryExercise.name.contains("walk", ignoreCase = true) ||
                 libraryExercise.name.contains("cycle", ignoreCase = true)) &&
                 defaultReps == null && defaultTime == null -> {
                    Distance(1000.0f) // 1000 meters default
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

        val newExercise = Exercise(
            id = newExerciseId,
            workoutId = workoutId,
            libraryExercise = libraryExercise,
            orderIndex = existingWorkout.exercises.size, // Add at end
            targetSets = initialSets,
            targetReps = null,
            targetWeight = null,
            targetTime = null,
            targetDistance = null,
            sets = defaultSets,
            notes = null,
            createdAt = Instant.now()
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

        Timber.i("🔥 ADD-EXERCISE-DEBUG: Successfully added exercise '${libraryExercise.name}' to workout '${savedWorkout.name}'")
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
            LiftrixError.BusinessLogicError(
                code = "ADD_MULTIPLE_EXERCISES_FAILED",
                errorMessage = "Failed to add exercises to workout: ${throwable.message}",
                analyticsContext = mapOf(
                    "workout_id" to workoutId.value,
                    "exercise_count" to exerciseLibraryIds.size.toString(),
                    "operation" to "ADD_MULTIPLE_EXERCISES_TO_WORKOUT"
                )
            )
        }
    ) {
        if (exerciseLibraryIds.isEmpty()) {
            throw IllegalArgumentException("No exercises to add")
        }

        val userId = getCurrentUserIdUseCase()
        if (userId.isNullOrBlank()) {
            throw IllegalStateException("User not authenticated")
        }

        // Add exercises one by one to maintain consistency
        var currentWorkout = workoutRepository.getWorkoutById(workoutId, userId).fold(
            onSuccess = { it ?: throw IllegalArgumentException("Workout not found") },
            onFailure = { error -> throw Exception("Failed to retrieve workout: ${error.message}") }
        )

        val successfullyAdded = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (exerciseId in exerciseLibraryIds) {
            try {
                val result = addExercise(currentWorkout.id, exerciseId, initialSets)
                currentWorkout = result.fold(
                    onSuccess = {
                        successfullyAdded.add(exerciseId)
                        it
                    },
                    onFailure = { error ->
                        errors.add("$exerciseId: ${error.message}")
                        currentWorkout // Keep current state
                    }
                )
            } catch (e: Exception) {
                errors.add("$exerciseId: ${e.message}")
            }
        }

        if (successfullyAdded.isEmpty()) {
            throw Exception("Failed to add any exercises: ${errors.joinToString("; ")}")
        }

        if (errors.isNotEmpty()) {
            Timber.w("🔥 ADD-EXERCISE-DEBUG: Some exercises failed to add: $errors")
        }

        Timber.i("🔥 ADD-EXERCISE-DEBUG: Successfully added ${successfullyAdded.size} exercises to workout")
        currentWorkout
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
}
