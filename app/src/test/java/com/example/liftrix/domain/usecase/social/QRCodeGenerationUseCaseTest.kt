package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for QRCodeGenerationUseCase
 * 
 * Tests QR code generation, URL creation, expiration handling, security,
 * and error scenarios for profile sharing functionality.
 */
class QRCodeGenerationUseCaseTest {

    private lateinit var qrCodeGenerationUseCase: QRCodeGenerationUseCase
    private lateinit var userSearchRepository: UserSearchRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var errorHandler: ErrorHandler

    private val currentUserId = "current_user_123"
    private val targetUserId = "target_user_456"
    private val qrCodeData = "qr_code_data_abc123"

    @Before
    fun setup() {
        userSearchRepository = mockk()
        authRepository = mockk()
        errorHandler = mockk()
        
        qrCodeGenerationUseCase = QRCodeGenerationUseCase(
            userSearchRepository = userSearchRepository,
            authRepository = authRepository,
            errorHandler = errorHandler
        )

        // Mock authentication
        coEvery { authRepository.getCurrentUserId() } returns currentUserId
    }

    @Test
    fun `generateQRCode for current user returns successful result`() = runTest {
        // Given
        val request = QRCodeGenerationRequest(
            targetUserId = null, // Should default to current user
            expirationHours = 0
        )
        
        coEvery { 
            userSearchRepository.generateProfileQRCode(currentUserId)
        } returns LiftrixResult.success(qrCodeData)

        // When
        val result = qrCodeGenerationUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val qrResult = result.getOrThrow()
        assertEquals(qrCodeData, qrResult.qrCodeData)
        assertEquals(currentUserId, qrResult.profileUserId)
        assertEquals(null, qrResult.expiresAt) // No expiration
        assertFalse(qrResult.isTemporary)
        assertEquals("https://liftrix.app/profile?qr=$qrCodeData", qrResult.shareableUrl)
    }

    @Test
    fun `generateQRCode for specific user returns successful result`() = runTest {
        // Given
        val request = QRCodeGenerationRequest(
            targetUserId = currentUserId, // Explicitly specify current user
            expirationHours = 0
        )
        
        coEvery { 
            userSearchRepository.generateProfileQRCode(currentUserId)
        } returns LiftrixResult.success(qrCodeData)

        // When
        val result = qrCodeGenerationUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val qrResult = result.getOrThrow()
        assertEquals(qrCodeData, qrResult.qrCodeData)
        assertEquals(currentUserId, qrResult.profileUserId)
        assertEquals("https://liftrix.app/profile?qr=$qrCodeData", qrResult.shareableUrl)
    }

    @Test
    fun `generateQRCode with expiration creates temporary QR code`() = runTest {
        // Given
        val expirationHours = 24
        val request = QRCodeGenerationRequest(
            targetUserId = null,
            expirationHours = expirationHours
        )
        
        coEvery { 
            userSearchRepository.generateProfileQRCode(currentUserId)
        } returns LiftrixResult.success(qrCodeData)

        val startTime = System.currentTimeMillis()

        // When
        val result = qrCodeGenerationUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val qrResult = result.getOrThrow()
        assertEquals(qrCodeData, qrResult.qrCodeData)
        assertTrue(qrResult.isTemporary)
        
        // Verify expiration time is approximately correct (within 1 minute tolerance)
        val expectedExpiration = startTime + (expirationHours * 60 * 60 * 1000)
        val actualExpiration = qrResult.expiresAt!!
        assertTrue(kotlin.math.abs(actualExpiration - expectedExpiration) < 60_000) // 1 minute tolerance
    }

    @Test
    fun `generateQRCode for other user returns permission error`() = runTest {
        // Given
        val request = QRCodeGenerationRequest(
            targetUserId = targetUserId, // Different from current user
            expirationHours = 0
        )

        // When
        val result = qrCodeGenerationUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.PermissionError>(error)
        assertEquals("Cannot generate QR code for other users", error.message)
        
        // Verify repository was not called
        coVerify(exactly = 0) { 
            userSearchRepository.generateProfileQRCode(any())
        }
    }

    @Test
    fun `generateQRCode with unauthenticated user returns authentication error`() = runTest {
        // Given
        coEvery { authRepository.getCurrentUserId() } returns null
        
        val request = QRCodeGenerationRequest(
            targetUserId = null,
            expirationHours = 0
        )

        // When
        val result = qrCodeGenerationUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.AuthenticationError>(error)
        assertEquals("User not authenticated", error.message)
    }

    @Test
    fun `generateQRCode with repository failure returns error`() = runTest {
        // Given
        val request = QRCodeGenerationRequest(
            targetUserId = null,
            expirationHours = 0
        )
        
        val repositoryError = LiftrixError.DatabaseError("Failed to generate QR code")
        coEvery { 
            userSearchRepository.generateProfileQRCode(currentUserId)
        } returns LiftrixResult.failure(repositoryError)

        // When
        val result = qrCodeGenerationUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.DatabaseError>(error)
        assertEquals("Failed to generate QR code", error.message)
    }

    @Test
    fun `generateQRCode with exception calls error handler and returns error`() = runTest {
        // Given
        val request = QRCodeGenerationRequest(
            targetUserId = null,
            expirationHours = 0
        )
        
        val exception = RuntimeException("Unexpected error")
        coEvery { 
            userSearchRepository.generateProfileQRCode(any())
        } throws exception
        
        every { 
            errorHandler.handleError(any(), any())
        } returns mockk()

        // When
        val result = qrCodeGenerationUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.UnknownError>(error)
        assertTrue(error.message.contains("QR code generation failed"))
        
        // Verify error handler was called
        coVerify { 
            errorHandler.handleError(
                any<LiftrixError.UnknownError>(),
                match { context ->
                    context["context"] == "QRCodeGenerationUseCase" &&
                    context["targetUserId"] == "current_user"
                }
            )
        }
    }

    @Test
    fun `resolveQRCode with valid data returns user information`() = runTest {
        // Given
        val request = QRCodeResolutionRequest(qrCodeData = qrCodeData)
        val resolvedUserId = "resolved_user_123"
        
        coEvery { 
            userSearchRepository.resolveQRCodeToUser(qrCodeData)
        } returns LiftrixResult.success(resolvedUserId)
        
        coEvery { 
            userSearchRepository.canViewProfile(resolvedUserId, currentUserId)
        } returns LiftrixResult.success(true)

        // When
        val result = qrCodeGenerationUseCase.resolveQRCode(request)

        // Then
        assertTrue(result.isSuccess)
        val resolveResult = result.getOrThrow()
        assertEquals(resolvedUserId, resolveResult.profileUserId)
        assertEquals(qrCodeData, resolveResult.qrCodeData)
        assertTrue(resolveResult.canViewProfile)
    }

    @Test
    fun `resolveQRCode with blocked user returns no view permission`() = runTest {
        // Given
        val request = QRCodeResolutionRequest(qrCodeData = qrCodeData)
        val blockedUserId = "blocked_user_123"
        
        coEvery { 
            userSearchRepository.resolveQRCodeToUser(qrCodeData)
        } returns LiftrixResult.success(blockedUserId)
        
        coEvery { 
            userSearchRepository.canViewProfile(blockedUserId, currentUserId)
        } returns LiftrixResult.success(false)

        // When
        val result = qrCodeGenerationUseCase.resolveQRCode(request)

        // Then
        assertTrue(result.isSuccess)
        val resolveResult = result.getOrThrow()
        assertEquals(blockedUserId, resolveResult.profileUserId)
        assertFalse(resolveResult.canViewProfile)
    }

    @Test
    fun `resolveQRCode with empty data returns validation error`() = runTest {
        // Given
        val request = QRCodeResolutionRequest(qrCodeData = "")

        // When
        val result = qrCodeGenerationUseCase.resolveQRCode(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.message.contains("QR code data cannot be empty"))
    }

    @Test
    fun `resolveQRCode with invalid data returns validation error`() = runTest {
        // Given
        val request = QRCodeResolutionRequest(qrCodeData = "abc") // Too short

        // When
        val result = qrCodeGenerationUseCase.resolveQRCode(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.message.contains("QR code data format is invalid"))
    }

    @Test
    fun `resolveQRCode with non-existent QR code returns not found error`() = runTest {
        // Given
        val request = QRCodeResolutionRequest(qrCodeData = "non_existent_qr_123")
        
        coEvery { 
            userSearchRepository.resolveQRCodeToUser("non_existent_qr_123")
        } returns LiftrixResult.success(null)

        // When
        val result = qrCodeGenerationUseCase.resolveQRCode(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.NotFoundError>(error)
        assertEquals("QR code not found or expired", error.message)
    }

    @Test
    fun `resolveQRCode with unauthenticated user returns authentication error`() = runTest {
        // Given
        coEvery { authRepository.getCurrentUserId() } returns null
        
        val request = QRCodeResolutionRequest(qrCodeData = qrCodeData)

        // When
        val result = qrCodeGenerationUseCase.resolveQRCode(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.AuthenticationError>(error)
        assertEquals("User not authenticated", error.message)
    }

    @Test
    fun `generateQRCode creates proper shareable URL format`() = runTest {
        // Given
        val request = QRCodeGenerationRequest(
            targetUserId = null,
            expirationHours = 0
        )
        
        val customQRData = "custom_qr_data_xyz789"
        coEvery { 
            userSearchRepository.generateProfileQRCode(currentUserId)
        } returns LiftrixResult.success(customQRData)

        // When
        val result = qrCodeGenerationUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val qrResult = result.getOrThrow()
        assertEquals("https://liftrix.app/profile?qr=$customQRData", qrResult.shareableUrl)
    }
}