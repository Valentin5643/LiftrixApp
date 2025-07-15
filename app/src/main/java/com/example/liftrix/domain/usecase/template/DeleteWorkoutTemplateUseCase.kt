package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for deleting a workout template
 */
class DeleteWorkoutTemplateUseCase @Inject constructor(
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val authRepository: AuthRepository
) {
    
    /**
     * Deletes a workout template by ID
     * Only the owner of the template can delete it
     * 
     * @param templateId The ID of the template to delete
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(templateId: WorkoutTemplateId): Result<Unit> {
        return try {
            // Get current user
            val currentUser = authRepository.currentUser.first()
                ?: return Result.failure(IllegalStateException("User not authenticated"))
            
            // Verify template exists and belongs to user
            val templateResult = workoutTemplateRepository.getTemplateById(templateId, currentUser.uid)
            val template = templateResult.getOrNull()
                ?: return Result.failure(IllegalArgumentException("Template not found or access denied"))
            
            // Delete the template
            val result = workoutTemplateRepository.deleteTemplate(templateId, currentUser.uid)
            
            if (result.isSuccess) {
                Timber.i("Template deleted successfully: ${template.name}")
            } else {
                Timber.e("Failed to delete template: ${template.name}")
            }
            
            result
            
        } catch (exception: Exception) {
            Timber.e(exception, "Error deleting template: $templateId")
            Result.failure(exception)
        }
    }
} 