package com.example.liftrix.notifications

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import javax.inject.Inject
import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.filters.SdkSuppress
import com.example.liftrix.data.repository.notifications.FCMTokenRepository
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.services.NotificationChannelManager
import com.example.liftrix.services.LiftrixFirebaseMessagingService
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for FCM (Firebase Cloud Messaging) functionality.
 * 
 * Tests the complete notification flow including:
 * - FCM token retrieval and refresh
 * - Token storage and management
 * - Notification delivery and handling
 * - Channel management integration
 * - Permission handling on different Android versions
 * 
 * These tests run on actual device/emulator with real Firebase integration
 * to ensure the notification system works end-to-end.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FCMIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val permissionRule: GrantPermissionRule = 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant() // No permission needed on older versions
        }

    @Inject
    lateinit var fcmTokenRepository: FCMTokenRepository

    @Inject
    lateinit var notificationChannelManager: NotificationChannelManager

    private lateinit var context: Context
    private lateinit var firebaseMessaging: FirebaseMessaging
    
    // Test state tracking
    private var testUserId: String = "test-user-${UUID.randomUUID()}"
    private var receivedMessages = mutableListOf<RemoteMessage>()
    private var tokenRefreshLatch: CountDownLatch? = null

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Initialize Firebase if not already done
        if (FirebaseApp.getApps(context).isEmpty()) {
            Firebase.initialize(context)
        }
        
        firebaseMessaging = FirebaseMessaging.getInstance()
        
        // Initialize notification channels
        notificationChannelManager.initializeChannels()
        
        // Clear any previous test data
        receivedMessages.clear()
        
        Timber.d("FCM Integration Test setup completed for user: $testUserId")
    }

    @After
    fun cleanup() {
        runBlocking {
            try {
                // Clean up test tokens
                cleanupTestTokens()
            } catch (e: Exception) {
                Timber.w(e, "Error during test cleanup")
            }
        }
    }

    /**
     * Test FCM token retrieval and basic functionality
     */
    @Test
    fun testFCMTokenRetrieval() = runBlocking {
        Timber.d("Testing FCM token retrieval")
        
        // Get FCM token
        val token = withTimeout(10_000) {
            firebaseMessaging.token.await()
        }
        
        // Verify token is not empty and has expected format
        assertNotNull("FCM token should not be null", token)
        assertFalse("FCM token should not be empty", token.isEmpty())
        assertTrue("FCM token should be reasonably long", token.length > 50)
        
        Timber.d("Successfully retrieved FCM token: ${token.take(20)}...")
    }

    /**
     * Test FCM token storage and retrieval from repository
     */
    @Test
    fun testFCMTokenStorage() = runBlocking {
        Timber.d("Testing FCM token storage")
        
        // Get fresh FCM token
        val fcmToken = firebaseMessaging.token.await()
        val deviceId = "test-device-${UUID.randomUUID()}"
        
        // Store token using repository
        val storeResult = fcmTokenRepository.updateToken(
            userId = testUserId,
            token = fcmToken,
            deviceId = deviceId,
            platform = "ANDROID",
            appVersion = "1.0.0-test"
        ).first()
        
        // Verify storage was successful
        assertTrue("Token storage should succeed", storeResult.isSuccess)
        
        // Retrieve stored tokens
        val retrieveResult = fcmTokenRepository.getActiveTokensForUser(testUserId).first()
        
        assertTrue("Token retrieval should succeed", retrieveResult.isSuccess)
        
        val tokens = retrieveResult.getOrNull()!!
        assertTrue("Should have at least one stored token", tokens.isNotEmpty())
        
        val storedToken = tokens.find { it.deviceId == deviceId }
        assertNotNull("Should find the stored token", storedToken)
        assertEquals("Stored token should match", fcmToken, storedToken!!.token)
        assertEquals("User ID should match", testUserId, storedToken.userId)
        assertTrue("Token should be active", storedToken.isActive)
        
        Timber.d("Successfully stored and retrieved FCM token")
    }

    /**
     * Test FCM token refresh handling
     */
    @Test
    fun testFCMTokenRefresh() = runBlocking {
        Timber.d("Testing FCM token refresh")
        
        val deviceId = "test-device-refresh-${UUID.randomUUID()}"
        
        // Get initial token and store it
        val initialToken = firebaseMessaging.token.await()
        val storeResult = fcmTokenRepository.updateToken(
            userId = testUserId,
            token = initialToken,
            deviceId = deviceId,
            platform = "ANDROID"
        ).first()
        
        assertTrue("Initial token storage should succeed", storeResult.isSuccess)
        
        // Simulate token refresh by deleting and recreating
        firebaseMessaging.deleteToken().await()
        delay(1000) // Give Firebase time to process
        
        val newToken = firebaseMessaging.token.await()
        assertNotEquals("New token should be different", initialToken, newToken)
        
        // Update with new token
        val updateResult = fcmTokenRepository.updateToken(
            userId = testUserId,
            token = newToken,
            deviceId = deviceId,
            platform = "ANDROID"
        ).first()
        
        assertTrue("Token refresh should succeed", updateResult.isSuccess)
        
        // Verify the new token is stored
        val retrieveResult = fcmTokenRepository.getActiveTokensForUser(testUserId).first()
        assertTrue("Token retrieval after refresh should succeed", retrieveResult.isSuccess)
        
        val tokens = retrieveResult.getOrNull()!!
        val refreshedToken = tokens.find { it.deviceId == deviceId }
        assertNotNull("Refreshed token should be found", refreshedToken)
        assertEquals("Token should be updated", newToken, refreshedToken!!.token)
        
        Timber.d("Successfully tested FCM token refresh")
    }

    /**
     * Test notification channel integration
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun testNotificationChannelIntegration() {
        Timber.d("Testing notification channel integration")
        
        // Verify all required channels are created
        val requiredChannels = listOf(
            NotificationChannelManager.CHANNEL_GYM_BUDDY,
            NotificationChannelManager.CHANNEL_SOCIAL_REQUESTS,
            NotificationChannelManager.CHANNEL_SOCIAL_ENGAGEMENT,
            NotificationChannelManager.CHANNEL_ACHIEVEMENT,
            NotificationChannelManager.CHANNEL_REMINDER,
            NotificationChannelManager.CHANNEL_DEFAULT
        )
        
        requiredChannels.forEach { channelId ->
            assertTrue(
                "Channel $channelId should be enabled",
                notificationChannelManager.isChannelEnabled(channelId)
            )
        }
        
        // Test channel ID mapping
        val testMappings = mapOf(
            "GYM_BUDDY_PR" to NotificationChannelManager.CHANNEL_GYM_BUDDY,
            "FOLLOW_REQUEST" to NotificationChannelManager.CHANNEL_SOCIAL_REQUESTS,
            "POST_LIKE" to NotificationChannelManager.CHANNEL_SOCIAL_ENGAGEMENT,
            "ACHIEVEMENT" to NotificationChannelManager.CHANNEL_ACHIEVEMENT,
            "WORKOUT_REMINDER" to NotificationChannelManager.CHANNEL_REMINDER,
            "UNKNOWN_TYPE" to NotificationChannelManager.CHANNEL_DEFAULT
        )
        
        testMappings.forEach { (notificationType, expectedChannelId) ->
            val actualChannelId = notificationChannelManager.getChannelIdForNotificationType(notificationType)
            assertEquals(
                "Channel mapping for $notificationType should be correct",
                expectedChannelId,
                actualChannelId
            )
        }
        
        Timber.d("Successfully tested notification channel integration")
    }

    /**
     * Test notification permissions on different Android versions
     */
    @Test
    fun testNotificationPermissions() {
        Timber.d("Testing notification permissions")
        
        val areNotificationsEnabled = notificationChannelManager.areNotificationsEnabled()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // On Android 13+, we granted permission in the test rule
            assertTrue(
                "Notifications should be enabled on Android 13+ with granted permission",
                areNotificationsEnabled
            )
        } else {
            // On older versions, notifications should be enabled by default
            assertTrue(
                "Notifications should be enabled by default on Android < 13",
                areNotificationsEnabled
            )
        }
        
        Timber.d("Notification permissions test completed - enabled: $areNotificationsEnabled")
    }

    /**
     * Test multiple device token management
     */
    @Test
    fun testMultipleDeviceTokens() = runBlocking {
        Timber.d("Testing multiple device token management")
        
        val device1Id = "test-device-1-${UUID.randomUUID()}"
        val device2Id = "test-device-2-${UUID.randomUUID()}"
        val mockToken1 = "mock-token-1-${UUID.randomUUID()}"
        val mockToken2 = "mock-token-2-${UUID.randomUUID()}"
        
        // Store tokens for two different devices
        val store1Result = fcmTokenRepository.updateToken(
            userId = testUserId,
            token = mockToken1,
            deviceId = device1Id,
            platform = "ANDROID"
        ).first()
        
        val store2Result = fcmTokenRepository.updateToken(
            userId = testUserId,
            token = mockToken2,
            deviceId = device2Id,
            platform = "ANDROID"
        ).first()
        
        assertTrue("Device 1 token storage should succeed", store1Result.isSuccess)
        assertTrue("Device 2 token storage should succeed", store2Result.isSuccess)
        
        // Retrieve all tokens for user
        val retrieveResult = fcmTokenRepository.getActiveTokensForUser(testUserId).first()
        assertTrue("Token retrieval should succeed", retrieveResult.isSuccess)
        
        val tokens = retrieveResult.getOrNull()!!
        assertTrue("Should have at least 2 tokens", tokens.size >= 2)
        
        // Verify both tokens are present
        val device1Token = tokens.find { it.deviceId == device1Id }
        val device2Token = tokens.find { it.deviceId == device2Id }
        
        assertNotNull("Device 1 token should be found", device1Token)
        assertNotNull("Device 2 token should be found", device2Token)
        assertEquals("Device 1 token should match", mockToken1, device1Token!!.token)
        assertEquals("Device 2 token should match", mockToken2, device2Token!!.token)
        
        // Test deactivating one token
        val deactivateResult = fcmTokenRepository.deactivateToken(mockToken1).first()
        assertTrue("Token deactivation should succeed", deactivateResult.isSuccess)
        
        // Verify only one token remains active
        val activeTokensResult = fcmTokenRepository.getActiveTokensForUser(testUserId).first()
        assertTrue("Active tokens retrieval should succeed", activeTokensResult.isSuccess)
        
        val activeTokens = activeTokensResult.getOrNull()!!
        val activeDevice1Token = activeTokens.find { it.deviceId == device1Id }
        val activeDevice2Token = activeTokens.find { it.deviceId == device2Id }
        
        assertTrue("Device 1 token should be deactivated", activeDevice1Token?.isActive == false)
        assertTrue("Device 2 token should still be active", activeDevice2Token?.isActive == true)
        
        Timber.d("Successfully tested multiple device token management")
    }

    /**
     * Test token cleanup for expired/invalid tokens
     */
    @Test
    fun testTokenCleanup() = runBlocking {
        Timber.d("Testing token cleanup")
        
        val expiredDeviceId = "expired-device-${UUID.randomUUID()}"
        val validDeviceId = "valid-device-${UUID.randomUUID()}"
        val expiredToken = "expired-token-${UUID.randomUUID()}"
        val validToken = "valid-token-${UUID.randomUUID()}"
        
        // Store both tokens
        fcmTokenRepository.updateToken(testUserId, expiredToken, expiredDeviceId, "ANDROID").first()
        fcmTokenRepository.updateToken(testUserId, validToken, validDeviceId, "ANDROID").first()
        
        // Mark one as expired by simulating last used time
        val expireResult = fcmTokenRepository.markTokenAsExpired(expiredToken).first()
        assertTrue("Token expiration should succeed", expireResult.isSuccess)
        
        // Run cleanup
        val cleanupResult = fcmTokenRepository.cleanupExpiredTokens().first()
        assertTrue("Token cleanup should succeed", cleanupResult.isSuccess)
        
        // Verify expired token is removed and valid token remains
        val remainingTokensResult = fcmTokenRepository.getActiveTokensForUser(testUserId).first()
        assertTrue("Remaining tokens retrieval should succeed", remainingTokensResult.isSuccess)
        
        val remainingTokens = remainingTokensResult.getOrNull()!!
        val expiredTokenExists = remainingTokens.any { it.token == expiredToken && it.isActive }
        val validTokenExists = remainingTokens.any { it.token == validToken && it.isActive }
        
        assertFalse("Expired token should be removed", expiredTokenExists)
        assertTrue("Valid token should remain", validTokenExists)
        
        Timber.d("Successfully tested token cleanup")
    }

    /**
     * Clean up test tokens to avoid leaving test data
     */
    private suspend fun cleanupTestTokens() {
        try {
            // Get all tokens for test user
            val tokensResult = fcmTokenRepository.getActiveTokensForUser(testUserId).first()
            if (tokensResult.isSuccess) {
                val tokens = tokensResult.getOrNull()!!
                
                // Deactivate all test tokens
                tokens.forEach { token ->
                    fcmTokenRepository.deactivateToken(token.token).first()
                }
                
                Timber.d("Cleaned up ${tokens.size} test tokens")
            }
        } catch (e: Exception) {
            Timber.w(e, "Error during token cleanup")
        }
    }

    /**
     * Helper method to wait for async operations with timeout
     */
    private suspend fun waitForCondition(
        timeoutMs: Long = 5000,
        condition: suspend () -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) {
                return true
            }
            delay(100)
        }
        return false
    }
}

/**
 * Test utilities for FCM integration testing
 */
object FCMTestUtils {
    
    /**
     * Generate a mock FCM token for testing
     */
    fun generateMockFCMToken(): String {
        return "mock_fcm_token_${UUID.randomUUID().toString().replace("-", "")}"
    }
    
    /**
     * Create a test RemoteMessage
     */
    fun createTestRemoteMessage(
        messageId: String = UUID.randomUUID().toString(),
        from: String = "test-sender",
        data: Map<String, String> = emptyMap()
    ): RemoteMessage {
        val builder = RemoteMessage.Builder("test-destination")
        builder.setMessageId(messageId)
        builder.addData("from", from)
        data.forEach { (key, value) ->
            builder.addData(key, value)
        }
        return builder.build()
    }
    
    /**
     * Verify notification data payload structure
     */
    fun verifyNotificationPayload(
        data: Map<String, String>,
        expectedType: String,
        requiredFields: List<String> = emptyList()
    ): Boolean {
        // Check notification type
        if (data["type"] != expectedType) {
            return false
        }
        
        // Check required fields
        return requiredFields.all { field ->
            data.containsKey(field) && !data[field].isNullOrEmpty()
        }
    }
}