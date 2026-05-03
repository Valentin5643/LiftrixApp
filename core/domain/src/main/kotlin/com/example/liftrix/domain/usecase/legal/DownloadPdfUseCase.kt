package com.example.liftrix.domain.usecase.legal

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.LegalDocumentService
import com.example.liftrix.domain.service.LegalDocumentType
import com.example.liftrix.domain.service.PdfGenerationService
import com.example.liftrix.domain.service.DownloadManagerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.example.liftrix.domain.util.DomainLogger as Timber
import javax.inject.Inject

/**
 * Use case for downloading legal documents as PDF files
 * 
 * Features:
 * - WebView-based PDF generation from HTML content
 * - Android Download Manager integration
 * - Progress tracking and user notifications
 * - Proper file management with user scoping
 * - Error handling for network and storage issues
 */
class DownloadPdfUseCase @Inject constructor(
    private val legalDocumentService: LegalDocumentService,
    private val pdfGenerationService: PdfGenerationService,
    private val downloadManagerService: DownloadManagerService
) {
    
    /**
     * Downloads a legal document as PDF with progress tracking
     * 
     * @param userId User identifier for scoped file management
     * @param documentType Type of document to download
     * @param displayName Human-readable name for the file
     * @return Flow of download progress updates and final result
     */
    suspend operator fun invoke(
        userId: String,
        documentType: String,
        displayName: String = documentType
    ): Flow<DownloadPdfResult> = flow {
        try {
            emit(DownloadPdfResult.Progress(0f, "Fetching document..."))
            
            // Step 1: Fetch document content
            val legalDocumentType = when (documentType) {
                "privacy_policy" -> LegalDocumentType.PRIVACY_POLICY
                "terms_of_service" -> LegalDocumentType.TERMS_OF_SERVICE
                "eula" -> LegalDocumentType.EULA
                "data_processing_agreement" -> LegalDocumentType.DATA_PROCESSING_AGREEMENT
                else -> throw IllegalArgumentException("Unsupported document type: $documentType")
            }
            
            val document = when (legalDocumentType) {
                LegalDocumentType.PRIVACY_POLICY -> legalDocumentService.getPrivacyPolicy(forceRefresh = false)
                LegalDocumentType.TERMS_OF_SERVICE -> legalDocumentService.getTermsOfService(forceRefresh = false)
                LegalDocumentType.EULA -> legalDocumentService.getEULA(forceRefresh = false)
                LegalDocumentType.DATA_PROCESSING_AGREEMENT -> legalDocumentService.getDataProcessingAgreement(forceRefresh = false)
                else -> throw IllegalArgumentException("Document type not supported: $legalDocumentType")
            }.fold(
                onSuccess = { it },
                onFailure = { error ->
                    emit(DownloadPdfResult.Error(error as? LiftrixError ?: LiftrixError.BusinessLogicError(
                        code = "DOCUMENT_FETCH_FAILED",
                        errorMessage = "Failed to fetch document: ${error.message}",
                        analyticsContext = mapOf("document_type" to documentType)
                    )))
                    return@flow
                }
            )
            
            if (document == null) {
                emit(DownloadPdfResult.Error(LiftrixError.BusinessLogicError(
                    code = "DOCUMENT_NOT_FOUND",
                    errorMessage = "Document not found: $documentType",
                    analyticsContext = mapOf("document_type" to documentType)
                )))
                return@flow
            }
            
            emit(DownloadPdfResult.Progress(20f, "Generating PDF..."))
            
            // Step 2: Generate PDF from HTML content
            val pdfGenerationRequest = PdfGenerationRequest(
                htmlContent = document.content,
                title = document.title,
                fileName = "${document.type.fileName}_${document.version}.pdf",
                isMarkdown = document.isMarkdown
            )
            
            val pdfFile = pdfGenerationService.generatePdf(pdfGenerationRequest).fold(
                onSuccess = { it },
                onFailure = { error ->
                    emit(DownloadPdfResult.Error(error as? LiftrixError ?: LiftrixError.BusinessLogicError(
                        code = "PDF_GENERATION_FAILED",
                        errorMessage = "Failed to generate PDF: ${error.message}",
                        analyticsContext = mapOf("document_type" to documentType)
                    )))
                    return@flow
                }
            )
            
            emit(DownloadPdfResult.Progress(60f, "Preparing download..."))
            
            // Step 3: Initiate download using Android Download Manager
            val downloadRequest = DownloadRequest(
                sourceFile = pdfFile,
                fileName = "${displayName}_v${document.version}.pdf",
                title = "$displayName (PDF)",
                description = "Legal document downloaded from Liftrix",
                mimeType = "application/pdf",
                userId = userId
            )
            
            // Step 4: Monitor download progress
            downloadManagerService.startDownload(downloadRequest).collect { downloadProgress ->
                when (downloadProgress) {
                    is DownloadProgress.InProgress -> {
                        val overallProgress = 60f + (downloadProgress.progress * 0.4f)
                        emit(DownloadPdfResult.Progress(overallProgress, "Downloading... ${(downloadProgress.progress * 100).toInt()}%"))
                    }
                    is DownloadProgress.Success -> {
                        emit(DownloadPdfResult.Success(
                            filePath = downloadProgress.filePath,
                            fileName = downloadRequest.fileName,
                            fileSize = downloadProgress.fileSize
                        ))
                        Timber.d("PDF download completed: ${downloadProgress.filePath}")
                    }
                    is DownloadProgress.Error -> {
                        emit(DownloadPdfResult.Error(LiftrixError.BusinessLogicError(
                            code = "DOWNLOAD_FAILED",
                            errorMessage = "Download failed: ${downloadProgress.error}",
                            analyticsContext = mapOf(
                                "document_type" to documentType,
                                "file_name" to downloadRequest.fileName
                            )
                        )))
                    }
                }
            }
            
        } catch (e: Exception) {
            val error = when (e) {
                is LiftrixError -> e
                else -> LiftrixError.BusinessLogicError(
                    code = "PDF_DOWNLOAD_FAILED",
                    errorMessage = "Failed to download document as PDF: ${e.message}",
                    analyticsContext = mapOf(
                        "operation" to "DOWNLOAD_LEGAL_DOCUMENT_PDF",
                        "document_type" to documentType,
                        "user_id" to userId
                    )
                )
            }
            emit(DownloadPdfResult.Error(error))
            Timber.e("PDF download failed: $error")
        }
    }
    
    /**
     * Validates download permissions and storage availability
     * 
     * @param userId User identifier
     * @return LiftrixResult indicating if download is possible
     */
    suspend fun validateDownloadConditions(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ValidationError(
                field = "download_conditions",
                violations = listOf("Download validation failed: ${throwable.message}"),
                analyticsContext = mapOf("operation" to "VALIDATE_PDF_DOWNLOAD")
            )
        }
    ) {
        downloadManagerService.validateDownloadConditions(userId)
    }
}

/**
 * Request for PDF generation from HTML content
 */
data class PdfGenerationRequest(
    val htmlContent: String,
    val title: String,
    val fileName: String,
    val isMarkdown: Boolean = false,
    val cssStyles: String? = null,
    val pageMargins: PdfPageMargins = PdfPageMargins.DEFAULT
)

/**
 * Page margin configuration for PDF generation
 */
data class PdfPageMargins(
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float
) {
    companion object {
        val DEFAULT = PdfPageMargins(
            top = 1.0f,
            bottom = 1.0f,
            left = 0.75f,
            right = 0.75f
        )
    }
}

/**
 * Request for downloading files via Android Download Manager
 */
data class DownloadRequest(
    val sourceFile: java.io.File,
    val fileName: String,
    val title: String,
    val description: String,
    val mimeType: String,
    val userId: String,
    val notificationVisibility: Int = 1, // DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
    val allowedNetworkTypes: Int = 3 // DownloadManager.Request.NETWORK_WIFI | NETWORK_MOBILE
)

/**
 * Download progress states
 */
sealed class DownloadProgress {
    data class InProgress(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress()
    data class Success(val filePath: String, val fileSize: Long) : DownloadProgress()
    data class Error(val error: String) : DownloadProgress()
}

/**
 * Results from PDF download operation
 */
sealed class DownloadPdfResult {
    data class Progress(val progress: Float, val message: String) : DownloadPdfResult()
    data class Success(val filePath: String, val fileName: String, val fileSize: Long) : DownloadPdfResult()
    data class Error(val error: LiftrixError) : DownloadPdfResult()
}
