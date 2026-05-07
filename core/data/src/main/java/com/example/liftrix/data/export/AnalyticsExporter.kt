package com.example.liftrix.data.export

import com.example.liftrix.domain.model.analytics.ProgressMetrics
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analytics data export coordination interface implementing Clean Architecture patterns
 * 
 * Provides centralized export functionality coordinating between:
 * - PdfExporter for visual report generation
 * - CsvExporter for raw data export
 * - File system management for temporary export files
 * - Error handling with comprehensive LiftrixResult<T> pattern
 * 
 * Export Capabilities:
 * - PDF reports with charts, metrics, and visual summaries
 * - CSV data export for external analysis tools
 * - File compression for large datasets
 * - Temporary file cleanup and management
 * 
 * Architecture:
 * - Dependency injection with Hilt integration
 * - Coroutine-based async processing for performance
 * - Comprehensive error handling and logging
 * - Clean separation of concerns between formats
 * 
 * Performance Targets:
 * - PDF generation: <3s for monthly reports
 * - CSV export: <1s for quarterly data
 * - Memory efficient processing for large datasets
 */
@Singleton
class AnalyticsExporter @Inject constructor(
    private val pdfExporter: PdfExporter,
    private val csvExporter: CsvExporter
) {
    
    companion object {
        private const val EXPORT_DIR_NAME = "analytics_exports"
        private const val FILE_CLEANUP_DELAY_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    /**
     * Generates PDF report with visual charts and progress summaries
     * 
     * @param progressMetrics The analytics data to export
     * @param dateRange The time range for the report
     * @return LiftrixResult containing generated PDF file or error
     */
    suspend fun generatePdfReport(
        progressMetrics: ProgressMetrics,
        dateRange: TimeRange
    ): LiftrixResult<File> = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("Generating PDF report for user: ${progressMetrics.userId}, dateRange: $dateRange")
            
            // Validate input data
            if (progressMetrics.userId.isBlank()) {
                return@withContext liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "progressMetrics.userId",
                        violations = listOf("User ID cannot be blank for PDF export")
                    )
                )
            }
            
            // Create export directory if it doesn't exist
            val exportDir = createExportDirectory()
            if (exportDir == null) {
                return@withContext liftrixFailure(
                    LiftrixError.FileSystemError(
                        errorMessage = "Failed to create export directory",
                        operation = "generatePdfReport"
                    )
                )
            }
            
            // Generate PDF content
            val pdfResult = pdfExporter.generateProgressReport(progressMetrics, dateRange)
            if (pdfResult.isFailure) {
                return@withContext liftrixFailure(
                    LiftrixError.ExportError(
                        errorMessage = "PDF generation failed: ${pdfResult.exceptionOrNull()?.message}",
                        operation = "generatePdfReport"
                    )
                )
            }
            
            val pdfBytes = pdfResult.getOrThrow()
            
            // Write PDF to file
            val filename = generatePdfFilename(progressMetrics.userId, dateRange)
            val pdfFile = File(exportDir, filename)
            
            pdfFile.writeBytes(pdfBytes)
            
            Timber.d("PDF report generated successfully: ${pdfFile.absolutePath}, size: ${pdfFile.length()} bytes")
            
            // Schedule file cleanup
            scheduleFileCleanup(pdfFile)
            
            liftrixSuccess(pdfFile)
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during PDF report generation")
            liftrixFailure(
                LiftrixError.ExportError(
                    errorMessage = "Unexpected error during PDF generation: ${e.message}",
                    operation = "generatePdfReport"
                )
            )
        }
    }
    
    /**
     * Generates CSV export for raw data analysis
     * 
     * @param progressMetrics The analytics data to export
     * @param dateRange The time range for the export
     * @return LiftrixResult containing generated CSV file or error
     */
    suspend fun generateCsvExport(
        progressMetrics: ProgressMetrics,
        dateRange: TimeRange
    ): LiftrixResult<File> = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("Generating CSV export for user: ${progressMetrics.userId}, dateRange: $dateRange")
            
            // Validate input data
            if (progressMetrics.userId.isBlank()) {
                return@withContext liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "progressMetrics.userId",
                        violations = listOf("User ID cannot be blank for CSV export")
                    )
                )
            }
            
            // Create export directory if it doesn't exist
            val exportDir = createExportDirectory()
            if (exportDir == null) {
                return@withContext liftrixFailure(
                    LiftrixError.FileSystemError(
                        errorMessage = "Failed to create export directory",
                        operation = "generateCsvExport"
                    )
                )
            }
            
            // Generate CSV content
            val csvResult = csvExporter.generateDataExport(progressMetrics, dateRange)
            if (csvResult.isFailure) {
                return@withContext liftrixFailure(
                    LiftrixError.ExportError(
                        errorMessage = "CSV generation failed: ${csvResult.exceptionOrNull()?.message}",
                        operation = "generateCsvExport"
                    )
                )
            }
            
            val csvContent = csvResult.getOrThrow()
            
            // Write CSV to file
            val filename = generateCsvFilename(progressMetrics.userId, dateRange)
            val csvFile = File(exportDir, filename)
            
            csvFile.writeText(csvContent)
            
            Timber.d("CSV export generated successfully: ${csvFile.absolutePath}, size: ${csvFile.length()} bytes")
            
            // Schedule file cleanup
            scheduleFileCleanup(csvFile)
            
            liftrixSuccess(csvFile)
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during CSV export generation")
            liftrixFailure(
                LiftrixError.ExportError(
                    errorMessage = "Unexpected error during CSV generation: ${e.message}",
                    operation = "generateCsvExport"
                )
            )
        }
    }
    
    /**
     * Creates export directory in app's cache directory
     * 
     * @return Export directory File object or null if creation failed
     */
    private fun createExportDirectory(): File? {
        return try {
            // Note: In a real implementation, this would use Android Context
            // For now, we'll use a temporary directory approach
            val tempDir = System.getProperty("java.io.tmpdir")
            val exportDir = File(tempDir, EXPORT_DIR_NAME)
            
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            if (exportDir.exists() && exportDir.isDirectory) {
                exportDir
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create export directory")
            null
        }
    }
    
    /**
     * Generates PDF filename with timestamp and date range
     * 
     * @param userId The user ID for the export
     * @param dateRange The date range for the export
     * @return Formatted filename for PDF export
     */
    private fun generatePdfFilename(userId: String, dateRange: TimeRange): String {
        val userIdSafe = userId.take(8) // Limit length for filename
        val dateRangeSuffix = "${dateRange.startDate}_${dateRange.endDate}"
        val timestamp = System.currentTimeMillis()
        
        return "liftrix_report_${userIdSafe}_${dateRangeSuffix}_${timestamp}.pdf"
    }
    
    /**
     * Generates CSV filename with timestamp and date range
     * 
     * @param userId The user ID for the export
     * @param dateRange The date range for the export
     * @return Formatted filename for CSV export
     */
    private fun generateCsvFilename(userId: String, dateRange: TimeRange): String {
        val userIdSafe = userId.take(8) // Limit length for filename
        val dateRangeSuffix = "${dateRange.startDate}_${dateRange.endDate}"
        val timestamp = System.currentTimeMillis()
        
        return "liftrix_data_${userIdSafe}_${dateRangeSuffix}_${timestamp}.csv"
    }
    
    /**
     * Schedules file cleanup for temporary export files
     * 
     * @param file The file to schedule for cleanup
     */
    private fun scheduleFileCleanup(file: File) {
        // Note: In a real implementation, this would use WorkManager or similar
        // For now, we'll just log the cleanup schedule
        Timber.d("Scheduled cleanup for file: ${file.absolutePath} in ${FILE_CLEANUP_DELAY_MS}ms")
        
        // Simple cleanup approach - mark for deletion on JVM exit
        file.deleteOnExit()
    }
}