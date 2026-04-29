package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.ai.GeneratedPrescriptionType
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.model.sharing.TemplateShareDeliveryMode
import com.example.liftrix.domain.model.sharing.TemplateShareEvent
import com.example.liftrix.domain.repository.sharing.TemplateShareRepository
import com.example.liftrix.domain.repository.social.GymBuddyRepository
import com.example.liftrix.domain.usecase.workout.WorkoutQueryUseCase
import kotlinx.coroutines.flow.first
import java.time.Duration
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated use case for all workout template mutation (command) operations.
 *
 * This use case consolidates:
 * - CreateWorkoutTemplateUseCase
 * - CreateTemplateFromSessionUseCase
 * - DuplicateWorkoutTemplateUseCase
 * - DeleteWorkoutTemplateUseCase
 * - MoveWorkoutToFolderUseCase
 *
 * **Consolidation Rationale**:
 * - All use cases perform mutations on WorkoutTemplate entities
 * - Share common validation logic (now extracted to TemplateValidationService)
 * - Share common error handling patterns
 * - Consolidating reduces duplication and maintains single responsibility
 *
 * **Command Operations**:
 * - create(): Create new template from scratch
 * - createFromSession(): Convert active workout session to template
 * - duplicate(): Duplicate existing template with new name
 * - delete(): Delete template by ID
 * - moveToFolder(): Move template to different folder
 */
@Singleton
class TemplateCommandUseCase @Inject constructor(
    private val templateRepository: WorkoutTemplateRepository,
    private val folderRepository: FolderRepository,
    private val authRepository: AuthRepository,
    private val workoutQueryUseCase: WorkoutQueryUseCase,
    private val gymBuddyRepository: GymBuddyRepository,
    private val templateShareRepository: TemplateShareRepository
) {

    // ========== CREATE OPERATIONS ==========

    /**
     * Creates a new workout template from scratch.
     *
     * **Replaces**: CreateWorkoutTemplateUseCase.invoke()
     *
     * @param userId The ID of the user creating the template
     * @param name The name of the template
     * @param folderId Optional folder ID (null uses default folder)
     * @param description Optional description for the template
     * @param exercises List of exercises to include in the template
     * @param estimatedDurationMinutes Optional estimated duration
     * @param difficultyLevel Optional difficulty level (1-10)
     * @return LiftrixResult containing the created template or error
     */
    suspend fun create(
        userId: String,
        name: String,
        folderId: String? = null,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        estimatedDurationMinutes: Int? = null,
        difficultyLevel: Int? = null
    ): LiftrixResult<WorkoutTemplate> {
        return liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "CREATE-TEMPLATE: Error occurred during template creation")
                when (throwable) {
                    is IllegalArgumentException -> {
                        LiftrixError.ValidationError(
                            field = when {
                                throwable.message?.contains("User ID") == true -> "userId"
                                throwable.message?.contains("Template name") == true -> "name"
                                throwable.message?.contains("exercises") == true -> "exercises"
                                throwable.message?.contains("Difficulty level") == true -> "difficultyLevel"
                                else -> "input"
                            },
                            violations = listOf(throwable.message ?: "Invalid input parameters")
                        )
                    }
                    is RuntimeException -> when {
                        throwable.message?.contains("Failed to create default folder") == true -> {
                            LiftrixError.DatabaseError(
                                errorMessage = "Failed to create default folder",
                                operation = "getOrCreateDefaultFolder"
                            )
                        }
                        else -> {
                            LiftrixError.BusinessLogicError(
                                code = "TEMPLATE_CREATION_FAILED",
                                analyticsContext = mapOf("userId" to userId, "templateName" to name),
                                errorMessage = throwable.message ?: "Failed to create workout template"
                            )
                        }
                    }
                    else -> {
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to create workout template",
                            operation = "createTemplate"
                        )
                    }
                }
            }
        ) {
            Timber.d("CREATE-TEMPLATE: Starting template creation for user=$userId, name=$name")

            // Validate inputs
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(name.isNotBlank()) { "Template name cannot be blank" }
            require(name.length <= WorkoutTemplate.MAX_NAME_LENGTH) { "Template name too long" }

            // Ensure default folder exists before creating template
            val defaultFolder = folderRepository.getOrCreateDefaultFolder(userId).getOrThrow()
            val actualFolderId = if (folderId == null) {
                defaultFolder.id
            } else {
                FolderId(folderId)
            }

            // Calculate estimated duration if not provided
            val finalEstimatedDuration = estimatedDurationMinutes ?: run {
                val tempTemplate = WorkoutTemplate(
                    id = WorkoutTemplateId.generate(),
                    userId = userId,
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotBlank() },
                    exercises = exercises.mapIndexed { index, exercise ->
                        exercise.copy(orderIndex = index)
                    },
                    estimatedDurationMinutes = null,
                    difficultyLevel = difficultyLevel,
                    folderId = actualFolderId.value,
                    usageCount = 0,
                    lastUsedAt = null,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
                val durationResult = workoutQueryUseCase.estimateDuration(tempTemplate)
                durationResult.fold(
                    onSuccess = { duration -> duration.toMinutes().toInt() },
                    onFailure = { 45 } // Fallback to 45 minutes
                )
            }

            val template = WorkoutTemplate(
                id = WorkoutTemplateId.generate(),
                userId = userId,
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                exercises = exercises.mapIndexed { index, exercise ->
                    exercise.copy(orderIndex = index)
                },
                estimatedDurationMinutes = finalEstimatedDuration,
                difficultyLevel = difficultyLevel,
                folderId = actualFolderId.value,
                usageCount = 0,
                lastUsedAt = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            Timber.d("CREATE-TEMPLATE: Calling repository.createTemplate()")
            val createdTemplate = templateRepository.createTemplate(template).getOrThrow()
            Timber.d("CREATE-TEMPLATE: Template created successfully - ID: ${createdTemplate.id.value}")

            createdTemplate
        }
    }

    /**
     * Creates a workout template from an active session.
     *
     * **Replaces**: CreateTemplateFromSessionUseCase.invoke()
     *
     * @param session The active workout session to convert
     * @param templateName Custom name for the template
     * @param templateDescription Optional description for the template
     * @return LiftrixResult containing the created template or error
     */
    suspend fun createFromSession(
        session: UnifiedWorkoutSession,
        templateName: String,
        templateDescription: String? = null
    ): LiftrixResult<WorkoutTemplate> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when (throwable) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = when {
                            throwable.message?.contains("Template name") == true -> "templateName"
                            throwable.message?.contains("empty workout") == true -> "session"
                            else -> "input"
                        },
                        violations = listOf(throwable.message ?: "Invalid input parameters")
                    )
                    else -> LiftrixError.DatabaseError(
                        errorMessage = "Failed to create template from session",
                        operation = "createTemplate"
                    )
                }
            }
        ) {
            // Validate inputs
            require(templateName.isNotBlank()) { "Template name cannot be blank" }
            require(templateName.length <= WorkoutTemplate.MAX_NAME_LENGTH) {
                "Template name too long: ${templateName.length} > ${WorkoutTemplate.MAX_NAME_LENGTH}"
            }
            require(session.exercises.isNotEmpty()) { "Cannot create template from empty workout" }

            // Ensure default folder exists before creating template
            val defaultFolder = folderRepository.getOrCreateDefaultFolder(session.userId).getOrThrow()

            val template = convertSessionToTemplate(
                session = session,
                templateName = templateName.trim(),
                templateDescription = templateDescription?.trim()?.takeIf { it.isNotBlank() },
                defaultFolderId = defaultFolder.id.value
            )

            templateRepository.createTemplate(template).getOrThrow()
        }
    }

    /**
     * Duplicates an existing workout template with a new name.
     *
     * **Replaces**: DuplicateWorkoutTemplateUseCase.invoke()
     *
     * @param originalTemplate The template to duplicate
     * @param newName The name for the duplicated template
     * @return LiftrixResult containing the new template or error
     */
    suspend fun duplicate(
        originalTemplate: WorkoutTemplate,
        newName: String
    ): LiftrixResult<WorkoutTemplate> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "DUPLICATE_TEMPLATE_FAILED",
                    errorMessage = "Failed to duplicate template: ${throwable.message}",
                    analyticsContext = mapOf(
                        "originalTemplateId" to originalTemplate.id.value,
                        "newName" to newName
                    )
                )
            }
        ) {
            require(newName.isNotBlank()) { "New template name cannot be blank" }
            require(newName.length <= 100) { "Template name too long" }

            // Check if name already exists
            val nameExistsResult = templateRepository.doesTemplateNameExist(
                originalTemplate.userId,
                newName.trim()
            )

            val nameExists = nameExistsResult.getOrElse { false }

            if (nameExists) {
                throw IllegalArgumentException("Template name '$newName' already exists")
            }

            // Create duplicate with new ID and name
            val now = Instant.now()
            val duplicateTemplate = originalTemplate.copy(
                id = WorkoutTemplateId.generate(),
                name = newName.trim(),
                description = originalTemplate.description?.let { "$it (Copy)" },
                usageCount = 0, // Reset usage count for new template
                lastUsedAt = null, // Reset last used timestamp
                createdAt = now,
                updatedAt = now
            )

            // Save the duplicate template
            val result = templateRepository.createTemplate(duplicateTemplate).getOrThrow()

            Timber.i("Template duplicated successfully: ${originalTemplate.name} -> $newName")

            result
        }
    }

    // ========== DELETE OPERATIONS ==========

    /**
     * Deletes a workout template by ID.
     *
     * **Replaces**: DeleteWorkoutTemplateUseCase.invoke()
     *
     * Only the owner of the template can delete it.
     *
     * @param templateId The ID of the template to delete
     * @return LiftrixResult indicating success or failure
     */
    suspend fun delete(templateId: WorkoutTemplateId): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "DELETE_TEMPLATE_FAILED",
                    errorMessage = "Failed to delete template: ${throwable.message}",
                    analyticsContext = mapOf("templateId" to templateId.value)
                )
            }
        ) {
            // Get current user
            val currentUser = authRepository.currentUser.first()
                ?: throw IllegalStateException("User not authenticated")

            // Verify template exists and belongs to user
            val template = templateRepository.getTemplateById(templateId, currentUser.uid).getOrNull()
                ?: throw IllegalArgumentException("Template not found or access denied")

            // Delete the template
            templateRepository.deleteTemplate(templateId, currentUser.uid).getOrThrow()

            Timber.i("Template deleted successfully: ${template.name}")
        }
    }

    // ========== UPDATE OPERATIONS ==========

    /**
     * Moves a workout template to a different folder.
     *
     * **Replaces**: MoveWorkoutToFolderUseCase.invoke()
     *
     * Validates that the target folder exists and belongs to the same user,
     * then updates the workout template's folder assignment.
     *
     * @param workoutTemplate The workout template to move
     * @param targetFolderId The ID of the target folder
     * @return LiftrixResult with the updated workout template or error
     */
    suspend fun moveToFolder(
        workoutTemplate: WorkoutTemplate,
        targetFolderId: String
    ): LiftrixResult<WorkoutTemplate> {
        return liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "Failed to move workout template")
                when (throwable) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = "targetFolderId",
                        violations = listOf(throwable.message ?: "Invalid folder ID")
                    )
                    else -> LiftrixError.UnknownError(
                        errorMessage = "Failed to move workout template: ${throwable.message}"
                    )
                }
            }
        ) {
            Timber.d("Moving workout '${workoutTemplate.name}' from folder '${workoutTemplate.folderId}' to folder '$targetFolderId'")

            // Check if workout is already in the target folder
            if (workoutTemplate.folderId == targetFolderId) {
                Timber.d("Workout is already in target folder, no move needed")
                return@liftrixCatching workoutTemplate
            }

            // Validate that target folder exists and belongs to the user
            val targetFolder = folderRepository.getFolderById(FolderId(targetFolderId))
            if (targetFolder == null) {
                Timber.w("Target folder not found: $targetFolderId")
                throw IllegalArgumentException("Target folder does not exist")
            }

            // Validate folder belongs to the same user as the workout template
            if (targetFolder.userId != workoutTemplate.userId) {
                Timber.w("Folder user mismatch - Folder User: ${targetFolder.userId}, Workout User: ${workoutTemplate.userId}")
                throw IllegalArgumentException("Cannot move workout to folder belonging to different user")
            }

            Timber.d("Target folder validation passed: '${targetFolder.name.value}' owned by user '${targetFolder.userId}'")

            // Update the workout template with new folder ID
            val updatedTemplate = workoutTemplate.copy(
                folderId = targetFolderId,
                updatedAt = Instant.now()
            )

            Timber.d("Updating workout template in repository")

            // Save the updated template and return the result
            val updatedWorkout = templateRepository.updateTemplate(updatedTemplate).getOrThrow()

            Timber.d("Successfully moved workout to folder '$targetFolderId'")

            updatedWorkout
        }
    }

    suspend fun updateFromEditedWorkout(workout: Workout): LiftrixResult<Workout> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "TEMPLATE_UPDATE_FAILED",
                    errorMessage = throwable.message ?: "Failed to update workout template",
                    analyticsContext = mapOf(
                        "templateId" to workout.id.value,
                        "userId" to workout.userId
                    )
                )
            }
        ) {
            val existingTemplate = templateRepository
                .getTemplateById(WorkoutTemplateId(workout.id.value), workout.userId)
                .getOrThrow()
                ?: throw IllegalArgumentException("Template not found: ${workout.id.value}")

            val updatedTemplate = existingTemplate.copy(
                name = workout.name,
                description = workout.notes?.takeIf { it.isNotBlank() },
                exercises = workout.exercises.mapIndexed { index, exercise ->
                    val previousExercise = existingTemplate.exercises.getOrNull(index)
                    TemplateExercise(
                        exerciseId = previousExercise?.exerciseId ?: exercise.id,
                        name = exercise.libraryExercise.name,
                        primaryMuscle = exercise.libraryExercise.primaryMuscleGroup,
                        equipment = exercise.libraryExercise.equipment,
                        targetSets = exercise.sets.size.takeIf { it > 0 } ?: exercise.targetSets,
                        targetReps = exercise.sets.firstOrNull()?.reps
                            ?.takeIf { it.count > 0 }
                            ?: exercise.targetReps?.takeIf { it > 0 }?.let { Reps(it) }
                            ?: Reps(1),
                        targetWeight = exercise.sets.firstOrNull()?.weight ?: exercise.targetWeight,
                        restTimeSeconds = previousExercise?.restTimeSeconds,
                        notes = exercise.notes,
                        orderIndex = index,
                        isCustomExercise = previousExercise?.isCustomExercise ?: false,
                        customExerciseId = previousExercise?.customExerciseId,
                        instanceId = previousExercise?.instanceId ?: exercise.id.value
                    )
                },
                updatedAt = Instant.now()
            )

            Timber.d(
                "EDIT-WORKOUT-DEBUG: TemplateCommandUseCase.updateFromEditedWorkout templateId=${workout.id.value} " +
                    "exerciseCount=${updatedTemplate.exercises.size} targetSets=${updatedTemplate.exercises.map { it.targetSets }}"
            )

            val savedTemplate = templateRepository.updateTemplate(updatedTemplate).getOrThrow()

            workout.copy(
                name = savedTemplate.name,
                notes = savedTemplate.description,
                exercises = workout.exercises.mapIndexed { index, exercise ->
                    val savedTemplateExercise = savedTemplate.exercises.getOrNull(index)
                    exercise.copy(
                        orderIndex = index,
                        targetSets = savedTemplateExercise?.targetSets,
                        targetReps = savedTemplateExercise?.targetReps?.count,
                        targetWeight = savedTemplateExercise?.targetWeight
                    )
                },
                updatedAt = savedTemplate.updatedAt
            )
        }
    }

    suspend fun updateTemplateFromAiModification(
        userId: String,
        templateId: String,
        program: GeneratedWorkoutProgram
    ): LiftrixResult<WorkoutTemplate> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "AI_TEMPLATE_UPDATE_FAILED",
                errorMessage = throwable.message ?: "Failed to update template from AI modification",
                analyticsContext = mapOf("templateId" to templateId, "userId" to userId)
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(templateId.isNotBlank()) { "Template ID cannot be blank" }
        val existingTemplate = templateRepository.getTemplateById(WorkoutTemplateId(templateId), userId).getOrThrow()
            ?: throw IllegalArgumentException("Template not found")
        val updatedTemplate = existingTemplate.copy(
            name = program.workoutName.take(WorkoutTemplate.MAX_NAME_LENGTH).ifBlank { existingTemplate.name },
            exercises = program.toTemplateExercises(),
            estimatedDurationMinutes = program.days.sumOf { it.estimatedDurationMinutes }
                .coerceIn(WorkoutTemplate.MIN_DURATION_MINUTES, WorkoutTemplate.MAX_DURATION_MINUTES),
            updatedAt = Instant.now()
        )
        templateRepository.updateTemplate(updatedTemplate).getOrThrow()
    }

    suspend fun shareTemplateToBuddy(
        templateId: String,
        buddyId: String
    ): LiftrixResult<TemplateShareEvent> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TEMPLATE_SHARE_FAILED",
                errorMessage = throwable.message ?: "Failed to share template with gym buddy",
                analyticsContext = mapOf("templateId" to templateId, "buddyId" to buddyId)
            )
        }
    ) {
        val senderId = authRepository.currentUser.first()?.uid
            ?: throw IllegalStateException("User not authenticated")
        require(templateId.isNotBlank()) { "Template ID cannot be blank" }
        require(buddyId.isNotBlank()) { "Buddy ID cannot be blank" }
        require(senderId != buddyId) { "Cannot share a template with yourself" }

        val areBuddies = gymBuddyRepository.areMutualGymBuddies(senderId, buddyId).getOrThrow()
        require(areBuddies) { "Template can only be shared with an existing gym buddy" }

        val template = templateRepository.getTemplateById(WorkoutTemplateId(templateId), senderId).getOrThrow()
            ?: throw IllegalArgumentException("Template not found")

        val event = TemplateShareEvent(
            senderId = senderId,
            receiverId = buddyId,
            templateId = template.id.value,
            deliveryMode = TemplateShareDeliveryMode.DIRECT
        )
        templateShareRepository.createShare(event).getOrThrow()
    }

    suspend fun createQrTemplateShare(templateId: String): LiftrixResult<TemplateShareEvent> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "QR_TEMPLATE_SHARE_FAILED",
                errorMessage = throwable.message ?: "Failed to create QR template share",
                analyticsContext = mapOf("templateId" to templateId)
            )
        }
    ) {
        val senderId = authRepository.currentUser.first()?.uid
            ?: throw IllegalStateException("User not authenticated")
        require(templateId.isNotBlank()) { "Template ID cannot be blank" }

        val template = templateRepository.getTemplateById(WorkoutTemplateId(templateId), senderId).getOrThrow()
            ?: throw IllegalArgumentException("Template not found")

        val event = TemplateShareEvent(
            senderId = senderId,
            receiverId = null,
            templateId = template.id.value,
            deliveryMode = TemplateShareDeliveryMode.QR
        )
        templateShareRepository.createShare(event).getOrThrow()
    }

    suspend fun acceptSharedTemplate(shareId: String): LiftrixResult<WorkoutTemplate> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.BusinessLogicError(
                    code = "ACCEPT_TEMPLATE_SHARE_FAILED",
                    errorMessage = throwable.message ?: "Failed to save shared template",
                    analyticsContext = mapOf("shareId" to shareId)
                )
            }
        }
    ) {
        val receiverId = authRepository.currentUser.first()?.uid
            ?: throw IllegalStateException("User not authenticated")
        require(shareId.isNotBlank()) { "Share ID cannot be blank" }

        val event = templateShareRepository.getPendingShareForReceiver(shareId, receiverId).getOrThrow()
            ?: throw LiftrixError.NotFoundError(
                errorMessage = "Shared workout is no longer available",
                resourceType = "TemplateShareEvent",
                resourceId = shareId
            )

        val template = templateRepository.getTemplateById(WorkoutTemplateId(event.templateId), event.senderId).getOrThrow()
            ?: throw LiftrixError.NotFoundError(
                errorMessage = "The shared workout was deleted by the sender",
                resourceType = "WorkoutTemplate",
                resourceId = event.templateId
            )

        val copiedTemplate = create(
            userId = receiverId,
            name = createUniqueImportedName(receiverId, template.name),
            folderId = null,
            description = template.description,
            exercises = template.exercises,
            estimatedDurationMinutes = template.estimatedDurationMinutes,
            difficultyLevel = template.difficultyLevel
        ).getOrThrow()

        templateShareRepository.markAccepted(shareId, receiverId).getOrThrow()
        copiedTemplate
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Converts a workout session to a template.
     */
    private suspend fun convertSessionToTemplate(
        session: UnifiedWorkoutSession,
        templateName: String,
        templateDescription: String?,
        defaultFolderId: String
    ): WorkoutTemplate {
        val templateExercises = session.exercises.mapIndexed { index, exercise ->
            // Extract target reps and weight from the first set (or use null if no sets)
            val firstSet = exercise.sets.firstOrNull()
            val targetReps = firstSet?.targetReps?.let { Reps(it) }
            val targetWeight = firstSet?.targetWeight

            TemplateExercise(
                exerciseId = exercise.exerciseId,
                name = exercise.name,
                primaryMuscle = exercise.primaryMuscle,
                equipment = exercise.equipment,
                orderIndex = index,
                targetSets = exercise.sets.size,
                targetReps = targetReps,
                targetWeight = targetWeight,
                notes = exercise.notes
            )
        }

        val difficultyLevel = calculateDifficultyLevel(templateExercises)

        // Create the template first
        val template = WorkoutTemplate(
            id = WorkoutTemplateId.generate(),
            userId = session.userId,
            name = templateName,
            description = templateDescription,
            exercises = templateExercises,
            estimatedDurationMinutes = null, // Avoid invalid placeholder value
            difficultyLevel = difficultyLevel,
            folderId = defaultFolderId,
            usageCount = 0,
            lastUsedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // Estimate duration using the complete template
        val durationResult = workoutQueryUseCase.estimateDuration(template)
        val estimatedDuration = durationResult.fold(
            onSuccess = { duration -> duration.toMinutes().toInt() },
            onFailure = { 45 } // Fallback to 45 minutes
        )

        // Return template with estimated duration
        return template.copy(estimatedDurationMinutes = estimatedDuration)
    }

    /**
     * Calculates difficulty level based on exercise complexity and volume.
     */
    private fun calculateDifficultyLevel(exercises: List<TemplateExercise>): Int {
        if (exercises.isEmpty()) return 1

        val totalSets = exercises.sumOf { it.targetSets ?: 0 }
        val averageSetsPerExercise = totalSets.toFloat() / exercises.size

        return when {
            exercises.size >= 8 && averageSetsPerExercise >= 4 -> 5  // Very Hard
            exercises.size >= 6 && averageSetsPerExercise >= 3 -> 4  // Hard
            exercises.size >= 4 && averageSetsPerExercise >= 3 -> 3  // Moderate
            exercises.size >= 3 && averageSetsPerExercise >= 2 -> 2  // Easy
            else -> 1  // Beginner
        }
    }

    private suspend fun createUniqueImportedName(userId: String, originalName: String): String {
        val baseName = "$originalName from Gym Buddy".take(WorkoutTemplate.MAX_NAME_LENGTH)
        if (!templateRepository.doesTemplateNameExist(userId, baseName).getOrElse { false }) {
            return baseName
        }

        for (copyNumber in 2..99) {
            val suffix = " ($copyNumber)"
            val candidate = baseName
                .take(WorkoutTemplate.MAX_NAME_LENGTH - suffix.length)
                .plus(suffix)
            if (!templateRepository.doesTemplateNameExist(userId, candidate).getOrElse { false }) {
                return candidate
            }
        }

        return "${System.currentTimeMillis()}".let { timestamp ->
            baseName.take(WorkoutTemplate.MAX_NAME_LENGTH - timestamp.length - 1) + " " + timestamp
        }
    }

    private fun GeneratedWorkoutProgram.toTemplateExercises(): List<TemplateExercise> =
        days.flatMap { it.exercises }.take(WorkoutTemplate.MAX_EXERCISES).mapIndexed { index, exercise ->
            TemplateExercise(
                exerciseId = ExerciseId.fromString(exercise.exerciseId),
                name = exercise.exerciseName,
                primaryMuscle = exercise.primaryMuscle,
                equipment = exercise.equipment,
                targetSets = exercise.sets,
                targetReps = when (exercise.type) {
                    GeneratedPrescriptionType.REPS -> Reps(exercise.repsMax ?: exercise.repsMin ?: 1)
                    GeneratedPrescriptionType.TIME -> null
                },
                targetWeight = null,
                restTimeSeconds = exercise.restSeconds,
                notes = exercise.notes,
                orderIndex = index,
                instanceId = exercise.exerciseId
            )
        }
}
