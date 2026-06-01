package com.example.liftrix.data.service

import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.UserRepository
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed user profile lookup helper.
 * Part of Phase 1: User Profile Data Integration from SPEC-20250901-todo-implementation.
 */
@Singleton
class UserProfileCacheService @Inject constructor(
    private val userRepository: UserRepository,
    private val socialProfileRepository: SocialProfileRepository
) {

    /**
     * Gets user profile data from repositories. Room remains the local source of truth.
     * @param userId The user ID to fetch profile for (mandatory for user scoping)
     * @return LiftrixResult with UserProfileData if found
     */
    suspend fun getUserProfile(userId: String): LiftrixResult<UserProfileData> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "PROFILE_FETCH_FAILED",
                    errorMessage = "Failed to fetch user profile",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }

            // Fetch from repositories (Room is source of truth)
            val userProfile = userRepository.getUserProfile(userId).fold(
                onSuccess = { it },
                onFailure = { 
                    Timber.w("UserProfileCacheService: Failed to fetch user profile for $userId")
                    null 
                }
            )
            
            val socialProfile = socialProfileRepository.getProfile(userId, viewerId = userId).fold(
                onSuccess = { it },
                onFailure = {
                    Timber.w("UserProfileCacheService: Failed to fetch social profile for $userId")
                    null
                }
            )
            
            // Create composite profile data
            val profileData = UserProfileData(
                userId = userId,
                displayName = userProfile?.displayName ?: socialProfile?.getDisplayNameOrUsername() ?: "Unknown",
                profileImageUrl = userProfile?.profileImageUrl ?: socialProfile?.profilePhotoUrl,
                bio = userProfile?.bio ?: socialProfile?.bio,
                memberSince = userProfile?.memberSince,
                totalWorkouts = userProfile?.totalWorkouts ?: socialProfile?.workoutCount ?: 0,
                loadedAt = System.currentTimeMillis()
            )

            Timber.d("UserProfileCacheService: Loaded profile data for user $userId")
            profileData
        }
    
    /**
     * Gets display name for a user with fallback strategy.
     * @param userId The user ID (mandatory for user scoping)
     * @return Display name or "Unknown" if not found
     */
    suspend fun getDisplayName(userId: String): String {
        return getUserProfile(userId).fold(
            onSuccess = { it.displayName },
            onFailure = { "Unknown" }
        )
    }
    
    /**
     * Gets profile image URL for a user with fallback strategy.
     * @param userId The user ID (mandatory for user scoping)
     * @return Profile image URL or null if not found
     */
    suspend fun getProfileImageUrl(userId: String): String? {
        return getUserProfile(userId).fold(
            onSuccess = { it.profileImageUrl },
            onFailure = { null }
        )
    }
    
    fun invalidateCache(userId: String) = Timber.d("UserProfileCacheService: No memory cache to invalidate for user $userId")

    fun clearCache() = Timber.d("UserProfileCacheService: No memory cache to clear")
}

/**
 * Composite data class combining user profile and social profile data.
 * Used for efficient caching and reduced database queries.
 */
data class UserProfileData(
    val userId: String,
    val displayName: String,
    val profileImageUrl: String? = null,
    val bio: String? = null,
    val memberSince: java.time.LocalDateTime? = null,
    val totalWorkouts: Int = 0,
    val loadedAt: Long = System.currentTimeMillis()
)
