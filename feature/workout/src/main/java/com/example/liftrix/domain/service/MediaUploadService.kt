package com.example.liftrix.domain.service

import android.net.Uri
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.MediaItem
import com.example.liftrix.domain.model.social.MediaUploadRequest

/**
 * Service interface for media upload operations
 * Handles photo/video upload to Firebase Storage with compression and thumbnails
 */
interface MediaUploadService {
    
    /**
     * Uploads multiple media items for a workout post
     * Handles compression, thumbnail generation, and CDN upload
     */
    suspend fun uploadMediaItems(
        userId: String,
        mediaRequests: List<MediaUploadRequest>
    ): LiftrixResult<List<MediaItem>>
    
    /**
     * Uploads a single media item
     */
    suspend fun uploadMediaItem(
        userId: String,
        mediaRequest: MediaUploadRequest
    ): LiftrixResult<MediaItem>
    
    /**
     * Uploads profile image with circular crop and multiple sizes
     */
    suspend fun uploadProfileImage(
        userId: String,
        imageUri: Uri
    ): LiftrixResult<String>
    
    /**
     * Deletes media from Firebase Storage
     */
    suspend fun deleteMedia(mediaUrl: String): LiftrixResult<Unit>
    
    /**
     * Compresses image for upload
     * @param imageUri Source image URI
     * @param maxSizeMB Maximum file size in MB
     * @param quality JPEG quality (0-100)
     */
    suspend fun compressImage(
        imageUri: Uri,
        maxSizeMB: Float = 2.0f,
        quality: Int = 85
    ): LiftrixResult<Uri>
    
    /**
     * Generates thumbnail for image
     * @param imageUri Source image URI
     * @param thumbnailSize Thumbnail dimensions in pixels
     */
    suspend fun generateThumbnail(
        imageUri: Uri,
        thumbnailSize: Int = 300
    ): LiftrixResult<Uri>
    
    /**
     * Compresses video for upload
     * @param videoUri Source video URI
     * @param maxSizeMB Maximum file size in MB
     * @param resolution Target resolution (480p, 720p, 1080p)
     */
    suspend fun compressVideo(
        videoUri: Uri,
        maxSizeMB: Float = 50.0f,
        resolution: String = "720p"
    ): LiftrixResult<Uri>
    
    /**
     * Generates video thumbnail
     * @param videoUri Source video URI
     * @param timeUs Time position in microseconds for thumbnail
     */
    suspend fun generateVideoThumbnail(
        videoUri: Uri,
        timeUs: Long = 1_000_000L // 1 second
    ): LiftrixResult<Uri>
    
    /**
     * Gets upload progress for tracking
     */
    fun getUploadProgress(uploadId: String): kotlinx.coroutines.flow.Flow<Float>
    
    /**
     * Cancels ongoing upload
     */
    suspend fun cancelUpload(uploadId: String): LiftrixResult<Unit>
    
    /**
     * Simple image upload method with automatic compression
     * Used by PostCreationViewModel for post media uploads
     * 
     * @param uri Image URI to upload
     * @param path Storage path for the image
     * @param maxSizeKb Maximum file size in KB
     * @return Download URL of uploaded image
     */
    suspend fun uploadImage(
        uri: Uri,
        path: String,
        maxSizeKb: Int = 5000
    ): LiftrixResult<String>
}