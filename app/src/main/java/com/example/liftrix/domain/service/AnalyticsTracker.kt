package com.example.liftrix.domain.service

/**
 * AnalyticsTracker - Interface for tracking social interactions and events
 * 
 * Tracks:
 * - Social actions (follow, unfollow, block)
 * - Profile views and interactions
 * - User discovery patterns
 * - Feature usage analytics
 * 
 * Implementation: AnalyticsTrackerImpl using Firebase Analytics
 */
interface AnalyticsTracker {
    
    /**
     * Track social action events
     */
    fun trackSocialAction(
        action: String,
        targetUser: String,
        source: String? = null,
        additionalProperties: Map<String, Any> = emptyMap()
    )
    
    /**
     * Track profile view events
     */
    fun trackProfileView(
        viewedUserId: String,
        viewerUserId: String,
        source: String,
        viewDurationMs: Long? = null
    )
    
    /**
     * Track user discovery events
     */
    fun trackUserDiscovery(
        discoveredUserId: String,
        discovererUserId: String,
        discoveryMethod: String, // SEARCH, SUGGESTIONS, MUTUAL_CONNECTIONS
        position: Int? = null
    )
    
    /**
     * Track feature usage
     */
    fun trackFeatureUsage(
        feature: String,
        userId: String,
        properties: Map<String, Any> = emptyMap()
    )
    
    /**
     * Track search queries
     */
    fun trackSearch(
        query: String,
        userId: String,
        resultsCount: Int,
        selectedResult: String? = null
    )
    
    /**
     * Track notification actions
     */
    fun trackNotificationAction(
        action: String,
        properties: Map<String, String> = emptyMap()
    )
    
    /**
     * Track notification received events
     */
    fun trackNotificationReceived(
        type: String,
        isInForeground: Boolean
    )
    
    /**
     * Track sharing events
     */
    fun trackShare(
        contentType: String, // POST, WORKOUT, PROGRESS
        contentId: String,
        platform: String, // INSTAGRAM, WHATSAPP, TWITTER, etc.
        userId: String,
        hasCustomMessage: Boolean = false,
        additionalProperties: Map<String, Any> = emptyMap()
    )
    
    /**
     * Track engagement events
     */
    fun trackEngagement(
        action: String, // LIKE, COMMENT, SAVE, COPY_WORKOUT
        contentType: String, // POST, WORKOUT
        contentId: String,
        contentOwnerUserId: String,
        userId: String,
        additionalProperties: Map<String, Any> = emptyMap()
    )
    
    /**
     * Track QR code events
     */
    fun trackQRCodeEvent(
        action: String, // GENERATE, SCAN, SHARE, SAVE
        userId: String,
        qrType: String = "GYM_BUDDY",
        additionalProperties: Map<String, Any> = emptyMap()
    )
    
    /**
     * Track error events
     */
    fun trackError(
        errorType: String,
        errorMessage: String,
        additionalProperties: Map<String, Any> = emptyMap()
    )
}