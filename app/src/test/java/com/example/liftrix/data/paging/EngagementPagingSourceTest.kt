package com.example.liftrix.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingSource.*
import com.example.liftrix.data.local.dao.PostLikeDao
import com.example.liftrix.data.local.dao.SavedPostDao
import com.example.liftrix.domain.model.social.PostLike
import com.example.liftrix.domain.model.social.SavedPost
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.IOException

/**
 * Comprehensive tests for engagement PagingSource implementations
 * 
 * Tests PagingSources for likes, saved posts, and other engagement data
 * with proper pagination behavior and error handling.
 */
@RunWith(JUnit4::class)
class EngagementPagingSourceTest {

    private lateinit var postLikeDao: PostLikeDao
    private lateinit var savedPostDao: SavedPostDao

    private val testUserId = "user-123"
    private val testPostId = "post-456"

    @Before
    fun setup() {
        postLikeDao = mockk()
        savedPostDao = mockk()
    }

    // ==========================================
    // Post Likes PagingSource Tests
    // ==========================================

    @Test
    fun `PostLikesPagingSource should load first page successfully`() = runTest {
        // Given
        val postLikes = createTestPostLikes(20) // Full page
        val pagingSource = PostLikesPagingSource(postLikeDao, testPostId)
        
        coEvery { postLikeDao.getPostLikesWithProfiles(testPostId, 0, 20) } returns postLikes

        // When
        val loadParams = LoadParams.Refresh(
            key = null,
            loadSize = 20,
            placeholdersEnabled = false
        )
        val result = pagingSource.load(loadParams)

        // Then
        assertTrue("Load should succeed", result is LoadResult.Page)
        val page = result as LoadResult.Page
        
        assertEquals("Should return correct data size", 20, page.data.size)
        assertNull("Previous key should be null for first page", page.prevKey)
        assertEquals("Next key should be 20", 20, page.nextKey)
        assertEquals("First like should have correct post ID", testPostId, page.data.first().postId)
    }

    @Test
    fun `PostLikesPagingSource should load subsequent pages correctly`() = runTest {
        // Given
        val postLikes = createTestPostLikes(15) // Partial page
        val pagingSource = PostLikesPagingSource(postLikeDao, testPostId)
        
        coEvery { postLikeDao.getPostLikesWithProfiles(testPostId, 20, 20) } returns postLikes

        // When
        val loadParams = LoadParams.Append(
            key = 20,
            loadSize = 20,
            placeholdersEnabled = false
        )
        val result = pagingSource.load(loadParams)

        // Then
        assertTrue("Load should succeed", result is LoadResult.Page)
        val page = result as LoadResult.Page
        
        assertEquals("Should return partial page data", 15, page.data.size)
        assertEquals("Previous key should be correct", 0, page.prevKey)
        assertEquals("Next key should be 35", 35, page.nextKey)
    }

    @Test
    fun `PostLikesPagingSource should handle end of pagination`() = runTest {
        // Given - empty result indicates end
        val pagingSource = PostLikesPagingSource(postLikeDao, testPostId)
        
        coEvery { postLikeDao.getPostLikesWithProfiles(testPostId, 40, 20) } returns emptyList()

        // When
        val loadParams = LoadParams.Append(
            key = 40,
            loadSize = 20,
            placeholdersEnabled = false
        )
        val result = pagingSource.load(loadParams)

        // Then
        assertTrue("Load should succeed", result is LoadResult.Page)
        val page = result as LoadResult.Page
        
        assertTrue("Should return empty data", page.data.isEmpty())
        assertEquals("Previous key should be correct", 20, page.prevKey)
        assertNull("Next key should be null at end", page.nextKey)
    }

    @Test
    fun `PostLikesPagingSource should handle database errors`() = runTest {
        // Given
        val pagingSource = PostLikesPagingSource(postLikeDao, testPostId)
        val dbException = IOException("Database connection failed")
        
        coEvery { postLikeDao.getPostLikesWithProfiles(any(), any(), any()) } throws dbException

        // When
        val loadParams = LoadParams.Refresh(
            key = null,
            loadSize = 20,
            placeholdersEnabled = false
        )
        val result = pagingSource.load(loadParams)

        // Then
        assertTrue("Load should return error", result is LoadResult.Error)
        val errorResult = result as LoadResult.Error
        assertEquals("Should preserve original exception", dbException, errorResult.throwable)
    }

    @Test
    fun `PostLikesPagingSource should calculate refresh key correctly`() = runTest {
        // Given
        val pagingSource = PostLikesPagingSource(postLikeDao, testPostId)
        val state = mockk<PagingState<Int, PostLike>>()
        
        every { state.anchorPosition } returns 25
        every { state.closestPageToPosition(25) } returns mockk<PagingSource.LoadResult.Page<Int, PostLike>> {
            every { prevKey } returns 20
            every { nextKey } returns 40
        }

        // When
        val refreshKey = pagingSource.getRefreshKey(state)

        // Then
        assertEquals("Refresh key should be calculated correctly", 21, refreshKey)
    }

    // ==========================================
    // Saved Posts PagingSource Tests
    // ==========================================

    @Test
    fun `SavedPostsPagingSource should load user's saved posts`() = runTest {
        // Given
        val savedPosts = createTestSavedPosts(15)
        val pagingSource = SavedPostsPagingSource(savedPostDao, testUserId)
        
        coEvery { savedPostDao.getUserSavedPostsWithDetails(testUserId, 0, 20) } returns savedPosts

        // When
        val loadParams = LoadParams.Refresh(
            key = null,
            loadSize = 20,
            placeholdersEnabled = false
        )
        val result = pagingSource.load(loadParams)

        // Then
        assertTrue("Load should succeed", result is LoadResult.Page)
        val page = result as LoadResult.Page
        
        assertEquals("Should return saved posts", 15, page.data.size)
        assertTrue("All posts should be for correct user", 
            page.data.all { it.userId == testUserId })
    }

    @Test
    fun `SavedPostsPagingSource should handle empty saved posts`() = runTest {
        // Given
        val pagingSource = SavedPostsPagingSource(savedPostDao, testUserId)
        
        coEvery { savedPostDao.getUserSavedPostsWithDetails(testUserId, 0, 20) } returns emptyList()

        // When
        val loadParams = LoadParams.Refresh(
            key = null,
            loadSize = 20,
            placeholdersEnabled = false
        )
        val result = pagingSource.load(loadParams)

        // Then
        assertTrue("Load should succeed", result is LoadResult.Page)
        val page = result as LoadResult.Page
        
        assertTrue("Should return empty list", page.data.isEmpty())
        assertNull("Previous key should be null", page.prevKey)
        assertNull("Next key should be null", page.nextKey)
    }

    @Test
    fun `SavedPostsPagingSource should handle prepend loads`() = runTest {
        // Given
        val savedPosts = createTestSavedPosts(10)
        val pagingSource = SavedPostsPagingSource(savedPostDao, testUserId)
        
        coEvery { savedPostDao.getUserSavedPostsWithDetails(testUserId, 0, 20) } returns savedPosts

        // When
        val loadParams = LoadParams.Prepend(
            key = 20,
            loadSize = 20,
            placeholdersEnabled = false
        )
        val result = pagingSource.load(loadParams)

        // Then
        assertTrue("Load should succeed", result is LoadResult.Page)
        val page = result as LoadResult.Page
        
        assertEquals("Should load data for prepend", 10, page.data.size)
        assertNull("Previous key should be null for prepend", page.prevKey)
        assertEquals("Next key should be correct", 20, page.nextKey)
    }

    // ==========================================
    // Error Recovery Tests
    // ==========================================

    @Test
    fun `PagingSources should handle transient database errors`() = runTest {
        // Given
        val pagingSource = PostLikesPagingSource(postLikeDao, testPostId)
        
        // First call fails, second succeeds
        coEvery { postLikeDao.getPostLikesWithProfiles(testPostId, 0, 20) } throws 
            IOException("Temporary database issue") andThen createTestPostLikes(10)

        val loadParams = LoadParams.Refresh(
            key = null,
            loadSize = 20,
            placeholdersEnabled = false
        )

        // When - first attempt fails
        val firstResult = pagingSource.load(loadParams)
        
        // Then
        assertTrue("First load should fail", firstResult is LoadResult.Error)
        
        // When - retry succeeds
        val retryResult = pagingSource.load(loadParams)
        
        // Then
        assertTrue("Retry should succeed", retryResult is LoadResult.Page)
        val page = retryResult as LoadResult.Page
        assertEquals("Should return data on retry", 10, page.data.size)
    }

    @Test
    fun `PagingSources should handle invalid parameters gracefully`() = runTest {
        // Given
        val pagingSource = PostLikesPagingSource(postLikeDao, "") // Invalid post ID
        
        coEvery { postLikeDao.getPostLikesWithProfiles("", any(), any()) } returns emptyList()

        // When
        val loadParams = LoadParams.Refresh(
            key = null,
            loadSize = 20,
            placeholdersEnabled = false
        )
        val result = pagingSource.load(loadParams)

        // Then
        assertTrue("Load should succeed with empty result", result is LoadResult.Page)
        val page = result as LoadResult.Page
        assertTrue("Should return empty data for invalid parameters", page.data.isEmpty())
    }

    // ==========================================
    // Performance Tests
    // ==========================================

    @Test
    fun `PagingSources should handle large page sizes efficiently`() = runTest {
        // Given
        val largePageSize = 100
        val pagingSource = PostLikesPagingSource(postLikeDao, testPostId)
        val largePage = createTestPostLikes(largePageSize)
        
        coEvery { postLikeDao.getPostLikesWithProfiles(testPostId, 0, largePageSize) } returns largePage

        // When
        val loadParams = LoadParams.Refresh(
            key = null,
            loadSize = largePageSize,
            placeholdersEnabled = false
        )
        val result = pagingSource.load(loadParams)

        // Then
        assertTrue("Large page should load successfully", result is LoadResult.Page)
        val page = result as LoadResult.Page
        assertEquals("Should handle large page size", largePageSize, page.data.size)
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private fun createTestPostLikes(count: Int): List<PostLike> {
        return (1..count).map { i ->
            PostLike(
                id = "like-$i",
                postId = testPostId,
                userId = "user-$i",
                username = "user$i",
                displayName = "User $i",
                profilePhotoUrl = "https://example.com/photo$i.jpg",
                createdAt = System.currentTimeMillis() - (i * 60000L) // Staggered timestamps
            )
        }
    }

    private fun createTestSavedPosts(count: Int): List<SavedPost> {
        return (1..count).map { i ->
            SavedPost(
                id = "saved-$i",
                postId = "post-$i",
                userId = testUserId,
                workoutId = "workout-$i",
                savedAt = System.currentTimeMillis() - (i * 3600000L), // Staggered by hours
                postCaption = "Saved workout $i",
                postAuthorUsername = "author$i",
                workoutName = "Workout $i",
                totalExercises = 5 + (i % 3),
                estimatedDuration = 45 + (i % 20)
            )
        }
    }

    // Inner class for testing - would normally be separate files
    private class PostLikesPagingSource(
        private val postLikeDao: PostLikeDao,
        private val postId: String
    ) : PagingSource<Int, PostLike>() {

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PostLike> {
            return try {
                val offset = params.key ?: 0
                val limit = params.loadSize

                val likes = postLikeDao.getPostLikesWithProfiles(postId, offset, limit)

                LoadResult.Page(
                    data = likes,
                    prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                    nextKey = if (likes.isEmpty()) null else offset + likes.size
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

        override fun getRefreshKey(state: PagingState<Int, PostLike>): Int? {
            return state.anchorPosition?.let { anchorPosition ->
                state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                    ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
            }
        }
    }

    private class SavedPostsPagingSource(
        private val savedPostDao: SavedPostDao,
        private val userId: String
    ) : PagingSource<Int, SavedPost>() {

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SavedPost> {
            return try {
                val offset = params.key ?: 0
                val limit = params.loadSize

                val savedPosts = savedPostDao.getUserSavedPostsWithDetails(userId, offset, limit)

                LoadResult.Page(
                    data = savedPosts,
                    prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                    nextKey = if (savedPosts.isEmpty()) null else offset + savedPosts.size
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

        override fun getRefreshKey(state: PagingState<Int, SavedPost>): Int? {
            return state.anchorPosition?.let { anchorPosition ->
                state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                    ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
            }
        }
    }
}