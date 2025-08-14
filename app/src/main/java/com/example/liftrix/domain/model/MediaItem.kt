package com.example.liftrix.domain.model

import android.net.Uri

/**
 * Domain model representing a media item (photo or video) in the content sharing system.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
data class MediaItem(
    val id: String,
    val userId: String,
    val postId: String? = null,
    val type: MediaType,
    val originalUrl: String,
    val cdnUrl: String? = null,
    val thumbnailUrl: String? = null,
    val blurhash: String? = null, // Placeholder while loading
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val durationSeconds: Int? = null, // For videos
    val mimeType: String? = null,
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
    val processedAt: Long? = null,
    val compressionRatio: Float? = null,
    val isPublic: Boolean = false,
    val createdAt: Long,
    val expiresAt: Long? = null // For temporary media
)

/**
 * Media type enumeration for content classification.
 */
enum class MediaType {
    PHOTO,
    VIDEO
}

/**
 * Processing status for media items.
 */
enum class ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

/**
 * Image format options for processing pipeline.
 */
enum class ImageFormat {
    JPEG,
    PNG,
    WEBP
}

/**
 * Video resolution options for processing pipeline.
 */
enum class VideoResolution {
    SD_480P,
    HD_720P,
    FHD_1080P
}

/**
 * Result of media processing operation.
 */
data class ProcessedMedia(
    val file: ByteArray,
    val thumbnail: ByteArray,
    val dimensions: MediaDimensions,
    val sizeBytes: Long,
    val format: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as ProcessedMedia
        
        if (!file.contentEquals(other.file)) return false
        if (!thumbnail.contentEquals(other.thumbnail)) return false
        if (dimensions != other.dimensions) return false
        if (sizeBytes != other.sizeBytes) return false
        if (format != other.format) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = file.contentHashCode()
        result = 31 * result + thumbnail.contentHashCode()
        result = 31 * result + dimensions.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + format.hashCode()
        return result
    }
}

/**
 * Media dimensions for processed content.
 */
data class MediaDimensions(
    val width: Int,
    val height: Int
)

/**
 * Media upload request containing source URI and processing options.
 */
data class MediaUploadRequest(
    val uri: Uri,
    val mediaType: MediaType,
    val userId: String,
    val postId: String? = null,
    val processingOptions: MediaProcessingOptions = MediaProcessingOptions()
)

/**
 * Processing options for media upload pipeline.
 */
data class MediaProcessingOptions(
    val maxWidth: Int = 2048,
    val maxHeight: Int = 2048,
    val quality: Int = 85,
    val generateThumbnail: Boolean = true,
    val generateBlurhash: Boolean = true,
    val compressForCDN: Boolean = true
)