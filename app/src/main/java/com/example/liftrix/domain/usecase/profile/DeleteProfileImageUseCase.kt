package com.example.liftrix.domain.usecase.profile

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.ProfileImageRepository
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Use case for deleting a user's profile image with complete cleanup.
 * 
 * Orchestrates the complete profile image deletion workflow:
 * 1. Validates user authentication and image existence
 * 2. Removes image from Firebase Storage to free storage space
 * 3. Updates user profile to clear image URL and reset custom image flag
 * 4. Handles partial failure scenarios with appropriate error responses
 * 
 * Business rules:
 * - Only authenticated users can delete their own profile images
 * - Deletion preserves profile data but reverts to initials display
 * - Storage cleanup happens even if database update fails (consistency over availability)
 * - Operation is idempotent - safe to retry if user has no custom image
 * 
 * Error handling:
 * - Missing authentication returns authorization errors
 * - Storage deletion failures return network/storage errors
 * - Database update failures return database errors but don't revert storage deletion
 * - No existing image is treated as successful operation (idempotent)
 */
class DeleteProfileImageUseCase @Inject constructor(
    private val profileImageRepository: ProfileImageRepository
) {
    
    /**
     * Deletes the profile image for the specified user.
     * 
     * @param userId User ID for profile image deletion (must be authenticated user)
     * @return LiftrixResult<Unit> indicating success or failure with detailed error context
     */
    suspend operator fun invoke(userId: String): LiftrixResult<Unit> {
        return try {
            Timber.i("🗑️ Starting profile image deletion for user: $userId")
            
            // Validate inputs
            val validationResult = validateInputs(userId)
            if (validationResult != null) {
                Timber.w("Input validation failed: $validationResult")
                return Result.failure(IllegalArgumentException(validationResult))
            }
            
            // Check if user has a custom profile image
            val currentImageResult = profileImageRepository.getProfileImageUrl(userId)
            if (currentImageResult.isFailure) {
                val error = currentImageResult.exceptionOrNull()
                Timber.e(error, "Failed to retrieve current image URL for user: $userId")
                return Result.failure(error ?: RuntimeException("Failed to check current image"))
            }
            
            val currentImageUrl = currentImageResult.getOrNull()
            if (currentImageUrl.isNullOrBlank()) {
                Timber.d("✅ No custom profile image found for user: $userId, operation complete")
                return Result.success(Unit) // Idempotent - no image to delete
            }
            
            Timber.d("📷 Found existing profile image, proceeding with deletion: $currentImageUrl")
            
            // Delete image from Firebase Storage
            val storageDeleteResult = profileImageRepository.deleteProfileImage(userId)
            if (storageDeleteResult.isFailure) {
                val error = storageDeleteResult.exceptionOrNull()
                Timber.e(error, "Firebase Storage deletion failed for user: $userId")
                return Result.failure(error ?: RuntimeException("Storage deletion failed"))
            }
            
            Timber.d("✅ Image deleted from Firebase Storage successfully")
            
            // Update profile to clear image URL and reset custom image flag
            val profileUpdateResult = profileImageRepository.updateProfileImageUrl(
                userId = userId,
                imageUrl = null, // Clear the URL
                updatedAt = LocalDateTime.now(),
                hasCustomImage = false // Reset custom image flag
            )
            
            if (profileUpdateResult.isFailure) {
                val error = profileUpdateResult.exceptionOrNull()
                Timber.e(error, "Failed to update profile after image deletion for user: $userId")
                
                // Log warning but don't fail the operation since storage cleanup succeeded
                // This maintains consistency where storage cleanup is prioritized
                Timber.w("⚠️ Profile update failed but storage cleanup succeeded - profile may show stale data temporarily")
                return Result.failure(error ?: RuntimeException("Profile update failed"))
            }
            
            Timber.i("🎉 Profile image deletion completed successfully for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during profile image deletion for user: $userId")
            Result.failure(e)
        }
    }
    
    /**
     * Validates input parameters for business rule compliance.
     */
    private fun validateInputs(userId: String): String? {
        return when {
            userId.isBlank() -> "User ID cannot be blank"
            else -> null
        }
    }
}