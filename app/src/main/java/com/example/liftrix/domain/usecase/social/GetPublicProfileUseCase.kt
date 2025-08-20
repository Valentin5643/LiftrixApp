package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.flatMapLiftrix
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for retrieving public user profiles with privacy controls
 * 
 * Responsibilities:
 * - Validates profile access permissions
 * - Retrieves privacy-aware profile information
 * - Tracks profile views for analytics
 * - Handles privacy settings and blocked user restrictions
 * 
 * Business Rules:
 * - Only public profiles are accessible through this use case
 * - Blocked users cannot view each other's profiles
 * - Profile views are tracked for analytics and privacy transparency
 * - Private information is filtered based on user privacy settings
 * - Profile access respects connection status and privacy levels
 */
class GetPublicProfileUseCase @Inject constructor(
    private val userSearchRepository: UserSearchRepository,
    private val authRepository: AuthRepository,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Retrieves a public user profile with privacy filtering applied
     * 
     * @param request The profile request containing user ID and viewing context
     * @return LiftrixResult containing public profile or error information
     */
    suspend operator fun invoke(request: GetPublicProfileRequest): LiftrixResult<GetPublicProfileResult> {
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
                return liftrixFailure(
                    LiftrixError.NotFoundError("User profile not found or not public")
                )
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
                "context" to "GetPublicProfileUseCase",
                "profileUserId" to request.profileUserId
            ))
            LiftrixResult.failure(error)
        }
    }
    
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