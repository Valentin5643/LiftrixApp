package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.model.FolderName
import com.example.liftrix.domain.repository.FolderRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for creating a new folder.
 * 
 * This use case handles creating new folders with proper validation,
 * uniqueness checking, and business rule enforcement.
 * 
 * Features:
 * - Input validation
 * - Name uniqueness checking
 * - User authentication validation
 * - Proper metadata initialization
 */
@Singleton
class CreateFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository
) {
    
    /**
     * Creates a new folder for the specified user
     * 
     * @param input The folder creation input containing userId and name
     * @return Result containing the created folder or error
     */
    suspend operator fun invoke(input: CreateFolderInput): Result<Folder> {
        return try {
            validateInput(input)
            
            // Check if folder name already exists for this user
            if (folderRepository.doesFolderNameExist(input.userId, input.name)) {
                return Result.failure(
                    IllegalArgumentException("Folder name '${input.name}' already exists for this user")
                )
            }
            
            val folder = Folder(
                id = FolderId.generate(),
                userId = input.userId,
                name = FolderName(input.name.trim()),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                templateCount = 0
            )
            
            folderRepository.createFolder(folder)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Validates input parameters
     */
    private fun validateInput(input: CreateFolderInput) {
        require(input.userId.isNotBlank()) { "User ID cannot be blank" }
        require(input.name.isNotBlank()) { "Folder name cannot be blank" }
        require(input.name.trim().length in FolderName.MIN_LENGTH..FolderName.MAX_LENGTH) { 
            "Folder name must be between ${FolderName.MIN_LENGTH} and ${FolderName.MAX_LENGTH} characters" 
        }
    }
    
    /**
     * Input data class for folder creation
     */
    data class CreateFolderInput(
        val userId: String,
        val name: String
    )
}