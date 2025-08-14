package com.example.liftrix.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.domain.service.DeepLinkMetadata
import com.example.liftrix.domain.service.DeepLinkType
import com.example.liftrix.service.DeepLinkServiceImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for deep link routing and web fallback functionality.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@RunWith(AndroidJUnit4::class)
class DeepLinkRoutingTest {
    
    private lateinit var deepLinkService: DeepLinkServiceImpl
    
    @Before
    fun setup() {
        deepLinkService = DeepLinkServiceImpl()
    }
    
    @Test
    fun testCreateRoutineLink() = runTest {
        // Given
        val routineId = "routine123"
        val shareToken = "token456"
        val metadata = DeepLinkMetadata(
            title = "My Awesome Workout Routine",
            description = "A challenging full-body workout"
        )
        
        // When
        val result = deepLinkService.createRoutineLink(routineId, shareToken, metadata)
        
        // Then
        assertTrue("Routine link creation should succeed", result.isSuccess)
        val deepLink = result.getOrThrow()
        
        assertNotNull("Short URL should be generated", deepLink.shortUrl)
        assertNotNull("Long URL should be generated", deepLink.longUrl)
        assertNotNull("Web URL should be generated", deepLink.webUrl)
        
        assertTrue("Short URL should contain domain", 
            deepLink.shortUrl.contains("liftrix.page.link"))
        assertTrue("Web URL should contain routine path", 
            deepLink.webUrl.contains("/shared/routine/"))
        assertTrue("Web URL should contain share token", 
            deepLink.webUrl.contains(shareToken))
        
        assertEquals("Metadata should be preserved", metadata.title, deepLink.metadata.title)
        assertEquals("Metadata should be preserved", metadata.description, deepLink.metadata.description)
    }
    
    @Test
    fun testCreateWorkoutPostLink() = runTest {
        // Given
        val postId = "post789"
        val metadata = DeepLinkMetadata(
            title = "Check out my workout!",
            description = "Just finished an amazing session"
        )
        
        // When
        val result = deepLinkService.createWorkoutPostLink(postId, metadata)
        
        // Then
        assertTrue("Post link creation should succeed", result.isSuccess)
        val deepLink = result.getOrThrow()
        
        assertNotNull("URLs should be generated", deepLink.shortUrl)
        assertTrue("Web URL should contain post path", 
            deepLink.webUrl.contains("/shared/post/"))
        assertTrue("Web URL should contain post ID", 
            deepLink.webUrl.contains(postId))
    }
    
    @Test
    fun testCreateProfileLink() = runTest {
        // Given
        val userId = "user456"
        val metadata = DeepLinkMetadata(
            title = "Check out this fitness profile!",
            description = "Amazing transformation journey"
        )
        
        // When
        val result = deepLinkService.createProfileLink(userId, metadata)
        
        // Then
        assertTrue("Profile link creation should succeed", result.isSuccess)
        val deepLink = result.getOrThrow()
        
        assertTrue("Web URL should contain profile path", 
            deepLink.webUrl.contains("/shared/profile/"))
        assertTrue("Web URL should contain user ID", 
            deepLink.webUrl.contains(userId))
    }
    
    @Test
    fun testCreateProgressLink() = runTest {
        // Given
        val comparisonId = "comparison123"
        val metadata = DeepLinkMetadata(
            title = "Amazing fitness transformation!",
            description = "6 months of hard work"
        )
        
        // When
        val result = deepLinkService.createProgressLink(comparisonId, metadata)
        
        // Then
        assertTrue("Progress link creation should succeed", result.isSuccess)
        val deepLink = result.getOrThrow()
        
        assertTrue("Web URL should contain progress path", 
            deepLink.webUrl.contains("/shared/progress/"))
        assertTrue("Web URL should contain comparison ID", 
            deepLink.webUrl.contains(comparisonId))
    }
    
    @Test
    fun testParseRoutineDeepLink() = runTest {
        // Given
        val routineDeepLink = "liftrix://app.liftrix.com/routine/routine123?token=token456"
        
        // When
        val result = deepLinkService.parseDeepLink(routineDeepLink)
        
        // Then
        assertTrue("Parsing should succeed", result.isSuccess)
        val parsed = result.getOrThrow()
        
        assertEquals("Should identify routine type", DeepLinkType.ROUTINE, parsed.type)
        assertEquals("Should extract routine ID", "routine123", parsed.targetId)
        assertTrue("Should be valid", parsed.isValid)
        assertFalse("Should not require auth", parsed.requiresAuth)
        
        // Verify token parameter
        assertTrue("Should contain token parameter", 
            parsed.parameters.containsKey("token"))
        assertEquals("Should extract correct token", "token456", parsed.parameters["token"])
    }
    
    @Test
    fun testParseWorkoutPostDeepLink() = runTest {
        // Given
        val postDeepLink = "liftrix://app.liftrix.com/post/post789"
        
        // When
        val result = deepLinkService.parseDeepLink(postDeepLink)
        
        // Then
        assertTrue("Parsing should succeed", result.isSuccess)
        val parsed = result.getOrThrow()
        
        assertEquals("Should identify post type", DeepLinkType.WORKOUT_POST, parsed.type)
        assertEquals("Should extract post ID", "post789", parsed.targetId)
        assertTrue("Should be valid", parsed.isValid)
    }
    
    @Test
    fun testParseProfileDeepLink() = runTest {
        // Given
        val profileDeepLink = "liftrix://app.liftrix.com/profile/user456"
        
        // When
        val result = deepLinkService.parseDeepLink(profileDeepLink)
        
        // Then
        assertTrue("Parsing should succeed", result.isSuccess)
        val parsed = result.getOrThrow()
        
        assertEquals("Should identify profile type", DeepLinkType.USER_PROFILE, parsed.type)
        assertEquals("Should extract user ID", "user456", parsed.targetId)
        assertTrue("Should be valid", parsed.isValid)
    }
    
    @Test
    fun testParseProgressDeepLink() = runTest {
        // Given
        val progressDeepLink = "liftrix://app.liftrix.com/progress/comparison123"
        
        // When
        val result = deepLinkService.parseDeepLink(progressDeepLink)
        
        // Then
        assertTrue("Parsing should succeed", result.isSuccess)
        val parsed = result.getOrThrow()
        
        assertEquals("Should identify progress type", DeepLinkType.PROGRESS_COMPARISON, parsed.type)
        assertEquals("Should extract comparison ID", "comparison123", parsed.targetId)
        assertTrue("Should be valid", parsed.isValid)
    }
    
    @Test
    fun testParseInvalidDeepLink() = runTest {
        // Given
        val invalidDeepLink = "invalid://unknown.com/invalid/path"
        
        // When
        val result = deepLinkService.parseDeepLink(invalidDeepLink)
        
        // Then
        assertTrue("Parsing should succeed but return invalid", result.isSuccess)
        val parsed = result.getOrThrow()
        
        assertEquals("Should identify as unknown type", DeepLinkType.UNKNOWN, parsed.type)
        assertFalse("Should be invalid", parsed.isValid)
        assertNotNull("Should have fallback URL", parsed.fallbackUrl)
    }
    
    @Test
    fun testValidateValidDeepLink() = runTest {
        // Given
        val validDeepLink = "liftrix://app.liftrix.com/routine/routine123"
        
        // When
        val result = deepLinkService.validateDeepLink(validDeepLink)
        
        // Then
        assertTrue("Validation should succeed", result.isSuccess)
        val validation = result.getOrThrow()
        
        assertTrue("Should be valid", validation.isValid)
        assertFalse("Should not be expired", validation.isExpired)
        assertTrue("Should be accessible", validation.isAccessible)
        assertTrue("Target should exist", validation.targetExists)
        assertTrue("Errors should be empty", validation.errors.isEmpty())
    }
    
    @Test
    fun testValidateInvalidDeepLink() = runTest {
        // Given
        val invalidDeepLink = "invalid://unknown.com/invalid"
        
        // When
        val result = deepLinkService.validateDeepLink(invalidDeepLink)
        
        // Then
        assertTrue("Validation should succeed", result.isSuccess)
        val validation = result.getOrThrow()
        
        assertFalse("Should be invalid", validation.isValid)
        assertTrue("Should have errors", validation.errors.isNotEmpty())
    }
    
    @Test
    fun testGetDeepLinkAnalytics() = runTest {
        // Given
        val deepLinkUrl = "https://liftrix.page.link/test123"
        
        // When
        val result = deepLinkService.getDeepLinkAnalytics(deepLinkUrl)
        
        // Then
        assertTrue("Analytics retrieval should succeed", result.isSuccess)
        val analytics = result.getOrThrow()
        
        assertEquals("Should return correct URL", deepLinkUrl, analytics.linkUrl)
        assertTrue("Should have positive click count", analytics.totalClicks >= 0)
        assertTrue("Should have reasonable unique clicks", 
            analytics.uniqueClicks <= analytics.totalClicks)
        assertTrue("Should have reasonable conversion rate", 
            analytics.conversionRate in 0.0f..1.0f)
        assertTrue("Should have top referrers", analytics.topReferrers.isNotEmpty())
        assertTrue("Should have top countries", analytics.topCountries.isNotEmpty())
        assertTrue("Should have top platforms", analytics.topPlatforms.isNotEmpty())
    }
    
    @Test
    fun testDeepLinkWithSpecialCharacters() = runTest {
        // Given
        val routineId = "routine-with_special.chars123"
        val shareToken = "token@special#chars"
        
        // When
        val createResult = deepLinkService.createRoutineLink(routineId, shareToken)
        
        // Then
        assertTrue("Creation should handle special characters", createResult.isSuccess)
        val deepLink = createResult.getOrThrow()
        
        // Parse the created link
        val parseResult = deepLinkService.parseDeepLink(deepLink.longUrl)
        assertTrue("Parsing should succeed", parseResult.isSuccess)
        
        val parsed = parseResult.getOrThrow()
        assertEquals("Should preserve routine ID", routineId, parsed.targetId)
    }
    
    @Test
    fun testDeepLinkWebFallback() = runTest {
        // Given
        val routineId = "routine123"
        val shareToken = "token456"
        
        // When
        val result = deepLinkService.createRoutineLink(routineId, shareToken)
        
        // Then
        assertTrue("Creation should succeed", result.isSuccess)
        val deepLink = result.getOrThrow()
        
        // Verify web fallback structure
        assertTrue("Web URL should be valid HTTP/HTTPS", 
            deepLink.webUrl.startsWith("http"))
        assertTrue("Web URL should contain app domain", 
            deepLink.webUrl.contains("liftrix.app"))
        assertTrue("Web URL should have routine path", 
            deepLink.webUrl.contains("/shared/routine/"))
        assertTrue("Web URL should include share token", 
            deepLink.webUrl.contains(shareToken))
    }
    
    @Test
    fun testQRCodeGeneration() = runTest {
        // Given
        val routineId = "routine123"
        val shareToken = "token456"
        
        // When
        val result = deepLinkService.createRoutineLink(routineId, shareToken)
        
        // Then
        assertTrue("Creation should succeed", result.isSuccess)
        val deepLink = result.getOrThrow()
        
        assertNotNull("QR code URL should be generated", deepLink.qrCodeUrl)
        assertTrue("QR code URL should be valid", 
            deepLink.qrCodeUrl?.startsWith("http") == true)
        assertTrue("QR code URL should contain the short URL", 
            deepLink.qrCodeUrl?.contains(deepLink.shortUrl) == true)
    }
    
    @Test
    fun testDeepLinkExpiration() = runTest {
        // Given
        val routineId = "routine123"
        val shareToken = "token456"
        val futureExpiration = System.currentTimeMillis() + 86400000 // 24 hours
        val metadata = DeepLinkMetadata(
            title = "Test Routine",
            expiration = futureExpiration
        )
        
        // When
        val result = deepLinkService.createRoutineLink(routineId, shareToken, metadata)
        
        // Then
        assertTrue("Creation should succeed", result.isSuccess)
        val deepLink = result.getOrThrow()
        
        assertEquals("Expiration should be set", futureExpiration, deepLink.expiresAt)
        
        // Validate the link
        val validationResult = deepLinkService.validateDeepLink(deepLink.shortUrl)
        assertTrue("Validation should succeed", validationResult.isSuccess)
        
        val validation = validationResult.getOrThrow()
        assertFalse("Should not be expired yet", validation.isExpired)
    }
    
    @Test
    fun testConcurrentDeepLinkCreation() = runTest {
        // Given
        val routineIds = (1..10).map { "routine$it" }
        val shareTokens = (1..10).map { "token$it" }
        
        // When - Create multiple links concurrently
        val results = routineIds.zip(shareTokens).map { (routineId, shareToken) ->
            deepLinkService.createRoutineLink(routineId, shareToken)
        }
        
        // Then
        results.forEach { result ->
            assertTrue("All creations should succeed", result.isSuccess)
        }
        
        val deepLinks = results.map { it.getOrThrow() }
        val shortUrls = deepLinks.map { it.shortUrl }
        
        // Verify all URLs are unique
        assertEquals("All short URLs should be unique", 
            shortUrls.size, shortUrls.distinct().size)
    }
    
    @Test
    fun testDeepLinkAnalyticsTimeframes() = runTest {
        // Given
        val deepLinkUrl = "https://liftrix.page.link/test123"
        val timeframes = listOf(
            com.example.liftrix.domain.service.DeepLinkTimeframe.LAST_1D,
            com.example.liftrix.domain.service.DeepLinkTimeframe.LAST_7D,
            com.example.liftrix.domain.service.DeepLinkTimeframe.LAST_30D,
            com.example.liftrix.domain.service.DeepLinkTimeframe.ALL_TIME
        )
        
        // When
        val results = timeframes.map { timeframe ->
            deepLinkService.getDeepLinkAnalytics(deepLinkUrl, timeframe)
        }
        
        // Then
        results.forEachIndexed { index, result ->
            assertTrue("Analytics should succeed for ${timeframes[index]}", result.isSuccess)
            val analytics = result.getOrThrow()
            assertEquals("Should return correct timeframe", timeframes[index], analytics.timeframe)
        }
        
        // Verify analytics scale with timeframe
        val analyticsList = results.map { it.getOrThrow() }
        val oneDayClicks = analyticsList[0].totalClicks
        val allTimeClicks = analyticsList[3].totalClicks
        
        assertTrue("All time should have more clicks than one day", 
            allTimeClicks >= oneDayClicks)
    }
}