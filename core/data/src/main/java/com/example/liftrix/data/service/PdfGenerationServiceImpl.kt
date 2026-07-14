package com.example.liftrix.data.service

import android.content.Context
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.PdfGenerationService
import com.example.liftrix.domain.usecase.legal.PdfGenerationRequest
import com.example.liftrix.domain.usecase.legal.PdfPageMargins
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Implementation of PdfGenerationService using Android WebView
 * Converts HTML content to PDF using WebView's built-in print capabilities
 */
class PdfGenerationServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PdfGenerationService {
    
    companion object {
        private const val PDF_TEMP_DIR = "pdf_temp"
        private const val CSS_STYLES_TEMPLATE = """
            <style>
                body { 
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    line-height: 1.6;
                    color: #333;
                    max-width: 100%;
                    margin: 0;
                    padding: 20px;
                }
                h1, h2, h3 { 
                    color: #2a3b7d;
                    margin-top: 2em;
                    margin-bottom: 0.5em;
                }
                h1 { 
                    font-size: 28px;
                    border-bottom: 2px solid #20c9b7;
                    padding-bottom: 10px;
                }
                h2 { 
                    font-size: 22px;
                    color: #20c9b7;
                }
                p { 
                    margin-bottom: 1em;
                    text-align: justify;
                }
                ul, ol { 
                    margin-bottom: 1em;
                    padding-left: 20px;
                }
                li { 
                    margin-bottom: 0.5em;
                }
                .header {
                    text-align: center;
                    margin-bottom: 40px;
                    padding-bottom: 20px;
                    border-bottom: 1px solid #eee;
                }
                .footer {
                    margin-top: 40px;
                    padding-top: 20px;
                    border-top: 1px solid #eee;
                    font-size: 12px;
                    color: #666;
                    text-align: center;
                }
                @media print {
                    body { 
                        margin: 0;
                        padding: 15px;
                    }
                    .no-print { 
                        display: none;
                    }
                }
            </style>
        """
    }
    
    override suspend fun generatePdf(request: PdfGenerationRequest): LiftrixResult<File> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PDF_GENERATION_FAILED",
                errorMessage = "Failed to generate PDF: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GENERATE_PDF",
                    "file_name" to request.fileName
                )
            )
        }
    ) {
        val tempDir = File(context.cacheDir, PDF_TEMP_DIR)
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        val outputFile = File(tempDir, request.fileName)
        
        // Generate HTML content with proper styling
        val htmlContent = if (request.isMarkdown) {
            convertMarkdownToHtml(request.htmlContent, request.title)
        } else {
            wrapHtmlContent(request.htmlContent, request.title, request.cssStyles)
        }
        
        // Generate PDF using WebView
        generatePdfFromHtml(htmlContent, outputFile, request.pageMargins)
        
        Timber.d("PDF generated successfully: ${outputFile.absolutePath}")
        outputFile
    }
    
    override suspend fun generatePdfFromMarkdown(
        markdownContent: String,
        title: String,
        fileName: String
    ): LiftrixResult<File> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "MARKDOWN_PDF_GENERATION_FAILED",
                errorMessage = "Failed to generate PDF from markdown: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GENERATE_MARKDOWN_PDF",
                    "file_name" to fileName
                )
            )
        }
    ) {
        val request = PdfGenerationRequest(
            htmlContent = markdownContent,
            title = title,
            fileName = fileName,
            isMarkdown = true
        )
        
        generatePdf(request).fold(
            onSuccess = { it },
            onFailure = { error -> throw error }
        )
    }
    
    override suspend fun validateGenerationCapabilities(): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ValidationError(
                field = "pdf_capabilities",
                violations = listOf("PDF generation validation failed: ${throwable.message}"),
                analyticsContext = mapOf("operation" to "VALIDATE_PDF_CAPABILITIES")
            )
        }
    ) {
        throw UnsupportedOperationException("PDF generation is unavailable in this build")
        
        // Check storage permissions and space
        val tempDir = File(context.cacheDir, PDF_TEMP_DIR)
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw IllegalStateException("Cannot create temporary directory for PDF generation")
        }
        
        // Check available space (require at least 10MB)
        val freeSpace = tempDir.freeSpace
        if (freeSpace < 10 * 1024 * 1024) {
            throw IllegalStateException("Insufficient storage space for PDF generation")
        }
        
        Unit
    }
    
    override suspend fun cleanupTemporaryFiles(maxAgeHours: Int): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PDF_CLEANUP_FAILED",
                errorMessage = "Failed to cleanup temporary PDF files: ${throwable.message}",
                analyticsContext = mapOf("operation" to "CLEANUP_PDF_TEMP_FILES")
            )
        }
    ) {
        val tempDir = File(context.cacheDir, PDF_TEMP_DIR)
        if (!tempDir.exists()) return@liftrixCatching
        
        val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000)
        var deletedCount = 0
        
        tempDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        
        Timber.d("Cleaned up $deletedCount temporary PDF files")
        Unit
    }
    
    /**
     * Generates PDF from HTML content using WebView's print functionality
     */
    private suspend fun generatePdfFromHtml(
        htmlContent: String,
        outputFile: File,
        pageMargins: PdfPageMargins
    ): Unit = throw UnsupportedOperationException("PDF generation is unavailable in this build")
    
    /**
     * Wraps HTML content with proper document structure and styling
     */
    private fun wrapHtmlContent(htmlContent: String, title: String, customCss: String?): String {
        val currentDate = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>$title</title>
                $CSS_STYLES_TEMPLATE
                ${customCss ?: ""}
            </head>
            <body>
                <div class="header">
                    <h1>$title</h1>
                    <p><strong>Liftrix - Legal Document</strong></p>
                </div>
                
                <div class="content">
                    $htmlContent
                </div>
                
                <div class="footer">
                    <p>Generated on $currentDate | Liftrix Legal Documents</p>
                    <p>For the most up-to-date version, visit our application or website.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
    
    /**
     * Converts markdown content to HTML (basic conversion)
     */
    private fun convertMarkdownToHtml(markdownContent: String, title: String): String {
        // Basic markdown to HTML conversion
        var html = markdownContent
            .replace(Regex("^# (.+)$", RegexOption.MULTILINE), "<h1>$1</h1>")
            .replace(Regex("^## (.+)$", RegexOption.MULTILINE), "<h2>$1</h2>")
            .replace(Regex("^### (.+)$", RegexOption.MULTILINE), "<h3>$1</h3>")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
            .replace(Regex("^- (.+)$", RegexOption.MULTILINE), "<li>$1</li>")
            .replace("\n\n", "</p><p>")
        
        // Wrap list items in ul tags
        html = html.replace(Regex("(<li>.+?</li>)+")) { matchResult ->
            "<ul>${matchResult.value}</ul>"
        }
        
        // Wrap in paragraphs
        if (!html.startsWith("<")) {
            html = "<p>$html</p>"
        }
        
        return wrapHtmlContent(html, title, null)
    }
    
}
