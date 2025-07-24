package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserAchievement
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.FitnessLevel
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.PublicWorkoutStats
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for GetPublicProfileUseCase
 * 
 * Tests profile retrieval, privacy filtering, connection status handling,
 * profile view tracking, and error scenarios for public profile viewing.
 */
class GetPublicProfileUseCaseTest {

    private lateinit var getPublicProfileUseCase: GetPublicProfileUseCase
    private lateinit var userSearchRepository: UserSearchRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var errorHandler: ErrorHandler

    private val currentUserId = "current_user_123"
    private val targetUserId = "target_user_456"

    @Before
    fun setup() {
        userSearchRepository = mockk()
        authRepository = mockk()
        errorHandler = mockk()
        
        getPublicProfileUseCase = GetPublicProfileUseCase(
            userSearchRepository = userSearchRepository,
            authRepository = authRepository,
            errorHandler = errorHandler
        )

        // Mock authentication
        coEvery { authRepository.getCurrentUserId() } returns currentUserId
    }

    @Test
    fun `getPublicProfile with valid user returns profile successfully`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = targetUserId,
            trackView = true
        )
        
        val mockProfile = createMockPublicProfile(
            userId = targetUserId,
            displayName = "John Doe",
            connectionStatus = ConnectionStatus.NONE
        )
        
        coEvery { 
            userSearchRepository.getPublicProfile(targetUserId, currentUserId)
        } returns LiftrixResult.success(mockProfile)
        
        coEvery { 
            userSearchRepository.trackProfileView(targetUserId, currentUserId)
        } returns LiftrixResult.success(Unit)

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val profileResult = result.getOrThrow()
        assertEquals(mockProfile, profileResult.profile)
        assertFalse(profileResult.isOwnProfile)
        assertTrue(profileResult.canInteract)
        
        // Verify profile view was tracked
        coVerify { 
            userSearchRepository.trackProfileView(targetUserId, currentUserId)
        }
    }

    @Test
    fun `getPublicProfile for own profile returns correct flags`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = currentUserId,
            trackView = false // Should not track view of own profile
        )
        
        val ownProfile = createMockPublicProfile(
            userId = currentUserId,
            displayName = "Current User",
            connectionStatus = ConnectionStatus.NONE
        )
        
        coEvery { 
            userSearchRepository.getPublicProfile(currentUserId, currentUserId)
        } returns LiftrixResult.success(ownProfile)

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val profileResult = result.getOrThrow()
        assertTrue(profileResult.isOwnProfile)
        assertTrue(profileResult.canInteract) // Can always interact with own profile
        
        // Verify profile view was not tracked for own profile
        coVerify(exactly = 0) { 
            userSearchRepository.trackProfileView(any(), any())
        }
    }

    @Test
    fun `getPublicProfile with connected users allows interaction`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = targetUserId,
            trackView = true
        )
        
        val connectedProfile = createMockPublicProfile(
            userId = targetUserId,
            displayName = "Connected Friend",
            connectionStatus = ConnectionStatus.CONNECTED
        )
        
        coEvery { 
            userSearchRepository.getPublicProfile(targetUserId, currentUserId)
        } returns LiftrixResult.success(connectedProfile)
        
        coEvery { 
            userSearchRepository.trackProfileView(any(), any())
        } returns LiftrixResult.success(Unit)

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val profileResult = result.getOrThrow()
        assertTrue(profileResult.canInteract)
        assertEquals(ConnectionStatus.CONNECTED, profileResult.profile.connectionStatus)
    }

    @Test
    fun `getPublicProfile with pending received request allows interaction`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = targetUserId,
            trackView = true
        )
        
        val pendingProfile = createMockPublicProfile(
            userId = targetUserId,
            displayName = "Pending User",
            connectionStatus = ConnectionStatus.PENDING_RECEIVED
        )
        
        coEvery { 
            userSearchRepository.getPublicProfile(targetUserId, currentUserId)
        } returns LiftrixResult.success(pendingProfile)
        
        coEvery { 
            userSearchRepository.trackProfileView(any(), any())
        } returns LiftrixResult.success(Unit)

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val profileResult = result.getOrThrow()
        assertTrue(profileResult.canInteract)
        assertEquals(ConnectionStatus.PENDING_RECEIVED, profileResult.profile.connectionStatus)
    }

    @Test
    fun `getPublicProfile with pending sent request prevents interaction`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = targetUserId,
            trackView = true
        )
        
        val pendingSentProfile = createMockPublicProfile(
            userId = targetUserId,
            displayName = "Pending Sent User",
            connectionStatus = ConnectionStatus.PENDING_SENT
        )
        
        coEvery { 
            userSearchRepository.getPublicProfile(targetUserId, currentUserId)
        } returns LiftrixResult.success(pendingSentProfile)
        
        coEvery { 
            userSearchRepository.trackProfileView(any(), any())
        } returns LiftrixResult.success(Unit)

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val profileResult = result.getOrThrow()
        assertFalse(profileResult.canInteract)
        assertEquals(ConnectionStatus.PENDING_SENT, profileResult.profile.connectionStatus)
    }

    @Test
    fun `getPublicProfile with trackView disabled skips view tracking`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = targetUserId,
            trackView = false
        )
        
        val mockProfile = createMockPublicProfile(
            userId = targetUserId,
            displayName = "John Doe",
            connectionStatus = ConnectionStatus.NONE
        )
        
        coEvery { 
            userSearchRepository.getPublicProfile(targetUserId, currentUserId)
        } returns LiftrixResult.success(mockProfile)

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        
        // Verify profile view was not tracked
        coVerify(exactly = 0) { 
            userSearchRepository.trackProfileView(any(), any())
        }
    }

    @Test
    fun `getPublicProfile with view tracking failure continues operation`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = targetUserId,
            trackView = true
        )
        
        val mockProfile = createMockPublicProfile(
            userId = targetUserId,
            displayName = "John Doe",
            connectionStatus = ConnectionStatus.NONE
        )
        
        coEvery { 
            userSearchRepository.getPublicProfile(targetUserId, currentUserId)
        } returns LiftrixResult.success(mockProfile)
        
        // View tracking fails but shouldn't fail the main operation
        coEvery { 
            userSearchRepository.trackProfileView(targetUserId, currentUserId)
        } throws RuntimeException("Tracking failed")

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isSuccess) // Should succeed despite tracking failure
        val profileResult = result.getOrThrow()
        assertEquals(mockProfile, profileResult.profile)
    }

    @Test
    fun `getPublicProfile with empty user ID returns validation error`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = "",
            trackView = true
        )

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.message.contains("Profile user ID cannot be empty"))
    }

    @Test
    fun `getPublicProfile with invalid user ID format returns validation error`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = "123", // Too short
            trackView = true
        )

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.message.contains("Profile user ID format is invalid"))
    }

    @Test
    fun `getPublicProfile with unauthenticated user returns authentication error`() = runTest {
        // Given
        coEvery { authRepository.getCurrentUserId() } returns null
        
        val request = GetPublicProfileRequest(
            profileUserId = targetUserId,
            trackView = true
        )

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.AuthenticationError>(error)
        assertEquals("User not authenticated", error.message)
    }

    @Test
    fun `getPublicProfile with non-existent user returns not found error`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = "non_existent_user",
            trackView = true
        )
        
        coEvery { 
            userSearchRepository.getPublicProfile("non_existent_user", currentUserId)
        } returns LiftrixResult.success(null)

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.NotFoundError>(error)
        assertEquals("User profile not found or not public", error.message)
    }

    @Test
    fun `getPublicProfile with repository failure returns error`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = targetUserId,
            trackView = true
        )
        
        val repositoryError = LiftrixError.NetworkError("Network connection failed")
        coEvery { 
            userSearchRepository.getPublicProfile(targetUserId, currentUserId)
        } returns LiftrixResult.failure(repositoryError)

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.NetworkError>(error)
        assertEquals("Network connection failed", error.message)
    }

    @Test
    fun `getPublicProfile with exception calls error handler and returns error`() = runTest {
        // Given
        val request = GetPublicProfileRequest(
            profileUserId = targetUserId,
            trackView = true
        )
        
        val exception = RuntimeException("Unexpected error")
        coEvery { 
            userSearchRepository.getPublicProfile(any(), any())
        } throws exception
        
        every { 
            errorHandler.handleError(any(), any())
        } returns mockk()

        // When
        val result = getPublicProfileUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.UnknownError>(error)
        assertTrue(error.message.contains("Profile retrieval failed"))
        
        // Verify error handler was called
        coVerify { 
            errorHandler.handleError(
                any<LiftrixError.UnknownError>(),
                match { context ->
                    context["context"] == "GetPublicProfileUseCase" &&
                    context["profileUserId"] == targetUserId
                }
            )
        }
    }

    private fun createMockPublicProfile(
        userId: String,
        displayName: String,
        connectionStatus: ConnectionStatus,
        bio: String? = "Test bio",
        fitnessLevel: FitnessLevel? = FitnessLevel.INTERMEDIATE,
        isOnline: Boolean = false,
        mutualConnections: Int = 0
    ): PublicUserProfile {
        return PublicUserProfile(
            userId = userId,
            displayName = displayName,
            profileImageUrl = null,
            bio = bio,
            memberSince = LocalDateTime.now().minusMonths(6),
            fitnessLevel = fitnessLevel,
            isOnline = isOnline,
            lastActiveAt = LocalDateTime.now().minusHours(2),
            connectionStatus = connectionStatus,
            mutualConnections = mutualConnections,
            publicAchievements = listOf(
                UserAchievement(
                    id = "achievement1",
                    title = "First Workout",
                    description = "Completed first workout",
                    earnedAt = LocalDateTime.now().minusDays(30),
                    type = "MILESTONE",
                    iconUrl = null
                )
            ),
            publicWorkoutStats = PublicWorkoutStats(
                totalWorkouts = 25,
                totalWorkoutTime = 1500, // 25 hours
                averageWorkoutTime = 60, // 1 hour
                currentStreak = 5,
                longestStreak = 12,
                favoriteExercises = listOf("Bench Press", "Squats", "Deadlift")
            ),
            publicFitnessGoals = listOf(FitnessGoal.MUSCLE_GAIN, FitnessGoal.STRENGTH),
            availableEquipment = listOf(Equipment.BARBELL, Equipment.DUMBBELLS)
        )
    }
}