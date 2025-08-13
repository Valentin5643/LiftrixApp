package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.validation.ProfileValidator
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

        // Validate inputs
        validator.validateUsername(username).getOrThrow()
        validator.validateDisplayName(displayName).getOrThrow()
        bio?.let { validator.validateBio(it).getOrThrow() }

        // Check username availability
        val isUsernameAvailable = repository.checkUsernameAvailability(username).getOrThrow()
        if (!isUsernameAvailable) {
            throw IllegalArgumentException("Username '$username' is already taken")
        }

        // Create profile with privacy-first defaults
        val now = System.currentTimeMillis()
        val profile = SocialProfile(
            userId = userId,
            username = username.lowercase().trim(),
            displayName = displayName.trim(),
            bio = bio?.trim(),
            memberSince = now,
            isPrivate = true, // Privacy by default
            hideFromSuggestions = false, // Allow discovery by default once profile is complete
            allowFriendRequests = true, // Allow friend requests by default
            createdAt = now,
            updatedAt = now
        )

        repository.createProfile(profile).getOrThrow()
    }
}

