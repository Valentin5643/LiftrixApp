package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.validation.ProfileValidator
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for creating a new social profile with validation and privacy defaults.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
class CreateSocialProfileUseCase @Inject constructor(
    private val repository: SocialProfileRepository,
    private val validator: ProfileValidator,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    suspend operator fun invoke(
        username: String,
        displayName: String,
        bio: String?
    ): LiftrixResult<SocialProfile> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CREATE_SOCIAL_PROFILE_FAILED",
                errorMessage = "Failed to create social profile: ${throwable.message ?: "Unknown error"}",
                analyticsContext = mapOf(
                    "username" to username,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        val userId = getCurrentUserIdUseCase() 
            ?: throw IllegalStateException("User not authenticated")
        
        Timber.i("[SOCIAL-PROFILE] 🔨 Creating social profile for user: $userId")
        Timber.d("[SOCIAL-PROFILE]   - Username: '$username'")
        Timber.d("[SOCIAL-PROFILE]   - Display Name: '$displayName'")
        Timber.d("[SOCIAL-PROFILE]   - Bio: ${if (bio != null) "'$bio'" else "null"}")
        

        // Validate inputs
        Timber.d("[SOCIAL-PROFILE] 🔍 Validating input fields...")
        validator.validateUsername(username).getOrThrow()
        Timber.d("[SOCIAL-PROFILE]   ✅ Username validation passed")
        validator.validateDisplayName(displayName).getOrThrow()
        Timber.d("[SOCIAL-PROFILE]   ✅ Display name validation passed")
        bio?.let { 
            validator.validateBio(it).getOrThrow()
            Timber.d("[SOCIAL-PROFILE]   ✅ Bio validation passed")
        }

        // Check username availability with cleanup
        Timber.d("[SOCIAL-PROFILE] 🔎 Checking username availability: '$username'")
        val isUsernameAvailable = repository.checkUsernameAvailability(username).getOrThrow()
        Timber.d("[SOCIAL-PROFILE]   - Username available: $isUsernameAvailable")
        if (!isUsernameAvailable) {
            Timber.w("[SOCIAL-PROFILE] ⚠️ Username '$username' is taken, attempting cleanup...")
            
            // Try to clean up orphaned username from previous failed signups
            val cleanupResult = repository.cleanupOrphanedUsername(username, userId).getOrThrow()
            if (cleanupResult) {
                Timber.i("[SOCIAL-PROFILE] ✅ Username '$username' cleaned up and is now available")
                // Username is now available, continue
            } else {
                Timber.e("[SOCIAL-PROFILE] ❌ Username '$username' is already taken and cleanup failed")
                throw IllegalArgumentException("Username '$username' is already taken")
            }
        } else {
            Timber.d("[SOCIAL-PROFILE] ✅ Username '$username' is available")
        }

        // Create profile with social-enabled defaults
        val now = System.currentTimeMillis()
        val profile = SocialProfile(
            userId = userId,
            username = username.lowercase().trim(),
            displayName = displayName.trim(),
            bio = bio?.trim(),
            memberSince = now,
            isPrivate = false, // Public by default for maximum discoverability
            hideFromSuggestions = false, // Show in user suggestions and discovery
            allowFriendRequests = true, // Enable follow requests by default
            createdAt = now,
            updatedAt = now
        )
        
        Timber.d("[SOCIAL-PROFILE] 📦 Creating social profile in repository...")
        Timber.d("[SOCIAL-PROFILE]   - Public by default: ${!profile.isPrivate}")
        Timber.d("[SOCIAL-PROFILE]   - Discoverable: ${!profile.hideFromSuggestions}")
        Timber.d("[SOCIAL-PROFILE]   - Allow friend requests: ${profile.allowFriendRequests}")
        
        val result = repository.createProfile(profile).getOrThrow()
        
        Timber.i("[SOCIAL-PROFILE] ✅ Social profile created successfully for user: $userId")
        Timber.i("[SOCIAL-PROFILE]   - User should now appear in search results")
        Timber.i("[SOCIAL-PROFILE]   - Profile will sync to Firebase social_profiles collection")
        
        result
    }
}

