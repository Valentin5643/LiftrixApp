package com.example.liftrix.data.remote.config

import com.example.liftrix.core.data.BuildConfig
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
        private const val MINIMUM_FETCH_INTERVAL_SECONDS = 3600L // 1 hour (production)
        private const val FETCH_TIMEOUT_SECONDS = 60L

        // Follow-up: TEMPORARY DEBUG OVERRIDE
        // WHY: Enables immediate Remote Config fetching in debug builds for testing/debugging
        // WHEN TO REMOVE: After Remote Config is fully tested and Firebase Console shows fetch activity
        // PRODUCTION IMPACT: None - only affects debug builds (BuildConfig.DEBUG)
        private const val DEBUG_MINIMUM_FETCH_INTERVAL_SECONDS = 0L // Immediate fetching for debug

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
        const val AI_DISCLAIMER_URL = "ai_disclaimer_url"
        const val COMMUNITY_GUIDELINES_URL = "community_guidelines_url"
        const val CONTENT_MODERATION_POLICY_URL = "content_moderation_policy_url"
        const val REFUND_SUBSCRIPTION_POLICY_URL = "refund_subscription_policy_url"
        const val PRIVACY_POLICY_VERSION = "privacy_policy_version"
        const val TERMS_VERSION = "terms_version"
        const val AI_DISCLAIMER_VERSION = "ai_disclaimer_version"
        const val COMMUNITY_GUIDELINES_VERSION = "community_guidelines_version"
        const val CONTENT_MODERATION_VERSION = "content_moderation_version"
        const val REFUND_POLICY_VERSION = "refund_policy_version"

        // App configuration keys
        const val HELP_SEARCH_ENABLED = "help_search_enabled"
        const val SUPPORT_ATTACHMENTS_ENABLED = "support_attachments_enabled"
        const val MAX_SUPPORT_ATTACHMENTS = "max_support_attachments"
        const val HELP_FEEDBACK_ENABLED = "help_feedback_enabled"

        // AI Chat configuration keys
        const val AI_CHAT_ENABLED = "ai_chat_enabled"
        const val AI_DAILY_MESSAGE_LIMIT = "ai_daily_message_limit"
        const val AI_MONTHLY_TOKEN_LIMIT = "ai_monthly_token_limit"
        const val AI_COST_THRESHOLD_PER_HOUR = "ai_cost_threshold_per_hour"
        const val AI_JAILBREAK_THRESHOLD = "ai_jailbreak_threshold"
        const val AI_FITNESS_CONTEXT_WEIGHT = "ai_fitness_context_weight"
        const val AI_RATE_LIMIT_MULTIPLIER = "ai_rate_limit_multiplier"
        const val AI_ENABLE_ABUSE_LOGGING = "ai_enable_abuse_logging"
        const val AI_REVIEW_QUEUE_ENABLED = "ai_review_queue_enabled"
        const val AI_MODEL_NAME = "ai_model_name"
        const val AI_MAX_OUTPUT_TOKENS = "ai_max_output_tokens"
        const val AI_TEMPERATURE = "ai_temperature"
        const val AI_TOP_K = "ai_top_k"
        const val AI_TOP_P = "ai_top_p"

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
            TERMS_VERSION to "1.0",
            AI_DISCLAIMER_VERSION to "1.0",
            COMMUNITY_GUIDELINES_VERSION to "1.0",
            CONTENT_MODERATION_VERSION to "1.0",
            REFUND_POLICY_VERSION to "1.0",
            PRIVACY_POLICY_URL to "",
            TERMS_OF_SERVICE_URL to "",
            AI_DISCLAIMER_URL to "",
            COMMUNITY_GUIDELINES_URL to "",
            CONTENT_MODERATION_POLICY_URL to "",
            REFUND_SUBSCRIPTION_POLICY_URL to "",

            // AI Chat defaults
            AI_CHAT_ENABLED to true,
            AI_DAILY_MESSAGE_LIMIT to 50,
            AI_MONTHLY_TOKEN_LIMIT to 100000,
            AI_COST_THRESHOLD_PER_HOUR to 1.0,
            AI_JAILBREAK_THRESHOLD to 0.8,
            AI_FITNESS_CONTEXT_WEIGHT to 0.5,
            AI_RATE_LIMIT_MULTIPLIER to 10,
            AI_ENABLE_ABUSE_LOGGING to true,
            AI_REVIEW_QUEUE_ENABLED to false,
            AI_MODEL_NAME to "gemini-2.5-flash-lite",
            AI_MAX_OUTPUT_TOKENS to 500,
            AI_TEMPERATURE to 0.7,
            AI_TOP_K to 40,
            AI_TOP_P to 0.95,

            // Additional AI rate limiting keys for compatibility
            "ai_max_daily_messages" to 50L,
            "ai_max_monthly_tokens" to 100000L,
            "ai_rate_limit_enabled" to true,
            "ai_cost_threshold_per_hour" to 1.0,
            "ai_avg_response_time_ms" to 1500L
        )
    }

    private var isInitialized = false

    /**
     * Initializes Remote Config with default values and settings
     *
     * DEBUG BUILD BEHAVIOR:
     * - Sets minimumFetchIntervalInSeconds = 0 for immediate fetching
     * - Enables real-time testing of Remote Config changes
     * - Automatically calls fetchAndActivate() after initialization
     *
     * PRODUCTION BUILD BEHAVIOR:
     * - Uses standard 1-hour fetch interval
     * - Normal caching behavior
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

            // Determine fetch interval based on build type
            val fetchInterval = if (BuildConfig.DEBUG) {
                DEBUG_MINIMUM_FETCH_INTERVAL_SECONDS
            } else {
                MINIMUM_FETCH_INTERVAL_SECONDS
            }

            // Set up Remote Config settings
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(fetchInterval)
                .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SECONDS)
                .build()

            remoteConfig.setConfigSettingsAsync(configSettings).await()

            // Set default values
            remoteConfig.setDefaultsAsync(DEFAULT_VALUES).await()

            isInitialized = true

            // Log configuration mode
            if (BuildConfig.DEBUG) {
                Timber.i("🔧 DEBUG MODE: Remote Config initialized with IMMEDIATE fetching (interval = 0s)")
                Timber.i("🔧 DEBUG MODE: Calling fetchAndActivate() now...")

                // Immediately fetch and activate in debug builds
                try {
                    val fetchSuccess = remoteConfig.fetchAndActivate().await()
                    if (fetchSuccess) {
                        Timber.i("✅ DEBUG MODE: Remote Config fetch SUCCESS - values are now active")
                    } else {
                        Timber.w("⚠️ DEBUG MODE: Remote Config fetch returned false - using cached/default values")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ DEBUG MODE: Remote Config fetch FAILED - using default values")
                }
            } else {
                Timber.d("Remote Config initialized successfully with ${MINIMUM_FETCH_INTERVAL_SECONDS}s fetch interval")
            }
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
     * Gets a double value from Remote Config
     */
    suspend fun getDouble(key: String): LiftrixResult<Double> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ConfigurationError(
                errorMessage = "Failed to get Remote Config double value",
                analyticsContext = mapOf(
                    "operation" to "REMOTE_CONFIG_GET_DOUBLE",
                    "key" to key,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val value = remoteConfig.getDouble(key)
            Timber.d("Remote Config getDouble($key) = $value")
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
     * Gets AI disclaimer URL from Remote Config
     */
    suspend fun getAIDisclaimerUrl(): LiftrixResult<String> = getString(AI_DISCLAIMER_URL)

    /**
     * Gets community guidelines URL from Remote Config
     */
    suspend fun getCommunityGuidelinesUrl(): LiftrixResult<String> = getString(COMMUNITY_GUIDELINES_URL)

    /**
     * Gets content moderation policy URL from Remote Config
     */
    suspend fun getContentModerationPolicyUrl(): LiftrixResult<String> = getString(CONTENT_MODERATION_POLICY_URL)

    /**
     * Gets refund & subscription policy URL from Remote Config
     */
    suspend fun getRefundSubscriptionPolicyUrl(): LiftrixResult<String> = getString(REFUND_SUBSCRIPTION_POLICY_URL)

    /**
     * Gets privacy policy version from Remote Config
     */
    suspend fun getPrivacyPolicyVersion(): LiftrixResult<String> = getString(PRIVACY_POLICY_VERSION)

    /**
     * Gets terms version from Remote Config
     */
    suspend fun getTermsVersion(): LiftrixResult<String> = getString(TERMS_VERSION)

    /**
     * Gets AI disclaimer version from Remote Config
     */
    suspend fun getAIDisclaimerVersion(): LiftrixResult<String> = getString(AI_DISCLAIMER_VERSION)

    /**
     * Gets community guidelines version from Remote Config
     */
    suspend fun getCommunityGuidelinesVersion(): LiftrixResult<String> = getString(COMMUNITY_GUIDELINES_VERSION)

    /**
     * Gets content moderation policy version from Remote Config
     */
    suspend fun getContentModerationPolicyVersion(): LiftrixResult<String> = getString(CONTENT_MODERATION_VERSION)

    /**
     * Gets refund & subscription policy version from Remote Config
     */
    suspend fun getRefundPolicyVersion(): LiftrixResult<String> = getString(REFUND_POLICY_VERSION)

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

    // AI Chat configuration methods

    /**
     * Checks if AI chat is enabled
     */
    suspend fun isAiChatEnabled(): LiftrixResult<Boolean> = getBoolean(AI_CHAT_ENABLED)

    /**
     * Gets daily message limit for AI chat
     */
    suspend fun getAiDailyMessageLimit(): LiftrixResult<Long> = getLong(AI_DAILY_MESSAGE_LIMIT)

    /**
     * Gets monthly token limit for AI chat
     */
    suspend fun getAiMonthlyTokenLimit(): LiftrixResult<Long> = getLong(AI_MONTHLY_TOKEN_LIMIT)

    /**
     * Gets cost threshold per hour for AI chat
     */
    suspend fun getAiCostThresholdPerHour(): LiftrixResult<Double> = getDouble(AI_COST_THRESHOLD_PER_HOUR)

    /**
     * Gets jailbreak detection threshold
     */
    suspend fun getAiJailbreakThreshold(): LiftrixResult<Double> = getDouble(AI_JAILBREAK_THRESHOLD)

    /**
     * Gets fitness context weight for abuse detection
     */
    suspend fun getAiFitnessContextWeight(): LiftrixResult<Double> = getDouble(AI_FITNESS_CONTEXT_WEIGHT)

    /**
     * Gets rate limit multiplier for anomaly detection
     */
    suspend fun getAiRateLimitMultiplier(): LiftrixResult<Long> = getLong(AI_RATE_LIMIT_MULTIPLIER)

    /**
     * Checks if abuse logging is enabled
     */
    suspend fun isAiAbuseLoggingEnabled(): LiftrixResult<Boolean> = getBoolean(AI_ENABLE_ABUSE_LOGGING)

    /**
     * Checks if review queue is enabled
     */
    suspend fun isAiReviewQueueEnabled(): LiftrixResult<Boolean> = getBoolean(AI_REVIEW_QUEUE_ENABLED)

    /**
     * Gets AI model name
     */
    suspend fun getAiModelName(): LiftrixResult<String> = getString(AI_MODEL_NAME)

    /**
     * Gets maximum output tokens for AI responses
     */
    suspend fun getAiMaxOutputTokens(): LiftrixResult<Long> = getLong(AI_MAX_OUTPUT_TOKENS)

    /**
     * Gets AI temperature setting
     */
    suspend fun getAiTemperature(): LiftrixResult<Double> = getDouble(AI_TEMPERATURE)

    /**
     * Gets AI top-k setting
     */
    suspend fun getAiTopK(): LiftrixResult<Long> = getLong(AI_TOP_K)

    /**
     * Gets AI top-p setting
     */
    suspend fun getAiTopP(): LiftrixResult<Double> = getDouble(AI_TOP_P)

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
