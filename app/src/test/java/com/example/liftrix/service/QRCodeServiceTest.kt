package com.example.liftrix.service

import com.example.liftrix.domain.model.social.QRCodeData
import com.example.liftrix.domain.service.QRCodeService
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
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
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.time.Instant
import java.util.*

/**
 * Comprehensive tests for QRCodeService implementation
 * 
 * Tests QR code generation, encryption, validation, and error handling
 * for gym buddy profile sharing functionality.
 */
@RunWith(JUnit4::class)
class QRCodeServiceTest {

    private lateinit var qrCodeService: QRCodeService
    private lateinit var getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    private lateinit var getSocialProfileUseCase: GetSocialProfileUseCase

    private val testUserId = "test-user-123"
    private val testProfile = SocialProfile(
        userId = testUserId,
        username = "testuser",
        displayName = "Test User",
        profilePhotoUrl = "https://example.com/photo.jpg",
        isPublic = true,
        followerCount = 42,
        followingCount = 13,
        workoutCount = 156
    )

    @Before
    fun setup() {
        getCurrentUserIdUseCase = mockk()
        getSocialProfileUseCase = mockk()
        
        qrCodeService = QRCodeServiceImpl(
            getCurrentUserIdUseCase = getCurrentUserIdUseCase,
            getSocialProfileUseCase = getSocialProfileUseCase
        )

        every { getCurrentUserIdUseCase() } returns testUserId
    }

    @Test
    fun `generateQRCode should return valid QR data for authenticated user`() = runTest {
        // Given
        coEvery { getSocialProfileUseCase(testUserId) } returns LiftrixResult.Success(testProfile)

        // When
        val result = qrCodeService.generateQRCode(testUserId, expirationHours = 24)

        // Then
        assertTrue("QR code generation should succeed", result.isSuccess)
        val qrData = result.getOrNull()!!

        assertEquals("User ID should match", testUserId, qrData.userId)
        assertEquals("Username should match", testProfile.username, qrData.username)
        assertEquals("Display name should match", testProfile.displayName, qrData.displayName)
        assertTrue("QR data should be encrypted", qrData.encryptedData.isNotEmpty())
        assertTrue("Should have valid expiration", qrData.expiresAt > Instant.now().epochSecond)
    }

    @Test
    fun `generateQRCode should handle profile fetch failure`() = runTest {
        // Given
        val profileError = LiftrixError.NetworkError("Profile fetch failed")
        coEvery { getSocialProfileUseCase(testUserId) } returns LiftrixResult.Error(profileError)

        // When
        val result = qrCodeService.generateQRCode(testUserId, expirationHours = 24)

        // Then
        assertTrue("QR code generation should fail", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.BusinessLogicError
        assertNotNull("Should return business logic error", error)
        assertEquals("Should indicate QR generation failure", "QR_CODE_GENERATION_FAILED", error!!.code)
    }

    @Test
    fun `generateQRCode should handle unauthenticated user`() = runTest {
        // Given
        every { getCurrentUserIdUseCase() } returns null

        // When
        val result = qrCodeService.generateQRCode("different-user", expirationHours = 24)

        // Then
        assertTrue("QR code generation should fail for unauthenticated user", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertEquals("Should indicate authentication required", "user_id", error!!.field)
    }

    @Test
    fun `generateQRCode should apply correct expiration times`() = runTest {
        // Given
        coEvery { getSocialProfileUseCase(testUserId) } returns LiftrixResult.Success(testProfile)
        val expirationHours = 48

        // When
        val result = qrCodeService.generateQRCode(testUserId, expirationHours)

        // Then
        assertTrue("QR code generation should succeed", result.isSuccess)
        val qrData = result.getOrNull()!!
        
        val expectedExpiration = Instant.now().plusSeconds(expirationHours * 3600L).epochSecond
        val actualExpiration = qrData.expiresAt
        
        // Allow 5 second tolerance for test execution time
        assertTrue("Expiration should be approximately correct", 
            kotlin.math.abs(expectedExpiration - actualExpiration) < 5)
    }

    @Test
    fun `generateQRCode should handle zero expiration (never expires)`() = runTest {
        // Given
        coEvery { getSocialProfileUseCase(testUserId) } returns LiftrixResult.Success(testProfile)

        // When
        val result = qrCodeService.generateQRCode(testUserId, expirationHours = 0)

        // Then
        assertTrue("QR code generation should succeed", result.isSuccess)
        val qrData = result.getOrNull()!!
        
        assertEquals("Zero expiration should mean never expires", 0L, qrData.expiresAt)
    }

    @Test
    fun `validateQRCode should validate non-expired codes`() = runTest {
        // Given
        coEvery { getSocialProfileUseCase(testUserId) } returns LiftrixResult.Success(testProfile)
        val generateResult = qrCodeService.generateQRCode(testUserId, expirationHours = 24)
        val qrData = generateResult.getOrNull()!!

        // When
        val validateResult = qrCodeService.validateQRCode(qrData.encryptedData)

        // Then
        assertTrue("QR code validation should succeed", validateResult.isSuccess)
        val validatedData = validateResult.getOrNull()!!
        assertEquals("User ID should match", testUserId, validatedData.userId)
        assertEquals("Username should match", testProfile.username, validatedData.username)
    }

    @Test
    fun `validateQRCode should reject expired codes`() = runTest {
        // Given - create QR code with past expiration
        val expiredQrData = QRCodeData(
            userId = testUserId,
            username = testProfile.username,
            displayName = testProfile.displayName,
            profilePhotoUrl = testProfile.profilePhotoUrl,
            encryptedData = "encrypted-expired-data",
            expiresAt = Instant.now().minusSeconds(3600).epochSecond // Expired 1 hour ago
        )

        // When
        val result = qrCodeService.validateQRCode(expiredQrData.encryptedData)

        // Then
        assertTrue("Expired QR code validation should fail", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertTrue("Should indicate expiration", error!!.violations.any { it.contains("expired") })
    }

    @Test
    fun `validateQRCode should handle invalid encryption data`() = runTest {
        // Given
        val invalidEncryptedData = "invalid-encrypted-data-123"

        // When
        val result = qrCodeService.validateQRCode(invalidEncryptedData)

        // Then
        assertTrue("Invalid QR code validation should fail", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertEquals("Should indicate encrypted_data field", "encrypted_data", error!!.field)
    }

    @Test
    fun `generateQRCode should create unique codes for same user`() = runTest {
        // Given
        coEvery { getSocialProfileUseCase(testUserId) } returns LiftrixResult.Success(testProfile)

        // When
        val result1 = qrCodeService.generateQRCode(testUserId, expirationHours = 24)
        Thread.sleep(10) // Ensure different timestamp
        val result2 = qrCodeService.generateQRCode(testUserId, expirationHours = 24)

        // Then
        assertTrue("First QR generation should succeed", result1.isSuccess)
        assertTrue("Second QR generation should succeed", result2.isSuccess)
        
        val qrData1 = result1.getOrNull()!!
        val qrData2 = result2.getOrNull()!!
        
        assertNotEquals("QR codes should be unique", qrData1.encryptedData, qrData2.encryptedData)
    }

    @Test
    fun `validateQRCode should handle empty encrypted data`() = runTest {
        // Given
        val emptyEncryptedData = ""

        // When
        val result = qrCodeService.validateQRCode(emptyEncryptedData)

        // Then
        assertTrue("Empty QR code validation should fail", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertEquals("Should indicate encrypted_data field", "encrypted_data", error!!.field)
    }

    @Test
    fun `generateQRCode should handle private profiles`() = runTest {
        // Given
        val privateProfile = testProfile.copy(isPublic = false)
        coEvery { getSocialProfileUseCase(testUserId) } returns LiftrixResult.Success(privateProfile)

        // When
        val result = qrCodeService.generateQRCode(testUserId, expirationHours = 24)

        // Then
        assertTrue("QR code generation should succeed even for private profiles", result.isSuccess)
        val qrData = result.getOrNull()!!
        assertEquals("Should still contain user data", testUserId, qrData.userId)
    }
}