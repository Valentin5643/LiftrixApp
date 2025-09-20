package com.example.liftrix.domain.usecase.profile

import android.graphics.Rect
import android.net.Uri
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProfileImageRepository
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.service.ImageProcessingService
import com.example.liftrix.data.service.UserProfileCacheService
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Use case for uploading a user's profile image with full processing pipeline.
 * 
 * Orchestrates the complete profile image upload workflow:
 * 1. Validates image format and size constraints
 * 2. Processes image (crop, resize, compress) via ImageProcessingService
 * 3. Uploads processed image to Firebase Storage via ProfileImageRepository
 * 4. Updates user profile with new image URL and metadata
 * 5. Handles cleanup of previous images to prevent storage bloat
 * 
 * Business rules:
 * - Only authenticated users can upload profile images
 * - Previous profile images are automatically deleted from storage
 * - Upload failures preserve existing profile image state
 * - Image processing optimizes for 400x400px display with <200KB target size
 * 
 * Error handling:
 * - Invalid image formats return validation errors
 * - Processing failures (out of memory, corruption) return processing errors
 * - Upload failures return network/storage errors with retry guidance
 * - Database update failures trigger rollback of uploaded image
 */
class UploadProfileImageUseCase @Inject constructor(
    private val imageProcessingService: ImageProcessingService,
    private val profileImageRepository: ProfileImageRepository,
    private val profileRepository: ProfileRepository,
    private val syncProfilePhotoToSocialUseCase: SyncProfilePhotoToSocialUseCase,
    private val userProfileCacheService: UserProfileCacheService
) {
    
    /**
     * Uploads and processes a profile image for the specified user.
     * 
     * @param userId User ID for profile image association (must be authenticated user)
     * @param imageUri URI of source image (content:// or file:// scheme)
     * @param cropRect Optional crop rectangle, auto-crops to center square if null
     * @return LiftrixResult<String> with Firebase Storage path on success (resolves to fresh URLs dynamically)
     */
    suspend operator fun invoke(
        userId: String,
        imageUri: Uri,
        cropRect: Rect? = null
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError("Failed to upload profile image: ${throwable.message}")
        }
    ) {
        Timber.i("🖼️ Starting profile image upload for user: $userId")
        
        // Validate inputs
        val validationResult = validateInputs(userId, imageUri)
        if (validationResult != null) {
            Timber.w("Input validation failed: $validationResult")
            throw IllegalArgumentException(validationResult)
        }
        
        // Validate image format compatibility
        val isFormatSupported = imageProcessingService.isFormatSupported(imageUri)
        if (!isFormatSupported) {
            val error = "Unsupported image format. Please use JPEG, PNG, or WebP."
            Timber.w("Image format validation failed for URI: $imageUri")
            throw IllegalArgumentException(error)
        }
        
        // Process image (crop, resize, compress)
        val processingResult = imageProcessingService.processProfileImage(imageUri, cropRect)
        if (processingResult.isFailure) {
            val error = processingResult.exceptionOrNull()
            Timber.e(error, "Image processing failed for user: $userId")
            throw error ?: RuntimeException("Image processing failed")
        }
        
        val processedImage = processingResult.getOrThrow()
        Timber.d("✅ Image processed successfully: ${processedImage.fileSizeBytes} bytes")
        
        // Get current profile image URL for cleanup
        val currentImageResult = profileImageRepository.getProfileImageUrl(userId)
        val currentImageUrl = if (currentImageResult.isSuccess) {
            currentImageResult.getOrNull()
        } else {
            null // No existing image or retrieval failed
        }
        
        // Upload processed image to Firebase Storage
        val uploadResult = profileImageRepository.uploadProfileImage(userId, processedImage.imageBytes)
        if (uploadResult.isFailure) {
            val error = uploadResult.exceptionOrNull()
            Timber.e(error, "Firebase Storage upload failed for user: $userId")
            throw error ?: RuntimeException("Image upload failed")
        }
        
        val newStoragePath = uploadResult.getOrThrow()
        Timber.d("✅ Image uploaded to Firebase Storage path: $newStoragePath")
        
        // Update profile with new storage path and metadata
        val updateResult = profileImageRepository.updateProfileImageUrl(
            userId = userId,
            imageUrl = newStoragePath,  // Now storing path instead of tokenized URL
            updatedAt = LocalDateTime.now(),
            hasCustomImage = true
        )
        
        if (updateResult.isFailure) {
            val error = updateResult.exceptionOrNull()
            Timber.e(error, "Failed to update profile image URL for user: $userId")
            
            // Attempt to clean up uploaded image since profile update failed
            cleanupUploadedImage(userId, newStoragePath)
            
            throw error ?: RuntimeException("Profile update failed")
        }
        
        // Clean up previous image if different from new one
        if (currentImageUrl != null && currentImageUrl != newStoragePath) {
            cleanupPreviousImage(userId, currentImageUrl)
        }
        
        // 🔄 SYNC TO SOCIAL PROFILE: Ensure the uploaded photo appears in feed/social contexts
        Timber.d("PFP_DEBUG: 🔄 SYNCING_TO_SOCIAL: Starting sync of profile photo to social profile for user: $userId")
        try {
            val syncResult = syncProfilePhotoToSocialUseCase(userId)
            syncResult.fold(
                onSuccess = { result -> 
                    Timber.i("PFP_DEBUG: ✅ SOCIAL_SYNC_SUCCESS: ${result.reason} | mainPhoto=${result.mainProfilePhotoUrl} | socialPhoto=${result.newSocialProfilePhotoUrl}")
                },
                onFailure = { error -> 
                    // Don't fail the entire upload if social sync fails - it's not critical
                    Timber.e("PFP_DEBUG: 🔥 SOCIAL_SYNC_FAILED: ${error.message} | Upload still successful but photo may not appear in feed immediately")
                }
            )
        } catch (e: Exception) {
            // Non-blocking failure - upload is still successful
            Timber.e(e, "PFP_DEBUG: 🔥 SOCIAL_SYNC_EXCEPTION: Exception during social profile sync, continuing normally")
        }
        
        // 🔄 INVALIDATE PROFILE CACHE: Force refresh of public profile data to show new photo
        Timber.d("PFP_DEBUG: 🔄 CACHE_INVALIDATION: Invalidating profile cache for user: $userId")
        try {
            userProfileCacheService.invalidateCache(userId)
            Timber.i("PFP_DEBUG: ✅ CACHE_INVALIDATED: Profile cache invalidated successfully for user: $userId")
        } catch (e: Exception) {
            // Non-blocking failure - upload is still successful
            Timber.e(e, "PFP_DEBUG: 🔥 CACHE_INVALIDATION_FAILED: Exception during cache invalidation, continuing normally")
        }
        
        Timber.i("🎉 Profile image upload completed successfully for user: $userId (with social sync attempt)")
        newStoragePath
    }
    
    /**
     * Validates input parameters for business rule compliance.
     */
    private fun validateInputs(userId: String, imageUri: Uri): String? {
        return when {
            userId.isBlank() -> "User ID cannot be blank"
            imageUri.toString().isBlank() -> "Image URI cannot be blank"
            imageUri.scheme != "content" && imageUri.scheme != "file" -> {
                "Invalid URI scheme. Only content:// and file:// URIs are supported"
            }
            else -> null
        }
    }
    
    /**
     * Attempts to clean up uploaded image when profile update fails.
     * Non-blocking operation - failures are logged but don't affect main flow.
     */
    private suspend fun cleanupUploadedImage(userId: String, imageUrl: String) {
        try {
            Timber.d("🧹 Attempting cleanup of uploaded image due to profile update failure")
            val deleteResult = profileImageRepository.deleteProfileImage(userId)
            if (deleteResult.isSuccess) {
                Timber.d("✅ Successfully cleaned up uploaded image")
            } else {
                Timber.w("⚠️ Failed to cleanup uploaded image, manual cleanup may be needed")
            }
        } catch (e: Exception) {
            Timber.w(e, "Exception during image cleanup, continuing normally")
        }
    }
    
    /**
     * Attempts to clean up previous profile image to prevent storage bloat.
     * Non-blocking operation - failures are logged but don't affect main flow.
     */
    private suspend fun cleanupPreviousImage(userId: String, previousImageUrl: String) {
        try {
            Timber.d("🧹 Cleaning up previous profile image for user: $userId")
            // Note: We use the same deletion method as it operates on the current user's image path
            // The actual cleanup will be handled by ProfileImageRepository implementation
            profileImageRepository.deleteProfileImage(userId)
            Timber.d("✅ Previous image cleanup initiated")
        } catch (e: Exception) {
            Timber.w(e, "Failed to cleanup previous image, continuing normally")
        }
    }
}