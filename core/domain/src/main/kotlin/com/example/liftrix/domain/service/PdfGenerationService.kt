package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.legal.PdfGenerationRequest
import java.io.File

/**
 * Service interface for PDF generation from HTML content
 * Uses WebView-based rendering for high-quality PDF output
 */
interface PdfGenerationService {
    
    /**
     * Generates a PDF file from HTML content
     * 
     * @param request PDF generation request with HTML content and formatting options
     * @return LiftrixResult containing the generated PDF file
     */
    suspend fun generatePdf(request: PdfGenerationRequest): LiftrixResult<File>
    
    /**
     * Generates PDF from markdown content by first converting to HTML
     * 
     * @param markdownContent Markdown content to convert
     * @param title Document title
     * @param fileName Output file name
     * @return LiftrixResult containing the generated PDF file
     */
    suspend fun generatePdfFromMarkdown(
        markdownContent: String,
        title: String,
        fileName: String
    ): LiftrixResult<File>
    
    /**
     * Validates PDF generation prerequisites (WebView availability, storage permissions)
     * 
     * @return LiftrixResult indicating if PDF generation is possible
     */
    suspend fun validateGenerationCapabilities(): LiftrixResult<Unit>
    
    /**
     * Cleans up temporary PDF files older than specified age
     * 
     * @param maxAgeHours Maximum age in hours for temporary files
     * @return LiftrixResult indicating cleanup success
     */
    suspend fun cleanupTemporaryFiles(maxAgeHours: Int = 24): LiftrixResult<Unit>
}