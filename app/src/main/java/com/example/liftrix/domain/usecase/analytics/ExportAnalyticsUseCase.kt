package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.data.export.AnalyticsExporter
import com.example.liftrix.domain.model.analytics.ProgressMetrics
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.service.AnalyticsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Analytics data export use case implementing Clean Architecture patterns
 * 
 * Provides comprehensive export functionality for analytics data including:
 * - PDF generation with visual charts and progress summaries
 * - CSV export for raw data analysis in external tools
 * - Configurable date ranges (weekly, monthly, quarterly, yearly)
 * - Error handling with LiftrixResult<T> pattern
 * - Performance optimization for large datasets
 * 
 * Export Formats:
 * - PDF: Visual reports with charts, metrics, and progress summaries
 * - CSV: Raw data export for external analysis tools
 * 
 * Integration Points:
 * - AnalyticsEngine for data calculation and aggregation
 * - AnalyticsExporter for format-specific generation
 * - Android sharing framework for distribution
 * 
 * Performance Targets:
 * - PDF generation: <3s for monthly reports
 * - CSV export: <1s for quarterly data
 * - File size optimization for sharing
 */
class ExportAnalyticsUseCase @Inject constructor(
    private val analyticsEngine: AnalyticsEngine,
    private val analyticsExporter: AnalyticsExporter
) {
    
    companion object {
        private const val MAX_EXPORT_TIME_MS = 10_000L // 10 second timeout
        private const val PDF_FILE_PREFIX = "liftrix_analytics_report"
        private const val CSV_FILE_PREFIX = "liftrix_analytics_data"
    }
    
    /**
     * Exports analytics data to PDF format with visual charts and summaries
     * 
     * @param userId The user ID to export analytics for
     * @param dateRange The time range for data export
     * @return LiftrixResult containing generated PDF file or error
     */
    suspend fun exportToPdf(
        userId: String,
        dateRange: TimeRange
    ): LiftrixResult<File> = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("Starting PDF export for user: $userId, dateRange: $dateRange")
            val startTime = System.currentTimeMillis()
            
            // Validate inputs
            if (userId.isBlank()) {
                return@withContext liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf("User ID cannot be blank for analytics export")
                    )
                )
            }
            
            if (!dateRange.isValid()) {
                return@withContext liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "dateRange",
                        violations = listOf("Invalid date range for export: $dateRange")
                    )
                )
            }
            
            // Calculate comprehensive progress metrics
            val metricsResult = analyticsEngine.calculateProgressMetrics(userId, dateRange)
            if (metricsResult.isFailure) {
                return@withContext liftrixFailure(
                    LiftrixError.CalculationError(
                        errorMessage = "Failed to calculate analytics for export: ${metricsResult.exceptionOrNull()?.message}",
                        operation = "exportToPdf"
                    )
                )
            }
            
            val progressMetrics = metricsResult.getOrThrow()
            
            // Generate PDF report
            val pdfResult = analyticsExporter.generatePdfReport(progressMetrics, dateRange)
            if (pdfResult.isFailure) {
                return@withContext liftrixFailure(
                    LiftrixError.ExportError(
                        errorMessage = "Failed to generate PDF report: ${pdfResult.exceptionOrNull()?.message}",
                        operation = "exportToPdf"
                    )
                )
            }
            
            val pdfFile = pdfResult.getOrThrow()
            
            val executionTime = System.currentTimeMillis() - startTime
            Timber.d("PDF export completed in ${executionTime}ms, file size: ${pdfFile.length()} bytes")
            
            // Check execution time performance
            if (executionTime > MAX_EXPORT_TIME_MS) {
                Timber.w("PDF export took ${executionTime}ms, exceeding target of ${MAX_EXPORT_TIME_MS}ms")
            }
            
            liftrixSuccess(pdfFile)
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during PDF export for user: $userId")
            liftrixFailure(
                LiftrixError.ExportError(
                    errorMessage = "Unexpected error during PDF export: ${e.message}",
                    operation = "exportToPdf"
                )
            )
        }
    }
    
    /**
     * Exports analytics data to CSV format for external analysis
     * 
     * @param userId The user ID to export analytics for
     * @param dateRange The time range for data export
     * @return LiftrixResult containing generated CSV file or error
     */
    suspend fun exportToCsv(
        userId: String,
        dateRange: TimeRange
    ): LiftrixResult<File> = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("Starting CSV export for user: $userId, dateRange: $dateRange")
            val startTime = System.currentTimeMillis()
            
            // Validate inputs
            if (userId.isBlank()) {
                return@withContext liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf("User ID cannot be blank for analytics export")
                    )
                )
            }
            
            if (!dateRange.isValid()) {
                return@withContext liftrixFailure(
                    LiftrixError.ValidationError(
                        field = "dateRange",
                        violations = listOf("Invalid date range for export: $dateRange")
                    )
                )
            }
            
            // Calculate comprehensive progress metrics
            val metricsResult = analyticsEngine.calculateProgressMetrics(userId, dateRange)
            if (metricsResult.isFailure) {
                return@withContext liftrixFailure(
                    LiftrixError.CalculationError(
                        errorMessage = "Failed to calculate analytics for export: ${metricsResult.exceptionOrNull()?.message}",
                        operation = "exportToCsv"
                    )
                )
            }
            
            val progressMetrics = metricsResult.getOrThrow()
            
            // Generate CSV export
            val csvResult = analyticsExporter.generateCsvExport(progressMetrics, dateRange)
            if (csvResult.isFailure) {
                return@withContext liftrixFailure(
                    LiftrixError.ExportError(
                        errorMessage = "Failed to generate CSV export: ${csvResult.exceptionOrNull()?.message}",
                        operation = "exportToCsv"
                    )
                )
            }
            
            val csvFile = csvResult.getOrThrow()
            
            val executionTime = System.currentTimeMillis() - startTime
            Timber.d("CSV export completed in ${executionTime}ms, file size: ${csvFile.length()} bytes")
            
            // Check execution time performance
            if (executionTime > MAX_EXPORT_TIME_MS) {
                Timber.w("CSV export took ${executionTime}ms, exceeding target of ${MAX_EXPORT_TIME_MS}ms")
            }
            
            liftrixSuccess(csvFile)
            
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during CSV export for user: $userId")
            liftrixFailure(
                LiftrixError.ExportError(
                    errorMessage = "Unexpected error during CSV export: ${e.message}",
                    operation = "exportToCsv"
                )
            )
        }
    }
    
    /**
     * Generates filename for export based on format and date range
     * 
     * @param format The export format (PDF or CSV)
     * @param dateRange The date range for the export
     * @return Formatted filename with timestamp
     */
    private fun generateExportFilename(format: ExportFormat, dateRange: TimeRange): String {
        val prefix = when (format) {
            ExportFormat.PDF -> PDF_FILE_PREFIX
            ExportFormat.CSV -> CSV_FILE_PREFIX
        }
        
        val extension = when (format) {
            ExportFormat.PDF -> "pdf"
            ExportFormat.CSV -> "csv"
        }
        
        val dateRangeSuffix = "${dateRange.startDate}_${dateRange.endDate}"
        val timestamp = System.currentTimeMillis()
        
        return "${prefix}_${dateRangeSuffix}_${timestamp}.${extension}"
    }
}

/**
 * Export format enumeration for analytics data
 */
enum class ExportFormat {
    PDF,
    CSV
}