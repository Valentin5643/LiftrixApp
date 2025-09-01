package com.example.liftrix.data.service

import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.UserRepository
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache service for efficient user profile data fetching.
 * Implements caching layer for user profiles to reduce database queries.
 * Part of Phase 1: User Profile Data Integration from SPEC-20250901-todo-implementation.
 */
@Singleton
class UserProfileCacheService @Inject constructor(
    private val userRepository: UserRepository,
    private val socialProfileRepository: SocialProfileRepository
) {
    
    // In-memory cache for user profile data
    private val profileCache = ConcurrentHashMap<String, UserProfileData>()
    private val socialProfileCache = ConcurrentHashMap<String, SocialProfile>()
    
    // Cache expiry time in milliseconds (15 minutes)
    private val cacheExpiryTime = 15 * 60 * 1000L
    
    /**
     * Gets user profile with caching. Uses Room as source of truth with offline-first strategy.
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
            
            // Check cache first
            val cachedProfile = profileCache[userId]
            if (cachedProfile != null && !isCacheExpired(cachedProfile.cacheTimestamp)) {
                Timber.d("UserProfileCacheService: Returning cached profile for user $userId")
                return@liftrixCatching cachedProfile
            }
            
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
                cacheTimestamp = System.currentTimeMillis()
            )
            
            // Update cache
            profileCache[userId] = profileData
            
            Timber.d("UserProfileCacheService: Cached profile data for user $userId")
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
    
    /**
     * Invalidates cache for a specific user.
     * @param userId The user ID to invalidate cache for
     */
    fun invalidateCache(userId: String) {
        profileCache.remove(userId)
        socialProfileCache.remove(userId)
        Timber.d("UserProfileCacheService: Invalidated cache for user $userId")
    }
    
    /**
     * Clears all cached profile data.
     * Used for memory management and testing.
     */
    fun clearCache() {
        profileCache.clear()
        socialProfileCache.clear()
        Timber.d("UserProfileCacheService: Cleared all cached profile data")
    }
    
    /**
     * Checks if cache entry has expired.
     */
    private fun isCacheExpired(cacheTimestamp: Long): Boolean {
        return System.currentTimeMillis() - cacheTimestamp > cacheExpiryTime
    }
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
    val cacheTimestamp: Long = System.currentTimeMillis()
)