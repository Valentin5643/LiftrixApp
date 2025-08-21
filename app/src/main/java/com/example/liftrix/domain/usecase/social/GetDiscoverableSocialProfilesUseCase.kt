package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import javax.inject.Inject

/**
 * Use case for getting discoverable social profiles for user suggestions.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
class GetDiscoverableSocialProfilesUseCase @Inject constructor(
    private val repository: SocialProfileRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    /**
     * Gets discoverable profiles for user suggestions.
     * Filters out private profiles, blocked users, and profiles hidden from suggestions.
     * 
     * @param limit Maximum number of profiles to return
     * @return List of discoverable profiles
     */
    suspend operator fun invoke(
        limit: Int = 50
    ): LiftrixResult<List<SocialProfile>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_DISCOVERABLE_PROFILES_FAILED",
                errorMessage = "Failed to get discoverable social profiles",
                analyticsContext = mapOf(
                    "limit" to limit.toString(),
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        val viewerId = getCurrentUserIdUseCase() 
            ?: throw IllegalStateException("User not authenticated")
        
        // Validate limit
        if (limit <= 0 || limit > 100) {
            throw IllegalArgumentException("Limit must be between 1 and 100")
        }

        val profiles = repository.getDiscoverableProfiles(viewerId, limit).getOrThrow()
        profiles
    }
}