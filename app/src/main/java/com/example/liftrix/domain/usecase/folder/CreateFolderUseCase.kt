package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.FolderId
import com.example.liftrix.domain.model.FolderName
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.ProfileRepository
import timber.log.Timber
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
    private val folderRepository: FolderRepository,
    private val profileRepository: ProfileRepository
) {
    
    /**
     * Creates a new folder for the specified user
     * 
     * @param input The folder creation input containing userId and name
     * @return Result containing the created folder or error
     */
    suspend operator fun invoke(input: CreateFolderInput): LiftrixResult<Folder> {
        return liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "🔥 CREATE-FOLDER-USE-CASE: Exception during folder creation")
                when (throwable) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = "folderName",
                        violations = listOf(throwable.message ?: "Invalid input"),
                        analyticsContext = mapOf("operation" to "CREATE_FOLDER")
                    )
                    else -> LiftrixError.BusinessLogicError(
                        code = "FOLDER_CREATION_FAILED",
                        errorMessage = "Failed to create folder: ${throwable.message}",
                        analyticsContext = mapOf("operation" to "CREATE_FOLDER", "userId" to input.userId)
                    )
                }
            }
        ) {
            Timber.d("🔥 CREATE-FOLDER-USE-CASE: Starting folder creation - userId: '${input.userId}', name: '${input.name}'")
            
            validateInput(input)
            Timber.d("🔥 CREATE-FOLDER-USE-CASE: Input validation passed")
            
            // Ensure user profile exists (required for foreign key constraint)
            val hasProfileBefore = profileRepository.hasProfile(input.userId)
            Timber.d("🔥 CREATE-FOLDER-USE-CASE: Profile existence check - hasProfile: $hasProfileBefore")
            
            if (!hasProfileBefore) {
                Timber.d("🔥 CREATE-FOLDER-USE-CASE: User profile doesn't exist, creating minimal profile")
                val minimalProfile = UserProfile.createMinimal(input.userId)
                profileRepository.saveProfile(minimalProfile).fold(
                    onSuccess = {
                        Timber.d("🔥 CREATE-FOLDER-USE-CASE: Minimal user profile created successfully")
                        
                        // Re-verify profile was saved
                        val hasProfileAfter = profileRepository.hasProfile(input.userId)
                        Timber.d("🔥 CREATE-FOLDER-USE-CASE: Profile existence after save - hasProfile: $hasProfileAfter")
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "🔥 CREATE-FOLDER-USE-CASE: Failed to create minimal user profile")
                        throw exception
                    }
                )
            } else {
                Timber.d("🔥 CREATE-FOLDER-USE-CASE: User profile already exists")
            }
            
            // Double-check profile exists before proceeding with folder creation
            val profileExistsNow = profileRepository.hasProfile(input.userId)
            Timber.d("🔥 CREATE-FOLDER-USE-CASE: Final profile existence check before folder creation - hasProfile: $profileExistsNow")
            
            if (!profileExistsNow) {
                Timber.e("🔥 CREATE-FOLDER-USE-CASE: CRITICAL - Profile still doesn't exist after creation attempt!")
                throw IllegalStateException("Unable to ensure user profile exists for user: ${input.userId}")
            }
            
            // Check if folder name already exists for this user
            val trimmedName = input.name.trim()
            val nameExists = folderRepository.doesFolderNameExist(input.userId, trimmedName)
            Timber.d("🔥 CREATE-FOLDER-USE-CASE: Name existence check - exists: $nameExists")
            
            if (nameExists) {
                Timber.w("🔥 CREATE-FOLDER-USE-CASE: Folder name '$trimmedName' already exists for user '${input.userId}'")
                throw IllegalArgumentException("Folder name '$trimmedName' already exists")
            }
            
            val folder = Folder(
                id = FolderId.generate(),
                userId = input.userId,
                name = FolderName(trimmedName),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                templateCount = 0
            )
            Timber.d("🔥 CREATE-FOLDER-USE-CASE: Created folder object - id: '${folder.id.value}', name: '${folder.name.value}'")
            
            val result = folderRepository.createFolder(folder)
            result.fold(
                onSuccess = { createdFolder ->
                    Timber.d("🔥 CREATE-FOLDER-USE-CASE: Repository createFolder completed successfully - folder: ${createdFolder.name.value}")
                    createdFolder
                },
                onFailure = { exception ->
                    Timber.e(exception, "🔥 CREATE-FOLDER-USE-CASE: Repository createFolder FAILED")
                    throw exception
                }
            )
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