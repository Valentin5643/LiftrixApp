package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import javax.inject.Inject

/**
 * Use case for retrieving a social profile with proper privacy filtering.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
class GetSocialProfileUseCase @Inject constructor(
    private val repository: SocialProfileRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    /**
     * Gets a social profile for the specified user ID.
     * Applies privacy filtering based on the current viewer.
     * 
     * @param userId The ID of the user whose profile to retrieve
     * @return The social profile if visible to current user, null if not found or private
     */
    suspend operator fun invoke(userId: String): LiftrixResult<SocialProfile?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_SOCIAL_PROFILE",
                errorMessage = "Failed to get social profile",
                analyticsContext = mapOf(
                    "target_user_id" to userId,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        val viewerId = getCurrentUserIdUseCase() // Can be null for anonymous viewing
        repository.getProfile(userId, viewerId).getOrThrow()
    }
}