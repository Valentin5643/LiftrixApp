package com.example.liftrix.ui.settings.legal

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.LegalDocumentService
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.legal.DownloadPdfUseCase
import com.example.liftrix.domain.usecase.legal.DownloadPdfResult
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for legal document screens implementing MVI pattern
 * 
 * Features:
 * - Document loading with remote content fetching
 * - Offline content caching for legal documents
 * - PDF download functionality
 * - Document search capabilities
 * - User acceptance tracking
 * - Error handling with retry capabilities
 * - Real-time content updates
 */
@HiltViewModel
class LegalDocumentViewModel @Inject constructor(
    private val legalDocumentService: LegalDocumentService,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val downloadPdfUseCase: DownloadPdfUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<LegalDocumentUiState, LegalDocumentEvent>(errorHandler) {
    
    override val _uiState = MutableStateFlow<LegalDocumentUiState>(LegalDocumentUiState.Loading)
    
    private val _sideEffects = MutableSharedFlow<LegalDocumentSideEffect>()
    val sideEffects = _sideEffects.asSharedFlow()
    
    init {
        // Load all documents on initialization
        handleEvent(LegalDocumentEvent.LoadAllDocuments)
    }
    
    override fun handleEvent(event: LegalDocumentEvent) {
        when (event) {
            is LegalDocumentEvent.LoadPrivacyPolicy -> loadPrivacyPolicy()
            is LegalDocumentEvent.LoadTermsOfService -> loadTermsOfService()
            is LegalDocumentEvent.LoadAllDocuments -> loadAllDocuments()
            is LegalDocumentEvent.RefreshContent -> refreshContent()
            is LegalDocumentEvent.DownloadAsPdf -> downloadAsPdf(event.documentType)
            is LegalDocumentEvent.SearchDocument -> searchDocument(event.query)
            is LegalDocumentEvent.ClearSearch -> clearSearch()
            is LegalDocumentEvent.AcceptDocument -> acceptDocument(event.documentType)
            is LegalDocumentEvent.Retry -> retry()
        }
    }
    
    /**
     * Loads privacy policy document
     */
    private fun loadPrivacyPolicy() {
        viewModelScope.launch {
            updateState { LegalDocumentUiState.Loading }
            
            legalDocumentService.getPrivacyPolicy().fold(
                onSuccess = { document ->
                    val currentData = getCurrentData()
                    val data = currentData.copy(
                        privacyPolicy = document.content,
                        privacyPolicyLastUpdated = document.lastModified.toString()
                    )
                    
                    if (data.hasPrivacyPolicy) {
                        updateState { LegalDocumentUiState.Success(data) }
                    } else {
                        updateState { LegalDocumentUiState.Empty() }
                    }
                    Timber.d("Privacy policy loaded successfully")
                },
                onFailure = { throwable ->
                    val error = when (throwable) {
                        is LiftrixError -> throwable
                        else -> LiftrixError.BusinessLogicError(
                            code = "PRIVACY_LOAD_FAILED",
                            errorMessage = throwable.message ?: "Failed to load privacy policy",
                            analyticsContext = mapOf("operation" to "LOAD_PRIVACY_POLICY")
                        )
                    }
                    updateState { LegalDocumentUiState.Error(error, getCurrentData()) }
                    Timber.e("Failed to load privacy policy: $error")
                }
            )
        }
    }
    
    /**
     * Loads terms of service document
     */
    private fun loadTermsOfService() {
        viewModelScope.launch {
            updateState { LegalDocumentUiState.Loading }
            
            legalDocumentService.getTermsOfService().fold(
                onSuccess = { document ->
                    val currentData = getCurrentData()
                    val data = currentData.copy(
                        termsOfService = document.content,
                        termsOfServiceLastUpdated = document.lastModified.toString()
                    )
                    
                    if (data.hasTermsOfService) {
                        updateState { LegalDocumentUiState.Success(data) }
                    } else {
                        updateState { LegalDocumentUiState.Empty() }
                    }
                    Timber.d("Terms of service loaded successfully")
                },
                onFailure = { throwable ->
                    val error = when (throwable) {
                        is LiftrixError -> throwable
                        else -> LiftrixError.BusinessLogicError(
                            code = "TERMS_LOAD_FAILED",
                            errorMessage = throwable.message ?: "Failed to load terms of service",
                            analyticsContext = mapOf("operation" to "LOAD_TERMS_OF_SERVICE")
                        )
                    }
                    updateState { LegalDocumentUiState.Error(error, getCurrentData()) }
                    Timber.e("Failed to load terms of service: $error")
                }
            )
        }
    }
    
    /**
     * Loads all legal documents
     */
    private fun loadAllDocuments() {
        viewModelScope.launch {
            updateState { LegalDocumentUiState.Loading }
            
            // Load both documents
            val privacyDocument = legalDocumentService.getPrivacyPolicy().fold(
                onSuccess = { it },
                onFailure = { null }
            )
            val termsDocument = legalDocumentService.getTermsOfService().fold(
                onSuccess = { it },
                onFailure = { null }
            )
            
            val data = LegalDocumentUiState.Data(
                privacyPolicy = privacyDocument?.content ?: "",
                termsOfService = termsDocument?.content ?: "",
                privacyPolicyLastUpdated = privacyDocument?.lastModified?.toString(),
                termsOfServiceLastUpdated = termsDocument?.lastModified?.toString()
            )
            
            if (data.hasAnyDocument) {
                updateState { LegalDocumentUiState.Success(data) }
            } else {
                updateState { LegalDocumentUiState.Empty() }
            }
            Timber.d("All legal documents loaded successfully")
        }
    }
    
    /**
     * Refreshes document content from remote sources
     */
    private fun refreshContent() {
        viewModelScope.launch {
            val currentData = getCurrentData()
            updateState { LegalDocumentUiState.Success(currentData.copy(isRefreshing = true)) }
            
            try {
                // Force refresh from remote
                legalDocumentService.refreshAllDocuments().fold(
                    onSuccess = {
                        Timber.d("Remote refresh succeeded")
                    },
                    onFailure = { error ->
                        Timber.w("Refresh failed but continuing: $error")
                    }
                )
                
                // Reload documents
                val privacyDocument = legalDocumentService.getPrivacyPolicy(forceRefresh = true).fold(
                    onSuccess = { it },
                    onFailure = { null }
                )
                val termsDocument = legalDocumentService.getTermsOfService(forceRefresh = true).fold(
                    onSuccess = { it },
                    onFailure = { null }
                )
                
                val data = currentData.copy(
                    privacyPolicy = privacyDocument?.content ?: "",
                    termsOfService = termsDocument?.content ?: "",
                    privacyPolicyLastUpdated = privacyDocument?.lastModified?.toString(),
                    termsOfServiceLastUpdated = termsDocument?.lastModified?.toString(),
                    isRefreshing = false
                )
                
                updateState { LegalDocumentUiState.Success(data) }
                Timber.d("Legal documents refreshed successfully")
            } catch (e: Exception) {
                val error = LiftrixError.BusinessLogicError(
                    code = "REFRESH_FAILED",
                    errorMessage = "Failed to refresh documents: ${e.message}",
                    analyticsContext = mapOf("operation" to "REFRESH_LEGAL_DOCUMENTS")
                )
                updateState { 
                    LegalDocumentUiState.Error(error, currentData.copy(isRefreshing = false))
                }
                emitSideEffect(LegalDocumentSideEffect.ShowError("Failed to refresh documents"))
                Timber.e(e, "Failed to refresh legal documents")
            }
        }
    }
    
    /**
     * Downloads document as PDF with full implementation
     */
    private fun downloadAsPdf(documentType: String) {
        viewModelScope.launch {
            val currentData = getCurrentData()
            updateState { 
                LegalDocumentUiState.Success(currentData.copy(downloadInProgress = true, downloadProgress = 0f))
            }
            
            try {
                val userId = getCurrentUserIdUseCase() ?: throw LiftrixError.BusinessLogicError(
                    code = "USER_NOT_AUTHENTICATED",
                    errorMessage = "User must be authenticated to download documents",
                    analyticsContext = mapOf("operation" to "DOWNLOAD_PDF_DOCUMENT")
                )
                
                val displayName = when (documentType) {
                    "privacy_policy" -> "Privacy Policy"
                    "terms_of_service" -> "Terms of Service"
                    "eula" -> "End User License Agreement"
                    "data_processing_agreement" -> "Data Processing Agreement"
                    else -> documentType.replace("_", " ").split(" ")
                        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                }
                
                // Start PDF download with progress tracking
                downloadPdfUseCase(userId, documentType, displayName).collect { result ->
                    when (result) {
                        is DownloadPdfResult.Progress -> {
                            val data = getCurrentData().copy(
                                downloadInProgress = true,
                                downloadProgress = result.progress / 100f
                            )
                            updateState { LegalDocumentUiState.Success(data) }
                            Timber.d("PDF download progress: ${result.progress}% - ${result.message}")
                        }
                        is DownloadPdfResult.Success -> {
                            val data = getCurrentData().copy(
                                downloadInProgress = false,
                                downloadProgress = 1.0f
                            )
                            updateState { LegalDocumentUiState.Success(data) }
                            emitSideEffect(LegalDocumentSideEffect.ShowDownloadComplete(result.filePath))
                            Timber.d("PDF download completed: ${result.filePath} (${result.fileSize} bytes)")
                        }
                        is DownloadPdfResult.Error -> {
                            val data = getCurrentData().copy(
                                downloadInProgress = false,
                                downloadProgress = 0f
                            )
                            updateState { 
                                LegalDocumentUiState.Error(result.error, data)
                            }
                            emitSideEffect(LegalDocumentSideEffect.ShowError("Failed to download PDF: ${result.error.message}"))
                            Timber.e("PDF download failed: ${result.error}")
                        }
                    }
                }
                
            } catch (e: Exception) {
                val error = when (e) {
                    is LiftrixError -> e
                    else -> LiftrixError.BusinessLogicError(
                        code = "DOWNLOAD_FAILED",
                        errorMessage = "Failed to download document: ${e.message}",
                        analyticsContext = mapOf(
                            "operation" to "DOWNLOAD_LEGAL_DOCUMENT_PDF",
                            "document_type" to documentType
                        )
                    )
                }
                val data = getCurrentData().copy(
                    downloadInProgress = false,
                    downloadProgress = 0f
                )
                updateState { 
                    LegalDocumentUiState.Error(error, data)
                }
                emitSideEffect(LegalDocumentSideEffect.ShowError("Failed to download document"))
                Timber.e(e, "Failed to download document as PDF")
            }
        }
    }
    
    /**
     * Searches within document content
     */
    private fun searchDocument(query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }
        
        executeUseCase(
            useCase = {
                val currentData = getCurrentData()
                val searchResults = mutableListOf<SearchResult>()
                
                // Search in privacy policy
                currentData.privacyPolicy?.let { document ->
                    val matches = findTextMatches(document, query, "Privacy Policy")
                    searchResults.addAll(matches)
                }
                
                // Search in terms of service
                currentData.termsOfService?.let { document ->
                    val matches = findTextMatches(document, query, "Terms of Service")
                    searchResults.addAll(matches)
                }
                
                Result.success(searchResults)
            },
            onSuccess = { results ->
                emitSideEffect(LegalDocumentSideEffect.ShowSearchResults(results))
                Timber.d("Document search completed: ${results.size} results for '$query'")
            },
            onError = { error ->
                emitSideEffect(LegalDocumentSideEffect.ShowError("Search failed"))
                Timber.e("Document search failed: $error")
            }
        )
    }
    
    /**
     * Clears search results
     */
    private fun clearSearch() {
        emitSideEffect(LegalDocumentSideEffect.ShowSearchResults(emptyList()))
        Timber.d("Document search cleared")
    }
    
    /**
     * Records user acceptance of a document
     */
    private fun acceptDocument(documentType: String) {
        executeUseCase(
            useCase = {
                when (documentType) {
                    "privacy_policy" -> {
                        val document = legalDocumentService.getPrivacyPolicy().fold(
                            onSuccess = { it },
                            onFailure = { error -> throw error }
                        )
                        val userId = getCurrentUserIdUseCase() ?: throw LiftrixError.BusinessLogicError(
                            code = "USER_NOT_AUTHENTICATED",
                            errorMessage = "User must be authenticated to accept legal documents",
                            analyticsContext = mapOf("operation" to "ACCEPT_PRIVACY_POLICY")
                        )
                        legalDocumentService.recordDocumentAcceptance(
                            userId = userId,
                            documentType = com.example.liftrix.domain.service.LegalDocumentType.PRIVACY_POLICY,
                            version = document.version
                        ).fold(
                            onSuccess = { /* Success */ },
                            onFailure = { error -> throw error }
                        )
                    }
                    "terms_of_service" -> {
                        val document = legalDocumentService.getTermsOfService().fold(
                            onSuccess = { it },
                            onFailure = { error -> throw error }
                        )
                        val userId = getCurrentUserIdUseCase() ?: throw LiftrixError.BusinessLogicError(
                            code = "USER_NOT_AUTHENTICATED",
                            errorMessage = "User must be authenticated to accept legal documents",
                            analyticsContext = mapOf("operation" to "ACCEPT_TERMS_OF_SERVICE")
                        )
                        legalDocumentService.recordDocumentAcceptance(
                            userId = userId,
                            documentType = com.example.liftrix.domain.service.LegalDocumentType.TERMS_OF_SERVICE,
                            version = document.version
                        ).fold(
                            onSuccess = { /* Success */ },
                            onFailure = { error -> throw error }
                        )
                    }
                    else -> throw LiftrixError.ValidationError(
                        field = "documentType",
                        violations = listOf("Invalid document type: $documentType"),
                        analyticsContext = mapOf("operation" to "ACCEPT_LEGAL_DOCUMENT")
                    )
                }
                Result.success(Unit)
            },
            onSuccess = {
                viewModelScope.launch {
                    _sideEffects.emit(LegalDocumentSideEffect.ShowAcceptanceConfirmation(documentType))
                }
                Timber.d("User accepted document: $documentType")
            },
            onError = { error ->
                emitSideEffect(LegalDocumentSideEffect.ShowError("Failed to record acceptance"))
                Timber.e("Failed to record document acceptance: $error")
            }
        )
    }
    
    /**
     * Retries the last failed operation
     */
    private fun retry() {
        when (val currentState = _uiState.value) {
            is LegalDocumentUiState.Error -> {
                if (currentState.previousData?.hasAnyDocument == true) {
                    // Retry refresh
                    refreshContent()
                } else {
                    // Retry initial load
                    loadAllDocuments()
                }
            }
            is LegalDocumentUiState.Empty -> loadAllDocuments()
            else -> {
                // Nothing to retry
                Timber.d("No failed operation to retry")
            }
        }
    }
    
    /**
     * Finds text matches within a document
     */
    private fun findTextMatches(
        document: String,
        query: String,
        sectionTitle: String
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val queryLower = query.lowercase()
        val documentLower = document.lowercase()
        
        var startIndex = 0
        while (true) {
            val index = documentLower.indexOf(queryLower, startIndex)
            if (index == -1) break
            
            // Extract context around the match (50 characters before and after)
            val contextStart = maxOf(0, index - 50)
            val contextEnd = minOf(document.length, index + query.length + 50)
            val context = document.substring(contextStart, contextEnd)
            
            val matchText = document.substring(index, index + query.length)
            
            results.add(
                SearchResult(
                    sectionTitle = sectionTitle,
                    matchText = matchText,
                    position = index,
                    context = context
                )
            )
            
            startIndex = index + 1
        }
        
        return results.take(10) // Limit to 10 results per document
    }
    
    /**
     * Gets current data from state or returns default
     */
    private fun getCurrentData(): LegalDocumentUiState.Data {
        return when (val currentState = _uiState.value) {
            is LegalDocumentUiState.Success -> currentState.data
            is LegalDocumentUiState.Error -> currentState.previousData ?: LegalDocumentUiState.Data()
            else -> LegalDocumentUiState.Data()
        }
    }
    
    /**
     * Emits a side effect to be handled by the UI
     */
    private fun emitSideEffect(effect: LegalDocumentSideEffect) {
        viewModelScope.launch {
            _sideEffects.emit(effect)
        }
    }
}