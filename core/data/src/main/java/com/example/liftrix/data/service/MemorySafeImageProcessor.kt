package com.example.liftrix.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Memory-safe image processing service to prevent OOM crashes.
 *
 * Features:
 * - Automatic downsampling for large images
 * - Progressive compression with quality fallback
 * - EXIF orientation correction
 * - Target size enforcement (max 2MB per image)
 * - Memory pressure detection and adaptive processing
 *
 * Prevents "java.lang.OutOfMemoryError: Failed to allocate a ... byte allocation with ... free bytes and ... until OOM"
 * that occurs when loading 10MB+ photos on devices with limited heap.
 */
@Singleton
class MemorySafeImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024 // 2MB max per image
        private const val MAX_DIMENSION = 2048 // Max width/height in pixels
        private const val MIN_DIMENSION = 512 // Min dimension after downsampling
        private const val INITIAL_QUALITY = 90 // Starting JPEG quality
        private const val MIN_QUALITY = 60 // Minimum acceptable quality
        private const val QUALITY_STEP = 5 // Quality reduction per iteration
        private const val LOW_MEMORY_THRESHOLD_MB = 32 // Consider low memory if < 32MB available
    }

    /**
     * Process image from URI with memory-safe loading and compression.
     *
     * Steps:
     * 1. Check available memory
     * 2. Calculate safe inSampleSize to avoid OOM
     * 3. Load downsampled bitmap
     * 4. Correct EXIF orientation
     * 5. Progressively compress until target size met
     *
     * @param uri Image URI (content:// or file://)
     * @return ProcessedImageResult with compressed bytes and metadata
     */
    suspend fun processImage(uri: Uri): LiftrixResult<ProcessedImageResult> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is InsufficientMemoryException -> LiftrixError.BusinessLogicError(
                    code = "INSUFFICIENT_MEMORY",
                    errorMessage = throwable.message ?: "Device has insufficient memory to process this image",
                    analyticsContext = mapOf("uri" to uri.toString())
                )
                is ImageTooLargeException -> LiftrixError.BusinessLogicError(
                    code = "IMAGE_TOO_LARGE",
                    errorMessage = throwable.message ?: "Image is too large even after maximum compression",
                    analyticsContext = mapOf("uri" to uri.toString())
                )
                else -> LiftrixError.BusinessLogicError(
                    code = "IMAGE_PROCESSING_FAILED",
                    errorMessage = "Failed to process image: ${throwable.message}",
                    analyticsContext = mapOf("uri" to uri.toString())
                )
            }
        }
    ) {
        // 1. Check available memory
        checkMemoryAvailable()

        // 2. Get image dimensions without loading full bitmap
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight

        if (originalWidth <= 0 || originalHeight <= 0) {
            throw IllegalArgumentException("Invalid image dimensions: ${originalWidth}x${originalHeight}")
        }

        // 3. Calculate safe sample size
        val sampleSize = calculateInSampleSize(options, MAX_DIMENSION, MAX_DIMENSION)

        // 4. Load downsampled bitmap
        val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            val bitmapOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(input, null, bitmapOptions)
        } ?: throw IllegalArgumentException("Failed to decode image from URI: $uri")

        try {
            // 5. Correct EXIF orientation
            val orientedBitmap = correctOrientation(uri, decodedBitmap)

            // 6. Progressive compression to target size
            val compressedBytes = compressToTargetSize(orientedBitmap, MAX_IMAGE_SIZE_BYTES)

            ProcessedImageResult(
                compressedBytes = compressedBytes,
                width = orientedBitmap.width,
                height = orientedBitmap.height,
                sizeKb = compressedBytes.size / 1024,
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                compressionRatio = (compressedBytes.size.toFloat() / (originalWidth * originalHeight * 4))
            )
        } finally {
            decodedBitmap.recycle()
        }
    }

    /**
     * Calculate safe inSampleSize to avoid OOM.
     * Uses power-of-2 values for efficiency.
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        // Additional safety: Check estimated memory usage
        val estimatedMemoryMB = (width * height * 4) / inSampleSize / inSampleSize / (1024 * 1024)
        if (estimatedMemoryMB > LOW_MEMORY_THRESHOLD_MB) {
            // Further reduce if estimated memory is too high
            inSampleSize *= 2
        }

        return inSampleSize
    }

    /**
     * Correct image orientation based on EXIF data.
     * Many cameras save images rotated and rely on EXIF orientation tag.
     */
    private fun correctOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap // No rotation needed
        }

        return try {
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            bitmap // Return original if rotation fails
        }
    }

    /**
     * Progressively compress bitmap until target size is met.
     * Reduces quality in steps if initial compression is too large.
     */
    private fun compressToTargetSize(bitmap: Bitmap, targetSizeBytes: Int): ByteArray {
        var quality = INITIAL_QUALITY
        var compressedBytes: ByteArray

        do {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            compressedBytes = outputStream.toByteArray()

            if (compressedBytes.size <= targetSizeBytes) {
                break // Target size met
            }

            quality -= QUALITY_STEP

            if (quality < MIN_QUALITY) {
                // If we can't meet target size even at min quality, try downscaling
                val scaleFactor = sqrt(targetSizeBytes.toFloat() / compressedBytes.size)
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scaleFactor).toInt(),
                    (bitmap.height * scaleFactor).toInt(),
                    true
                )

                try {
                    val finalStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, MIN_QUALITY, finalStream)
                    compressedBytes = finalStream.toByteArray()

                    if (compressedBytes.size > targetSizeBytes) {
                        throw ImageTooLargeException(
                            "Image cannot be compressed to ${targetSizeBytes / 1024}KB " +
                                    "even after maximum compression (current: ${compressedBytes.size / 1024}KB)"
                        )
                    }
                } finally {
                    if (scaledBitmap != bitmap) {
                        scaledBitmap.recycle()
                    }
                }

                break
            }
        } while (compressedBytes.size > targetSizeBytes && quality >= MIN_QUALITY)

        return compressedBytes
    }

    /**
     * Check if device has sufficient memory to process images.
     * Throws InsufficientMemoryException if low memory.
     */
    private fun checkMemoryAvailable() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val availableMemory = maxMemory - usedMemory
        val availableMemoryMB = availableMemory / (1024 * 1024)

        if (availableMemoryMB < LOW_MEMORY_THRESHOLD_MB) {
            throw InsufficientMemoryException(
                "Insufficient memory to process image. Available: ${availableMemoryMB}MB, Required: ${LOW_MEMORY_THRESHOLD_MB}MB. " +
                        "Try closing other apps or freeing up device memory."
            )
        }
    }
}

/**
 * Result of image processing with compression metadata.
 */
data class ProcessedImageResult(
    val compressedBytes: ByteArray,
    val width: Int,
    val height: Int,
    val sizeKb: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val compressionRatio: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProcessedImageResult

        if (!compressedBytes.contentEquals(other.compressedBytes)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (sizeKb != other.sizeKb) return false

        return true
    }

    override fun hashCode(): Int {
        var result = compressedBytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + sizeKb
        return result
    }
}

/**
 * Exception thrown when device has insufficient memory for image processing.
 */
class InsufficientMemoryException(message: String) : Exception(message)

/**
 * Exception thrown when image cannot be compressed to target size.
 */
class ImageTooLargeException(message: String) : Exception(message)
