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
                val documentJson = when (configKey) {
                    PRIVACY_POLICY_CONFIG_KEY -> remoteConfigManager.getPrivacyPolicyUrl().getOrElse { "" }
                    TERMS_CONFIG_KEY -> remoteConfigManager.getTermsOfServiceUrl().getOrElse { "" }
                    else -> remoteConfigManager.getString(configKey).getOrElse { "" }
                }
                
                if (documentJson.isNotBlank()) {
                    val document = json.decodeFromString<LegalDocument>(documentJson)
                    
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