package com.example.liftrix.domain.share

import android.content.Intent
import com.example.liftrix.domain.model.ExternalShare
import com.example.liftrix.domain.model.ShareableContent
import com.example.liftrix.domain.model.SocialPlatform
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Interface for sharing content to external social platforms.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
interface PlatformShareAdapter {
    
    /**
     * Shares content to the specified platform.
     * 
     * @param platform Target social platform
     * @param content Content to share
     * @param customText Optional custom text to include
     * @param imageUrl Optional image URL to share
     * @return Result containing share intent or error
     */
    suspend fun shareContent(
        platform: SocialPlatform,
        content: ShareableContent,
        customText: String? = null,
        imageUrl: String? = null
    ): LiftrixResult<Intent>

    /**
     * Creates an image share intent for a generated local image.
     */
    suspend fun shareImage(
        platform: SocialPlatform,
        imageFilePath: String,
        content: ShareableContent,
        customText: String? = null
    ): LiftrixResult<Intent>

    /**
     * Creates a native Android image share-sheet intent for a generated local image.
     */
    suspend fun createNativeImageShare(
        imageFilePath: String,
        content: ShareableContent,
        customText: String? = null
    ): LiftrixResult<Intent>
    
    /**
     * Checks if the specified platform app is installed.
     * 
     * @param platform Platform to check
     * @return True if the platform app is available
     */
    suspend fun isPlatformAvailable(platform: SocialPlatform): Boolean
    
    /**
     * Gets the optimal image dimensions for the platform.
     * 
     * @param platform Target platform
     * @return Recommended width and height in pixels
     */
    fun getOptimalImageDimensions(platform: SocialPlatform): Pair<Int, Int>
    
    /**
     * Gets the maximum text length for the platform.
     * 
     * @param platform Target platform
     * @return Maximum characters allowed, or null if no limit
     */
    fun getMaxTextLength(platform: SocialPlatform): Int?
    
    /**
     * Gets platform-specific hashtag recommendations.
     * 
     * @param platform Target platform
     * @param content Content being shared
     * @return List of recommended hashtags
     */
    suspend fun getRecommendedHashtags(
        platform: SocialPlatform,
        content: ShareableContent
    ): List<String>
}

/**
 * Configuration for platform-specific sharing behavior.
 */
data class PlatformConfig(
    val packageName: String,
    val intentAction: String,
    val mimeType: String,
    val maxTextLength: Int?,
    val optimalImageDimensions: Pair<Int, Int>,
    val supportsHashtags: Boolean,
    val supportsImages: Boolean,
    val supportsVideo: Boolean
)

/**
 * Result of a platform share operation.
 */
data class ShareResult(
    val platform: SocialPlatform,
    val success: Boolean,
    val shareId: String? = null,
    val error: String? = null,
    val analyticsData: Map<String, Any> = emptyMap()
)
