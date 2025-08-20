package com.example.liftrix.data.service

import com.example.liftrix.domain.service.AnalyticsTracker
import com.google.firebase.analytics.FirebaseAnalytics
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AnalyticsTrackerImpl - Firebase Analytics implementation of AnalyticsTracker
 * 
 * Tracks social interactions and user behavior for:
 * - Product analytics and insights
 * - User engagement optimization
 * - Feature usage patterns
 * - Social network analysis
 */
@Singleton
class AnalyticsTrackerImpl @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) : AnalyticsTracker {
    
    override fun trackSocialAction(
        action: String,
        targetUser: String,
        source: String?,
        additionalProperties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "action" to action,
                "target_user_id" to targetUser
            )
            
            source?.let { params["source"] = it }
            params.putAll(additionalProperties)
            
            // Firebase Analytics event
            firebaseAnalytics.logEvent("social_action", params.toBundle())
            
            Timber.d("Analytics: Social action tracked - $action on $targetUser from $source")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track social action analytics")
        }
    }
    
    override fun trackProfileView(
        viewedUserId: String,
        viewerUserId: String,
        source: String,
        viewDurationMs: Long?
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "viewed_user_id" to viewedUserId,
                "viewer_user_id" to viewerUserId,
                "source" to source
            )
            
            viewDurationMs?.let { params["view_duration_ms"] = it }
            
            firebaseAnalytics.logEvent("profile_view", params.toBundle())
            
            Timber.d("Analytics: Profile view tracked - $viewerUserId viewed $viewedUserId from $source")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track profile view analytics")
        }
    }
    
    override fun trackUserDiscovery(
        discoveredUserId: String,
        discovererUserId: String,
        discoveryMethod: String,
        position: Int?
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "discovered_user_id" to discoveredUserId,
                "discoverer_user_id" to discovererUserId,
                "discovery_method" to discoveryMethod
            )
            
            position?.let { params["position"] = it }
            
            firebaseAnalytics.logEvent("user_discovery", params.toBundle())
            
            Timber.d("Analytics: User discovery tracked - $discovererUserId discovered $discoveredUserId via $discoveryMethod")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track user discovery analytics")
        }
    }
    
    override fun trackFeatureUsage(
        feature: String,
        userId: String,
        properties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "feature" to feature,
                "user_id" to userId
            )
            
            params.putAll(properties)
            
            firebaseAnalytics.logEvent("feature_usage", params.toBundle())
            
            Timber.d("Analytics: Feature usage tracked - $userId used $feature")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track feature usage analytics")
        }
    }
    
    override fun trackSearch(
        query: String,
        userId: String,
        resultsCount: Int,
        selectedResult: String?
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "search_query" to query.take(50), // Limit query length for privacy
                "user_id" to userId,
                "results_count" to resultsCount
            )
            
            selectedResult?.let { params["selected_result"] = it }
            
            firebaseAnalytics.logEvent("search", params.toBundle())
            
            Timber.d("Analytics: Search tracked - $userId searched '$query' with $resultsCount results")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track search analytics")
        }
    }
    
    override fun trackNotificationAction(
        action: String,
        properties: Map<String, String>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "action" to action
            )
            
            params.putAll(properties)
            
            firebaseAnalytics.logEvent("notification_action", params.toBundle())
            
            Timber.d("Analytics: Notification action tracked - $action")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track notification action analytics")
        }
    }
    
    override fun trackNotificationReceived(
        type: String,
        isInForeground: Boolean
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "notification_type" to type,
                "is_foreground" to isInForeground
            )
            
            firebaseAnalytics.logEvent("notification_received", params.toBundle())
            
            Timber.d("Analytics: Notification received tracked - $type (foreground: $isInForeground)")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track notification received analytics")
        }
    }
    
    override fun trackShare(
        contentType: String,
        contentId: String,
        platform: String,
        userId: String,
        hasCustomMessage: Boolean,
        additionalProperties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "content_type" to contentType,
                "content_id" to contentId,
                "platform" to platform,
                "user_id" to userId,
                "has_custom_message" to hasCustomMessage
            )
            
            params.putAll(additionalProperties)
            
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SHARE, params.toBundle())
            
            Timber.d("Analytics: Share tracked - $userId shared $contentType:$contentId to $platform")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track share analytics")
        }
    }
    
    override fun trackEngagement(
        action: String,
        contentType: String,
        contentId: String,
        contentOwnerUserId: String,
        userId: String,
        additionalProperties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "action" to action,
                "content_type" to contentType,
                "content_id" to contentId,
                "content_owner_user_id" to contentOwnerUserId,
                "user_id" to userId
            )
            
            params.putAll(additionalProperties)
            
            firebaseAnalytics.logEvent("engagement", params.toBundle())
            
            Timber.d("Analytics: Engagement tracked - $userId performed $action on $contentType:$contentId")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track engagement analytics")
        }
    }
    
    override fun trackQRCodeEvent(
        action: String,
        userId: String,
        qrType: String,
        additionalProperties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "action" to action,
                "user_id" to userId,
                "qr_type" to qrType
            )
            
            params.putAll(additionalProperties)
            
            firebaseAnalytics.logEvent("qr_code_event", params.toBundle())
            
            Timber.d("Analytics: QR code event tracked - $userId performed $action for $qrType")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track QR code event analytics")
        }
    }
    
    override fun trackError(
        errorType: String,
        errorMessage: String,
        additionalProperties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "error_type" to errorType,
                "error_message" to errorMessage.take(200) // Limit message length
            )
            
            params.putAll(additionalProperties)
            
            firebaseAnalytics.logEvent("error_occurred", params.toBundle())
            
            Timber.d("Analytics: Error tracked - $errorType: $errorMessage")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track error analytics")
        }
    }
    
    // AI Chat Analytics Implementation
    
    override fun trackAIChatResponse(
        userId: String,
        tokensUsed: Int,
        processingTimeMs: Long,
        modelVersion: String,
        language: String,
        hasWorkoutContext: Boolean,
        additionalProperties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "user_id" to userId,
                "tokens_used" to tokensUsed,
                "processing_time_ms" to processingTimeMs,
                "model_version" to modelVersion,
                "language" to language,
                "has_workout_context" to hasWorkoutContext
            )
            
            params.putAll(additionalProperties)
            
            firebaseAnalytics.logEvent("ai_chat_response", params.toBundle())
            
            Timber.d("Analytics: AI chat response tracked - $userId used $tokensUsed tokens in ${processingTimeMs}ms")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track AI chat response analytics")
        }
    }
    
    override fun trackAIChatUsage(
        userId: String,
        dailyMessagesUsed: Int,
        monthlyTokensUsed: Int,
        isNearLimit: Boolean,
        additionalProperties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "user_id" to userId,
                "daily_messages_used" to dailyMessagesUsed,
                "monthly_tokens_used" to monthlyTokensUsed,
                "is_near_limit" to isNearLimit
            )
            
            params.putAll(additionalProperties)
            
            firebaseAnalytics.logEvent("ai_chat_usage", params.toBundle())
            
            Timber.d("Analytics: AI chat usage tracked - $userId has used $dailyMessagesUsed daily messages, $monthlyTokensUsed monthly tokens")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track AI chat usage analytics")
        }
    }
    
    override fun trackAbuseDetection(
        userId: String,
        abuseType: String,
        action: String,
        confidence: Float,
        messageLength: Int,
        additionalProperties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "user_id" to userId,
                "abuse_type" to abuseType,
                "action" to action,
                "confidence" to confidence.toDouble(),
                "message_length" to messageLength
            )
            
            params.putAll(additionalProperties)
            
            firebaseAnalytics.logEvent("ai_abuse_detection", params.toBundle())
            
            Timber.d("Analytics: Abuse detection tracked - $userId triggered $abuseType with action $action (confidence: $confidence)")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track abuse detection analytics")
        }
    }
    
    override fun trackRateLimit(
        userId: String,
        limitType: String,
        currentUsage: Int,
        limit: Int,
        timeToReset: Long,
        additionalProperties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "user_id" to userId,
                "limit_type" to limitType,
                "current_usage" to currentUsage,
                "limit" to limit,
                "time_to_reset" to timeToReset,
                "usage_percentage" to ((currentUsage.toDouble() / limit) * 100).toInt()
            )
            
            params.putAll(additionalProperties)
            
            firebaseAnalytics.logEvent("ai_rate_limit", params.toBundle())
            
            Timber.d("Analytics: Rate limit tracked - $userId hit $limitType limit ($currentUsage/$limit)")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track rate limit analytics")
        }
    }
    
    override fun trackAIChatError(
        userId: String,
        errorType: String,
        errorMessage: String,
        modelVersion: String,
        tokensRequested: Int?,
        additionalProperties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "user_id" to userId,
                "error_type" to errorType,
                "error_message" to errorMessage.take(200),
                "model_version" to modelVersion
            )
            
            tokensRequested?.let { params["tokens_requested"] = it }
            params.putAll(additionalProperties)
            
            firebaseAnalytics.logEvent("ai_chat_error", params.toBundle())
            
            Timber.d("Analytics: AI chat error tracked - $userId encountered $errorType: $errorMessage")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track AI chat error analytics")
        }
    }
    
    override fun trackAIChatCost(
        userId: String,
        estimatedCost: Double,
        tokensUsed: Int,
        timeWindow: String,
        isNearThreshold: Boolean,
        additionalProperties: Map<String, Any>
    ) {
        try {
            val params = mutableMapOf<String, Any>(
                "user_id" to userId,
                "estimated_cost" to estimatedCost,
                "tokens_used" to tokensUsed,
                "time_window" to timeWindow,
                "is_near_threshold" to isNearThreshold,
                "cost_per_token" to if (tokensUsed > 0) estimatedCost / tokensUsed else 0.0
            )
            
            params.putAll(additionalProperties)
            
            firebaseAnalytics.logEvent("ai_chat_cost", params.toBundle())
            
            Timber.d("Analytics: AI chat cost tracked - $userId incurred $estimatedCost cost for $tokensUsed tokens in $timeWindow")
        } catch (e: Exception) {
            Timber.w(e, "Failed to track AI chat cost analytics")
        }
    }
    
    private fun Map<String, Any>.toBundle(): android.os.Bundle {
        val bundle = android.os.Bundle()
        this.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        return bundle
    }
}