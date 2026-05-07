package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.AppConfigDao
import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.DocumentAcceptance
import com.example.liftrix.domain.service.LegalDocument
import com.example.liftrix.domain.service.LegalDocumentService
import com.example.liftrix.domain.service.LegalDocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LegalDocumentService providing legal document management
 * 
 * Features:
 * - Remote document fetching with local caching
 * - Version tracking and update detection
 * - User acceptance recording and validation
 * - Offline-first with graceful degradation
 */
@Singleton
class LegalDocumentServiceImpl @Inject constructor(
    private val appConfigDao: AppConfigDao,
    private val remoteConfigManager: RemoteConfigManager
) : LegalDocumentService {
    
    companion object {
        private const val PRIVACY_POLICY_CONFIG_KEY = "privacy_policy"
        private const val TERMS_CONFIG_KEY = "terms_of_service"
        private const val AI_DISCLAIMER_CONFIG_KEY = "ai_disclaimer"
        private const val COMMUNITY_GUIDELINES_CONFIG_KEY = "community_guidelines"
        private const val CONTENT_MODERATION_POLICY_CONFIG_KEY = "content_moderation_policy"
        private const val REFUND_SUBSCRIPTION_POLICY_CONFIG_KEY = "refund_subscription_policy"
        private const val EULA_CONFIG_KEY = "eula"
        private const val DPA_CONFIG_KEY = "data_processing_agreement"
        private const val DOCUMENT_VERSIONS_KEY = "legal_document_versions"
        private const val CACHE_TTL_HOURS = 24L
        
        // Default fallback content for legal documents
        private const val DEFAULT_PRIVACY_POLICY = """Privacy Policy for Liftrix

Last Updated: January 2025

1. Information We Collect
We collect the following types of information:
• Account information (email, username, profile data)
• Workout data (exercises, sets, reps, weights)
• Usage analytics (app interactions, feature usage)
• Device information (for sync and notifications)

2. How We Use Your Information
Your data is used to:
• Provide personalized workout tracking and analytics
• Sync your data across devices
• Send workout reminders and achievement notifications
• Improve app features and user experience
• Generate progress reports and insights

3. Data Security & Storage
• All data is encrypted in transit and at rest
• Workout data is stored locally and synced to secure cloud servers
• We use industry-standard security measures
• Regular security audits are performed

4. Data Sharing
We do NOT:
• Sell your personal data to third parties
• Share your workout data without consent
• Use your data for advertising

We MAY share data:
• With your explicit consent (e.g., social features)
• To comply with legal obligations
• In anonymized form for research

5. Your Rights & Controls
You have the right to:
• Access all your personal data
• Export your workout history
• Delete your account and all associated data
• Opt-out of analytics and notifications
• Control privacy settings for social features

6. Social Features & Privacy
• Profile visibility settings (Public/Followers/Private)
• Gym buddy connections require mutual consent
• You control what workout data is shared
• Block and report features available

7. Contact Us
For privacy concerns or data requests:
• In-app: Settings > Help & Support
• Email: valijianu98@gmail.com"""

        private const val DEFAULT_AI_DISCLAIMER = """AI Disclaimer for Liftrix

Last Updated: January 2025

1. General Information Only
The AI assistant provides general fitness information and is not medical advice.

2. Not a Substitute for Professionals
Always consult qualified health or fitness professionals before starting a program.

3. No Guarantees
We do not guarantee results, accuracy, or suitability for your specific needs.

4. User Responsibility
You are responsible for your decisions, actions, and safety when using AI guidance.

5. Contact
For questions about AI guidance: support@liftrix.app"""
        
        private const val DEFAULT_TERMS_OF_SERVICE = """Terms of Service for Liftrix

Last Updated: January 2025

1. Acceptance of Terms
By creating an account or using Liftrix, you agree to these Terms of Service and our Privacy Policy. If you disagree with any part, please do not use our service.

2. Account Registration
• You must be 13 years or older to use Liftrix
• You must provide accurate account information
• You are responsible for your account security
• One person per account (no sharing)
• You must notify us of any unauthorized access

3. Acceptable Use
You agree to:
• Use Liftrix for personal fitness tracking only
• Not interfere with the service operation
• Not attempt to access other users' data
• Not use automated systems or bots
• Respect other users in social features
• Not post inappropriate content

4. Your Content & Data
• You own your workout data and content
• You grant us license to process and display your data for service operation
• You can export your data at any time
• You can delete your account and data
• We backup data but are not liable for data loss

5. Subscription & Payments
• Free tier includes core features
• Premium features require subscription
• Subscriptions auto-renew unless cancelled
• Refunds per platform policy (App Store/Google Play)
• Prices may change with notice

6. Intellectual Property
• Liftrix name, logo, and design are our property
• Exercise database is licensed content
• You may not copy or redistribute app content
• User-generated content remains yours

7. Service Availability
• We strive for 99.9% uptime but don't guarantee it
• Maintenance windows will be announced
• Features may be added or removed
• Service may be discontinued with 30 days notice

8. Limitation of Liability
• Liftrix is not medical advice
• Consult professionals before starting fitness programs
• We are not liable for injuries from exercises
• Maximum liability limited to subscription fees paid

9. Termination
We may terminate accounts that:
• Violate these terms
• Are inactive for 12+ months
• Engage in fraudulent activity
• Abuse social features

10. Changes to Terms
• We'll notify you of material changes
• Continued use constitutes acceptance
• Review terms periodically

11. Governing Law
These terms are governed by the laws of [Your Jurisdiction].

12. Contact
• In-app: Settings > Help & Support
• Email: legal@liftrix.com
• Response within 48 hours"""

        private const val DEFAULT_COMMUNITY_GUIDELINES = """Community Guidelines for Liftrix

Last Updated: January 2025

1. Be Respectful
Treat others with respect in all social interactions.

2. Safe Content
Do not post harmful, abusive, or explicit content.

3. Accurate Information
Avoid sharing misleading health or fitness claims.

4. Reporting
Report content that violates guidelines.

5. Contact
Questions: support@liftrix.app"""

        private const val DEFAULT_CONTENT_MODERATION_POLICY = """Content Moderation Policy for Liftrix

Last Updated: January 2025

1. Enforcement
We moderate content to keep the community safe.

2. Prohibited Content
Harassment, hate, explicit content, and illegal activity are not allowed.

3. Reporting
Users can report content for review.

4. Actions
We may remove content or restrict accounts for violations.

5. Contact
Moderation questions: support@liftrix.app"""

        private const val DEFAULT_REFUND_SUBSCRIPTION_POLICY = """Refund & Subscription Policy for Liftrix

Last Updated: January 2025

1. Subscriptions
Subscriptions are managed through the app store.

2. Cancellations
Cancel anytime through your app store settings.

3. Refunds
Refunds are subject to the app store policy.

4. Contact
Billing questions: support@liftrix.app"""
        
        private fun getDocumentCacheKey(type: LegalDocumentType): String = 
            "legal_document_${type.fileName}_cache"
        
        private fun getDocumentAcceptanceKey(userId: String): String = 
            "user_document_acceptances_$userId"
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    override suspend fun getPrivacyPolicy(forceRefresh: Boolean): LiftrixResult<LegalDocument> = 
        getDocument(LegalDocumentType.PRIVACY_POLICY, PRIVACY_POLICY_CONFIG_KEY, forceRefresh)
    
    override suspend fun getTermsOfService(forceRefresh: Boolean): LiftrixResult<LegalDocument> = 
        getDocument(LegalDocumentType.TERMS_OF_SERVICE, TERMS_CONFIG_KEY, forceRefresh)

    override suspend fun getAIDisclaimer(forceRefresh: Boolean): LiftrixResult<LegalDocument> =
        getDocument(LegalDocumentType.AI_DISCLAIMER, AI_DISCLAIMER_CONFIG_KEY, forceRefresh)

    override suspend fun getCommunityGuidelines(forceRefresh: Boolean): LiftrixResult<LegalDocument> =
        getDocument(LegalDocumentType.COMMUNITY_GUIDELINES, COMMUNITY_GUIDELINES_CONFIG_KEY, forceRefresh)

    override suspend fun getContentModerationPolicy(forceRefresh: Boolean): LiftrixResult<LegalDocument> =
        getDocument(LegalDocumentType.CONTENT_MODERATION_POLICY, CONTENT_MODERATION_POLICY_CONFIG_KEY, forceRefresh)

    override suspend fun getRefundSubscriptionPolicy(forceRefresh: Boolean): LiftrixResult<LegalDocument> =
        getDocument(LegalDocumentType.REFUND_SUBSCRIPTION_POLICY, REFUND_SUBSCRIPTION_POLICY_CONFIG_KEY, forceRefresh)
    
    override suspend fun getEULA(forceRefresh: Boolean): LiftrixResult<LegalDocument?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "EULA_FETCH_FAILED",
                errorMessage = "Failed to fetch EULA",
                analyticsContext = mapOf(
                    "operation" to "GET_EULA",
                    "force_refresh" to forceRefresh.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        try {
            getDocument(LegalDocumentType.EULA, EULA_CONFIG_KEY, forceRefresh).getOrThrow()
        } catch (e: Exception) {
            // EULA is optional, return null if not available
            null
        }
    }
    
    override suspend fun getDataProcessingAgreement(forceRefresh: Boolean): LiftrixResult<LegalDocument?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DPA_FETCH_FAILED",
                errorMessage = "Failed to fetch Data Processing Agreement",
                analyticsContext = mapOf(
                    "operation" to "GET_DPA",
                    "force_refresh" to forceRefresh.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        try {
            getDocument(LegalDocumentType.DATA_PROCESSING_AGREEMENT, DPA_CONFIG_KEY, forceRefresh).getOrThrow()
        } catch (e: Exception) {
            // DPA is optional, return null if not available
            null
        }
    }
    
    override suspend fun getAllLegalDocuments(forceRefresh: Boolean): LiftrixResult<List<LegalDocument>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ALL_DOCUMENTS_FETCH_FAILED",
                errorMessage = "Failed to fetch all legal documents",
                analyticsContext = mapOf(
                    "operation" to "GET_ALL_DOCUMENTS",
                    "force_refresh" to forceRefresh.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val documents = mutableListOf<LegalDocument>()
            
            // Required documents
            try {
                documents.add(getPrivacyPolicy(forceRefresh).getOrThrow())
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch privacy policy")
            }
            
            try {
                documents.add(getTermsOfService(forceRefresh).getOrThrow())
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch terms of service")
            }

            try {
                documents.add(getAIDisclaimer(forceRefresh).getOrThrow())
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch AI disclaimer")
            }
            
            // Optional documents
            try {
                documents.add(getCommunityGuidelines(forceRefresh).getOrThrow())
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch community guidelines")
            }

            try {
                documents.add(getContentModerationPolicy(forceRefresh).getOrThrow())
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch content moderation policy")
            }

            try {
                documents.add(getRefundSubscriptionPolicy(forceRefresh).getOrThrow())
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch refund & subscription policy")
            }

            getEULA(forceRefresh).getOrNull()?.let { documents.add(it) }
            getDataProcessingAgreement(forceRefresh).getOrNull()?.let { documents.add(it) }
            
            documents
        }
    }
    
    override suspend fun hasDocumentUpdates(documentType: LegalDocumentType): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to check for document updates",
                analyticsContext = mapOf(
                    "operation" to "CHECK_DOCUMENT_UPDATES",
                    "document_type" to documentType.name,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Fetch latest versions from remote config
                remoteConfigManager.initialize().getOrThrow()
                remoteConfigManager.fetchAndActivate().getOrThrow()
                val remoteVersionsJson = remoteConfigManager.getString(DOCUMENT_VERSIONS_KEY).getOrElse { "" }
                
                if (remoteVersionsJson.isNotBlank()) {
                    val remoteVersions = json.decodeFromString<Map<String, String>>(remoteVersionsJson)
                    val remoteVersion = remoteVersions[documentType.fileName]
                    
                    if (remoteVersion != null) {
                        val cachedDocument = getCachedDocument(documentType)
                        return@withContext cachedDocument?.version != remoteVersion
                    }
                }
                
                false
            } catch (e: Exception) {
                Timber.e(e, "Failed to check document updates for ${documentType.name}")
                false
            }
        }
    }
    
    override suspend fun recordDocumentAcceptance(
        userId: String,
        documentType: LegalDocumentType,
        version: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DOCUMENT_ACCEPTANCE_RECORD_FAILED",
                errorMessage = "Failed to record document acceptance",
                analyticsContext = mapOf(
                    "operation" to "RECORD_ACCEPTANCE",
                    "user_id" to userId,
                    "document_type" to documentType.name,
                    "version" to version,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val acceptance = DocumentAcceptance(
                userId = userId,
                documentType = documentType,
                version = version,
                acceptedAt = Instant.now()
            )
            
            // Get existing acceptances
            val existingAcceptances = getUserDocumentAcceptances(userId).getOrElse { emptyList() }
            
            // Remove any existing acceptance for this document type and add new one
            val updatedAcceptances = existingAcceptances
                .filter { it.documentType != documentType } + acceptance
            
            // Save updated acceptances
            val acceptancesJson = json.encodeToString(updatedAcceptances)
            appConfigDao.setStringConfig(getDocumentAcceptanceKey(userId), acceptancesJson)
            
            Timber.d("Recorded acceptance of ${documentType.name} v$version for user $userId")
        }
    }
    
    override suspend fun getUserDocumentAcceptances(userId: String): LiftrixResult<List<DocumentAcceptance>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "USER_ACCEPTANCES_FETCH_FAILED",
                errorMessage = "Failed to fetch user document acceptances",
                analyticsContext = mapOf(
                    "operation" to "GET_USER_ACCEPTANCES",
                    "user_id" to userId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val acceptancesJson = appConfigDao.getConfigValue(getDocumentAcceptanceKey(userId))
            
            if (acceptancesJson.isNullOrBlank()) {
                return@withContext emptyList()
            }
            
            try {
                json.decodeFromString<List<DocumentAcceptance>>(acceptancesJson)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse document acceptances for user $userId")
                emptyList()
            }
        }
    }
    
    override suspend fun hasUserAcceptedCurrentVersion(
        userId: String,
        documentType: LegalDocumentType
    ): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ACCEPTANCE_CHECK_FAILED",
                errorMessage = "Failed to check user document acceptance",
                analyticsContext = mapOf(
                    "operation" to "CHECK_ACCEPTANCE",
                    "user_id" to userId,
                    "document_type" to documentType.name,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val acceptances = getUserDocumentAcceptances(userId).getOrElse { emptyList() }
            val currentDocument = when (documentType) {
                LegalDocumentType.PRIVACY_POLICY -> getPrivacyPolicy().getOrNull()
                LegalDocumentType.TERMS_OF_SERVICE -> getTermsOfService().getOrNull()
                LegalDocumentType.AI_DISCLAIMER -> getAIDisclaimer().getOrNull()
                LegalDocumentType.COMMUNITY_GUIDELINES -> getCommunityGuidelines().getOrNull()
                LegalDocumentType.CONTENT_MODERATION_POLICY -> getContentModerationPolicy().getOrNull()
                LegalDocumentType.REFUND_SUBSCRIPTION_POLICY -> getRefundSubscriptionPolicy().getOrNull()
                LegalDocumentType.EULA -> getEULA().getOrNull()
                LegalDocumentType.DATA_PROCESSING_AGREEMENT -> getDataProcessingAgreement().getOrNull()
                else -> null
            }
            
            if (currentDocument == null) {
                return@withContext false
            }
            
            val acceptance = acceptances.find { it.documentType == documentType }
            acceptance?.isValidForVersion(currentDocument.version) ?: false
        }
    }
    
    override suspend fun refreshAllDocuments(): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to refresh legal documents",
                analyticsContext = mapOf(
                    "operation" to "REFRESH_ALL_DOCUMENTS",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Force refresh all documents
                getPrivacyPolicy(forceRefresh = true).getOrThrow()
                getTermsOfService(forceRefresh = true).getOrThrow()
                getAIDisclaimer(forceRefresh = true).getOrThrow()
                
                // Optional documents (don't fail if they're not available)
                try { getCommunityGuidelines(forceRefresh = true) } catch (e: Exception) { /* ignored */ }
                try { getContentModerationPolicy(forceRefresh = true) } catch (e: Exception) { /* ignored */ }
                try { getRefundSubscriptionPolicy(forceRefresh = true) } catch (e: Exception) { /* ignored */ }
                try { getEULA(forceRefresh = true) } catch (e: Exception) { /* ignored */ }
                try { getDataProcessingAgreement(forceRefresh = true) } catch (e: Exception) { /* ignored */ }
                
                Timber.d("Successfully refreshed all legal documents")
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh legal documents")
                throw e
            }
        }
    }
    
    /**
     * Generic method to fetch a legal document with caching
     */
    private suspend fun getDocument(
        type: LegalDocumentType,
        configKey: String,
        forceRefresh: Boolean
    ): LiftrixResult<LegalDocument> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DOCUMENT_FETCH_FAILED",
                errorMessage = "Failed to fetch ${type.displayName}",
                analyticsContext = mapOf(
                    "operation" to "GET_DOCUMENT",
                    "document_type" to type.name,
                    "config_key" to configKey,
                    "force_refresh" to forceRefresh.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Check cache first unless forcing refresh
            if (!forceRefresh) {
                val cachedDocument = getCachedDocument(type)
                if (cachedDocument != null && !isCacheExpired(type)) {
                    return@withContext cachedDocument
                }
            }
            
            // Fetch from remote config
            try {
                remoteConfigManager.initialize().getOrThrow()
                remoteConfigManager.fetchAndActivate(forceRefresh).getOrThrow()
                
                // Fetch document from URL if available, otherwise use default content
                val document = when (type) {
                    LegalDocumentType.PRIVACY_POLICY -> {
                        fetchDocumentFromRemoteUrl(
                            type = type,
                            title = "Privacy Policy",
                            urlFetcher = { remoteConfigManager.getPrivacyPolicyUrl() },
                            versionFetcher = { remoteConfigManager.getPrivacyPolicyVersion() },
                            fallbackContent = DEFAULT_PRIVACY_POLICY
                        )
                    }
                    LegalDocumentType.TERMS_OF_SERVICE -> {
                        fetchDocumentFromRemoteUrl(
                            type = type,
                            title = "Terms of Service",
                            urlFetcher = { remoteConfigManager.getTermsOfServiceUrl() },
                            versionFetcher = { remoteConfigManager.getTermsVersion() },
                            fallbackContent = DEFAULT_TERMS_OF_SERVICE
                        )
                    }
                    LegalDocumentType.AI_DISCLAIMER -> {
                        fetchDocumentFromRemoteUrl(
                            type = type,
                            title = "AI Disclaimer",
                            urlFetcher = { remoteConfigManager.getAIDisclaimerUrl() },
                            versionFetcher = { remoteConfigManager.getAIDisclaimerVersion() },
                            fallbackContent = DEFAULT_AI_DISCLAIMER
                        )
                    }
                    LegalDocumentType.COMMUNITY_GUIDELINES -> {
                        fetchDocumentFromRemoteUrl(
                            type = type,
                            title = "Community Guidelines",
                            urlFetcher = { remoteConfigManager.getCommunityGuidelinesUrl() },
                            versionFetcher = { remoteConfigManager.getCommunityGuidelinesVersion() },
                            fallbackContent = DEFAULT_COMMUNITY_GUIDELINES
                        )
                    }
                    LegalDocumentType.CONTENT_MODERATION_POLICY -> {
                        fetchDocumentFromRemoteUrl(
                            type = type,
                            title = "Content Moderation Policy",
                            urlFetcher = { remoteConfigManager.getContentModerationPolicyUrl() },
                            versionFetcher = { remoteConfigManager.getContentModerationPolicyVersion() },
                            fallbackContent = DEFAULT_CONTENT_MODERATION_POLICY
                        )
                    }
                    LegalDocumentType.REFUND_SUBSCRIPTION_POLICY -> {
                        fetchDocumentFromRemoteUrl(
                            type = type,
                            title = "Refund & Subscription Policy",
                            urlFetcher = { remoteConfigManager.getRefundSubscriptionPolicyUrl() },
                            versionFetcher = { remoteConfigManager.getRefundPolicyVersion() },
                            fallbackContent = DEFAULT_REFUND_SUBSCRIPTION_POLICY
                        )
                    }
                    else -> {
                        // For EULA and DPA, check if remote config has content
                        val documentJson = remoteConfigManager.getString(configKey).getOrElse { "" }
                        if (documentJson.isNotBlank()) {
                            try {
                                json.decodeFromString<LegalDocument>(documentJson)
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to decode document JSON for ${type.name}")
                                null
                            }
                        } else {
                            null
                        }
                    }
                }
                
                if (document != null) {
                    // Cache the document
                    cacheDocument(type, document)
                    return@withContext document
                }
                
                // Fallback to cached version if available
                val cachedDocument = getCachedDocument(type)
                if (cachedDocument != null) {
                    Timber.w("Using cached ${type.displayName} as remote fetch failed")
                    return@withContext cachedDocument
                }
                
                throw LiftrixError.BusinessLogicError(
                    code = "DOCUMENT_NOT_AVAILABLE",
                    errorMessage = "${type.displayName} is not available",
                    analyticsContext = mapOf(
                        "document_type" to type.name,
                        "config_key" to configKey
                    )
                )
                
            } catch (e: Exception) {
                // Try to return cached version on error
                val cachedDocument = getCachedDocument(type)
                if (cachedDocument != null) {
                    Timber.w(e, "Using cached ${type.displayName} due to fetch error")
                    return@withContext cachedDocument
                }
                
                throw e
            }
        }
    }
    
    /**
     * Retrieves a cached legal document
     */
    private suspend fun getCachedDocument(type: LegalDocumentType): LegalDocument? {
        return try {
            val cachedJson = appConfigDao.getConfigValue(getDocumentCacheKey(type))
            if (cachedJson.isNullOrBlank()) {
                return null
            }
            
            json.decodeFromString<LegalDocument>(cachedJson)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse cached document for ${type.name}")
            null
        }
    }
    
    /**
     * Caches a legal document
     */
    private suspend fun cacheDocument(type: LegalDocumentType, document: LegalDocument) {
        try {
            val documentJson = json.encodeToString(document)
            appConfigDao.setStringConfig(getDocumentCacheKey(type), documentJson)
            
            // Update cache timestamp
            appConfigDao.setStringConfig(
                "${getDocumentCacheKey(type)}_timestamp",
                Instant.now().epochSecond.toString()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache document for ${type.name}")
        }
    }
    
    /**
     * Checks if the cached document is expired
     */
    private suspend fun isCacheExpired(type: LegalDocumentType): Boolean {
        return try {
            val timestampStr = appConfigDao.getConfigValue("${getDocumentCacheKey(type)}_timestamp")
            if (timestampStr == null) {
                return true
            }
            
            val cachedTime = Instant.ofEpochSecond(timestampStr.toLong())
            val expiryTime = cachedTime.plusSeconds(CACHE_TTL_HOURS * 3600)
            
            Instant.now().isAfter(expiryTime)
        } catch (e: Exception) {
            true // Consider expired if we can't determine
        }
    }
    
    /**
     * Fetches a legal document from remote URL with fallback to default content
     */
    private suspend fun fetchDocumentFromRemoteUrl(
        type: LegalDocumentType,
        title: String,
        urlFetcher: suspend () -> LiftrixResult<String>,
        versionFetcher: suspend () -> LiftrixResult<String>,
        fallbackContent: String
    ): LegalDocument = withContext(Dispatchers.IO) {
        try {
            // Get URL from Remote Config
            val urlResult = urlFetcher()
            val url = urlResult.getOrNull()
            
            if (url.isNullOrBlank()) {
                Timber.w("No URL configured for ${type.name}, using fallback content")
                return@withContext createFallbackDocument(type, title, fallbackContent)
            }
            
            Timber.d("Fetching ${type.name} from URL: $url")
            
            // Fetch content from URL
            val content = fetchContentFromUrl(url)
            
            // Get version from Remote Config
            val version = versionFetcher().getOrElse { "1.0" }
            
            LegalDocument(
                type = type,
                title = title,
                content = content,
                version = version,
                effectiveDate = Instant.now(),
                lastModified = Instant.now()
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch ${type.name} from remote URL, using fallback")
            createFallbackDocument(type, title, fallbackContent)
        }
    }
    
    /**
     * Fetches content from a URL using HttpURLConnection
     */
    private suspend fun fetchContentFromUrl(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000 // 10 seconds
            connection.readTimeout = 30000 // 30 seconds
            connection.setRequestProperty("User-Agent", "LiftrixApp/1.0")
            connection.setRequestProperty("Accept", "text/html,text/plain,application/json")
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP error code: $responseCode")
            }
            
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            
            if (content.isBlank()) {
                throw IOException("Fetched content is empty")
            }
            
            // Basic content validation and cleaning
            val cleanedContent = content.trim()
                .replace(Regex("\\s+"), " ") // Normalize whitespace
                .replace("\\n", "\n") // Fix line breaks
            
            Timber.d("Successfully fetched ${cleanedContent.length} characters from URL")
            return@withContext cleanedContent
            
        } catch (e: IOException) {
            Timber.e(e, "Failed to fetch content from URL: $url")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error fetching content from URL: $url")
            throw IOException("Failed to fetch content: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Creates a fallback document with default content
     */
    private fun createFallbackDocument(
        type: LegalDocumentType,
        title: String,
        fallbackContent: String
    ): LegalDocument {
        return LegalDocument(
            type = type,
            title = title,
            content = fallbackContent,
            version = "1.0-fallback",
            effectiveDate = Instant.now(),
            lastModified = Instant.now()
        )
    }
}
