package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.social.SocialPrivacySettingsRepository
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.validation.ProfileValidator
import javax.inject.Inject

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
                errorMessage = "Failed to create social profile: ${throwable.message ?: "Unknown error"}"
            )
        }
    ) {
        val userId = getCurrentUserIdUseCase() ?: throw IllegalStateException("User not authenticated")
        validator.validateUsername(username).getOrThrow()
        validator.validateDisplayName(displayName).getOrThrow()
        bio?.let { validator.validateBio(it).getOrThrow() }
        if (!repository.checkUsernameAvailability(username).getOrThrow()) {
            throw IllegalArgumentException("Username '$username' is already taken")
        }

        val now = System.currentTimeMillis()
        val profile = SocialProfile(
            userId = userId,
            username = username.lowercase().trim(),
            displayName = displayName.trim(),
            bio = bio?.trim(),
            memberSince = now,
            isPrivate = true,
            hideFromSuggestions = false,
            allowFriendRequests = true,
            createdAt = now,
            updatedAt = now
        )
        repository.createProfile(profile).getOrThrow()
    }
}

class UpdateSocialPrivacySettingsUseCase @Inject constructor(
    private val repository: SocialPrivacySettingsRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    suspend operator fun invoke(settings: SocialPrivacySettings): LiftrixResult<SocialPrivacySettings> =
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "UPDATE_PRIVACY_SETTINGS",
                    errorMessage = "Failed to update privacy settings",
                    analyticsContext = mapOf("error" to (throwable.message ?: "Unknown error"))
                )
            }
        ) {
            val userId = getCurrentUserIdUseCase() ?: throw IllegalStateException("User not authenticated")
            repository.updatePrivacySettings(settings.copy(userId = userId)).getOrThrow()
        }
}

class CheckUsernameAvailabilityUseCase @Inject constructor(
    private val repository: SocialProfileRepository
) {
    suspend operator fun invoke(username: String): LiftrixResult<Boolean> =
        repository.checkUsernameAvailability(username)
}

class FollowUserUseCase @Inject constructor(
    private val socialRelationshipUseCase: SocialRelationshipUseCase
) {
    suspend operator fun invoke(targetUserId: String): LiftrixResult<FollowStatus> =
        socialRelationshipUseCase.followAction(targetUserId, FollowAction.FOLLOW)

    suspend operator fun invoke(
        action: FollowAction,
        targetUserId: String,
        context: String = "PROFILE_VIEW"
    ): LiftrixResult<FollowStatus> =
        socialRelationshipUseCase.followAction(targetUserId, action, context)

    suspend operator fun invoke(
        currentUserId: String,
        action: FollowAction,
        targetUserId: String
    ): LiftrixResult<FollowStatus> =
        socialRelationshipUseCase.followAction(targetUserId, action, currentUserId)
}

class GetSocialProfileUseCase @Inject constructor(
    private val socialProfileQueryUseCase: SocialProfileQueryUseCase
) {
    suspend operator fun invoke(userId: String): LiftrixResult<SocialProfile?> =
        socialProfileQueryUseCase(userId)
}
