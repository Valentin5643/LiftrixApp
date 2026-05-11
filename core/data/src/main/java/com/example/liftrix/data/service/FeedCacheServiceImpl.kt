package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.FeedCacheDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.PostLikeDao
import com.example.liftrix.data.local.dao.PostCommentDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.SavedPostDao
import com.example.liftrix.data.local.entity.FeedCacheEntity
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.service.FeedCacheService
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.exp
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
    private val savedPostDao: SavedPostDao,
    private val followRelationshipDao: FollowRelationshipDao,
    private val privacyEnforcementService: com.example.liftrix.domain.service.PrivacyEnforcementService
) : FeedCacheService {
    
    override suspend fun calculateRelevanceScore(
        postId: String, 
        viewerId: String
    ): LiftrixResult<Double> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CALCULATE_RELEVANCE_SCORE",
                errorMessage = "Failed to calculate relevance score",
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
            
            // Recency score (max 30 points) - gradual one-week decay keeps stable workout
            // preferences visible across sessions instead of zeroing out after a day.
            val hoursSincePost = (System.currentTimeMillis() - post.createdAt) / 3600000.0
            score += 30.0 * exp(-hoursSincePost / 168.0)

            // Persistent workout affinity (max 25 points). Every social feed item here is
            // workout content, so keep a durable baseline instead of treating interest as a
            // short spike from the latest interaction.
            score += 18.0
            if (isOfficialWorkoutSeedPost(post)) {
                score += 7.0
            }
            
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
            
            // Session-to-session memory: creators the viewer repeatedly likes or saves stay
            // weighted in Explore and cache generation without needing a new schema.
            val engagedCreatorIds = getEngagedCreatorIds(viewerId)
            if (post.userId in engagedCreatorIds) {
                score += 10.0
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
                code = "UPDATE_FEED_CACHE",
                errorMessage = "Failed to update feed cache",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "force_refresh" to forceRefresh.toString()
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

            val currentTime = System.currentTimeMillis()

            val homeCandidates = if (followedUsers.isNotEmpty()) {
                workoutPostDao.getRecentPostsFromUsers(followedUsers + userId, CACHE_CANDIDATE_LIMIT).first()
            } else {
                emptyList()
            }

            val discoveryCandidates = workoutPostDao.getRecentPublicPosts(CACHE_CANDIDATE_LIMIT).first()
                .filter { post -> post.userId != userId }

            val cacheEntries = buildList {
                addAll(homeCandidates.toCacheEntries(userId, "HOME", currentTime))
                addAll(discoveryCandidates.toCacheEntries(userId, "DISCOVERY", currentTime))
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
                code = "INVALIDATE_POST_CACHE",
                errorMessage = "Failed to invalidate post cache",
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
                code = "INVALIDATE_USER_CACHE",
                errorMessage = "Failed to invalidate user cache",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Clear all cache for user (legacy method - kept for compatibility)
            feedCacheDao.clearUserCache(userId)
        }
    }
    
    override suspend fun invalidateProfileCache(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "INVALIDATE_PROFILE_CACHE",
                errorMessage = "Failed to invalidate profile cache",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Only invalidate profile-related cached data
            // This preserves feed content to avoid UI disruption
            // Since FeedCacheDao doesn't have granular methods yet,
            // we'll skip invalidation for profile updates
            // This is safe because feed content doesn't depend on profile metadata
            
            // In the future, implement: feedCacheDao.clearProfileCache(userId)
            // For now, do nothing to preserve feed content
        }
    }
    
    override suspend fun invalidateFeedCache(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "INVALIDATE_FEED_CACHE",
                errorMessage = "Failed to invalidate feed cache",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Clear only feed content cache (for when follow list changes)
            feedCacheDao.clearUserCache(userId)
        }
    }
    
    override suspend fun cleanupOldCache(olderThanHours: Int): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CLEANUP_OLD_CACHE",
                errorMessage = "Failed to cleanup old cache",
                analyticsContext = mapOf("older_than_hours" to olderThanHours.toString())
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
                code = "GET_CACHED_FEED_POST_IDS",
                errorMessage = "Failed to get cached feed post IDs",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "limit" to limit.toString(),
                    "offset" to offset.toString()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // FIX PRIV-004: Privacy re-validation on cache retrieval (CVSS 7.9)
            // Get cached post IDs
            val cachedPostIds = feedCacheDao.getCachedPostIds(userId, limit, offset)

            // Validate privacy for each cached post before returning
            // This prevents leaking posts from users who:
            // - Changed their privacy settings
            // - Blocked the viewer
            // - Were unfollowed (for private accounts)
            val validatedPostIds = cachedPostIds.filter { postId ->
                val postEntity = workoutPostDao.getPostById(postId)
                if (postEntity == null) return@filter false

                // Convert entity to domain model for privacy check
                val domainPost = entityToDomainForPrivacyCheck(postEntity)
                privacyEnforcementService.canViewPost(userId, domainPost)
            }

            validatedPostIds
        }
    }
    
    override suspend fun hasSufficientCache(
        userId: String, 
        minCacheSize: Int
    ): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "HAS_SUFFICIENT_CACHE",
                errorMessage = "Failed to check cache sufficiency",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "min_cache_size" to minCacheSize.toString()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val cacheSize = feedCacheDao.getCacheSize(userId)
            cacheSize >= minCacheSize
        }
    }

    /**
     * Converts WorkoutPostEntity to WorkoutPost domain model for privacy checking.
     * This is a minimal conversion that only populates fields needed for privacy validation.
     */
    private fun entityToDomainForPrivacyCheck(entity: WorkoutPostEntity): WorkoutPost {
        return WorkoutPost(
            id = entity.id,
            userId = entity.userId,
            workoutId = entity.workoutId,
            caption = entity.caption ?: "",
            visibility = try {
                PostVisibility.valueOf(entity.visibility)
            } catch (e: IllegalArgumentException) {
                PostVisibility.FOLLOWERS
            },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            likeCount = entity.likeCount,
            commentCount = entity.commentCount
        )
    }

    private suspend fun List<WorkoutPostEntity>.toCacheEntries(
        userId: String,
        feedType: String,
        fetchedAt: Long
    ): List<FeedCacheEntity> {
        return mapNotNull { post ->
            val domainPost = entityToDomainForPrivacyCheck(post)
            if (!privacyEnforcementService.canViewPost(userId, domainPost)) return@mapNotNull null

            val score = calculateRelevanceScore(post.id, userId).getOrNull() ?: 0.0
            FeedCacheEntity(
                userId = userId,
                postId = post.id,
                score = score,
                fetchedAt = fetchedAt,
                feedType = feedType
            )
        }
    }

    private suspend fun getEngagedCreatorIds(viewerId: String): Set<String> {
        val likedPostIds = postLikeDao.getUserLikes(viewerId).first().map { it.postId }
        val savedPostIds = savedPostDao.getUserSavedPosts(viewerId).first().map { it.postId }
        return (likedPostIds + savedPostIds)
            .distinct()
            .mapNotNull { postId -> workoutPostDao.getPostById(postId)?.userId }
            .toSet()
    }

    private fun isOfficialWorkoutSeedPost(post: WorkoutPostEntity): Boolean =
        post.userId.startsWith("official_liftrix_")

    private companion object {
        private const val CACHE_CANDIDATE_LIMIT = 100
    }
}
