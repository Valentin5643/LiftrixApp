package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import javax.inject.Inject

/**
 * Use case for searching social profiles with privacy filtering.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 */
class SearchSocialProfilesUseCase @Inject constructor(
    private val repository: SocialProfileRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    /**
     * Searches social profiles by username or display name.
     * Results are filtered based on privacy settings and blocked users.
     * 
     * @param query The search query
     * @param limit Maximum number of results to return
     * @return List of matching profiles visible to current user
     */
    suspend operator fun invoke(
        query: String,
        limit: Int = 20
    ): LiftrixResult<List<SocialProfile>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SEARCH_SOCIAL_PROFILES",
                errorMessage = "Failed to search social profiles",
                analyticsContext = mapOf(
                    "query" to query,
                    "limit" to limit.toString(),
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        val viewerId = getCurrentUserIdUseCase() 
            ?: throw IllegalStateException("User not authenticated")

        // Validate inputs
        if (query.isBlank()) {
            throw IllegalArgumentException("Search query cannot be blank")
        }
        if (query.length < 2) {
            throw IllegalArgumentException("Search query must be at least 2 characters")
        }
        if (limit <= 0 || limit > 100) {
            throw IllegalArgumentException("Limit must be between 1 and 100")
        }

        repository.searchProfiles(viewerId, query.trim(), limit).getOrThrow()
    }
}