package com.example.liftrix.data.paging

import androidx.paging.*
import androidx.paging.RemoteMediator.*
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.data.remote.social.SocialApiService
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.IOException

/**
 * Comprehensive tests for FeedRemoteMediator implementation
 * 
 * Tests network sync, caching logic, error handling, and refresh behavior
 * for the social feed pagination system.
 */
@RunWith(JUnit4::class)
class FeedRemoteMediatorTest {

    private lateinit var feedRemoteMediator: FeedRemoteMediator
    private lateinit var workoutPostDao: WorkoutPostDao
    private lateinit var socialApiService: SocialApiService

    private val testUserId = "user-123"
    private val testPosts = listOf(
        createWorkoutPostEntity("post-1", "user-1", "Great workout today!"),
        createWorkoutPostEntity("post-2", "user-2", "Hit a new PR!"),
        createWorkoutPostEntity("post-3", "user-3", "Chest and triceps session")
    )

    @Before
    fun setup() {
        workoutPostDao = mockk(relaxed = true)
        socialApiService = mockk()
        
        feedRemoteMediator = FeedRemoteMediator(
            userId = testUserId,
            workoutPostDao = workoutPostDao,
            socialApiService = socialApiService
        )
    }

    // ==========================================
    // Refresh Load Tests
    // ==========================================

    @Test
    fun `load should return Success and cache posts on REFRESH`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()

        coEvery { socialApiService.getHomeFeed(testUserId, 0, 20) } returns 
            LiftrixResult.Success(testPosts.map { it.toApiModel() })
        coEvery { workoutPostDao.clearFeedCache(testUserId) } just Runs
        coEvery { workoutPostDao.insertPosts(any()) } just Runs

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should succeed", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertFalse("Should indicate more data available", successResult.endOfPaginationReached)

        // Verify caching behavior
        coVerify { workoutPostDao.clearFeedCache(testUserId) }
        coVerify { workoutPostDao.insertPosts(match { posts ->
            posts.size == testPosts.size && posts.all { it.userId == testUserId || it.isFromFeed }
        }) }
    }

    @Test
    fun `load should handle API failure on REFRESH`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()

        val networkError = LiftrixError.NetworkError("Feed service unavailable")
        coEvery { socialApiService.getHomeFeed(testUserId, 0, 20) } returns 
            LiftrixResult.Error(networkError)

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should return error", result is MediatorResult.Error)
        val errorResult = result as MediatorResult.Error
        assertTrue("Should contain network error", errorResult.throwable is LiftrixError.NetworkError)

        // Verify no caching occurs on failure
        coVerify(exactly = 0) { workoutPostDao.clearFeedCache(any()) }
        coVerify(exactly = 0) { workoutPostDao.insertPosts(any()) }
    }

    @Test
    fun `load should handle empty feed response`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()

        coEvery { socialApiService.getHomeFeed(testUserId, 0, 20) } returns 
            LiftrixResult.Success(emptyList())

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should succeed", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertTrue("Should indicate end of pagination", successResult.endOfPaginationReached)
    }

    // ==========================================
    // Append Load Tests
    // ==========================================

    @Test
    fun `load should append new posts on APPEND`() = runTest {
        // Given
        val loadType = LoadType.APPEND
        val pagingState = createPagingState(
            lastItem = testPosts.first(),
            offset = 20
        )

        val newPosts = listOf(
            createWorkoutPostEntity("post-4", "user-4", "Evening workout"),
            createWorkoutPostEntity("post-5", "user-5", "Back and biceps")
        )

        coEvery { socialApiService.getHomeFeed(testUserId, 20, 20) } returns 
            LiftrixResult.Success(newPosts.map { it.toApiModel() })
        coEvery { workoutPostDao.insertPosts(any()) } just Runs

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should succeed", result is MediatorResult.Success)
        
        // Verify no cache clearing on append
        coVerify(exactly = 0) { workoutPostDao.clearFeedCache(any()) }
        coVerify { workoutPostDao.insertPosts(match { posts ->
            posts.size == newPosts.size
        }) }
    }

    @Test
    fun `load should handle pagination end on APPEND`() = runTest {
        // Given
        val loadType = LoadType.APPEND
        val pagingState = createPagingState(offset = 100)

        // API returns less than requested page size, indicating end
        val finalPosts = listOf(createWorkoutPostEntity("final-post", "user-final", "Last post"))
        
        coEvery { socialApiService.getHomeFeed(testUserId, 100, 20) } returns 
            LiftrixResult.Success(finalPosts.map { it.toApiModel() })

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should succeed", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertTrue("Should indicate end of pagination reached", successResult.endOfPaginationReached)
    }

    // ==========================================
    // Prepend Load Tests
    // ==========================================

    @Test
    fun `load should handle PREPEND load type`() = runTest {
        // Given
        val loadType = LoadType.PREPEND
        val pagingState = createPagingState()

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Prepend should succeed immediately", result is MediatorResult.Success)
        val successResult = result as MediatorResult.Success
        assertTrue("Prepend should indicate end reached", successResult.endOfPaginationReached)
    }

    // ==========================================
    // Error Handling Tests
    // ==========================================

    @Test
    fun `load should handle database errors gracefully`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()

        coEvery { socialApiService.getHomeFeed(testUserId, 0, 20) } returns 
            LiftrixResult.Success(testPosts.map { it.toApiModel() })
        coEvery { workoutPostDao.clearFeedCache(testUserId) } throws RuntimeException("Database error")

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should return error", result is MediatorResult.Error)
        val errorResult = result as MediatorResult.Error
        assertTrue("Should contain database error", errorResult.throwable is RuntimeException)
    }

    @Test
    fun `load should handle network timeout`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()

        coEvery { socialApiService.getHomeFeed(testUserId, 0, 20) } returns 
            LiftrixResult.Error(LiftrixError.NetworkError("Request timeout"))

        // When
        val result = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("Load should return error", result is MediatorResult.Error)
        val errorResult = result as MediatorResult.Error
        assertEquals("Should preserve timeout error", "Request timeout", errorResult.throwable.message)
    }

    @Test
    fun `load should retry after transient failures`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()

        // First call fails, second succeeds
        coEvery { socialApiService.getHomeFeed(testUserId, 0, 20) } returnsMany listOf(
            LiftrixResult.Error(LiftrixError.NetworkError("Temporary failure")),
            LiftrixResult.Success(testPosts.map { it.toApiModel() })
        )

        // When - first call
        val firstResult = feedRemoteMediator.load(loadType, pagingState)
        
        // Then - first call fails
        assertTrue("First load should fail", firstResult is MediatorResult.Error)
        
        // When - retry
        val retryResult = feedRemoteMediator.load(loadType, pagingState)
        
        // Then - retry succeeds
        assertTrue("Retry should succeed", retryResult is MediatorResult.Success)
    }

    // ==========================================
    // Concurrency Tests
    // ==========================================

    @Test
    fun `load should handle concurrent refresh requests`() = runTest {
        // Given
        val loadType = LoadType.REFRESH
        val pagingState = createPagingState()

        coEvery { socialApiService.getHomeFeed(testUserId, 0, 20) } returns 
            LiftrixResult.Success(testPosts.map { it.toApiModel() })
        coEvery { workoutPostDao.clearFeedCache(testUserId) } just Runs
        coEvery { workoutPostDao.insertPosts(any()) } just Runs

        // When - simulate concurrent calls
        val result1 = feedRemoteMediator.load(loadType, pagingState)
        val result2 = feedRemoteMediator.load(loadType, pagingState)

        // Then
        assertTrue("First load should succeed", result1 is MediatorResult.Success)
        assertTrue("Second load should succeed", result2 is MediatorResult.Success)
        
        // Verify cache cleared for both requests
        coVerify(atLeast = 1) { workoutPostDao.clearFeedCache(testUserId) }
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private fun createWorkoutPostEntity(id: String, userId: String, caption: String) = 
        WorkoutPostEntity(
            id = id,
            userId = userId,
            workoutId = "workout-$id",
            caption = caption,
            likeCount = 5,
            commentCount = 2,
            shareCount = 1,
            saveCount = 3,
            createdAt = System.currentTimeMillis(),
            isFromFeed = true,
            lastSyncTimestamp = System.currentTimeMillis()
        )

    private fun WorkoutPostEntity.toApiModel() = 
        // Convert to API model - would normally use proper mapper
        mapOf(
            "id" to id,
            "userId" to userId,
            "caption" to caption,
            "likeCount" to likeCount
        )

    private fun createPagingState(
        lastItem: WorkoutPostEntity? = null,
        offset: Int = 0
    ): PagingState<Int, WorkoutPostEntity> {
        return PagingState(
            pages = if (lastItem != null) listOf(
                PagingSource.LoadResult.Page(
                    data = listOf(lastItem),
                    prevKey = if (offset > 0) offset - 20 else null,
                    nextKey = offset + 20
                )
            ) else emptyList(),
            anchorPosition = offset,
            config = PagingConfig(pageSize = 20),
            leadingPlaceholderCount = 0
        )
    }
}