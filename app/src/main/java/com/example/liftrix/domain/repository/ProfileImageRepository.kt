package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.common.LiftrixResult
import java.time.LocalDateTime

/**
 * Repository interface for managing user profile images with Firebase Storage integration.
 * 
 * Provides comprehensive profile image management including:
 * - Secure upload/download with user scoping
 * - Automatic cleanup of previous images
 * - Cache-friendly URL management
 * - Offline-first database integration
 * 
 * All operations are user-scoped for security and comply with Firebase Storage rules.
 * Database operations follow the offline-first pattern with Room as source of truth.
 */
interface ProfileImageRepository {
    
    /**
     * Uploads a processed profile image to Firebase Storage for the specified user.
     * 
     * Automatically generates user-scoped storage path and manages file naming.
     * Previous images are automatically cleaned up to prevent storage bloat.
     * 
     * @param userId User ID for scoped storage (must match authenticated user)
     * @param imageBytes Processed image data (JPEG format from ImageProcessingService)
     * @return LiftrixResult<String> with Firebase Storage path on success (not tokenized URL)
     */
    suspend fun uploadProfileImage(
        userId: String,
        imageBytes: ByteArray
    ): LiftrixResult<String>
    
    /**
     * Deletes the profile image from Firebase Storage for the specified user.
     * 
     * Removes the image file from user-scoped storage path.
     * Operation is idempotent - safe to call even if no image exists.
     * 
     * @param userId User ID for scoped deletion (must match authenticated user)
     * @return LiftrixResult<Unit> indicating success or failure
     */
    suspend fun deleteProfileImage(userId: String): LiftrixResult<Unit>
    
    /**
     * Retrieves the current profile image URL from local database.
     * 
     * Returns cached URL from Room database for offline-first experience.
     * URL may be null if user has no custom profile image.
     * 
     * @param userId User ID to query for image URL
     * @return LiftrixResult<String?> with image URL or null if no custom image
     */
    suspend fun getProfileImageUrl(userId: String): LiftrixResult<String?>
    
    /**
     * Updates the profile image URL and metadata in local database.
     * 
     * Updates Room database with new image URL, timestamp, and custom image flag.
     * Triggers profile completion recalculation if applicable.
     * 
     * @param userId User ID for profile update
     * @param imageUrl New Firebase Storage URL (null to clear)
     * @param updatedAt Timestamp of image update for cache invalidation
     * @param hasCustomImage Flag indicating whether user has uploaded custom image
     * @return LiftrixResult<Unit> indicating success or failure
     */
    suspend fun updateProfileImageUrl(
        userId: String,
        imageUrl: String?,
        updatedAt: LocalDateTime?,
        hasCustomImage: Boolean
    ): LiftrixResult<Unit>
    
    /**
     * Checks if the specified user has a custom profile image.
     * 
     * Queries local database for hasCustomProfileImage flag.
     * Used for UI state management and profile completion calculations.
     * 
     * @param userId User ID to check for custom image
     * @return LiftrixResult<Boolean> indicating if user has custom image
     */
    suspend fun hasCustomProfileImage(userId: String): LiftrixResult<Boolean>
}