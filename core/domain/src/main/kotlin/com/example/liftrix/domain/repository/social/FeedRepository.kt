package com.example.liftrix.domain.repository.social

import androidx.paging.PagingData
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.social.CreateWorkoutPostRequest
import com.example.liftrix.domain.model.social.FeedType
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for social feed operations.
 * Provides paginated feed data with caching and real-time updates.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
interface FeedRepository {
    
    /**
     * Gets a paginated feed for the specified user and feed type.
     * Implements intelligent caching and relevance scoring.
     * 
     * @param userId User requesting the feed
     * @param feedType Type of feed (HOME, DISCOVERY, USER)
     * @param targetUserId Required for USER feed type
     * @param pageSize Number of posts per page (default: 20)
     * @return Flow of PagingData for infinite scroll
     */
    fun getFeed(
        userId: String,
        feedType: FeedType,
        targetUserId: String? = null,
        pageSize: Int = 20
    ): Flow<PagingData<WorkoutPost>>
    
    /**
     * Checks if a post already exists for a specific workout.
     * Used to prevent duplicate posts during auto-creation.
     * 
     * @param userId User who completed the workout
     * @param workoutId Workout to check for existing posts
     * @return True if a post already exists, false otherwise
     */
    suspend fun hasPostForWorkout(userId: String, workoutId: String): LiftrixResult<Boolean>
    
    /**
     * Creates a new workout post.
     * 
     * @param userId Author of the post
     * @param request Post creation details
     * @return Created workout post
     */
    suspend fun createPost(
        userId: String,
        request: CreateWorkoutPostRequest
    ): LiftrixResult<WorkoutPost>
    
    /**
     * Gets a specific workout post by ID.
     * 
     * @param postId Post to retrieve
     * @param viewerId User requesting the post (for privacy checks)
     * @return Workout post if found and accessible
     */
    suspend fun getPost(postId: String, viewerId: String): LiftrixResult<WorkoutPost>
    
    /**
     * Updates an existing workout post.
     * Only allows updating caption and visibility.
     * 
     * @param postId Post to update
     * @param userId Author of the post (for authorization)
     * @param caption New caption (null to keep existing)
     * @param visibility New visibility (null to keep existing)
     * @return Updated workout post
     */
    suspend fun updatePost(
        postId: String,
        userId: String,
        caption: String? = null,
        visibility: String? = null
    ): LiftrixResult<WorkoutPost>
    
    /**
     * Deletes a workout post.
     * 
     * @param postId Post to delete
     * @param userId Author of the post (for authorization)
     */
    suspend fun deletePost(postId: String, userId: String): LiftrixResult<Unit>
    
    /**
     * Refreshes the feed cache for a user.
     * Forces re-calculation of relevance scores and cache update.
     * 
     * @param userId User whose feed cache to refresh
     */
    suspend fun refreshFeed(userId: String): LiftrixResult<Unit>
    
    /**
     * Gets posts from a specific user with pagination.
     * 
     * @param userId User whose posts to retrieve
     * @param viewerId User requesting the posts (for privacy checks)
     * @param pageSize Number of posts per page
     * @return Flow of PagingData for user's posts
     */
    fun getUserPosts(
        userId: String,
        viewerId: String,
        pageSize: Int = 20
    ): Flow<PagingData<WorkoutPost>>
    
    /**
     * Gets posts that mention or involve the specified user.
     * Includes posts they're tagged in or mentioned in comments.
     * 
     * @param userId User to find mentions for
     * @param pageSize Number of posts per page
     * @return Flow of PagingData for mentions
     */
    fun getUserMentions(
        userId: String,
        pageSize: Int = 20
    ): Flow<PagingData<WorkoutPost>>
    
    /**
     * Gets trending posts based on recent engagement.
     * Used for discovery feed and explore functionality.
     * 
     * @param viewerId User requesting trending posts
     * @param timeWindowHours How far back to look for trending content
     * @param pageSize Number of posts per page
     * @return Flow of PagingData for trending posts
     */
    fun getTrendingPosts(
        viewerId: String,
        timeWindowHours: Int = 24,
        pageSize: Int = 20
    ): Flow<PagingData<WorkoutPost>>
    
    /**
     * Invalidates feed cache when user's social graph changes.
     * Called when user follows/unfollows someone.
     * 
     * @param userId User whose social graph changed
     */
    suspend fun invalidateFeedCache(userId: String): LiftrixResult<Unit>
    
    /**
     * Gets the home feed for a specific user.
     * Shows posts from users they follow.
     * 
     * @param userId User requesting the home feed
     * @param pageSize Number of posts per page
     * @return Flow of PagingData for home feed
     */
    fun getHomeFeed(
        userId: String,
        pageSize: Int = 20
    ): Flow<PagingData<WorkoutPost>>
    
    /**
     * Gets the discovery feed for a specific user.
     * Shows public posts from users they don't follow.
     * 
     * @param userId User requesting the discovery feed
     * @param pageSize Number of posts per page
     * @return Flow of PagingData for discovery feed
     */
    fun getDiscoveryFeed(
        userId: String,
        pageSize: Int = 20
    ): Flow<PagingData<WorkoutPost>>
}