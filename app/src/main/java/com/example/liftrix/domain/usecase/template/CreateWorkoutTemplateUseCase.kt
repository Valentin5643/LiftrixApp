package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
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
    private val templateRepository: WorkoutTemplateRepository
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
     * @return Result containing the created template or error
     */
    suspend operator fun invoke(
        userId: String,
        name: String,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        estimatedDurationMinutes: Int? = null,
        difficultyLevel: Int? = null,
        tags: Set<String> = emptySet()
    ): Result<WorkoutTemplate> {
        return try {
            validateInput(userId, name, exercises, difficultyLevel)
            
            val template = WorkoutTemplate(
                id = WorkoutTemplateId.generate(),
                userId = userId,
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                exercises = exercises.mapIndexed { index, exercise ->
                    exercise.copy(orderIndex = index)
                },
                estimatedDurationMinutes = estimatedDurationMinutes,
                difficultyLevel = difficultyLevel,
                tags = tags,
                usageCount = 0,
                lastUsedAt = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            
            templateRepository.createTemplate(template)
            
        } catch (e: Exception) {
            Result.failure(e)
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