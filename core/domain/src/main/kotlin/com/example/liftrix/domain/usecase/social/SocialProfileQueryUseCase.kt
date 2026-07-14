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
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
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
    private val authQueryUseCase: AuthQueryUseCase,
    private val errorHandler: ErrorHandler,
    private val userAccountRepository: com.example.liftrix.domain.repository.UserAccountRepository,
    private val socialProfileCommandUseCase: SocialProfileCommandUseCase
) {
    // In-memory guard to prevent infinite retry loops for profile creation
    private val creationAttempts = mutableMapOf<String, Long>()
    private val CREATION_COOLDOWN_MS = 5000L // 5 seconds cooldown between attempts
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
        val viewerId = authQueryUseCase(waitForAuth = false).getOrNull()?.value // Extract String from UserId
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
                return LiftrixResult.failure(
                    validationResult.exceptionOrNull()
                        ?: LiftrixError.UnknownError("Unexpected public profile validation failure")
                )
            }

            // Retrieve public profile with privacy filtering
            val profileResult = userSearchRepository.getPublicProfile(
                userId = request.profileUserId,
                viewerId = currentUserId
            )

            if (profileResult.isFailure) {
                return LiftrixResult.failure(
                    profileResult.exceptionOrNull()
                        ?: LiftrixError.UnknownError("Unexpected public profile lookup failure")
                )
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
                    // ENHANCED FIX: Auto-create missing profile instead of just returning error
                    return attemptAutoProfileCreation(request.profileUserId, currentUserId, request)
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
        val userId = authQueryUseCase(waitForAuth = false).getOrNull()
            ?: throw IllegalStateException("User not authenticated")

        // Validate limit
        if (limit <= 0 || limit > 100) {
            throw IllegalArgumentException("Limit must be between 1 and 100")
        }

        val profiles = socialProfileRepository.getDiscoverableProfiles(userId.value, limit).getOrThrow()
        profiles
    }

    // Private helper methods

    /**
     * Gets current authenticated user ID
     */
    private suspend fun getCurrentUserId(): String? {
        return try {
            authRepository.getCurrentUserId()?.value
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            // Don't fail the main operation if view tracking fails
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

    /**
     * Attempts to auto-create a missing social profile for the user.
     * This is a recovery mechanism for users who completed signup but social profile creation failed.
     *
     * Self-healing design: Allows ANY caller (owner, followers, searchers) to trigger profile recovery.
     * Safe because it only creates from existing UserAccount data (public info, no private data exposure).
     * Enables consistent state across follow, search, and profile view flows.
     */
    private suspend fun attemptAutoProfileCreation(
        profileUserId: String,
        currentUserId: String,
        request: GetPublicProfileRequest
    ): LiftrixResult<GetPublicProfileResult> {
        return try {
            // GUARD: Prevent infinite loop - check if we recently attempted creation
            val lastAttempt = creationAttempts[profileUserId]
            val now = System.currentTimeMillis()
            if (lastAttempt != null && (now - lastAttempt) < CREATION_COOLDOWN_MS) {
                return liftrixFailure(
                    LiftrixError.NotFoundError(
                        errorMessage = "User profile not found (creation in progress - please refresh)",
                        isRecoverable = true,
                        analyticsContext = mapOf(
                            "context" to "SocialProfileQueryUseCase",
                            "profileUserId" to profileUserId,
                            "reason" to "Creation cooldown active - preventing infinite loop"
                        ),
                        resourceType = "USER_PROFILE",
                        resourceId = profileUserId
                    )
                )
            }

            // Mark creation attempt
            creationAttempts[profileUserId] = now

            // FIXED: Allow auto-creation from any caller to enable self-healing for follow/search flows

            // SAFEGUARD: Get UserAccount data if available, otherwise use fallback values
            // This ensures MAXIMUM self-healing - even for users with incomplete account data
            val userAccountResult = userAccountRepository.getAccountInfoSuspend(profileUserId)
            val userAccount = userAccountResult.getOrNull()

            if (userAccount == null) {
            } else {
            }

            // Use actual data if available, otherwise intelligent fallbacks
            val username = userAccount?.username
                ?: userAccount?.displayName?.replace(" ", "_")?.lowercase()
                ?: "user_${profileUserId.take(8)}"

            val displayName = userAccount?.displayName
                ?: userAccount?.username
                ?: "User ${profileUserId.take(8)}"

            // Create the missing profile (idempotent - will return existing if already created)
            val createResult = socialProfileCommandUseCase.create(
                username = username,
                displayName = displayName,
                bio = null
            )

            createResult.fold(
                onSuccess = { socialProfile ->

                    // Clear creation attempt tracker on success
                    creationAttempts.remove(profileUserId)

                    // CRITICAL FIX: Construct result directly from created profile instead of re-querying
                    // This prevents infinite loop when Room/Firestore haven't synced yet
                    val publicProfile = com.example.liftrix.domain.model.social.PublicUserProfile(
                        userId = socialProfile.userId,
                        username = socialProfile.username,
                        displayName = socialProfile.displayName,
                        profileImageUrl = null, // Will be populated on next sync
                        coverImageUrl = null,
                        bio = socialProfile.bio,
                        age = null,
                        location = null,
                        fitnessLevel = null,
                        fitnessGoals = emptyList(),
                        followersCount = 0,
                        followingCount = 0,
                        mutualConnectionsCount = 0,
                        totalWorkouts = 0,
                        currentStreak = 0,
                        longestStreak = 0,
                        memberSince = java.time.LocalDateTime.now(), // Convert from millis if needed
                        lastActive = null,
                        isVerified = false,
                        isPrivate = socialProfile.isPrivate,
                        followStatus = if (profileUserId == currentUserId)
                            com.example.liftrix.domain.model.social.FollowStatus.NONE
                            else com.example.liftrix.domain.model.social.FollowStatus.NONE,
                        connectionStatus = if (profileUserId == currentUserId)
                            com.example.liftrix.domain.model.social.ConnectionStatus.SELF
                            else com.example.liftrix.domain.model.social.ConnectionStatus.NONE,
                        canViewDetails = true, // Just created, viewer can see
                        instagramHandle = null,
                        youtubeChannel = null,
                        personalWebsite = null,
                        recentWorkouts = emptyList(),
                        recentWorkoutPosts = emptyList(),
                        achievements = emptyList(),
                        publicWorkoutStats = null
                    )

                    val result = GetPublicProfileResult(
                        profile = publicProfile,
                        isOwnProfile = profileUserId == currentUserId,
                        canInteract = true
                    )

                    return LiftrixResult.success(result)
                },
                onFailure = { error ->
                    return liftrixFailure(
                        LiftrixError.NotFoundError(
                            errorMessage = "User profile not found and could not be created automatically. Please try again or contact support.",
                            isRecoverable = true,
                            analyticsContext = mapOf(
                                "context" to "SocialProfileQueryUseCase",
                                "profileUserId" to profileUserId,
                                "reason" to "Auto-creation failed: ${error.message}",
                                "suggestion" to "RETRY_OR_CONTACT_SUPPORT"
                            ),
                            resourceType = "USER_PROFILE",
                            resourceId = profileUserId
                        )
                    )
                }
            )
        } catch (e: Exception) {
            liftrixFailure(
                LiftrixError.NotFoundError(
                    errorMessage = "User profile not found",
                    isRecoverable = true,
                    analyticsContext = mapOf(
                        "context" to "SocialProfileQueryUseCase",
                        "profileUserId" to profileUserId,
                        "error" to (e.message ?: "Unknown exception")
                    ),
                    resourceType = "USER_PROFILE",
                    resourceId = profileUserId
                )
            )
        }
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
