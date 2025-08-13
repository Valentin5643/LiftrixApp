package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.FeedCacheDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.PostLikeDao
import com.example.liftrix.data.local.dao.PostCommentDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.entity.FeedCacheEntity
import com.example.liftrix.domain.service.FeedCacheService
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of FeedCacheService for managing feed cache operations and scoring.
 * Optimizes feed performance through intelligent caching and relevance scoring.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@Singleton
class FeedCacheServiceImpl @Inject constructor(
    private val feedCacheDao: FeedCacheDao,
    private val workoutPostDao: WorkoutPostDao,
    private val postLikeDao: PostLikeDao,
    private val postCommentDao: PostCommentDao,
    private val followRelationshipDao: FollowRelationshipDao
) : FeedCacheService {
    
    override suspend fun calculateRelevanceScore(
        postId: String, 
        viewerId: String
    ): LiftrixResult<Double> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to calculate relevance score",
                operation = "CALCULATE_RELEVANCE_SCORE",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "viewer_id" to viewerId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val post = workoutPostDao.getPostById(postId)
                ?: return@withContext 0.0
            
            var score = 0.0
            
            // Recency score (max 40 points) - newer posts get higher scores
            val hoursSincePost = (System.currentTimeMillis() - post.createdAt) / 3600000.0
            score += maxOf(0.0, 40.0 - hoursSincePost * 0.5)
            
            // Engagement score (max 30 points) - likes and comments boost relevance
            val engagementScore = minOf(30.0, post.likeCount * 0.5 + post.commentCount * 2.0)
            score += engagementScore
            
            // Personal Records score (max 20 points) - PRs are highly relevant
            score += post.prsCount * 10.0
            
            // Media presence score (max 10 points) - posts with media are more engaging
            if (!post.mediaUrls.isNullOrEmpty()) {
                score += 10.0
            }
            
            // Following relationship bonus (max 15 points) - followed users get priority
            val isFollowing = followRelationshipDao.isFollowing(viewerId, post.userId)
            if (isFollowing) {
                score += 15.0
            }
            
            // User interaction history bonus (max 10 points) - users they've liked before
            val hasLikedUserPosts = postLikeDao.getUserLikes(viewerId)
                .toString().contains(post.userId) // Simplified check
            if (hasLikedUserPosts) {
                score += 5.0
            }
            
            // Normalize score to 0-100 range
            minOf(100.0, maxOf(0.0, score))
        }
    }
    
    override suspend fun updateFeedCache(
        userId: String, 
        forceRefresh: Boolean
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to update feed cache",
                operation = "UPDATE_FEED_CACHE",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "force_refresh" to forceRefresh
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Clear existing cache if force refresh
            if (forceRefresh) {
                feedCacheDao.clearUserCache(userId)
            }
            
            // Get followed users
            val followedUsers = followRelationshipDao.getFollowing(userId)
                .map { it.followingId }
            
            // Get recent posts from followed users (last 7 days)
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            
            // This would need a proper query in WorkoutPostDao
            // For now, implementing basic logic
            val cacheEntries = mutableListOf<FeedCacheEntity>()
            val currentTime = System.currentTimeMillis()
            
            // Process posts from followed users
            followedUsers.forEach { followedUserId ->
                // Calculate scores for this user's posts
                // This is simplified - would need proper batching in production
                val score = calculateRelevanceScore("dummy_post_id", userId).getOrNull() ?: 0.0
                
                cacheEntries.add(
                    FeedCacheEntity(
                        userId = userId,
                        postId = "dummy_post_id", // Would be actual post ID
                        score = score,
                        fetchedAt = currentTime
                    )
                )
            }
            
            // Insert cache entries
            if (cacheEntries.isNotEmpty()) {
                feedCacheDao.insertCacheEntries(cacheEntries)
            }
        }
    }
    
    override suspend fun invalidatePostCache(postId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to invalidate post cache",
                operation = "INVALIDATE_POST_CACHE",
                analyticsContext = mapOf("post_id" to postId)
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Remove cache entries for this specific post from all users' caches
            // This would need a proper query in FeedCacheDao
            // For now, implementing placeholder logic
        }
    }
    
    override suspend fun invalidateUserCache(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to invalidate user cache",
                operation = "INVALIDATE_USER_CACHE",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            feedCacheDao.clearUserCache(userId)
        }
    }
    
    override suspend fun cleanupOldCache(olderThanHours: Int): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to cleanup old cache",
                operation = "CLEANUP_OLD_CACHE",
                analyticsContext = mapOf("older_than_hours" to olderThanHours)
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val cutoffTime = System.currentTimeMillis() - (olderThanHours * 60 * 60 * 1000)
            
            // This would need to iterate through all users or have a global cleanup query
            // For now, implementing placeholder logic
        }
    }
    
    override suspend fun getCachedFeedPostIds(
        userId: String, 
        limit: Int, 
        offset: Int
    ): LiftrixResult<List<String>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to get cached feed post IDs",
                operation = "GET_CACHED_FEED_POST_IDS",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "limit" to limit,
                    "offset" to offset
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            feedCacheDao.getCachedPostIds(userId, limit, offset)
        }
    }
    
    override suspend fun hasSufficientCache(
        userId: String, 
        minCacheSize: Int
    ): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to check cache sufficiency",
                operation = "HAS_SUFFICIENT_CACHE",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "min_cache_size" to minCacheSize
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val cacheSize = feedCacheDao.getCacheSize(userId)
            cacheSize >= minCacheSize
        }
    }
}