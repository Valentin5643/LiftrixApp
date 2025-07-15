package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * Use case for duplicating an existing workout template
 */
class DuplicateWorkoutTemplateUseCase @Inject constructor(
    private val workoutTemplateRepository: WorkoutTemplateRepository
) {
    
    /**
     * Duplicates an existing workout template with a new name
     * 
     * @param originalTemplate The template to duplicate
     * @param newName The name for the duplicated template
     * @return Result containing the new template or error
     */
    suspend operator fun invoke(
        originalTemplate: WorkoutTemplate,
        newName: String
    ): Result<WorkoutTemplate> {
        return try {
            require(newName.isNotBlank()) { "New template name cannot be blank" }
            require(newName.length <= 100) { "Template name too long" }
            
            // Check if name already exists
            val nameExistsResult = workoutTemplateRepository.doesTemplateNameExist(
                originalTemplate.userId, 
                newName.trim()
            )
            
            val nameExists = nameExistsResult.getOrElse { false }
            
            if (nameExists) {
                return Result.failure(
                    IllegalArgumentException("Template name '$newName' already exists")
                )
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
            val result = workoutTemplateRepository.createTemplate(duplicateTemplate)
            
            if (result.isSuccess) {
                Timber.i("Template duplicated successfully: ${originalTemplate.name} -> $newName")
            } else {
                Timber.e("Failed to duplicate template: ${originalTemplate.name}")
            }
            
            result
            
        } catch (exception: Exception) {
            Timber.e(exception, "Error duplicating template: ${originalTemplate.name}")
            Result.failure(exception)
        }
    }
} 