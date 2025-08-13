package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.model.social.ProfileVisibility
import com.example.liftrix.domain.model.social.WorkoutVisibility
import com.example.liftrix.domain.repository.social.SocialPrivacySettingsRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for UpdateSocialPrivacySettingsUseCase.
 * Tests privacy settings updates with user authentication and validation.
 * Part of social infrastructure testing from SPEC-20250113-social-infrastructure.
 */
class UpdateSocialPrivacySettingsUseCaseTest {

    private lateinit var useCase: UpdateSocialPrivacySettingsUseCase
    private val repository: SocialPrivacySettingsRepository = mockk()
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase = mockk()

    private val testUserId = "user123"
    private val testSettings = SocialPrivacySettings(
        userId = "original_user_id", // Will be overridden
        socialEnabled = true,
        profileVisibility = ProfileVisibility.PRIVATE,
        allowFollowRequests = true,
        workoutSharingEnabled = false,
        gymBuddiesEnabled = false,
        communityParticipation = false,
        challengeParticipation = false,
        routineSharingEnabled = false,
        defaultWorkoutVisibility = WorkoutVisibility.PRIVATE,
        showWorkoutStats = true,
        showAchievements = true,
        showWorkoutStreak = true,
        hideFromSuggestions = false,
        hideFromSearch = false,
        notificationSettings = emptyMap(),
        updatedAt = System.currentTimeMillis()
    )

    @Before
    fun setup() {
        useCase = UpdateSocialPrivacySettingsUseCase(
            repository = repository,
            getCurrentUserIdUseCase = getCurrentUserIdUseCase
        )
    }

    @Test
    fun `updatePrivacySettings succeeds with valid data`() = runTest {
        // Arrange
        coEvery { getCurrentUserIdUseCase() } returns testUserId
        val expectedSettings = testSettings.copy(userId = testUserId)
        coEvery { repository.updatePrivacySettings(expectedSettings) } returns LiftrixResult.success(expectedSettings)

        // Act
        val result = useCase(testSettings)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(expectedSettings, result.getOrNull())
        
        coVerify { 
            getCurrentUserIdUseCase()
            repository.updatePrivacySettings(expectedSettings)
        }
    }

    @Test
    fun `updatePrivacySettings fails when user not authenticated`() = runTest {
        // Arrange
        coEvery { getCurrentUserIdUseCase() } returns null

        // Act
        val result = useCase(testSettings)

        // Assert
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as LiftrixError.BusinessLogicError
        assertEquals("Failed to update privacy settings", error.message)
        assertEquals("UPDATE_PRIVACY_SETTINGS", error.code)
        
        coVerify(exactly = 0) { repository.updatePrivacySettings(any()) }
    }

    @Test
    fun `updatePrivacySettings overrides userId with current user`() = runTest {
        // Arrange
        coEvery { getCurrentUserIdUseCase() } returns testUserId
        
        var capturedSettings: SocialPrivacySettings? = null
        val settingsSlot = slot<SocialPrivacySettings>()
        coEvery { repository.updatePrivacySettings(capture(settingsSlot)) } answers {
            capturedSettings = firstArg()
            LiftrixResult.success(capturedSettings!!)
        }

        // Act
        val result = useCase(testSettings)

        // Assert
        assertTrue(result.isSuccess)
        assertNotNull(capturedSettings)
        assertEquals(testUserId, capturedSettings!!.userId)
        // Verify other fields are preserved
        assertEquals(testSettings.socialEnabled, capturedSettings!!.socialEnabled)
        assertEquals(testSettings.profileVisibility, capturedSettings!!.profileVisibility)
        assertEquals(testSettings.allowFollowRequests, capturedSettings!!.allowFollowRequests)
    }

    @Test
    fun `updatePrivacySettings fails when repository fails`() = runTest {
        // Arrange
        coEvery { getCurrentUserIdUseCase() } returns testUserId
        val expectedSettings = testSettings.copy(userId = testUserId)
        coEvery { repository.updatePrivacySettings(expectedSettings) } returns LiftrixResult.failure(
            LiftrixError.DatabaseError("Database connection failed")
        )

        // Act
        val result = useCase(testSettings)

        // Assert
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as LiftrixError.BusinessLogicError
        assertEquals("Failed to update privacy settings", error.message)
        
        coVerify { repository.updatePrivacySettings(expectedSettings) }
    }

    @Test
    fun `updatePrivacySettings preserves all privacy settings fields`() = runTest {
        // Arrange
        val complexSettings = SocialPrivacySettings(
            userId = "will_be_overridden",
            socialEnabled = true,
            profileVisibility = ProfileVisibility.PUBLIC,
            allowFollowRequests = false,
            workoutSharingEnabled = true,
            gymBuddiesEnabled = true,
            communityParticipation = true,
            challengeParticipation = true,
            routineSharingEnabled = true,
            defaultWorkoutVisibility = WorkoutVisibility.FOLLOWERS,
            showWorkoutStats = false,
            showAchievements = false,
            showWorkoutStreak = false,
            hideFromSuggestions = true,
            hideFromSearch = true,
            notificationSettings = mapOf("follow_requests" to true, "workout_likes" to false),
            updatedAt = 1234567890L
        )
        
        coEvery { getCurrentUserIdUseCase() } returns testUserId
        
        var capturedSettings: SocialPrivacySettings? = null
        val settingsSlot2 = slot<SocialPrivacySettings>()
        coEvery { repository.updatePrivacySettings(capture(settingsSlot2)) } answers {
            capturedSettings = firstArg()
            LiftrixResult.success(capturedSettings!!)
        }

        // Act
        val result = useCase(complexSettings)

        // Assert
        assertTrue(result.isSuccess)
        assertNotNull(capturedSettings)
        
        with(capturedSettings!!) {
            assertEquals(testUserId, userId) // Overridden
            assertEquals(complexSettings.socialEnabled, socialEnabled)
            assertEquals(complexSettings.profileVisibility, profileVisibility)
            assertEquals(complexSettings.allowFollowRequests, allowFollowRequests)
            assertEquals(complexSettings.workoutSharingEnabled, workoutSharingEnabled)
            assertEquals(complexSettings.gymBuddiesEnabled, gymBuddiesEnabled)
            assertEquals(complexSettings.communityParticipation, communityParticipation)
            assertEquals(complexSettings.challengeParticipation, challengeParticipation)
            assertEquals(complexSettings.routineSharingEnabled, routineSharingEnabled)
            assertEquals(complexSettings.defaultWorkoutVisibility, defaultWorkoutVisibility)
            assertEquals(complexSettings.showWorkoutStats, showWorkoutStats)
            assertEquals(complexSettings.showAchievements, showAchievements)
            assertEquals(complexSettings.showWorkoutStreak, showWorkoutStreak)
            assertEquals(complexSettings.hideFromSuggestions, hideFromSuggestions)
            assertEquals(complexSettings.hideFromSearch, hideFromSearch)
            assertEquals(complexSettings.notificationSettings, notificationSettings)
            assertEquals(complexSettings.updatedAt, updatedAt)
        }
    }

    @Test
    fun `updatePrivacySettings handles network error gracefully`() = runTest {
        // Arrange
        coEvery { getCurrentUserIdUseCase() } returns testUserId
        val expectedSettings = testSettings.copy(userId = testUserId)
        coEvery { repository.updatePrivacySettings(expectedSettings) } returns LiftrixResult.failure(
            LiftrixError.NetworkError("No internet connection")
        )

        // Act
        val result = useCase(testSettings)

        // Assert
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as LiftrixError.BusinessLogicError
        assertTrue(error.analyticsContext.containsKey("error"))
        assertEquals("No internet connection", error.analyticsContext["error"])
    }

    @Test
    fun `updatePrivacySettings includes analytics context on error`() = runTest {
        // Arrange
        coEvery { getCurrentUserIdUseCase() } returns null

        // Act
        val result = useCase(testSettings)

        // Assert
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as LiftrixError.BusinessLogicError
        assertTrue(error.analyticsContext.containsKey("error"))
        assertEquals("User not authenticated", error.analyticsContext["error"])
    }
}