package com.example.liftrix.domain.service

import android.net.Uri
import com.example.liftrix.domain.model.MediaItem
import com.example.liftrix.domain.model.MediaProcessingOptions
import com.example.liftrix.domain.model.MediaType
import com.example.liftrix.domain.model.ProcessedMedia
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for comprehensive media processing pipeline.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 * 
 * Handles photo and video processing with quality preservation, compression,
 * thumbnail generation, and CDN optimization.
 */
interface MediaProcessingService {
    
    /**
     * Processes a photo with compression, thumbnail generation, and optimization.
     * 
     * @param uri Source photo URI
     * @param options Processing configuration
     * @return Processed media with compressed file and thumbnail
     */
    suspend fun processPhoto(
        uri: Uri,
        options: MediaProcessingOptions = MediaProcessingOptions()
    ): LiftrixResult<ProcessedMedia>
    
    /**
     * Processes a video with compression, thumbnail extraction, and optimization.
     * 
     * @param uri Source video URI
     * @param options Processing configuration
     * @return Processed media with compressed video and thumbnail
     */
    suspend fun processVideo(
        uri: Uri,
        options: MediaProcessingOptions = MediaProcessingOptions()
    ): LiftrixResult<ProcessedMedia>
    
    /**
     * Generates a blurhash placeholder for progressive image loading.
     * 
     * @param imageBytes Processed image data
     * @return Blurhash string for placeholder
     */
    suspend fun generateBlurhash(imageBytes: ByteArray): LiftrixResult<String>
    
    /**
     * Validates if the media URI is supported and within size limits.
     * 
     * @param uri Media URI to validate
     * @param mediaType Expected media type
     * @return Validation result
     */
    suspend fun validateMedia(
        uri: Uri,
        mediaType: MediaType
    ): LiftrixResult<MediaValidationResult>
    
    /**
     * Estimates processing time and output size for given media.
     * 
     * @param uri Media URI to analyze
     * @param mediaType Media type
     * @return Processing estimates
     */
    suspend fun estimateProcessing(
        uri: Uri,
        mediaType: MediaType
    ): LiftrixResult<ProcessingEstimate>
}

/**
 * Result of media validation.
 */
data class MediaValidationResult(
    val isValid: Boolean,
    val fileSize: Long,
    val duration: Int? = null, // For videos
    val resolution: Pair<Int, Int>? = null,
    val format: String,
    val errors: List<String> = emptyList()
)

/**
 * Processing time and size estimates.
 */
data class ProcessingEstimate(
    val estimatedTimeMs: Long,
    val estimatedOutputSize: Long,
    val compressionRatio: Float,
    val willCreateThumbnail: Boolean
)