package com.example.liftrix.domain.model

/**
 * Domain model representing an external platform share.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
data class ExternalShare(
    val id: String,
    val userId: String,
    val contentType: ShareableContentType,
    val contentId: String,
    val platform: SocialPlatform,
    val shareImageUrl: String? = null,
    val shareText: String? = null,
    val hashtags: List<String> = emptyList(),
    val shareMethod: ShareMethod? = null,
    val sharedAt: Long
)

/**
 * Types of content that can be shared externally.
 */
enum class ShareableContentType {
    WORKOUT,
    ROUTINE,
    PR,
    PROGRESS
}

/**
 * External social platforms for sharing.
 */
enum class SocialPlatform {
    INSTAGRAM,
    WHATSAPP,
    TWITTER,
    FACEBOOK,
    TELEGRAM,
    DISCORD
}

/**
 * Methods of sharing on each platform.
 */
enum class ShareMethod {
    STORY,
    POST,
    MESSAGE,
    FEED
}

/**
 * Request for sharing content to external platform.
 */
data class ExternalShareRequest(
    val content: ShareableContent,
    val platform: SocialPlatform,
    val shareMethod: ShareMethod = ShareMethod.POST,
    val customText: String? = null,
    val customHashtags: List<String> = emptyList(),
    val includeWatermark: Boolean = true
)

/**
 * Content that can be shared externally.
 */
data class ShareableContent(
    val id: String,
    val type: ShareableContentType,
    val title: String,
    val subtitle: String? = null,
    val stats: Map<String, Any> = emptyMap(),
    val imageUrl: String? = null,
    val userAvatar: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Template for generating share images.
 */
enum class StoryTemplate {
    FITNESS_ACHIEVEMENT,
    WORKOUT_SUMMARY,
    PROGRESS_COMPARISON,
    ROUTINE_SHOWCASE,
    PR_CELEBRATION
}

/**
 * Share analytics data.
 */
data class ShareAnalytics(
    val userId: String,
    val timeframe: ShareTimeframe,
    val platformStats: Map<SocialPlatform, Int>,
    val contentTypeStats: Map<ShareableContentType, Int>,
    val totalShares: Int,
    val engagementRate: Float? = null
)

/**
 * Timeframe for share analytics.
 */
enum class ShareTimeframe {
    DAILY,
    WEEKLY,
    MONTHLY,
    ALL_TIME
}