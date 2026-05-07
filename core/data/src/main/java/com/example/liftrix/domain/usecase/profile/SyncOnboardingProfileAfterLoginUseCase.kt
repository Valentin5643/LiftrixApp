package com.example.liftrix.domain.usecase.profile

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProfileRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case to sync onboarding profile data after user login.
 * This ensures that any profile data collected during Getting Started
 * is properly synced to Firebase once the user is authenticated.
 * 
 * Critical for ensuring onboarding data persists across the authentication boundary.
 */
class SyncOnboardingProfileAfterLoginUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    
    /**
     * Checks for unsynced profile data and triggers immediate sync after login.
     * 
     * @param userId The authenticated user's ID
     * @return Result indicating success or failure of the sync operation
     */
    suspend operator fun invoke(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ONBOARDING_SYNC_FAILED",
                errorMessage = "Failed to sync onboarding profile after login: ${throwable.message}",
                analyticsContext = mapOf(
                    "userId" to userId,
                    "operation" to "POST_LOGIN_PROFILE_SYNC"
                )
            )
        }
    ) {
        Timber.d("Checking for unsynced onboarding profile data for user: $userId")
        
        // Check if user has a profile
        val hasProfile = profileRepository.hasProfile(userId)
        if (!hasProfile) {
            Timber.d("No profile found for user $userId - skipping sync")
            return@liftrixCatching Unit
        }
        
        // Check if there's unsynced data
        val unsyncedCount = profileRepository.getUnsyncedCount(userId)
        if (unsyncedCount > 0) {
            Timber.d("Found $unsyncedCount unsynced profile entries for user $userId - triggering immediate sync")
            
            // Queue the sync operation
            val queueResult = profileRepository.queueSync(userId)
            if (queueResult.isFailure) {
                Timber.w("Failed to queue profile sync: ${queueResult.exceptionOrNull()?.message}")
            }
            
            // Trigger immediate sync
            val syncResult = profileRepository.syncNow(userId)
            if (syncResult.isSuccess) {
                Timber.d("Successfully synced onboarding profile data for user: $userId")
            } else {
                Timber.e("Failed to sync onboarding profile immediately: ${syncResult.exceptionOrNull()?.message}")
                // Don't fail the entire operation - the queued sync will retry
            }
        } else {
            Timber.d("Profile already synced for user $userId")
        }
        
        Unit
    }
    
    /**
     * Convenience method to check if sync is needed without performing it.
     * Useful for UI indicators or conditional logic.
     */
    suspend fun isSyncNeeded(userId: String): Boolean {
        return try {
            profileRepository.hasProfile(userId) && 
            profileRepository.getUnsyncedCount(userId) > 0
        } catch (e: Exception) {
            Timber.e(e, "Error checking sync status for user: $userId")
            false
        }
    }
}