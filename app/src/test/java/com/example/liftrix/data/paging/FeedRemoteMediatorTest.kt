package com.example.liftrix.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.RemoteMediator.InitializeAction
import androidx.paging.RemoteMediator.MediatorResult
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.FeedCacheDao
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.domain.service.FeedCacheService
import com.example.liftrix.domain.model.social.FeedType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Comprehensive tests for FeedRemoteMediator implementation
 * 
 * Tests network sync, caching logic, error handling, and refresh behavior
 * for the social feed pagination system.
 */
@OptIn(ExperimentalPagingApi::class)
@RunWith(JUnit4::class)
class FeedRemoteMediatorTest {

    private lateinit var feedRemoteMediator: FeedRemoteMediator
    private lateinit var workoutPostDao: WorkoutPostDao
    private lateinit var feedCacheDao: FeedCacheDao
    private lateinit var feedCacheService: FeedCacheService

    private val testUserId = "user-123"
    private val testPosts = listOf(
        createWorkoutPostEntity("post-1", "user-1", "Great workout today!"),
        createWorkoutPostEntity("post-2", "user-2", "Hit a new PR!"),
        createWorkoutPostEntity("post-3", "user-3", "Chest and triceps session")
    )

    @Before
    fun setup() {
        workoutPostDao = mockk(relaxed = true)
        feedCacheDao = mockk(relaxed = true)
        feedCacheService = mockk()
        
        feedRemoteMediator = FeedRemoteMediator(
            workoutPostDao = workoutPostDao,
            feedCacheDao = feedCacheDao,
            feedCacheService = feedCacheService,
            userId = testUserId,
            feedType = FeedType.HOME,
            targetUserId = null
        )
    }

    // ==========================================
    // Initialize Tests
    // ==========================================

    @Test
    fun `initialize should skip refresh when sufficient cache exists`() = runTest {
        // Given
        coEvery { feedCacheService.hasSufficientCache(testUserId) } returns 
            LiftrixResult.success(true)

        // When
        val result = feedRemoteMediator.initialize()

        // Then
        assertEquals("Should skip initial refresh", 
            InitializeAction.SKIP_INITIAL_REFRESH, result)
    }

    @Test
    fun `initialize should launch refresh when cache insufficient`() = runTest {
        // Given
        coEvery { feedCacheService.hasSufficientCache(testUserId) } returns 
            LiftrixResult.success(false)

        // When
        val result = feedRemoteMediator.initialize()

        // Then
        assertEquals("Should launch initial refresh", 
            InitializeAction.LAUNCH_INITIAL_REFRESH, result)
    }

    @Test
    fun `initialize should launch refresh when cache service fails`() = runTest {
        // Given
        val cacheError = LiftrixError.BusinessLogicError(
            code = "CACHE_CHECK_FAILED",
            errorMessage = "Failed to check cache status",
            analyticsContext = mapOf("operation" to "CACHE_CHECK")
        )
        
        coEvery { feedCacheService.hasSufficientCache(testUserId) } returns 
            LiftrixResult.failure(cacheError)

        // When
        val result = feedRemoteMediator.initialize()

        // Then
        assertEquals("Should launch refresh on cache service error", 
            InitializeAction.LAUNCH_INITIAL_REFRESH, result)
    }

    // ==========================================
    // Load Tests
    // ==========================================

    @Test
    fun `load should return Success with cached data on REFRESH`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()
        val cachedPostIds = listOf("post-1", "post-2", "post-3")

        coEvery { feedCacheService.getCachedFeedPostIds(testUserId, 20, 0) } returns 
            LiftrixResult.success(cachedPostIds)
        coEvery { feedCacheService.invalidateUserCache(testUserId) } returns LiftrixResult.success(Unit)
        coEvery { feedCacheService.updateFeedCache(testUserId, true) } returns LiftrixResult.success(Unit)

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should succeed", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertFalse("Should indicate more data available", successResult.endOfPaginationReached)
    }

    @Test
    fun `load should handle empty cache on REFRESH`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()

        coEvery { feedCacheService.getCachedFeedPostIds(testUserId, 20, 0) } returns 
            LiftrixResult.success(emptyList<String>())
        coEvery { feedCacheService.invalidateUserCache(testUserId) } returns LiftrixResult.success(Unit)
        coEvery { feedCacheService.updateFeedCache(testUserId, true) } returns LiftrixResult.success(Unit)

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should succeed", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertTrue("Should indicate end of pagination", successResult.endOfPaginationReached)
    }

    @Test
    fun `load should return Success on PREPEND`() = runTest {
        // Given
        val loadType = LoadType.PREPEND
        val pagingState = createPagingState()

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Prepend should succeed", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertTrue("Should indicate end of pagination for prepend", 
            successResult.endOfPaginationReached)
    }

    @Test
    fun `load should handle APPEND with existing data`() = runTest {
        // Given
        val loadType = LoadType.APPEND
        val pagingState = createPagingStateWithData()
        val nextPageIds = listOf("post-4", "post-5")

        coEvery { feedCacheService.getCachedFeedPostIds(testUserId, 20, 3) } returns 
            LiftrixResult.success(nextPageIds)
        coEvery { feedCacheService.updateFeedCache(testUserId, false) } returns LiftrixResult.success(Unit)

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Append should succeed", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertFalse("Should indicate more data available", successResult.endOfPaginationReached)
    }

    @Test
    fun `load should handle cache service errors`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()
        val cacheError = LiftrixError.BusinessLogicError(
            code = "CACHE_ERROR",
            errorMessage = "Cache service failed",
            analyticsContext = mapOf("operation" to "FEED_LOAD")
        )

        coEvery { feedCacheService.getCachedFeedPostIds(testUserId, any(), any()) } returns 
            LiftrixResult.failure(cacheError)
        coEvery { feedCacheService.invalidateUserCache(testUserId) } returns LiftrixResult.success(Unit)
        coEvery { feedCacheService.updateFeedCache(testUserId, true) } returns LiftrixResult.success(Unit)

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should succeed with empty data on error", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertTrue("Should indicate end of pagination on error", successResult.endOfPaginationReached)
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private fun createPagingState(): PagingState<Int, WorkoutPostEntity> {
        return PagingState(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = 20),
            leadingPlaceholderCount = 0
        )
    }

    private fun createPagingStateWithData(): PagingState<Int, WorkoutPostEntity> {
        val pages = listOf(
            PagingSource.LoadResult.Page(
                data = testPosts,
                prevKey = null,
                nextKey = 3
            )
        )
        return PagingState(
            pages = pages,
            anchorPosition = null,
            config = PagingConfig(pageSize = 20),
            leadingPlaceholderCount = 0
        )
    }

    @Test
    fun `load should handle cache invalidation on REFRESH`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()
        
        // First call returns empty cache, trigger network update
        coEvery { feedCacheService.getCachedFeedPostIds(testUserId, 20, 0) } returns 
            LiftrixResult.success(emptyList<String>()) andThen LiftrixResult.success(listOf("post-1", "post-2"))
        
        coEvery { feedCacheService.invalidateUserCache(testUserId) } returns LiftrixResult.success(Unit)
        coEvery { feedCacheService.updateFeedCache(testUserId, true) } returns LiftrixResult.success(Unit)

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should succeed", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertFalse("Should indicate more data available", successResult.endOfPaginationReached)
        
        // Verify cache operations were called
        coVerify { feedCacheService.invalidateUserCache(testUserId) }
        coVerify { feedCacheService.updateFeedCache(testUserId, true) }
    }

    @Test
    fun `load should handle service error gracefully`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()
        
        val serviceError = LiftrixError.BusinessLogicError(
            code = "SERVICE_ERROR",
            errorMessage = "Feed service unavailable",
            analyticsContext = mapOf("operation" to "FEED_CACHE_SERVICE")
        )
        
        coEvery { feedCacheService.getCachedFeedPostIds(testUserId, any(), any()) } returns 
            LiftrixResult.failure(serviceError)

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should succeed with empty data", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertTrue("Should indicate end of pagination on error", successResult.endOfPaginationReached)
    }

    @Test
    fun `load should handle APPEND with no more cached data`() = runTest {
        // Given
        val loadType = LoadType.APPEND
        val pagingState = createPagingStateWithData()

        // First call returns empty cache, second call after update also returns empty
        coEvery { feedCacheService.getCachedFeedPostIds(testUserId, 20, 3) } returns 
            LiftrixResult.success(emptyList<String>())
        coEvery { feedCacheService.updateFeedCache(testUserId, false) } returns LiftrixResult.success(Unit)

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Append should succeed", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertTrue("Should indicate end of pagination", successResult.endOfPaginationReached)
    }

    @Test
    fun `load should handle runtime exceptions gracefully`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()
        
        coEvery { feedCacheService.getCachedFeedPostIds(testUserId, any(), any()) } throws 
            RuntimeException("Unexpected error")

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should return error result", result is MediatorResult.Error)
        val errorResult = result as MediatorResult.Error
        assertTrue("Should preserve original exception type", errorResult.throwable is RuntimeException)
    }

    @Test
    fun `load should verify cache operations on APPEND`() = runTest {
        // Given
        val loadType = LoadType.APPEND
        val pagingState = createPagingStateWithData()
        val nextPageIds = listOf("post-4")

        coEvery { feedCacheService.getCachedFeedPostIds(testUserId, 20, 3) } returns 
            LiftrixResult.success(emptyList<String>()) andThen LiftrixResult.success(nextPageIds)
        coEvery { feedCacheService.updateFeedCache(testUserId, false) } returns LiftrixResult.success(Unit)

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Append should succeed", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertFalse("Should indicate more data available", successResult.endOfPaginationReached)
        
        // Verify cache update was called for APPEND
        coVerify { feedCacheService.updateFeedCache(testUserId, false) }
        // Verify invalidate was NOT called for APPEND
        coVerify(exactly = 0) { feedCacheService.invalidateUserCache(testUserId) }
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private fun createWorkoutPostEntity(
        id: String,
        userId: String,
        caption: String
    ): WorkoutPostEntity {
        return WorkoutPostEntity(
            id = id,
            userId = userId,
            workoutId = "workout-$id",
            caption = caption,
            mediaUrls = null,
            mediaThumbnails = null,
            workoutDuration = 45,
            totalVolume = 5000.0,
            exercisesCount = 5,
            prsCount = 0,
            likeCount = 0,
            commentCount = 0,
            shareCount = 0,
            saveCount = 0,
            visibility = "FOLLOWERS",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isSynced = true,
            syncVersion = 1
        )
    }
}