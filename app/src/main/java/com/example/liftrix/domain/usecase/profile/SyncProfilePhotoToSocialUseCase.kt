package com.example.liftrix.domain.usecase.profile

import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.sync.SocialProfileSyncWorker
import androidx.work.WorkManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for synchronizing profile photos from UserProfile to SocialProfile.
 * 
 * This use case addresses the issue where profile photo changes in the main UserProfile
 * are not reflected in the SocialProfile, causing posts to show null profile photos
 * even when the user has uploaded a profile image.
 * 
 * Business Logic:
 * 1. Retrieves the current profile photo URL from UserProfile
 * 2. Updates the corresponding SocialProfile with the same photo URL
 * 3. Triggers sync to Firebase to make changes searchable
 * 4. Provides comprehensive logging for diagnosis
 * 
 * Usage Scenarios:
 * - Called automatically when profile photos are uploaded/updated
 * - Can be used for one-time migration of existing users with mismatched photos
 * - Debugging tool for investigating profile photo sync issues
 */
class SyncProfilePhotoToSocialUseCase @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val socialProfileDao: SocialProfileDao,
    private val workManager: WorkManager
) {
    
    /**
     * Synchronizes profile photo from main UserProfile to SocialProfile
     * 
     * @param userId The user whose profile photo should be synchronized
     * @return LiftrixResult indicating success or failure with diagnostic information
     */
    suspend operator fun invoke(userId: String): LiftrixResult<ProfilePhotoSyncResult> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_PHOTO_SYNC_FAILED",
                errorMessage = "Failed to sync profile photo to social profile",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "operation" to "SYNC_PROFILE_PHOTO_TO_SOCIAL"
                )
            )
        }
    ) {
        Timber.d("[PROFILE-PHOTO-SYNC-UC] Starting sync for user: $userId")
        
        // Get current photo URL from main profile
        val mainProfileImageUrl = userProfileDao.getProfileImageUrl(userId)
        Timber.d("[PROFILE-PHOTO-SYNC-UC] Main profile photo: ${mainProfileImageUrl ?: "null"}")
        
        // Check if social profile exists
        val hasSocialProfile = socialProfileDao.hasProfile(userId)
        
        if (!hasSocialProfile) {
            Timber.i("[PROFILE-PHOTO-SYNC-UC] No social profile found - creating fallback profile")
            return@liftrixCatching ProfilePhotoSyncResult(
                syncPerformed = false,
                reason = "No social profile exists - will be created with photo during next feed access",
                mainProfilePhotoUrl = mainProfileImageUrl,
                socialProfilePhotoUrl = null,
                socialProfileExists = false
            )
        }
        
        // Get current social profile photo for comparison
        val currentSocialProfile = socialProfileDao.getSocialProfileByUserId(userId)
        val currentSocialPhotoUrl = currentSocialProfile?.profilePhotoUrl?.takeIf { it.isNotBlank() }
        
        Timber.d("[PROFILE-PHOTO-SYNC-UC] Current social profile photo: ${currentSocialPhotoUrl ?: "null"}")
        
        // Check if sync is needed
        if (mainProfileImageUrl == currentSocialPhotoUrl) {
            Timber.d("[PROFILE-PHOTO-SYNC-UC] Photos already match - no sync needed")
            return@liftrixCatching ProfilePhotoSyncResult(
                syncPerformed = false,
                reason = "Profile photos already match",
                mainProfilePhotoUrl = mainProfileImageUrl,
                socialProfilePhotoUrl = currentSocialPhotoUrl,
                socialProfileExists = true
            )
        }
        
        // Perform the sync
        Timber.i("[PROFILE-PHOTO-SYNC-UC] Syncing profile photo: main=${mainProfileImageUrl ?: "null"} -> social=${currentSocialPhotoUrl ?: "null"}")
        
        val updatedAt = System.currentTimeMillis()
        val rowsUpdated = socialProfileDao.updateProfilePhoto(userId, mainProfileImageUrl, updatedAt)
        
        if (rowsUpdated > 0) {
            Timber.i("[PROFILE-PHOTO-SYNC-UC] ✅ Social profile photo updated successfully")
            
            // Trigger social profile sync to Firebase to make changes searchable
            try {
                val socialSyncRequest = SocialProfileSyncWorker.createWorkRequest(userId, forceSync = true)
                workManager.enqueue(socialSyncRequest)
                Timber.d("[PROFILE-PHOTO-SYNC-UC] Social profile sync to Firebase triggered")
            } catch (e: Exception) {
                Timber.e(e, "[PROFILE-PHOTO-SYNC-UC] Failed to trigger Firebase sync")
            }
            
            return@liftrixCatching ProfilePhotoSyncResult(
                syncPerformed = true,
                reason = "Successfully synchronized profile photo",
                mainProfilePhotoUrl = mainProfileImageUrl,
                socialProfilePhotoUrl = currentSocialPhotoUrl,
                socialProfileExists = true,
                newSocialProfilePhotoUrl = mainProfileImageUrl
            )
        } else {
            Timber.w("[PROFILE-PHOTO-SYNC-UC] No social profile rows updated - profile may not exist")
            return@liftrixCatching ProfilePhotoSyncResult(
                syncPerformed = false,
                reason = "No social profile rows updated - profile may not exist",
                mainProfilePhotoUrl = mainProfileImageUrl,
                socialProfilePhotoUrl = currentSocialPhotoUrl,
                socialProfileExists = false
            )
        }
    }
    
    /**
     * Bulk synchronization for multiple users - useful for migrations
     */
    suspend fun syncMultipleUsers(userIds: List<String>): LiftrixResult<List<ProfilePhotoSyncResult>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "BULK_PROFILE_PHOTO_SYNC_FAILED",
                errorMessage = "Failed to perform bulk profile photo sync",
                analyticsContext = mapOf(
                    "user_count" to userIds.size.toString(),
                    "operation" to "BULK_SYNC_PROFILE_PHOTOS"
                )
            )
        }
    ) {
        Timber.i("[PROFILE-PHOTO-SYNC-UC] Starting bulk sync for ${userIds.size} users")
        
        val results = mutableListOf<ProfilePhotoSyncResult>()
        var successCount = 0
        var failureCount = 0
        
        for (userId in userIds) {
            try {
                val result = invoke(userId)
                result.fold(
                    onSuccess = { syncResult ->
                        results.add(syncResult)
                        if (syncResult.syncPerformed) successCount++ else failureCount++
                    },
                    onFailure = { error ->
                        failureCount++
                        Timber.e("[PROFILE-PHOTO-SYNC-UC] Failed to sync user $userId: $error")
                    }
                )
            } catch (e: Exception) {
                failureCount++
                Timber.e(e, "[PROFILE-PHOTO-SYNC-UC] Exception during sync for user $userId")
            }
        }
        
        Timber.i("[PROFILE-PHOTO-SYNC-UC] Bulk sync completed: $successCount succeeded, $failureCount failed")
        results
    }
}

/**
 * Result data class for profile photo sync operations
 */
data class ProfilePhotoSyncResult(
    val syncPerformed: Boolean,
    val reason: String,
    val mainProfilePhotoUrl: String?,
    val socialProfilePhotoUrl: String?,
    val socialProfileExists: Boolean,
    val newSocialProfilePhotoUrl: String? = null
)