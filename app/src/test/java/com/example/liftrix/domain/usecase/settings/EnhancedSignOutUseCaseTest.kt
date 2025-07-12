package com.example.liftrix.domain.usecase.settings

import androidx.work.WorkManager
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.sync.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EnhancedSignOutUseCase.
 * 
 * Tests cover all aspects of the enhanced sign out functionality including:
 * - Successful sign out flow
 * - Error scenarios and fallback handling
 * - Analytics tracking
 * - Background service cleanup
 * - Local data cleanup
 */
class EnhancedSignOutUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var analyticsService: AnalyticsService
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var syncManager: SyncManager
    private lateinit var workManager: WorkManager
    private lateinit var enhancedSignOutUseCase: EnhancedSignOutUseCase

    @Before
    fun setUp() {
        authRepository = mockk()
        analyticsService = mockk()
        settingsRepository = mockk()
        syncManager = mockk()
        workManager = mockk()
        
        enhancedSignOutUseCase = EnhancedSignOutUseCase(
            authRepository = authRepository,
            analyticsService = analyticsService,
            settingsRepository = settingsRepository,
            syncManager = syncManager,
            workManager = workManager
        )
    }

    @Test
    fun `when all operations succeed, should complete sign out successfully`() = runTest {
        // Given
        val testUserId = "test_user_123"
        
        coEvery { authRepository.getCurrentUserId() } returns testUserId
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        coEvery { settingsRepository.clearAllSettings() } returns Result.success(Unit)
        coEvery { analyticsService.clearUserProperties() } returns Result.success(Unit)
        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)
        coEvery { analyticsService.recordException(any(), any()) } returns Result.success(Unit)
        every { syncManager.cancelSync() } returns Unit
        every { workManager.cancelAllWork() } returns Unit

        // When
        val result = enhancedSignOutUseCase()

        // Then
        assertTrue("Sign out should succeed", result.isSuccess)
        
        // Verify all operations were called in correct order
        coVerify { authRepository.getCurrentUserId() }
        coVerify { authRepository.signOut() }
        coVerify { settingsRepository.clearAllSettings() }
        verify { syncManager.cancelSync() }
        verify { workManager.cancelAllWork() }
        coVerify { analyticsService.clearUserProperties() }
        coVerify { 
            analyticsService.logEvent(
                "user_signed_out", 
                match { parameters ->
                    parameters["user_id"] == testUserId &&
                    parameters["sign_out_method"] == "enhanced_sign_out_use_case"
                }
            )
        }
    }

    @Test
    fun `when Firebase sign out fails, should return failure immediately`() = runTest {
        // Given
        val testUserId = "test_user_123"
        val signOutError = Exception("Firebase sign out failed")
        
        coEvery { authRepository.getCurrentUserId() } returns testUserId
        coEvery { authRepository.signOut() } returns Result.failure(signOutError)
        coEvery { analyticsService.recordException(any(), any()) } returns Result.success(Unit)

        // When
        val result = enhancedSignOutUseCase()

        // Then
        assertTrue("Sign out should fail", result.isFailure)
        assertEquals("Should return the same error", signOutError, result.exceptionOrNull())
        
        // Verify Firebase sign out was attempted
        coVerify { authRepository.getCurrentUserId() }
        coVerify { authRepository.signOut() }
        
        // Verify no cleanup operations were performed after Firebase failure
        coVerify(exactly = 0) { settingsRepository.clearAllSettings() }
        verify(exactly = 0) { syncManager.cancelSync() }
        verify(exactly = 0) { workManager.cancelAllWork() }
        coVerify(exactly = 0) { analyticsService.clearUserProperties() }
        
        // Verify exception was recorded
        coVerify { 
            analyticsService.recordException(
                signOutError, 
                match { data ->
                    data["operation"] == "enhanced_sign_out"
                }
            )
        }
    }

    @Test
    fun `when settings cleanup fails, should continue with sign out process`() = runTest {
        // Given
        val testUserId = "test_user_123"
        val settingsError = Exception("Settings cleanup failed")
        
        coEvery { authRepository.getCurrentUserId() } returns testUserId
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        coEvery { settingsRepository.clearAllSettings() } returns Result.failure(settingsError)
        coEvery { analyticsService.clearUserProperties() } returns Result.success(Unit)
        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)
        every { syncManager.cancelSync() } returns Unit
        every { workManager.cancelAllWork() } returns Unit

        // When
        val result = enhancedSignOutUseCase()

        // Then
        assertTrue("Sign out should succeed despite settings cleanup failure", result.isSuccess)
        
        // Verify all operations were still attempted
        coVerify { authRepository.signOut() }
        coVerify { settingsRepository.clearAllSettings() }
        verify { syncManager.cancelSync() }
        verify { workManager.cancelAllWork() }
        coVerify { analyticsService.clearUserProperties() }
        coVerify { analyticsService.logEvent("user_signed_out", any()) }
    }

    @Test
    fun `when background service cleanup fails, should continue with sign out process`() = runTest {
        // Given
        val testUserId = "test_user_123"
        
        coEvery { authRepository.getCurrentUserId() } returns testUserId
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        coEvery { settingsRepository.clearAllSettings() } returns Result.success(Unit)
        coEvery { analyticsService.clearUserProperties() } returns Result.success(Unit)
        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)
        every { syncManager.cancelSync() } throws Exception("Sync cancel failed")
        every { workManager.cancelAllWork() } returns Unit

        // When
        val result = enhancedSignOutUseCase()

        // Then
        assertTrue("Sign out should succeed despite sync cancel failure", result.isSuccess)
        
        // Verify operations were still attempted
        coVerify { authRepository.signOut() }
        coVerify { settingsRepository.clearAllSettings() }
        verify { syncManager.cancelSync() }
        verify { workManager.cancelAllWork() }
        coVerify { analyticsService.clearUserProperties() }
        coVerify { analyticsService.logEvent("user_signed_out", any()) }
    }

    @Test
    fun `when analytics operations fail, should continue with sign out process`() = runTest {
        // Given
        val testUserId = "test_user_123"
        val analyticsError = Exception("Analytics failed")
        
        coEvery { authRepository.getCurrentUserId() } returns testUserId
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        coEvery { settingsRepository.clearAllSettings() } returns Result.success(Unit)
        coEvery { analyticsService.clearUserProperties() } returns Result.failure(analyticsError)
        coEvery { analyticsService.logEvent(any(), any()) } returns Result.failure(analyticsError)
        every { syncManager.cancelSync() } returns Unit
        every { workManager.cancelAllWork() } returns Unit

        // When
        val result = enhancedSignOutUseCase()

        // Then
        assertTrue("Sign out should succeed despite analytics failures", result.isSuccess)
        
        // Verify core operations were performed
        coVerify { authRepository.signOut() }
        coVerify { settingsRepository.clearAllSettings() }
        verify { syncManager.cancelSync() }
        verify { workManager.cancelAllWork() }
        
        // Verify analytics operations were attempted
        coVerify { analyticsService.clearUserProperties() }
        coVerify { analyticsService.logEvent("user_signed_out", any()) }
    }

    @Test
    fun `when user ID is null, should handle gracefully`() = runTest {
        // Given
        coEvery { authRepository.getCurrentUserId() } returns null
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        coEvery { settingsRepository.clearAllSettings() } returns Result.success(Unit)
        coEvery { analyticsService.clearUserProperties() } returns Result.success(Unit)
        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)
        every { syncManager.cancelSync() } returns Unit
        every { workManager.cancelAllWork() } returns Unit

        // When
        val result = enhancedSignOutUseCase()

        // Then
        assertTrue("Sign out should succeed with null user ID", result.isSuccess)
        
        // Verify analytics event was logged with "unknown" user ID
        coVerify { 
            analyticsService.logEvent(
                "user_signed_out", 
                match { parameters ->
                    parameters["user_id"] == "unknown"
                }
            )
        }
    }

    @Test
    fun `when unexpected exception occurs, should handle gracefully and record exception`() = runTest {
        // Given
        val testUserId = "test_user_123"
        val unexpectedException = RuntimeException("Unexpected error")
        
        coEvery { authRepository.getCurrentUserId() } returns testUserId
        coEvery { authRepository.signOut() } throws unexpectedException
        coEvery { analyticsService.recordException(any(), any()) } returns Result.success(Unit)

        // When
        val result = enhancedSignOutUseCase()

        // Then
        assertTrue("Sign out should fail with unexpected exception", result.isFailure)
        assertEquals("Should return the unexpected exception", unexpectedException, result.exceptionOrNull())
        
        // Verify exception was recorded
        coVerify { 
            analyticsService.recordException(
                unexpectedException, 
                match { data ->
                    data["operation"] == "enhanced_sign_out"
                }
            )
        }
    }

    @Test
    fun `when analytics exception recording fails, should not affect overall result`() = runTest {
        // Given
        val testUserId = "test_user_123"
        val originalException = Exception("Original error")
        val analyticsRecordingError = Exception("Analytics recording failed")
        
        coEvery { authRepository.getCurrentUserId() } returns testUserId
        coEvery { authRepository.signOut() } returns Result.failure(originalException)
        coEvery { analyticsService.recordException(any(), any()) } throws analyticsRecordingError

        // When
        val result = enhancedSignOutUseCase()

        // Then
        assertTrue("Sign out should fail with original exception", result.isFailure)
        assertEquals("Should return the original exception", originalException, result.exceptionOrNull())
        
        // Verify analytics recording was attempted
        coVerify { analyticsService.recordException(originalException, any()) }
    }

    @Test
    fun `should log correct analytics event parameters`() = runTest {
        // Given
        val testUserId = "test_user_123"
        val testTimestamp = 1234567890L
        
        coEvery { authRepository.getCurrentUserId() } returns testUserId
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        coEvery { settingsRepository.clearAllSettings() } returns Result.success(Unit)
        coEvery { analyticsService.clearUserProperties() } returns Result.success(Unit)
        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)
        every { syncManager.cancelSync() } returns Unit
        every { workManager.cancelAllWork() } returns Unit

        // When
        val result = enhancedSignOutUseCase()

        // Then
        assertTrue("Sign out should succeed", result.isSuccess)
        
        // Verify analytics event parameters
        coVerify { 
            analyticsService.logEvent(
                "user_signed_out",
                match { parameters ->
                    parameters["user_id"] == testUserId &&
                    parameters["sign_out_method"] == "enhanced_sign_out_use_case" &&
                    parameters["timestamp"] is Long
                }
            )
        }
    }
}