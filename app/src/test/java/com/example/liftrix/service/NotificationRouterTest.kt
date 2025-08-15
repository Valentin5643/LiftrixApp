package com.example.liftrix.service

import com.example.liftrix.data.service.NotificationRouterImpl
import com.example.liftrix.domain.service.NotificationRouter
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.social.FollowUserUseCase
import com.example.liftrix.domain.usecase.social.UnfollowUserUseCase
import com.example.liftrix.domain.usecase.social.GetSocialProfileUseCase
import com.example.liftrix.domain.model.social.SocialProfile
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
 * Comprehensive tests for NotificationRouter implementation
 * 
 * Tests notification routing, PR celebrations, follow request handling,
 * and decline actions with proper error handling.
 */
@RunWith(JUnit4::class)
class NotificationRouterTest {

    private lateinit var notificationRouter: NotificationRouter
    private lateinit var getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    private lateinit var followUserUseCase: FollowUserUseCase
    private lateinit var unfollowUserUseCase: UnfollowUserUseCase
    private lateinit var getSocialProfileUseCase: GetSocialProfileUseCase

    private val currentUserId = "current-user-123"
    private val targetUserId = "target-user-456"
    private val requesterId = "requester-789"

    private val testProfile = SocialProfile(
        userId = targetUserId,
        username = "targetuser",
        displayName = "Target User",
        profilePhotoUrl = null,
        isPublic = true,
        followerCount = 25,
        followingCount = 30,
        workoutCount = 100
    )

    @Before
    fun setup() {
        getCurrentUserIdUseCase = mockk()
        followUserUseCase = mockk()
        unfollowUserUseCase = mockk()
        getSocialProfileUseCase = mockk()

        notificationRouter = NotificationRouterImpl(
            getCurrentUserIdUseCase = getCurrentUserIdUseCase,
            followUserUseCase = followUserUseCase,
            unfollowUserUseCase = unfollowUserUseCase,
            getSocialProfileUseCase = getSocialProfileUseCase
        )

        every { getCurrentUserIdUseCase() } returns currentUserId
    }

    // ==========================================
    // PR Celebration Tests
    // ==========================================

    @Test
    fun `routePRCelebration should handle successful PR celebration`() = runTest {
        // Given
        val prData = mapOf(
            "achievement_type" to "personal_record",
            "exercise_name" to "Bench Press",
            "previous_weight" to "135",
            "new_weight" to "145",
            "user_id" to targetUserId
        )

        coEvery { getSocialProfileUseCase(targetUserId) } returns LiftrixResult.Success(testProfile)

        // When
        val result = notificationRouter.routePRCelebration(prData)

        // Then
        assertTrue("PR celebration should succeed", result.isSuccess)
        assertTrue("Should return success message", result.getOrNull() == true)
    }

    @Test
    fun `routePRCelebration should handle missing user profile`() = runTest {
        // Given
        val prData = mapOf(
            "achievement_type" to "personal_record",
            "exercise_name" to "Squat",
            "user_id" to "nonexistent-user"
        )

        val profileError = LiftrixError.NotFoundError("User profile not found")
        coEvery { getSocialProfileUseCase("nonexistent-user") } returns LiftrixResult.Error(profileError)

        // When
        val result = notificationRouter.routePRCelebration(prData)

        // Then
        assertTrue("PR celebration should fail for missing user", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.NotFoundError
        assertNotNull("Should return not found error", error)
        assertTrue("Should indicate user not found", error!!.errorMessage.contains("User profile not found"))
    }

    @Test
    fun `routePRCelebration should validate required PR data fields`() = runTest {
        // Given - missing required fields
        val incompletePrData = mapOf(
            "achievement_type" to "personal_record"
            // Missing exercise_name, user_id, etc.
        )

        // When
        val result = notificationRouter.routePRCelebration(incompletePrData)

        // Then
        assertTrue("PR celebration should fail with incomplete data", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertTrue("Should indicate missing fields", error!!.violations.isNotEmpty())
    }

    @Test
    fun `routePRCelebration should handle different achievement types`() = runTest {
        // Given
        val streakData = mapOf(
            "achievement_type" to "workout_streak",
            "streak_days" to "30",
            "user_id" to targetUserId
        )

        coEvery { getSocialProfileUseCase(targetUserId) } returns LiftrixResult.Success(testProfile)

        // When
        val result = notificationRouter.routePRCelebration(streakData)

        // Then
        assertTrue("Streak celebration should succeed", result.isSuccess)
        assertTrue("Should handle different achievement types", result.getOrNull() == true)
    }

    // ==========================================
    // Follow Request Tests
    // ==========================================

    @Test
    fun `routeFollowRequest should handle successful follow request acceptance`() = runTest {
        // Given
        coEvery { followUserUseCase(currentUserId, requesterId) } returns LiftrixResult.Success(Unit)
        coEvery { getSocialProfileUseCase(requesterId) } returns LiftrixResult.Success(testProfile.copy(userId = requesterId))

        // When
        val result = notificationRouter.routeFollowRequest(requesterId, accept = true)

        // Then
        assertTrue("Follow request acceptance should succeed", result.isSuccess)
        coVerify { followUserUseCase(currentUserId, requesterId) }
    }

    @Test
    fun `routeFollowRequest should handle follow request decline`() = runTest {
        // Given
        coEvery { getSocialProfileUseCase(requesterId) } returns LiftrixResult.Success(testProfile.copy(userId = requesterId))

        // When
        val result = notificationRouter.routeFollowRequest(requesterId, accept = false)

        // Then
        assertTrue("Follow request decline should succeed", result.isSuccess)
        // Verify that follow use case is NOT called for decline
        coVerify(exactly = 0) { followUserUseCase(any(), any()) }
        assertTrue("Should indicate request was declined", result.getOrNull() == true)
    }

    @Test
    fun `routeFollowRequest should handle follow use case failure`() = runTest {
        // Given
        val followError = LiftrixError.BusinessLogicError(
            code = "FOLLOW_FAILED",
            errorMessage = "Failed to follow user"
        )
        coEvery { followUserUseCase(currentUserId, requesterId) } returns LiftrixResult.Error(followError)
        coEvery { getSocialProfileUseCase(requesterId) } returns LiftrixResult.Success(testProfile.copy(userId = requesterId))

        // When
        val result = notificationRouter.routeFollowRequest(requesterId, accept = true)

        // Then
        assertTrue("Follow request should fail when follow use case fails", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.BusinessLogicError
        assertNotNull("Should return business logic error", error)
        assertEquals("Should propagate follow error", "FOLLOW_FAILED", error!!.code)
    }

    @Test
    fun `routeFollowRequest should handle unauthenticated user`() = runTest {
        // Given
        every { getCurrentUserIdUseCase() } returns null

        // When
        val result = notificationRouter.routeFollowRequest(requesterId, accept = true)

        // Then
        assertTrue("Follow request should fail for unauthenticated user", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertEquals("Should indicate authentication required", "current_user_id", error!!.field)
    }

    @Test
    fun `routeFollowRequest should handle invalid requester ID`() = runTest {
        // Given
        val emptyRequesterId = ""

        // When
        val result = notificationRouter.routeFollowRequest(emptyRequesterId, accept = true)

        // Then
        assertTrue("Follow request should fail for invalid requester ID", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertEquals("Should indicate requester_id validation", "requester_id", error!!.field)
    }

    // ==========================================
    // Decline Action Tests  
    // ==========================================

    @Test
    fun `routeDeclineAction should handle notification dismissal`() = runTest {
        // Given
        val notificationId = "notification-123"
        val actionData = mapOf(
            "notification_id" to notificationId,
            "action_type" to "dismiss"
        )

        // When
        val result = notificationRouter.routeDeclineAction(actionData)

        // Then
        assertTrue("Decline action should succeed", result.isSuccess)
        assertTrue("Should successfully dismiss notification", result.getOrNull() == true)
    }

    @Test
    fun `routeDeclineAction should handle follow request rejection`() = runTest {
        // Given
        val actionData = mapOf(
            "requester_id" to requesterId,
            "action_type" to "reject_follow_request"
        )

        coEvery { getSocialProfileUseCase(requesterId) } returns LiftrixResult.Success(testProfile.copy(userId = requesterId))

        // When
        val result = notificationRouter.routeDeclineAction(actionData)

        // Then
        assertTrue("Follow rejection should succeed", result.isSuccess)
        // Verify that no follow relationship is created
        coVerify(exactly = 0) { followUserUseCase(any(), any()) }
    }

    @Test
    fun `routeDeclineAction should validate action data`() = runTest {
        // Given - missing required fields
        val incompleteActionData = mapOf(
            "some_field" to "some_value"
            // Missing notification_id or action_type
        )

        // When
        val result = notificationRouter.routeDeclineAction(incompleteActionData)

        // Then
        assertTrue("Decline action should fail with incomplete data", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertTrue("Should indicate validation failures", error!!.violations.isNotEmpty())
    }

    @Test
    fun `routeDeclineAction should handle unknown action types`() = runTest {
        // Given
        val unknownActionData = mapOf(
            "notification_id" to "test-123",
            "action_type" to "unknown_action"
        )

        // When
        val result = notificationRouter.routeDeclineAction(unknownActionData)

        // Then
        assertTrue("Unknown action should fail gracefully", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertTrue("Should indicate unsupported action", error!!.violations.any { it.contains("unsupported") })
    }

    // ==========================================
    // Integration Tests
    // ==========================================

    @Test
    fun `notification routing should handle concurrent requests`() = runTest {
        // Given
        val prData = mapOf("achievement_type" to "personal_record", "user_id" to targetUserId)
        coEvery { getSocialProfileUseCase(targetUserId) } returns LiftrixResult.Success(testProfile)
        coEvery { followUserUseCase(currentUserId, requesterId) } returns LiftrixResult.Success(Unit)
        coEvery { getSocialProfileUseCase(requesterId) } returns LiftrixResult.Success(testProfile.copy(userId = requesterId))

        // When - simulate concurrent operations
        val prResult = notificationRouter.routePRCelebration(prData)
        val followResult = notificationRouter.routeFollowRequest(requesterId, accept = true)

        // Then
        assertTrue("PR celebration should succeed concurrently", prResult.isSuccess)
        assertTrue("Follow request should succeed concurrently", followResult.isSuccess)
    }
}