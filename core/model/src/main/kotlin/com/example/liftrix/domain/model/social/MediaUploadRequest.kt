package com.example.liftrix.domain.model.social

/**
 * Media upload request. Runtime upload implementations interpret the URI object.
 */
data class MediaUploadRequest(
    val uri: Any,
    val type: MediaType,
    val caption: String? = null,
    val compressionQuality: Int = 85,
    val maxFileSizeMB: Float = if (type == MediaType.VIDEO) 50.0f else 2.0f
)
