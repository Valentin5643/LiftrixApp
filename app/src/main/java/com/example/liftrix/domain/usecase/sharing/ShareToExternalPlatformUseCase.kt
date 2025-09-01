package com.example.liftrix.domain.usecase.sharing

import com.example.liftrix.data.service.ShareContentFactory
import com.example.liftrix.data.service.PlatformIntentService
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for sharing content to external platforms (Instagram, WhatsApp, generic sharing)
 * 
 * This use case handles platform-specific sharing logic including:
 * - Instagram Story sharing with proper media formats
 * - WhatsApp sharing with formatted text and links
 * - Generic platform sharing for fallback scenarios
 * 
 * Following Clean Architecture principles, this use case coordinates between:
 * - ShareContentFactory for content formatting
 * - PlatformIntentService for Android Intent management
 */
class ShareToExternalPlatformUseCase @Inject constructor(
    private val shareContentFactory: ShareContentFactory,
    private val platformIntentService: PlatformIntentService
) {
    
    /**
     * Shares workout content to specified external platform
     * 
     * @param request The share request containing platform and content details
     * @return LiftrixResult containing ShareResult or error
     */
    suspend fun invoke(request: ShareRequest): LiftrixResult<ShareResult> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SHARE_FAILED",
                errorMessage = "Failed to share content to ${request.platform}: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "SHARE_TO_EXTERNAL_PLATFORM",
                    "platform" to request.platform.name,
                    "content_type" to request.contentType.name
                )
            )
        }
    ) {
        Timber.d("Sharing ${request.contentType} to ${request.platform} for user ${request.userId}")
        
        // Create platform-specific shareable content
        val shareableContent = shareContentFactory.createShareableContent(request)
        
        // Generate platform-specific Android Intent
        val shareIntent = when (request.platform) {
            SharePlatform.INSTAGRAM_STORY -> {
                platformIntentService.shareToInstagramStory(shareableContent)
            }
            SharePlatform.WHATSAPP -> {
                platformIntentService.shareToWhatsApp(shareableContent)
            }
            SharePlatform.GENERIC -> {
                platformIntentService.shareGeneric(shareableContent)
            }
        }
        
        ShareResult(
            platform = request.platform,
            intent = shareIntent,
            shareableContent = shareableContent,
            success = true
        )
    }
}

/**
 * Request model for external platform sharing
 */
data class ShareRequest(
    val userId: String,
    val platform: SharePlatform,
    val contentType: ShareContentType,
    val workoutId: String? = null,
    val workoutData: ShareWorkoutData? = null,
    val customMessage: String? = null
)

/**
 * Supported external sharing platforms
 */
enum class SharePlatform {
    INSTAGRAM_STORY,
    WHATSAPP,
    GENERIC
}

/**
 * Types of content that can be shared
 */
enum class ShareContentType {
    WORKOUT_SUMMARY,
    WORKOUT_LINK,
    PERSONAL_RECORD,
    ACHIEVEMENT
}

/**
 * Workout data for sharing
 */
data class ShareWorkoutData(
    val workoutName: String,
    val duration: String,
    val totalVolume: String,
    val exercises: List<String>,
    val personalRecords: List<String> = emptyList(),
    val imageUrl: String? = null
)

/**
 * Result of sharing operation
 */
data class ShareResult(
    val platform: SharePlatform,
    val intent: android.content.Intent,
    val shareableContent: ShareableContent,
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * Platform-agnostic shareable content
 */
data class ShareableContent(
    val title: String,
    val message: String,
    val imageUrl: String? = null,
    val linkUrl: String? = null,
    val hashtags: List<String> = emptyList()
)