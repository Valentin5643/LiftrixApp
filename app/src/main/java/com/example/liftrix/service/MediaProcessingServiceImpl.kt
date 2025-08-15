package com.example.liftrix.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.liftrix.domain.model.ImageFormat
import com.example.liftrix.domain.model.MediaDimensions
import com.example.liftrix.domain.model.MediaProcessingOptions
import com.example.liftrix.domain.model.MediaType
import com.example.liftrix.domain.model.ProcessedMedia
import com.example.liftrix.domain.model.VideoResolution
import com.example.liftrix.domain.service.MediaProcessingService
import com.example.liftrix.domain.service.MediaValidationResult
import com.example.liftrix.domain.service.ProcessingEstimate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Enhanced media processing service implementation.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@Singleton
class MediaProcessingServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageProcessingService: ImageProcessingService
) : MediaProcessingService {
    
    companion object {
        // Processing limits and targets
        private const val MAX_PHOTO_SIZE_MB = 10
        private const val MAX_VIDEO_SIZE_MB = 50
        private const val MAX_VIDEO_DURATION_SECONDS = 60
        private const val TARGET_PHOTO_SIZE_KB = 500
        private const val TARGET_VIDEO_BITRATE = 2_000_000 // 2 Mbps
        
        // Thumbnail specifications
        private const val THUMBNAIL_SIZE = 300
        private const val THUMBNAIL_QUALITY = 75
        
        // Supported formats
        private val SUPPORTED_PHOTO_FORMATS = setOf(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic"
        )
        private val SUPPORTED_VIDEO_FORMATS = setOf(
            "video/mp4", "video/mpeg", "video/3gpp", "video/quicktime", "video/x-msvideo"
        )
    }
    
    override suspend fun processPhoto(
        uri: Uri,
        options: MediaProcessingOptions
    ): Result<ProcessedMedia> {
        return try {
            withContext(Dispatchers.IO) {
                Timber.d("🖼️ Processing photo: $uri")
                
                // Validate input
                val validation = validateMedia(uri, MediaType.PHOTO).getOrThrow()
                if (!validation.isValid) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid photo: ${validation.errors.joinToString()}")
                    )
                }
                
                // Load and process bitmap
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Cannot open photo URI")
                    )
                
                var bitmap = BitmapFactory.decodeStream(inputStream)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Cannot decode photo")
                    )
                inputStream.close()
                
                // Resize if needed
                if (bitmap.width > options.maxWidth || bitmap.height > options.maxHeight) {
                    val scaleFactor = min(
                        options.maxWidth.toFloat() / bitmap.width,
                        options.maxHeight.toFloat() / bitmap.height
                    )
                    val newWidth = (bitmap.width * scaleFactor).toInt()
                    val newHeight = (bitmap.height * scaleFactor).toInt()
                    
                    val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                    bitmap.recycle()
                    bitmap = resizedBitmap
                }
                
                // Compress main image
                val compressedBytes = compressBitmap(bitmap, options.quality)
                
                // Generate thumbnail
                val thumbnailBytes = if (options.generateThumbnail) {
                    generateThumbnail(bitmap)
                } else {
                    byteArrayOf()
                }
                
                val dimensions = MediaDimensions(bitmap.width, bitmap.height)
                bitmap.recycle()
                
                Timber.i("✅ Photo processed: ${compressedBytes.size} bytes")
                
                Result.success(
                    ProcessedMedia(
                        file = compressedBytes,
                        thumbnail = thumbnailBytes,
                        dimensions = dimensions,
                        sizeBytes = compressedBytes.size.toLong(),
                        format = "image/jpeg"
                    )
                )
            }
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to process photo")
            Result.failure(exception)
        }
    }
    
    override suspend fun processVideo(
        uri: Uri,
        options: MediaProcessingOptions
    ): Result<ProcessedMedia> {
        return try {
            withContext(Dispatchers.IO) {
                Timber.d("🎥 Processing video: $uri")
                
                // Validate input
                val validation = validateMedia(uri, MediaType.VIDEO).getOrThrow()
                if (!validation.isValid) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid video: ${validation.errors.joinToString()}")
                    )
                }
                
                // For now, we'll copy the original video and extract thumbnail
                // In a production app, you'd use FFmpeg or similar for video compression
                val videoBytes = copyVideoFile(uri)
                
                // Extract thumbnail from video
                val thumbnailBytes = extractVideoThumbnail(uri)
                
                // Get video dimensions
                val dimensions = getVideoDimensions(uri)
                
                Timber.i("✅ Video processed: ${videoBytes.size} bytes")
                
                Result.success(
                    ProcessedMedia(
                        file = videoBytes,
                        thumbnail = thumbnailBytes,
                        dimensions = dimensions,
                        sizeBytes = videoBytes.size.toLong(),
                        format = "video/mp4"
                    )
                )
            }
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to process video")
            Result.failure(exception)
        }
    }
    
    override suspend fun generateBlurhash(imageBytes: ByteArray): Result<String> {
        return try {
            // Simplified blurhash generation - in production use actual blurhash library
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            val hash = generateSimpleBlurhash(bitmap)
            bitmap.recycle()
            Result.success(hash)
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to generate blurhash")
            Result.failure(exception)
        }
    }
    
    override suspend fun validateMedia(
        uri: Uri,
        mediaType: MediaType
    ): Result<MediaValidationResult> {
        return try {
            val errors = mutableListOf<String>()
            
            // Check if URI can be opened
            val inputStream = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                errors.add("Cannot open media file")
                null
            }
            
            if (inputStream == null) {
                // Add error message if not already added by exception handler
                if (errors.isEmpty()) {
                    errors.add("Cannot open media file")
                }
                return Result.success(
                    MediaValidationResult(
                        isValid = false,
                        fileSize = 0,
                        format = "unknown",
                        errors = errors
                    )
                )
            }
            
            // Get file size
            val fileSize = inputStream.available().toLong()
            inputStream.close()
            
            // Check file size limits
            val maxSizeMB = when (mediaType) {
                MediaType.PHOTO -> MAX_PHOTO_SIZE_MB
                MediaType.VIDEO -> MAX_VIDEO_SIZE_MB
            }
            
            if (fileSize > maxSizeMB * 1024 * 1024) {
                errors.add("File size exceeds ${maxSizeMB}MB limit")
            }
            
            // Check format
            val mimeType = context.contentResolver.getType(uri) ?: "unknown"
            val supportedFormats = when (mediaType) {
                MediaType.PHOTO -> SUPPORTED_PHOTO_FORMATS
                MediaType.VIDEO -> SUPPORTED_VIDEO_FORMATS
            }
            
            if (!supportedFormats.contains(mimeType)) {
                errors.add("Unsupported format: $mimeType")
            }
            
            // Additional video validation
            var duration: Int? = null
            var resolution: Pair<Int, Int>? = null
            
            if (mediaType == MediaType.VIDEO && errors.isEmpty()) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.div(1000)?.toInt()
                    
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    
                    if (width != null && height != null) {
                        resolution = width to height
                    }
                    
                    if (duration != null && duration > MAX_VIDEO_DURATION_SECONDS) {
                        errors.add("Video duration exceeds ${MAX_VIDEO_DURATION_SECONDS}s limit")
                    }
                    
                    retriever.release()
                } catch (e: Exception) {
                    errors.add("Cannot read video metadata")
                }
            }
            
            Result.success(
                MediaValidationResult(
                    isValid = errors.isEmpty(),
                    fileSize = fileSize,
                    duration = duration,
                    resolution = resolution,
                    format = mimeType,
                    errors = errors
                )
            )
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to validate media")
            Result.failure(exception)
        }
    }
    
    override suspend fun estimateProcessing(
        uri: Uri,
        mediaType: MediaType
    ): Result<ProcessingEstimate> {
        return try {
            val validation = validateMedia(uri, mediaType).getOrThrow()
            
            val estimatedTimeMs = when (mediaType) {
                MediaType.PHOTO -> estimatePhotoProcessingTime(validation.fileSize)
                MediaType.VIDEO -> estimateVideoProcessingTime(validation.fileSize, validation.duration)
            }
            
            val compressionRatio = when (mediaType) {
                MediaType.PHOTO -> 0.3f // Typical 70% compression
                MediaType.VIDEO -> 0.5f // Typical 50% compression
            }
            
            Result.success(
                ProcessingEstimate(
                    estimatedTimeMs = estimatedTimeMs,
                    estimatedOutputSize = (validation.fileSize * compressionRatio).toLong(),
                    compressionRatio = compressionRatio,
                    willCreateThumbnail = true
                )
            )
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to estimate processing")
            Result.failure(exception)
        }
    }
    
    private fun compressBitmap(bitmap: Bitmap, quality: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }
    
    private fun generateThumbnail(bitmap: Bitmap): ByteArray {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val thumbnailWidth: Int
        val thumbnailHeight: Int
        
        if (aspectRatio > 1) {
            thumbnailWidth = THUMBNAIL_SIZE
            thumbnailHeight = (THUMBNAIL_SIZE / aspectRatio).toInt()
        } else {
            thumbnailWidth = (THUMBNAIL_SIZE * aspectRatio).toInt()
            thumbnailHeight = THUMBNAIL_SIZE
        }
        
        val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbnailWidth, thumbnailHeight, true)
        val bytes = compressBitmap(thumbnail, THUMBNAIL_QUALITY)
        thumbnail.recycle()
        return bytes
    }
    
    private fun copyVideoFile(uri: Uri): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open video file")
        
        return inputStream.use { it.readBytes() }
    }
    
    private fun extractVideoThumbnail(uri: Uri): ByteArray {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: throw IllegalArgumentException("Cannot extract video thumbnail")
            
            val thumbnail = generateThumbnail(bitmap)
            bitmap.recycle()
            thumbnail
        } finally {
            retriever.release()
        }
    }
    
    private fun getVideoDimensions(uri: Uri): MediaDimensions {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            MediaDimensions(width, height)
        } finally {
            retriever.release()
        }
    }
    
    private fun generateSimpleBlurhash(bitmap: Bitmap): String {
        // Simplified blurhash - in production use actual blurhash library
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 4, 4, true)
        val colors = mutableListOf<Int>()
        
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                colors.add(scaledBitmap.getPixel(x, y))
            }
        }
        
        scaledBitmap.recycle()
        
        // Generate a simple hash based on average colors
        val avgRed = colors.map { (it shr 16) and 0xFF }.average().toInt()
        val avgGreen = colors.map { (it shr 8) and 0xFF }.average().toInt()
        val avgBlue = colors.map { it and 0xFF }.average().toInt()
        
        return String.format("L%02X%02X%02X", avgRed, avgGreen, avgBlue)
    }
    
    private fun estimatePhotoProcessingTime(fileSize: Long): Long {
        // Optimized estimate: 50ms per MB with minimum 200ms overhead
        val sizeMB = fileSize / 1024.0 / 1024.0
        return (sizeMB * 50 + 200).toLong().coerceAtLeast(200).coerceAtMost(5000)
    }
    
    private fun estimateVideoProcessingTime(fileSize: Long, duration: Int?): Long {
        // Optimized estimate: 200ms per MB + 50ms per second with realistic bounds
        val sizeMB = fileSize / 1024.0 / 1024.0
        val sizeTime = sizeMB * 200
        val durationTime = (duration ?: 10) * 50
        return (sizeTime + durationTime + 500).toLong().coerceAtLeast(500).coerceAtMost(30000)
    }
}