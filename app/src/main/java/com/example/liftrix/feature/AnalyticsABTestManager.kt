package com.example.liftrix.feature

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analytics A/B Test Manager for dashboard configurations
 * 
 * Manages A/B testing for:
 * - Dashboard widget configurations
 * - Analytics feature variations
 * - User experience optimizations
 * - Feature adoption tracking
 */
@Singleton
class AnalyticsABTestManager @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val firebaseAnalytics: FirebaseAnalytics,
    private val featureFlags: AnalyticsFeatureFlags
) {
    companion object {
        // A/B Test Configuration Keys
        private const val DASHBOARD_CONFIG_VARIANT = "dashboard_config_variant"
        private const val WIDGET_LAYOUT_VARIANT = "widget_layout_variant"
        private const val ONBOARDING_FLOW_VARIANT = "onboarding_flow_variant"
        private const val EXPORT_UI_VARIANT = "export_ui_variant"
        
        // Analytics Event Names
        private const val EVENT_AB_TEST_ASSIGNMENT = "ab_test_assignment"
        private const val EVENT_FEATURE_ADOPTION = "feature_adoption"
        private const val EVENT_DASHBOARD_INTERACTION = "dashboard_interaction"
        
        // Default configurations
        private const val DEFAULT_DASHBOARD_VARIANT = "beginner"
        private const val DEFAULT_WIDGET_LAYOUT = "grid"
        private const val DEFAULT_ONBOARDING_FLOW = "standard"
        private const val DEFAULT_EXPORT_UI = "dropdown"
    }
    
    /**
     * Dashboard configuration variants for A/B testing
     */
    sealed class DashboardConfiguration(val variant: String, val displayName: String) {
        object Beginner : DashboardConfiguration("beginner", "Beginner") {
            val widgets = listOf(
                AnalyticsWidget.TotalVolume,
                AnalyticsWidget.WorkoutFrequency,
                AnalyticsWidget.ConsistencyStreak,
                AnalyticsWidget.ProgressChart
            )
            val maxWidgets = 4
        }
        
        object Intermediate : DashboardConfiguration("intermediate", "Intermediate") {
            val widgets = listOf(
                AnalyticsWidget.TotalVolume,
                AnalyticsWidget.WorkoutFrequency,
                AnalyticsWidget.ProgressChart,
                AnalyticsWidget.ConsistencyStreak,
                AnalyticsWidget.VolumeCalendar,
                AnalyticsWidget.WeeklyTrends
            )
            val maxWidgets = 6
        }
        
        object Advanced : DashboardConfiguration("advanced", "Advanced") {
            val widgets = listOf(
                AnalyticsWidget.TotalVolume,
                AnalyticsWidget.WorkoutFrequency,
                AnalyticsWidget.ProgressChart,
                AnalyticsWidget.ConsistencyStreak,
                AnalyticsWidget.VolumeCalendar,
                AnalyticsWidget.MuscleGroupDistribution,
                AnalyticsWidget.OneRMProgression,
                AnalyticsWidget.VolumeLoadProgression,
                AnalyticsWidget.RecoveryPatterns,
                AnalyticsWidget.WeeklyTrends
            )
            val maxWidgets = 10
        }
        
        object Compact : DashboardConfiguration("compact", "Compact") {
            val widgets = listOf(
                AnalyticsWidget.TotalVolume,
                AnalyticsWidget.WorkoutFrequency,
                AnalyticsWidget.ProgressChart
            )
            val maxWidgets = 3
        }
    }
    
    /**
     * Widget layout variants for testing
     */
    sealed class WidgetLayoutVariant(val variant: String) {
        object Grid : WidgetLayoutVariant("grid")
        object List : WidgetLayoutVariant("list")
        object Cards : WidgetLayoutVariant("cards")
        object Carousel : WidgetLayoutVariant("carousel")
    }
    
    /**
     * Onboarding flow variants
     */
    sealed class OnboardingFlowVariant(val variant: String) {
        object Standard : OnboardingFlowVariant("standard")
        object Interactive : OnboardingFlowVariant("interactive")
        object Minimal : OnboardingFlowVariant("minimal")
        object Gamified : OnboardingFlowVariant("gamified")
    }
    
    /**
     * Analytics widget types for configuration.
     * 
     * IMPORTANT: These widget IDs and names must match the main AnalyticsWidget enum
     * to avoid conflicts and ensure consistency across the application.
     */
    sealed class AnalyticsWidget(val id: String, val displayName: String) {
        object TotalVolume : AnalyticsWidget("TotalVolume", "Total Volume")
        object WorkoutFrequency : AnalyticsWidget("WorkoutFrequency", "Workout Frequency")
        object ProgressChart : AnalyticsWidget("ProgressChart", "Progress Chart")
        object ConsistencyStreak : AnalyticsWidget("ConsistencyStreak", "Consistency Streak")
        object VolumeCalendar : AnalyticsWidget("VolumeCalendar", "Volume Calendar")
        object MuscleGroupDistribution : AnalyticsWidget("MuscleGroupProgress", "Muscle Group Balance")
        object OneRMProgression : AnalyticsWidget("OneRMProgression", "1RM Progression")
        object VolumeLoadProgression : AnalyticsWidget("VolumeLoadProgression", "Volume Load Progression")
        object RecoveryPatterns : AnalyticsWidget("RecoveryPatterns", "Recovery Patterns")
        object WeeklyTrends : AnalyticsWidget("WeeklyTrends", "Weekly Trends")
    }
    
    /**
     * Get dashboard configuration for user based on A/B test assignment
     * 
     * @param userId unique user identifier for consistent assignment
     * @return dashboard configuration variant
     */
    fun getDashboardConfiguration(userId: String): DashboardConfiguration {
        return try {
            // Check if analytics features are enabled for this user
            if (!featureFlags.shouldShowAnalyticsFeatures(userId)) {
                return DashboardConfiguration.Beginner
            }
            
            val variant = getUserVariant(userId, DASHBOARD_CONFIG_VARIANT, DEFAULT_DASHBOARD_VARIANT)
            
            val configuration = when (variant) {
                "beginner" -> DashboardConfiguration.Beginner
                "intermediate" -> DashboardConfiguration.Intermediate
                "advanced" -> DashboardConfiguration.Advanced
                "compact" -> DashboardConfiguration.Compact
                else -> DashboardConfiguration.Beginner
            }
            
            // Track A/B test assignment
            trackABTestAssignment(userId, "dashboard_config", variant)
            
            configuration
        } catch (e: Exception) {
            Timber.e(e, "Failed to get dashboard configuration")
            DashboardConfiguration.Beginner
        }
    }
    
    /**
     * Get widget layout variant for user
     * 
     * @param userId unique user identifier
     * @return widget layout variant
     */
    fun getWidgetLayoutVariant(userId: String): WidgetLayoutVariant {
        return try {
            val variant = getUserVariant(userId, WIDGET_LAYOUT_VARIANT, DEFAULT_WIDGET_LAYOUT)
            
            val layoutVariant = when (variant) {
                "grid" -> WidgetLayoutVariant.Grid
                "list" -> WidgetLayoutVariant.List
                "cards" -> WidgetLayoutVariant.Cards
                "carousel" -> WidgetLayoutVariant.Carousel
                else -> WidgetLayoutVariant.Grid
            }
            
            trackABTestAssignment(userId, "widget_layout", variant)
            
            layoutVariant
        } catch (e: Exception) {
            Timber.e(e, "Failed to get widget layout variant")
            WidgetLayoutVariant.Grid
        }
    }
    
    /**
     * Get onboarding flow variant for user
     * 
     * @param userId unique user identifier
     * @return onboarding flow variant
     */
    fun getOnboardingFlowVariant(userId: String): OnboardingFlowVariant {
        return try {
            val variant = getUserVariant(userId, ONBOARDING_FLOW_VARIANT, DEFAULT_ONBOARDING_FLOW)
            
            val flowVariant = when (variant) {
                "standard" -> OnboardingFlowVariant.Standard
                "interactive" -> OnboardingFlowVariant.Interactive
                "minimal" -> OnboardingFlowVariant.Minimal
                "gamified" -> OnboardingFlowVariant.Gamified
                else -> OnboardingFlowVariant.Standard
            }
            
            trackABTestAssignment(userId, "onboarding_flow", variant)
            
            flowVariant
        } catch (e: Exception) {
            Timber.e(e, "Failed to get onboarding flow variant")
            OnboardingFlowVariant.Standard
        }
    }
    
    /**
     * Get user variant assignment with consistent hashing
     * 
     * @param userId unique user identifier
     * @param testKey remote config key for the test
     * @param defaultValue fallback value
     * @return assigned variant string
     */
    private fun getUserVariant(userId: String, testKey: String, defaultValue: String): String {
        return try {
            // Get available variants from remote config
            val variants = remoteConfig.getString(testKey).takeIf { it.isNotEmpty() }
                ?: return defaultValue
            
            val variantList = variants.split(",").map { it.trim() }
            if (variantList.isEmpty()) return defaultValue
            
            // Consistent hash-based assignment
            val userHash = userId.hashCode().toLong()
            val variantIndex = (userHash % variantList.size).let { 
                if (it < 0) it + variantList.size else it 
            }.toInt()
            
            variantList[variantIndex]
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user variant for $testKey")
            defaultValue
        }
    }
    
    /**
     * Track A/B test assignment for analytics
     * 
     * @param userId user identifier
     * @param testName name of the A/B test
     * @param variant assigned variant
     */
    private fun trackABTestAssignment(userId: String, testName: String, variant: String) {
        try {
            val params = mutableMapOf<String, Any>(
                "test_name" to testName,
                "variant" to variant,
                "user_id" to userId
            )
            
            firebaseAnalytics.logEvent(EVENT_AB_TEST_ASSIGNMENT, params.toBundle())
            
            Timber.d("A/B test assignment tracked: $testName = $variant for user $userId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to track A/B test assignment")
        }
    }
    
    /**
     * Track feature adoption metrics
     * 
     * @param userId user identifier
     * @param feature feature name
     * @param action adoption action (viewed, used, completed)
     */
    fun trackFeatureAdoption(userId: String, feature: String, action: String) {
        try {
            val params = mutableMapOf<String, Any>(
                "feature" to feature,
                "action" to action,
                "user_id" to userId
            )
            
            firebaseAnalytics.logEvent(EVENT_FEATURE_ADOPTION, params.toBundle())
            
            Timber.d("Feature adoption tracked: $feature - $action for user $userId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to track feature adoption")
        }
    }
    
    /**
     * Track dashboard interaction metrics
     * 
     * @param userId user identifier
     * @param widget widget that was interacted with
     * @param interaction type of interaction
     */
    fun trackDashboardInteraction(userId: String, widget: String, interaction: String) {
        try {
            val params = mutableMapOf<String, Any>(
                "widget" to widget,
                "interaction" to interaction,
                "user_id" to userId
            )
            
            firebaseAnalytics.logEvent(EVENT_DASHBOARD_INTERACTION, params.toBundle())
            
            Timber.d("Dashboard interaction tracked: $widget - $interaction for user $userId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to track dashboard interaction")
        }
    }
    
    /**
     * Convert map to Bundle for Firebase Analytics
     */
    private fun Map<String, Any>.toBundle() = android.os.Bundle().apply {
        for ((key, value) in this@toBundle) {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Double -> putDouble(key, value)
                is Float -> putFloat(key, value)
                is Boolean -> putBoolean(key, value)
                else -> putString(key, value.toString())
            }
        }
    }
}