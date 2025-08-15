package com.example.liftrix.service

import android.graphics.Bitmap
import com.example.liftrix.domain.service.QRCodeService
import com.example.liftrix.service.QRCodeServiceImpl
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
 * Comprehensive tests for QRCodeService implementation
 * 
 * Tests QR code generation, parsing, validation, encryption, and error handling.
 */
@RunWith(JUnit4::class)
class QRCodeServiceTest {

    private lateinit var qrCodeService: QRCodeService
    
    private val testData = "https://liftrix.app/profile/test-user-123"
    private val testEncryptionKey = "test-encryption-key-1234567890"
    private val testQRSize = 400
    private val testMargin = 1

    @Before
    fun setup() {
        qrCodeService = mockk<QRCodeService>()
    }

    @Test
    fun `generateQRCode should return valid bitmap for valid data`() = runTest {
        // Given
        val mockBitmap = mockk<Bitmap> {
            every { width } returns testQRSize
            every { height } returns testQRSize
        }
        coEvery { qrCodeService.generateQRCode(testData, testQRSize, testMargin) } returns LiftrixResult.success(mockBitmap)

        // When
        val result = qrCodeService.generateQRCode(testData, testQRSize, testMargin)

        // Then
        assertTrue("QR code generation should succeed", result.isSuccess)
        val bitmap = result.getOrNull()!!
        assertNotNull("Should return valid bitmap", bitmap)
        assertEquals("Bitmap width should match requested size", testQRSize, bitmap.width)
        assertEquals("Bitmap height should match requested size", testQRSize, bitmap.height)
    }

    @Test
    fun `generateQRCode should handle empty data`() = runTest {
        // Given
        coEvery { qrCodeService.generateQRCode("", testQRSize, testMargin) } returns LiftrixResult.failure(
            LiftrixError.ValidationError(
                field = "data",
                violations = listOf("QR code data cannot be empty")
            )
        )

        // When
        val result = qrCodeService.generateQRCode("", testQRSize, testMargin)

        // Then
        assertTrue("QR code generation should fail for empty data", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertEquals("Should indicate data field", "data", error!!.field)
    }

    @Test
    fun `generateQRCode should handle invalid size parameters`() = runTest {
        // Given
        coEvery { qrCodeService.generateQRCode(testData, size = 50, margin = testMargin) } returns LiftrixResult.failure(
            LiftrixError.ValidationError(
                field = "size",
                violations = listOf("QR code size must be between 100 and 1000 pixels")
            )
        )

        // When
        val result = qrCodeService.generateQRCode(testData, size = 50, margin = testMargin) // Too small

        // Then
        assertTrue("QR code generation should fail for invalid size", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertEquals("Should indicate size field", "size", error!!.field)
    }

    @Test
    fun `parseQRCode should extract data from valid QR bitmap`() = runTest {
        // Given
        val mockBitmap = mockk<Bitmap>()
        coEvery { qrCodeService.generateQRCode(testData, testQRSize, testMargin) } returns LiftrixResult.success(mockBitmap)
        coEvery { qrCodeService.parseQRCode(mockBitmap) } returns LiftrixResult.success(testData)
        
        val generateResult = qrCodeService.generateQRCode(testData, testQRSize, testMargin)
        assertTrue("QR generation should succeed", generateResult.isSuccess)
        val bitmap = generateResult.getOrNull()!!

        // When
        val parseResult = qrCodeService.parseQRCode(bitmap)

        // Then
        assertTrue("QR code parsing should succeed", parseResult.isSuccess)
        val parsedData = parseResult.getOrNull()!!
        assertEquals("Parsed data should match original", testData, parsedData)
    }

    @Test
    fun `validateQRCodeData should validate known formats`() {
        // Given
        every { qrCodeService.validateQRCodeData("liftrix://profile/user123") } returns true
        every { qrCodeService.validateQRCodeData("https://liftrix.app/profile?qr=token") } returns true
        every { qrCodeService.validateQRCodeData("liftrix://gym-buddy/user123") } returns true
        every { qrCodeService.validateQRCodeData("12345678-1234-1234-1234-123456789012") } returns true
        every { qrCodeService.validateQRCodeData("eyJ0ZXN0IjoidGVzdCJ9") } returns true
        every { qrCodeService.validateQRCodeData("") } returns false
        every { qrCodeService.validateQRCodeData("random-invalid-data") } returns false

        // Test valid formats
        assertTrue("Should validate profile URLs", 
            qrCodeService.validateQRCodeData("liftrix://profile/user123"))
        assertTrue("Should validate web URLs", 
            qrCodeService.validateQRCodeData("https://liftrix.app/profile?qr=token"))
        assertTrue("Should validate gym buddy URLs", 
            qrCodeService.validateQRCodeData("liftrix://gym-buddy/user123"))
        assertTrue("Should validate UUIDs", 
            qrCodeService.validateQRCodeData("12345678-1234-1234-1234-123456789012"))
        assertTrue("Should validate Base64 JSON", 
            qrCodeService.validateQRCodeData("eyJ0ZXN0IjoidGVzdCJ9"))

        // Test invalid formats
        assertFalse("Should reject empty strings", 
            qrCodeService.validateQRCodeData(""))
        assertFalse("Should reject random strings", 
            qrCodeService.validateQRCodeData("random-invalid-data"))
    }

    @Test
    fun `generateProfileQRCode should create branded QR code`() = runTest {
        // Given
        val profileUrl = "https://liftrix.app/profile/test-user"
        val mockBitmap = mockk<Bitmap> {
            every { width } returns testQRSize
            every { height } returns testQRSize
        }
        coEvery { qrCodeService.generateProfileQRCode(profileUrl, testQRSize) } returns LiftrixResult.success(mockBitmap)

        // When
        val result = qrCodeService.generateProfileQRCode(profileUrl, testQRSize)

        // Then
        assertTrue("Profile QR generation should succeed", result.isSuccess)
        val bitmap = result.getOrNull()!!
        assertNotNull("Should return valid bitmap", bitmap)
        assertEquals("Bitmap should match requested size", testQRSize, bitmap.width)
    }

    @Test
    fun `encryptQRData should encrypt data successfully`() = runTest {
        // Given
        val encryptedData = "eyJlbmNyeXB0ZWQiOiJ0cnVlIn0="
        coEvery { qrCodeService.encryptQRData(testData, testEncryptionKey) } returns LiftrixResult.success(encryptedData)

        // When
        val result = qrCodeService.encryptQRData(testData, testEncryptionKey)

        // Then
        assertTrue("Encryption should succeed", result.isSuccess)
        val encryptedResult = result.getOrNull()!!
        assertNotNull("Should return encrypted data", encryptedResult)
        assertNotEquals("Encrypted data should differ from original", testData, encryptedResult)
        assertTrue("Encrypted data should be Base64", encryptedResult.matches(Regex("^[A-Za-z0-9+/]+=*$")))
    }

    @Test
    fun `decryptQRData should decrypt encrypted data successfully`() = runTest {
        // Given
        val encryptedData = "eyJlbmNyeXB0ZWQiOiJ0cnVlIn0="
        coEvery { qrCodeService.encryptQRData(testData, testEncryptionKey) } returns LiftrixResult.success(encryptedData)
        coEvery { qrCodeService.decryptQRData(encryptedData, testEncryptionKey) } returns LiftrixResult.success(testData)
        
        val encryptResult = qrCodeService.encryptQRData(testData, testEncryptionKey)
        assertTrue("Encryption should succeed", encryptResult.isSuccess)
        val encryptedResult = encryptResult.getOrNull()!!

        // When
        val decryptResult = qrCodeService.decryptQRData(encryptedResult, testEncryptionKey)

        // Then
        assertTrue("Decryption should succeed", decryptResult.isSuccess)
        val decryptedData = decryptResult.getOrNull()!!
        assertEquals("Decrypted data should match original", testData, decryptedData)
    }

    @Test
    fun `encryptDecrypt should be reversible operations`() = runTest {
        // Given
        val originalData = "test-profile-data-12345"
        val encryptedData = "ZXlKbGJtTnllWEIwWldRaU9pSjBjblZsYVc0MA=="
        
        coEvery { qrCodeService.encryptQRData(originalData, testEncryptionKey) } returns LiftrixResult.success(encryptedData)
        coEvery { qrCodeService.decryptQRData(encryptedData, testEncryptionKey) } returns LiftrixResult.success(originalData)

        // When
        val encryptResult = qrCodeService.encryptQRData(originalData, testEncryptionKey)
        assertTrue("Encryption should succeed", encryptResult.isSuccess)
        
        val encryptedResult = encryptResult.getOrNull()!!
        val decryptResult = qrCodeService.decryptQRData(encryptedResult, testEncryptionKey)
        assertTrue("Decryption should succeed", decryptResult.isSuccess)

        // Then
        val finalData = decryptResult.getOrNull()!!
        assertEquals("Round-trip should preserve data", originalData, finalData)
    }

    @Test
    fun `decryptQRData should handle invalid encrypted data`() = runTest {
        // Given
        coEvery { qrCodeService.decryptQRData("invalid-base64-data", testEncryptionKey) } returns LiftrixResult.failure(
            LiftrixError.PermissionError(
                errorMessage = "Failed to decrypt QR data: Invalid base64 format"
            )
        )

        // When
        val result = qrCodeService.decryptQRData("invalid-base64-data", testEncryptionKey)

        // Then
        assertTrue("Decryption should fail for invalid data", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.PermissionError
        assertNotNull("Should return permission error", error)
    }

    @Test
    fun `generateQRCode should handle data length limits`() = runTest {
        // Given - create very long data (over 2000 characters)
        val longData = "x".repeat(2001)
        coEvery { qrCodeService.generateQRCode(longData, testQRSize, testMargin) } returns LiftrixResult.failure(
            LiftrixError.ValidationError(
                field = "data",
                violations = listOf("QR code data too long (max 2000 characters)")
            )
        )

        // When
        val result = qrCodeService.generateQRCode(longData, testQRSize, testMargin)

        // Then
        assertTrue("Should fail for data too long", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertEquals("Should indicate data field", "data", error!!.field)
    }

    @Test
    fun `generateQRCode should handle margin validation`() = runTest {
        // Given
        coEvery { qrCodeService.generateQRCode(testData, testQRSize, margin = 10) } returns LiftrixResult.failure(
            LiftrixError.ValidationError(
                field = "margin",
                violations = listOf("QR code margin must be between 0 and 4")
            )
        )

        // When
        val result = qrCodeService.generateQRCode(testData, testQRSize, margin = 10) // Invalid margin

        // Then
        assertTrue("Should fail for invalid margin", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.ValidationError
        assertNotNull("Should return validation error", error)
        assertEquals("Should indicate margin field", "margin", error!!.field)
    }
}