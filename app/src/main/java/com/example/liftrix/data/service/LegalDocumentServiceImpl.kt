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
• Email: privacy@liftrix.com"""
        
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
            
            // Optional documents
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
                
                // Optional documents (don't fail if they're not available)
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
                
                // For now, we'll use default content since remote config returns URLs not JSON
                // TODO: Implement URL fetching and content parsing when backend is ready
                val document = when (type) {
                    LegalDocumentType.PRIVACY_POLICY -> {
                        LegalDocument(
                            type = type,
                            title = "Privacy Policy",
                            content = DEFAULT_PRIVACY_POLICY,
                            version = "1.0",
                            effectiveDate = Instant.now(),
                            lastModified = Instant.now()
                        )
                    }
                    LegalDocumentType.TERMS_OF_SERVICE -> {
                        LegalDocument(
                            type = type,
                            title = "Terms of Service",
                            content = DEFAULT_TERMS_OF_SERVICE,
                            version = "1.0",
                            effectiveDate = Instant.now(),
                            lastModified = Instant.now()
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
}