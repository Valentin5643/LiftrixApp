package com.example.liftrix.domain.model.social

/**
 * Domain model for uploaded media items
 */
data class MediaItem(
    val id: String,
    val type: MediaType,
    val originalUrl: String,
    val thumbnailUrl: String?,
    val compressedUrl: String?,
    val width: Int?,
    val height: Int?,
    val fileSizeBytes: Long,
    val duration: Long? = null, // For videos, in milliseconds
    val uploadedAt: Long = System.currentTimeMillis()
)

/**
 * Media upload request
 */
data class MediaUploadRequest(
    val uri: android.net.Uri,
    val type: MediaType,
    val caption: String? = null,
    val compressionQuality: Int = 85, // 0-100 for images
    val maxFileSizeMB: Float = if (type == MediaType.VIDEO) 50.0f else 2.0f
)

/**
 * Supported media types
 */
enum class MediaType {
    IMAGE,
    VIDEO
}