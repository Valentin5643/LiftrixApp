package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import java.time.Instant

/**
 * Service interface for legal document management
 * Provides access to privacy policy, terms of service, and other legal documents
 */
interface LegalDocumentService {
    
    /**
     * Retrieves the current privacy policy document
     * @param forceRefresh Whether to force refresh from remote source
     * @return LiftrixResult containing the privacy policy document
     */
    suspend fun getPrivacyPolicy(forceRefresh: Boolean = false): LiftrixResult<LegalDocument>
    
    /**
     * Retrieves the current terms of service document
     * @param forceRefresh Whether to force refresh from remote source
     * @return LiftrixResult containing the terms of service document
     */
    suspend fun getTermsOfService(forceRefresh: Boolean = false): LiftrixResult<LegalDocument>
    
    /**
     * Retrieves the EULA (End User License Agreement) if applicable
     * @param forceRefresh Whether to force refresh from remote source
     * @return LiftrixResult containing the EULA document
     */
    suspend fun getEULA(forceRefresh: Boolean = false): LiftrixResult<LegalDocument?>
    
    /**
     * Retrieves the data processing agreement for enterprise users
     * @param forceRefresh Whether to force refresh from remote source
     * @return LiftrixResult containing the DPA document
     */
    suspend fun getDataProcessingAgreement(forceRefresh: Boolean = false): LiftrixResult<LegalDocument?>
    
    /**
     * Gets all available legal documents
     * @param forceRefresh Whether to force refresh from remote sources
     * @return LiftrixResult containing list of all legal documents
     */
    suspend fun getAllLegalDocuments(forceRefresh: Boolean = false): LiftrixResult<List<LegalDocument>>
    
    /**
     * Checks if a legal document has been updated since last cache
     * @param documentType Type of document to check
     * @return LiftrixResult indicating if document has updates
     */
    suspend fun hasDocumentUpdates(documentType: LegalDocumentType): LiftrixResult<Boolean>
    
    /**
     * Records user acceptance of a legal document
     * @param userId User identifier
     * @param documentType Type of document accepted
     * @param version Version of document accepted
     * @return LiftrixResult indicating success or failure
     */
    suspend fun recordDocumentAcceptance(
        userId: String, 
        documentType: LegalDocumentType, 
        version: String
    ): LiftrixResult<Unit>
    
    /**
     * Gets user's document acceptance history
     * @param userId User identifier
     * @return LiftrixResult containing list of document acceptances
     */
    suspend fun getUserDocumentAcceptances(userId: String): LiftrixResult<List<DocumentAcceptance>>
    
    /**
     * Checks if user has accepted the current version of a document
     * @param userId User identifier
     * @param documentType Type of document to check
     * @return LiftrixResult indicating if current version is accepted
     */
    suspend fun hasUserAcceptedCurrentVersion(
        userId: String, 
        documentType: LegalDocumentType
    ): LiftrixResult<Boolean>
    
    /**
     * Refreshes all legal documents from remote sources
     * @return LiftrixResult indicating success or failure of refresh operation
     */
    suspend fun refreshAllDocuments(): LiftrixResult<Unit>
}

/**
 * Legal document data model
 */
data class LegalDocument(
    val type: LegalDocumentType,
    val title: String,
    val content: String,
    val version: String,
    val effectiveDate: Instant,
    val lastModified: Instant,
    val url: String? = null,
    val language: String = "en",
    val isMarkdown: Boolean = false,
    val requiresAcceptance: Boolean = true
) {
    /**
     * Gets a preview of the document content
     * @param maxLength Maximum length of the preview
     * @return Truncated content with ellipsis if needed
     */
    fun getContentPreview(maxLength: Int = 200): String {
        return if (content.length <= maxLength) {
            content
        } else {
            content.take(maxLength).trimEnd() + "..."
        }
    }
    
    /**
     * Checks if this document is newer than another version
     * @param otherVersion Version string to compare against
     * @return True if this document is newer
     */
    fun isNewerThan(otherVersion: String): Boolean {
        return try {
            val thisVersionParts = version.split(".").map { it.toInt() }
            val otherVersionParts = otherVersion.split(".").map { it.toInt() }
            
            for (i in 0 until maxOf(thisVersionParts.size, otherVersionParts.size)) {
                val thisVersion = thisVersionParts.getOrNull(i) ?: 0
                val otherVersionPart = otherVersionParts.getOrNull(i) ?: 0
                
                when {
                    thisVersion > otherVersionPart -> return true
                    thisVersion < otherVersionPart -> return false
                }
            }
            false
        } catch (e: Exception) {
            // Fallback to string comparison
            version > otherVersion
        }
    }
}

/**
 * Types of legal documents
 */
enum class LegalDocumentType(val displayName: String, val fileName: String) {
    PRIVACY_POLICY("Privacy Policy", "privacy_policy"),
    TERMS_OF_SERVICE("Terms of Service", "terms_of_service"),
    EULA("End User License Agreement", "eula"),
    DATA_PROCESSING_AGREEMENT("Data Processing Agreement", "data_processing_agreement"),
    COOKIE_POLICY("Cookie Policy", "cookie_policy"),
    ACCESSIBILITY_STATEMENT("Accessibility Statement", "accessibility_statement");
    
    companion object {
        /**
         * Gets documents that require user acceptance
         */
        fun getRequiredDocuments(): List<LegalDocumentType> = listOf(
            PRIVACY_POLICY,
            TERMS_OF_SERVICE
        )
        
        /**
         * Gets optional documents for information purposes
         */
        fun getOptionalDocuments(): List<LegalDocumentType> = listOf(
            EULA,
            DATA_PROCESSING_AGREEMENT,
            COOKIE_POLICY,
            ACCESSIBILITY_STATEMENT
        )
    }
}

/**
 * Record of user acceptance of a legal document
 */
data class DocumentAcceptance(
    val userId: String,
    val documentType: LegalDocumentType,
    val version: String,
    val acceptedAt: Instant,
    val ipAddress: String? = null,
    val userAgent: String? = null
) {
    /**
     * Checks if this acceptance is still valid for a given document version
     * @param currentVersion Current version of the document
     * @return True if acceptance is still valid
     */
    fun isValidForVersion(currentVersion: String): Boolean = version == currentVersion
    
    /**
     * Gets the age of this acceptance in days
     */
    fun getAgeInDays(): Long {
        return java.time.Duration.between(acceptedAt, Instant.now()).toDays()
    }
}