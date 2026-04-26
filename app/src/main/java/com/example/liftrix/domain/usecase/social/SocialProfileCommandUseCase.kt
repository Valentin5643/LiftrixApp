package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.social.SocialPrivacySettingsRepository
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.validation.ProfileValidator
import timber.log.Timber
import javax.inject.Inject

/**
 * Consolidated use case for social profile command operations.
 *
 * Consolidates:
 * - CreateSocialProfileUseCase (profile creation with validation)
 * - UpdateSocialProfileUseCase (profile updates)
 * - UpdateSocialPrivacySettingsUseCase (privacy settings updates)
 *
 * Part of Phase 3: Social & Workout Domains consolidation.
 * Ref: SPEC-20251031-usecase-consolidation.md
 */
class SocialProfileCommandUseCase @Inject constructor(
    private val profileRepository: SocialProfileRepository,
    private val privacyRepository: SocialPrivacySettingsRepository,
    private val validator: ProfileValidator,
    private val authQueryUseCase: AuthQueryUseCase
) {
    /**
     * Creates a new social profile with validation and privacy defaults.
     * Replaces: CreateSocialProfileUseCase.invoke()
     *
     * @param username Unique username for the profile
     * @param displayName Display name for the profile
     * @param bio Optional bio/description
     * @return The created social profile
     */
    suspend fun create(
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
        val userId = authQueryUseCase(waitForAuth = false).getOrNull()
            ?: throw IllegalStateException("User not authenticated")

        Timber.i("[SOCIAL-PROFILE] 🔨 Creating social profile for user: ${userId.value}")
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
        val isUsernameAvailable = profileRepository.checkUsernameAvailability(username).getOrThrow()
        Timber.d("[SOCIAL-PROFILE]   - Username available: $isUsernameAvailable")
        if (!isUsernameAvailable) {
            Timber.w("[SOCIAL-PROFILE] ⚠️ Username '$username' is taken, attempting cleanup...")

            // Try to clean up orphaned username from previous failed signups
            val cleanupResult = profileRepository.cleanupOrphanedUsername(username, userId.value).getOrThrow()
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
            userId = userId.value,
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

        val result = profileRepository.createProfile(profile).getOrThrow()

        Timber.i("[SOCIAL-PROFILE] ✅ Social profile created successfully for user: ${userId.value}")
        Timber.i("[SOCIAL-PROFILE]   - User should now appear in search results")
        Timber.i("[SOCIAL-PROFILE]   - Profile will sync to Firebase social_profiles collection")

        result
    }

    /**
     * Updates an existing social profile with validation.
     * Replaces: UpdateSocialProfileUseCase.invoke()
     *
     * @param displayName Optional new display name
     * @param bio Optional new bio
     * @param profilePhotoUrl Optional new profile photo URL
     * @param coverPhotoUrl Optional new cover photo URL
     * @param instagramHandle Optional Instagram handle
     * @param youtubeChannel Optional YouTube channel
     * @param personalWebsite Optional personal website URL
     * @return The updated social profile
     */
    suspend fun update(
        displayName: String? = null,
        bio: String? = null,
        profilePhotoUrl: String? = null,
        coverPhotoUrl: String? = null,
        instagramHandle: String? = null,
        youtubeChannel: String? = null,
        personalWebsite: String? = null
    ): LiftrixResult<SocialProfile> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UPDATE_SOCIAL_PROFILE",
                errorMessage = "Failed to update social profile",
                analyticsContext = mapOf(
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        val userId = authQueryUseCase(waitForAuth = false).getOrNull()
            ?: throw IllegalStateException("User not authenticated")

        // Validate inputs if provided
        displayName?.let { validator.validateDisplayName(it).getOrThrow() }
        bio?.let { validator.validateBio(it).getOrThrow() }

        val updates = SocialProfileRepository.ProfileUpdate(
            displayName = displayName,
            bio = bio,
            profilePhotoUrl = profilePhotoUrl,
            coverPhotoUrl = coverPhotoUrl,
            instagramHandle = instagramHandle,
            youtubeChannel = youtubeChannel,
            personalWebsite = personalWebsite
        )

        profileRepository.updateProfile(userId.value, updates).getOrThrow()
    }

    /**
     * Updates social privacy settings with validation and sync.
     * Replaces: UpdateSocialPrivacySettingsUseCase.invoke()
     *
     * @param privacySettings The new privacy settings to apply
     * @return The updated privacy settings
     */
    suspend fun updatePrivacySettings(
        privacySettings: SocialPrivacySettings
    ): LiftrixResult<SocialPrivacySettings> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UPDATE_PRIVACY_SETTINGS",
                errorMessage = "Failed to update privacy settings",
                analyticsContext = mapOf(
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        val userId = authQueryUseCase(waitForAuth = false).getOrNull()
            ?: throw IllegalStateException("User not authenticated")

        // Ensure the privacy settings belong to the current user
        val settingsForUser = privacySettings.copy(userId = userId.value)

        privacyRepository.updatePrivacySettings(settingsForUser).getOrThrow()
    }
}
