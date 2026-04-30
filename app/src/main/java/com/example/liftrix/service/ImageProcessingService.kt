package com.example.liftrix.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for processing profile images with cropping, resizing, and compression.
 * Optimizes images for Firebase Storage upload with consistent quality and size.
 * 
 * Processing Pipeline:
 * 1. Load image from URI with proper orientation correction
 * 2. Crop to specified rectangle or auto-crop to square
 * 3. Resize to target dimensions (400x400px for profile images)
 * 4. Compress to JPEG with 85% quality for optimal size/quality balance
 * 
 * Performance targets:
 * - Process 4K image in <3 seconds on mid-range device
 * - Output file size <200KB for standard profile images
 * - Maintain visual quality suitable for 120dp display
 */
@Singleton
class ImageProcessingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        // Profile image specifications from technical spec
        private const val PROFILE_IMAGE_SIZE = 400 // 400x400px square
        private const val JPEG_QUALITY = 85 // 85% quality for size/quality balance
        private const val MAX_FILE_SIZE_BYTES = 1024 * 1024 // 1MB maximum
        private const val TARGET_FILE_SIZE_BYTES = 200 * 1024 // 200KB target
        
        // Supported input formats
        private val SUPPORTED_FORMATS = setOf("image/jpeg", "image/png", "image/webp")
    }
    
    /**
     * Processes a profile image from URI with automatic cropping and optimization.
     * 
     * @param imageUri Source image URI (content:// or file://)
     * @param cropRect Optional crop rectangle, auto-crops to center square if null
     * @return ProcessedImage with compressed JPEG bytes and metadata
     */
    suspend fun processProfileImage(
        imageUri: Uri,
        cropRect: Rect? = null
    ): Result<ProcessedImage> = withContext(Dispatchers.IO) {
        try {
            Timber.d("🖼️ Processing profile image from URI: $imageUri")
            
            // Load and validate input image
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot open image URI"))
            
            val originalBitmap = loadBitmapWithOrientation(inputStream, imageUri)
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot decode image"))
            
            Timber.d("📏 Original image dimensions: ${originalBitmap.width}x${originalBitmap.height}")
            
            // Determine crop rectangle (auto-crop to center square if not provided)
            val finalCropRect = cropRect ?: calculateCenterSquareCrop(originalBitmap)
            
            // Process image through pipeline
            val croppedBitmap = cropBitmap(originalBitmap, finalCropRect)
            val resizedBitmap = resizeBitmap(croppedBitmap, PROFILE_IMAGE_SIZE, PROFILE_IMAGE_SIZE)
            val compressedBytes = compressBitmapToJpeg(resizedBitmap, JPEG_QUALITY)
            
            // Cleanup bitmaps to free memory
            if (croppedBitmap != originalBitmap) {
                croppedBitmap.recycle()
            }
            if (resizedBitmap != croppedBitmap) {
                resizedBitmap.recycle()
            }
            originalBitmap.recycle()
            
            Timber.i("✅ Image processed successfully: ${compressedBytes.size} bytes (target: $TARGET_FILE_SIZE_BYTES)")
            
            Result.success(
                ProcessedImage(
                    imageBytes = compressedBytes,
                    mimeType = "image/jpeg",
                    width = PROFILE_IMAGE_SIZE,
                    height = PROFILE_IMAGE_SIZE,
                    fileSizeBytes = compressedBytes.size
                )
            )
            
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "❌ Out of memory while processing image")
            Result.failure(RuntimeException("Image too large to process", e))
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to process profile image")
            Result.failure(e)
        }
    }
    
    /**
     * Validates if the image URI points to a supported format.
     * 
     * @param imageUri URI to validate
     * @return true if format is supported (JPEG, PNG, WebP)
     */
    suspend fun isFormatSupported(imageUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(imageUri)
            SUPPORTED_FORMATS.contains(mimeType)
        } catch (e: Exception) {
            Timber.w(e, "Failed to check image format for URI: $imageUri")
            false
        }
    }
    
    /**
     * Loads bitmap from input stream with proper EXIF orientation correction.
     * Essential for handling images from camera which may have rotation metadata.
     */
    private fun loadBitmapWithOrientation(inputStream: InputStream, uri: Uri): Bitmap? {
        try {
            // First pass: get image dimensions for optimal loading
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // Calculate sample size for memory-efficient loading
            val sampleSize = calculateInSampleSize(options, PROFILE_IMAGE_SIZE * 2, PROFILE_IMAGE_SIZE * 2)
            
            // Second pass: load bitmap with sample size
            val decodingInputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(decodingInputStream, null, BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            })
            decodingInputStream?.close()
            
            // Apply EXIF orientation correction
            return bitmap?.let { applyExifOrientation(it, uri) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load bitmap with orientation")
            return null
        }
    }
    
    /**
     * Applies EXIF orientation correction to handle rotated camera images.
     */
    private fun applyExifOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = ExifInterface(inputStream!!)
            inputStream.close()
            
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap // No rotation needed
            }
            
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            return rotatedBitmap
        } catch (e: Exception) {
            Timber.w(e, "Failed to apply EXIF orientation, using original bitmap")
            return bitmap
        }
    }
    
    /**
     * Calculates center square crop rectangle for automatic cropping.
     * Used when user doesn't specify custom crop area.
     */
    private fun calculateCenterSquareCrop(bitmap: Bitmap): Rect {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        return Rect(xOffset, yOffset, xOffset + size, yOffset + size)
    }
    
    /**
     * Crops bitmap to specified rectangle with bounds validation.
     */
    private fun cropBitmap(bitmap: Bitmap, cropRect: Rect): Bitmap {
        // Validate and adjust crop rectangle to bitmap bounds
        val adjustedRect = Rect(
            maxOf(0, cropRect.left),
            maxOf(0, cropRect.top),
            minOf(bitmap.width, cropRect.right),
            minOf(bitmap.height, cropRect.bottom)
        )
        
        val width = adjustedRect.width()
        val height = adjustedRect.height()
        
        return if (width > 0 && height > 0) {
            Bitmap.createBitmap(bitmap, adjustedRect.left, adjustedRect.top, width, height)
        } else {
            bitmap // Return original if crop rect is invalid
        }
    }
    
    /**
     * Resizes bitmap to target dimensions using high-quality filtering.
     */
    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else {
            bitmap
        }
    }
    
    /**
     * Compresses bitmap to JPEG with specified quality.
     * Uses progressive quality reduction if target file size is exceeded.
     */
    private fun compressBitmapToJpeg(bitmap: Bitmap, initialQuality: Int): ByteArray {
        var quality = initialQuality
        var compressedBytes: ByteArray
        
        do {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            compressedBytes = outputStream.toByteArray()
            
            // Reduce quality if file is too large
            if (compressedBytes.size > MAX_FILE_SIZE_BYTES && quality > 30) {
                quality -= 10
                Timber.d("📦 Reducing quality to $quality% (size: ${compressedBytes.size} bytes)")
            } else {
                break
            }
        } while (quality > 30)
        
        return compressedBytes
    }
    
    /**
     * Calculates optimal sample size for memory-efficient bitmap loading.
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
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
        
        return inSampleSize
    }
}

/**
 * Result of image processing operation containing optimized image data.
 * 
 * @param imageBytes Compressed JPEG image data ready for upload
 * @param mimeType Always "image/jpeg" for processed profile images
 * @param width Image width in pixels (always 400 for profile images)
 * @param height Image height in pixels (always 400 for profile images)
 * @param fileSizeBytes Size of compressed image in bytes
 */
data class ProcessedImage(
    val imageBytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as ProcessedImage
        
        if (!imageBytes.contentEquals(other.imageBytes)) return false
        if (mimeType != other.mimeType) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (fileSizeBytes != other.fileSizeBytes) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + fileSizeBytes
        return result
    }
}