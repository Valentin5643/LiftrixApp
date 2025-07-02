package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for toggling the favorite status of a workout template
 */
class ToggleTemplateFavoriteUseCase @Inject constructor(
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val authRepository: AuthRepository
) {
    
    /**
     * Toggles the favorite status of a workout template
     * Only the owner of the template can modify its favorite status
     * 
     * @param templateId The ID of the template to toggle favorite status
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(templateId: WorkoutTemplateId): Result<Unit> {
        return try {
            // Get current user
            val currentUser = authRepository.currentUser.first()
                ?: return Result.failure(IllegalStateException("User not authenticated"))
            
            // Get the current template
            val template = workoutTemplateRepository.getTemplateById(templateId, currentUser.uid)
                ?: return Result.failure(IllegalArgumentException("Template not found or access denied"))
            
            // Toggle favorite status by adding/removing "favorite" tag
            val currentTags = template.tags.toMutableSet()
            val isFavorite = currentTags.contains("favorite")
            
            val updatedTemplate = if (isFavorite) {
                // Remove from favorites
                currentTags.remove("favorite")
                template.copy(
                    tags = currentTags,
                    updatedAt = java.time.Instant.now()
                )
            } else {
                // Add to favorites
                currentTags.add("favorite")
                template.copy(
                    tags = currentTags,
                    updatedAt = java.time.Instant.now()
                )
            }
            
            // Update the template
            val result = workoutTemplateRepository.updateTemplate(updatedTemplate)
            
            if (result.isSuccess) {
                val action = if (isFavorite) "removed from" else "added to"
                Timber.i("Template ${template.name} $action favorites")
            } else {
                Timber.e("Failed to toggle favorite status for template: ${template.name}")
            }
            
            result.map { Unit }
            
        } catch (exception: Exception) {
            Timber.e(exception, "Error toggling favorite status for template: $templateId")
            Result.failure(exception)
        }
    }
} 