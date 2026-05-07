package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for CDN (Content Delivery Network) integration.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 * 
 * Provides high-performance content delivery with CloudFlare integration and fallback mechanisms.
 */
interface CDNService {
    
    /**
     * Caches content on CDN and returns the CDN URL.
     * 
     * @param originalUrl Original Firebase Storage URL
     * @param contentType MIME type of the content
     * @param cachePolicy Caching strategy configuration
     * @return CDN URL for fast content delivery
     */
    suspend fun cacheContent(
        originalUrl: String,
        contentType: String,
        cachePolicy: CDNCachePolicy = CDNCachePolicy.DEFAULT
    ): LiftrixResult<String>
    
    /**
     * Purges content from CDN cache.
     * 
     * @param cdnUrl CDN URL to purge
     * @return Success result
     */
    suspend fun purgeContent(cdnUrl: String): LiftrixResult<Unit>
    
    /**
     * Optimizes image URL with CDN transformations.
     * 
     * @param imageUrl Original image URL
     * @param transformations Image optimization parameters
     * @return Optimized CDN URL with transformations
     */
    suspend fun optimizeImage(
        imageUrl: String,
        transformations: ImageTransformations
    ): LiftrixResult<String>
    
    /**
     * Gets CDN analytics for content performance.
     * 
     * @param cdnUrl CDN URL to analyze
     * @param timeframe Analytics time period
     * @return CDN performance metrics
     */
    suspend fun getAnalytics(
        cdnUrl: String,
        timeframe: AnalyticsTimeframe = AnalyticsTimeframe.LAST_24H
    ): LiftrixResult<CDNAnalytics>
    
    /**
     * Validates CDN configuration and connectivity.
     * 
     * @return Health check result
     */
    suspend fun healthCheck(): LiftrixResult<CDNHealthStatus>
}

/**
 * CDN caching policy configuration.
 */
data class CDNCachePolicy(
    val ttlSeconds: Long,
    val browserCacheSeconds: Long,
    val edgeCacheSeconds: Long,
    val staleWhileRevalidate: Boolean = true
) {
    companion object {
        val DEFAULT = CDNCachePolicy(
            ttlSeconds = 86400, // 24 hours
            browserCacheSeconds = 3600, // 1 hour
            edgeCacheSeconds = 86400, // 24 hours
            staleWhileRevalidate = true
        )
        
        val MEDIA_CONTENT = CDNCachePolicy(
            ttlSeconds = 604800, // 7 days
            browserCacheSeconds = 86400, // 1 day
            edgeCacheSeconds = 604800, // 7 days
            staleWhileRevalidate = true
        )
        
        val PROFILE_IMAGES = CDNCachePolicy(
            ttlSeconds = 86400, // 1 day
            browserCacheSeconds = 3600, // 1 hour
            edgeCacheSeconds = 86400, // 1 day
            staleWhileRevalidate = true
        )
    }
}

/**
 * Image transformation parameters for CDN optimization.
 */
data class ImageTransformations(
    val width: Int? = null,
    val height: Int? = null,
    val quality: Int? = null,
    val format: String? = null, // webp, avif, etc.
    val fit: ResizeMode = ResizeMode.SCALE_DOWN,
    val sharpen: Boolean = false,
    val blur: Int? = null,
    val brightness: Float? = null,
    val contrast: Float? = null
)

/**
 * Image resize modes for CDN transformations.
 */
enum class ResizeMode {
    SCALE_DOWN,
    CONTAIN,
    COVER,
    CROP,
    PAD
}

/**
 * CDN analytics data.
 */
data class CDNAnalytics(
    val url: String,
    val requests: Long,
    val bandwidth: Long, // bytes
    val cacheHitRatio: Float,
    val avgResponseTime: Long, // milliseconds
    val topLocations: List<String>,
    val errorRate: Float,
    val timeframe: AnalyticsTimeframe
)

/**
 * Analytics timeframe options.
 */
enum class AnalyticsTimeframe {
    LAST_1H,
    LAST_6H,
    LAST_24H,
    LAST_7D,
    LAST_30D
}

/**
 * CDN health status.
 */
data class CDNHealthStatus(
    val isHealthy: Boolean,
    val responseTime: Long, // milliseconds
    val cacheStatus: String,
    val edgeLocation: String?,
    val lastChecked: Long,
    val issues: List<String> = emptyList()
)