package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.workout.EstimateWorkoutDurationUseCase
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for creating a new workout template from scratch.
 * 
 * This use case handles creating completely new workout templates
 * as opposed to CreateTemplateFromSessionUseCase which converts
 * existing sessions to templates.
 * 
 * Features:
 * - Input validation
 * - Name uniqueness checking
 * - Template structure validation
 * - Proper metadata initialization
 */
@Singleton
class CreateWorkoutTemplateUseCase @Inject constructor(
    private val templateRepository: WorkoutTemplateRepository,
    private val folderRepository: FolderRepository,
    private val estimateWorkoutDurationUseCase: EstimateWorkoutDurationUseCase,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Creates a new workout template
     * 
     * @param userId The ID of the user creating the template
     * @param name The name of the template
     * @param description Optional description for the template
     * @param exercises List of exercises to include in the template
     * @param estimatedDurationMinutes Optional estimated duration
     * @param difficultyLevel Optional difficulty level (1-5)
     * @param tags Optional set of tags for categorization
     * @return LiftrixResult containing the created template or error
     */
    suspend operator fun invoke(
        userId: String,
        name: String,
        folderId: String = "uncategorized_$userId",
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        estimatedDurationMinutes: Int? = null,
        difficultyLevel: Int? = null
    ): LiftrixResult<WorkoutTemplate> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when (throwable) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = when {
                            throwable.message?.contains("User ID") == true -> "userId"
                            throwable.message?.contains("Template name") == true -> "name"
                            throwable.message?.contains("exercises") == true -> "exercises"
                            throwable.message?.contains("Difficulty level") == true -> "difficultyLevel"
                            else -> "input"
                        },
                        violations = listOf(throwable.message ?: "Invalid input parameters")
                    )
                    is RuntimeException -> when {
                        throwable.message?.contains("Failed to create default folder") == true -> 
                            LiftrixError.DatabaseError(
                                errorMessage = "Failed to create default folder",
                                operation = "getOrCreateDefaultFolder"
                            )
                        else -> LiftrixError.BusinessLogicError(
                            code = "TEMPLATE_CREATION_FAILED",
                            analyticsContext = mapOf("userId" to userId, "templateName" to name),
                            errorMessage = throwable.message ?: "Failed to create workout template",
                        )
                    }
                    else -> LiftrixError.DatabaseError(
                        errorMessage = "Failed to create workout template",
                        operation = "createTemplate"
                    )
                }
            }
        ) {
            validateInput(userId, name, exercises, difficultyLevel)
            
            // Ensure default folder exists before creating template
            val defaultFolderResult = folderRepository.getOrCreateDefaultFolder(userId)
            if (defaultFolderResult.isFailure) {
                throw RuntimeException("Failed to create default folder: ${defaultFolderResult.exceptionOrNull()?.message}")
            }
            
            // Get the actual folder to ensure it exists and use its ID
            val defaultFolder = defaultFolderResult.getOrThrow()
            val actualFolderId = if (folderId == "uncategorized_$userId") {
                defaultFolder.id
            } else {
                folderId
            }
            
            timber.log.Timber.d("Creating template '${name}' for user $userId in folder $actualFolderId")
            
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
                    folderId = actualFolderId.toString(),
                    usageCount = 0,
                    lastUsedAt = null,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
                
                estimateWorkoutDurationUseCase.estimateDurationMinutes(tempTemplate)
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
                folderId = actualFolderId.toString(),
                usageCount = 0,
                lastUsedAt = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            
            templateRepository.createTemplate(template).getOrThrow()
        }
    }
    
    /**
     * Validates input parameters
     */
    private fun validateInput(
        userId: String,
        name: String,
        exercises: List<TemplateExercise>,
        difficultyLevel: Int?
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(name.isNotBlank()) { "Template name cannot be blank" }
        require(name.length <= WorkoutTemplate.MAX_NAME_LENGTH) { 
            "Template name too long: ${name.length} > ${WorkoutTemplate.MAX_NAME_LENGTH}" 
        }
        require(exercises.size <= WorkoutTemplate.MAX_EXERCISES) {
            "Too many exercises: ${exercises.size} > ${WorkoutTemplate.MAX_EXERCISES}"
        }
        difficultyLevel?.let { level ->
            require(level in 1..5) { "Difficulty level must be between 1 and 5" }
        }
    }
}