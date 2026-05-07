package com.example.liftrix.domain.usecase.settings

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.service.FeatureFlagService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for feature flag evaluation, A/B testing, and remote configuration management.
 * 
 * This use case provides a unified interface for feature flag operations through the
 * FeatureFlagService abstraction layer. It handles comprehensive feature flag evaluation
 * with proper error handling and context switching for background operations.
 * 
 * Key Features:
 * - Feature flag evaluation with caching for performance
 * - A/B test variant assignment with consistency guarantees
 * - Remote configuration synchronization with Firebase
 * - Fallback to local defaults when remote config unavailable
 * - Bulk feature flag retrieval for dashboard configuration
 * - Feature flag validation and error handling
 * 
 * Error Handling:
 * All operations return LiftrixResult<T> for consistent error handling with proper
 * context information and recovery mechanisms.
 * 
 * Usage:
 * ```
 * val isEnabled = evaluateFeatureFlagUseCase.isFeatureEnabled("new_dashboard_layout")
 * isEnabled.fold(
 *     onSuccess = { enabled -> if (enabled) showNewDashboard() },
 *     onFailure = { error -> handleError(error) }
 * )
 * ```
 */
@Singleton
class EvaluateFeatureFlagUseCase @Inject constructor(
    private val featureFlagService: FeatureFlagService
) {
    
    /**
     * Checks if a specific feature flag is enabled.
     * 
     * Evaluates a feature flag by key and returns whether it's enabled for the current
     * user context. Uses cached values for performance with fallback to remote config.
     * 
     * @param featureKey The unique identifier for the feature flag
     * @return LiftrixResult<Boolean> indicating if the feature is enabled
     */
    suspend fun isFeatureEnabled(featureKey: String): LiftrixResult<Boolean> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ConfigurationError(
                    errorMessage = "Failed to evaluate feature flag '$featureKey': ${throwable.message}",
                    configKey = "isFeatureEnabled"
                )
            }
        ) {
            Timber.d("Evaluating feature flag: $featureKey")
            
            val result = featureFlagService.isFeatureEnabled(featureKey)
            result.getOrThrow()
        }
    }
    
    /**
     * Gets the A/B test variant for a specific test.
     * 
     * Retrieves the assigned A/B test variant for the current user. Variants are
     * assigned consistently per user to ensure stable experiment conditions.
     * 
     * @param testKey The unique identifier for the A/B test
     * @return LiftrixResult<String> containing the assigned variant (e.g., "control", "variant_a")
     */
    suspend fun getABTestVariant(testKey: String): LiftrixResult<String> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ConfigurationError(
                    errorMessage = "Failed to retrieve A/B test variant for '$testKey': ${throwable.message}",
                    configKey = "getABTestVariant"
                )
            }
        ) {
            Timber.d("Retrieving A/B test variant for: $testKey")
            
            val result = featureFlagService.getABTestVariant(testKey)
            result.getOrThrow()
        }
    }
    
    /**
     * Retrieves all available feature flags and their current values.
     * 
     * Returns a complete map of all feature flags with their current enabled/disabled
     * states. Useful for dashboard configuration and bulk feature evaluation.
     * 
     * @return LiftrixResult<Map<String, Boolean>> containing all feature flags
     */
    suspend fun getAllFeatureFlags(): LiftrixResult<Map<String, Boolean>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ConfigurationError(
                    errorMessage = "Failed to retrieve all feature flags: ${throwable.message}",
                    configKey = "getAllFeatureFlags"
                )
            }
        ) {
            Timber.d("Retrieving all feature flags")
            
            val result = featureFlagService.getAllFeatureFlags()
            result.getOrThrow()
        }
    }
    
    /**
     * Manually refreshes the remote configuration.
     * 
     * Forces a refresh of feature flags and A/B test configuration from Firebase
     * Remote Config. Useful for testing and immediate application of configuration changes.
     * 
     * @return LiftrixResult<Unit> indicating success or failure of refresh
     */
    suspend fun refreshRemoteConfig(): LiftrixResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ConfigurationError(
                    errorMessage = "Failed to refresh remote configuration: ${throwable.message}",
                    configKey = "refreshRemoteConfig"
                )
            }
        ) {
            Timber.d("Refreshing remote configuration")
            
            val result = featureFlagService.refreshRemoteConfig()
            result.getOrThrow()
        }
    }
    
    /**
     * Checks multiple feature flags efficiently in a single operation.
     * 
     * Batch evaluation of multiple feature flags to optimize performance when
     * configuring complex UI states that depend on multiple flags.
     * 
     * @param featureKeys List of feature flag keys to evaluate
     * @return LiftrixResult<Map<String, Boolean>> containing evaluation results
     */
    suspend fun evaluateMultipleFeatures(
        featureKeys: List<String>
    ): LiftrixResult<Map<String, Boolean>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ConfigurationError(
                    errorMessage = "Failed to evaluate multiple feature flags: ${throwable.message}",
                    configKey = "evaluateMultipleFeatures"
                )
            }
        ) {
            Timber.d("Evaluating ${featureKeys.size} feature flags")
            
            val results = mutableMapOf<String, Boolean>()
            
            // Evaluate each feature flag
            featureKeys.forEach { featureKey ->
                val enabled = featureFlagService.isFeatureEnabled(featureKey).getOrThrow()
                results[featureKey] = enabled
            }
            
            results
        }
    }
    
    /**
     * Checks if a feature flag is enabled with a fallback value.
     * 
     * Evaluates a feature flag and returns a fallback value if the flag cannot be
     * evaluated due to network issues or configuration errors.
     * 
     * @param featureKey The unique identifier for the feature flag
     * @param fallback The fallback value to use if evaluation fails
     * @return Boolean indicating if the feature is enabled (or fallback value)
     */
    suspend fun isFeatureEnabledWithFallback(
        featureKey: String,
        fallback: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("Evaluating feature flag with fallback: $featureKey, fallback: $fallback")
            
            val result = featureFlagService.isFeatureEnabled(featureKey)
            result.getOrElse { fallback }
        } catch (e: Exception) {
            Timber.w(e, "Failed to evaluate feature flag $featureKey, using fallback: $fallback")
            fallback
        }
    }
    
    /**
     * Validates that a feature flag exists and is properly configured.
     * 
     * Checks if a feature flag key exists in the remote configuration and
     * returns validation information including default values and constraints.
     * 
     * @param featureKey The unique identifier for the feature flag
     * @return LiftrixResult<FeatureFlagValidation> containing validation results
     */
    suspend fun validateFeatureFlag(featureKey: String): LiftrixResult<FeatureFlagValidation> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ConfigurationError(
                    errorMessage = "Failed to validate feature flag '$featureKey': ${throwable.message}",
                    configKey = "validateFeatureFlag"
                )
            }
        ) {
            Timber.d("Validating feature flag: $featureKey")
            
            val allFlags = featureFlagService.getAllFeatureFlags().getOrThrow()
            val exists = allFlags.containsKey(featureKey)
            val isEnabled = allFlags[featureKey] ?: false
            
            FeatureFlagValidation(
                featureKey = featureKey,
                exists = exists,
                isEnabled = isEnabled,
                hasDefaultValue = exists,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Gets feature flag configuration for a specific feature group.
     * 
     * Retrieves all feature flags that belong to a specific feature group or prefix,
     * useful for organizing related features and bulk configuration.
     * 
     * @param groupPrefix The prefix or group identifier for feature flags
     * @return LiftrixResult<Map<String, Boolean>> containing grouped feature flags
     */
    suspend fun getFeatureGroupConfiguration(
        groupPrefix: String
    ): LiftrixResult<Map<String, Boolean>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ConfigurationError(
                    errorMessage = "Failed to retrieve feature group configuration for '$groupPrefix': ${throwable.message}",
                    configKey = "getFeatureGroupConfiguration"
                )
            }
        ) {
            Timber.d("Retrieving feature group configuration for prefix: $groupPrefix")
            
            val allFlags = featureFlagService.getAllFeatureFlags().getOrThrow()
            val groupFlags = allFlags.filterKeys { it.startsWith(groupPrefix) }
            
            groupFlags
        }
    }
    
    /**
     * Data class representing feature flag validation results.
     */
    data class FeatureFlagValidation(
        val featureKey: String,
        val exists: Boolean,
        val isEnabled: Boolean,
        val hasDefaultValue: Boolean,
        val lastUpdated: Long
    )
    
    /**
     * Common feature flag keys used throughout the application.
     */
    object FeatureFlags {
        const val NEW_DASHBOARD_LAYOUT = "new_dashboard_layout"
        const val ADVANCED_ANALYTICS = "advanced_analytics"
        const val SOCIAL_FEATURES = "social_features"
        const val PREMIUM_CHARTS = "premium_charts"
        const val WORKOUT_RECOMMENDATIONS = "workout_recommendations"
        const val CALORIE_TRACKING = "calorie_tracking"
        const val PROGRESS_INSIGHTS = "progress_insights"
        const val WIDGET_CUSTOMIZATION = "widget_customization"
        const val OFFLINE_SYNC = "offline_sync"
        const val PERFORMANCE_MONITORING = "performance_monitoring"
    }
    
    /**
     * Common A/B test keys used throughout the application.
     */
    object ABTests {
        const val ONBOARDING_FLOW = "onboarding_flow_test"
        const val DASHBOARD_LAYOUT = "dashboard_layout_test"
        const val WORKOUT_CREATION = "workout_creation_test"
        const val PROGRESS_VISUALIZATION = "progress_visualization_test"
        const val SOCIAL_INTEGRATION = "social_integration_test"
    }
}