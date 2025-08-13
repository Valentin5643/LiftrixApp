package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.validation.ProfileValidator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for CreateSocialProfileUseCase.
 * Tests social profile creation with validation, username availability, and error handling.
 * Part of social infrastructure testing from SPEC-20250113-social-infrastructure.
 */
class CreateSocialProfileUseCaseTest {

    private lateinit var useCase: CreateSocialProfileUseCase
    private val repository: SocialProfileRepository = mockk()
    private val validator: ProfileValidator = mockk()
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase = mockk()

    private val testUserId = "user123"
    private val testUsername = "testuser"
    private val testDisplayName = "Test User"
    private val testBio = "Test bio"

    @Before
    fun setup() {
        useCase = CreateSocialProfileUseCase(
            repository = repository,
            validator = validator,
            getCurrentUserIdUseCase = getCurrentUserIdUseCase
        )
    }

    @Test
    fun `createProfile succeeds with valid data`() = runTest {
        // Arrange
        every { getCurrentUserIdUseCase() } returns testUserId
        every { validator.validateUsername(testUsername) } returns LiftrixResult.success(Unit)
        every { validator.validateDisplayName(testDisplayName) } returns LiftrixResult.success(Unit)
        every { validator.validateBio(testBio) } returns LiftrixResult.success(Unit)
        coEvery { repository.checkUsernameAvailability(testUsername) } returns LiftrixResult.success(true)
        
        val expectedProfile = mockk<SocialProfile>()
        coEvery { repository.createProfile(any()) } returns LiftrixResult.success(expectedProfile)

        // Act
        val result = useCase(testUsername, testDisplayName, testBio)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(expectedProfile, result.getOrNull())
        
        coVerify { 
            validator.validateUsername(testUsername)
            validator.validateDisplayName(testDisplayName) 
            validator.validateBio(testBio)
            repository.checkUsernameAvailability(testUsername)
            repository.createProfile(any())
        }
    }

    @Test
    fun `createProfile succeeds with null bio`() = runTest {
        // Arrange
        every { getCurrentUserIdUseCase() } returns testUserId
        every { validator.validateUsername(testUsername) } returns LiftrixResult.success(Unit)
        every { validator.validateDisplayName(testDisplayName) } returns LiftrixResult.success(Unit)
        coEvery { repository.checkUsernameAvailability(testUsername) } returns LiftrixResult.success(true)
        
        val expectedProfile = mockk<SocialProfile>()
        coEvery { repository.createProfile(any()) } returns LiftrixResult.success(expectedProfile)

        // Act
        val result = useCase(testUsername, testDisplayName, null)

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { validator.validateBio(any()) }
        coVerify { repository.createProfile(any()) }
    }

    @Test
    fun `createProfile fails when user not authenticated`() = runTest {
        // Arrange
        every { getCurrentUserIdUseCase() } returns null

        // Act
        val result = useCase(testUsername, testDisplayName, testBio)

        // Assert
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as LiftrixError.BusinessLogicError
        assertEquals("Failed to create social profile", error.errorMessage)
        assertEquals("CREATE_SOCIAL_PROFILE", error.operation)
    }

    @Test
    fun `createProfile fails with invalid username`() = runTest {
        // Arrange
        every { getCurrentUserIdUseCase() } returns testUserId
        every { validator.validateUsername(testUsername) } returns LiftrixResult.failure(
            LiftrixError.ValidationError(
                errorMessage = "Invalid username",
                operation = "VALIDATE_USERNAME"
            )
        )

        // Act
        val result = useCase(testUsername, testDisplayName, testBio)

        // Assert
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.checkUsernameAvailability(any()) }
        coVerify(exactly = 0) { repository.createProfile(any()) }
    }

    @Test
    fun `createProfile fails with invalid display name`() = runTest {
        // Arrange
        every { getCurrentUserIdUseCase() } returns testUserId
        every { validator.validateUsername(testUsername) } returns LiftrixResult.success(Unit)
        every { validator.validateDisplayName(testDisplayName) } returns LiftrixResult.failure(
            LiftrixError.ValidationError(
                errorMessage = "Invalid display name",
                operation = "VALIDATE_DISPLAY_NAME"
            )
        )

        // Act
        val result = useCase(testUsername, testDisplayName, testBio)

        // Assert
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.checkUsernameAvailability(any()) }
        coVerify(exactly = 0) { repository.createProfile(any()) }
    }

    @Test
    fun `createProfile fails with invalid bio`() = runTest {
        // Arrange
        every { getCurrentUserIdUseCase() } returns testUserId
        every { validator.validateUsername(testUsername) } returns LiftrixResult.success(Unit)
        every { validator.validateDisplayName(testDisplayName) } returns LiftrixResult.success(Unit)
        every { validator.validateBio(testBio) } returns LiftrixResult.failure(
            LiftrixError.ValidationError(
                errorMessage = "Invalid bio",
                operation = "VALIDATE_BIO"
            )
        )

        // Act
        val result = useCase(testUsername, testDisplayName, testBio)

        // Assert
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.checkUsernameAvailability(any()) }
        coVerify(exactly = 0) { repository.createProfile(any()) }
    }

    @Test
    fun `createProfile fails when username is taken`() = runTest {
        // Arrange
        every { getCurrentUserIdUseCase() } returns testUserId
        every { validator.validateUsername(testUsername) } returns LiftrixResult.success(Unit)
        every { validator.validateDisplayName(testDisplayName) } returns LiftrixResult.success(Unit)
        every { validator.validateBio(testBio) } returns LiftrixResult.success(Unit)
        coEvery { repository.checkUsernameAvailability(testUsername) } returns LiftrixResult.success(false)

        // Act
        val result = useCase(testUsername, testDisplayName, testBio)

        // Assert
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.createProfile(any()) }
    }

    @Test
    fun `createProfile fails when username availability check fails`() = runTest {
        // Arrange
        every { getCurrentUserIdUseCase() } returns testUserId
        every { validator.validateUsername(testUsername) } returns LiftrixResult.success(Unit)
        every { validator.validateDisplayName(testDisplayName) } returns LiftrixResult.success(Unit)
        every { validator.validateBio(testBio) } returns LiftrixResult.success(Unit)
        coEvery { repository.checkUsernameAvailability(testUsername) } returns LiftrixResult.failure(
            LiftrixError.NetworkError("Network error")
        )

        // Act
        val result = useCase(testUsername, testDisplayName, testBio)

        // Assert
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { repository.createProfile(any()) }
    }

    @Test
    fun `createProfile fails when repository fails`() = runTest {
        // Arrange
        every { getCurrentUserIdUseCase() } returns testUserId
        every { validator.validateUsername(testUsername) } returns LiftrixResult.success(Unit)
        every { validator.validateDisplayName(testDisplayName) } returns LiftrixResult.success(Unit)
        every { validator.validateBio(testBio) } returns LiftrixResult.success(Unit)
        coEvery { repository.checkUsernameAvailability(testUsername) } returns LiftrixResult.success(true)
        coEvery { repository.createProfile(any()) } returns LiftrixResult.failure(
            LiftrixError.DatabaseError("Database error")
        )

        // Act
        val result = useCase(testUsername, testDisplayName, testBio)

        // Assert
        assertTrue(result.isFailure)
        coVerify { repository.createProfile(any()) }
    }

    @Test
    fun `createProfile creates profile with correct privacy defaults`() = runTest {
        // Arrange
        every { getCurrentUserIdUseCase() } returns testUserId
        every { validator.validateUsername(testUsername) } returns LiftrixResult.success(Unit)
        every { validator.validateDisplayName(testDisplayName) } returns LiftrixResult.success(Unit)
        every { validator.validateBio(testBio) } returns LiftrixResult.success(Unit)
        coEvery { repository.checkUsernameAvailability(testUsername) } returns LiftrixResult.success(true)
        
        var capturedProfile: SocialProfile? = null
        coEvery { repository.createProfile(capture(slot<SocialProfile>())) } answers {
            capturedProfile = firstArg()
            LiftrixResult.success(capturedProfile!!)
        }

        // Act
        val result = useCase(testUsername, testDisplayName, testBio)

        // Assert
        assertTrue(result.isSuccess)
        assertNotNull(capturedProfile)
        with(capturedProfile!!) {
            assertEquals(testUserId, userId)
            assertEquals(testUsername.lowercase(), username)
            assertEquals(testDisplayName, displayName)
            assertEquals(testBio, bio)
            assertTrue(isPrivate) // Privacy by default
            assertTrue(allowFriendRequests) // Allow friend requests by default
        }
    }

    @Test
    fun `createProfile normalizes username to lowercase`() = runTest {
        // Arrange
        val mixedCaseUsername = "TestUser123"
        every { getCurrentUserIdUseCase() } returns testUserId
        every { validator.validateUsername(mixedCaseUsername) } returns LiftrixResult.success(Unit)
        every { validator.validateDisplayName(testDisplayName) } returns LiftrixResult.success(Unit)
        coEvery { repository.checkUsernameAvailability(mixedCaseUsername) } returns LiftrixResult.success(true)
        
        var capturedProfile: SocialProfile? = null
        coEvery { repository.createProfile(capture(slot<SocialProfile>())) } answers {
            capturedProfile = firstArg()
            LiftrixResult.success(capturedProfile!!)
        }

        // Act
        val result = useCase(mixedCaseUsername, testDisplayName, null)

        // Assert
        assertTrue(result.isSuccess)
        assertNotNull(capturedProfile)
        assertEquals("testuser123", capturedProfile!!.username)
    }

    @Test
    fun `createProfile trims whitespace from inputs`() = runTest {
        // Arrange
        val paddedUsername = "  testuser  "
        val paddedDisplayName = "  Test User  "
        val paddedBio = "  Test bio  "
        
        every { getCurrentUserIdUseCase() } returns testUserId
        every { validator.validateUsername(paddedUsername) } returns LiftrixResult.success(Unit)
        every { validator.validateDisplayName(paddedDisplayName) } returns LiftrixResult.success(Unit)
        every { validator.validateBio(paddedBio) } returns LiftrixResult.success(Unit)
        coEvery { repository.checkUsernameAvailability(paddedUsername) } returns LiftrixResult.success(true)
        
        var capturedProfile: SocialProfile? = null
        coEvery { repository.createProfile(capture(slot<SocialProfile>())) } answers {
            capturedProfile = firstArg()
            LiftrixResult.success(capturedProfile!!)
        }

        // Act
        val result = useCase(paddedUsername, paddedDisplayName, paddedBio)

        // Assert
        assertTrue(result.isSuccess)
        assertNotNull(capturedProfile)
        with(capturedProfile!!) {
            assertEquals("testuser", username) // trimmed and lowercase
            assertEquals("Test User", displayName) // trimmed
            assertEquals("Test bio", bio) // trimmed
        }
    }
}

// Helper function for MockK slot
private fun <T> slot(): io.mockk.CapturingSlot<T> = io.mockk.slot()