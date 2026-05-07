package com.example.liftrix.domain.usecase.session

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import com.example.liftrix.domain.repository.template.TemplateRepository
import com.example.liftrix.service.WorkoutSessionManagerPort
import kotlinx.coroutines.flow.first
import com.example.liftrix.domain.util.DomainLogger as Timber
import javax.inject.Inject

/**
 * Consolidated use case for workout session operations.
 * Part of Phase 4: Remaining Domains consolidation.
 *
 * **Replaces**:
 * - AddExerciseToSessionUseCase.kt
 * - ToggleSetCompletionUseCase.kt
 * - CreateTemplateFromSessionUseCase.kt (duplicate - also in template domain)
 *
 * Follows CQRS-lite pattern:
 * - Queries: validateTemplateCreation(), getCurrentSession()
 * - Commands: addExercise(), toggleSetCompletion(), createTemplateFromSession()
 *
 * **Session Management**: All operations work with UnifiedWorkoutSessionManager
 * **Error Handling**: All operations return Result<T> with proper error context
 * **State Management**: Proper state propagation and UI refresh triggering
 *
 * @property sessionManager Manager for unified workout session state
 * @property exerciseRepository Repository for exercise library operations
 * @property workoutTemplateRepository Repository for workout template operations
 */
class SessionOperationsUseCase @Inject constructor(
    private val sessionManager: WorkoutSessionManagerPort,
    private val exerciseRepository: ExerciseRepository,
    private val workoutTemplateRepository: TemplateRepository
) {

    // ==================== EXERCISE MANAGEMENT ====================

    /**
     * Adds an exercise to the current session by exercise ID.
     *
     * **Replaces**: AddExerciseToSessionUseCase.execute()
     *
     * This operation:
     * 1. Validates active session exists
     * 2. Checks exercise doesn't already exist in session
     * 3. Loads exercise from library
     * 4. Creates session exercise with proper defaults
     * 5. Adds to session with proper ordering
     *
     * @param exerciseId ID of the exercise to add from library
     * @return Result<Unit> indicating success or failure
     */
    suspend fun addExercise(exerciseId: ExerciseId): Result<Unit> {
        return try {
            // Get current session
            val currentSession = sessionManager.getCurrentSession()
            if (currentSession == null) {
                Timber.w("Cannot add exercise - no active session")
                return Result.failure(Exception("No active workout session"))
            }

            // Check if exercise already exists in session
            val existingExercise = currentSession.exercises.find { it.exerciseId == exerciseId }
            if (existingExercise != null) {
                Timber.w("Exercise already exists in session: ${exerciseId.value}")
                return Result.failure(Exception("Exercise already added to this workout"))
            }

            // Get exercise from library
            val exercisesResult = exerciseRepository.getAllExercises().first()
            val allExercises = exercisesResult.fold(
                onSuccess = { it },
                onFailure = { throwable ->
                    Timber.e("Failed to get exercises: ${throwable.message}")
                    return Result.failure(Exception("Failed to load exercises"))
                }
            )
            val libraryExercise = allExercises.find { it.id == exerciseId.value }

            if (libraryExercise == null) {
                Timber.w("Exercise not found in library: ${exerciseId.value}")
                return Result.failure(Exception("Exercise not found"))
            }

            // Create session exercise with proper defaults
            val sessionExercise = SessionExercise.createBlank(
                exerciseId = exerciseId,
                name = libraryExercise.name,
                category = libraryExercise.primaryMuscleGroup,
                primaryMuscle = libraryExercise.primaryMuscleGroup,
                equipment = libraryExercise.equipment,
                orderIndex = currentSession.exercises.size,
                initialSets = 3 // Default to 3 sets
            )

            // Add to session
            sessionManager.addExerciseToSession(sessionExercise)

            Timber.d("Exercise added to session: ${libraryExercise.name}")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to add exercise to session")
            Result.failure(e)
        }
    }

    /**
     * Adds a custom exercise to the current session.
     *
     * **Replaces**: AddExerciseToSessionUseCase.executeCustom()
     *
     * @param name Name of the custom exercise
     * @param category Exercise category
     * @param primaryMuscle Primary muscle group
     * @param initialSets Number of initial sets (default: 3)
     * @return Result<Unit> indicating success or failure
     */
    suspend fun addCustomExercise(
        name: String,
        category: ExerciseCategory,
        primaryMuscle: ExerciseCategory,
        initialSets: Int = 3
    ): Result<Unit> {
        return try {
            // Get current session
            val currentSession = sessionManager.getCurrentSession()
            if (currentSession == null) {
                Timber.w("Cannot add custom exercise - no active session")
                return Result.failure(Exception("No active workout session"))
            }

            // Validate input
            if (name.isBlank()) {
                return Result.failure(Exception("Exercise name cannot be blank"))
            }

            if (initialSets < 1 || initialSets > 10) {
                return Result.failure(Exception("Initial sets must be between 1 and 10"))
            }

            // Check if exercise with same name already exists in session
            val existingExercise = currentSession.exercises.find {
                it.name.equals(name, ignoreCase = true)
            }
            if (existingExercise != null) {
                Timber.w("Exercise with name '$name' already exists in session")
                return Result.failure(Exception("Exercise with this name already exists in workout"))
            }

            // Create session exercise
            val sessionExercise = SessionExercise.createBlank(
                exerciseId = ExerciseId.generate(),
                name = name.trim(),
                category = category,
                primaryMuscle = primaryMuscle,
                equipment = Equipment.BODYWEIGHT_ONLY, // Default for custom exercises
                orderIndex = currentSession.exercises.size,
                initialSets = initialSets
            )

            // Add to session
            sessionManager.addExerciseToSession(sessionExercise)

            Timber.d("Custom exercise added to session: $name")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to add custom exercise to session")
            Result.failure(e)
        }
    }

    /**
     * Adds multiple exercises to the current session.
     *
     * **Replaces**: AddExerciseToSessionUseCase.executeMultiple()
     *
     * @param exerciseIds List of exercise IDs to add
     * @return Result<Unit> indicating success or failure
     */
    suspend fun addMultipleExercises(exerciseIds: List<ExerciseId>): Result<Unit> {
        return try {
            val currentSession = sessionManager.getCurrentSession()
            if (currentSession == null) {
                Timber.w("Cannot add exercises - no active session")
                return Result.failure(Exception("No active workout session"))
            }

            if (exerciseIds.isEmpty()) {
                return Result.failure(Exception("No exercises to add"))
            }

            // Filter out exercises already in session
            val newExerciseIds = exerciseIds.filter { exerciseId ->
                !currentSession.exercises.any { it.exerciseId == exerciseId }
            }

            if (newExerciseIds.isEmpty()) {
                return Result.failure(Exception("All selected exercises are already in the workout"))
            }

            // Add each exercise
            var successCount = 0
            val errors = mutableListOf<String>()

            for (exerciseId in newExerciseIds) {
                val result = addExercise(exerciseId)
                if (result.isSuccess) {
                    successCount++
                } else {
                    errors.add("${exerciseId.value}: ${result.exceptionOrNull()?.message}")
                }
            }

            if (successCount > 0) {
                Timber.d("Added $successCount exercises to session")
                if (errors.isNotEmpty()) {
                    Timber.w("Some exercises failed to add: $errors")
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to add any exercises: ${errors.joinToString(", ")}"))
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to add multiple exercises to session")
            Result.failure(e)
        }
    }

    // ==================== SET COMPLETION ====================

    /**
     * Toggles completion status of a specific set.
     *
     * **Replaces**: ToggleSetCompletionUseCase.execute()
     *
     * This operation:
     * 1. Validates active session and set existence
     * 2. Toggles completion status
     * 3. Updates session state
     * 4. Triggers UI refresh
     *
     * @param exerciseId ID of the exercise containing the set
     * @param setNumber Number of the set to toggle (1-based)
     * @return Result<Unit> indicating success or failure
     */
    suspend fun toggleSetCompletion(
        exerciseId: ExerciseId,
        setNumber: Int
    ): Result<Unit> {
        return try {
            val session = sessionManager.getCurrentSession()
            if (session == null) {
                Timber.w("Cannot toggle set completion - no active session")
                return Result.failure(Exception("No active session"))
            }

            if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
                Timber.w("Cannot toggle set completion - session completed")
                return Result.failure(Exception("Session already completed"))
            }

            // Find the exercise
            val exercise = session.exercises.find { it.exerciseId == exerciseId }
            if (exercise == null) {
                Timber.w("Exercise not found in session: $exerciseId")
                return Result.failure(Exception("Exercise not found"))
            }

            // Find the set
            val set = exercise.sets.find { it.setNumber == setNumber }
            if (set == null) {
                Timber.w("Set not found in exercise: set $setNumber")
                return Result.failure(Exception("Set not found"))
            }

            // Toggle completion status
            val updatedSet = if (set.isCompleted()) {
                set.uncomplete()
            } else {
                set.complete()
            }

            // Update the session with the new set state
            sessionManager.updateSetInSession(exerciseId, setNumber, updatedSet)

            // Force a session state refresh to ensure UI updates
            sessionManager.refreshSessionState()

            Timber.d("Set completion toggled: exercise=$exerciseId, set=$setNumber, completed=${updatedSet.isCompleted()}")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle set completion: exercise=$exerciseId, set=$setNumber")
            Result.failure(e)
        }
    }

    /**
     * Marks a specific set as completed with actual values.
     *
     * **Replaces**: ToggleSetCompletionUseCase.markSetCompleted()
     *
     * @param exerciseId ID of the exercise containing the set
     * @param setNumber Number of the set to complete
     * @param actualReps Actual reps performed (optional)
     * @param actualWeight Actual weight used (optional)
     * @param actualTime Actual time taken (optional)
     * @param actualRpe Actual RPE rating (optional)
     * @return Result<Unit> indicating success or failure
     */
    suspend fun markSetCompleted(
        exerciseId: ExerciseId,
        setNumber: Int,
        actualReps: Int? = null,
        actualWeight: Weight? = null,
        actualTime: Long? = null,
        actualRpe: Int? = null
    ): Result<Unit> {
        return try {
            val session = sessionManager.getCurrentSession()
            if (session == null) {
                Timber.w("Cannot mark set completed - no active session")
                return Result.failure(Exception("No active session"))
            }

            if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
                Timber.w("Cannot mark set completed - session completed")
                return Result.failure(Exception("Session already completed"))
            }

            // Find the exercise
            val exercise = session.exercises.find { it.exerciseId == exerciseId }
            if (exercise == null) {
                Timber.w("Exercise not found in session: $exerciseId")
                return Result.failure(Exception("Exercise not found"))
            }

            // Find the set
            val set = exercise.sets.find { it.setNumber == setNumber }
            if (set == null) {
                Timber.w("Set not found in exercise: set $setNumber")
                return Result.failure(Exception("Set not found"))
            }

            // Update set with actual values and mark as completed
            val updatedSet = set
                .let { if (actualReps != null) it.updateActualReps(actualReps) else it }
                .let { if (actualWeight != null) it.updateActualWeight(actualWeight) else it }
                .let { if (actualTime != null) it.updateActualTime(actualTime) else it }
                .let { if (actualRpe != null) it.updateActualRpe(actualRpe) else it }
                .complete()

            // Update the session with the new set state
            sessionManager.updateSetInSession(exerciseId, setNumber, updatedSet)

            // Force a session state refresh to ensure UI updates
            sessionManager.refreshSessionState()

            Timber.d("Set marked as completed: exercise=$exerciseId, set=$setNumber")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to mark set as completed: exercise=$exerciseId, set=$setNumber")
            Result.failure(e)
        }
    }

    // ==================== TEMPLATE CREATION ====================

    /**
     * Creates a template from the current active session.
     *
     * **Replaces**: CreateTemplateFromSessionUseCase.executeFromCurrentSession()
     *
     * @param templateName Name for the new template
     * @param description Optional description
     * @param isPublic Whether template should be public
     * @return Result<WorkoutTemplate> with created template
     */
    suspend fun createTemplateFromCurrentSession(
        templateName: String,
        description: String? = null,
        isPublic: Boolean = false
    ): Result<WorkoutTemplate> {
        return try {
            val currentSession = sessionManager.getCurrentSession()
            if (currentSession == null) {
                Timber.w("Cannot create template - no active session")
                return Result.failure(Exception("No active workout session"))
            }

            createTemplateFromSession(currentSession, templateName, description, isPublic)

        } catch (e: Exception) {
            Timber.e(e, "Failed to create template from current session")
            Result.failure(e)
        }
    }

    /**
     * Creates a template from a specific session.
     *
     * **Replaces**: CreateTemplateFromSessionUseCase.executeFromSession()
     *
     * @param session Session to create template from
     * @param templateName Name for the new template
     * @param description Optional description
     * @param isPublic Whether template should be public
     * @return Result<WorkoutTemplate> with created template
     */
    suspend fun createTemplateFromSession(
        session: UnifiedWorkoutSession,
        templateName: String,
        description: String? = null,
        isPublic: Boolean = false
    ): Result<WorkoutTemplate> {
        return try {
            // Validate input
            if (templateName.isBlank()) {
                return Result.failure(Exception("Template name cannot be blank"))
            }

            if (session.exercises.isEmpty()) {
                return Result.failure(Exception("Cannot create template from empty workout"))
            }

            // Create template from session
            val template = session.toWorkoutTemplate(
                templateName = templateName.trim(),
                description = description?.trim()
            )

            // Save template to repository
            return workoutTemplateRepository.createTemplate(template)
                .fold(
                    onSuccess = {
                        Timber.i("Template created successfully: $templateName")
                        Result.success(template)
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to save template: $templateName")
                        Result.failure(exception)
                    }
                )

        } catch (e: Exception) {
            Timber.e(e, "Failed to create template from session")
            Result.failure(e)
        }
    }

    /**
     * Creates a template with smart defaults based on session data.
     *
     * **Replaces**: CreateTemplateFromSessionUseCase.executeWithSmartDefaults()
     *
     * @param session Session to create template from
     * @param templateName Optional custom name (auto-generated if not provided)
     * @return Result<WorkoutTemplate> with created template
     */
    suspend fun createTemplateWithSmartDefaults(
        session: UnifiedWorkoutSession,
        templateName: String? = null
    ): Result<WorkoutTemplate> {
        return try {
            // Generate smart template name if not provided
            val smartTemplateName = templateName?.takeIf { it.isNotBlank() }
                ?: generateSmartTemplateName(session)

            // Generate smart description based on session content
            val smartDescription = generateSmartDescription(session)

            // Determine if template should be public based on session quality
            val shouldBePublic = session.exercises.size >= 3 &&
                    session.getCompletionPercentage() >= 80f

            createTemplateFromSession(
                session = session,
                templateName = smartTemplateName,
                description = smartDescription,
                isPublic = shouldBePublic
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to create template with smart defaults")
            Result.failure(e)
        }
    }

    /**
     * Validates template creation requirements.
     *
     * @param session Session to validate
     * @return Result<Unit> indicating if template creation is allowed
     */
    fun validateTemplateCreation(session: UnifiedWorkoutSession): Result<Unit> {
        return try {
            when {
                session.exercises.isEmpty() -> {
                    Result.failure(Exception("Cannot create template from empty workout"))
                }
                session.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE -> {
                    Result.failure(Exception("Cannot create template from active session. Complete or pause the workout first."))
                }
                session.getCompletionPercentage() < 20f -> {
                    Result.failure(Exception("Cannot create template from workout with less than 20% completion"))
                }
                else -> Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Generates a smart template name based on session content.
     */
    private fun generateSmartTemplateName(session: UnifiedWorkoutSession): String {
        // If session already has a template, use that as base
        if (session.templateId != null) {
            return "${session.name} - Modified"
        }

        // Generate based on primary muscle groups
        val primaryMuscles = session.exercises
            .map { it.primaryMuscle }
            .distinct()
            .take(2)

        val muscleGroupName = when {
            primaryMuscles.size == 1 -> primaryMuscles.first().name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }
            primaryMuscles.size == 2 -> "${primaryMuscles.first().name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }} & ${primaryMuscles.last().name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }}"
            else -> "Full Body"
        }

        return "$muscleGroupName Workout"
    }

    /**
     * Generates a smart description based on session content.
     */
    private fun generateSmartDescription(session: UnifiedWorkoutSession): String {
        val stats = session.getSessionStats()
        val duration = session.getFormattedDuration()

        return buildString {
            append("Created from workout session on ${java.time.LocalDate.now()}. ")
            append("${stats.totalExercises} exercises, ")
            append("${stats.totalSets} sets, ")
            append("${stats.totalVolume.toInt()} kg total volume. ")
            append("Duration: $duration.")
        }
    }
}
