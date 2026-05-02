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
 * Supported media types
 */
enum class MediaType {
    IMAGE,
    VIDEO
}
