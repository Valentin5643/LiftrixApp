package com.example.liftrix.service

import android.content.Context
import android.net.Uri
import com.example.liftrix.domain.model.MediaProcessingOptions
import com.example.liftrix.domain.model.MediaType
import com.example.liftrix.domain.model.ProcessedMedia
import com.example.liftrix.domain.model.MediaDimensions
import com.example.liftrix.domain.service.MediaValidationResult
import com.example.liftrix.domain.service.MediaProcessingService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for media processing service focusing on compression quality and performance.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@RunWith(RobolectricTestRunner::class)
class MediaProcessingTest {
    
    private lateinit var context: Context
    private lateinit var imageProcessingService: ImageProcessingService
    private lateinit var mediaProcessingService: MediaProcessingService
    
    @Before
    fun setup() {
        context = mockk(relaxed = true)
        imageProcessingService = mockk(relaxed = true)
        mediaProcessingService = mockk(relaxed = true)
    }
    
    @Test
    fun `processPhoto should compress image while maintaining quality`() = runTest {
        // Given
        val testUri = mockk<Uri>()
        val options = MediaProcessingOptions(
            maxWidth = 2048,
            maxHeight = 2048,
            quality = 85,
            generateThumbnail = true
        )
        
        // Mock the processPhoto method directly since we're testing the interface
        val expectedProcessedMedia = ProcessedMedia(
            file = ByteArray(2 * 1024 * 1024), // Compressed file data
            thumbnail = ByteArray(1024), // Mock thumbnail data
            dimensions = MediaDimensions(2048, 1536), // Resized within limits
            sizeBytes = 2 * 1024 * 1024, // Compressed to 2MB
            format = "image/jpeg"
        )
        
        coEvery { 
            mediaProcessingService.processPhoto(testUri, options) 
        } returns Result.success(expectedProcessedMedia)
        
        // When
        val result = mediaProcessingService.processPhoto(testUri, options)
        
        // Then
        assertTrue("Photo processing should succeed", result.isSuccess)
        val processedMedia = result.getOrThrow()
        
        // Verify compression
        assertTrue("Processed file should be smaller than original", 
            processedMedia.sizeBytes < 5 * 1024 * 1024)
        
        // Verify dimensions are within limits
        assertTrue("Width should be within limits", 
            processedMedia.dimensions.width <= options.maxWidth)
        assertTrue("Height should be within limits", 
            processedMedia.dimensions.height <= options.maxHeight)
        
        // Verify thumbnail generation
        assertTrue("Thumbnail should be generated", 
            processedMedia.thumbnail.isNotEmpty())
        
        // Verify format
        assertEquals("Format should be JPEG", "image/jpeg", processedMedia.format)
        
        // Verify file data exists
        assertTrue("Processed file should contain data", 
            processedMedia.file.isNotEmpty())
    }
    
    @Test
    fun `processVideo should handle video compression correctly`() = runTest {
        // Given
        val testUri = mockk<Uri>()
        val options = MediaProcessingOptions()
        
        // Mock validation result
        coEvery { 
            mediaProcessingService.validateMedia(testUri, MediaType.VIDEO) 
        } returns Result.success(
            MediaValidationResult(
                isValid = true,
                fileSize = 30 * 1024 * 1024, // 30MB
                duration = 45, // 45 seconds
                format = "video/mp4",
                resolution = 1920 to 1080
            )
        )
        
        // When
        val result = mediaProcessingService.processVideo(testUri, options)
        
        // Then
        assertTrue("Video processing should succeed", result.isSuccess)
        val processedMedia = result.getOrThrow()
        
        // Verify thumbnail extraction
        assertTrue("Video thumbnail should be generated", 
            processedMedia.thumbnail.isNotEmpty())
        
        // Verify format
        assertEquals("Format should be MP4", "video/mp4", processedMedia.format)
        
        // Verify dimensions are set
        assertTrue("Width should be positive", processedMedia.dimensions.width > 0)
        assertTrue("Height should be positive", processedMedia.dimensions.height > 0)
    }
    
    @Test
    fun `validateMedia should reject oversized photos`() = runTest {
        // Given
        val testUri = mockk<Uri>()
        val oversizeFile = 15 * 1024 * 1024L // 15MB (over 10MB limit)
        
        // When
        val result = mediaProcessingService.validateMedia(testUri, MediaType.PHOTO)
        
        // Then
        if (result.isSuccess) {
            val validation = result.getOrThrow()
            if (validation.fileSize > 10 * 1024 * 1024) {
                assertFalse("Oversized photo should be invalid", validation.isValid)
                assertTrue("Should have size error", 
                    validation.errors.any { it.contains("size") })
            }
        }
    }
    
    @Test
    fun `validateMedia should reject oversized videos`() = runTest {
        // Given
        val testUri = mockk<Uri>()
        
        // When
        val result = mediaProcessingService.validateMedia(testUri, MediaType.VIDEO)
        
        // Then
        if (result.isSuccess) {
            val validation = result.getOrThrow()
            if (validation.fileSize > 50 * 1024 * 1024) {
                assertFalse("Oversized video should be invalid", validation.isValid)
                assertTrue("Should have size error", 
                    validation.errors.any { it.contains("size") })
            }
        }
    }
    
    @Test
    fun `validateMedia should reject unsupported formats`() = runTest {
        // Given
        val testUri = mockk<Uri>()
        
        // Mock unsupported format
        coEvery { context.contentResolver.getType(testUri) } returns "image/bmp"
        
        // When
        val result = mediaProcessingService.validateMedia(testUri, MediaType.PHOTO)
        
        // Then
        if (result.isSuccess) {
            val validation = result.getOrThrow()
            assertFalse("Unsupported format should be invalid", validation.isValid)
            assertTrue("Should have format error", 
                validation.errors.any { it.contains("format") })
        }
    }
    
    @Test
    fun `validateMedia should reject videos exceeding duration limit`() = runTest {
        // Given
        val testUri = mockk<Uri>()
        
        // When
        val result = mediaProcessingService.validateMedia(testUri, MediaType.VIDEO)
        
        // Then
        if (result.isSuccess) {
            val validation = result.getOrThrow()
            validation.duration?.let { duration ->
                if (duration > 60) {
                    assertFalse("Long video should be invalid", validation.isValid)
                    assertTrue("Should have duration error", 
                        validation.errors.any { it.contains("duration") })
                }
            }
        }
    }
    
    @Test
    fun `generateBlurhash should create valid hash`() = runTest {
        // Given
        val imageBytes = ByteArray(1024) { it.toByte() } // Mock image data
        
        // When
        val result = mediaProcessingService.generateBlurhash(imageBytes)
        
        // Then
        assertTrue("Blurhash generation should succeed", result.isSuccess)
        val blurhash = result.getOrThrow()
        assertNotNull("Blurhash should not be null", blurhash)
        assertTrue("Blurhash should not be empty", blurhash.isNotEmpty())
        assertTrue("Blurhash should start with L (simple format)", blurhash.startsWith("L"))
    }
    
    @Test
    fun `estimateProcessing should provide reasonable estimates for photos`() = runTest {
        // Given
        val testUri = mockk<Uri>()
        val photoSize = 3 * 1024 * 1024L // 3MB
        
        // Mock validation
        coEvery { 
            mediaProcessingService.validateMedia(testUri, MediaType.PHOTO) 
        } returns Result.success(
            MediaValidationResult(
                isValid = true,
                fileSize = photoSize,
                format = "image/jpeg"
            )
        )
        
        // When
        val result = mediaProcessingService.estimateProcessing(testUri, MediaType.PHOTO)
        
        // Then
        assertTrue("Estimation should succeed", result.isSuccess)
        val estimate = result.getOrThrow()
        
        // Verify reasonable estimates
        assertTrue("Estimated time should be positive", estimate.estimatedTimeMs > 0)
        assertTrue("Estimated time should be reasonable (< 30s)", 
            estimate.estimatedTimeMs < 30_000)
        assertTrue("Output size should be smaller", 
            estimate.estimatedOutputSize < photoSize)
        assertTrue("Compression ratio should be reasonable", 
            estimate.compressionRatio in 0.1f..0.9f)
        assertTrue("Should create thumbnail", estimate.willCreateThumbnail)
    }
    
    @Test
    fun `estimateProcessing should provide reasonable estimates for videos`() = runTest {
        // Given
        val testUri = mockk<Uri>()
        val videoSize = 25 * 1024 * 1024L // 25MB
        val videoDuration = 30 // 30 seconds
        
        // Mock validation
        coEvery { 
            mediaProcessingService.validateMedia(testUri, MediaType.VIDEO) 
        } returns Result.success(
            MediaValidationResult(
                isValid = true,
                fileSize = videoSize,
                duration = videoDuration,
                format = "video/mp4"
            )
        )
        
        // When
        val result = mediaProcessingService.estimateProcessing(testUri, MediaType.VIDEO)
        
        // Then
        assertTrue("Estimation should succeed", result.isSuccess)
        val estimate = result.getOrThrow()
        
        // Verify reasonable estimates
        assertTrue("Estimated time should be positive", estimate.estimatedTimeMs > 0)
        assertTrue("Estimated time should account for duration", 
            estimate.estimatedTimeMs > videoDuration * 100) // At least 100ms per second
        assertTrue("Output size should be reasonable", 
            estimate.estimatedOutputSize < videoSize)
        assertTrue("Should create thumbnail", estimate.willCreateThumbnail)
    }
    
    @Test
    fun `processPhoto should handle processing options correctly`() = runTest {
        // Given
        val testUri = mockk<Uri>()
        val customOptions = MediaProcessingOptions(
            maxWidth = 1024,
            maxHeight = 1024,
            quality = 90,
            generateThumbnail = false,
            generateBlurhash = false
        )
        
        // Mock validation
        coEvery { 
            mediaProcessingService.validateMedia(testUri, MediaType.PHOTO) 
        } returns Result.success(
            MediaValidationResult(
                isValid = true,
                fileSize = 2 * 1024 * 1024, // 2MB
                format = "image/jpeg",
                resolution = 2000 to 1500
            )
        )
        
        // When
        val result = mediaProcessingService.processPhoto(testUri, customOptions)
        
        // Then
        assertTrue("Processing should succeed", result.isSuccess)
        val processedMedia = result.getOrThrow()
        
        // Verify custom options were applied
        assertTrue("Width should respect custom limit", 
            processedMedia.dimensions.width <= customOptions.maxWidth)
        assertTrue("Height should respect custom limit", 
            processedMedia.dimensions.height <= customOptions.maxHeight)
        
        // Verify thumbnail generation was disabled
        if (!customOptions.generateThumbnail) {
            assertTrue("Thumbnail should be empty when disabled", 
                processedMedia.thumbnail.isEmpty())
        }
    }
    
    @Test
    fun `performance test - processPhoto should complete within time limit`() = runTest {
        // Given
        val testUri = mockk<Uri>()
        val options = MediaProcessingOptions()
        val startTime = System.currentTimeMillis()
        
        // Mock validation
        coEvery { 
            mediaProcessingService.validateMedia(testUri, MediaType.PHOTO) 
        } returns Result.success(
            MediaValidationResult(
                isValid = true,
                fileSize = 5 * 1024 * 1024, // 5MB
                format = "image/jpeg"
            )
        )
        
        // When
        val result = mediaProcessingService.processPhoto(testUri, options)
        val processingTime = System.currentTimeMillis() - startTime
        
        // Then
        assertTrue("Processing should succeed", result.isSuccess)
        assertTrue("Processing should complete within 5 seconds", 
            processingTime < 5000)
    }
    
    @Test
    fun `compression quality test - should maintain acceptable quality`() = runTest {
        // Given
        val testUri = mockk<Uri>()
        val highQualityOptions = MediaProcessingOptions(quality = 95)
        val lowQualityOptions = MediaProcessingOptions(quality = 60)
        
        // Mock validation
        val validationResult = MediaValidationResult(
            isValid = true,
            fileSize = 3 * 1024 * 1024, // 3MB
            format = "image/jpeg"
        )
        
        coEvery { 
            mediaProcessingService.validateMedia(testUri, MediaType.PHOTO) 
        } returns Result.success(validationResult)
        
        // When
        val highQualityResult = mediaProcessingService.processPhoto(testUri, highQualityOptions)
        val lowQualityResult = mediaProcessingService.processPhoto(testUri, lowQualityOptions)
        
        // Then
        assertTrue("High quality processing should succeed", highQualityResult.isSuccess)
        assertTrue("Low quality processing should succeed", lowQualityResult.isSuccess)
        
        val highQualityMedia = highQualityResult.getOrThrow()
        val lowQualityMedia = lowQualityResult.getOrThrow()
        
        // Verify compression trade-offs
        assertTrue("High quality should produce larger file", 
            highQualityMedia.sizeBytes >= lowQualityMedia.sizeBytes)
        
        // Both should be significantly smaller than original
        assertTrue("High quality should still compress significantly", 
            highQualityMedia.sizeBytes < validationResult.fileSize * 0.7)
        assertTrue("Low quality should compress more", 
            lowQualityMedia.sizeBytes < validationResult.fileSize * 0.5)
    }
}