package com.example.liftrix.domain.model.social

import android.net.Uri

/**
 * Android-facing media upload request kept in app because it carries Uri.
 */
data class MediaUploadRequest(
    val uri: Uri,
    val type: MediaType,
    val caption: String? = null,
    val compressionQuality: Int = 85,
    val maxFileSizeMB: Float = if (type == MediaType.VIDEO) 50.0f else 2.0f
)
