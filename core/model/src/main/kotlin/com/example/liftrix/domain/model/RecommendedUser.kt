package com.example.liftrix.domain.model

import java.io.Serializable
import java.util.concurrent.TimeUnit

/**
 * Domain model representing a user recommendation for the discovery carousel
 * Includes profile information and caching metadata for TTL implementation
 */
data class RecommendedUser(
    val userId: String,
    val username: String,
    val profileImageUrl: String?,
    val isFollowing: Boolean,
    val cachedAt: Long = System.currentTimeMillis()
) : Serializable {
    
    companion object {
        private const val CACHE_TTL_HOURS = 1L
        private val CACHE_TTL_MILLIS = TimeUnit.HOURS.toMillis(CACHE_TTL_HOURS)
        
        fun fromUser(user: User, isFollowing: Boolean): RecommendedUser {
            return RecommendedUser(
                userId = user.uid,
                username = user.displayName ?: "Unknown User",
                profileImageUrl = user.photoUrl,
                isFollowing = isFollowing
            )
        }
        
        /**
         * Factory method for creating RecommendedUser with specific cache timestamp
         * Useful for testing or cache restoration scenarios
         * 
         * @param userId user identifier
         * @param username display name
         * @param profileImageUrl profile image URL (nullable)
         * @param isFollowing current follow status
         * @param cachedAt cache timestamp
         * @return RecommendedUser instance
         */
        fun withCacheTimestamp(
            userId: String,
            username: String,
            profileImageUrl: String?,
            isFollowing: Boolean,
            cachedAt: Long
        ): RecommendedUser {
            return RecommendedUser(
                userId = userId,
                username = username,
                profileImageUrl = profileImageUrl,
                isFollowing = isFollowing,
                cachedAt = cachedAt
            )
        }
    }
    
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(cachedAt > 0) { "Cache timestamp must be positive" }
    }
    
    /**
     * Determines if the cached recommendation is still valid based on TTL
     * 
     * @return true if cache is valid (less than 1 hour old), false otherwise
     */
    val isCacheValid: Boolean
        get() = System.currentTimeMillis() - cachedAt < CACHE_TTL_MILLIS
    
    /**
     * Returns the age of the cache in milliseconds
     */
    val cacheAge: Long
        get() = System.currentTimeMillis() - cachedAt
    
    /**
     * Returns the remaining time until cache expires in milliseconds
     * 
     * @return remaining TTL in milliseconds, or 0 if already expired
     */
    val remainingCacheTtl: Long
        get() = maxOf(0L, CACHE_TTL_MILLIS - cacheAge)
    
    /**
     * Creates a new RecommendedUser with updated follow status
     * 
     * @param following new follow status
     * @return new RecommendedUser instance with updated follow status
     */
    fun withFollowStatus(following: Boolean): RecommendedUser = copy(
        isFollowing = following,
        cachedAt = System.currentTimeMillis() // Refresh cache timestamp on state change
    )
    
    /**
     * Creates a new RecommendedUser with refreshed cache timestamp
     * 
     * @return new RecommendedUser instance with current timestamp
     */
    fun refreshCache(): RecommendedUser = copy(
        cachedAt = System.currentTimeMillis()
    )
    

}