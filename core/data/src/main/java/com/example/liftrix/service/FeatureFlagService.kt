package com.example.liftrix.service

import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for feature flags and A/B testing functionality
 * 
 * Provides access to feature flag evaluation, A/B test variant assignment,
 * and remote configuration management for controlling feature availability
 * and conducting experiments within the Liftrix application.
 * 
 * Key Features:
 * - Feature flag evaluation with caching for performance
 * - A/B test variant assignment with consistency guarantees
 * - Remote configuration synchronization with Firebase
 * - Fallback to local defaults when remote config unavailable
 * 
 * Implementation Notes:
 * - All methods return LiftrixResult<T> for consistent error handling
 * - Feature flags are cached locally for performance optimization
 * - Remote config is refreshed periodically and on app startup
 * - A/B test variants are assigned consistently per user
 */
interface FeatureFlagService {
    
    /**
     * Checks if a specific feature flag is enabled
     * 
     * @param featureKey The unique identifier for the feature flag
     * @return LiftrixResult<Boolean> indicating if the feature is enabled
     */
    suspend fun isFeatureEnabled(featureKey: String): LiftrixResult<Boolean>
    
    /**
     * Gets the A/B test variant for a specific test
     * 
     * @param testKey The unique identifier for the A/B test
     * @return LiftrixResult<String> containing the assigned variant (e.g., "control", "variant_a")
     */
    suspend fun getABTestVariant(testKey: String): LiftrixResult<String>
    
    /**
     * Retrieves all available feature flags and their current values
     * 
     * @return LiftrixResult<Map<String, Boolean>> containing all feature flags
     */
    suspend fun getAllFeatureFlags(): LiftrixResult<Map<String, Boolean>>
    
    /**
     * Manually refreshes the remote configuration
     * 
     * Forces a refresh of feature flags and A/B test configuration
     * from Firebase Remote Config. Useful for testing and immediate
     * application of configuration changes.
     * 
     * @return LiftrixResult<Unit> indicating success or failure of refresh
     */
    suspend fun refreshRemoteConfig(): LiftrixResult<Unit>
}