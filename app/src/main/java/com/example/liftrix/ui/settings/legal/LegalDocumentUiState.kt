package com.example.liftrix.ui.settings.legal

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * UI state for legal document screens (Privacy Policy, Terms of Service)
 */
sealed class LegalDocumentUiState {
    
    /**
     * Data class containing all legal document state
     */
    data class Data(
        val privacyPolicy: String? = null,
        val termsOfService: String? = null,
        val aiDisclaimer: String? = null,
        val communityGuidelines: String? = null,
        val contentModerationPolicy: String? = null,
        val refundSubscriptionPolicy: String? = null,
        val privacyPolicyLastUpdated: String? = null,
        val termsOfServiceLastUpdated: String? = null,
        val aiDisclaimerLastUpdated: String? = null,
        val communityGuidelinesLastUpdated: String? = null,
        val contentModerationPolicyLastUpdated: String? = null,
        val refundSubscriptionPolicyLastUpdated: String? = null,
        val isRefreshing: Boolean = false,
        val downloadInProgress: Boolean = false,
        val downloadProgress: Float = 0f
    ) {
        /**
         * Checks if any documents are available
         */
        val hasAnyDocument: Boolean
            get() = privacyPolicy != null || termsOfService != null || aiDisclaimer != null ||
                    communityGuidelines != null || contentModerationPolicy != null || refundSubscriptionPolicy != null

        /**
         * Checks if privacy policy is available
         */
        val hasPrivacyPolicy: Boolean
            get() = !privacyPolicy.isNullOrBlank()

        /**
         * Checks if terms of service is available
         */
        val hasTermsOfService: Boolean
            get() = !termsOfService.isNullOrBlank()

        /**
         * Checks if AI disclaimer is available
         */
        val hasAIDisclaimer: Boolean
            get() = !aiDisclaimer.isNullOrBlank()

        /**
         * Checks if community guidelines are available
         */
        val hasCommunityGuidelines: Boolean
            get() = !communityGuidelines.isNullOrBlank()

        /**
         * Checks if content moderation policy is available
         */
        val hasContentModerationPolicy: Boolean
            get() = !contentModerationPolicy.isNullOrBlank()

        /**
         * Checks if refund & subscription policy is available
         */
        val hasRefundSubscriptionPolicy: Boolean
            get() = !refundSubscriptionPolicy.isNullOrBlank()
    }
    
    /**
     * Loading state while fetching legal documents
     */
    data object Loading : LegalDocumentUiState()
    
    /**
     * Success state with legal document data
     */
    data class Success(
        val data: Data,
        val isRefreshing: Boolean = false
    ) : LegalDocumentUiState()
    
    /**
     * Error state with failure information
     */
    data class Error(
        val error: LiftrixError,
        val previousData: Data? = null
    ) : LegalDocumentUiState()
    
    /**
     * Empty state when no legal documents are available
     */
    data class Empty(
        val message: String = "No legal documents available",
        val actionText: String? = "Reload",
        val showAction: Boolean = true
    ) : LegalDocumentUiState()
}

/**
 * Events that can be triggered from the legal document UI
 */
sealed class LegalDocumentEvent : ViewModelEvent {
    /**
     * Load privacy policy document
     */
    data object LoadPrivacyPolicy : LegalDocumentEvent()
    
    /**
     * Load terms of service document
     */
    data object LoadTermsOfService : LegalDocumentEvent()
    
    /**
     * Load all legal documents
     */
    data object LoadAllDocuments : LegalDocumentEvent()
    
    /**
     * Refresh document content from remote sources
     */
    data object RefreshContent : LegalDocumentEvent()
    
    /**
     * Download document as PDF
     */
    data class DownloadAsPdf(val documentType: String) : LegalDocumentEvent()
    
    /**
     * Search within document content
     */
    data class SearchDocument(val query: String) : LegalDocumentEvent()
    
    /**
     * Clear search results
     */
    data object ClearSearch : LegalDocumentEvent()
    
    /**
     * Accept document (for first-time users)
     */
    data class AcceptDocument(val documentType: String) : LegalDocumentEvent()
    
    /**
     * Retry failed operations
     */
    data object Retry : LegalDocumentEvent()
}

/**
 * Side effects that should be handled by the UI
 */
sealed class LegalDocumentSideEffect {
    /**
     * Navigate to external browser with document URL
     */
    data class NavigateToExternalBrowser(val url: String) : LegalDocumentSideEffect()
    
    /**
     * Show document download completion
     */
    data class ShowDownloadComplete(val filePath: String) : LegalDocumentSideEffect()
    
    /**
     * Show document acceptance confirmation
     */
    data class ShowAcceptanceConfirmation(val documentType: String) : LegalDocumentSideEffect()
    
    /**
     * Show error message
     */
    data class ShowError(val message: String) : LegalDocumentSideEffect()
    
    /**
     * Show search results
     */
    data class ShowSearchResults(val results: List<SearchResult>) : LegalDocumentSideEffect()
    
    /**
     * Copy document text to clipboard
     */
    data class CopyToClipboard(val text: String) : LegalDocumentSideEffect()
}

/**
 * Search result within document
 */
data class SearchResult(
    val sectionTitle: String,
    val matchText: String,
    val position: Int,
    val context: String
)