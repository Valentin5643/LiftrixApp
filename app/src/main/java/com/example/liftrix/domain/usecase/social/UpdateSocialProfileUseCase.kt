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
 * Use case for updating an existing social profile with validation.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
class UpdateSocialProfileUseCase @Inject constructor(
    private val repository: SocialProfileRepository,
    private val validator: ProfileValidator,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    suspend operator fun invoke(
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
        val userId = getCurrentUserIdUseCase() 
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

        repository.updateProfile(userId, updates).getOrThrow()
    }
}