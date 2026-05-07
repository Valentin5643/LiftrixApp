package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for deep link generation and handling.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 * 
 * Provides Firebase Dynamic Links integration for shareable content with web fallbacks.
 */
interface DeepLinkService {
    
    /**
     * Creates a dynamic link for sharing workout routines.
     * 
     * @param routineId Workout routine to share
     * @param shareToken Unique share token for the routine
     * @param metadata Additional metadata for the link
     * @return Generated dynamic link with web preview
     */
    suspend fun createRoutineLink(
        routineId: String,
        shareToken: String,
        metadata: DeepLinkMetadata = DeepLinkMetadata()
    ): LiftrixResult<DeepLink>
    
    /**
     * Creates a dynamic link for sharing workout posts.
     * 
     * @param postId Workout post to share
     * @param metadata Additional metadata for the link
     * @return Generated dynamic link with web preview
     */
    suspend fun createWorkoutPostLink(
        postId: String,
        metadata: DeepLinkMetadata = DeepLinkMetadata()
    ): LiftrixResult<DeepLink>
    
    /**
     * Creates a dynamic link for sharing user profiles.
     * 
     * @param userId User profile to share
     * @param metadata Additional metadata for the link
     * @return Generated dynamic link with web preview
     */
    suspend fun createProfileLink(
        userId: String,
        metadata: DeepLinkMetadata = DeepLinkMetadata()
    ): LiftrixResult<DeepLink>
    
    /**
     * Creates a dynamic link for sharing progress comparisons.
     * 
     * @param comparisonId Progress comparison to share
     * @param metadata Additional metadata for the link
     * @return Generated dynamic link with web preview
     */
    suspend fun createProgressLink(
        comparisonId: String,
        metadata: DeepLinkMetadata = DeepLinkMetadata()
    ): LiftrixResult<DeepLink>
    
    /**
     * Parses and handles incoming deep links.
     * 
     * @param deepLinkUrl Incoming deep link URL
     * @return Parsed deep link data for navigation
     */
    suspend fun parseDeepLink(deepLinkUrl: String): LiftrixResult<ParsedDeepLink>
    
    /**
     * Validates if a deep link is still active and accessible.
     * 
     * @param deepLinkUrl Deep link to validate
     * @return Validation result
     */
    suspend fun validateDeepLink(deepLinkUrl: String): LiftrixResult<DeepLinkValidation>
    
    /**
     * Gets analytics data for a specific deep link.
     * 
     * @param deepLinkUrl Deep link to analyze
     * @param timeframe Analytics time period
     * @return Deep link performance metrics
     */
    suspend fun getDeepLinkAnalytics(
        deepLinkUrl: String,
        timeframe: DeepLinkTimeframe = DeepLinkTimeframe.LAST_7D
    ): LiftrixResult<DeepLinkAnalytics>
}

/**
 * Metadata for customizing deep link behavior and appearance.
 */
data class DeepLinkMetadata(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val socialMetadata: SocialMetadata = SocialMetadata(),
    val analytics: AnalyticsMetadata = AnalyticsMetadata(),
    val expiration: Long? = null // Unix timestamp for link expiration
)

/**
 * Social media specific metadata for link previews.
 */
data class SocialMetadata(
    val ogTitle: String? = null,
    val ogDescription: String? = null,
    val ogImage: String? = null,
    val twitterCard: String = "summary_large_image",
    val twitterTitle: String? = null,
    val twitterDescription: String? = null,
    val twitterImage: String? = null
)

/**
 * Analytics tracking metadata for deep links.
 */
data class AnalyticsMetadata(
    val campaign: String? = null,
    val source: String? = null,
    val medium: String? = null,
    val term: String? = null,
    val content: String? = null,
    val customParameters: Map<String, String> = emptyMap()
)

/**
 * Generated deep link with all necessary URLs and metadata.
 */
data class DeepLink(
    val shortUrl: String, // Firebase Dynamic Link short URL
    val longUrl: String, // Full dynamic link URL
    val webUrl: String, // Fallback web URL for non-app users
    val qrCodeUrl: String? = null, // QR code image URL
    val metadata: DeepLinkMetadata,
    val createdAt: Long,
    val expiresAt: Long? = null
)

/**
 * Parsed deep link data for app navigation.
 */
data class ParsedDeepLink(
    val type: DeepLinkType,
    val targetId: String, // ID of the target resource
    val parameters: Map<String, String> = emptyMap(),
    val isValid: Boolean,
    val requiresAuth: Boolean = false,
    val fallbackUrl: String? = null
)

/**
 * Types of content that can be deep linked.
 */
enum class DeepLinkType {
    ROUTINE,
    WORKOUT_POST,
    USER_PROFILE,
    PROGRESS_COMPARISON,
    EXERCISE,
    CHALLENGE,
    UNKNOWN
}

/**
 * Deep link validation result.
 */
data class DeepLinkValidation(
    val isValid: Boolean,
    val isExpired: Boolean,
    val isAccessible: Boolean,
    val targetExists: Boolean,
    val requiresAuth: Boolean,
    val errors: List<String> = emptyList()
)

/**
 * Deep link analytics data.
 */
data class DeepLinkAnalytics(
    val linkUrl: String,
    val totalClicks: Long,
    val uniqueClicks: Long,
    val appInstalls: Long,
    val appOpens: Long,
    val webOpens: Long,
    val topReferrers: List<String>,
    val topCountries: List<String>,
    val topPlatforms: List<String>,
    val conversionRate: Float, // app opens / total clicks
    val timeframe: DeepLinkTimeframe
)

/**
 * Timeframe for deep link analytics.
 */
enum class DeepLinkTimeframe {
    LAST_1D,
    LAST_7D,
    LAST_30D,
    ALL_TIME
}

/**
 * Configuration for Firebase Dynamic Links.
 */
data class DynamicLinkConfig(
    val domainUriPrefix: String = "https://liftrix.page.link",
    val androidPackageName: String = "com.example.liftrix",
    val iosAppStoreId: String = "your_ios_app_id",
    val iosBundleId: String = "com.example.liftrix.ios",
    val webFallbackUrl: String = "https://liftrix.app",
    val analytics: DynamicLinkAnalyticsConfig = DynamicLinkAnalyticsConfig()
)

/**
 * Analytics configuration for dynamic links.
 */
data class DynamicLinkAnalyticsConfig(
    val enableAnalytics: Boolean = true,
    val enableDebugAnalytics: Boolean = false,
    val customParameters: Map<String, String> = emptyMap()
)