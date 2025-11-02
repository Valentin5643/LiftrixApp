package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import timber.log.Timber
import javax.inject.Inject

/**
 * Consolidated use case for querying social profiles.
 *
 * Consolidates:
 * - GetSocialProfileUseCase (basic profile retrieval with privacy filtering)
 * - GetPublicProfileUseCase (public profile with extended metadata)
 * - GetDiscoverableSocialProfilesUseCase (suggestions/discovery)
 *
 * Part of Phase 3: Social & Workout Domains consolidation.
 * Ref: SPEC-20251031-usecase-consolidation.md
 */
class SocialProfileQueryUseCase @Inject constructor(
    private val socialProfileRepository: SocialProfileRepository,
    private val userSearchRepository: UserSearchRepository,
    private val authRepository: AuthRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val errorHandler: ErrorHandler
) {
    /**
     * Gets a social profile for the specified user ID (simple invocation).
     * Applies privacy filtering based on the current viewer.
     * Replaces: GetSocialProfileUseCase.invoke()
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
        socialProfileRepository.getProfile(userId, viewerId).getOrThrow()
    }

    /**
     * Gets a public user profile with extended metadata and interaction capabilities.
     * Replaces: GetPublicProfileUseCase.invoke()
     *
     * @param request The profile request containing user ID and viewing context
     * @return LiftrixResult containing public profile with metadata
     */
    suspend fun getPublicProfile(request: GetPublicProfileRequest): LiftrixResult<GetPublicProfileResult> {
        return try {
            // Get current user ID for privacy filtering
            val currentUserId = getCurrentUserId()
                ?: return liftrixFailure(LiftrixError.AuthenticationError("User not authenticated"))

            // Validate request
            val validationResult = validateRequest(request, currentUserId)
            if (validationResult.isFailure) {
                return validationResult as LiftrixResult<GetPublicProfileResult>
            }

            // Retrieve public profile with privacy filtering
            val profileResult = userSearchRepository.getPublicProfile(
                userId = request.profileUserId,
                viewerId = currentUserId
            )

            if (profileResult.isFailure) {
                return profileResult as LiftrixResult<GetPublicProfileResult>
            }

            val profile = profileResult.getOrThrow()
            if (profile == null) {
                // Check if profile exists but is private by querying without privacy filtering
                val profileExistsResult = userSearchRepository.profileExists(request.profileUserId)
                val profileExists = profileExistsResult.getOrNull() == true

                if (profileExists) {
                    return liftrixFailure(
                        LiftrixError.BusinessLogicError(
                            code = "PROFILE_PRIVATE",
                            errorMessage = "This profile is private",
                            analyticsContext = mapOf(
                                "context" to "SocialProfileQueryUseCase",
                                "profileUserId" to request.profileUserId,
                                "viewerId" to currentUserId
                            )
                        )
                    )
                } else {
                    return liftrixFailure(
                        LiftrixError.NotFoundError(
                            errorMessage = "User profile not found",
                            analyticsContext = mapOf(
                                "context" to "SocialProfileQueryUseCase",
                                "profileUserId" to request.profileUserId
                            )
                        )
                    )
                }
            }

            // Track profile view if enabled
            if (request.trackView && request.profileUserId != currentUserId) {
                trackProfileViewSafely(request.profileUserId, currentUserId)
            }

            val result = GetPublicProfileResult(
                profile = profile,
                isOwnProfile = request.profileUserId == currentUserId,
                canInteract = canUserInteract(profile, currentUserId)
            )

            LiftrixResult.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during public profile retrieval")
            val error = LiftrixError.UnknownError("Profile retrieval failed: ${e.message}")
            errorHandler.handleError(error, mapOf(
                "context" to "SocialProfileQueryUseCase",
                "profileUserId" to request.profileUserId
            ))
            LiftrixResult.failure(error)
        }
    }

    /**
     * Gets discoverable profiles for user suggestions.
     * Filters out private profiles, blocked users, and profiles hidden from suggestions.
     * Replaces: GetDiscoverableSocialProfilesUseCase.invoke()
     *
     * @param limit Maximum number of profiles to return (1-100)
     * @return List of discoverable profiles
     */
    suspend fun getDiscoverableProfiles(
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

        val profiles = socialProfileRepository.getDiscoverableProfiles(viewerId, limit).getOrThrow()
        profiles
    }

    // Private helper methods

    /**
     * Gets current authenticated user ID
     */
    private suspend fun getCurrentUserId(): String? {
        return try {
            authRepository.getCurrentUserId()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user ID")
            null
        }
    }

    /**
     * Validates the profile request parameters
     */
    private fun validateRequest(
        request: GetPublicProfileRequest,
        currentUserId: String
    ): LiftrixResult<GetPublicProfileRequest> {
        val violations = mutableListOf<String>()

        // Validate profile user ID
        if (request.profileUserId.isBlank()) {
            violations.add("Profile user ID cannot be empty")
        }

        // Validate profile user ID format (basic check)
        if (request.profileUserId.length < MIN_USER_ID_LENGTH) {
            violations.add("Profile user ID format is invalid")
        }

        return if (violations.isEmpty()) {
            LiftrixResult.success(request)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "GetPublicProfileRequest",
                    violations = violations,
                    errorMessage = "Get public profile request validation failed"
                )
            )
        }
    }

    /**
     * Tracks profile view safely without failing the main operation
     */
    private suspend fun trackProfileViewSafely(profileUserId: String, viewerId: String) {
        try {
            userSearchRepository.trackProfileView(
                profileUserId = profileUserId,
                viewerId = viewerId
            )
            Timber.d("Profile view tracked: $profileUserId viewed by $viewerId")
        } catch (e: Exception) {
            // Don't fail the main operation if view tracking fails
            Timber.w(e, "Failed to track profile view, but continuing with profile retrieval")
        }
    }

    /**
     * Determines if the current user can interact with the profile
     */
    private fun canUserInteract(profile: PublicUserProfile, currentUserId: String): Boolean {
        // Users can always interact with their own profile
        if (profile.userId == currentUserId) {
            return true
        }

        // Check if users are already connected
        return when (profile.connectionStatus) {
            com.example.liftrix.domain.model.social.ConnectionStatus.CONNECTED -> true
            com.example.liftrix.domain.model.social.ConnectionStatus.PENDING_RECEIVED -> true
            com.example.liftrix.domain.model.social.ConnectionStatus.PENDING_SENT -> false
            com.example.liftrix.domain.model.social.ConnectionStatus.NONE -> true
            com.example.liftrix.domain.model.social.ConnectionStatus.MUTUAL_FOLLOW -> true
            com.example.liftrix.domain.model.social.ConnectionStatus.GYM_BUDDY -> true
            com.example.liftrix.domain.model.social.ConnectionStatus.BLOCKED -> false
            com.example.liftrix.domain.model.social.ConnectionStatus.SELF -> true // User can always see their own profile
        }
    }

    /**
     * Checks if a username is available for registration.
     *
     * @param username The username to check
     * @return LiftrixResult<Boolean> indicating if username is available
     */
    suspend fun checkUsernameAvailability(username: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHECK_USERNAME_AVAILABILITY_FAILED",
                errorMessage = "Failed to check username availability: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CHECK_USERNAME_AVAILABILITY",
                    "username" to username
                )
            )
        }
    ) {
        require(username.isNotBlank()) { "Username cannot be blank" }
        socialProfileRepository.checkUsernameAvailability(username).getOrThrow()
    }

    companion object {
        private const val MIN_USER_ID_LENGTH = 5
    }
}

/**
 * Request data class for retrieving public profiles
 *
 * @property profileUserId ID of the user whose profile to retrieve
 * @property trackView Whether to track this profile view for analytics (default: true)
 */
data class GetPublicProfileRequest(
    val profileUserId: String,
    val trackView: Boolean = true
)

/**
 * Result data class for public profile retrieval
 *
 * @property profile The retrieved public profile
 * @property isOwnProfile Whether this is the current user's own profile
 * @property canInteract Whether the current user can interact with this profile
 */
data class GetPublicProfileResult(
    val profile: PublicUserProfile,
    val isOwnProfile: Boolean,
    val canInteract: Boolean
)
