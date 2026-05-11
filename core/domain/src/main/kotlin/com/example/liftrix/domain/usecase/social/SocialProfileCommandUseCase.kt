package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.ProfileVisibility
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.social.SocialPrivacySettingsRepository
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.validation.ProfileValidator
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
     * @param profilePhotoUrl Optional profile photo URL
     * @return The created social profile
     */
    suspend fun create(
        username: String,
        displayName: String,
        bio: String?,
        profilePhotoUrl: String? = null
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

        val normalizedPhotoUrl = profilePhotoUrl?.trim()?.takeIf { it.isNotBlank() }

        // IDEMPOTENCY FIX: Check if profile already exists - make creation idempotent
        val existingProfile = profileRepository.getProfile(userId.value, userId.value).getOrNull()
        if (existingProfile != null) {
            if (normalizedPhotoUrl != null && existingProfile.profilePhotoUrl.isNullOrBlank()) {
                profileRepository.updateProfile(
                    userId = userId.value,
                    updates = SocialProfileRepository.ProfileUpdate(profilePhotoUrl = normalizedPhotoUrl)
                ).fold(
                    onSuccess = {
                    },
                    onFailure = { error ->
                    }
                )
            }
            return LiftrixResult.success(existingProfile)
        }

        // Validate inputs
        validator.validateUsername(username).getOrThrow()
        validator.validateDisplayName(displayName).getOrThrow()
        bio?.let {
            validator.validateBio(it).getOrThrow()
        }

        // Check username availability with cleanup
        val isUsernameAvailable = profileRepository.checkUsernameAvailability(username).getOrThrow()
        if (!isUsernameAvailable) {

            // Try to clean up orphaned username from previous failed signups
            val cleanupResult = profileRepository.cleanupOrphanedUsername(username, userId.value).getOrThrow()
            if (cleanupResult) {
                // Username is now available, continue
            } else {
                throw IllegalArgumentException("Username '$username' is already taken")
            }
        } else {
        }

        // Create profile with social-enabled defaults
        val now = System.currentTimeMillis()
        val profile = SocialProfile(
            userId = userId.value,
            username = username.lowercase().trim(),
            displayName = displayName.trim(),
            bio = bio?.trim(),
            profilePhotoUrl = normalizedPhotoUrl,
            memberSince = now,
            isPrivate = false, // Public by default for maximum discoverability
            hideFromSuggestions = false, // Show in user suggestions and discovery
            allowFriendRequests = true, // Enable follow requests by default
            createdAt = now,
            updatedAt = now
        )

        val result = profileRepository.createProfile(profile).getOrThrow()

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

        val updatedSettings = privacyRepository.updatePrivacySettings(settingsForUser).getOrThrow()
        profileRepository.updatePrivacySetting(
            userId = userId.value,
            isPrivate = settingsForUser.profileVisibility != ProfileVisibility.PUBLIC
        ).getOrThrow()
        updatedSettings
    }
}
