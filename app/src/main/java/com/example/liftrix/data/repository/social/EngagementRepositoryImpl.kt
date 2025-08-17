package com.example.liftrix.data.repository.social

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.liftrix.data.local.dao.*
import com.example.liftrix.data.mapper.EngagementMapper
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.model.social.*
import com.example.liftrix.domain.model.MediaItem
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.service.PrivacyEnforcementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of EngagementRepository with optimistic updates and real-time sync.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@Singleton
class EngagementRepositoryImpl @Inject constructor(
    private val postLikeDao: PostLikeDao,
    private val postCommentDao: PostCommentDao,
    private val savedPostDao: SavedPostDao,
    private val workoutPostDao: WorkoutPostDao,
    private val socialProfileDao: SocialProfileDao,
    private val engagementMapper: EngagementMapper,
    private val workoutPostMapper: WorkoutPostMapper,
    private val privacyEnforcementService: PrivacyEnforcementService,
    private val analyticsTracker: AnalyticsTracker
) : EngagementRepository {
    
    // ==========================================
    // Like Operations
    // ==========================================
    
    override suspend fun toggleLike(postId: String, userId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TOGGLE_POST_LIKE",
                errorMessage = "Failed to toggle post like",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "user_id" to userId,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Check if user can view this post before allowing engagement
            val post = workoutPostDao.getPostById(postId)
                ?: throw IllegalArgumentException("Post not found: $postId")
            
            val workoutPost = workoutPostMapper.toDomain(post)
            val canViewPost = privacyEnforcementService.canViewPost(userId, workoutPost)
            if (!canViewPost) {
                throw IllegalStateException("User cannot engage with post due to privacy restrictions")
            }
            
            val isCurrentlyLiked = postLikeDao.isPostLikedByUser(postId, userId)
            
            if (isCurrentlyLiked) {
                // Unlike: Remove like and decrement count
                postLikeDao.deleteLikeByPostAndUser(postId, userId)
                
                // Update post like count optimistically
                val currentPost = workoutPostDao.getPostById(postId)
                currentPost?.let { post ->
                    val newCount = maxOf(0, post.likeCount - 1)
                    workoutPostDao.updateLikeCount(postId, newCount, System.currentTimeMillis())
                    
                    // Track unlike analytics
                    analyticsTracker.trackEngagement(
                        action = "UNLIKE",
                        contentType = "POST",
                        contentId = postId,
                        contentOwnerUserId = post.userId,
                        userId = userId
                    )
                }
                
                false
            } else {
                // Like: Add like and increment count
                val likeEntity = engagementMapper.createLikeEntity(postId, userId)
                postLikeDao.insertLike(likeEntity)
                
                // Update post like count optimistically
                val currentPost = workoutPostDao.getPostById(postId)
                currentPost?.let { post ->
                    val newCount = post.likeCount + 1
                    workoutPostDao.updateLikeCount(postId, newCount, System.currentTimeMillis())
                    
                    // Track like analytics
                    analyticsTracker.trackEngagement(
                        action = "LIKE",
                        contentType = "POST",
                        contentId = postId,
                        contentOwnerUserId = post.userId,
                        userId = userId
                    )
                }
                
                true
            }
        }
    }
    
    override suspend fun isPostLiked(postId: String, userId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "IS_POST_LIKED",
                errorMessage = "Failed to check post like status",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Check if user can view this post before checking like status
            val post = workoutPostDao.getPostById(postId)
                ?: throw IllegalArgumentException("Post not found: $postId")
            
            val workoutPost = workoutPostMapper.toDomain(post)
            val canViewPost = privacyEnforcementService.canViewPost(userId, workoutPost)
            if (!canViewPost) {
                return@withContext false // Can't check like status for posts they can't view
            }
            
            postLikeDao.isPostLikedByUser(postId, userId)
        }
    }
    
    override fun observePostLiked(postId: String, userId: String): Flow<Boolean> {
        return postLikeDao.observeIsPostLikedByUser(postId, userId)
    }
    
    override fun getPostLikers(postId: String, pageSize: Int): Flow<PagingData<PostLike>> {
        // For now, return a simple implementation using the available DAO method
        // In production, this would be a proper PagingSource or Room's direct PagingSource support
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { 
                // This is a simplified implementation - in production would use proper Room PagingSource
                // Room supports PagingSource directly, but we use a simple approach here
                object : androidx.paging.PagingSource<Int, PostLike>() {
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PostLike> {
                        return try {
                            val offset = params.key ?: 0
                            val limit = minOf(params.loadSize, pageSize)
                            
                            val likersWithProfiles = postLikeDao.getPostLikersWithProfiles(
                                postId = postId,
                                limit = limit
                            )
                            
                            val postLikes = likersWithProfiles.map { likerProfile ->
                                PostLike(
                                    id = likerProfile.id,
                                    postId = likerProfile.postId,
                                    userId = likerProfile.userId,
                                    createdAt = likerProfile.createdAt,
                                    userDisplayName = likerProfile.displayName ?: "",
                                    userUsername = likerProfile.username ?: "",
                                    userProfileImageUrl = likerProfile.profilePhotoUrl
                                )
                            }
                            
                            LoadResult.Page(
                                data = postLikes,
                                prevKey = if (offset == 0) null else offset - params.loadSize,
                                nextKey = if (postLikes.isEmpty()) null else offset + postLikes.size
                            )
                        } catch (e: Exception) {
                            LoadResult.Error(e)
                        }
                    }
                    
                    override fun getRefreshKey(state: androidx.paging.PagingState<Int, PostLike>): Int? {
                        return state.anchorPosition?.let { anchorPosition ->
                            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
                        }
                    }
                }
            }
        ).flow
    }
    
    // ==========================================
    // Comment Operations
    // ==========================================
    
    override suspend fun createComment(
        userId: String,
        request: CreateCommentRequest
    ): LiftrixResult<PostComment> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CREATE_COMMENT",
                errorMessage = "Failed to create comment",
                analyticsContext = mapOf(
                    "post_id" to request.postId,
                    "user_id" to userId,
                    "is_reply" to (request.parentCommentId != null).toString()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Check if user can view and comment on this post
            val post = workoutPostDao.getPostById(request.postId)
                ?: throw IllegalArgumentException("Post not found: ${request.postId}")
            
            val workoutPost = workoutPostMapper.toDomain(post)
            val canViewPost = privacyEnforcementService.canViewPost(userId, workoutPost)
            if (!canViewPost) {
                throw IllegalStateException("User cannot comment on post due to privacy restrictions")
            }
            
            val commentEntity = engagementMapper.createCommentEntity(userId, request)
            postCommentDao.insertComment(commentEntity)
            
            // Update post comment count optimistically
            val currentPost = workoutPostDao.getPostById(request.postId)
            currentPost?.let { post ->
                val newCount = post.commentCount + 1
                workoutPostDao.updateCommentCount(request.postId, newCount, System.currentTimeMillis())
            }
            
            // Get author profile for enhanced response
            val authorProfile = socialProfileDao.getSocialProfileByUserId(userId)
            
            engagementMapper.toDomain(
                entity = commentEntity,
                authorUsername = authorProfile?.username,
                authorDisplayName = authorProfile?.displayName,
                authorProfilePhotoUrl = authorProfile?.profilePhotoUrl
            )
        }
    }
    
    override suspend fun editComment(
        userId: String,
        request: EditCommentRequest
    ): LiftrixResult<PostComment> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "EDIT_COMMENT",
                errorMessage = "Failed to edit comment",
                analyticsContext = mapOf(
                    "comment_id" to request.commentId,
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val existingComment = postCommentDao.getCommentById(request.commentId)
                ?: throw IllegalArgumentException("Comment not found")
            
            if (existingComment.userId != userId) {
                throw SecurityException("User not authorized to edit this comment")
            }
            
            val currentTime = System.currentTimeMillis()
            postCommentDao.editComment(
                commentId = request.commentId,
                userId = userId,
                content = request.content,
                editedAt = currentTime,
                updatedAt = currentTime
            )
            
            // Get updated comment and author profile
            val updatedComment = postCommentDao.getCommentById(request.commentId)!!
            val authorProfile = socialProfileDao.getSocialProfileByUserId(userId)
            
            engagementMapper.toDomain(
                entity = updatedComment,
                authorUsername = authorProfile?.username,
                authorDisplayName = authorProfile?.displayName,
                authorProfilePhotoUrl = authorProfile?.profilePhotoUrl
            )
        }
    }
    
    override suspend fun deleteComment(commentId: String, userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DELETE_COMMENT",
                errorMessage = "Failed to delete comment",
                analyticsContext = mapOf(
                    "comment_id" to commentId,
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val existingComment = postCommentDao.getCommentById(commentId)
                ?: throw IllegalArgumentException("Comment not found")
            
            // Check if user is comment author or post owner
            val post = workoutPostDao.getPostById(existingComment.postId)
            val canDelete = existingComment.userId == userId || post?.userId == userId
            
            if (!canDelete) {
                throw SecurityException("User not authorized to delete this comment")
            }
            
            postCommentDao.deleteCommentById(commentId, userId)
            
            // Update post comment count optimistically
            post?.let {
                val newCount = maxOf(0, it.commentCount - 1)
                workoutPostDao.updateCommentCount(it.id, newCount, System.currentTimeMillis())
            }
        }
    }
    
    override fun getPostComments(postId: String, pageSize: Int): Flow<PagingData<PostComment>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { postCommentDao.getTopLevelComments(postId) }
        ).flow.map { pagingData ->
            pagingData.map { entity ->
                enhanceCommentWithUserData(entity)
            }
        }
    }
    
    override suspend fun getCommentReplies(commentId: String): LiftrixResult<List<PostComment>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_COMMENT_REPLIES",
                errorMessage = "Failed to get comment replies",
                analyticsContext = mapOf("comment_id" to commentId)
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val replyEntities = postCommentDao.getReplies(commentId)
            replyEntities.map { entity ->
                enhanceCommentWithUserData(entity)
            }
        }
    }
    
    override fun observeCommentCount(postId: String): Flow<Int> {
        return postCommentDao.observeCommentCount(postId)
    }
    
    // ==========================================
    // Save Operations
    // ==========================================
    
    override suspend fun toggleSave(postId: String, userId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TOGGLE_POST_SAVE",
                errorMessage = "Failed to toggle post save",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val isCurrentlySaved = savedPostDao.isPostSaved(userId, postId)
            
            if (isCurrentlySaved) {
                // Unsave: Remove save and decrement count
                savedPostDao.unsavePostByIds(userId, postId)
                
                // Update post save count optimistically
                val currentPost = workoutPostDao.getPostById(postId)
                currentPost?.let { post ->
                    val newCount = maxOf(0, post.saveCount - 1)
                    workoutPostDao.updateSaveCount(postId, newCount, System.currentTimeMillis())
                    
                    // Track unsave analytics
                    analyticsTracker.trackEngagement(
                        action = "UNSAVE",
                        contentType = "POST",
                        contentId = postId,
                        contentOwnerUserId = post.userId,
                        userId = userId
                    )
                }
                
                false
            } else {
                // Save: Add save and increment count
                val saveEntity = engagementMapper.createSavedPostEntity(postId, userId)
                savedPostDao.savePost(saveEntity)
                
                // Update post save count optimistically
                val currentPost = workoutPostDao.getPostById(postId)
                currentPost?.let { post ->
                    val newCount = post.saveCount + 1
                    workoutPostDao.updateSaveCount(postId, newCount, System.currentTimeMillis())
                    
                    // Track save analytics
                    analyticsTracker.trackEngagement(
                        action = "SAVE",
                        contentType = "POST",
                        contentId = postId,
                        contentOwnerUserId = post.userId,
                        userId = userId
                    )
                }
                
                true
            }
        }
    }
    
    override suspend fun isPostSaved(postId: String, userId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "IS_POST_SAVED",
                errorMessage = "Failed to check post save status",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            savedPostDao.isPostSaved(userId, postId)
        }
    }
    
    override fun observePostSaved(postId: String, userId: String): Flow<Boolean> {
        return savedPostDao.observeIsPostSaved(userId, postId)
    }
    
    override fun getUserSavedPosts(userId: String, pageSize: Int): Flow<PagingData<WorkoutPost>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { 
                object : androidx.paging.PagingSource<Int, WorkoutPost>() {
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, WorkoutPost> {
                        return try {
                            val offset = params.key ?: 0
                            val limit = minOf(params.loadSize, pageSize)
                            
                            // Get saved posts with details - limited approach for now
                            // In production, this would be a proper offset-based query
                            val allSavedPosts = savedPostDao.getUserSavedPostsWithDetails(userId)
                            val savedPostsList = mutableListOf<WorkoutPost>()
                            
                            // Collect from Flow (simplified - in production use proper pagination)
                            allSavedPosts.collect { savedPosts ->
                                val paginatedPosts = savedPosts
                                    .drop(offset)
                                    .take(limit)
                                    .map { savedPostWithDetails ->
                                        WorkoutPost(
                                            id = savedPostWithDetails.postId,
                                            userId = savedPostWithDetails.userId,
                                            workoutId = "", // Would be mapped from workout_posts table
                                            caption = savedPostWithDetails.caption ?: "",
                                            mediaUrls = emptyList(),
                                            mediaThumbnails = emptyList(),
                                            mediaItems = emptyList(),
                                            visibility = PostVisibility.PUBLIC, // Default visibility
                                            workoutDuration = savedPostWithDetails.workoutDuration,
                                            totalVolume = 0.0, // Would be calculated
                                            exercisesCount = savedPostWithDetails.exercisesCount ?: 0,
                                            prsCount = savedPostWithDetails.prsCount,
                                            likeCount = 0, // Would be fetched separately
                                            commentCount = 0, // Would be fetched separately
                                            shareCount = 0, // Would be fetched separately
                                            saveCount = 0, // Would be fetched separately
                                            createdAt = savedPostWithDetails.createdAt,
                                            updatedAt = savedPostWithDetails.createdAt,
                                            authorUsername = savedPostWithDetails.authorUsername ?: "",
                                            authorDisplayName = savedPostWithDetails.authorDisplayName ?: "",
                                            authorProfilePhotoUrl = savedPostWithDetails.authorProfilePhotoUrl,
                                            isLikedByViewer = false, // Would be checked separately
                                            isSavedByViewer = true // Always true for saved posts
                                        )
                                    }
                                savedPostsList.addAll(paginatedPosts)
                                return@collect
                            }
                            
                            LoadResult.Page(
                                data = savedPostsList,
                                prevKey = if (offset == 0) null else offset - params.loadSize,
                                nextKey = if (savedPostsList.isEmpty()) null else offset + savedPostsList.size
                            )
                        } catch (e: Exception) {
                            LoadResult.Error(e)
                        }
                    }
                    
                    override fun getRefreshKey(state: androidx.paging.PagingState<Int, WorkoutPost>): Int? {
                        return state.anchorPosition?.let { anchorPosition ->
                            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
                        }
                    }
                }
            }
        ).flow
    }
    
    // ==========================================
    // Share Operations
    // ==========================================
    
    override suspend fun recordShare(
        postId: String,
        userId: String,
        shareMethod: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "RECORD_SHARE",
                errorMessage = "Failed to record share",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "user_id" to userId,
                    "share_method" to shareMethod
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Update post share count optimistically
            val currentPost = workoutPostDao.getPostById(postId)
            currentPost?.let { post ->
                val newCount = post.shareCount + 1
                workoutPostDao.updateShareCount(postId, newCount, System.currentTimeMillis())
            }
            
            // Analytics events handled by UI layer
        }
    }
    
    // ==========================================
    // Engagement Statistics
    // ==========================================
    
    override suspend fun getPostEngagementStats(
        postId: String,
        viewerId: String
    ): LiftrixResult<PostEngagementStats> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_POST_ENGAGEMENT_STATS",
                errorMessage = "Failed to get post engagement stats",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "viewer_id" to viewerId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val post = workoutPostDao.getPostById(postId)
                ?: throw IllegalArgumentException("Post not found")
            
            val isLiked = postLikeDao.isPostLikedByUser(postId, viewerId)
            val isSaved = savedPostDao.isPostSaved(viewerId, postId)
            
            PostEngagementStats(
                postId = postId,
                likeCount = post.likeCount,
                commentCount = post.commentCount,
                shareCount = post.shareCount,
                saveCount = post.saveCount,
                isLikedByViewer = isLiked,
                isSavedByViewer = isSaved
            )
        }
    }
    
    override fun observePostEngagementStats(
        postId: String,
        viewerId: String
    ): Flow<PostEngagementStats> {
        // Currently observing like count only - can be extended to combine multiple flows
        return postLikeDao.observeLikeCount(postId).map { likeCount ->
            PostEngagementStats(
                postId = postId,
                likeCount = likeCount,
                commentCount = 0, // Would need to observe comment count too
                shareCount = 0,
                saveCount = 0,
                isLikedByViewer = false,
                isSavedByViewer = false
            )
        }
    }
    
    override suspend fun getTrendingEngagement(
        timeWindowHours: Int,
        limit: Int
    ): LiftrixResult<List<Pair<String, Double>>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_TRENDING_ENGAGEMENT",
                errorMessage = "Failed to get trending engagement",
                analyticsContext = mapOf(
                    "time_window_hours" to timeWindowHours.toString(),
                    "limit" to limit.toString()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Trending calculation placeholder - returns empty for now
            emptyList()
        }
    }
    
    override suspend fun copyWorkoutFromPost(
        postId: String,
        userId: String
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "COPY_WORKOUT_FROM_POST",
                errorMessage = "Failed to copy workout from post",
                analyticsContext = mapOf(
                    "post_id" to postId,
                    "user_id" to userId,
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val post = workoutPostDao.getPostById(postId)
                ?: throw IllegalArgumentException("Post not found: $postId")
            
            // Workout copying logic implementation placeholder
            // Implementation would involve:
            // 1. Getting the workout from workoutDao using post.workoutId
            // 2. Creating a new workout template for the user
            // 3. Copying exercises, sets, and other workout data
            // 4. Returning the new template ID
            
            val templateId = java.util.UUID.randomUUID().toString()
            templateId
        }
    }
    
    // ==========================================
    // Helper Methods
    // ==========================================
    
    private suspend fun enhanceCommentWithUserData(entity: com.example.liftrix.data.local.entity.PostCommentEntity): PostComment {
        val authorProfile = socialProfileDao.getSocialProfileByUserId(entity.userId)
        
        return engagementMapper.toDomain(
            entity = entity,
            authorUsername = authorProfile?.username,
            authorDisplayName = authorProfile?.displayName,
            authorProfilePhotoUrl = authorProfile?.profilePhotoUrl
        )
    }
}