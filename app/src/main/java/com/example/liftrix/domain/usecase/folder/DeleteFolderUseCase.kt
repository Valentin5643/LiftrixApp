package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.data.local.LiftrixDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import timber.log.Timber
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
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val database: LiftrixDatabase // 🔥 ADDED: For atomic transactions
) {
    
    /**
     * Deletes a folder and moves all its templates to the default folder atomically
     * 
     * 🔥 FIXED: Now uses database transactions to prevent CASCADE DELETE race conditions
     * The migration 38→39 removed the CASCADE DELETE constraint, so templates won't be deleted
     * 
     * @param input The deletion input containing userId and folderId
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(input: DeleteFolderInput): Result<Unit> {
        return try {
            validateInput(input)
            
            // 🔥 CRITICAL FIX: Use database transaction for atomic folder deletion + template preservation
            database.withTransaction {
                // Verify user owns the folder using direct method to prevent Flow abortion
                val folderResult = folderRepository.getFolderByIdDirect(input.folderId, input.userId)
                val folder: com.example.liftrix.domain.model.Folder = when {
                    folderResult.isFailure -> {
                        throw (folderResult.exceptionOrNull() ?: RuntimeException("Failed to access folder"))
                    }
                    folderResult.getOrNull() == null -> {
                        throw IllegalArgumentException("Folder not found or not owned by user: ${input.folderId.value}")
                    }
                    else -> folderResult.getOrThrow()!! // Safe to use !! here since we checked for null above
                }
                
                // Prevent deletion of default "Uncategorized" folder
                if (folder.id.value.startsWith("uncategorized_")) {
                    throw IllegalArgumentException("Cannot delete the default 'Uncategorized' folder")
                }
                
                // Get or create default folder for template reallocation
                val defaultFolderResult = folderRepository.getOrCreateDefaultFolder(input.userId)
                if (defaultFolderResult.isFailure) {
                    throw (defaultFolderResult.exceptionOrNull() ?: RuntimeException("Failed to access default folder"))
                }
                
                val defaultFolder = defaultFolderResult.getOrThrow()
                
                // 🔥 PERFORMANCE FIX: Move templates using direct DAO operations within transaction
                // This is faster than using repository flows and prevents race conditions
                val templatesResult = workoutTemplateRepository.getTemplatesByFolder(input.userId, folder.id.value).first()
                if (templatesResult.isFailure) {
                    throw (templatesResult.exceptionOrNull() ?: RuntimeException("Failed to load templates"))
                }
                
                val templates = templatesResult.getOrThrow()
                
                // Move each template to default folder atomically  
                // 🔥 NULL SAFETY: Handle templates that may have null folder IDs
                for (template in templates) {
                    // Skip templates that are already in default folder or have null folder ID
                    if (template.folderId == null || template.folderId == defaultFolder.id.value) {
                        continue
                    }
                    
                    val moveResult = folderRepository.moveTemplateToFolder(
                        templateId = template.id.value,
                        targetFolderId = FolderId(defaultFolder.id.value),
                        userId = input.userId
                    )
                    if (moveResult.isFailure) {
                        throw (moveResult.exceptionOrNull() ?: RuntimeException("Failed to move template ${template.id.value}"))
                    }
                }
                
                // Delete the folder (templates have been moved to default folder)
                val deleteResult = folderRepository.deleteFolder(input.folderId, input.userId)
                if (deleteResult.isFailure) {
                    val exception = deleteResult.exceptionOrNull() ?: RuntimeException("Failed to delete folder")
                    throw exception
                }
            }
            
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