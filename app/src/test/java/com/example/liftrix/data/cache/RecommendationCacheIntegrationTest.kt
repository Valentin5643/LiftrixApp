package com.example.liftrix.data.cache

import android.content.Context
import android.content.SharedPreferences
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.model.User
import com.google.gson.Gson
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for RecommendationCache functionality
 * Tests cache implementation with TTL, user validation, and SharedPreferences integration
 */
@RunWith(RobolectricTestRunner::class)
class RecommendationCacheIntegrationTest {

    @MockK
    private lateinit var context: Context
    
    @MockK
    private lateinit var sharedPreferences: SharedPreferences
    
    @MockK
    private lateinit var editor: SharedPreferences.Editor
    
    private lateinit var gson: Gson
    private lateinit var recommendationCache: RecommendationCache

    private val testUserId = "test-user-123"
    private val otherUserId = "other-user-456"
    
    private val testUser = User(
        uid = "recommended-user-1",
        email = "recommended1@example.com",
        displayName = "Recommended User 1",
        photoUrl = "https://example.com/avatar1.jpg",
        isAnonymous = false,
        subscriptionTier = SubscriptionTier.FREE,
        subscriptionStatus = SubscriptionStatus.ACTIVE,
        subscriptionExpiresAt = null,
        premiumFeaturesEnabled = false,
        onboardingCompleted = true,
        profileVersion = 1L,
        createdAt = LocalDateTime.now().minusDays(10),
        lastSignInAt = LocalDateTime.now().minusHours(2),
        updatedAt = LocalDateTime.now().minusHours(2)
    )
    
    private val secondUser = User(
        uid = "recommended-user-2",
        email = "recommended2@example.com",
        displayName = "Recommended User 2",
        photoUrl = null,
        isAnonymous = false,
        subscriptionTier = SubscriptionTier.FREE,
        subscriptionStatus = SubscriptionStatus.ACTIVE,
        subscriptionExpiresAt = null,
        premiumFeaturesEnabled = false,
        onboardingCompleted = true,
        profileVersion = 1L,
        createdAt = LocalDateTime.now().minusDays(5),
        lastSignInAt = LocalDateTime.now().minusHours(1),
        updatedAt = LocalDateTime.now().minusHours(1)
    )
    
    private val validRecommendations = listOf(
        RecommendedUser.fromUser(testUser, isFollowing = false),
        RecommendedUser.fromUser(secondUser, isFollowing = true)
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        gson = Gson()
        
        every { context.getSharedPreferences("liftrix_recommendation_cache", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit
        
        recommendationCache = RecommendationCache(context, gson)
    }

    @Test
    fun `getCachedRecommendations returns null when no cache exists`() = runTest {
        // Given
        every { sharedPreferences.getString("cached_user_id", null) } returns null
        every { sharedPreferences.getLong("cache_timestamp", 0L) } returns 0L
        every { sharedPreferences.getString("recommendations", null) } returns null

        // When
        val result = recommendationCache.getCachedRecommendations(testUserId)

        // Then
        assertNull(result)
        
        verify { sharedPreferences.getString("cached_user_id", null) }
    }

    @Test
    fun `getCachedRecommendations returns null when cached for different user`() = runTest {
        // Given
        every { sharedPreferences.getString("cached_user_id", null) } returns otherUserId
        every { sharedPreferences.getLong("cache_timestamp", 0L) } returns System.currentTimeMillis()

        // When
        val result = recommendationCache.getCachedRecommendations(testUserId)

        // Then
        assertNull(result)
        
        verify { sharedPreferences.getString("cached_user_id", null) }
    }

    @Test
    fun `getCachedRecommendations returns null when timestamp is missing`() = runTest {
        // Given
        every { sharedPreferences.getString("cached_user_id", null) } returns testUserId
        every { sharedPreferences.getLong("cache_timestamp", 0L) } returns 0L

        // When
        val result = recommendationCache.getCachedRecommendations(testUserId)

        // Then
        assertNull(result)
        
        verify { sharedPreferences.getString("cached_user_id", null) }
        verify { sharedPreferences.getLong("cache_timestamp", 0L) }
    }

    @Test
    fun `getCachedRecommendations returns null when recommendations data is missing`() = runTest {
        // Given
        every { sharedPreferences.getString("cached_user_id", null) } returns testUserId
        every { sharedPreferences.getLong("cache_timestamp", 0L) } returns System.currentTimeMillis()
        every { sharedPreferences.getString("recommendations", null) } returns null

        // When
        val result = recommendationCache.getCachedRecommendations(testUserId)

        // Then
        assertNull(result)
        
        verify { sharedPreferences.getString("cached_user_id", null) }
        verify { sharedPreferences.getLong("cache_timestamp", 0L) }
        verify { sharedPreferences.getString("recommendations", null) }
    }

    @Test
    fun `getCachedRecommendations returns null when recommendations list is empty`() = runTest {
        // Given
        val emptyRecommendationsJson = gson.toJson(emptyList<RecommendedUser>())
        
        every { sharedPreferences.getString("cached_user_id", null) } returns testUserId
        every { sharedPreferences.getLong("cache_timestamp", 0L) } returns System.currentTimeMillis()
        every { sharedPreferences.getString("recommendations", null) } returns emptyRecommendationsJson

        // When
        val result = recommendationCache.getCachedRecommendations(testUserId)

        // Then
        assertNull(result)
        
        verify { sharedPreferences.getString("cached_user_id", null) }
        verify { sharedPreferences.getLong("cache_timestamp", 0L) }
        verify { sharedPreferences.getString("recommendations", null) }
    }

    @Test
    fun `getCachedRecommendations clears cache when TTL expired`() = runTest {
        // Given - Create expired recommendations (cache timestamp from 25 hours ago)
        val expiredTimestamp = System.currentTimeMillis() - (25 * 60 * 60 * 1000L) // 25 hours ago
        val expiredRecommendations = validRecommendations.map { 
            it.copy(cachedAt = expiredTimestamp) 
        }
        val expiredRecommendationsJson = gson.toJson(expiredRecommendations)
        
        every { sharedPreferences.getString("cached_user_id", null) } returns testUserId
        every { sharedPreferences.getLong("cache_timestamp", 0L) } returns expiredTimestamp
        every { sharedPreferences.getString("recommendations", null) } returns expiredRecommendationsJson

        // When
        val result = recommendationCache.getCachedRecommendations(testUserId)

        // Then
        assertNull(result)
        
        // Verify cache was cleared
        verify { editor.remove("cached_user_id") }
        verify { editor.remove("recommendations") }
        verify { editor.remove("cache_timestamp") }
        verify { editor.apply() }
    }

    @Test
    fun `getCachedRecommendations returns valid cached recommendations`() = runTest {
        // Given - Create fresh recommendations (cached 1 hour ago)
        val recentTimestamp = System.currentTimeMillis() - (60 * 60 * 1000L) // 1 hour ago
        val freshRecommendations = validRecommendations.map { 
            it.copy(cachedAt = recentTimestamp) 
        }
        val freshRecommendationsJson = gson.toJson(freshRecommendations)
        
        every { sharedPreferences.getString("cached_user_id", null) } returns testUserId
        every { sharedPreferences.getLong("cache_timestamp", 0L) } returns recentTimestamp
        every { sharedPreferences.getString("recommendations", null) } returns freshRecommendationsJson

        // When
        val result = recommendationCache.getCachedRecommendations(testUserId)

        // Then
        assertEquals(2, result?.size)
        assertEquals("recommended-user-1", result?.first()?.userId)
        assertEquals("recommended-user-2", result?.get(1)?.userId)
        assertFalse(result?.first()?.isFollowing ?: true)
        assertTrue(result?.get(1)?.isFollowing ?: false)
        
        verify { sharedPreferences.getString("cached_user_id", null) }
        verify { sharedPreferences.getLong("cache_timestamp", 0L) }
        verify { sharedPreferences.getString("recommendations", null) }
    }

    @Test
    fun `cacheRecommendations stores recommendations with current timestamp`() = runTest {
        // Given
        val currentTime = System.currentTimeMillis()
        
        // Mock system time for consistent testing
        every { sharedPreferences.edit() } returns editor

        // When
        recommendationCache.cacheRecommendations(testUserId, validRecommendations)

        // Then
        verify { editor.putString("cached_user_id", testUserId) }
        verify { editor.putLong("cache_timestamp", any()) } // Timestamp will be current
        verify { editor.putString("recommendations", any()) } // JSON will be generated
        verify { editor.apply() }
    }

    @Test
    fun `cacheRecommendations does not cache empty recommendations list`() = runTest {
        // Given
        val emptyRecommendations = emptyList<RecommendedUser>()

        // When
        recommendationCache.cacheRecommendations(testUserId, emptyRecommendations)

        // Then
        // Should not attempt to store empty list
        verify(exactly = 0) { editor.putString(any(), any()) }
        verify(exactly = 0) { editor.putLong(any(), any()) }
        verify(exactly = 0) { editor.apply() }
    }

    @Test
    fun `isCacheValid returns false when no cache exists`() = runTest {
        // Given
        every { sharedPreferences.getString("cached_user_id", null) } returns null

        // When
        val isValid = recommendationCache.isCacheValid(testUserId)

        // Then
        assertFalse(isValid)
        
        verify { sharedPreferences.getString("cached_user_id", null) }
    }

    @Test
    fun `isCacheValid returns false when cached for different user`() = runTest {
        // Given
        every { sharedPreferences.getString("cached_user_id", null) } returns otherUserId

        // When
        val isValid = recommendationCache.isCacheValid(testUserId)

        // Then
        assertFalse(isValid)
        
        verify { sharedPreferences.getString("cached_user_id", null) }
    }

    @Test
    fun `isCacheValid returns true for valid cache within TTL`() = runTest {
        // Given - Create fresh recommendations
        val recentTimestamp = System.currentTimeMillis() - (30 * 60 * 1000L) // 30 minutes ago
        val freshRecommendations = validRecommendations.map { 
            it.copy(cachedAt = recentTimestamp) 
        }
        val freshRecommendationsJson = gson.toJson(freshRecommendations)
        
        every { sharedPreferences.getString("cached_user_id", null) } returns testUserId
        every { sharedPreferences.getLong("cache_timestamp", 0L) } returns recentTimestamp
        every { sharedPreferences.getString("recommendations", null) } returns freshRecommendationsJson

        // When
        val isValid = recommendationCache.isCacheValid(testUserId)

        // Then
        assertTrue(isValid)
        
        verify { sharedPreferences.getString("cached_user_id", null) }
        verify { sharedPreferences.getLong("cache_timestamp", 0L) }
        verify { sharedPreferences.getString("recommendations", null) }
    }

    @Test
    fun `isCacheValid returns false for expired cache`() = runTest {
        // Given - Create expired recommendations
        val expiredTimestamp = System.currentTimeMillis() - (25 * 60 * 60 * 1000L) // 25 hours ago
        val expiredRecommendations = validRecommendations.map { 
            it.copy(cachedAt = expiredTimestamp) 
        }
        val expiredRecommendationsJson = gson.toJson(expiredRecommendations)
        
        every { sharedPreferences.getString("cached_user_id", null) } returns testUserId
        every { sharedPreferences.getLong("cache_timestamp", 0L) } returns expiredTimestamp
        every { sharedPreferences.getString("recommendations", null) } returns expiredRecommendationsJson

        // When
        val isValid = recommendationCache.isCacheValid(testUserId)

        // Then
        assertFalse(isValid)
    }

    @Test
    fun `clearCache removes all cache data`() = runTest {
        // When
        recommendationCache.clearCache()

        // Then
        verify { editor.remove("cached_user_id") }
        verify { editor.remove("recommendations") }
        verify { editor.remove("cache_timestamp") }
        verify { editor.apply() }
    }

    @Test
    fun `invalidateCacheForUser clears cache only for matching user`() = runTest {
        // Given
        every { sharedPreferences.getString("cached_user_id", null) } returns testUserId

        // When
        recommendationCache.invalidateCacheForUser(testUserId)

        // Then
        verify { editor.remove("cached_user_id") }
        verify { editor.remove("recommendations") }
        verify { editor.remove("cache_timestamp") }
        verify { editor.apply() }
    }

    @Test
    fun `invalidateCacheForUser does not clear cache for different user`() = runTest {
        // Given
        every { sharedPreferences.getString("cached_user_id", null) } returns otherUserId

        // When
        recommendationCache.invalidateCacheForUser(testUserId)

        // Then
        // Should not clear cache since it's for a different user
        verify(exactly = 0) { editor.remove(any()) }
        verify(exactly = 0) { editor.apply() }
        
        verify { sharedPreferences.getString("cached_user_id", null) }
    }

    @Test
    fun `cache integration end-to-end scenario`() = runTest {
        // Given - Setup complete cache lifecycle
        val currentTime = System.currentTimeMillis()
        val recommendations = validRecommendations
        
        // Setup for caching
        every { sharedPreferences.edit() } returns editor

        // Step 1: Cache recommendations
        recommendationCache.cacheRecommendations(testUserId, recommendations)
        
        // Step 2: Setup retrieval mocks (simulate fresh cache)
        val recentTimestamp = currentTime - (30 * 60 * 1000L) // 30 minutes ago
        val cachedRecommendations = recommendations.map { 
            it.copy(cachedAt = recentTimestamp) 
        }
        val cachedJson = gson.toJson(cachedRecommendations)
        
        every { sharedPreferences.getString("cached_user_id", null) } returns testUserId
        every { sharedPreferences.getLong("cache_timestamp", 0L) } returns recentTimestamp
        every { sharedPreferences.getString("recommendations", null) } returns cachedJson

        // When - Execute complete cache cycle
        val cacheValidBefore = recommendationCache.isCacheValid(testUserId)
        val retrievedRecommendations = recommendationCache.getCachedRecommendations(testUserId)
        
        // Invalidate cache
        recommendationCache.invalidateCacheForUser(testUserId)
        
        // Check after invalidation
        every { sharedPreferences.getString("cached_user_id", null) } returns null
        val cacheValidAfter = recommendationCache.isCacheValid(testUserId)

        // Then - Verify complete cache lifecycle
        assertTrue(cacheValidBefore)
        assertEquals(2, retrievedRecommendations?.size)
        assertEquals("recommended-user-1", retrievedRecommendations?.first()?.userId)
        assertEquals("Recommended User 1", retrievedRecommendations?.first()?.username)
        assertEquals("recommended-user-2", retrievedRecommendations?.get(1)?.userId)
        assertEquals("Recommended User 2", retrievedRecommendations?.get(1)?.username)
        assertFalse(cacheValidAfter)
        
        // Verify cache operations
        verify { editor.putString("cached_user_id", testUserId) }
        verify { editor.putString("recommendations", any()) }
        verify { editor.putLong("cache_timestamp", any()) }
        verify(atLeast = 2) { editor.apply() } // Once for cache, once for invalidation
        
        // Verify cache retrieval
        verify(atLeast = 1) { sharedPreferences.getString("cached_user_id", null) }
        verify { sharedPreferences.getString("recommendations", null) }
        verify { sharedPreferences.getLong("cache_timestamp", 0L) }
        
        // Verify cache invalidation
        verify { editor.remove("cached_user_id") }
        verify { editor.remove("recommendations") }
        verify { editor.remove("cache_timestamp") }
    }

    @Test
    fun `cache handles JSON serialization errors gracefully`() = runTest {
        // Given - Setup corrupted JSON data
        every { sharedPreferences.getString("cached_user_id", null) } returns testUserId
        every { sharedPreferences.getLong("cache_timestamp", 0L) } returns System.currentTimeMillis()
        every { sharedPreferences.getString("recommendations", null) } returns "corrupted-json-data"

        // When
        val result = recommendationCache.getCachedRecommendations(testUserId)

        // Then
        assertNull(result) // Should handle JSON parsing error gracefully
        
        // Verify cache was cleared due to corruption
        verify { editor.remove("cached_user_id") }
        verify { editor.remove("recommendations") }
        verify { editor.remove("cache_timestamp") }
        verify { editor.apply() }
    }
}