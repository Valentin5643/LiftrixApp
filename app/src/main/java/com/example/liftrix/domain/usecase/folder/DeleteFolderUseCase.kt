package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
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
 * - Atomic operations (handled by repository layer)
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
    suspend operator fun invoke(input: DeleteFolderInput): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when (throwable) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = "folderId",
                        violations = listOf(throwable.message ?: "Invalid folder ID"),
                        analyticsContext = mapOf("operation" to "DELETE_FOLDER")
                    )
                    else -> LiftrixError.BusinessLogicError(
                        code = "FOLDER_DELETION_FAILED",
                        errorMessage = "Failed to delete folder: ${throwable.message}",
                        analyticsContext = mapOf("operation" to "DELETE_FOLDER", "userId" to input.userId)
                    )
                }
            }
        ) {
            validateInput(input)
            
            // Step 1: Verify user owns the folder
            val folder = validateFolderOwnership(input)
            
            // Step 2: Prevent deletion of default "Uncategorized" folder
            validateNotDefaultFolder(folder)
            
            // Step 3: Get or create default folder for template reallocation
            val defaultFolder = ensureDefaultFolder(input.userId)
            
            // Step 4: Move templates to default folder if folder has templates
            if (folder.templateCount > 0) {
                moveTemplatesToDefaultFolder(input.userId, folder.id.value, defaultFolder.id)
            }
            
            // Step 5: Delete the folder (templates have been moved to default folder)
            deleteFolderSafely(input.folderId, input.userId)
        }
    }
    
    /**
     * Validates user owns the folder
     */
    private suspend fun validateFolderOwnership(input: DeleteFolderInput): Folder {
        val folderResult = folderRepository.getFolderByIdDirect(input.folderId, input.userId)
        val folder = when {
            folderResult.isFailure -> {
                val exception = folderResult.exceptionOrNull()
                if (exception != null) {
                    throw exception
                } else {
                    throw RuntimeException("Failed to access folder")
                }
            }
            folderResult.getOrNull() == null -> {
                throw IllegalArgumentException("Folder not found or not owned by user")
            }
            else -> folderResult.getOrThrow()!!
        }
        return folder
    }
    
    /**
     * Validates folder is not a default folder
     */
    private fun validateNotDefaultFolder(folder: Folder) {
        if (folder.id.value.startsWith("uncategorized_")) {
            throw IllegalArgumentException("Cannot delete the default 'Uncategorized' folder")
        }
    }
    
    /**
     * Ensures default folder exists for template reallocation
     */
    private suspend fun ensureDefaultFolder(userId: String): Folder {
        val defaultFolderResult = folderRepository.getOrCreateDefaultFolder(userId)
        if (defaultFolderResult.isFailure) {
            val exception = defaultFolderResult.exceptionOrNull()
            if (exception != null) {
                throw exception
            } else {
                throw RuntimeException("Default folder creation failed")
            }
        }
        return defaultFolderResult.getOrThrow()!!
    }
    
    /**
     * Moves all templates from source folder to default folder
     */
    private suspend fun moveTemplatesToDefaultFolder(userId: String, sourceFolderId: String, defaultFolderId: FolderId) {
        val templatesResult = workoutTemplateRepository.getTemplatesByFolder(userId, sourceFolderId).first()
        if (templatesResult.isFailure) {
            val exception = templatesResult.exceptionOrNull()
            if (exception != null) {
                throw exception
            } else {
                throw RuntimeException("Template retrieval failed")
            }
        }
        
        val templates = templatesResult.getOrThrow()
        
        // Move each template to default folder
        for (template in templates) {
            // Skip templates that are already in default folder or have null folder ID
            if (template.folderId == null || template.folderId == defaultFolderId.value) {
                continue
            }
            
            val moveResult = folderRepository.moveTemplateToFolder(
                templateId = template.id.value,
                targetFolderId = defaultFolderId,
                userId = userId
            )
            if (moveResult.isFailure) {
                val exception = moveResult.exceptionOrNull()
                if (exception != null) {
                    throw exception
                } else {
                    throw RuntimeException("Template move failed")
                }
            }
        }
    }
    
    /**
     * Deletes the folder safely
     */
    private suspend fun deleteFolderSafely(folderId: FolderId, userId: String) {
        val deleteResult = folderRepository.deleteFolder(folderId, userId)
        if (deleteResult.isFailure) {
            val exception = deleteResult.exceptionOrNull()
            if (exception != null) {
                throw exception
            } else {
                throw RuntimeException("Failed to delete folder")
            }
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