package com.example.liftrix.domain.usecase.template

import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.data.mapper.WorkoutTemplateMapper
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for retrieving a workout template by ID and converting it to a Workout for editing.
 * 
 * This use case handles the conversion of WorkoutTemplateEntity to Workout domain object
 * so that templates can be edited using the same EditWorkoutViewModel.
 * 
 * Business Rules:
 * - User can only access their own templates
 * - Templates are converted to editable workout format
 * - Template-specific metadata is preserved where possible
 */
class GetWorkoutTemplateByIdUseCase @Inject constructor(
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val workoutMapper: WorkoutMapper,
    private val workoutTemplateMapper: WorkoutTemplateMapper
) {
    
    /**
     * Retrieves a workout template by ID and converts it to a Workout for editing.
     * 
     * @param templateId The template ID (including "template-" prefix)
     * @param userId The user ID for authorization
     * @return LiftrixResult containing the converted workout if found, null if not found, or error
     */
    suspend operator fun invoke(templateId: String, userId: String): LiftrixResult<Workout?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "TEMPLATE_RETRIEVAL_FAILED",
                    errorMessage = "Failed to retrieve workout template: ${throwable.message}",
                    analyticsContext = mapOf(
                        "templateId" to templateId,
                        "userId" to userId,
                        "error" to throwable.message.orEmpty()
                    )
                )
            }
        ) {
            // Validate inputs
            if (userId.isBlank()) {
                return@liftrixCatching null
            }
            
            if (templateId.isBlank()) {
                return@liftrixCatching null
            }
            
            // Get template from database
            val templateEntity = workoutTemplateDao.getTemplateById(templateId, userId)
            
            if (templateEntity == null) {
                return@liftrixCatching null
            }
            
            // Convert template entity to domain template first
            val workoutTemplate = workoutTemplateMapper.toDomain(templateEntity)
            
            // Debug: Check template exercises
            Timber.d("🔥 TEMPLATE-DEBUG: Template has ${workoutTemplate.exercises.size} exercises")
            
            // Now convert template to workout using proper domain model conversion
            val workout = Workout(
                userId = workoutTemplate.userId,
                id = WorkoutId(workoutTemplate.id.value),
                name = workoutTemplate.name,
                date = java.time.LocalDate.now(),
                exercises = convertTemplateExercisesToExercises(workoutTemplate.exercises, WorkoutId(workoutTemplate.id.value)),
                status = com.example.liftrix.domain.model.WorkoutStatus.PLANNED,
                startTime = null,
                endTime = null,
                notes = workoutTemplate.description ?: "",
                templateId = WorkoutId(workoutTemplate.id.value),
                createdAt = workoutTemplate.createdAt,
                updatedAt = workoutTemplate.updatedAt
            )
            
            workout
        }
    }

    /**
     * Converts template exercises to workout exercises
     */
    private fun convertTemplateExercisesToExercises(
        templateExercises: List<com.example.liftrix.domain.model.TemplateExercise>,
        workoutId: WorkoutId
    ): List<com.example.liftrix.domain.model.Exercise> {
        return templateExercises.mapIndexed { index, templateExercise ->
            // Create default sets based on targetSets or default to 3 sets
            val numberOfSets = templateExercise.targetSets ?: 3
            val defaultSets = (1..numberOfSets).map { setNumber ->
                com.example.liftrix.domain.model.ExerciseSet(
                    id = com.example.liftrix.domain.model.ExerciseSetId(
                        "${workoutId.value}-${index}-set-${setNumber}"
                    ),
                    setNumber = setNumber,
                    // Initialize with target values from template if available
                    reps = templateExercise.targetReps,
                    weight = templateExercise.targetWeight,
                    time = null, // Template doesn't have target time
                    distance = null, // Template doesn't have target distance
                    completedAt = null // Not completed yet since this is for editing
                )
            }
            
            Timber.d("🔥 TEMPLATE-DEBUG: Creating exercise '${templateExercise.name}' with ${defaultSets.size} default sets")
            
            com.example.liftrix.domain.model.Exercise(
                id = com.example.liftrix.domain.model.ExerciseId("${workoutId.value}-${index}"),
                workoutId = workoutId,
                libraryExercise = com.example.liftrix.domain.model.ExerciseLibrary(
                    id = templateExercise.exerciseId.value,
                    name = templateExercise.name,
                    primaryMuscleGroup = templateExercise.primaryMuscle,
                    equipment = templateExercise.equipment,
                    secondaryMuscleGroups = emptyList(),
                    movementPattern = "Unknown",
                    difficultyLevel = 1,
                    instructions = null,
                    isCompound = false,
                    searchableTerms = emptyList()
                ),
                orderIndex = templateExercise.orderIndex,
                targetSets = templateExercise.targetSets,
                targetReps = templateExercise.targetReps?.count,
                targetWeight = templateExercise.targetWeight,
                targetTime = null, // Template doesn't have target time
                targetDistance = null, // Template doesn't have target distance
                sets = defaultSets, // Now has default sets instead of empty list
                notes = templateExercise.notes,
                createdAt = java.time.Instant.now()
            )
        }
    }
}

/**
 * Request data class for retrieving a workout template by ID.
 */
data class GetWorkoutTemplateByIdRequest(
    val templateId: String,
    val userId: String
)