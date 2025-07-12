package com.example.liftrix.domain.usecase.settings

import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class GetUserSettingsUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var getUserSettingsUseCase: GetUserSettingsUseCase

    @Before
    fun setUp() {
        settingsRepository = mockk()
        getUserSettingsUseCase = GetUserSettingsUseCase(settingsRepository)
    }

    @Test
    fun `when settings exist, should return success with settings`() = runTest {
        // Given
        val userId = "test_user_123"
        val expectedSettings = UserSettings(
            userId = userId,
            darkMode = true,
            notificationsEnabled = false,
            updatedAt = Instant.now()
        )
        coEvery { settingsRepository.getUserSettings(userId) } returns flowOf(expectedSettings)

        // When
        val result = getUserSettingsUseCase(userId).first()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedSettings, result.getOrNull())
        coVerify { settingsRepository.getUserSettings(userId) }
    }

    @Test
    fun `when settings do not exist, should return success with default settings`() = runTest {
        // Given
        val userId = "test_user_456"
        coEvery { settingsRepository.getUserSettings(userId) } returns flowOf(null)

        // When
        val result = getUserSettingsUseCase(userId).first()

        // Then
        assertTrue(result.isSuccess)
        val returnedSettings = result.getOrNull()
        assertNotNull(returnedSettings)
        assertEquals(userId, returnedSettings?.userId)
        assertEquals(false, returnedSettings?.darkMode) // Default value
        assertEquals(true, returnedSettings?.notificationsEnabled) // Default value
        coVerify { settingsRepository.getUserSettings(userId) }
    }

    @Test
    fun `when repository throws exception, should return failure`() = runTest {
        // Given
        val userId = "test_user_789"
        val exception = RuntimeException("Database error")
        coEvery { settingsRepository.getUserSettings(userId) } returns flowOf<UserSettings?>(null)
            .apply { throw exception }

        // When
        val result = getUserSettingsUseCase(userId).first()

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify { settingsRepository.getUserSettings(userId) }
    }

    @Test
    fun `when userId is blank, should throw IllegalArgumentException`() = runTest {
        // Given
        val blankUserId = ""

        // When & Then
        try {
            getUserSettingsUseCase(blankUserId).first()
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("User ID cannot be blank", e.message)
        }
    }

    @Test
    fun `when userId is whitespace, should throw IllegalArgumentException`() = runTest {
        // Given
        val whitespaceUserId = "   "

        // When & Then
        try {
            getUserSettingsUseCase(whitespaceUserId).first()
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("User ID cannot be blank", e.message)
        }
    }

    @Test
    fun `hasUserSettings should return true when settings exist`() = runTest {
        // Given
        val userId = "test_user_123"
        coEvery { settingsRepository.hasSettings(userId) } returns true

        // When
        val result = getUserSettingsUseCase.hasUserSettings(userId)

        // Then
        assertTrue(result)
        coVerify { settingsRepository.hasSettings(userId) }
    }

    @Test
    fun `hasUserSettings should return false when settings do not exist`() = runTest {
        // Given
        val userId = "test_user_456"
        coEvery { settingsRepository.hasSettings(userId) } returns false

        // When
        val result = getUserSettingsUseCase.hasUserSettings(userId)

        // Then
        assertFalse(result)
        coVerify { settingsRepository.hasSettings(userId) }
    }

    @Test
    fun `hasUserSettings should return false when repository throws exception`() = runTest {
        // Given
        val userId = "test_user_789"
        coEvery { settingsRepository.hasSettings(userId) } throws RuntimeException("Database error")

        // When
        val result = getUserSettingsUseCase.hasUserSettings(userId)

        // Then
        assertFalse(result)
        coVerify { settingsRepository.hasSettings(userId) }
    }

    @Test
    fun `hasUserSettings should return false when userId is blank`() = runTest {
        // Given
        val blankUserId = ""

        // When
        val result = getUserSettingsUseCase.hasUserSettings(blankUserId)

        // Then
        assertFalse(result)
    }

    @Test
    fun `syncUserSettings should return success when sync succeeds`() = runTest {
        // Given
        val userId = "test_user_123"
        coEvery { settingsRepository.syncSettings(userId) } returns Result.success(Unit)

        // When
        val result = getUserSettingsUseCase.syncUserSettings(userId)

        // Then
        assertTrue(result.isSuccess)
        coVerify { settingsRepository.syncSettings(userId) }
    }

    @Test
    fun `syncUserSettings should return failure when sync fails`() = runTest {
        // Given
        val userId = "test_user_456"
        val failure = Result.failure<Unit>(RuntimeException("Sync failed"))
        coEvery { settingsRepository.syncSettings(userId) } returns failure

        // When
        val result = getUserSettingsUseCase.syncUserSettings(userId)

        // Then
        assertTrue(result.isFailure)
        coVerify { settingsRepository.syncSettings(userId) }
    }

    @Test
    fun `syncUserSettings should return failure when userId is blank`() = runTest {
        // Given
        val blankUserId = ""

        // When
        val result = getUserSettingsUseCase.syncUserSettings(blankUserId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("User ID cannot be blank", result.exceptionOrNull()?.message)
    }

    @Test
    fun `syncUserSettings should return failure when repository throws exception`() = runTest {
        // Given
        val userId = "test_user_789"
        val exception = RuntimeException("Database error")
        coEvery { settingsRepository.syncSettings(userId) } throws exception

        // When
        val result = getUserSettingsUseCase.syncUserSettings(userId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
        coVerify { settingsRepository.syncSettings(userId) }
    }
}