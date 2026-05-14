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
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.data.paging.StableFeedPagingSource
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.social.CreateWorkoutPostRequest
import com.example.liftrix.domain.model.social.FeedType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.FeedCacheService
import com.example.liftrix.domain.sync.SyncScheduler
import com.example.liftrix.domain.usecase.social.OfficialLiftrixAccountCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import com.example.liftrix.core.json.ExerciseJsonParser
import com.google.gson.annotations.SerializedName
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
    private val userProfileDao: UserProfileDao,
    private val workoutDao: WorkoutDao,
    private val workoutPostMapper: WorkoutPostMapper,
    private val feedCacheService: FeedCacheService,
    private val feedCacheDao: FeedCacheDao,
    private val followRelationshipDao: FollowRelationshipDao,
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler
) : FeedRepository {

    private companion object {
        private const val FOLLOWING_FEED_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
    }

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
                try {
                    syncScheduler.enqueueWorkoutPostSync(userId, forceSync = true)
                } catch (e: Exception) {
                    Timber.w(e, "[WORKOUT-POSTS] Failed to enqueue following post hydration")
                }

                // Get the list of users that the current user follows
                // Include the current user's own posts in their home feed
                val followedUserIds = withContext(Dispatchers.IO) {
                    // First try to get from database
                    val followedIds = followRelationshipDao.getFollowingUserIds(userId)

                    // Cache the followed IDs and use cache if DB returns empty
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
                    val allUserIds = (finalIds + OfficialLiftrixAccountCatalog.accounts.map { it.id } + userId).distinct()

                    // Check total posts for this user
                    val userPostCount = workoutPostDao.getUserPostCount(userId)
                    val publicCount = workoutPostDao.getUserPostCountByVisibility(userId, "PUBLIC")
                    val followersCount = workoutPostDao.getUserPostCountByVisibility(userId, "FOLLOWERS")
                    val privateCount = workoutPostDao.getUserPostCountByVisibility(userId, "PRIVATE")
                    Timber.d("[WORKOUT-POSTS] User $userId posts - Total: $userPostCount, Public: $publicCount, Followers: $followersCount, Private: $privateCount")

                    allUserIds
                }

                Pager(
                    config = PagingConfig(
                        pageSize = pageSize,
                        enablePlaceholders = false,
                        prefetchDistance = pageSize / 2
                    ),
                    // Remove RemoteMediator entirely to prevent cache-related feed clearing
                    // The feed will now always load directly from the database without any
                    // cache invalidation or network refresh logic
                    // remoteMediator = null, // Removed to prevent feed clearing
                    pagingSourceFactory = {
                        // Always include current user's posts and handle empty follow lists gracefully
                        // Use the new query that includes the current user's posts by default
                        val safeFollowedIds = if (followedUserIds.isEmpty()) {
                            // Empty list is fine with the new query since it always includes currentUserId
                            emptyList()
                        } else {
                            followedUserIds
                        }

                        // Wrap in StableFeedPagingSource to handle intermediate empty states
                        StableFeedPagingSource(
                            delegate = workoutPostDao.getHomeFeedPostsWithSelfSince(
                                currentUserId = userId,
                                followedUserIds = safeFollowedIds,
                                sinceTimestamp = System.currentTimeMillis() - FOLLOWING_FEED_RETENTION_MILLIS
                            ),
                            userId = userId,
                            hasLocalPosts = {
                                workoutPostDao.getUserPostCount(userId) > 0
                            }
                        )
                    }
                )
            }

            FeedType.DISCOVERY -> {
                try {
                    syncScheduler.enqueueWorkoutPostSync(userId, forceSync = true)
                } catch (e: Exception) {
                    Timber.w(e, "[WORKOUT-POSTS] Failed to enqueue public post hydration for discovery feed")
                }

                // Exclude only the viewer's own posts from discovery. Followed users are still
                // eligible because discovery is the public feed surface.
                val followedUserIds = withContext(Dispatchers.IO) {
                    val followedIds = followRelationshipDao.getFollowingUserIds(userId)
                    if (followedIds.isNotEmpty()) {
                        followedUserIdsCache[userId] = followedIds
                    }
                    Timber.i(
                        "[PUBLIC-LOG] Discovery exclusion user=$userId followedCount=${followedIds.size} excludedAuthors=[$userId]"
                    )
                    listOf(userId)
                }

                Pager(
                    config = PagingConfig(
                        pageSize = pageSize,
                        initialLoadSize = pageSize, // Load exactly pageSize items initially
                        enablePlaceholders = false,
                        prefetchDistance = pageSize / 2
                    ),
                    // Remove RemoteMediator entirely to prevent cache-related feed clearing
                    // The feed will now always load directly from the database without any
                    // cache invalidation or network refresh logic
                    // remoteMediator = null, // Removed to prevent feed clearing
                    pagingSourceFactory = {
                        // Ensure exclude list is never empty for proper SQL
                        val safeExcludeIds = if (followedUserIds.isEmpty()) {
                            listOf("__dummy_user_1__")
                        } else {
                            followedUserIds
                        }

                        // Use hybrid approach - feed_cache first, fallback to direct query
                        createHybridDiscoveryPagingSource(userId, safeExcludeIds)
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
                Timber.d("[WORKOUT-POSTS] Feed delivering post ID=${entity.id}, user=${entity.userId}, visibility=${entity.visibility}, workout=${entity.workoutId}")
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

            Timber.d("[WORKOUT-POSTS] Creating post for user=$userId, workout=${request.workoutId}, visibility=${request.visibility}")

            // Get workout details for metadata
            val workout = workoutDao.getWorkoutByIdForUser(request.workoutId, userId)

            val calculatedVolume = calculateTotalVolume(workout)
            Timber.d("[WORKOUT-POSTS] About to create post entity - workoutId=${request.workoutId}, calculatedVolume=$calculatedVolume")

            val entity = workoutPostMapper.createEntityFromRequest(
                id = postId,
                userId = userId,
                request = request,
                workoutDuration = calculateWorkoutDuration(workout),
                totalVolume = calculatedVolume,
                exercisesCount = calculateExercisesCount(workout),
                prsCount = 0 // Will be calculated later
            )

            workoutPostDao.upsertLocal(entity)
            Timber.i(
                "[PUBLIC-LOG] Local post created id=${entity.id} user=$userId workout=${entity.workoutId} visibility=${entity.visibility} isDirty=${entity.isDirty} isSynced=${entity.isSynced}"
            )

            Timber.d("[WORKOUT-POSTS] Post created successfully - ID=$postId, visibility=${entity.visibility}")

            // Enqueue sync worker to upload post to Firebase
            // This will trigger generateFeedOnPostCreation function to populate feed_cache
            syncScheduler.enqueueWorkoutPostSync(userId)

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

            workoutPostDao.upsertLocal(updatedPost)

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
        // Don't update cache or clear anything on refresh
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
     * Auto-creates social profiles when missing to ensure posts always have profile data
     */
    private suspend fun enhanceWithUserData(entity: WorkoutPostEntity, viewerId: String): WorkoutPost {
        val isLiked = postLikeDao.isPostLikedByUser(entity.id, viewerId)
        val isSaved = savedPostDao.isPostSaved(viewerId, entity.id)

        var authorProfile = loadOrCreateAuthorProfile(entity)
        authorProfile = normalizeAuthorProfilePhoto(entity, authorProfile)
        authorProfile = syncMissingSocialProfilePhoto(entity, authorProfile)
        val authorDisplayInfo = buildAuthorDisplayInfo(entity, authorProfile)

        return workoutPostMapper.toDomain(
            entity = entity,
            isLikedByViewer = isLiked,
            isSavedByViewer = isSaved,
            authorUsername = authorDisplayInfo.username,
            authorDisplayName = authorDisplayInfo.displayName,
            authorProfilePhotoUrl = authorDisplayInfo.profilePhotoUrl
        )
    }

    private suspend fun loadOrCreateAuthorProfile(entity: WorkoutPostEntity): SocialProfileEntity? {
        var authorProfile = socialProfileDao.getSocialProfileByUserId(entity.userId)

        Timber.d("[PROFILE-PHOTO] PROFILE_LOOKUP: User ${entity.userId} | foundSocialProfile=${authorProfile != null} | photoUrl='${authorProfile?.profilePhotoUrl}' | displayName='${authorProfile?.displayName}' | username='${authorProfile?.username}'")

        if (authorProfile == null) {
            Timber.e("[PROFILE-PHOTO] PROFILE_MISSING: Social profile not found for user ${entity.userId} in post ${entity.id}")
            Timber.i("[PROFILE-PHOTO] AUTO_PROFILE_CREATE: Creating fallback social profile for user ${entity.userId}")
            authorProfile = createFallbackSocialProfile(entity.userId)

            if (authorProfile != null) {
                Timber.i("[PROFILE-PHOTO] AUTO_PROFILE_SUCCESS: Created social profile for user ${entity.userId}, hasPhoto: ${authorProfile.profilePhotoUrl != null}")
            } else {
                Timber.e("[PROFILE-PHOTO] AUTO_PROFILE_FAILED: Could not create social profile for user ${entity.userId}")
            }
        }

        return authorProfile
    }

    private suspend fun normalizeAuthorProfilePhoto(
        entity: WorkoutPostEntity,
        authorProfile: SocialProfileEntity?
    ): SocialProfileEntity? {
        if (authorProfile == null) return null

        val cleanedPhotoUrl = authorProfile.profilePhotoUrl?.takeIf { it.isNotBlank() }
        if (authorProfile.profilePhotoUrl == cleanedPhotoUrl) return authorProfile

        Timber.w("[PROFILE-PHOTO] PROFILE_FIX: Converting empty profile photo URL to null for user ${entity.userId}")
        return try {
            val cleanedProfile = authorProfile.copy(profilePhotoUrl = cleanedPhotoUrl)
            socialProfileDao.updateProfile(cleanedProfile)
            cleanedProfile
        } catch (e: Exception) {
            Timber.e(e, "[PROFILE-PHOTO] Failed to update profile photo URL for user ${entity.userId}")
            authorProfile
        }
    }

    private suspend fun syncMissingSocialProfilePhoto(
        entity: WorkoutPostEntity,
        authorProfile: SocialProfileEntity?
    ): SocialProfileEntity? {
        if (authorProfile == null) return null

        val cleanedPhotoUrl = authorProfile.profilePhotoUrl?.takeIf { it.isNotBlank() }
        return try {
            val mainProfilePhoto = userProfileDao.getProfileImageUrl(entity.userId)
            val currentUserId = try {
                authRepository.getCurrentUserId()
            } catch (e: Exception) {
                Timber.w(e, "[PROFILE-PHOTO] CURRENT_USER_ID_ERROR: Failed to get current user ID")
                null
            }

            val firebaseAuthPhoto = if (entity.userId == currentUserId?.value) {
                try {
                    val authUser = authRepository.getCurrentUser()
                    Timber.d("[PROFILE-PHOTO] AUTH_CHECK: User ${entity.userId} is current user, checking Firebase Auth photo")
                    authUser?.photoUrl
                } catch (e: Exception) {
                    Timber.w(e, "[PROFILE-PHOTO] AUTH_FALLBACK_ERROR: Failed to get Firebase Auth user for ${entity.userId}")
                    null
                }
            } else {
                Timber.d("[PROFILE-PHOTO] AUTH_SKIP: User ${entity.userId} is not current user ($currentUserId), cannot access Firebase Auth")
                null
            }

            val effectiveMainPhoto = mainProfilePhoto ?: firebaseAuthPhoto

            Timber.d("[PROFILE-PHOTO] SYNC_CHECK: User ${entity.userId} | dbProfilePhoto='$mainProfilePhoto' | firebaseAuthPhoto='$firebaseAuthPhoto' | effectiveMainPhoto='$effectiveMainPhoto' | socialProfilePhoto='$cleanedPhotoUrl'")

            val updatedProfile = if (effectiveMainPhoto != null && cleanedPhotoUrl == null) {
                updateMissingSocialProfilePhoto(entity, authorProfile, effectiveMainPhoto)
            } else {
                Timber.d("[PROFILE-PHOTO] SYNC_SKIP: User ${entity.userId} | reason: effectiveMainPhoto=${effectiveMainPhoto != null}, socialPhoto=${cleanedPhotoUrl != null}")
                authorProfile
            }

            Timber.d("[PROFILE-PHOTO] PROFILE_ENHANCED: Post ${entity.id} enhanced with profile: ${updatedProfile.username}, photo: ${updatedProfile.profilePhotoUrl ?: "null"}, mainPhoto: ${mainProfilePhoto ?: "null"}")
            updatedProfile
        } catch (e: Exception) {
            Timber.e(e, "[PROFILE-PHOTO] SYNC_CHECK_ERROR: Failed to check main profile photo for user ${entity.userId}")
            Timber.d("[PROFILE-PHOTO] PROFILE_ENHANCED: Post ${entity.id} enhanced with profile: ${authorProfile.username}, photo: ${authorProfile.profilePhotoUrl ?: "null"}")
            authorProfile
        }
    }

    private suspend fun updateMissingSocialProfilePhoto(
        entity: WorkoutPostEntity,
        authorProfile: SocialProfileEntity,
        effectiveMainPhoto: String
    ): SocialProfileEntity {
        Timber.e("[PROFILE-PHOTO] [PROFILE-PHOTO-SYNC-MISMATCH] User ${entity.userId} has effective profile photo but social profile photo is null")
        Timber.e("[PROFILE-PHOTO] [PROFILE-PHOTO-SYNC-MISMATCH] EffectiveMain: $effectiveMainPhoto, Social: null - attempting immediate fix")

        return try {
            val updatedAt = System.currentTimeMillis()
            val rowsUpdated = socialProfileDao.updateProfilePhoto(entity.userId, effectiveMainPhoto, updatedAt)

            if (rowsUpdated > 0) {
                Timber.i("[PROFILE-PHOTO] [PROFILE-PHOTO-SYNC-FIX] Auto-synced profile photo for user ${entity.userId}")
                try {
                    syncScheduler.enqueueSocialProfileSync(entity.userId, forceSync = true)
                    Timber.d("[PROFILE-PHOTO] [PROFILE-PHOTO-SYNC-FIX] Social profile sync to Firebase triggered")
                } catch (e: Exception) {
                    Timber.e(e, "[PROFILE-PHOTO] [PROFILE-PHOTO-SYNC-FIX] Failed to trigger Firebase sync")
                }
                authorProfile.copy(profilePhotoUrl = effectiveMainPhoto)
            } else {
                Timber.w("[PROFILE-PHOTO] [PROFILE-PHOTO-SYNC-FIX] No social profile rows updated - profile may not exist")
                authorProfile
            }
        } catch (e: Exception) {
            Timber.e(e, "[PROFILE-PHOTO] [PROFILE-PHOTO-SYNC-FIX] Failed to auto-sync profile photo for user ${entity.userId}")
            authorProfile
        }
    }

    private fun buildAuthorDisplayInfo(
        entity: WorkoutPostEntity,
        authorProfile: SocialProfileEntity?
    ): AuthorDisplayInfo {
        val finalUsername = authorProfile?.username?.takeIf { it.isNotBlank() } ?: "user${entity.userId.take(8)}"
        val finalDisplayName = authorProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: finalUsername.takeIf { !it.startsWith("user") }
            ?: "Liftrix User"
        val safeDisplayName = if (finalDisplayName.isBlank()) "Liftrix User" else finalDisplayName
        val finalPhotoUrl = authorProfile?.profilePhotoUrl?.takeIf { it.isNotBlank() }

        Timber.d("[PROFILE-PHOTO] POST_ENHANCED: Post ${entity.id} final data - username: $finalUsername, displayName: $safeDisplayName, hasPhoto: ${finalPhotoUrl != null}, photoUrl: '${finalPhotoUrl ?: "null"}'")

        return AuthorDisplayInfo(
            username = finalUsername,
            displayName = safeDisplayName,
            profilePhotoUrl = finalPhotoUrl
        )
    }

    private data class AuthorDisplayInfo(
        val username: String,
        val displayName: String,
        val profilePhotoUrl: String?
    )
    // Helper functions for workout metadata calculation
    private fun calculateWorkoutDuration(workout: WorkoutEntity?): Int? {
        val startTime = workout?.startTime ?: return null
        val endTime = workout.endTime ?: return null
        val duration = java.time.Duration.between(startTime, endTime)
        if (duration.isNegative || duration.isZero) return 0
        return ((duration.seconds + 59L) / 60L).toInt()
    }

    private fun calculateTotalVolume(workout: WorkoutEntity?): Double? {
        return try {
            val workoutEntity = workout ?: return null

            // Parse exercises from JSON and calculate total volume
            if (workoutEntity.exercisesJson.isBlank()) return null

            // Log workout JSON length and bounded prefix before parsing
            Timber.d("[FEED-PARSE] Workout JSON length=${workoutEntity.exercisesJson.length}, prefix='${workoutEntity.exercisesJson.take(240)}'")

            // Handle completedAt/createdAt as either String or {} object
            val gson = com.google.gson.GsonBuilder()
                .registerTypeAdapter(String::class.java, SafeDateAdapter())
                .registerTypeAdapter(java.time.Instant::class.java, SafeInstantAdapter())
                .create()
            val exercises = try {
                parseExercisesFromJson(workoutEntity.exercisesJson, gson)
            } catch (e: Exception) {
                Timber.e("[FEED-PARSE] parsing workout JSON for workout ${workoutEntity.id}: ${e.message}", e)
                Timber.e("[FEED-PARSE] Workout JSON parse failed. length=${workoutEntity.exercisesJson.length}, prefix='${workoutEntity.exercisesJson.take(240)}'")
                emptyList() // Return empty instead of crashing
            }

            // Debug logging to understand exercise structure
            if (exercises.isNotEmpty()) {
                Timber.d("[VOLUME] Found ${exercises.size} exercises. Sample JSON: ${workoutEntity.exercisesJson.take(500)}")
                exercises.take(2).forEach { exercise ->
                    val effectiveSets = exercise.effectiveSets
                    Timber.d("[VOLUME] Exercise name='${exercise.name}', libraryName='${exercise.libraryExercise?.name}', effectiveName='${exercise.effectiveName}', sets=${exercise.sets?.size ?: 0}, effectiveSets=${effectiveSets.size}")
                    effectiveSets.take(2).forEach { set ->
                        Timber.d("[VOLUME] Set - actualWeight=${set.actualWeight}, weight=${set.weight}, weightKg=${set.weightKg}, reps=${set.actualReps ?: set.reps}, repsCount=${set.repsCount}, completed=${set.completed}, completedAt=${set.completedAt}, completedAtEpochMilli=${set.completedAtEpochMilli}, isEffectivelyCompleted=${set.isEffectivelyCompleted}")
                    }
                }
            } else {
                Timber.w("[VOLUME] NO EXERCISES found after parsing JSON! This will result in 0 volume.")
            }

            // Sum volume (weight * reps) for all completed sets using centralized calculator
            val totalVolumeKg = exercises.sumOf { exercise ->
                val exerciseVolume = exercise.effectiveSets.sumOf { set ->
                    val weight = set.effectiveWeight
                    val reps = set.effectiveReps
                    val isCompleted = set.isEffectivelyCompleted

                    // Enhanced debug logging to understand why sets aren't being counted
                    Timber.d("[VOLUME] Set analysis - weight=$weight, reps=$reps, isCompleted=$isCompleted")
                    Timber.d("[VOLUME] Raw set data - actualWeight=${set.actualWeight}, targetWeight=${set.targetWeight}, weight=${set.weight}, weightKg=${set.weightKg}, weightLbs=${set.weightLbs}")
                    Timber.d("[VOLUME] Completion data - completed=${set.completed}, completedAt=${set.completedAt}, completedAtEpochMilli=${set.completedAtEpochMilli}")

                    val setVolume = com.example.liftrix.domain.util.VolumeCalculator.calculateSetVolume(
                        effectiveWeight = weight,
                        effectiveReps = reps,
                        isCompleted = isCompleted
                    )

                    if (setVolume > 0.0) {
                        Timber.d("[VOLUME] Adding ${setVolume}kg volume (${weight}kg x ${reps} reps)")
                    } else {
                        Timber.d("[VOLUME] Skipping set - completed=$isCompleted, weight=$weight, reps=$reps")
                    }

                    setVolume
                }
                Timber.d("[VOLUME] Exercise '${exercise.effectiveName}' total volume: ${exerciseVolume}kg")
                exerciseVolume
            }

            Timber.d("[VOLUME] Final calculated volume: ${totalVolumeKg}kg for workout ${workoutEntity.id}")
            // Return the calculated volume even if it's 0.0 - let the UI handle display decisions
            totalVolumeKg
        } catch (e: Exception) {
            Timber.w(e, "Failed to calculate total volume for workout")
            null
        }
    }

    /**
     * Helper data classes for JSON parsing
     */
    private data class WorkoutExerciseJson(
        @SerializedName("name")
        val name: String? = null,
        @SerializedName("libraryExerciseName")
        val libraryExerciseName: String? = null,
        @SerializedName("sets")
        val sets: List<WorkoutSetJson>? = emptyList(),
        @SerializedName("libraryExercise")
        val libraryExercise: LibraryExerciseJson? = null
    ) {
        // Helper to get effective name from current, canonical, and legacy formats.
        val effectiveName: String
            get() = libraryExerciseName ?: name ?: libraryExercise?.libraryExerciseName
                ?: libraryExercise?.name ?: "Unknown Exercise"

        // Helper to get effective sets with fallback to libraryExercise sets
        val effectiveSets: List<WorkoutSetJson> get() =
            sets?.takeIf { it.isNotEmpty() } ?: libraryExercise?.sets ?: emptyList()
    }

    private data class LibraryExerciseJson(
        @SerializedName("name")
        val name: String? = null,
        @SerializedName("libraryExerciseName")
        val libraryExerciseName: String? = null,
        @SerializedName("id")
        val id: String? = null,
        @SerializedName("sets")
        val sets: List<WorkoutSetJson>? = null
    )

    private data class WorkoutSetJson(
        // Support both direct fields and nested object structures from WorkoutMapper
        @SerializedName("actualWeight")
        val actualWeight: com.google.gson.JsonElement? = null,
        @SerializedName("targetWeight")
        val targetWeight: com.google.gson.JsonElement? = null,
        @SerializedName("weight")
        val weight: com.google.gson.JsonElement? = null, // Supports canonical number and legacy Weight object structures
        @SerializedName("weightKg")
        val weightKg: Double? = null,       // Legacy field
        @SerializedName("weightLbs")
        val weightLbs: Double? = null,      // Legacy field

        @SerializedName("actualReps")
        val actualReps: Int? = null,
        @SerializedName("targetReps")
        val targetReps: Int? = null,
        @SerializedName("reps")
        val reps: com.google.gson.JsonElement? = null, // Supports canonical number and legacy Reps object structures
        @SerializedName("repsValue")
        val repsValue: Int? = null,         // Legacy field
        @SerializedName("repsCount")
        val repsCount: Int? = null,         // From KotlinxWorkoutSerializationService

        @SerializedName("completed")
        val completed: Boolean = false,
        @SerializedName("completedAt")
        val completedAt: String? = null,           // ISO timestamp
        @SerializedName("completedAtEpochMilli")
        val completedAtEpochMilli: Long? = null    // From KotlinxWorkoutSerializationService
    ) {
        // Helper to get the effective weight with fallback to nested/legacy fields
        val effectiveWeight: Double? get() =
            actualWeight.asKilograms() ?: targetWeight.asKilograms() ?:
            weightKg ?: weight.asKilograms() ?:
            // Convert lbs to kg if that's all we have
            weightLbs?.let { it / 2.20462 }

        // Helper to get the effective reps with fallback to nested/legacy fields
        val effectiveReps: Int? get() =
            actualReps ?: targetReps ?:
            reps.asRepsCount() ?: repsValue ?: repsCount

        // Helper to get the effective completion status
        val isEffectivelyCompleted: Boolean get() =
            completed || completedAt != null || completedAtEpochMilli != null

        private fun com.google.gson.JsonElement?.asKilograms(): Double? {
            if (this == null || isJsonNull) return null
            return when {
                isJsonPrimitive && asJsonPrimitive.isNumber -> asDouble
                isJsonObject -> {
                    val obj = asJsonObject
                    obj.get("kilograms")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
                        ?: obj.get("value")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
                }
                else -> null
            }
        }

        private fun com.google.gson.JsonElement?.asRepsCount(): Int? {
            if (this == null || isJsonNull) return null
            return when {
                isJsonPrimitive && asJsonPrimitive.isNumber -> asInt
                isJsonObject -> {
                    val obj = asJsonObject
                    obj.get("count")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                        ?: obj.get("value")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                }
                else -> null
            }
        }
    }

    // Support for nested Weight object structure from WorkoutMapper
    private data class WeightJson(
        @SerializedName("kilograms")
        val kilograms: Double? = null,
        @SerializedName("pounds")
        val pounds: Double? = null
    )

    // Support for nested Reps object structure from WorkoutMapper
    private data class RepsJson(
        @SerializedName("count")
        val count: Int? = null
    )

    /**
     * Parse exercises from workout JSON using shared defensive parser
     */
    private fun parseExercisesFromJson(exercisesJson: String, gson: com.google.gson.Gson): List<WorkoutExerciseJson> {
        return try {
            // Handle wrapper format from WorkoutMapper.toEntity()
            // The JSON structure is: {"exercises": [...], "totalVolume": 123.45}
            val element = com.google.gson.JsonParser.parseString(exercisesJson)

            if (element.isJsonObject) {
                val jsonObject = element.asJsonObject

                // Check if this is the wrapped format with "exercises" key
                if (jsonObject.has("exercises")) {
                    Timber.d("[FEED-PARSE]: Found wrapped format with 'exercises' key")
                    val exercisesElement = jsonObject.get("exercises")

                    if (exercisesElement.isJsonArray) {
                        val listType = com.google.gson.reflect.TypeToken.getParameterized(
                            List::class.java,
                            WorkoutExerciseJson::class.java
                        ).type
                        gson.fromJson<List<WorkoutExerciseJson>>(exercisesElement, listType) ?: emptyList()
                    } else {
                        Timber.w("[FEED-PARSE]: 'exercises' element is not an array")
                        emptyList()
                    }
                } else {
                    // Fallback to original parser for other formats
                    Timber.d("[FEED-PARSE]: Using fallback parser for non-wrapped format")
                    ExerciseJsonParser.parseExercises(exercisesJson, WorkoutExerciseJson::class.java)
                }
            } else {
                // Direct array format
                Timber.d("[FEED-PARSE]: Using direct array parser")
                ExerciseJsonParser.parseExercises(exercisesJson, WorkoutExerciseJson::class.java)
            }
        } catch (e: Exception) {
            Timber.e("[FEED-PARSE]: Failed to parse exercises: ${e.message}", e)
            emptyList()
        }
    }

    private fun calculateExercisesCount(workout: WorkoutEntity?): Int? {
        return try {
            val workoutEntity = workout ?: return null

            // Parse exercises from JSON and count them
            if (workoutEntity.exercisesJson.isBlank()) return null

            // Handle completedAt/createdAt as either String or {} object
            val gson = com.google.gson.GsonBuilder()
                .registerTypeAdapter(String::class.java, SafeDateAdapter())
                .registerTypeAdapter(java.time.Instant::class.java, SafeInstantAdapter())
                .create()
            val exercises = parseExercisesFromJson(workoutEntity.exercisesJson, gson)

            exercises.size.takeIf { it > 0 }
        } catch (e: Exception) {
            Timber.w(e, "Failed to calculate exercises count for workout")
            null
        }
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

    /**
     * Creates a hybrid PagingSource for discovery feed that intelligently chooses between
     * feed_cache and direct queries based on cache availability and population.
     * This provides the performance benefits of caching while maintaining robustness.
     */
    private fun createHybridDiscoveryPagingSource(userId: String, excludeUserIds: List<String>): PagingSource<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity> {
        return object : PagingSource<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, com.example.liftrix.data.local.entity.WorkoutPostEntity> {
                return try {
                    val page = params.key ?: 0
                    val pageSize = params.loadSize
                    val offset = page * pageSize

                    // Check if user has sufficient cached content for discovery feed
                    val hasSufficientCache = feedCacheService.hasSufficientCache(
                        userId = userId,
                        minCacheSize = 20 // Minimum 20 posts for good user experience
                    ).getOrElse { false }

                    val posts = if (hasSufficientCache) {
                        // Use feed_cache for optimal relevance scoring when available
                        Timber.d("[FEED-CACHE]: Using cached feed for user=$userId, page=$page")

                        val cachedPostIds = feedCacheDao.getCachedPostIdsByType(
                            userId = userId,
                            feedType = "DISCOVERY",
                            limit = pageSize,
                            offset = offset
                        )

                        if (cachedPostIds.isEmpty()) {
                            Timber.w("[FEED-CACHE]: Cache insufficient, falling back to direct query for user=$userId")
                            // Fallback to direct query if cache is empty despite hasSufficientCache check
                            getDirectDiscoveryPosts(userId, excludeUserIds, pageSize, offset)
                        } else {
                            // Get actual post entities using the cached post IDs
                            val cachedPosts = cachedPostIds.mapNotNull { postId ->
                                workoutPostDao.getPostById(postId)
                            }
                            if (cachedPosts.isEmpty()) {
                                Timber.w("[FEED-CACHE]: Cache entries did not resolve to posts, falling back to direct query for user=$userId")
                                getDirectDiscoveryPosts(userId, excludeUserIds, pageSize, offset)
                            } else {
                                cachedPosts
                            }
                        }
                    } else {
                        // Fallback to direct query for immediate results
                        Timber.d("[FEED-CACHE]: Using direct query for user=$userId, page=$page (insufficient cache)")
                        getDirectDiscoveryPosts(userId, excludeUserIds, pageSize, offset)
                    }

                    // Background cache population for better future performance
                    if (!hasSufficientCache && page == 0) {
                        // Trigger background cache update for next time (fire and forget)
                        try {
                            feedCacheService.updateFeedCache(userId, forceRefresh = false)
                        } catch (e: Exception) {
                            Timber.w(e, "[FEED-CACHE]: Background cache update failed for user=$userId")
                        }
                    }

                    LoadResult.Page(
                        data = posts,
                        prevKey = if (page > 0) page - 1 else null,
                        nextKey = if (posts.size == pageSize) page + 1 else null
                    )

                } catch (e: Exception) {
                    Timber.e(e, "Error loading hybrid discovery feed for user $userId")
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
     * Helper method to get discovery posts directly from workout_posts table
     * Used as fallback when feed_cache is not available or insufficient
     */
    private suspend fun getDirectDiscoveryPosts(
        userId: String,
        excludeUserIds: List<String>,
        pageSize: Int,
        offset: Int
    ): List<com.example.liftrix.data.local.entity.WorkoutPostEntity> {
        val posts = workoutPostDao.getPublicPostsExcludingUsers(
            excludeUserIds = excludeUserIds,
            limit = pageSize,
            offset = offset
        )
        Timber.i(
            "[PUBLIC-LOG] Discovery direct query user=$userId visibility=PUBLIC excludedAuthors=${excludeUserIds.joinToString()} offset=$offset limit=$pageSize returned=${posts.size}"
        )
        posts.take(5).forEach { post ->
            Timber.d(
                "[PUBLIC-LOG] Discovery post id=${post.id} author=${post.userId} visibility=${post.visibility} hidden=${post.isHidden}"
            )
        }
        return posts
    }

    /**
     * Creates a fallback social profile for users who don't have one yet
     * This ensures posts always have profile information available
     * Syncs data from main user profile when available
     */
    private suspend fun createFallbackSocialProfile(userId: String): com.example.liftrix.data.local.entity.SocialProfileEntity? {
        return try {
            // Try to get main user profile information for better fallback data
            val userProfile = try {
                userProfileDao.getProfileForUserSuspend(userId)
            } catch (e: Exception) {
                Timber.w(e, "Could not load main user profile for $userId")
                null
            }

            // Always try to get the latest profile photo from main profile
            val mainProfilePhotoUrl = try {
                userProfileDao.getProfileImageUrl(userId)
            } catch (e: Exception) {
                Timber.w(e, "Could not load profile image URL for $userId")
                userProfile?.profileImageUrl
            }

            // Also check Firebase Auth as fallback (only works for current user)
            val currentUserId = try {
                authRepository.getCurrentUserId()
            } catch (e: Exception) {
                Timber.w("[PROFILE-PHOTO] FALLBACK_CURRENT_USER_ERROR: Failed to get current user ID during fallback", e)
                null
            }

            val firebaseAuthPhotoUrl = if (userId == currentUserId?.value) {
                try {
                    val authUser = authRepository.getCurrentUser()
                    Timber.d("[PROFILE-PHOTO] FALLBACK_AUTH_CHECK: User $userId is current user, checking Firebase Auth photo")
                    authUser?.photoUrl
                } catch (e: Exception) {
                    Timber.w("[PROFILE-PHOTO] FALLBACK_AUTH_ERROR: Failed to get Firebase Auth user during fallback creation for $userId", e)
                    null
                }
            } else {
                Timber.d("[PROFILE-PHOTO] FALLBACK_AUTH_SKIP: User $userId is not current user ($currentUserId), cannot access Firebase Auth")
                null
            }

            val effectiveFallbackPhotoUrl = mainProfilePhotoUrl ?: firebaseAuthPhotoUrl ?: userProfile?.profileImageUrl

            Timber.d("[PROFILE-PHOTO] FALLBACK_PROFILE_DATA: user=$userId, hasMainProfile=${userProfile != null}, mainPhoto=${mainProfilePhotoUrl ?: "null"}, firebaseAuthPhoto=${firebaseAuthPhotoUrl ?: "null"}, effectivePhoto=${effectiveFallbackPhotoUrl ?: "null"}")

            val now = System.currentTimeMillis()
            val fallbackUsername = generateUniqueUsername(userId)

            // Better fallback display name logic
            val fallbackDisplayName = userProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: fallbackUsername.takeIf { !it.startsWith("user_") }
                ?: "Liftrix User"

            Timber.d("[PROFILE-PHOTO] Creating fallback profile for user $userId with effectivePhotoUrl: '$effectiveFallbackPhotoUrl', displayName: '$fallbackDisplayName'")

            val fallbackProfile = com.example.liftrix.data.local.entity.SocialProfileEntity(
                userId = userId,
                username = fallbackUsername,
                displayName = fallbackDisplayName,
                bio = null,
                profilePhotoUrl = effectiveFallbackPhotoUrl, // Use effective photo (DB -> Firebase Auth -> UserProfile fallback)
                coverPhotoUrl = null,
                workoutCount = 0,
                followerCount = 0,
                followingCount = 0,
                memberSince = userProfile?.createdAt?.let { java.time.ZoneOffset.UTC.let { zone -> it.atZone(zone).toInstant().toEpochMilli() } } ?: now,
                lastActive = now,
                isVerified = false,
                isPrivate = false, // Default to public for discoverability
                hideFromSuggestions = false,
                allowFriendRequests = true,
                instagramHandle = null,
                youtubeChannel = null,
                personalWebsite = null,
                createdAt = userProfile?.createdAt?.let { java.time.ZoneOffset.UTC.let { zone -> it.atZone(zone).toInstant().toEpochMilli() } } ?: now,
                updatedAt = now,
                isSynced = false,
                syncVersion = 0
            )

            // Insert the fallback profile
            socialProfileDao.insertProfile(fallbackProfile)
            Timber.i("[PROFILE-PHOTO] FALLBACK_CREATED: Social profile for user $userId with username: ${fallbackProfile.username}, hasPhoto: ${fallbackProfile.profilePhotoUrl != null}, effectivePhotoUrl: '${fallbackProfile.profilePhotoUrl ?: "null"}'")

            // Trigger sync to Firebase to make it searchable
            try {
                syncScheduler.enqueueSocialProfileSync(userId, forceSync = true)
                Timber.d(" SYNC_QUEUED: Social profile sync enqueued for user $userId")
            } catch (e: Exception) {
                Timber.w(e, "Failed to enqueue social profile sync for user $userId")
            }

            fallbackProfile
        } catch (e: Exception) {
            Timber.e(e, "Failed to create fallback social profile for user $userId")
            null
        }
    }

    /**
     * Generates a unique username based on userId to avoid collisions
     */
    private suspend fun generateUniqueUsername(userId: String): String {
        val baseUsername = "user${userId.take(8)}"
        var username = baseUsername
        var suffix = 1

        // Ensure username is unique by checking existing profiles
        while (!socialProfileDao.isUsernameAvailable(username)) {
            username = "${baseUsername}_${suffix}"
            suffix++
            // Prevent infinite loop
            if (suffix > 100) {
                username = "${baseUsername}_${System.currentTimeMillis().toString().takeLast(6)}"
                break
            }
        }

        return username
    }

    /**
     * Diagnostic method to log comprehensive profile information for debugging
     * Call this when investigating profile loading issues
     */
    suspend fun diagnoseProfileLoadingIssues(userId: String): String {
        val report = StringBuilder()
        report.appendLine(" PROFILE_DIAGNOSIS for user: $userId")
        report.appendLine("=" .repeat(50))

        try {
            // Check social profile
            val socialProfile = socialProfileDao.getSocialProfileByUserId(userId)
            if (socialProfile != null) {
                report.appendLine(" SOCIAL_PROFILE: Found")
                report.appendLine("   - username: ${socialProfile.username}")
                report.appendLine("   - displayName: ${socialProfile.displayName}")
                report.appendLine("   - profilePhotoUrl: ${socialProfile.profilePhotoUrl}")
                report.appendLine("   - hasPhoto: ${!socialProfile.profilePhotoUrl.isNullOrBlank()}")
                report.appendLine("   - createdAt: ${socialProfile.createdAt}")
                report.appendLine("   - isPrivate: ${socialProfile.isPrivate}")
                report.appendLine("   - isSynced: ${socialProfile.isSynced}")
            } else {
                report.appendLine(" SOCIAL_PROFILE: NOT FOUND")
            }

            // Check main user profile
            val userProfile = userProfileDao.getProfileForUserSuspend(userId)
            if (userProfile != null) {
                report.appendLine(" USER_PROFILE: Found")
                report.appendLine("   - displayName: ${userProfile.displayName}")
                report.appendLine("   - displayName: ${userProfile.displayName}")
                report.appendLine("   - profileImageUrl: ${userProfile.profileImageUrl}")
                report.appendLine("   - hasPhoto: ${!userProfile.profileImageUrl.isNullOrBlank()}")
            } else {
                report.appendLine(" USER_PROFILE: NOT FOUND")
            }

            // Check posts count
            val postCount = workoutPostDao.getUserPostCount(userId)
            report.appendLine(" POSTS_COUNT: $postCount")

            // Check total social profiles count
            val totalSocialProfiles = socialProfileDao.getTotalProfileCount()
            report.appendLine(" TOTAL_SOCIAL_PROFILES: $totalSocialProfiles")

        } catch (e: Exception) {
            report.appendLine(" DIAGNOSIS_ERROR: ${e.message}")
            Timber.e(e, "Failed to diagnose profile issues for user $userId")
        }

        report.appendLine("=" .repeat(50))
        val reportString = report.toString()
        Timber.w(reportString)
        return reportString
    }
}

/**
 * Safe Gson adapter that handles date fields that can be either String or {} object
 */
private class SafeDateAdapter : com.google.gson.JsonDeserializer<String?> {
    override fun deserialize(
        json: com.google.gson.JsonElement,
        typeOfT: java.lang.reflect.Type,
        context: com.google.gson.JsonDeserializationContext
    ): String? {
        return when {
            json.isJsonNull -> null
            json.isJsonPrimitive && json.asJsonPrimitive.isString -> json.asString
            json.isJsonObject && json.asJsonObject.entrySet().isEmpty() -> null // treat {} as null
            json.isJsonObject -> null // treat any object as null for now
            else -> json.toString()
        }
    }
}

/**
 * Safe Gson adapter that handles Instant fields that can be either String or {} object
 */
private class SafeInstantAdapter : com.google.gson.JsonDeserializer<java.time.Instant?> {
    override fun deserialize(
        json: com.google.gson.JsonElement,
        typeOfT: java.lang.reflect.Type,
        context: com.google.gson.JsonDeserializationContext
    ): java.time.Instant? {
        return when {
            json.isJsonNull -> null
            json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                try {
                    java.time.Instant.parse(json.asString)
                } catch (e: Exception) {
                    null
                }
            }
            json.isJsonObject && json.asJsonObject.entrySet().isEmpty() -> null // treat {} as null
            json.isJsonObject -> null // treat any object as null for now
            else -> null
        }
    }
}
