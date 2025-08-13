package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.LiftrixResult
import com.example.liftrix.domain.model.notifications.NotificationQueue
import com.example.liftrix.domain.model.notifications.QueuedNotification
import com.example.liftrix.domain.repository.notifications.NotificationQueueRepository
import com.example.liftrix.domain.service.notifications.NotificationBatchProcessor
import com.example.liftrix.domain.service.notifications.FCMSender
import com.example.liftrix.domain.service.notifications.BatchNotificationBuilder
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.SendResponse
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*

/**
 * Unit tests for NotificationBatchProcessor.
 * 
 * Tests the batching logic including:
 * - Grouping notifications by user and batch key
 * - Different batch size handling (single, multiple, large batches)
 * - Timing and scheduling logic
 * - Error handling during batch processing
 * - Notification format conversion for different batch sizes
 * - Performance with large notification queues
 */
@RunWith(RobolectricTestRunner::class)
class NotificationBatchProcessorTest {

    private lateinit var batchProcessor: NotificationBatchProcessor
    private lateinit var mockQueueRepository: NotificationQueueRepository
    private lateinit var mockFcmSender: FCMSender
    private lateinit var mockNotificationBuilder: BatchNotificationBuilder
    
    private val testUserId = "test-user-123"
    private val testBatchKey = "social_notifications"

    @Before
    fun setup() {
        mockQueueRepository = mockk()
        mockFcmSender = mockk()
        mockNotificationBuilder = mockk()
        
        batchProcessor = NotificationBatchProcessor(
            queueRepository = mockQueueRepository,
            fcmSender = mockFcmSender,
            notificationBuilder = mockNotificationBuilder
        )
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    /**
     * Test processing a batch with a single notification
     */
    @Test
    fun `processBatch with single notification sends individual notification`() = runTest {
        // Given
        val singleNotification = createTestQueuedNotification(
            id = "notif-1",
            userId = testUserId,
            batchKey = testBatchKey,
            title = "New like",
            body = "John liked your post"
        )
        
        val pendingNotifications = listOf(singleNotification)
        
        coEvery { mockQueueRepository.getPending(testUserId) } returns flowOf(
            LiftrixResult.Success(pendingNotifications)
        )
        
        coEvery { mockFcmSender.sendSingle(any()) } returns LiftrixResult.Success(Unit)
        coEvery { mockQueueRepository.markSent(any()) } returns flowOf(LiftrixResult.Success(Unit))
        
        // When
        val result = batchProcessor.processBatch(testUserId)
        
        // Then
        assertTrue("Batch processing should succeed", result is LiftrixResult.Success)
        
        coVerify(exactly = 1) { mockFcmSender.sendSingle(singleNotification) }
        coVerify(exactly = 1) { mockQueueRepository.markSent("notif-1") }
        coVerify(exactly = 0) { mockNotificationBuilder.buildInboxStyleNotification(any(), any()) }
        coVerify(exactly = 0) { mockNotificationBuilder.buildSummaryNotification(any(), any()) }
    }

    /**
     * Test processing a batch with multiple notifications (2-4)
     */
    @Test
    fun `processBatch with multiple notifications sends inbox style notification`() = runTest {
        // Given
        val notifications = listOf(
            createTestQueuedNotification("notif-1", testUserId, testBatchKey, "Like", "John liked your post"),
            createTestQueuedNotification("notif-2", testUserId, testBatchKey, "Comment", "Alice commented on your post"),
            createTestQueuedNotification("notif-3", testUserId, testBatchKey, "Follow", "Bob followed you")
        )
        
        val mockInboxNotification = mockk<NotificationQueue>()
        
        coEvery { mockQueueRepository.getPending(testUserId) } returns flowOf(
            LiftrixResult.Success(notifications)
        )
        
        coEvery { mockNotificationBuilder.buildInboxStyleNotification(notifications, testUserId) } returns 
            LiftrixResult.Success(mockInboxNotification)
        
        coEvery { mockFcmSender.sendToUser(testUserId, mockInboxNotification) } returns 
            LiftrixResult.Success(Unit)
        
        coEvery { mockQueueRepository.markSent(any()) } returns flowOf(LiftrixResult.Success(Unit))
        
        // When
        val result = batchProcessor.processBatch(testUserId)
        
        // Then
        assertTrue("Batch processing should succeed", result is LiftrixResult.Success)
        
        coVerify(exactly = 1) { mockNotificationBuilder.buildInboxStyleNotification(notifications, testUserId) }
        coVerify(exactly = 1) { mockFcmSender.sendToUser(testUserId, mockInboxNotification) }
        coVerify(exactly = 3) { mockQueueRepository.markSent(any()) }
        coVerify(exactly = 0) { mockFcmSender.sendSingle(any()) }
        coVerify(exactly = 0) { mockNotificationBuilder.buildSummaryNotification(any(), any()) }
    }

    /**
     * Test processing a batch with many notifications (5+)
     */
    @Test
    fun `processBatch with many notifications sends summary notification`() = runTest {
        // Given
        val notifications = (1..7).map { i ->
            createTestQueuedNotification(
                "notif-$i", 
                testUserId, 
                testBatchKey, 
                "Title $i", 
                "Body $i"
            )
        }
        
        val mockSummaryNotification = mockk<NotificationQueue>()
        
        coEvery { mockQueueRepository.getPending(testUserId) } returns flowOf(
            LiftrixResult.Success(notifications)
        )
        
        coEvery { mockNotificationBuilder.buildSummaryNotification(notifications, testUserId) } returns 
            LiftrixResult.Success(mockSummaryNotification)
        
        coEvery { mockFcmSender.sendToUser(testUserId, mockSummaryNotification) } returns 
            LiftrixResult.Success(Unit)
        
        coEvery { mockQueueRepository.markSent(any()) } returns flowOf(LiftrixResult.Success(Unit))
        
        // When
        val result = batchProcessor.processBatch(testUserId)
        
        // Then
        assertTrue("Batch processing should succeed", result is LiftrixResult.Success)
        
        coVerify(exactly = 1) { mockNotificationBuilder.buildSummaryNotification(notifications, testUserId) }
        coVerify(exactly = 1) { mockFcmSender.sendToUser(testUserId, mockSummaryNotification) }
        coVerify(exactly = 7) { mockQueueRepository.markSent(any()) }
        coVerify(exactly = 0) { mockFcmSender.sendSingle(any()) }
        coVerify(exactly = 0) { mockNotificationBuilder.buildInboxStyleNotification(any(), any()) }
    }

    /**
     * Test processing notifications grouped by different batch keys
     */
    @Test
    fun `processBatch groups notifications by batch key correctly`() = runTest {
        // Given
        val socialNotifications = listOf(
            createTestQueuedNotification("notif-1", testUserId, "social", "Like", "John liked"),
            createTestQueuedNotification("notif-2", testUserId, "social", "Comment", "Alice commented")
        )
        
        val achievementNotifications = listOf(
            createTestQueuedNotification("notif-3", testUserId, "achievements", "PR", "New PR!"),
            createTestQueuedNotification("notif-4", testUserId, "achievements", "Streak", "10 day streak!")
        )
        
        val allNotifications = socialNotifications + achievementNotifications
        
        val mockSocialNotification = mockk<NotificationQueue>()
        val mockAchievementNotification = mockk<NotificationQueue>()
        
        coEvery { mockQueueRepository.getPending(testUserId) } returns flowOf(
            LiftrixResult.Success(allNotifications)
        )
        
        coEvery { 
            mockNotificationBuilder.buildInboxStyleNotification(socialNotifications, testUserId) 
        } returns LiftrixResult.Success(mockSocialNotification)
        
        coEvery { 
            mockNotificationBuilder.buildInboxStyleNotification(achievementNotifications, testUserId) 
        } returns LiftrixResult.Success(mockAchievementNotification)
        
        coEvery { mockFcmSender.sendToUser(testUserId, any()) } returns LiftrixResult.Success(Unit)
        coEvery { mockQueueRepository.markSent(any()) } returns flowOf(LiftrixResult.Success(Unit))
        
        // When
        val result = batchProcessor.processBatch(testUserId)
        
        // Then
        assertTrue("Batch processing should succeed", result is LiftrixResult.Success)
        
        // Verify two separate notifications were built and sent
        coVerify(exactly = 1) { 
            mockNotificationBuilder.buildInboxStyleNotification(socialNotifications, testUserId) 
        }
        coVerify(exactly = 1) { 
            mockNotificationBuilder.buildInboxStyleNotification(achievementNotifications, testUserId) 
        }
        coVerify(exactly = 2) { mockFcmSender.sendToUser(testUserId, any()) }
        coVerify(exactly = 4) { mockQueueRepository.markSent(any()) }
    }

    /**
     * Test processing empty notification queue
     */
    @Test
    fun `processBatch with empty queue returns success without processing`() = runTest {
        // Given
        coEvery { mockQueueRepository.getPending(testUserId) } returns flowOf(
            LiftrixResult.Success(emptyList())
        )
        
        // When
        val result = batchProcessor.processBatch(testUserId)
        
        // Then
        assertTrue("Batch processing should succeed for empty queue", result is LiftrixResult.Success)
        
        coVerify(exactly = 0) { mockFcmSender.sendSingle(any()) }
        coVerify(exactly = 0) { mockFcmSender.sendToUser(any(), any()) }
        coVerify(exactly = 0) { mockQueueRepository.markSent(any()) }
    }

    /**
     * Test error handling when queue retrieval fails
     */
    @Test
    fun `processBatch handles queue retrieval failure`() = runTest {
        // Given
        coEvery { mockQueueRepository.getPending(testUserId) } returns flowOf(
            LiftrixResult.Error(mockk {
                every { errorMessage } returns "Database connection failed"
            })
        )
        
        // When
        val result = batchProcessor.processBatch(testUserId)
        
        // Then
        assertTrue("Batch processing should fail when queue retrieval fails", result is LiftrixResult.Error)
        val error = (result as LiftrixResult.Error).error
        assertEquals("Error message should be propagated", "Database connection failed", error.errorMessage)
        
        coVerify(exactly = 0) { mockFcmSender.sendSingle(any()) }
        coVerify(exactly = 0) { mockFcmSender.sendToUser(any(), any()) }
    }

    /**
     * Test error handling when FCM sending fails
     */
    @Test
    fun `processBatch handles FCM sending failure`() = runTest {
        // Given
        val notification = createTestQueuedNotification("notif-1", testUserId, testBatchKey)
        
        coEvery { mockQueueRepository.getPending(testUserId) } returns flowOf(
            LiftrixResult.Success(listOf(notification))
        )
        
        coEvery { mockFcmSender.sendSingle(notification) } returns LiftrixResult.Error(mockk {
            every { errorMessage } returns "FCM send failed"
        })
        
        // When
        val result = batchProcessor.processBatch(testUserId)
        
        // Then
        assertTrue("Batch processing should fail when FCM sending fails", result is LiftrixResult.Error)
        
        // Verify notification was not marked as sent
        coVerify(exactly = 0) { mockQueueRepository.markSent(any()) }
    }

    /**
     * Test partial failure handling - some notifications succeed, others fail
     */
    @Test
    fun `processBatch handles partial failures correctly`() = runTest {
        // Given
        val notifications = listOf(
            createTestQueuedNotification("notif-1", testUserId, "batch1"),
            createTestQueuedNotification("notif-2", testUserId, "batch2")
        )
        
        coEvery { mockQueueRepository.getPending(testUserId) } returns flowOf(
            LiftrixResult.Success(notifications)
        )
        
        // First notification succeeds
        coEvery { mockFcmSender.sendSingle(notifications[0]) } returns LiftrixResult.Success(Unit)
        // Second notification fails
        coEvery { mockFcmSender.sendSingle(notifications[1]) } returns LiftrixResult.Error(mockk {
            every { errorMessage } returns "Send failed"
        })
        
        coEvery { mockQueueRepository.markSent("notif-1") } returns flowOf(LiftrixResult.Success(Unit))
        
        // When
        val result = batchProcessor.processBatch(testUserId)
        
        // Then
        assertTrue("Batch processing should handle partial failures", result is LiftrixResult.Error)
        
        // Verify only successful notification was marked as sent
        coVerify(exactly = 1) { mockQueueRepository.markSent("notif-1") }
        coVerify(exactly = 0) { mockQueueRepository.markSent("notif-2") }
    }

    /**
     * Test batch processing with scheduled notifications (timing logic)
     */
    @Test
    fun `processBatch only processes notifications scheduled for current time`() = runTest {
        // Given
        val currentTime = System.currentTimeMillis()
        val futureTime = currentTime + 60_000 // 1 minute in future
        
        val readyNotifications = listOf(
            createTestQueuedNotification("notif-1", testUserId, testBatchKey, scheduledFor = currentTime - 1000),
            createTestQueuedNotification("notif-2", testUserId, testBatchKey, scheduledFor = currentTime)
        )
        
        val futureNotifications = listOf(
            createTestQueuedNotification("notif-3", testUserId, testBatchKey, scheduledFor = futureTime)
        )
        
        // Mock the repository to return only ready notifications
        coEvery { mockQueueRepository.getPending(testUserId) } returns flowOf(
            LiftrixResult.Success(readyNotifications)
        )
        
        val mockInboxNotification = mockk<NotificationQueue>()
        
        coEvery { 
            mockNotificationBuilder.buildInboxStyleNotification(readyNotifications, testUserId) 
        } returns LiftrixResult.Success(mockInboxNotification)
        
        coEvery { mockFcmSender.sendToUser(testUserId, mockInboxNotification) } returns 
            LiftrixResult.Success(Unit)
        
        coEvery { mockQueueRepository.markSent(any()) } returns flowOf(LiftrixResult.Success(Unit))
        
        // When
        val result = batchProcessor.processBatch(testUserId)
        
        // Then
        assertTrue("Batch processing should succeed", result is LiftrixResult.Success)
        
        // Verify only ready notifications were processed
        coVerify(exactly = 1) { mockFcmSender.sendToUser(testUserId, mockInboxNotification) }
        coVerify(exactly = 2) { mockQueueRepository.markSent(any()) }
        
        // Verify the specific notifications that were marked as sent
        coVerify(exactly = 1) { mockQueueRepository.markSent("notif-1") }
        coVerify(exactly = 1) { mockQueueRepository.markSent("notif-2") }
        coVerify(exactly = 0) { mockQueueRepository.markSent("notif-3") }
    }

    /**
     * Test performance with large number of notifications
     */
    @Test
    fun `processBatch handles large number of notifications efficiently`() = runTest {
        // Given
        val largeNotificationList = (1..100).map { i ->
            createTestQueuedNotification("notif-$i", testUserId, testBatchKey)
        }
        
        val mockSummaryNotification = mockk<NotificationQueue>()
        
        coEvery { mockQueueRepository.getPending(testUserId) } returns flowOf(
            LiftrixResult.Success(largeNotificationList)
        )
        
        coEvery { 
            mockNotificationBuilder.buildSummaryNotification(largeNotificationList, testUserId) 
        } returns LiftrixResult.Success(mockSummaryNotification)
        
        coEvery { mockFcmSender.sendToUser(testUserId, mockSummaryNotification) } returns 
            LiftrixResult.Success(Unit)
        
        coEvery { mockQueueRepository.markSent(any()) } returns flowOf(LiftrixResult.Success(Unit))
        
        val startTime = System.currentTimeMillis()
        
        // When
        val result = batchProcessor.processBatch(testUserId)
        
        // Then
        val processingTime = System.currentTimeMillis() - startTime
        
        assertTrue("Batch processing should succeed", result is LiftrixResult.Success)
        assertTrue("Processing should complete within reasonable time", processingTime < 1000) // Less than 1 second
        
        // Verify all notifications were processed as a single summary
        coVerify(exactly = 1) { mockNotificationBuilder.buildSummaryNotification(largeNotificationList, testUserId) }
        coVerify(exactly = 1) { mockFcmSender.sendToUser(testUserId, mockSummaryNotification) }
        coVerify(exactly = 100) { mockQueueRepository.markSent(any()) }
    }

    /**
     * Test notification priority handling in batches
     */
    @Test
    fun `processBatch respects notification priorities`() = runTest {
        // Given
        val highPriorityNotifications = listOf(
            createTestQueuedNotification("high-1", testUserId, "high_priority", priority = "HIGH"),
            createTestQueuedNotification("high-2", testUserId, "high_priority", priority = "HIGH")
        )
        
        val normalPriorityNotifications = listOf(
            createTestQueuedNotification("normal-1", testUserId, "normal_priority", priority = "NORMAL"),
            createTestQueuedNotification("normal-2", testUserId, "normal_priority", priority = "NORMAL"),
            createTestQueuedNotification("normal-3", testUserId, "normal_priority", priority = "NORMAL")
        )
        
        val allNotifications = highPriorityNotifications + normalPriorityNotifications
        
        val mockHighPriorityNotification = mockk<NotificationQueue>()
        val mockNormalPriorityNotification = mockk<NotificationQueue>()
        
        coEvery { mockQueueRepository.getPending(testUserId) } returns flowOf(
            LiftrixResult.Success(allNotifications)
        )
        
        coEvery { 
            mockNotificationBuilder.buildInboxStyleNotification(highPriorityNotifications, testUserId) 
        } returns LiftrixResult.Success(mockHighPriorityNotification)
        
        coEvery { 
            mockNotificationBuilder.buildInboxStyleNotification(normalPriorityNotifications, testUserId) 
        } returns LiftrixResult.Success(mockNormalPriorityNotification)
        
        coEvery { mockFcmSender.sendToUser(testUserId, any()) } returns LiftrixResult.Success(Unit)
        coEvery { mockQueueRepository.markSent(any()) } returns flowOf(LiftrixResult.Success(Unit))
        
        // When
        val result = batchProcessor.processBatch(testUserId)
        
        // Then
        assertTrue("Batch processing should succeed", result is LiftrixResult.Success)
        
        // Verify both priority groups were processed separately
        coVerify(exactly = 1) { 
            mockNotificationBuilder.buildInboxStyleNotification(highPriorityNotifications, testUserId) 
        }
        coVerify(exactly = 1) { 
            mockNotificationBuilder.buildInboxStyleNotification(normalPriorityNotifications, testUserId) 
        }
        coVerify(exactly = 2) { mockFcmSender.sendToUser(testUserId, any()) }
        coVerify(exactly = 5) { mockQueueRepository.markSent(any()) }
    }

    /**
     * Helper function to create test queued notifications
     */
    private fun createTestQueuedNotification(
        id: String,
        userId: String,
        batchKey: String,
        title: String = "Test Title",
        body: String = "Test Body",
        priority: String = "NORMAL",
        scheduledFor: Long = System.currentTimeMillis() - 1000
    ): QueuedNotification {
        return QueuedNotification(
            id = id,
            userId = userId,
            type = "TEST_TYPE",
            title = title,
            body = body,
            data = "{}",
            priority = priority,
            channelId = "test_channel",
            batchKey = batchKey,
            canBatch = true,
            scheduledFor = scheduledFor,
            expiresAt = scheduledFor + 24 * 60 * 60 * 1000, // 24 hours later
            status = "PENDING",
            sentAt = null,
            failureReason = null,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Helper function to create mock batch responses
     */
    private fun createMockBatchResponse(
        successCount: Int,
        failureCount: Int = 0
    ): BatchResponse {
        val responses = mutableListOf<SendResponse>()
        
        // Add successful responses
        repeat(successCount) {
            responses.add(mockk<SendResponse> {
                every { isSuccessful } returns true
                every { messageId } returns "msg-$it"
            })
        }
        
        // Add failed responses
        repeat(failureCount) {
            responses.add(mockk<SendResponse> {
                every { isSuccessful } returns false
                every { exception } returns RuntimeException("Send failed")
            })
        }
        
        return mockk<BatchResponse> {
            every { successCount } returns successCount
            every { failureCount } returns failureCount
            every { responses } returns responses
        }
    }
}