package com.example.liftrix.domain.repository.social

import androidx.paging.PagingData
import com.example.liftrix.domain.model.social.*
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for social engagement operations (likes, comments, saves, shares).
 * Implements optimistic updates for responsive UI interactions.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
interface EngagementRepository {
    
    // ==========================================
    // Like Operations
    // ==========================================
    
    /**
     * Toggles like status for a post with optimistic update.
     * Returns the new like state immediately, syncs in background.
     * 
     * @param postId Post to like/unlike
     * @param userId User performing the action
     * @return New like state (true if liked, false if unliked)
     */
    suspend fun toggleLike(postId: String, userId: String): LiftrixResult<Boolean>
    
    /**
     * Gets the current like status for a post by the user.
     * 
     * @param postId Post to check
     * @param userId User to check for
     * @return True if the user has liked the post
     */
    suspend fun isPostLiked(postId: String, userId: String): LiftrixResult<Boolean>
    
    /**
     * Observes like status changes for a post.
     * 
     * @param postId Post to observe
     * @param userId User to observe for
     * @return Flow of like status
     */
    fun observePostLiked(postId: String, userId: String): Flow<Boolean>
    
    /**
     * Gets users who liked a specific post.
     * 
     * @param postId Post to get likers for
     * @param pageSize Number of users per page
     * @return Paginated list of users who liked the post
     */
    fun getPostLikers(postId: String, pageSize: Int = 20): Flow<PagingData<PostLike>>
    
    // ==========================================
    // Comment Operations
    // ==========================================
    
    /**
     * Creates a new comment on a post.
     * 
     * @param userId User creating the comment
     * @param request Comment creation details
     * @return Created comment
     */
    suspend fun createComment(
        userId: String,
        request: CreateCommentRequest
    ): LiftrixResult<PostComment>
    
    /**
     * Edits an existing comment.
     * 
     * @param userId User editing the comment (must be author)
     * @param request Edit details
     * @return Updated comment
     */
    suspend fun editComment(
        userId: String,
        request: EditCommentRequest
    ): LiftrixResult<PostComment>
    
    /**
     * Deletes a comment.
     * 
     * @param commentId Comment to delete
     * @param userId User deleting the comment (must be author or post owner)
     * @return Success result
     */
    suspend fun deleteComment(commentId: String, userId: String): LiftrixResult<Unit>
    
    /**
     * Gets comments for a post with pagination.
     * 
     * @param postId Post to get comments for
     * @param pageSize Number of comments per page
     * @return Paginated list of top-level comments
     */
    fun getPostComments(
        postId: String,
        viewerId: String,
        pageSize: Int = 20
    ): Flow<PagingData<PostComment>>
    
    /**
     * Gets replies to a specific comment.
     * 
     * @param commentId Parent comment to get replies for
     * @return List of reply comments
     */
    suspend fun getCommentReplies(commentId: String, viewerId: String): LiftrixResult<List<PostComment>>
    
    /**
     * Observes comment count changes for a post.
     * 
     * @param postId Post to observe
     * @return Flow of comment count
     */
    fun observeCommentCount(postId: String): Flow<Int>
    
    // ==========================================
    // Save Operations
    // ==========================================
    
    /**
     * Toggles save status for a post with optimistic update.
     * 
     * @param postId Post to save/unsave
     * @param userId User performing the action
     * @return New save state (true if saved, false if unsaved)
     */
    suspend fun toggleSave(postId: String, userId: String): LiftrixResult<Boolean>
    
    /**
     * Gets the current save status for a post by the user.
     * 
     * @param postId Post to check
     * @param userId User to check for
     * @return True if the user has saved the post
     */
    suspend fun isPostSaved(postId: String, userId: String): LiftrixResult<Boolean>
    
    /**
     * Observes save status changes for a post.
     * 
     * @param postId Post to observe
     * @param userId User to observe for
     * @return Flow of save status
     */
    fun observePostSaved(postId: String, userId: String): Flow<Boolean>
    
    /**
     * Gets all saved posts for a user.
     * 
     * @param userId User to get saved posts for
     * @param pageSize Number of posts per page
     * @return Paginated list of saved posts
     */
    fun getUserSavedPosts(userId: String, pageSize: Int = 20): Flow<PagingData<WorkoutPost>>
    
    // ==========================================
    // Share Operations
    // ==========================================
    
    /**
     * Records a share action for analytics.
     * Updates share count and tracks sharing method.
     * 
     * @param postId Post that was shared
     * @param userId User who shared the post
     * @param shareMethod How the post was shared (e.g., "copy_link", "instagram")
     * @return Success result
     */
    suspend fun recordShare(
        postId: String,
        userId: String,
        shareMethod: String
    ): LiftrixResult<Unit>
    
    // ==========================================
    // Engagement Statistics
    // ==========================================
    
    /**
     * Gets comprehensive engagement statistics for a post.
     * 
     * @param postId Post to get stats for
     * @param viewerId User viewing the stats (for personalized data)
     * @return Engagement statistics
     */
    suspend fun getPostEngagementStats(
        postId: String,
        viewerId: String
    ): LiftrixResult<PostEngagementStats>
    
    /**
     * Observes engagement statistics changes for a post.
     * 
     * @param postId Post to observe
     * @param viewerId User viewing the stats
     * @return Flow of engagement statistics
     */
    fun observePostEngagementStats(
        postId: String,
        viewerId: String
    ): Flow<PostEngagementStats>
    
    /**
     * Gets trending engagement data for discovery feed.
     * 
     * @param timeWindowHours How far back to look for trending content
     * @param limit Number of trending posts to return
     * @return List of trending post IDs with engagement scores
     */
    suspend fun getTrendingEngagement(
        timeWindowHours: Int = 24,
        limit: Int = 50
    ): LiftrixResult<List<Pair<String, Double>>>
    
    /**
     * Copies a workout from a post to the user's workout templates.
     * 
     * @param postId Post containing the workout to copy
     * @param userId User who wants to copy the workout
     * @return Success result with template ID
     */
    suspend fun copyWorkoutFromPost(
        postId: String,
        userId: String
    ): LiftrixResult<String>
}
