package com.example.liftrix.data.repository

import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProfileImageRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ProfileImageRepository providing Firebase Storage integration
 * with user-scoped security and offline-first database management.
 * 
 * Architecture:
 * - Firebase Storage for image file storage with user-scoped paths
 * - Room database for URL caching and offline-first experience
 * - Firebase Auth for security context and user verification
 * 
 * Storage Structure:
 * profile_images/{userId}/avatar.jpg - Current profile image
 * 
 * Security:
 * - All operations validated against authenticated user ID
 * - Firebase Security Rules enforce user-scoped access
 * - Storage paths include user ID to prevent cross-user access
 */
@Singleton
class ProfileImageRepositoryImpl @Inject constructor(
    private val firebaseStorage: FirebaseStorage,
    private val firebaseAuth: FirebaseAuth,
    private val userProfileDao: UserProfileDao,
    private val dateTimeConverters: DateTimeConverters
) : ProfileImageRepository {
    
    companion object {
        private const val STORAGE_PATH_PROFILE_IMAGES = "profile_images"
        private const val PROFILE_IMAGE_FILENAME = "avatar.jpg"
    }
    
    override suspend fun uploadProfileImage(
        userId: String,
        imageBytes: ByteArray
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable -> 
            LiftrixError.NetworkError("Failed to upload profile image: ${throwable.message}")
        }
    ) {
        Timber.d("🔄 Uploading profile image for user: $userId, size: ${imageBytes.size} bytes")
        
        // Validate authentication and user context
        validateUserAuthentication(userId)
        
        // Create user-scoped storage reference
        val storageRef = firebaseStorage.reference
            .child(STORAGE_PATH_PROFILE_IMAGES)
            .child(userId)
            .child(PROFILE_IMAGE_FILENAME)
        
        // Upload image bytes to Firebase Storage
        val uploadTask = storageRef.putBytes(imageBytes).await()
        Timber.d("✅ Upload task completed: ${uploadTask.totalByteCount} bytes")
        
        // Get download URL for the uploaded image
        val downloadUrl = storageRef.downloadUrl.await()
        val downloadUrlString = downloadUrl.toString()
        
        Timber.i("🎉 Profile image uploaded successfully: $downloadUrlString")
        downloadUrlString
    }
    
    override suspend fun deleteProfileImage(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError("Failed to delete profile image: ${throwable.message}")
        }
    ) {
        Timber.d("🗑️ Deleting profile image for user: $userId")
        
        // Validate authentication and user context
        validateUserAuthentication(userId)
        
        // Create user-scoped storage reference
        val storageRef = firebaseStorage.reference
            .child(STORAGE_PATH_PROFILE_IMAGES)
            .child(userId)
            .child(PROFILE_IMAGE_FILENAME)
        
        // Delete image from Firebase Storage
        storageRef.delete().await()
        
        Timber.i("✅ Profile image deleted successfully for user: $userId")
    }
    
    override suspend fun getProfileImageUrl(userId: String): LiftrixResult<String?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DatabaseError("Failed to retrieve profile image URL: ${throwable.message}")
        }
    ) {
        Timber.d("📖 Retrieving profile image URL for user: $userId")
        
        // Query local database for cached URL (offline-first)
        val imageUrl = userProfileDao.getProfileImageUrl(userId)
        
        Timber.d("📷 Profile image URL retrieved: ${if (imageUrl != null) "present" else "null"}")
        imageUrl
    }
    
    override suspend fun updateProfileImageUrl(
        userId: String,
        imageUrl: String?,
        updatedAt: LocalDateTime?,
        hasCustomImage: Boolean
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DatabaseError("Failed to update profile image URL: ${throwable.message}")
        }
    ) {
        Timber.d("💾 Updating profile image URL for user: $userId, hasCustom: $hasCustomImage")
        
        // Convert LocalDateTime to String for database storage
        val updatedAtString = updatedAt?.let { dateTimeConverters.fromLocalDateTime(it) }
        
        // Update profile image fields in database
        val updatedRows = userProfileDao.updateProfileImage(
            userId = userId,
            imageUrl = imageUrl,
            updatedAt = updatedAtString,
            hasCustom = hasCustomImage
        )
        
        if (updatedRows == 0) {
            Timber.w("⚠️ No profile found to update for user: $userId")
            throw IllegalStateException("Profile not found for user: $userId")
        }
        
        Timber.d("✅ Profile image URL updated successfully: $updatedRows row(s) affected")
    }
    
    override suspend fun hasCustomProfileImage(userId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DatabaseError("Failed to check custom profile image status: ${throwable.message}")
        }
    ) {
        Timber.d("🔍 Checking custom profile image status for user: $userId")
        
        // Query database for custom image flag
        val hasCustom = userProfileDao.hasCustomProfileImage(userId) ?: false
        
        Timber.d("📷 Custom profile image status: $hasCustom")
        hasCustom
    }
    
    /**
     * Validates that the current user is authenticated and matches the provided userId.
     * Throws SecurityException if validation fails.
     */
    private fun validateUserAuthentication(userId: String) {
        val currentUser = firebaseAuth.currentUser
            ?: throw SecurityException("User must be authenticated to manage profile images")
        
        if (currentUser.uid != userId) {
            throw SecurityException("User can only manage their own profile images")
        }
        
        Timber.d("✅ User authentication validated for profile image operation")
    }
}