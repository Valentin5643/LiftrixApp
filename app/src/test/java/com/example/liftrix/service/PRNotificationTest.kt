package com.example.liftrix.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.GymBuddy
import com.example.liftrix.domain.model.workout.Workout
import com.example.liftrix.domain.model.workout.Exercise
import com.example.liftrix.domain.model.workout.ExerciseSet
import com.example.liftrix.domain.repository.social.GymBuddyRepository
import com.example.liftrix.domain.repository.social.PRNotificationRepository
import com.example.liftrix.domain.service.FCMService
import com.example.liftrix.domain.usecase.social.SendPRNotificationUseCase
import com.example.liftrix.domain.model.social.PRNotification
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for PR notification system functionality.
 * 
 * Tests the complete PR notification flow including:
 * - PR detection in completed workouts
 * - Cooldown enforcement (max 1 notification per day per buddy)
 * - Notification delivery to gym buddies
 * - FCM message formatting and sending
 * - Error handling for notification failures
 * - Bulk notification processing for multiple buddies
 */
class PRNotificationTest {

    private lateinit var prNotificationRepository: PRNotificationRepository
    private lateinit var gymBuddyRepository: GymBuddyRepository
    private lateinit var fcmService: FCMService
    private lateinit var sendPRNotificationUseCase: SendPRNotificationUseCase

    private val testUserId = "test-user-123"
    private val testBuddyId1 = "buddy-1"
    private val testBuddyId2 = "buddy-2"
    private val testWorkoutId = "workout-123"

    @Before
    fun setup() {
        prNotificationRepository = mockk()
        gymBuddyRepository = mockk()
        fcmService = mockk()
        
        sendPRNotificationUseCase = SendPRNotificationUseCase(
            prRepository = prNotificationRepository,
            gymBuddyRepository = gymBuddyRepository,
            fcmService = fcmService
        )
    }

    @After
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `sendPRNotification - successful PR detection and notification`() = runTest {
        // Given
        val workout = createWorkoutWithPR()
        val gymBuddies = listOf(
            createGymBuddy(testBuddyId1, "Buddy One", "fcm-token-1"),
            createGymBuddy(testBuddyId2, "Buddy Two", "fcm-token-2")
        )

        mockSuccessfulNotificationFlow(gymBuddies)

        // When
        val result = sendPRNotificationUseCase(workout, testUserId)

        // Then
        assertThat(result).isInstanceOf(LiftrixResult.Success::class.java)
        val notifications = (result as LiftrixResult.Success).data
        assertThat(notifications).hasSize(2)

        // Verify notification content
        notifications.forEach { notification ->
            assertThat(notification.fromUserId).isEqualTo(testUserId)
            assertThat(notification.workoutId).isEqualTo(testWorkoutId)
            assertThat(notification.exerciseName).isEqualTo("Bench Press")
            assertThat(notification.prWeight).isEqualTo(225.0f)
            assertThat(notification.prReps).isEqualTo(5)
        }

        // Verify repository interactions
        verify(exactly = 2) { prRepository.create(any()) }
        verify(exactly = 2) { fcmService.sendDataMessage(any(), any(), any()) }
    }

    @Test
    fun `sendPRNotification - no PRs in workout returns empty list`() = runTest {
        // Given
        val workoutWithoutPR = createWorkoutWithoutPR()
        
        // When
        val result = sendPRNotificationUseCase(workoutWithoutPR, testUserId)

        // Then
        assertThat(result).isInstanceOf(LiftrixResult.Success::class.java)
        val notifications = (result as LiftrixResult.Success).data
        assertThat(notifications).isEmpty()

        // Verify no notifications were sent
        verify(exactly = 0) { prNotificationRepository.create(any()) }
        verify(exactly = 0) { fcmService.sendDataMessage(any(), any(), any()) }
    }

    @Test
    fun `sendPRNotification - respects daily cooldown per buddy`() = runTest {
        // Given
        val workout = createWorkoutWithPR()
        val gymBuddies = listOf(
            createGymBuddy(testBuddyId1, "Buddy One", "fcm-token-1"),
            createGymBuddy(testBuddyId2, "Buddy Two", "fcm-token-2")
        )

        coEvery { gymBuddyRepository.getGymBuddies(testUserId) } returns LiftrixResult.Success(gymBuddies)
        
        // Mock cooldown: buddy1 already received notification today, buddy2 hasn't
        val todayCooldownKey1 = "$testUserId:$testBuddyId1:${LocalDate.now()}"
        val todayCooldownKey2 = "$testUserId:$testBuddyId2:${LocalDate.now()}"
        
        coEvery { prNotificationRepository.hasSentToday(todayCooldownKey1) } returns true
        coEvery { prNotificationRepository.hasSentToday(todayCooldownKey2) } returns false
        
        coEvery { prNotificationRepository.create(any()) } returns LiftrixResult.Success(Unit)
        coEvery { fcmService.sendDataMessage(any(), any(), any()) } returns LiftrixResult.Success(Unit)

        // When
        val result = sendPRNotificationUseCase(workout, testUserId)

        // Then
        assertThat(result).isInstanceOf(LiftrixResult.Success::class.java)
        val notifications = (result as LiftrixResult.Success).data
        assertThat(notifications).hasSize(1) // Only buddy2 should receive notification

        assertThat(notifications[0].toUserId).isEqualTo(testBuddyId2)

        // Verify only one notification was sent
        verify(exactly = 1) { prNotificationRepository.create(any()) }
        verify(exactly = 1) { fcmService.sendDataMessage("fcm-token-2", any(), any()) }
    }

    @Test
    fun `sendPRNotification - handles FCM delivery failure gracefully`() = runTest {
        // Given
        val workout = createWorkoutWithPR()
        val gymBuddies = listOf(createGymBuddy(testBuddyId1, "Buddy One", "invalid-token"))

        coEvery { gymBuddyRepository.getGymBuddies(testUserId) } returns LiftrixResult.Success(gymBuddies)
        coEvery { prNotificationRepository.hasSentToday(any()) } returns false
        coEvery { prNotificationRepository.create(any()) } returns LiftrixResult.Success(Unit)
        
        // FCM fails
        coEvery { 
            fcmService.sendDataMessage(any(), any(), any()) 
        } returns LiftrixResult.Error(Exception("FCM delivery failed"))

        // When
        val result = sendPRNotificationUseCase(workout, testUserId)

        // Then - Should still succeed as notification was stored, even if FCM failed
        assertThat(result).isInstanceOf(LiftrixResult.Success::class.java)
        
        // Verify notification was stored despite FCM failure
        verify(exactly = 1) { prNotificationRepository.create(any()) }
        verify(exactly = 1) { fcmService.sendDataMessage(any(), any(), any()) }
    }

    @Test
    fun `sendPRNotification - selects most impressive PR when multiple PRs exist`() = runTest {
        // Given
        val workoutWithMultiplePRs = createWorkoutWithMultiplePRs()
        val gymBuddies = listOf(createGymBuddy(testBuddyId1, "Buddy One", "fcm-token-1"))

        mockSuccessfulNotificationFlow(gymBuddies)

        // When
        val result = sendPRNotificationUseCase(workoutWithMultiplePRs, testUserId)

        // Then
        assertThat(result).isInstanceOf(LiftrixResult.Success::class.java)
        val notifications = (result as LiftrixResult.Success).data
        assertThat(notifications).hasSize(1)

        // Should send the most impressive PR (Deadlift with higher weight)
        val notification = notifications[0]
        assertThat(notification.exerciseName).isEqualTo("Deadlift")
        assertThat(notification.prWeight).isEqualTo(405.0f)
    }

    @Test
    fun `sendPRNotification - handles repository errors gracefully`() = runTest {
        // Given
        val workout = createWorkoutWithPR()
        
        coEvery { 
            gymBuddyRepository.getGymBuddies(testUserId) 
        } returns LiftrixResult.Error(Exception("Database error"))

        // When
        val result = sendPRNotificationUseCase(workout, testUserId)

        // Then
        assertThat(result).isInstanceOf(LiftrixResult.Error::class.java)
        
        // Verify no notifications were attempted
        verify(exactly = 0) { prNotificationRepository.create(any()) }
        verify(exactly = 0) { fcmService.sendDataMessage(any(), any(), any()) }
    }

    @Test
    fun `sendPRNotification - validates PR notification content format`() = runTest {
        // Given
        val workout = createWorkoutWithPR()
        val gymBuddies = listOf(createGymBuddy(testBuddyId1, "Buddy One", "fcm-token-1"))

        mockSuccessfulNotificationFlow(gymBuddies)

        // When
        val result = sendPRNotificationUseCase(workout, testUserId)

        // Then
        verify { 
            fcmService.sendDataMessage(
                token = "fcm-token-1",
                data = match { data ->
                    data["type"] == "GYM_BUDDY_PR" &&
                    data["fromUser"] == testUserId &&
                    data.containsKey("prDetail")
                },
                priority = "high"
            )
        }
    }

    @Test
    fun `sendPRNotification - enforces buddy eligibility for notifications`() = runTest {
        // Given
        val workout = createWorkoutWithPR()
        val ineligibleBuddy = createGymBuddy(
            testBuddyId1, 
            "Ineligible Buddy", 
            "fcm-token-1",
            notificationCooldownHours = 0 // Notifications disabled
        )

        coEvery { gymBuddyRepository.getGymBuddies(testUserId) } returns LiftrixResult.Success(listOf(ineligibleBuddy))

        // When
        val result = sendPRNotificationUseCase(workout, testUserId)

        // Then
        assertThat(result).isInstanceOf(LiftrixResult.Success::class.java)
        val notifications = (result as LiftrixResult.Success).data
        assertThat(notifications).isEmpty() // No notifications should be sent to ineligible buddy

        verify(exactly = 0) { prNotificationRepository.create(any()) }
    }

    // Helper methods for creating test data

    private fun createWorkoutWithPR(): Workout {
        return Workout(
            id = testWorkoutId,
            userId = testUserId,
            name = "Push Day",
            exercises = listOf(
                Exercise(
                    id = "exercise-1",
                    name = "Bench Press",
                    sets = listOf(
                        ExerciseSet(
                            id = "set-1",
                            reps = 5,
                            weight = 225.0f,
                            isPersonalRecord = true,
                            previousBest = 205.0f
                        )
                    )
                )
            ),
            startedAt = System.currentTimeMillis() - 3600000, // 1 hour ago
            completedAt = System.currentTimeMillis()
        )
    }

    private fun createWorkoutWithoutPR(): Workout {
        return Workout(
            id = testWorkoutId,
            userId = testUserId,
            name = "Regular Day",
            exercises = listOf(
                Exercise(
                    id = "exercise-1",
                    name = "Bench Press",
                    sets = listOf(
                        ExerciseSet(
                            id = "set-1",
                            reps = 5,
                            weight = 185.0f,
                            isPersonalRecord = false
                        )
                    )
                )
            ),
            startedAt = System.currentTimeMillis() - 3600000,
            completedAt = System.currentTimeMillis()
        )
    }

    private fun createWorkoutWithMultiplePRs(): Workout {
        return Workout(
            id = testWorkoutId,
            userId = testUserId,
            name = "PR Day",
            exercises = listOf(
                Exercise(
                    id = "exercise-1",
                    name = "Bench Press",
                    sets = listOf(
                        ExerciseSet(
                            id = "set-1",
                            reps = 5,
                            weight = 225.0f,
                            isPersonalRecord = true,
                            previousBest = 205.0f
                        )
                    )
                ),
                Exercise(
                    id = "exercise-2",
                    name = "Deadlift",
                    sets = listOf(
                        ExerciseSet(
                            id = "set-2",
                            reps = 1,
                            weight = 405.0f,
                            isPersonalRecord = true,
                            previousBest = 385.0f
                        )
                    )
                )
            ),
            startedAt = System.currentTimeMillis() - 3600000,
            completedAt = System.currentTimeMillis()
        )
    }

    private fun createGymBuddy(
        buddyId: String, 
        displayName: String, 
        fcmToken: String,
        notificationCooldownHours: Int = 24
    ): GymBuddy {
        return GymBuddy(
            id = "relation-$buddyId",
            userId = testUserId,
            buddyId = buddyId,
            buddyNickname = displayName,
            createdAt = System.currentTimeMillis() - 86400000, // 1 day ago
            lastPrNotificationSent = null,
            notificationCooldownHours = notificationCooldownHours,
            pairedViaQr = true,
            pairingLocation = "Test Gym"
        ).apply {
            // Mock FCM token (would typically be stored in user profile)
            // For testing purposes, we simulate this data
        }
    }

    private fun mockSuccessfulNotificationFlow(gymBuddies: List<GymBuddy>) {
        coEvery { gymBuddyRepository.getGymBuddies(testUserId) } returns LiftrixResult.Success(gymBuddies)
        coEvery { prNotificationRepository.hasSentToday(any()) } returns false
        coEvery { prNotificationRepository.create(any()) } returns LiftrixResult.Success(Unit)
        coEvery { fcmService.sendDataMessage(any(), any(), any()) } returns LiftrixResult.Success(Unit)
    }

    // Mock data classes for testing (these would be real domain models in the actual implementation)
    private data class Workout(
        val id: String,
        val userId: String,
        val name: String,
        val exercises: List<Exercise>,
        val startedAt: Long,
        val completedAt: Long
    )

    private data class Exercise(
        val id: String,
        val name: String,
        val sets: List<ExerciseSet>
    )

    private data class ExerciseSet(
        val id: String,
        val reps: Int,
        val weight: Float,
        val isPersonalRecord: Boolean,
        val previousBest: Float? = null
    )
}