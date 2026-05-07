package com.example.liftrix.service

import com.example.liftrix.core.data.BuildConfig
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of FeatureFlagService using Firebase Remote Config
 * 
 * Provides feature flag evaluation, A/B testing, and remote configuration
 * management with local caching and fallback capabilities.
 * 
 * Key Implementation Details:
 * - Uses Firebase Remote Config for remote feature flag management
 * - Implements local caching with in-memory Map for performance
 * - Provides fallback to hardcoded defaults when remote config fails
 * - Supports A/B testing with consistent variant assignment
 * - All operations use proper error handling with LiftrixResult<T>
 * 
 * Performance Optimizations:
 * - Local cache reduces Firebase API calls
 * - Async operations with proper dispatcher usage
 * - Fallback mechanisms prevent blocking user experience
 */
@Singleton
class FeatureFlagServiceImpl @Inject constructor(
    private val firebaseRemoteConfig: FirebaseRemoteConfig,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FeatureFlagService {
    
    companion object {
        // Default feature flag values as fallback
        private val DEFAULT_FEATURE_FLAGS = mapOf(
            "use_new_progress_dashboard" to false,
            "enable_analytics_widgets" to true,
            "show_calorie_tracking" to true,
            "enable_export_analytics" to false,
            "show_premium_features" to true,
            "enable_advanced_charts" to false,
            "show_social_features" to true,
            "enable_workout_recommendations" to false
        )

        // A/B test default variants
        private val DEFAULT_AB_TEST_VARIANTS = mapOf(
            "progress_dashboard_layout" to "control",
            "onboarding_flow" to "control",
            "premium_pricing_display" to "control",
            "chart_visualization_style" to "control"
        )

        // Remote config fetch timeout in seconds
        private const val FETCH_TIMEOUT_SECONDS = 10L

        // Cache expiration time in seconds (1 hour for production)
        private const val CACHE_EXPIRATION_SECONDS = 3600L

        // Follow-up: TEMPORARY DEBUG OVERRIDE
        // WHY: Enables immediate Remote Config fetching in debug builds for testing/debugging
        // WHEN TO REMOVE: After Remote Config is fully tested and Firebase Console shows fetch activity
        // PRODUCTION IMPACT: None - only affects debug builds (BuildConfig.DEBUG)
        private const val DEBUG_CACHE_EXPIRATION_SECONDS = 0L // Immediate fetching for debug
    }
    
    // In-memory cache for feature flags
    private val featureFlagCache = mutableMapOf<String, Boolean>()
    private val abTestVariantCache = mutableMapOf<String, String>()
    
    // Flag to track if remote config has been initialized
    private var isRemoteConfigInitialized = false
    
    init {
        initializeRemoteConfig()
    }
    
    /**
     * Initialize Firebase Remote Config with appropriate settings
     *
     * DEBUG BUILD BEHAVIOR:
     * - Sets minimumFetchIntervalInSeconds = 0 for immediate fetching
     * - Enables real-time testing of feature flags
     *
     * PRODUCTION BUILD BEHAVIOR:
     * - Uses standard 1-hour fetch interval
     * - Normal caching behavior
     */
    private fun initializeRemoteConfig() {
        try {
            // Determine fetch interval based on build type
            val fetchInterval = if (BuildConfig.DEBUG) {
                DEBUG_CACHE_EXPIRATION_SECONDS
            } else {
                CACHE_EXPIRATION_SECONDS
            }

            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(fetchInterval)
                .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SECONDS)
                .build()

            firebaseRemoteConfig.setConfigSettingsAsync(configSettings)
            firebaseRemoteConfig.setDefaultsAsync(DEFAULT_FEATURE_FLAGS + DEFAULT_AB_TEST_VARIANTS)

            // Log configuration mode
            if (BuildConfig.DEBUG) {
                Timber.i("🔧 DEBUG MODE: FeatureFlagService initialized with IMMEDIATE fetching (interval = 0s)")
            }

            // Perform initial fetch
            performInitialFetch()

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Remote Config")
        }
    }
    
    /**
     * Perform initial remote config fetch
     */
    private fun performInitialFetch() {
        try {
            firebaseRemoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    isRemoteConfigInitialized = true
                    populateLocalCache()
                    Timber.d("Remote Config initialized successfully")
                } else {
                    Timber.w("Failed to fetch remote config on initialization")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during initial remote config fetch")
        }
    }
    
    /**
     * Populate local cache with values from remote config
     */
    private fun populateLocalCache() {
        try {
            // Cache feature flags
            DEFAULT_FEATURE_FLAGS.keys.forEach { key ->
                featureFlagCache[key] = firebaseRemoteConfig.getBoolean(key)
            }
            
            // Cache A/B test variants
            DEFAULT_AB_TEST_VARIANTS.keys.forEach { key ->
                abTestVariantCache[key] = firebaseRemoteConfig.getString(key)
            }
            
            Timber.d("Local cache populated with ${featureFlagCache.size} feature flags and ${abTestVariantCache.size} A/B test variants")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to populate local cache")
        }
    }
    
    override suspend fun isFeatureEnabled(featureKey: String): LiftrixResult<Boolean> =
        withContext(dispatcher) {
            try {
                // First check local cache
                featureFlagCache[featureKey]?.let { cachedValue ->
                    return@withContext Result.success(cachedValue)
                }
                
                // If not in cache, check remote config
                val remoteValue = if (isRemoteConfigInitialized) {
                    firebaseRemoteConfig.getBoolean(featureKey)
                } else {
                    // Fallback to default if remote config not ready
                    DEFAULT_FEATURE_FLAGS[featureKey] ?: false
                }
                
                // Update cache
                featureFlagCache[featureKey] = remoteValue
                
                Timber.d("Feature flag '$featureKey' evaluated to: $remoteValue")
                Result.success(remoteValue)
                
            } catch (e: Exception) {
                val fallbackValue = DEFAULT_FEATURE_FLAGS[featureKey] ?: false
                Timber.w(e, "Failed to evaluate feature flag '$featureKey', using fallback: $fallbackValue")
                
                Result.failure(
                    LiftrixError.NetworkError(
                        errorMessage = "Failed to fetch feature flag: ${e.message}",
                        networkType = "firebase_remote_config",
                        analyticsContext = mapOf(
                            "feature_key" to featureKey,
                            "fallback_value" to fallbackValue.toString(),
                            "error_type" to "feature_flag_fetch_error"
                        )
                    )
                )
            }
        }
    
    override suspend fun getABTestVariant(testKey: String): LiftrixResult<String> =
        withContext(dispatcher) {
            try {
                // First check local cache
                abTestVariantCache[testKey]?.let { cachedVariant ->
                    return@withContext Result.success(cachedVariant)
                }
                
                // If not in cache, check remote config
                val remoteVariant = if (isRemoteConfigInitialized) {
                    firebaseRemoteConfig.getString(testKey)
                } else {
                    // Fallback to default variant if remote config not ready
                    DEFAULT_AB_TEST_VARIANTS[testKey] ?: "control"
                }
                
                // Update cache
                abTestVariantCache[testKey] = remoteVariant
                
                Timber.d("A/B test variant for '$testKey': $remoteVariant")
                Result.success(remoteVariant)
                
            } catch (e: Exception) {
                val fallbackVariant = DEFAULT_AB_TEST_VARIANTS[testKey] ?: "control"
                Timber.w(e, "Failed to get A/B test variant for '$testKey', using fallback: $fallbackVariant")
                
                Result.failure(
                    LiftrixError.NetworkError(
                        errorMessage = "Failed to fetch A/B test variant: ${e.message}",
                        networkType = "firebase_remote_config",
                        analyticsContext = mapOf(
                            "test_key" to testKey,
                            "fallback_variant" to fallbackVariant,
                            "error_type" to "ab_test_variant_fetch_error"
                        )
                    )
                )
            }
        }
    
    override suspend fun getAllFeatureFlags(): LiftrixResult<Map<String, Boolean>> =
        withContext(dispatcher) {
            try {
                val allFlags = mutableMapOf<String, Boolean>()
                
                // Get all feature flags from defaults first
                DEFAULT_FEATURE_FLAGS.keys.forEach { key ->
                    val value = if (isRemoteConfigInitialized) {
                        firebaseRemoteConfig.getBoolean(key)
                    } else {
                        DEFAULT_FEATURE_FLAGS[key] ?: false
                    }
                    allFlags[key] = value
                }
                
                // Update cache with all values
                featureFlagCache.putAll(allFlags)
                
                Timber.d("Retrieved ${allFlags.size} feature flags")
                Result.success(allFlags.toMap())
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to get all feature flags")
                
                Result.failure(
                    LiftrixError.NetworkError(
                        errorMessage = "Failed to fetch feature flags: ${e.message}",
                        networkType = "firebase_remote_config",
                        analyticsContext = mapOf(
                            "operation" to "get_all_feature_flags",
                            "error_type" to "bulk_fetch_error"
                        )
                    )
                )
            }
        }
    
    override suspend fun refreshRemoteConfig(): LiftrixResult<Unit> =
        withContext(dispatcher) {
            try {
                // Perform fetch and activate
                val fetchSuccess = firebaseRemoteConfig.fetchAndActivate().await()
                
                if (fetchSuccess) {
                    // Clear cache to force fresh values
                    featureFlagCache.clear()
                    abTestVariantCache.clear()
                    
                    // Repopulate cache with new values
                    populateLocalCache()
                    
                    isRemoteConfigInitialized = true
                    
                    Timber.d("Remote config refreshed successfully")
                    Result.success(Unit)
                } else {
                    Timber.w("Remote config refresh returned false")
                    Result.failure(
                        LiftrixError.NetworkError(
                            errorMessage = "Remote config refresh failed",
                            networkType = "firebase_remote_config",
                            analyticsContext = mapOf(
                                "operation" to "refresh_remote_config",
                                "error_type" to "refresh_failed"
                            )
                        )
                    )
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Exception during remote config refresh")
                
                Result.failure(
                    LiftrixError.NetworkError(
                        errorMessage = "Failed to refresh remote config: ${e.message}",
                        networkType = "firebase_remote_config",
                        analyticsContext = mapOf(
                            "operation" to "refresh_remote_config",
                            "error_type" to "refresh_exception",
                            "exception_type" to (e::class.simpleName ?: "Unknown")
                        )
                    )
                )
            }
        }
}
