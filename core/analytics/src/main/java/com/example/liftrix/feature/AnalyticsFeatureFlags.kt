package com.example.liftrix.feature

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analytics Feature Flags system using Firebase Remote Config
 * 
 * Provides centralized control over analytics feature rollout with:
 * - Progressive rollout percentages
 * - A/B testing configurations
 * - Feature toggle capabilities
 * - Fallback mechanisms
 */
@Singleton
class AnalyticsFeatureFlags @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) {
    companion object {
        // Feature flag keys
        private const val ANALYTICS_VOLUME_CALENDAR_ENABLED = "analytics_volume_calendar_enabled"
        private const val ANALYTICS_DASHBOARD_WIDGETS_ENABLED = "analytics_dashboard_widgets_enabled"
        private const val ANALYTICS_EXPORT_ENABLED = "analytics_export_enabled"
        private const val ANALYTICS_GOAL_TRACKING_ENABLED = "analytics_goal_tracking_enabled"
        private const val ANALYTICS_ROLLOUT_PERCENTAGE = "analytics_rollout_percentage"
        private const val ANALYTICS_DASHBOARD_WIDGET_LIMIT = "analytics_dashboard_widget_limit"
        private const val ANALYTICS_ONBOARDING_ENABLED = "analytics_onboarding_enabled"
        
        // Default values
        private const val DEFAULT_ROLLOUT_PERCENTAGE = 0
        private const val DEFAULT_WIDGET_LIMIT = 4
        private const val DEFAULT_FEATURE_ENABLED = false
        
        // Config fetch intervals
        private const val FETCH_TIMEOUT_SECONDS = 10L
        private const val CACHE_EXPIRATION_SECONDS = 3600L // 1 hour
    }
    
    init {
        setupRemoteConfig()
    }
    
    /**
     * Configure Firebase Remote Config with defaults and fetch settings
     */
    private fun setupRemoteConfig() {
        try {
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = CACHE_EXPIRATION_SECONDS
                fetchTimeoutInSeconds = FETCH_TIMEOUT_SECONDS
            }
            
            remoteConfig.setConfigSettingsAsync(configSettings)
            
            val defaults = mapOf(
                ANALYTICS_VOLUME_CALENDAR_ENABLED to DEFAULT_FEATURE_ENABLED,
                ANALYTICS_DASHBOARD_WIDGETS_ENABLED to DEFAULT_FEATURE_ENABLED,
                ANALYTICS_EXPORT_ENABLED to DEFAULT_FEATURE_ENABLED,
                ANALYTICS_GOAL_TRACKING_ENABLED to DEFAULT_FEATURE_ENABLED,
                ANALYTICS_ROLLOUT_PERCENTAGE to DEFAULT_ROLLOUT_PERCENTAGE,
                ANALYTICS_DASHBOARD_WIDGET_LIMIT to DEFAULT_WIDGET_LIMIT,
                ANALYTICS_ONBOARDING_ENABLED to DEFAULT_FEATURE_ENABLED
            )
            
            remoteConfig.setDefaultsAsync(defaults)
            
            // Fetch and activate config
            fetchAndActivateConfig()
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup Firebase Remote Config")
        }
    }
    
    /**
     * Fetch latest configuration from Firebase Remote Config
     */
    fun fetchAndActivateConfig() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Timber.d("Config fetch successful. Updated: $updated")
                } else {
                    Timber.w("Config fetch failed: ${task.exception?.message}")
                }
            }
    }
    
    /**
     * Check if Volume Calendar feature is enabled
     * 
     * @return true if volume calendar should be shown to users
     */
    fun isVolumeCalendarEnabled(): Boolean {
        return try {
            remoteConfig.getBoolean(ANALYTICS_VOLUME_CALENDAR_ENABLED)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get volume calendar flag")
            DEFAULT_FEATURE_ENABLED
        }
    }
    
    /**
     * Check if Dashboard Widgets feature is enabled
     * 
     * @return true if dashboard widgets should be shown to users
     */
    fun isDashboardWidgetsEnabled(): Boolean {
        return try {
            remoteConfig.getBoolean(ANALYTICS_DASHBOARD_WIDGETS_ENABLED)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get dashboard widgets flag")
            DEFAULT_FEATURE_ENABLED
        }
    }
    
    /**
     * Check if Analytics Export feature is enabled
     * 
     * @return true if export functionality should be available
     */
    fun isExportEnabled(): Boolean {
        return try {
            remoteConfig.getBoolean(ANALYTICS_EXPORT_ENABLED)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get export flag")
            DEFAULT_FEATURE_ENABLED
        }
    }
    
    /**
     * Check if Goal Tracking feature is enabled
     * 
     * @return true if goal tracking should be available
     */
    fun isGoalTrackingEnabled(): Boolean {
        return try {
            remoteConfig.getBoolean(ANALYTICS_GOAL_TRACKING_ENABLED)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get goal tracking flag")
            DEFAULT_FEATURE_ENABLED
        }
    }
    
    /**
     * Get the rollout percentage for analytics features
     * 
     * @return percentage (0-100) of users who should see analytics features
     */
    fun getFeatureRolloutPercentage(): Int {
        return try {
            remoteConfig.getLong(ANALYTICS_ROLLOUT_PERCENTAGE).toInt()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get rollout percentage")
            DEFAULT_ROLLOUT_PERCENTAGE
        }
    }
    
    /**
     * Get the maximum number of dashboard widgets to display
     * 
     * @return widget limit for dashboard configuration
     */
    fun getDashboardWidgetLimit(): Int {
        return try {
            remoteConfig.getLong(ANALYTICS_DASHBOARD_WIDGET_LIMIT).toInt()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get widget limit")
            DEFAULT_WIDGET_LIMIT
        }
    }
    
    /**
     * Check if analytics onboarding should be shown
     * 
     * @return true if onboarding flow should be presented
     */
    fun shouldShowAnalyticsOnboarding(): Boolean {
        return try {
            remoteConfig.getBoolean(ANALYTICS_ONBOARDING_ENABLED)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get onboarding flag")
            DEFAULT_FEATURE_ENABLED
        }
    }
    
    /**
     * Check if user should see analytics features based on rollout percentage
     * 
     * @param userId unique user identifier for consistent assignment
     * @return true if user is in the rollout group
     */
    fun shouldShowAnalyticsFeatures(userId: String): Boolean {
        return try {
            val rolloutPercentage = getFeatureRolloutPercentage()
            if (rolloutPercentage >= 100) return true
            if (rolloutPercentage <= 0) return false
            
            // Consistent hash-based assignment
            val userHash = userId.hashCode().toLong()
            val userPercentile = (userHash % 100).let { if (it < 0) it + 100 else it }
            
            userPercentile < rolloutPercentage
        } catch (e: Exception) {
            Timber.e(e, "Failed to determine user rollout status")
            false
        }
    }
    
    /**
     * Get all feature flag states for debugging
     * 
     * @return map of feature flags and their current values
     */
    fun getFeatureFlagStates(): Map<String, Any> {
        return try {
            mapOf(
                "volumeCalendarEnabled" to isVolumeCalendarEnabled(),
                "dashboardWidgetsEnabled" to isDashboardWidgetsEnabled(),
                "exportEnabled" to isExportEnabled(),
                "goalTrackingEnabled" to isGoalTrackingEnabled(),
                "rolloutPercentage" to getFeatureRolloutPercentage(),
                "widgetLimit" to getDashboardWidgetLimit(),
                "onboardingEnabled" to shouldShowAnalyticsOnboarding()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get feature flag states")
            emptyMap()
        }
    }
}