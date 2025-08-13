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