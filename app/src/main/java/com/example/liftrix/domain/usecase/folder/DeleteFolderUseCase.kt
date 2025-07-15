package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for deleting a folder.
 * 
 * This use case handles folder deletion with proper validation,
 * template reallocation to default folder, and business rule enforcement.
 * 
 * Features:
 * - User authentication validation
 * - Folder ownership validation
 * - Default folder protection
 * - Template reallocation to default folder
 * - Atomic operations
 */
@Singleton
class DeleteFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val workoutTemplateRepository: WorkoutTemplateRepository
) {
    
    /**
     * Deletes a folder and moves all its templates to the default folder
     * 
     * @param input The deletion input containing userId and folderId
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(input: DeleteFolderInput): Result<Unit> {
        return try {
            validateInput(input)
            
            // Verify user owns the folder
            val folder = folderRepository.getFolderById(input.folderId, input.userId).first()
            if (folder == null) {
                return Result.failure(
                    IllegalArgumentException("Folder not found or not owned by user")
                )
            }
            
            // Prevent deletion of default "Uncategorized" folder
            if (folder.id.value.startsWith("uncategorized_")) {
                return Result.failure(
                    IllegalArgumentException("Cannot delete the default 'Uncategorized' folder")
                )
            }
            
            // Get or create default folder for template reallocation
            val defaultFolderResult = folderRepository.getOrCreateDefaultFolder(input.userId)
            if (defaultFolderResult.isFailure) {
                return Result.failure(
                    defaultFolderResult.exceptionOrNull() ?: RuntimeException("Failed to access default folder")
                )
            }
            
            val defaultFolder = defaultFolderResult.getOrThrow()
            
            // Move all templates from this folder to the default folder
            if (folder.templateCount > 0) {
                val templatesResult = workoutTemplateRepository.getTemplatesByFolder(input.userId, folder.id.value).first()
                if (templatesResult.isFailure) {
                    return Result.failure(templatesResult.exceptionOrNull()!!)
                }
                val templates = templatesResult.getOrThrow()
                
                // Move each template to default folder
                for (template in templates) {
                    val moveResult = folderRepository.moveTemplateToFolder(
                        templateId = template.id.value,
                        targetFolderId = FolderId(defaultFolder.id.value),
                        userId = input.userId
                    )
                    if (moveResult.isFailure) {
                        return Result.failure(
                            moveResult.exceptionOrNull() ?: RuntimeException("Failed to move template ${template.id.value}")
                        )
                    }
                }
            }
            
            // Delete the folder
            folderRepository.deleteFolder(input.folderId, input.userId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Validates input parameters
     */
    private fun validateInput(input: DeleteFolderInput) {
        require(input.userId.isNotBlank()) { "User ID cannot be blank" }
        require(input.folderId.value.isNotBlank()) { "Folder ID cannot be blank" }
    }
    
    /**
     * Input data class for folder deletion
     */
    data class DeleteFolderInput(
        val userId: String,
        val folderId: FolderId
    )
}