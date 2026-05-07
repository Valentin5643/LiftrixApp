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
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// Import iText7 dependencies
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder

/**
 * PDF generation service using iText7 for analytics report export
 * 
 * Generates professional PDF reports with:
 * - Executive summary with key metrics
 * - Detailed progress charts and tables
 * - Performance analytics and trends
 * - Visual branding consistent with Liftrix design
 * 
 * Technical Implementation:
 * - Uses iText7 for PDF generation with professional styling
 * - Supports comprehensive data visualization
 * - Optimized for performance with large datasets
 * - Follows Clean Architecture patterns with proper error handling
 * 
 * Performance Targets:
 * - PDF generation: <3s for monthly reports
 * - Memory efficient processing for large datasets
 * - File size optimization for sharing
 * 
 * @since 1.0.0
 */
@Singleton
class PdfExporter @Inject constructor() {
    
    companion object {
        private const val TITLE_FONT_SIZE = 20f
        private const val HEADER_FONT_SIZE = 16f
        private const val BODY_FONT_SIZE = 12f
        private const val SMALL_FONT_SIZE = 10f
        
        // Liftrix brand colors
        private val BRAND_PRIMARY = DeviceRgb(32, 201, 183) // #20C9B7
        private val BRAND_SECONDARY = DeviceRgb(42, 59, 125) // #2A3B7D
        private val BRAND_ACCENT = DeviceRgb(255, 107, 107) // #FF6B6B
        private val TEXT_PRIMARY = DeviceRgb(33, 37, 41) // #212529
        private val TEXT_SECONDARY = DeviceRgb(108, 117, 125) // #6C757D
        
        private val DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())
    }
    
    /**
     * Generates comprehensive PDF progress report
     * 
     * @param progressMetrics Analytics data for the report
     * @param dateRange Time range for the report
     * @return LiftrixResult containing PDF bytes or error
     */
    suspend fun generateProgressReport(
        progressMetrics: ProgressMetrics,
        dateRange: TimeRange
    ): LiftrixResult<ByteArray> = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("Starting PDF generation for user: ${progressMetrics.userId}")
            val startTime = System.currentTimeMillis()
            
            // Create PDF document
            val outputStream = ByteArrayOutputStream()
            val pdfWriter = PdfWriter(outputStream)
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)
            
            // Set up fonts
            val titleFont = PdfFontFactory.createFont()
            val headerFont = PdfFontFactory.createFont()
            val bodyFont = PdfFontFactory.createFont()
            
            // Generate PDF content
            addHeader(document, titleFont, dateRange)
            addExecutiveSummary(document, headerFont, bodyFont, progressMetrics)
            addDetailedMetrics(document, headerFont, bodyFont, progressMetrics)
            addProgressCharts(document, headerFont, bodyFont, progressMetrics)
            addFooter(document, bodyFont)
            
            // Close document
            document.close()
            
            val pdfBytes = outputStream.toByteArray()
            val executionTime = System.currentTimeMillis() - startTime
            
            Timber.d("PDF generation completed in ${executionTime}ms, size: ${pdfBytes.size} bytes")
            
            liftrixSuccess(pdfBytes)
            
        } catch (e: Exception) {
            Timber.e(e, "Error generating PDF report")
            liftrixFailure(
                LiftrixError.ExportError(
                    errorMessage = "PDF generation failed: ${e.message}",
                    operation = "generateProgressReport"
                )
            )
        }
    }
    
    /**
     * Adds header section with branding and report details
     */
    private fun addHeader(
        document: Document,
        titleFont: PdfFont,
        dateRange: TimeRange
    ) {
        // App title
        val titleParagraph = Paragraph("Liftrix Analytics Report")
            .setFont(titleFont)
            .setFontSize(TITLE_FONT_SIZE)
            .setFontColor(BRAND_PRIMARY)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(10f)
        
        document.add(titleParagraph)
        
        // Date range
        val dateRangeParagraph = Paragraph("${DATE_FORMAT.format(dateRange.startDate)} - ${DATE_FORMAT.format(dateRange.endDate)}")
            .setFontSize(HEADER_FONT_SIZE)
            .setFontColor(TEXT_SECONDARY)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(20f)
        
        document.add(dateRangeParagraph)
        
        // Generated timestamp
        val generatedParagraph = Paragraph("Generated: ${DATE_FORMAT.format(Date())} at ${TIME_FORMAT.format(Date())}")
            .setFontSize(SMALL_FONT_SIZE)
            .setFontColor(TEXT_SECONDARY)
            .setTextAlignment(TextAlignment.RIGHT)
            .setMarginBottom(30f)
        
        document.add(generatedParagraph)
    }
    
    /**
     * Adds executive summary section
     */
    private fun addExecutiveSummary(
        document: Document,
        headerFont: PdfFont,
        bodyFont: PdfFont,
        progressMetrics: ProgressMetrics
    ) {
        // Section title
        val sectionTitle = Paragraph("Executive Summary")
            .setFont(headerFont)
            .setFontSize(HEADER_FONT_SIZE)
            .setFontColor(BRAND_SECONDARY)
            .setBold()
            .setMarginBottom(15f)
        
        document.add(sectionTitle)
        
        // Create summary table
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f, 1f, 1f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)
        
        // Add header row
        summaryTable.addHeaderCell(createHeaderCell("Total Workouts"))
        summaryTable.addHeaderCell(createHeaderCell("Total Volume"))
        summaryTable.addHeaderCell(createHeaderCell("Avg Duration"))
        summaryTable.addHeaderCell(createHeaderCell("Strength Gain"))
        
        // Add data row
        summaryTable.addCell(createDataCell(progressMetrics.totalWorkouts.toString()))
        summaryTable.addCell(createDataCell("${progressMetrics.totalVolume} lbs"))
        summaryTable.addCell(createDataCell("${progressMetrics.averageDuration} min"))
        summaryTable.addCell(createDataCell("${progressMetrics.strengthGain}%"))
        
        document.add(summaryTable)
    }
    
    /**
     * Adds detailed metrics section
     */
    private fun addDetailedMetrics(
        document: Document,
        headerFont: PdfFont,
        bodyFont: PdfFont,
        progressMetrics: ProgressMetrics
    ) {
        // Section title
        val sectionTitle = Paragraph("Detailed Metrics")
            .setFont(headerFont)
            .setFontSize(HEADER_FONT_SIZE)
            .setFontColor(BRAND_SECONDARY)
            .setBold()
            .setMarginBottom(15f)
        
        document.add(sectionTitle)
        
        // Performance metrics
        val performanceText = """
            Total Training Sessions: ${progressMetrics.totalWorkouts}
            Total Volume Lifted: ${progressMetrics.totalVolume} lbs
            Average Session Duration: ${progressMetrics.averageDuration} minutes
            Strength Progression: ${progressMetrics.strengthGain}%
            Consistency Score: ${progressMetrics.consistencyScore}%
            
            Key Achievements:
            • Completed ${progressMetrics.totalWorkouts} workout sessions
            • Lifted a total of ${progressMetrics.totalVolume} lbs
            • Maintained ${progressMetrics.consistencyScore}% consistency
            • Achieved ${progressMetrics.strengthGain}% strength improvement
            
            Areas for Improvement:
            • Consider increasing workout frequency for better consistency
            • Focus on progressive overload for continued strength gains
            • Track rest times for optimal recovery
        """.trimIndent()
        
        val performanceParagraph = Paragraph(performanceText)
            .setFont(bodyFont)
            .setFontSize(BODY_FONT_SIZE)
            .setMarginBottom(20f)
        
        document.add(performanceParagraph)
    }
    
    /**
     * Adds progress charts section (placeholder for future chart implementation)
     */
    private fun addProgressCharts(
        document: Document,
        headerFont: PdfFont,
        bodyFont: PdfFont,
        progressMetrics: ProgressMetrics
    ) {
        // Section title
        val sectionTitle = Paragraph("Progress Charts")
            .setFont(headerFont)
            .setFontSize(HEADER_FONT_SIZE)
            .setFontColor(BRAND_SECONDARY)
            .setBold()
            .setMarginBottom(15f)
        
        document.add(sectionTitle)
        
        // Chart placeholder
        val chartPlaceholder = Paragraph("📊 Visual charts will be displayed here\n\n" +
            "This section will include:\n" +
            "• Volume progression over time\n" +
            "• Strength gains by exercise\n" +
            "• Workout frequency heatmap\n" +
            "• Duration trends analysis")
            .setFont(bodyFont)
            .setFontSize(BODY_FONT_SIZE)
            .setMarginBottom(20f)
        
        document.add(chartPlaceholder)
    }
    
    /**
     * Adds footer section
     */
    private fun addFooter(
        document: Document,
        bodyFont: PdfFont
    ) {
        val footerText = Paragraph("Generated by Liftrix Analytics Engine")
            .setFont(bodyFont)
            .setFontSize(SMALL_FONT_SIZE)
            .setFontColor(TEXT_SECONDARY)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(30f)
        
        document.add(footerText)
    }
    
    /**
     * Creates styled header cell for tables
     */
    private fun createHeaderCell(text: String): Cell {
        return Cell()
            .add(Paragraph(text))
            .setBackgroundColor(BRAND_PRIMARY.copy(0.1f))
            .setFontColor(BRAND_SECONDARY)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(8f)
            .setBorder(SolidBorder(BRAND_PRIMARY, 1f))
    }
    
    /**
     * Creates styled data cell for tables
     */
    private fun createDataCell(text: String): Cell {
        return Cell()
            .add(Paragraph(text))
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(8f)
            .setBorder(SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
    }
    
    /**
     * Creates a copy of DeviceRgb with alpha transparency
     */
    private fun DeviceRgb.copy(alpha: Float): DeviceRgb {
        return DeviceRgb(
            this.colorValue[0] * alpha,
            this.colorValue[1] * alpha,
            this.colorValue[2] * alpha
        )
    }
}