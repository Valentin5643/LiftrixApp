package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for managing feed cache operations and scoring.
 * Optimizes feed performance through intelligent caching and relevance scoring.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
interface FeedCacheService {
    
    /**
     * Calculates relevance score for a post based on multiple factors
     * @param postId The post ID to score
     * @param viewerId The user viewing the feed
     * @return Calculated relevance score (0.0 - 100.0)
     */
    suspend fun calculateRelevanceScore(postId: String, viewerId: String): LiftrixResult<Double>
    
    /**
     * Updates feed cache for a user with fresh scored posts
     * @param userId User whose cache to update
     * @param forceRefresh Whether to bypass existing cache
     */
    suspend fun updateFeedCache(userId: String, forceRefresh: Boolean = false): LiftrixResult<Unit>
    
    /**
     * Invalidates cache entries for a specific post (when post is updated/deleted)
     * @param postId Post that was modified
     */
    suspend fun invalidatePostCache(postId: String): LiftrixResult<Unit>
    
    /**
     * Invalidates all cache entries for a user (when their follow list changes)
     * @param userId User whose cache to invalidate
     */
    suspend fun invalidateUserCache(userId: String): LiftrixResult<Unit>
    
    /**
     * Invalidates only profile-related cache for a user (when profile is updated)
     * This preserves feed content cache to avoid UI disruption
     * @param userId User whose profile cache to invalidate
     */
    suspend fun invalidateProfileCache(userId: String): LiftrixResult<Unit>
    
    /**
     * Invalidates only feed content cache for a user (when follow list changes)
     * @param userId User whose feed cache to invalidate
     */
    suspend fun invalidateFeedCache(userId: String): LiftrixResult<Unit>
    
    /**
     * Removes old cache entries beyond retention period
     * @param olderThanHours Remove cache entries older than this many hours
     */
    suspend fun cleanupOldCache(olderThanHours: Int = 24): LiftrixResult<Unit>
    
    /**
     * Gets cached post IDs for a user's feed with pagination
     * @param userId User requesting the feed
     * @param limit Number of posts to return
     * @param offset Pagination offset
     * @return List of cached post IDs ordered by relevance score
     */
    suspend fun getCachedFeedPostIds(
        userId: String, 
        limit: Int, 
        offset: Int
    ): LiftrixResult<List<String>>
    
    /**
     * Checks if user has sufficient cached content
     * @param userId User to check
     * @param minCacheSize Minimum required cache size
     * @return True if cache is sufficient
     */
    suspend fun hasSufficientCache(userId: String, minCacheSize: Int = 20): LiftrixResult<Boolean>
}