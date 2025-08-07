package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.repository.FolderRepository
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for moving a workout template to a different folder.
 * 
 * Validates that the target folder exists and belongs to the same user,
 * then updates the workout template's folder assignment.
 */
@Singleton
class MoveWorkoutToFolderUseCase @Inject constructor(
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val folderRepository: FolderRepository
) {
    
    /**
     * Moves a workout template to the specified folder.
     * 
     * @param workoutTemplate The workout template to move
     * @param targetFolderId The ID of the target folder
     * @return LiftrixResult with the updated workout template or error
     */
    suspend operator fun invoke(
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
            val result = workoutTemplateRepository.updateTemplate(updatedTemplate)
            result.fold(
                onSuccess = { updatedWorkout ->
                    Timber.d("Successfully moved workout to folder '$targetFolderId'")
                    updatedWorkout
                },
                onFailure = { exception ->
                    Timber.e(exception, "Failed to update workout template")
                    throw Exception("Failed to update workout template: ${exception.message}", exception)
                }
            )
        }
    }
}