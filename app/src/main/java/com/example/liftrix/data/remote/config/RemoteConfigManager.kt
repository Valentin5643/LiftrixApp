package com.example.liftrix.data.remote.config

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote Config Manager for handling Firebase Remote Config operations
 * 
 * Manages remote configuration for dynamic content including:
 * - Help articles and documentation
 * - Feature flags and experiments
 * - App configuration parameters
 * - Legal document versions
 * - Support content and categories
 * 
 * Features:
 * - Fetch and activate remote config values
 * - Cache management with TTL
 * - Fallback to default values
 * - Error handling and retry logic
 * - Analytics and monitoring integration
 */
@Singleton
class RemoteConfigManager @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) {
    
    companion object {
        // Cache configuration
        private const val MINIMUM_FETCH_INTERVAL_SECONDS = 3600L // 1 hour
        private const val FETCH_TIMEOUT_SECONDS = 60L
        
        // Help content keys
        const val HELP_ARTICLES_JSON = "help_articles_json"
        const val HELP_CATEGORIES_JSON = "help_categories_json"
        const val HELP_CONTENT_VERSION = "help_content_version"
        const val HELP_FEATURED_ARTICLES = "help_featured_articles"
        
        // Support content keys
        const val SUPPORT_CATEGORIES_JSON = "support_categories_json"
        const val SUPPORT_FAQ_JSON = "support_faq_json"
        const val SUPPORT_CONTACT_INFO = "support_contact_info"
        
        // Legal document keys
        const val PRIVACY_POLICY_URL = "privacy_policy_url"
        const val TERMS_OF_SERVICE_URL = "terms_of_service_url"
        const val PRIVACY_POLICY_VERSION = "privacy_policy_version"
        const val TERMS_VERSION = "terms_version"
        
        // App configuration keys
        const val HELP_SEARCH_ENABLED = "help_search_enabled"
        const val SUPPORT_ATTACHMENTS_ENABLED = "support_attachments_enabled"
        const val MAX_SUPPORT_ATTACHMENTS = "max_support_attachments"
        const val HELP_FEEDBACK_ENABLED = "help_feedback_enabled"
        
        // Default values
        private val DEFAULT_VALUES = mapOf(
            HELP_CONTENT_VERSION to "1.0",
            HELP_SEARCH_ENABLED to true,
            SUPPORT_ATTACHMENTS_ENABLED to true,
            MAX_SUPPORT_ATTACHMENTS to 5,
            HELP_FEEDBACK_ENABLED to true,
            HELP_ARTICLES_JSON to "[]",
            HELP_CATEGORIES_JSON to "[]",
            SUPPORT_CATEGORIES_JSON to "[]",
            SUPPORT_FAQ_JSON to "[]",
            PRIVACY_POLICY_VERSION to "1.0",
            TERMS_VERSION to "1.0"
        )
    }
    
    private var isInitialized = false
    
    /**
     * Initializes Remote Config with default values and settings
     */
    suspend fun initialize(): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ConfigurationError(
                errorMessage = "Failed to initialize Remote Config",
                analyticsContext = mapOf(
                    "operation" to "REMOTE_CONFIG_INIT",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            if (isInitialized) {
                Timber.d("Remote Config already initialized")
                return@withContext
            }
            
            // Set up Remote Config settings
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(MINIMUM_FETCH_INTERVAL_SECONDS)
                .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SECONDS)
                .build()
            
            remoteConfig.setConfigSettingsAsync(configSettings).await()
            
            // Set default values
            remoteConfig.setDefaultsAsync(DEFAULT_VALUES).await()
            
            isInitialized = true
            Timber.d("Remote Config initialized successfully")
        }
    }
    
    /**
     * Fetches and activates the latest Remote Config values
     */
    suspend fun fetchAndActivate(forceRefresh: Boolean = false): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to fetch Remote Config",
                analyticsContext = mapOf(
                    "operation" to "REMOTE_CONFIG_FETCH",
                    "force_refresh" to forceRefresh.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            
            // Override minimum fetch interval if force refresh
            if (forceRefresh) {
                val configSettings = FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(0)
                    .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SECONDS)
                    .build()
                remoteConfig.setConfigSettingsAsync(configSettings).await()
            }
            
            // Fetch and activate
            val fetchResult = remoteConfig.fetchAndActivate().await()
            
            // Reset to normal fetch interval if we forced refresh
            if (forceRefresh) {
                val normalSettings = FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(MINIMUM_FETCH_INTERVAL_SECONDS)
                    .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SECONDS)
                    .build()
                remoteConfig.setConfigSettingsAsync(normalSettings).await()
            }
            
            Timber.d("Remote Config fetch and activate result: $fetchResult")
            fetchResult
        }
    }
    
    /**
     * Gets a string value from Remote Config
     */
    suspend fun getString(key: String): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ConfigurationError(
                errorMessage = "Failed to get Remote Config string value",
                analyticsContext = mapOf(
                    "operation" to "REMOTE_CONFIG_GET_STRING",
                    "key" to key,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val value = remoteConfig.getString(key)
            Timber.d("Remote Config getString($key) = $value")
            value
        }
    }
    
    /**
     * Gets a boolean value from Remote Config
     */
    suspend fun getBoolean(key: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ConfigurationError(
                errorMessage = "Failed to get Remote Config boolean value",
                analyticsContext = mapOf(
                    "operation" to "REMOTE_CONFIG_GET_BOOLEAN",
                    "key" to key,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val value = remoteConfig.getBoolean(key)
            Timber.d("Remote Config getBoolean($key) = $value")
            value
        }
    }
    
    /**
     * Gets a long value from Remote Config
     */
    suspend fun getLong(key: String): LiftrixResult<Long> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ConfigurationError(
                errorMessage = "Failed to get Remote Config long value",
                analyticsContext = mapOf(
                    "operation" to "REMOTE_CONFIG_GET_LONG",
                    "key" to key,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val value = remoteConfig.getLong(key)
            Timber.d("Remote Config getLong($key) = $value")
            value
        }
    }
    
    /**
     * Gets help articles JSON from Remote Config
     */
    suspend fun getHelpArticlesJson(): LiftrixResult<String> = getString(HELP_ARTICLES_JSON)
    
    /**
     * Gets help categories JSON from Remote Config
     */
    suspend fun getHelpCategoriesJson(): LiftrixResult<String> = getString(HELP_CATEGORIES_JSON)
    
    /**
     * Gets help content version from Remote Config
     */
    suspend fun getHelpContentVersion(): LiftrixResult<String> = getString(HELP_CONTENT_VERSION)
    
    /**
     * Gets featured articles list from Remote Config
     */
    suspend fun getFeaturedArticlesJson(): LiftrixResult<String> = getString(HELP_FEATURED_ARTICLES)
    
    /**
     * Gets support categories JSON from Remote Config
     */
    suspend fun getSupportCategoriesJson(): LiftrixResult<String> = getString(SUPPORT_CATEGORIES_JSON)
    
    /**
     * Gets support FAQ JSON from Remote Config
     */
    suspend fun getSupportFaqJson(): LiftrixResult<String> = getString(SUPPORT_FAQ_JSON)
    
    /**
     * Gets privacy policy URL from Remote Config
     */
    suspend fun getPrivacyPolicyUrl(): LiftrixResult<String> = getString(PRIVACY_POLICY_URL)
    
    /**
     * Gets terms of service URL from Remote Config
     */
    suspend fun getTermsOfServiceUrl(): LiftrixResult<String> = getString(TERMS_OF_SERVICE_URL)
    
    /**
     * Gets privacy policy version from Remote Config
     */
    suspend fun getPrivacyPolicyVersion(): LiftrixResult<String> = getString(PRIVACY_POLICY_VERSION)
    
    /**
     * Gets terms version from Remote Config
     */
    suspend fun getTermsVersion(): LiftrixResult<String> = getString(TERMS_VERSION)
    
    /**
     * Checks if help search is enabled
     */
    suspend fun isHelpSearchEnabled(): LiftrixResult<Boolean> = getBoolean(HELP_SEARCH_ENABLED)
    
    /**
     * Checks if support attachments are enabled
     */
    suspend fun isSupportAttachmentsEnabled(): LiftrixResult<Boolean> = getBoolean(SUPPORT_ATTACHMENTS_ENABLED)
    
    /**
     * Gets maximum number of support attachments allowed
     */
    suspend fun getMaxSupportAttachments(): LiftrixResult<Long> = getLong(MAX_SUPPORT_ATTACHMENTS)
    
    /**
     * Checks if help feedback is enabled
     */
    suspend fun isHelpFeedbackEnabled(): LiftrixResult<Boolean> = getBoolean(HELP_FEEDBACK_ENABLED)
    
    /**
     * Gets all Remote Config values as a map
     */
    suspend fun getAllValues(): LiftrixResult<Map<String, String>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ConfigurationError(
                errorMessage = "Failed to get all Remote Config values",
                analyticsContext = mapOf(
                    "operation" to "REMOTE_CONFIG_GET_ALL",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val allValues = remoteConfig.all.mapValues { it.value.asString() }
            Timber.d("Remote Config getAllValues() = ${allValues.size} values")
            allValues
        }
    }
    
    /**
     * Gets Remote Config info for debugging
     */
    suspend fun getConfigInfo(): LiftrixResult<RemoteConfigInfo> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ConfigurationError(
                errorMessage = "Failed to get Remote Config info",
                analyticsContext = mapOf(
                    "operation" to "REMOTE_CONFIG_GET_INFO",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val info = remoteConfig.info
            RemoteConfigInfo(
                lastFetchTimeMillis = info.fetchTimeMillis,
                lastFetchStatus = info.lastFetchStatus,
                configSettings = RemoteConfigSettingsInfo(
                    fetchTimeoutInSeconds = info.configSettings.fetchTimeoutInSeconds,
                    minimumFetchIntervalInSeconds = info.configSettings.minimumFetchIntervalInSeconds
                )
            )
        }
    }
    
    /**
     * Ensures Remote Config is initialized before use
     */
    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            initialize().getOrThrow()
        }
    }
}

/**
 * Data class containing Remote Config information
 */
data class RemoteConfigInfo(
    val lastFetchTimeMillis: Long,
    val lastFetchStatus: Int,
    val configSettings: RemoteConfigSettingsInfo
)

/**
 * Data class containing Remote Config settings information
 */
data class RemoteConfigSettingsInfo(
    val fetchTimeoutInSeconds: Long,
    val minimumFetchIntervalInSeconds: Long
)