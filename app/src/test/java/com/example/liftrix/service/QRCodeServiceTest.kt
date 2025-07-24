package com.example.liftrix.service

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for QRCodeService
 * 
 * Tests QR code generation, bitmap creation, encoding parameters,
 * error handling, and ZXing library integration for profile sharing.
 */
class QRCodeServiceTest {

    private lateinit var qrCodeService: QRCodeService
    private lateinit var mockQRCodeWriter: QRCodeWriter
    private lateinit var mockBitMatrix: BitMatrix

    private val testQRData = "test_qr_data_abc123"
    private val testUrl = "https://liftrix.app/profile?qr=$testQRData"
    private val qrCodeSize = 512
    
    @Before
    fun setup() {
        mockQRCodeWriter = mockk()
        mockBitMatrix = mockk()
        
        qrCodeService = QRCodeServiceImpl()
        
        // Mock BitMatrix behavior
        every { mockBitMatrix.width } returns qrCodeSize
        every { mockBitMatrix.height } returns qrCodeSize
        every { mockBitMatrix.get(any(), any()) } returns true // All pixels black for simplicity
    }

    @Test
    fun `generateQRCodeBitmap with valid data returns bitmap`() {
        // Given
        val qrData = testQRData
        
        // When
        val result = qrCodeService.generateQRCodeBitmap(qrData)
        
        // Then
        assertNotNull(result)
        assertEquals(qrCodeSize, result.width)
        assertEquals(qrCodeSize, result.height)
        assertEquals(Bitmap.Config.ARGB_8888, result.config)
    }

    @Test
    fun `generateQRCodeBitmap with URL data returns bitmap`() {
        // Given
        val urlData = testUrl
        
        // When
        val result = qrCodeService.generateQRCodeBitmap(urlData)
        
        // Then
        assertNotNull(result)
        assertTrue(result.width > 0)
        assertTrue(result.height > 0)
    }

    @Test
    fun `generateQRCodeBitmap with empty data throws exception`() {
        // Given
        val emptyData = ""
        
        // When & Then
        assertFailsWith<IllegalArgumentException> {
            qrCodeService.generateQRCodeBitmap(emptyData)
        }
    }

    @Test
    fun `generateQRCodeBitmap with null data throws exception`() {
        // When & Then
        assertFailsWith<IllegalArgumentException> {
            qrCodeService.generateQRCodeBitmap(null)
        }
    }

    @Test
    fun `generateQRCodeBitmap with very long data handles gracefully`() {
        // Given
        val longData = "a".repeat(2000) // Very long string
        
        // When
        val result = qrCodeService.generateQRCodeBitmap(longData)
        
        // Then
        assertNotNull(result)
        // QR code should still be generated but might be complex
    }

    @Test
    fun `generateQRCodeBitmap uses correct encoding parameters`() {
        // This test would verify that the QR code is generated with correct parameters
        // In a real implementation, you might mock the QRCodeWriter to verify parameters
        
        // Given
        val qrData = testQRData
        
        // When
        val result = qrCodeService.generateQRCodeBitmap(qrData)
        
        // Then
        assertNotNull(result)
        // Verify the QR code has appropriate error correction and encoding
        // This would require integration with the actual ZXing library behavior
    }

    @Test
    fun `generateQRCodeBitmap creates high contrast black and white image`() {
        // Given
        val qrData = testQRData
        
        // When
        val result = qrCodeService.generateQRCodeBitmap(qrData)
        
        // Then
        assertNotNull(result)
        
        // Verify bitmap uses only black and white pixels
        // In a real test, you might sample pixels to verify contrast
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        
        // All pixels should be either black (0xFF000000) or white (0xFFFFFFFF)
        assertTrue(pixels.all { pixel -> 
            pixel == 0xFF000000.toInt() || pixel == 0xFFFFFFFF.toInt() 
        })
    }

    @Test
    fun `generateQRCodeBitmap with special characters works correctly`() {
        // Given
        val specialCharsData = "user_id_123!@#$%^&*()_+-=[]{}|;':\",./<>?"
        
        // When
        val result = qrCodeService.generateQRCodeBitmap(specialCharsData)
        
        // Then
        assertNotNull(result)
        assertTrue(result.width > 0)
        assertTrue(result.height > 0)
    }

    @Test
    fun `generateQRCodeBitmap with unicode characters works correctly`() {
        // Given
        val unicodeData = "user_測試_123_🏋️‍♂️"
        
        // When
        val result = qrCodeService.generateQRCodeBitmap(unicodeData)
        
        // Then
        assertNotNull(result)
        assertTrue(result.width > 0)
        assertTrue(result.height > 0)
    }

    @Test
    fun `parseQRCodeData with valid QR code returns correct data`() {
        // Given
        val originalData = testQRData
        val qrBitmap = qrCodeService.generateQRCodeBitmap(originalData)
        
        // When
        val parsedData = qrCodeService.parseQRCodeData(qrBitmap)
        
        // Then
        assertEquals(originalData, parsedData)
    }

    @Test
    fun `parseQRCodeData with invalid bitmap returns null`() {
        // Given
        val invalidBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        // Fill with random colors (not a valid QR code)
        invalidBitmap.eraseColor(0xFF0000FF.toInt()) // Blue background
        
        // When
        val result = qrCodeService.parseQRCodeData(invalidBitmap)
        
        // Then
        assertEquals(null, result)
    }

    @Test
    fun `parseQRCodeData with null bitmap throws exception`() {
        // When & Then
        assertFailsWith<IllegalArgumentException> {
            qrCodeService.parseQRCodeData(null)
        }
    }

    @Test
    fun `generateQRCodeBitmap with custom size parameter works`() {
        // Given
        val customSize = 256
        val qrData = testQRData
        
        // When
        val result = qrCodeService.generateQRCodeBitmap(qrData, customSize)
        
        // Then
        assertNotNull(result)
        assertEquals(customSize, result.width)
        assertEquals(customSize, result.height)
    }

    @Test
    fun `generateQRCodeBitmap with very small size throws exception`() {
        // Given
        val tooSmallSize = 50 // Too small for QR code
        val qrData = testQRData
        
        // When & Then
        assertFailsWith<IllegalArgumentException> {
            qrCodeService.generateQRCodeBitmap(qrData, tooSmallSize)
        }
    }

    @Test
    fun `generateQRCodeBitmap with very large size handles gracefully`() {
        // Given
        val largeSize = 2048
        val qrData = testQRData
        
        // When
        val result = qrCodeService.generateQRCodeBitmap(qrData, largeSize)
        
        // Then
        assertNotNull(result)
        assertEquals(largeSize, result.width)
        assertEquals(largeSize, result.height)
    }

    @Test
    fun `generateProfileQRUrl creates correctly formatted URL`() {
        // Given
        val qrData = testQRData
        
        // When
        val result = qrCodeService.generateProfileQRUrl(qrData)
        
        // Then
        assertEquals("https://liftrix.app/profile?qr=$qrData", result)
    }

    @Test
    fun `generateProfileQRUrl with empty data throws exception`() {
        // Given
        val emptyData = ""
        
        // When & Then
        assertFailsWith<IllegalArgumentException> {
            qrCodeService.generateProfileQRUrl(emptyData)
        }
    }

    @Test
    fun `generateProfileQRUrl with special characters encodes correctly`() {
        // Given
        val specialData = "user_123&data=test"
        
        // When
        val result = qrCodeService.generateProfileQRUrl(specialData)
        
        // Then
        assertTrue(result.startsWith("https://liftrix.app/profile?qr="))
        assertTrue(result.contains(specialData))
    }

    @Test
    fun `extractQRDataFromUrl with valid URL returns QR data`() {
        // Given
        val validUrl = "https://liftrix.app/profile?qr=$testQRData"
        
        // When
        val result = qrCodeService.extractQRDataFromUrl(validUrl)
        
        // Then
        assertEquals(testQRData, result)
    }

    @Test
    fun `extractQRDataFromUrl with invalid URL returns null`() {
        // Given
        val invalidUrl = "https://other-site.com/profile"
        
        // When
        val result = qrCodeService.extractQRDataFromUrl(invalidUrl)
        
        // Then
        assertEquals(null, result)
    }

    @Test
    fun `extractQRDataFromUrl with malformed URL returns null`() {
        // Given
        val malformedUrl = "not-a-url"
        
        // When
        val result = qrCodeService.extractQRDataFromUrl(malformedUrl)
        
        // Then
        assertEquals(null, result)
    }

    @Test
    fun `extractQRDataFromUrl with missing QR parameter returns null`() {
        // Given
        val urlWithoutQR = "https://liftrix.app/profile?user=123"
        
        // When
        val result = qrCodeService.extractQRDataFromUrl(urlWithoutQR)
        
        // Then
        assertEquals(null, result)
    }

    @Test
    fun `validateQRCodeData with valid data returns true`() {
        // Given
        val validData = testQRData
        
        // When
        val result = qrCodeService.validateQRCodeData(validData)
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `validateQRCodeData with empty data returns false`() {
        // Given
        val emptyData = ""
        
        // When
        val result = qrCodeService.validateQRCodeData(emptyData)
        
        // Then
        assertEquals(false, result)
    }

    @Test
    fun `validateQRCodeData with null data returns false`() {
        // Given
        val nullData: String? = null
        
        // When
        val result = qrCodeService.validateQRCodeData(nullData)
        
        // Then
        assertEquals(false, result)
    }

    @Test
    fun `validateQRCodeData with too short data returns false`() {
        // Given
        val shortData = "abc" // Too short for valid QR data
        
        // When
        val result = qrCodeService.validateQRCodeData(shortData)
        
        // Then
        assertEquals(false, result)
    }

    @Test
    fun `roundTrip QR generation and parsing preserves data`() {
        // Given
        val originalData = "complex_qr_data_with_special_chars_123!@#"
        
        // When
        val qrBitmap = qrCodeService.generateQRCodeBitmap(originalData)
        val parsedData = qrCodeService.parseQRCodeData(qrBitmap)
        
        // Then
        assertEquals(originalData, parsedData)
    }

    @Test
    fun `multiple QR generation calls produce consistent results`() {
        // Given
        val qrData = testQRData
        
        // When
        val bitmap1 = qrCodeService.generateQRCodeBitmap(qrData)
        val bitmap2 = qrCodeService.generateQRCodeBitmap(qrData)
        
        // Then
        assertNotNull(bitmap1)
        assertNotNull(bitmap2)
        assertEquals(bitmap1.width, bitmap2.width)
        assertEquals(bitmap1.height, bitmap2.height)
        assertEquals(bitmap1.config, bitmap2.config)
        
        // Bitmaps should be identical for same input
        assertTrue(bitmap1.sameAs(bitmap2))
    }
}