package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for moving a workout template between folders.
 * 
 * This use case handles moving templates from one folder to another
 * with proper validation, ownership checking, and template count updates.
 * 
 * Features:
 * - User authentication validation
 * - Folder ownership validation
 * - Template ownership validation
 * - Atomic template count updates
 */
@Singleton
class MoveFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val workoutTemplateRepository: WorkoutTemplateRepository
) {
    
    /**
     * Moves a workout template to a different folder
     * 
     * @param input The move operation input containing userId, templateId, and targetFolderId
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(input: MoveFolderInput): Result<Unit> {
        return try {
            validateInput(input)
            
            // Verify user owns the target folder
            val targetFolder = folderRepository.getFolderById(input.targetFolderId, input.userId).first()
            if (targetFolder == null) {
                return Result.failure(
                    IllegalArgumentException("Target folder not found or not owned by user")
                )
            }
            
            // Verify user owns the template
            val template = workoutTemplateRepository.getTemplateById(
                WorkoutTemplateId.fromString(input.templateId), 
                input.userId
            )
            
            if (template == null) {
                return Result.failure(
                    IllegalArgumentException("Template not found or not owned by user")
                )
            }
            
            // Don't move if already in target folder
            if (template.folderId == input.targetFolderId.value) {
                return Result.success(Unit)
            }
            
            // Move template to target folder (this will handle template count updates automatically)
            folderRepository.moveTemplateToFolder(
                templateId = input.templateId,
                targetFolderId = input.targetFolderId,
                userId = input.userId
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Validates input parameters
     */
    private fun validateInput(input: MoveFolderInput) {
        require(input.userId.isNotBlank()) { "User ID cannot be blank" }
        require(input.templateId.isNotBlank()) { "Template ID cannot be blank" }
        require(input.targetFolderId.value.isNotBlank()) { "Target folder ID cannot be blank" }
    }
    
    /**
     * Input data class for moving template between folders
     */
    data class MoveFolderInput(
        val userId: String,
        val templateId: String,
        val targetFolderId: FolderId
    )
}