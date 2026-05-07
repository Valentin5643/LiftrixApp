package com.example.liftrix.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.FeedCacheDao
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.domain.service.FeedCacheService
import com.example.liftrix.domain.model.social.FeedType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * RemoteMediator for social feed data with intelligent caching.
 * Manages network/local data coordination for paginated feed.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@OptIn(ExperimentalPagingApi::class)
class FeedRemoteMediator @Inject constructor(
    private val workoutPostDao: WorkoutPostDao,
    private val feedCacheDao: FeedCacheDao,
    private val feedCacheService: FeedCacheService,
    private val userId: String,
    private val feedType: FeedType,
    private val targetUserId: String? = null
) : RemoteMediator<Int, WorkoutPostEntity>() {
    
    override suspend fun initialize(): InitializeAction {
        // Check if we have sufficient cached data
        val cacheResult = feedCacheService.hasSufficientCache(userId)
        val hasSufficientCache = cacheResult.fold(
            onSuccess = { it },
            onFailure = { false }
        )
        
        return if (hasSufficientCache) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }
    
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, WorkoutPostEntity>
    ): MediatorResult = withContext(Dispatchers.IO) {
        try {
            val loadKey = when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> return@withContext MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                    if (lastItem == null) {
                        return@withContext MediatorResult.Success(endOfPaginationReached = true)
                    }
                    // Calculate offset based on current data size
                    state.pages.sumOf { it.data.size }
                }
            }
            
            // Try to load from cache first
            val cachedPostIdsResult = feedCacheService.getCachedFeedPostIds(
                userId = userId,
                limit = state.config.pageSize,
                offset = loadKey
            )
            
            val cachedPostIds = cachedPostIdsResult.fold(
                onSuccess = { it },
                onFailure = { emptyList<String>() }
            )
            
            if (cachedPostIds.isNotEmpty()) {
                // We have cached data, check if it's sufficient
                val endOfPagination = cachedPostIds.size < state.config.pageSize
                return@withContext MediatorResult.Success(endOfPaginationReached = endOfPagination)
            }
            
            // No cache or insufficient cache - but DON'T clear existing data
            when (loadType) {
                LoadType.REFRESH -> {
                    // 🔥 FIX: COMPLETELY DISABLE cache operations on refresh
                    // The feed should persist through ANY refresh that isn't explicitly
                    // triggered by new content being added
                    
                    // DO NOTHING - keep existing posts visible
                    // The database already has the posts, we don't need to clear them
                }
                LoadType.APPEND -> {
                    // For append, also do nothing - just load more from database
                    // The paging source will handle getting more data
                }
                else -> { /* PREPEND already handled above */ }
            }
            
            // 🔥 FIX: Since we're not updating cache, just return success
            // The PagingSource will handle loading data from the database
            // We don't need to check cache or determine pagination here
            
            // Always return false for endOfPaginationReached to let the
            // PagingSource determine when there's no more data
            MediatorResult.Success(endOfPaginationReached = false)
            
        } catch (exception: Exception) {
            MediatorResult.Error(exception)
        }
    }
    
    /**
     * Determines if this mediator should be used for the given feed type
     */
    fun isApplicableForFeedType(feedType: FeedType): Boolean {
        return when (feedType) {
            FeedType.HOME -> true      // Uses cache for personalized home feed
            FeedType.DISCOVERY -> true // Uses cache for discovery algorithm
            FeedType.USER -> false     // Direct database query for user profiles
        }
    }
}