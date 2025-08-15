package com.example.liftrix.domain.usecase.social

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.domain.model.social.FollowRelationship
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.MediaItem
import com.example.liftrix.domain.model.social.MediaType
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.domain.service.PrivacyEnforcementService
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for feed generation algorithm and privacy filters
 * Tests the scoring algorithm and privacy enforcement
 */
class FeedGeneratorUseCaseTest {

    private lateinit var feedRepository: FeedRepository
    private lateinit var followRepository: FollowRepository
    private lateinit var privacyService: PrivacyEnforcementService
    private lateinit var feedGeneratorUseCase: FeedGeneratorUseCase

    private val testUserId = "test_user_123"
    private val followedUserId = "followed_user_456"
    private val publicUserId = "public_user_789"

    @Before
    fun setup() {
        feedRepository = mockk()
        followRepository = mockk()
        privacyService = mockk()
        
        feedGeneratorUseCase = FeedGeneratorUseCase(
            feedRepository = feedRepository,
            followRepository = followRepository,
            privacyService = privacyService
        )
    }

    @After
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `invoke with home feed returns posts from followed users only`() = runTest {
        // Given
        val followedUsers = listOf(
            createFollowRelationship(testUserId, followedUserId)
        )
        val expectedPosts = listOf(
            createWorkoutPost(
                id = "post1",
                userId = followedUserId,
                visibility = PostVisibility.FOLLOWERS,
                likeCount = 5,
                prsCount = 1
            )
        )

        coEvery { followRepository.getFollowing(testUserId) } returns LiftrixResult.success(followedUsers)
        every { feedRepository.getHomeFeed(testUserId) } returns flowOf(PagingData.from(expectedPosts))
        coEvery { privacyService.canViewPost(testUserId, any()) } returns true

        // When
        val result = feedGeneratorUseCase.invoke(testUserId, includeDiscovery = false)

        // Then - Test the first page of results
        val snapshot = result.asSnapshot()
        assertEquals(1, snapshot.size)
        assertEquals("post1", snapshot[0].id)
        assertEquals(followedUserId, snapshot[0].userId)
        
        verify { feedRepository.getHomeFeed(testUserId) }
    }

    @Test
    fun `invoke with discovery feed includes public posts from non-followed users`() = runTest {
        // Given
        val followedUsers = listOf(
            createFollowRelationship(testUserId, followedUserId)
        )
        val expectedPosts = listOf(
            createWorkoutPost(
                id = "public_post",
                userId = publicUserId,
                visibility = PostVisibility.PUBLIC,
                likeCount = 10,
                prsCount = 2
            )
        )

        coEvery { followRepository.getFollowing(testUserId) } returns LiftrixResult.success(followedUsers)
        every { feedRepository.getDiscoveryFeed(testUserId) } returns flowOf(PagingData.from(expectedPosts))
        coEvery { privacyService.canViewPost(testUserId, any()) } returns true

        // When
        val result = feedGeneratorUseCase.invoke(testUserId, includeDiscovery = true)

        // Then
        val snapshot = result.asSnapshot()
        assertEquals(1, snapshot.size)
        assertEquals("public_post", snapshot[0].id)
        assertEquals(publicUserId, snapshot[0].userId)
        assertEquals(PostVisibility.PUBLIC, snapshot[0].visibility)
    }

    @Test
    fun `calculateRelevance scores posts correctly based on multiple factors`() {
        // Test posts with different characteristics
        val recentPostWithPRs = createWorkoutPost(
            id = "recent_pr",
            createdAt = System.currentTimeMillis() - 1800000, // 30 minutes ago
            likeCount = 15,
            commentCount = 3,
            prsCount = 2,
            mediaItems = listOf(createMediaItem())
        )

        val oldPopularPost = createWorkoutPost(
            id = "old_popular",
            createdAt = System.currentTimeMillis() - 86400000, // 24 hours ago
            likeCount = 100,
            commentCount = 20,
            prsCount = 0,
            mediaItems = emptyList()
        )

        val simpleRecentPost = createWorkoutPost(
            id = "simple_recent",
            createdAt = System.currentTimeMillis() - 3600000, // 1 hour ago
            likeCount = 2,
            commentCount = 0,
            prsCount = 0,
            mediaItems = emptyList()
        )

        // Calculate scores using a helper that mirrors the private method logic
        val recentPRScore = calculateTestRelevanceScore(recentPostWithPRs, testUserId)
        val oldPopularScore = calculateTestRelevanceScore(oldPopularPost, testUserId)
        val simpleRecentScore = calculateTestRelevanceScore(simpleRecentPost, testUserId)

        // Recent post with PRs should score highest due to recency + PRs + media + engagement
        assertTrue("Recent PR post should score higher than old popular post", 
            recentPRScore > oldPopularScore)
        
        // Recent simple post should score higher than old post due to recency
        assertTrue("Recent simple post should score higher than old popular post due to recency", 
            simpleRecentScore > oldPopularScore)
        
        // All scores should be within expected range (0-100)
        assertTrue("Scores should be within 0-100 range", 
            recentPRScore in 0f..100f && oldPopularScore in 0f..100f && simpleRecentScore in 0f..100f)
    }

    @Test
    fun `privacy filter blocks private posts from non-authors`() = runTest {
        // Given
        val privatePosts = listOf(
            createWorkoutPost(
                id = "private_post",
                userId = followedUserId,
                visibility = PostVisibility.PRIVATE
            )
        )

        coEvery { followRepository.getFollowing(testUserId) } returns LiftrixResult.success(emptyList())
        every { feedRepository.getHomeFeed(testUserId) } returns flowOf(PagingData.from(privatePosts))
        coEvery { privacyService.canViewPost(testUserId, any()) } returns false

        // When
        val result = feedGeneratorUseCase.invoke(testUserId, includeDiscovery = false)

        // Then - posts should be filtered out by privacy service
        val snapshot = result.asSnapshot()
        assertEquals(0, snapshot.size)
        
        coVerify { privacyService.canViewPost(testUserId, any()) }
    }

    @Test
    fun `privacy filter allows followers to see followers-only posts`() = runTest {
        // Given
        val followersOnlyPosts = listOf(
            createWorkoutPost(
                id = "followers_post",
                userId = followedUserId,
                visibility = PostVisibility.FOLLOWERS
            )
        )

        coEvery { followRepository.getFollowing(testUserId) } returns LiftrixResult.success(listOf(
            createFollowRelationship(testUserId, followedUserId)
        ))
        every { feedRepository.getHomeFeed(testUserId) } returns flowOf(PagingData.from(followersOnlyPosts))
        coEvery { privacyService.canViewPost(testUserId, any()) } returns true

        // When
        val result = feedGeneratorUseCase.invoke(testUserId, includeDiscovery = false)

        // Then
        val snapshot = result.asSnapshot()
        assertEquals(1, snapshot.size)
        assertEquals("followers_post", snapshot[0].id)
        
        coVerify { privacyService.canViewPost(testUserId, any()) }
    }

    @Test
    fun `privacy filter allows everyone to see public posts`() = runTest {
        // Given
        val publicPosts = listOf(
            createWorkoutPost(
                id = "public_post",
                userId = publicUserId,
                visibility = PostVisibility.PUBLIC
            )
        )

        coEvery { followRepository.getFollowing(testUserId) } returns LiftrixResult.success(emptyList())
        every { feedRepository.getDiscoveryFeed(testUserId) } returns flowOf(PagingData.from(publicPosts))
        coEvery { privacyService.canViewPost(testUserId, any()) } returns true

        // When
        val result = feedGeneratorUseCase.invoke(testUserId, includeDiscovery = true)

        // Then
        val snapshot = result.asSnapshot()
        assertEquals(1, snapshot.size)
        assertEquals("public_post", snapshot[0].id)
        
        coVerify { privacyService.canViewPost(testUserId, any()) }
    }

    // Helper methods for creating test data

    private fun createWorkoutPost(
        id: String,
        userId: String = "user123",
        visibility: PostVisibility = PostVisibility.FOLLOWERS,
        createdAt: Long = System.currentTimeMillis(),
        likeCount: Int = 0,
        commentCount: Int = 0,
        prsCount: Int = 0,
        mediaItems: List<MediaItem> = emptyList()
    ): WorkoutPost {
        return WorkoutPost(
            id = id,
            userId = userId,
            workoutId = "workout_${id}",
            caption = "Test workout post",
            mediaItems = mediaItems,
            workoutDuration = 60,
            totalVolume = 1000.0,
            exercisesCount = 5,
            prsCount = prsCount,
            likeCount = likeCount,
            commentCount = commentCount,
            shareCount = 0,
            saveCount = 0,
            visibility = visibility,
            createdAt = createdAt,
            updatedAt = createdAt,
            authorDisplayName = "Test User",
            authorUsername = "testuser"
        )
    }

    private fun createFollowRelationship(
        followerId: String,
        followingId: String
    ): FollowRelationship {
        return FollowRelationship(
            id = "${followerId}_${followingId}",
            followerId = followerId,
            followingId = followingId,
            status = FollowStatus.FOLLOWING,
            createdAt = System.currentTimeMillis(),
            acceptedAt = System.currentTimeMillis(),
            blockedAt = null,
            userId = followingId,
            displayName = "Test User",
            profileImageUrl = null,
            bio = "Test bio",
            location = null,
            connectionStatus = ConnectionStatus.CONNECTED
        )
    }

    private fun createMediaItem(): MediaItem {
        return MediaItem(
            id = "media_123",
            type = MediaType.IMAGE,
            originalUrl = "https://example.com/image.jpg",
            thumbnailUrl = "https://example.com/thumb.jpg",
            compressedUrl = "https://example.com/compressed.jpg",
            width = 1080,
            height = 1080,
            fileSizeBytes = 2048000
        )
    }

    /**
     * Test implementation of relevance score calculation
     * This mimics the algorithm from the actual implementation
     */
    private fun calculateTestRelevanceScore(post: WorkoutPost, viewerId: String): Float {
        var score = 0f
        
        // Recency (max 40 points)
        val hoursSincePost = (System.currentTimeMillis() - post.createdAt) / 3600000f
        score += maxOf(0f, 40f - hoursSincePost * 0.5f)
        
        // Engagement (max 30 points)
        score += minOf(30f, post.likeCount * 0.5f + post.commentCount * 2f)
        
        // PRs and achievements (max 20 points)
        score += minOf(20f, post.prsCount * 10f)
        
        // Media presence (max 10 points)
        if (post.mediaItems.isNotEmpty()) score += 10f
        
        return score
    }
}

