package com.example.liftrix.data.repository.social

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.PostLikeDao
import com.example.liftrix.data.local.dao.SavedPostDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.data.paging.FeedRemoteMediator
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.social.CreateWorkoutPostRequest
import com.example.liftrix.domain.model.social.FeedType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.FeedCacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of FeedRepository with Paging3 integration and intelligent caching.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@Singleton
class FeedRepositoryImpl @Inject constructor(
    private val workoutPostDao: WorkoutPostDao,
    private val postLikeDao: PostLikeDao,
    private val savedPostDao: SavedPostDao,
    private val socialProfileDao: SocialProfileDao,
    private val workoutDao: WorkoutDao,
    private val workoutPostMapper: WorkoutPostMapper,
    private val feedCacheService: FeedCacheService
) : FeedRepository {
    
    @OptIn(ExperimentalPagingApi::class)
    override fun getFeed(
        userId: String,
        feedType: FeedType,
        targetUserId: String?,
        pageSize: Int
    ): Flow<PagingData<WorkoutPost>> {
        
        val pager = when (feedType) {
            FeedType.HOME -> {
                Pager(
                    config = PagingConfig(
                        pageSize = pageSize,
                        enablePlaceholders = false,
                        prefetchDistance = pageSize / 2
                    ),
                    // TODO: Implement remote mediator when needed
                    // remoteMediator = FeedRemoteMediator(...),
                    pagingSourceFactory = { workoutPostDao.getHomeFeedPosts(emptyList()) }
                )
            }
            
            FeedType.DISCOVERY -> {
                Pager(
                    config = PagingConfig(
                        pageSize = pageSize,
                        enablePlaceholders = false,
                        prefetchDistance = pageSize / 2
                    ),
                    // TODO: Implement remote mediator when needed
                    // remoteMediator = FeedRemoteMediator(...),
                    pagingSourceFactory = { workoutPostDao.getDiscoveryFeedPosts(listOf(userId)) }
                )
            }
            
            FeedType.USER -> {
                requireNotNull(targetUserId) { "targetUserId is required for USER feed type" }
                Pager(
                    config = PagingConfig(
                        pageSize = pageSize,
                        enablePlaceholders = false
                    ),
                    pagingSourceFactory = { workoutPostDao.getHomeFeedPosts(listOf(targetUserId)) }
                )
            }
        }
        
        return pager.flow.map { pagingData ->
            pagingData.map { entity ->
                enhanceWithUserData(entity, userId)
            }
        }
    }
    
    override suspend fun createPost(
        userId: String,
        request: CreateWorkoutPostRequest
    ): LiftrixResult<WorkoutPost> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to create workout post",
                operation = "CREATE_WORKOUT_POST",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "workout_id" to request.workoutId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val postId = UUID.randomUUID().toString()
            
            // Get workout details for metadata
            val workout = workoutDao.getWorkoutById(request.workoutId)
            
            val entity = workoutPostMapper.createEntityFromRequest(
                id = postId,
                userId = userId,
                request = request,
                workoutDuration = calculateWorkoutDuration(workout),
                totalVolume = calculateTotalVolume(workout),
                exercisesCount = calculateExercisesCount(workout),
                prsCount = 0 // Will be calculated later
            )
            
            workoutPostDao.insertPost(entity)
            
            // Invalidate feed caches since new content is available
            feedCacheService.invalidateUserCache(userId)
            
            enhanceWithUserData(entity, userId)
        }
    }
    
    override suspend fun getPost(postId: String, viewerId: String): LiftrixResult<WorkoutPost> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to get workout post",
                operation = "GET_WORKOUT_POST",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "viewer_id" to viewerId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val entity = workoutPostDao.getPostById(postId)
                ?: throw IllegalArgumentException("Post not found")
            
            enhanceWithUserData(entity, viewerId)
        }
    }
    
    override suspend fun updatePost(
        postId: String,
        userId: String,
        caption: String?,
        visibility: String?
    ): LiftrixResult<WorkoutPost> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to update workout post",
                operation = "UPDATE_WORKOUT_POST",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val existingPost = workoutPostDao.getPostById(postId)
                ?: throw IllegalArgumentException("Post not found")
            
            if (existingPost.userId != userId) {
                throw SecurityException("User not authorized to update this post")
            }
            
            val updatedPost = existingPost.copy(
                caption = caption ?: existingPost.caption,
                visibility = visibility ?: existingPost.visibility,
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
            
            workoutPostDao.updatePost(updatedPost)
            
            enhanceWithUserData(updatedPost, userId)
        }
    }
    
    override suspend fun deletePost(postId: String, userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to delete workout post",
                operation = "DELETE_WORKOUT_POST",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val existingPost = workoutPostDao.getPostById(postId)
                ?: throw IllegalArgumentException("Post not found")
            
            if (existingPost.userId != userId) {
                throw SecurityException("User not authorized to delete this post")
            }
            
            workoutPostDao.deletePostById(postId, userId)
            
            // Invalidate caches
            feedCacheService.invalidatePostCache(postId)
        }
    }
    
    override suspend fun refreshFeed(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to refresh feed",
                operation = "REFRESH_FEED",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        feedCacheService.updateFeedCache(userId, forceRefresh = true).getOrThrow()
    }
    
    override fun getUserPosts(
        userId: String,
        viewerId: String,
        pageSize: Int
    ): Flow<PagingData<WorkoutPost>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { workoutPostDao.getHomeFeedPosts(listOf(userId)) }
        ).flow.map { pagingData ->
            pagingData.map { entity ->
                enhanceWithUserData(entity, viewerId)
            }
        }
    }
    
    override fun getUserMentions(userId: String, pageSize: Int): Flow<PagingData<WorkoutPost>> {
        // TODO: Implement mentions when comment system supports mentions
        return Pager(
            config = PagingConfig(pageSize = pageSize),
            pagingSourceFactory = { workoutPostDao.getHomeFeedPosts(emptyList()) }
        ).flow.map { pagingData ->
            pagingData.map { entity ->
                enhanceWithUserData(entity, userId)
            }
        }
    }
    
    override fun getTrendingPosts(
        viewerId: String,
        timeWindowHours: Int,
        pageSize: Int
    ): Flow<PagingData<WorkoutPost>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { workoutPostDao.getDiscoveryFeedPosts(listOf(viewerId)) }
        ).flow.map { pagingData ->
            pagingData.map { entity ->
                enhanceWithUserData(entity, viewerId)
            }
        }
    }
    
    override suspend fun invalidateFeedCache(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                errorMessage = "Failed to invalidate feed cache",
                operation = "INVALIDATE_FEED_CACHE",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        feedCacheService.invalidateUserCache(userId).getOrThrow()
    }
    
    /**
     * Enhances post entity with user interaction data and author information
     */
    private suspend fun enhanceWithUserData(entity: com.example.liftrix.data.local.entity.WorkoutPostEntity, viewerId: String): WorkoutPost {
        // Get user interaction data
        val isLiked = postLikeDao.isPostLikedByUser(entity.id, viewerId)
        val isSaved = savedPostDao.isPostSaved(viewerId, entity.id)
        
        // Get author profile information
        val authorProfile = socialProfileDao.getSocialProfileByUserId(entity.userId)
        
        return workoutPostMapper.toDomain(
            entity = entity,
            isLikedByViewer = isLiked,
            isSavedByViewer = isSaved,
            authorUsername = authorProfile?.username,
            authorDisplayName = authorProfile?.displayName,
            authorProfilePhotoUrl = authorProfile?.profilePhotoUrl
        )
    }
    
    // Helper functions for workout metadata calculation
    private fun calculateWorkoutDuration(workout: Any?): Int? {
        // TODO: Implement based on workout entity structure
        return null
    }
    
    private fun calculateTotalVolume(workout: Any?): Double? {
        // TODO: Implement based on workout entity structure
        return null
    }
    
    private fun calculateExercisesCount(workout: Any?): Int? {
        // TODO: Implement based on workout entity structure
        return null
    }
}