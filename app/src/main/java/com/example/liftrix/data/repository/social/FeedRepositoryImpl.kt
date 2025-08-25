package com.example.liftrix.data.repository.social

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.map
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.PostLikeDao
import com.example.liftrix.data.local.dao.SavedPostDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.FeedCacheDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.data.paging.FeedRemoteMediator
import com.example.liftrix.data.paging.StableFeedPagingSource
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.social.CreateWorkoutPostRequest
import com.example.liftrix.domain.model.social.FeedType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.FeedCacheService
import com.example.liftrix.sync.WorkoutPostSyncWorker
import androidx.work.WorkManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import timber.log.Timber
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
    private val feedCacheService: FeedCacheService,
    private val feedCacheDao: FeedCacheDao,
    private val followRelationshipDao: FollowRelationshipDao,
    @ApplicationContext private val context: Context
) : FeedRepository {
    
    // Cache for followed user IDs to prevent feed clearing during sync
    private val followedUserIdsCache = mutableMapOf<String, List<String>>()
    
    @OptIn(ExperimentalPagingApi::class)
    override fun getFeed(
        userId: String,
        feedType: FeedType,
        targetUserId: String?,
        pageSize: Int
    ): Flow<PagingData<WorkoutPost>> = flow {
        
        val pager = when (feedType) {
            FeedType.HOME -> {
                // Get the list of users that the current user follows
                // Include the current user's own posts in their home feed
                val followedUserIds = withContext(Dispatchers.IO) {
                    // First try to get from database
                    val followedIds = followRelationshipDao.getFollowingUserIds(userId)
                    
                    // 🔥 FIX: Cache the followed IDs and use cache if DB returns empty
                    // This prevents feed from disappearing during sync operations
                    val finalIds = if (followedIds.isEmpty() && followedUserIdsCache.containsKey(userId)) {
                        // Use cached IDs if DB is temporarily empty during sync
                        followedUserIdsCache[userId] ?: emptyList()
                    } else {
                        // Update cache with fresh data
                        if (followedIds.isNotEmpty()) {
                            followedUserIdsCache[userId] = followedIds
                        }
                        followedIds
                    }
                    
                    // Include the current user's own posts in their home feed
                    val allUserIds = finalIds + userId
                    
                    // Check total posts for this user
                    val userPostCount = workoutPostDao.getUserPostCount(userId)
                    val publicCount = workoutPostDao.getUserPostCountByVisibility(userId, "PUBLIC")
                    val followersCount = workoutPostDao.getUserPostCountByVisibility(userId, "FOLLOWERS") 
                    val privateCount = workoutPostDao.getUserPostCountByVisibility(userId, "PRIVATE")
                    Timber.d("🔍 WORKOUT-POSTS-DEBUG: User $userId posts - Total: $userPostCount, Public: $publicCount, Followers: $followersCount, Private: $privateCount")
                    
                    allUserIds
                }
                
                Pager(
                    config = PagingConfig(
                        pageSize = pageSize,
                        enablePlaceholders = false,
                        prefetchDistance = pageSize / 2
                    ),
                    // 🔥 FIX: Remove RemoteMediator entirely to prevent cache-related feed clearing
                    // The feed will now always load directly from the database without any
                    // cache invalidation or network refresh logic
                    // remoteMediator = null, // Removed to prevent feed clearing
                    pagingSourceFactory = { 
                        // 🔥 FIX: Always include current user's posts and handle empty follow lists gracefully
                        // Use the new query that includes the current user's posts by default
                        val safeFollowedIds = if (followedUserIds.isEmpty()) {
                            // Empty list is fine with the new query since it always includes currentUserId
                            emptyList()
                        } else {
                            followedUserIds
                        }
                        
                        // 🔥 FIX: Wrap in StableFeedPagingSource to handle intermediate empty states
                        StableFeedPagingSource(
                            delegate = workoutPostDao.getHomeFeedPostsWithSelf(userId, safeFollowedIds),
                            userId = userId,
                            hasLocalPosts = { 
                                workoutPostDao.getUserPostCount(userId) > 0
                            }
                        )
                    }
                )
            }
            
            FeedType.DISCOVERY -> {
                // Get followed users to potentially exclude them from discovery
                val followedUserIds = withContext(Dispatchers.IO) {
                    val followedIds = followRelationshipDao.getFollowingUserIds(userId)
                    
                    // 🔥 FIX: Use cached IDs if DB is temporarily empty
                    val finalIds = if (followedIds.isEmpty() && followedUserIdsCache.containsKey(userId)) {
                        followedUserIdsCache[userId] ?: emptyList()
                    } else {
                        if (followedIds.isNotEmpty()) {
                            followedUserIdsCache[userId] = followedIds
                        }
                        followedIds
                    }
                    
                    // Only exclude followed users from discovery (allow current user's PUBLIC posts)
                    val excludeIds = finalIds
                    excludeIds
                }
                
                Pager(
                    config = PagingConfig(
                        pageSize = pageSize,
                        initialLoadSize = pageSize, // Load exactly pageSize items initially
                        enablePlaceholders = false,
                        prefetchDistance = pageSize / 2
                    ),
                    // 🔥 FIX: Remove RemoteMediator entirely to prevent cache-related feed clearing
                    // The feed will now always load directly from the database without any
                    // cache invalidation or network refresh logic
                    // remoteMediator = null, // Removed to prevent feed clearing
                    pagingSourceFactory = { 
                        // 🔥 FIX: Ensure exclude list is never empty for proper SQL
                        val safeExcludeIds = if (followedUserIds.isEmpty()) {
                            listOf("__dummy_user_1__")
                        } else {
                            followedUserIds
                        }
                        
                        // 🔥 FIX: Use direct PUBLIC posts query with proper exclusion handling
                        // TODO: Migrate to feed_cache once Firebase Functions are properly populating it
                        createDiscoveryDirectPagingSource(userId, safeExcludeIds)
                    }
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
        
        emitAll(pager.flow.map { pagingData ->
            pagingData.map { entity ->
                Timber.d("🔍 WORKOUT-POSTS-DEBUG: Feed delivering post ID=${entity.id}, user=${entity.userId}, visibility=${entity.visibility}, workout=${entity.workoutId}")
                enhanceWithUserData(entity, userId)
            }
        })
    }
    
    override suspend fun hasPostForWorkout(
        userId: String, 
        workoutId: String
    ): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHECK_WORKOUT_POST",
                errorMessage = "Failed to check for existing workout post",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "workout_id" to workoutId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val existingPost = workoutPostDao.getPostByWorkoutId(userId, workoutId)
            existingPost != null
        }
    }
    
    override suspend fun createPost(
        userId: String,
        request: CreateWorkoutPostRequest
    ): LiftrixResult<WorkoutPost> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CREATE_WORKOUT_POST",
                errorMessage = "Failed to create workout post",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "workout_id" to request.workoutId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val postId = UUID.randomUUID().toString()
            
            Timber.d("🔍 WORKOUT-POSTS-DEBUG: Creating post for user=$userId, workout=${request.workoutId}, visibility=${request.visibility}")
            
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
            
            Timber.d("🔍 WORKOUT-POSTS-DEBUG: Post created successfully - ID=$postId, visibility=${entity.visibility}")
            
            // 🔥 FIX: Enqueue sync worker to upload post to Firebase
            // This will trigger generateFeedOnPostCreation function to populate feed_cache
            val syncWorkRequest = WorkoutPostSyncWorker.createWorkRequest(userId)
            WorkManager.getInstance(context).enqueue(syncWorkRequest)
            
            enhanceWithUserData(entity, userId)
        }
    }
    
    override suspend fun getPost(postId: String, viewerId: String): LiftrixResult<WorkoutPost> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_WORKOUT_POST",
                errorMessage = "Failed to get workout post",
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
                code = "UPDATE_WORKOUT_POST",
                errorMessage = "Failed to update workout post",
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
                code = "DELETE_WORKOUT_POST",
                errorMessage = "Failed to delete workout post",
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
                code = "REFRESH_FEED",
                errorMessage = "Failed to refresh feed",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        // 🔥 FIX: Don't update cache or clear anything on refresh
        // The feed should persist and only update when new posts are added
        // Just return success without doing anything
        Unit
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
            pagingSourceFactory = { 
                // Create a custom PagingSource that fetches posts from a specific user
                object : PagingSource<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity>() {
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity> {
                        return try {
                            // Get all posts from this specific user
                            val posts = workoutPostDao.getUserPosts(userId).first()
                            val page = params.key ?: 0
                            val startIndex = page * params.loadSize
                            val endIndex = minOf(startIndex + params.loadSize, posts.size)
                            
                            Timber.d("Loading user posts for $userId: page=$page, total=${posts.size}, returning ${endIndex - startIndex} items")
                            
                            if (startIndex >= posts.size) {
                                LoadResult.Page(
                                    data = emptyList(),
                                    prevKey = if (page > 0) page - 1 else null,
                                    nextKey = null
                                )
                            } else {
                                LoadResult.Page(
                                    data = posts.subList(startIndex, endIndex),
                                    prevKey = if (page > 0) page - 1 else null,
                                    nextKey = if (endIndex < posts.size) page + 1 else null
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error loading user posts for $userId")
                            LoadResult.Error(e)
                        }
                    }
                    
                    override fun getRefreshKey(state: PagingState<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity>): Int? {
                        return state.anchorPosition?.let { anchorPosition ->
                            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
                        }
                    }
                }
            }
        ).flow.map { pagingData ->
            pagingData.map { entity ->
                enhanceWithUserData(entity, viewerId)
            }
        }
    }
    
    override fun getUserMentions(userId: String, pageSize: Int): Flow<PagingData<WorkoutPost>> {
        // Note: Mentions will be implemented when comment system supports @ mentions
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
                code = "INVALIDATE_FEED_CACHE",
                errorMessage = "Failed to invalidate feed cache",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        // Use feed-specific invalidation for feed content changes
        feedCacheService.invalidateFeedCache(userId).getOrThrow()
    }
    
    /**
     * Creates a PagingSource for discovery feed using direct PUBLIC posts query
     * This bypasses feed_cache and queries workout_posts directly for PUBLIC visibility
     */
    private fun createDiscoveryDirectPagingSource(userId: String, excludeUserIds: List<String>): PagingSource<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity> {
        return object : PagingSource<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity> {
                return try {
                    val page = params.key ?: 0
                    val pageSize = params.loadSize
                    val offset = page * pageSize
                    
                    // Query PUBLIC posts, excluding followed users + dummy user
                    val posts = workoutPostDao.getPublicPostsExcludingUsers(
                        excludeUserIds = excludeUserIds,
                        limit = pageSize,
                        offset = offset
                    )
                    
                    
                    LoadResult.Page(
                        data = posts,
                        prevKey = if (page > 0) page - 1 else null,
                        nextKey = if (posts.size == pageSize) page + 1 else null
                    )
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error loading discovery feed for user $userId")
                    LoadResult.Error(e)
                }
            }
            
            override fun getRefreshKey(state: PagingState<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity>): Int? {
                return state.anchorPosition?.let { anchorPosition ->
                    state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                        ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
                }
            }
        }
    }
    
    /**
     * Creates a PagingSource for discovery feed using feed_cache populated by Firebase Functions
     * DEPRECATED: Use createDiscoveryDirectPagingSource until Firebase Functions populate feed_cache
     */
    private fun createDiscoveryFeedPagingSource(userId: String): PagingSource<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity> {
        return object : PagingSource<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity> {
                return try {
                    val page = params.key ?: 0
                    val pageSize = params.loadSize
                    val offset = page * pageSize
                    
                    // Get post IDs from feed_cache ordered by relevance score
                    val cachedPostIds = feedCacheDao.getCachedPostIdsByType(
                        userId = userId,
                        feedType = "DISCOVERY",
                        limit = pageSize,
                        offset = offset
                    )
                    
                    if (cachedPostIds.isEmpty()) {
                        return LoadResult.Page(
                            data = emptyList(),
                            prevKey = if (page > 0) page - 1 else null,
                            nextKey = null
                        )
                    }
                    
                    // Get actual post entities using the cached post IDs
                    val posts = cachedPostIds.mapNotNull { postId ->
                        workoutPostDao.getPostById(postId)
                    }
                    
                    
                    LoadResult.Page(
                        data = posts,
                        prevKey = if (page > 0) page - 1 else null,
                        nextKey = if (posts.size == pageSize) page + 1 else null
                    )
                    
                } catch (e: Exception) {
                    Timber.e(e, "Error loading discovery feed from cache for user $userId")
                    LoadResult.Error(e)
                }
            }
            
            override fun getRefreshKey(state: PagingState<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity>): Int? {
                return state.anchorPosition?.let { anchorPosition ->
                    state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                        ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
                }
            }
        }
    }
    
    /**
     * Enhances post entity with user interaction data and author information
     */
    private suspend fun enhanceWithUserData(entity: com.example.liftrix.data.local.entity.WorkoutPostEntity, viewerId: String): WorkoutPost {
        // Get user interaction data
        val isLiked = postLikeDao.isPostLikedByUser(entity.id, viewerId)
        val isSaved = savedPostDao.isPostSaved(viewerId, entity.id)
        
        // Get author profile information - log error but don't crash
        val authorProfile = socialProfileDao.getSocialProfileByUserId(entity.userId)
        
        if (authorProfile == null) {
            Timber.e("PROFILE_MISSING: Social profile not found for user ${entity.userId} in post ${entity.id}")
        } else {
            // Fix empty string profile photo URLs by converting them to null
            val cleanedPhotoUrl = authorProfile.profilePhotoUrl?.takeIf { it.isNotBlank() }
            if (authorProfile.profilePhotoUrl != cleanedPhotoUrl) {
                Timber.w("PROFILE_FIX: Converting empty profile photo URL to null for user ${entity.userId}")
                try {
                    socialProfileDao.updateProfile(authorProfile.copy(profilePhotoUrl = cleanedPhotoUrl))
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update profile photo URL for user ${entity.userId}")
                }
            }
            Timber.d("Enhanced post ${entity.id} with profile: ${authorProfile.username}, photo: ${cleanedPhotoUrl}")
        }
        
        return workoutPostMapper.toDomain(
            entity = entity,
            isLikedByViewer = isLiked,
            isSavedByViewer = isSaved,
            authorUsername = authorProfile?.username ?: "ERROR_NO_PROFILE",
            authorDisplayName = authorProfile?.displayName ?: "Missing Profile",
            authorProfilePhotoUrl = authorProfile?.profilePhotoUrl?.takeIf { it.isNotBlank() }
        )
    }
    
    // Helper functions for workout metadata calculation
    private fun calculateWorkoutDuration(workout: Any?): Int? {
        // For now, return null as workout duration calculation needs WorkoutEntity structure
        // In a full implementation, this would calculate duration from workout.startTime to workout.endTime
        return null
    }
    
    private fun calculateTotalVolume(workout: Any?): Double? {
        // For now, return null as total volume calculation needs access to exercise sets
        // In a full implementation, this would sum weight * reps for all completed sets
        return null
    }
    
    private fun calculateExercisesCount(workout: Any?): Int? {
        // For now, return null as exercise count needs access to exercises collection
        // In a full implementation, this would count unique exercises in the workout
        return null
    }
    
    override fun getHomeFeed(
        userId: String,
        pageSize: Int
    ): Flow<PagingData<WorkoutPost>> {
        return getFeed(
            userId = userId,
            feedType = FeedType.HOME,
            targetUserId = null,
            pageSize = pageSize
        )
    }
    
    override fun getDiscoveryFeed(
        userId: String,
        pageSize: Int
    ): Flow<PagingData<WorkoutPost>> {
        return getFeed(
            userId = userId,
            feedType = FeedType.DISCOVERY,
            targetUserId = null,
            pageSize = pageSize
        )
    }
}