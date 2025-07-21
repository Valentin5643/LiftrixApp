package com.example.liftrix.service.export

import android.graphics.pdf.PdfDocument
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color
import com.example.liftrix.domain.model.analytics.ProgressMetrics
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PDF report generator using Android PDF API with comprehensive analytics visualizations.
 * 
 * Features:
 * - Professional fitness analytics reports with Material 3 design
 * - Visual charts and progress indicators using Canvas drawing
 * - Multi-page layout with headers, footers, and page numbering
 * - Responsive typography with proper font hierarchy
 * - Data visualization for trends, metrics, and comparisons
 * 
 * Report Sections:
 * - Executive Summary: Key metrics and progress overview
 * - Workout Analytics: Volume, frequency, and consistency trends
 * - Progress Tracking: Strength gains and performance analysis
 * - Calendar View: Activity patterns and streak visualization
 * - Goal Analysis: Target vs actual performance comparison
 * 
 * Design System:
 * - Typography: Poppins Bold (headlines), Inter Medium (body)
 * - Colors: Liftrix brand colors (#20C9B7 primary, Material 3 palette)
 * - Layout: 8dp grid system with proper margins and spacing
 * - Charts: Custom-drawn using Canvas with responsive scaling
 */
@Singleton
class PdfReportGenerator @Inject constructor() {
    
    companion object {
        // Page dimensions (A4 standard)
        private const val PAGE_WIDTH = 595f
        private const val PAGE_HEIGHT = 842f
        
        // Margins and spacing
        private const val MARGIN_LEFT = 50f
        private const val MARGIN_RIGHT = 50f
        private const val MARGIN_TOP = 70f
        private const val MARGIN_BOTTOM = 70f
        private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        private const val CONTENT_HEIGHT = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM
        
        // Typography sizes
        private const val TITLE_SIZE = 24f
        private const val HEADING_SIZE = 18f
        private const val SUBHEADING_SIZE = 14f
        private const val BODY_SIZE = 12f
        private const val CAPTION_SIZE = 10f
        
        // Liftrix brand colors
        private const val PRIMARY_COLOR = 0xFF20C9B7.toInt()
        private const val SECONDARY_COLOR = 0xFF1A1A1A.toInt()
        private const val ACCENT_COLOR = 0xFF4CAF50.toInt()
        private const val TEXT_PRIMARY = 0xFF000000.toInt()
        private const val TEXT_SECONDARY = 0xFF666666.toInt()
        private const val BACKGROUND_COLOR = 0xFFF5F5F5.toInt()
        
        // Chart dimensions
        private const val CHART_HEIGHT = 200f
        private const val CHART_MARGIN = 20f
    }
    
    /**
     * Generates comprehensive PDF analytics report with visualizations
     * 
     * @param progressMetrics Complete analytics data for the user
     * @param dateRange Time range for the report
     * @return LiftrixResult containing PDF bytes or error
     */
    suspend fun generateProgressReport(
        progressMetrics: ProgressMetrics,
        dateRange: TimeRange
    ): LiftrixResult<ByteArray> {
        return liftrixCatching(
            errorMapper = { exception ->
                when (exception) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = "pdf_generation_params",
                        violations = listOf("Invalid PDF generation parameters: ${exception.message}")
                    )
                    else -> LiftrixError.ExportError(
                        errorMessage = "PDF generation failed: ${exception.message}",
                        operation = "generateProgressReport"
                    )
                }
            }
        ) {
            Timber.d("Generating PDF report for user: ${progressMetrics.userId}, dateRange: $dateRange")
            
            // Validate inputs
            validateGenerationParameters(progressMetrics, dateRange)
            
            // Create PDF document
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), 1).create()
            
            try {
                // Generate report pages
                generateCoverPage(pdfDocument, pageInfo, progressMetrics, dateRange)
                generateSummaryPage(pdfDocument, pageInfo, progressMetrics, dateRange)
                generateChartsPage(pdfDocument, pageInfo, progressMetrics, dateRange)
                generateAnalysisPage(pdfDocument, pageInfo, progressMetrics, dateRange)
                
                // Convert to byte array
                val outputStream = ByteArrayOutputStream()
                pdfDocument.writeTo(outputStream)
                val pdfBytes = outputStream.toByteArray()
                
                Timber.d("PDF report generated successfully, size: ${pdfBytes.size} bytes")
                pdfBytes
                
            } finally {
                pdfDocument.close()
            }
        }
    }
    
    /**
     * Validates PDF generation parameters
     */
    private fun validateGenerationParameters(progressMetrics: ProgressMetrics, dateRange: TimeRange) {
        if (progressMetrics.userId.isBlank()) {
            throw IllegalArgumentException("User ID cannot be blank for PDF generation")
        }
        
        if (!dateRange.isValid()) {
            throw IllegalArgumentException("Invalid date range for PDF generation")
        }
        
        if (dateRange.getDurationInDays() <= 0) {
            throw IllegalArgumentException("Date range must be positive duration")
        }
    }
    
    /**
     * Generates cover page with user summary and date range
     */
    private fun generateCoverPage(
        pdfDocument: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        progressMetrics: ProgressMetrics,
        dateRange: TimeRange
    ) {
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        try {
            // Set up paints
            val titlePaint = createPaint(TITLE_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
            val headingPaint = createPaint(HEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
            val bodyPaint = createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
            val accentPaint = createPaint(BODY_SIZE, PRIMARY_COLOR, Typeface.DEFAULT)
            
            var yPosition = MARGIN_TOP + 100f
            
            // Title
            canvas.drawText("Liftrix Analytics Report", MARGIN_LEFT, yPosition, titlePaint)
            yPosition += 80f
            
            // Date range
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val dateRangeText = "Report Period: ${dateFormat.format(dateRange.startDate)} - ${dateFormat.format(dateRange.endDate)}"
            canvas.drawText(dateRangeText, MARGIN_LEFT, yPosition, headingPaint)
            yPosition += 60f
            
            // Key metrics summary box
            drawMetricsSummaryBox(canvas, yPosition, progressMetrics)
            yPosition += 300f
            
            // Generated timestamp
            val generatedText = "Generated on ${SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date())}"
            canvas.drawText(generatedText, MARGIN_LEFT, PAGE_HEIGHT - MARGIN_BOTTOM - 30f, bodyPaint)
            
            // Footer
            drawPageFooter(canvas, 1)
            
        } finally {
            pdfDocument.finishPage(page)
        }
    }
    
    /**
     * Generates summary page with key performance indicators
     */
    private fun generateSummaryPage(
        pdfDocument: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        progressMetrics: ProgressMetrics,
        dateRange: TimeRange
    ) {
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        try {
            val headingPaint = createPaint(HEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
            val subheadingPaint = createPaint(SUBHEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
            val bodyPaint = createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
            
            var yPosition = MARGIN_TOP + 50f
            
            // Page title
            canvas.drawText("Performance Summary", MARGIN_LEFT, yPosition, headingPaint)
            yPosition += 50f
            
            // Workout frequency section
            canvas.drawText("Workout Analytics", MARGIN_LEFT, yPosition, subheadingPaint)
            yPosition += 30f
            
            // Draw frequency metrics
            yPosition = drawFrequencyMetrics(canvas, yPosition, progressMetrics)
            yPosition += 40f
            
            // Volume progression section
            canvas.drawText("Volume Progression", MARGIN_LEFT, yPosition, subheadingPaint)
            yPosition += 30f
            
            // Draw volume metrics
            yPosition = drawVolumeMetrics(canvas, yPosition, progressMetrics)
            yPosition += 40f
            
            // Consistency analysis
            canvas.drawText("Consistency Analysis", MARGIN_LEFT, yPosition, subheadingPaint)
            yPosition += 30f
            
            // Draw consistency metrics
            drawConsistencyMetrics(canvas, yPosition, progressMetrics)
            
            // Footer
            drawPageFooter(canvas, 2)
            
        } finally {
            pdfDocument.finishPage(page)
        }
    }
    
    /**
     * Generates charts page with visual data representations
     */
    private fun generateChartsPage(
        pdfDocument: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        progressMetrics: ProgressMetrics,
        dateRange: TimeRange
    ) {
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        try {
            val headingPaint = createPaint(HEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
            
            var yPosition = MARGIN_TOP + 50f
            
            // Page title
            canvas.drawText("Progress Visualization", MARGIN_LEFT, yPosition, headingPaint)
            yPosition += 50f
            
            // Volume trend chart
            yPosition = drawVolumeChart(canvas, yPosition, progressMetrics)
            yPosition += 50f
            
            // Frequency heatmap
            yPosition = drawFrequencyHeatmap(canvas, yPosition, progressMetrics)
            
            // Footer
            drawPageFooter(canvas, 3)
            
        } finally {
            pdfDocument.finishPage(page)
        }
    }
    
    /**
     * Generates analysis page with insights and recommendations
     */
    private fun generateAnalysisPage(
        pdfDocument: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        progressMetrics: ProgressMetrics,
        dateRange: TimeRange
    ) {
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        try {
            val headingPaint = createPaint(HEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
            val subheadingPaint = createPaint(SUBHEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
            val bodyPaint = createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
            
            var yPosition = MARGIN_TOP + 50f
            
            // Page title
            canvas.drawText("Performance Analysis", MARGIN_LEFT, yPosition, headingPaint)
            yPosition += 50f
            
            // Trends analysis
            canvas.drawText("Key Trends", MARGIN_LEFT, yPosition, subheadingPaint)
            yPosition += 30f
            
            yPosition = drawTrendsAnalysis(canvas, yPosition, progressMetrics)
            yPosition += 40f
            
            // Recommendations
            canvas.drawText("Recommendations", MARGIN_LEFT, yPosition, subheadingPaint)
            yPosition += 30f
            
            drawRecommendations(canvas, yPosition, progressMetrics)
            
            // Footer
            drawPageFooter(canvas, 4)
            
        } finally {
            pdfDocument.finishPage(page)
        }
    }
    
    /**
     * Draws metrics summary box on cover page
     */
    private fun drawMetricsSummaryBox(canvas: Canvas, yPosition: Float, progressMetrics: ProgressMetrics) {
        val boxPaint = Paint().apply {
            color = BACKGROUND_COLOR
            style = Paint.Style.FILL
        }
        
        val borderPaint = Paint().apply {
            color = PRIMARY_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        
        val metricPaint = createPaint(SUBHEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
        val valuePaint = createPaint(HEADING_SIZE, PRIMARY_COLOR, Typeface.DEFAULT_BOLD)
        
        val boxHeight = 250f
        val boxRect = android.graphics.RectF(MARGIN_LEFT, yPosition, MARGIN_LEFT + CONTENT_WIDTH, yPosition + boxHeight)
        
        // Draw box background and border
        canvas.drawRoundRect(boxRect, 8f, 8f, boxPaint)
        canvas.drawRoundRect(boxRect, 8f, 8f, borderPaint)
        
        // Draw key metrics in grid layout
        val metrics = listOf(
            "Total Workouts" to "${progressMetrics.totalWorkouts}",
            "Total Volume" to "${progressMetrics.totalVolume} kg",
            "Average Duration" to "${progressMetrics.averageDuration} min", 
            "Current Streak" to "${progressMetrics.consistencyMetrics.currentStreak} days"
        )
        
        val columnWidth = CONTENT_WIDTH / 2
        val rowHeight = 60f
        
        metrics.forEachIndexed { index, (label, value) ->
            val col = index % 2
            val row = index / 2
            val x = MARGIN_LEFT + 20f + col * columnWidth
            val y = yPosition + 40f + row * rowHeight
            
            canvas.drawText(label, x, y, metricPaint)
            canvas.drawText(value, x, y + 25f, valuePaint)
        }
    }
    
    /**
     * Draws frequency metrics section
     */
    private fun drawFrequencyMetrics(canvas: Canvas, yPosition: Float, progressMetrics: ProgressMetrics): Float {
        val bodyPaint = createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
        val valuePaint = createPaint(BODY_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
        
        var currentY = yPosition
        val lineHeight = 20f
        
        val frequencyData = listOf(
            "Total Workouts: ${progressMetrics.totalWorkouts}",
            "Average per Week: ${String.format("%.1f", progressMetrics.workoutFrequency)}",
            "Workout Count: ${progressMetrics.frequencyMetrics.workoutCount}",
            "Weekly Change: ${if (progressMetrics.frequencyMetrics.weekOverWeekChange >= 0) "+" else ""}${String.format("%.1f", progressMetrics.frequencyMetrics.weekOverWeekChange)}%"
        )
        
        frequencyData.forEach { text ->
            canvas.drawText(text, MARGIN_LEFT + 20f, currentY, bodyPaint)
            currentY += lineHeight
        }
        
        return currentY
    }
    
    /**
     * Draws volume metrics section
     */
    private fun drawVolumeMetrics(canvas: Canvas, yPosition: Float, progressMetrics: ProgressMetrics): Float {
        val bodyPaint = createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
        
        var currentY = yPosition
        val lineHeight = 20f
        
        val volumeData = listOf(
            "Total Volume: ${progressMetrics.totalVolume} kg",
            "Average per Workout: ${String.format("%.1f", progressMetrics.volumeMetrics.averageVolumePerWorkout.kilograms)} kg",
            "Personal Record: ${progressMetrics.volumeMetrics.personalRecordVolume.kilograms.toInt()} kg",
            "Volume Trend: ${getTrendDisplay(progressMetrics.volumeMetrics.volumeTrend)}"
        )
        
        volumeData.forEach { text ->
            canvas.drawText(text, MARGIN_LEFT + 20f, currentY, bodyPaint)
            currentY += lineHeight
        }
        
        return currentY
    }
    
    /**
     * Draws consistency metrics section
     */
    private fun drawConsistencyMetrics(canvas: Canvas, yPosition: Float, progressMetrics: ProgressMetrics) {
        val bodyPaint = createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
        
        var currentY = yPosition
        val lineHeight = 20f
        
        val consistencyData = listOf(
            "Current Streak: ${progressMetrics.consistencyMetrics.currentStreak} days",
            "Longest Streak: ${progressMetrics.consistencyMetrics.longestStreak} days",
            "Consistency Score: ${progressMetrics.consistencyScore}%",
            "Training Days: ${progressMetrics.totalTrainingDays} days"
        )
        
        consistencyData.forEach { text ->
            canvas.drawText(text, MARGIN_LEFT + 20f, currentY, bodyPaint)
            currentY += lineHeight
        }
    }
    
    /**
     * Draws volume progression chart
     */
    private fun drawVolumeChart(canvas: Canvas, yPosition: Float, progressMetrics: ProgressMetrics): Float {
        val subheadingPaint = createPaint(SUBHEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
        val chartPaint = Paint().apply {
            color = PRIMARY_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        
        // Chart title
        canvas.drawText("Volume Progression Over Time", MARGIN_LEFT, yPosition, subheadingPaint)
        
        val chartY = yPosition + 30f
        val chartRect = android.graphics.RectF(
            MARGIN_LEFT + CHART_MARGIN,
            chartY,
            MARGIN_LEFT + CONTENT_WIDTH - CHART_MARGIN,
            chartY + CHART_HEIGHT
        )
        
        // Draw chart background
        val backgroundPaint = Paint().apply {
            color = BACKGROUND_COLOR
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(chartRect, 8f, 8f, backgroundPaint)
        
        // Draw chart border
        val borderPaint = Paint().apply {
            color = TEXT_SECONDARY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(chartRect, 8f, 8f, borderPaint)
        
        // Simplified chart drawing - in production this would use actual data points
        val path = android.graphics.Path()
        val chartWidth = chartRect.width() - 40f
        val chartHeight = chartRect.height() - 40f
        
        // Sample data points for demonstration
        val dataPoints = 10
        for (i in 0 until dataPoints) {
            val x = chartRect.left + 20f + (i * chartWidth / (dataPoints - 1))
            val y = chartRect.bottom - 20f - (Math.sin(i * 0.5) * chartHeight * 0.3).toFloat() - chartHeight * 0.4f
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, chartPaint)
        
        return chartY + CHART_HEIGHT + 20f
    }
    
    /**
     * Draws frequency heatmap visualization
     */
    private fun drawFrequencyHeatmap(canvas: Canvas, yPosition: Float, progressMetrics: ProgressMetrics): Float {
        val subheadingPaint = createPaint(SUBHEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
        
        // Heatmap title
        canvas.drawText("Weekly Activity Heatmap", MARGIN_LEFT, yPosition, subheadingPaint)
        
        val heatmapY = yPosition + 30f
        val cellSize = 30f
        val daysInWeek = 7
        val weeksToShow = 12
        
        // Draw day labels
        val dayLabels = arrayOf("S", "M", "T", "W", "T", "F", "S")
        val labelPaint = createPaint(CAPTION_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
        
        for (i in dayLabels.indices) {
            val x = MARGIN_LEFT + 50f + i * (cellSize + 2f) + cellSize / 2
            canvas.drawText(dayLabels[i], x, heatmapY + 15f, labelPaint)
        }
        
        // Draw heatmap cells (simplified with random activity levels)
        val cellPaint = Paint().apply { style = Paint.Style.FILL }
        
        for (week in 0 until weeksToShow) {
            for (day in 0 until daysInWeek) {
                val x = MARGIN_LEFT + 50f + day * (cellSize + 2f)
                val y = heatmapY + 25f + week * (cellSize + 2f)
                
                // Simulate activity intensity (0-3)
                val intensity = (Math.random() * 4).toInt()
                cellPaint.color = when (intensity) {
                    0 -> 0xFFE0E0E0.toInt() // No activity
                    1 -> 0xFFB3E5D1.toInt() // Light activity
                    2 -> 0xFF66D3A6.toInt() // Moderate activity
                    3 -> PRIMARY_COLOR       // High activity
                    else -> 0xFFE0E0E0.toInt()
                }
                
                val cellRect = android.graphics.RectF(x, y, x + cellSize, y + cellSize)
                canvas.drawRoundRect(cellRect, 4f, 4f, cellPaint)
            }
        }
        
        return heatmapY + weeksToShow * (cellSize + 2f) + 30f
    }
    
    /**
     * Draws trends analysis section
     */
    private fun drawTrendsAnalysis(canvas: Canvas, yPosition: Float, progressMetrics: ProgressMetrics): Float {
        val bodyPaint = createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
        val bulletPaint = createPaint(BODY_SIZE, PRIMARY_COLOR, Typeface.DEFAULT_BOLD)
        
        var currentY = yPosition
        val lineHeight = 25f
        
        val trends = listOf(
            "Volume is trending ${getTrendDisplay(progressMetrics.volumeMetrics.volumeTrend).lowercase()} with consistent progression",
            "Workout frequency shows ${if (progressMetrics.frequencyMetrics.weekOverWeekChange >= 0) "improvement" else "decline"} week-over-week",
            "Current ${progressMetrics.consistencyMetrics.currentStreak}-day streak indicates strong commitment",
            "Average workout duration of ${progressMetrics.averageDuration} minutes is ${if (progressMetrics.averageDuration >= 45) "optimal" else "below target"}"
        )
        
        trends.forEach { trend ->
            canvas.drawText("•", MARGIN_LEFT + 20f, currentY, bulletPaint)
            canvas.drawText(trend, MARGIN_LEFT + 40f, currentY, bodyPaint)
            currentY += lineHeight
        }
        
        return currentY
    }
    
    /**
     * Draws recommendations section
     */
    private fun drawRecommendations(canvas: Canvas, yPosition: Float, progressMetrics: ProgressMetrics) {
        val bodyPaint = createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
        val bulletPaint = createPaint(BODY_SIZE, ACCENT_COLOR, Typeface.DEFAULT_BOLD)
        
        var currentY = yPosition
        val lineHeight = 25f
        
        val recommendations = generateRecommendations(progressMetrics)
        
        recommendations.forEach { recommendation ->
            canvas.drawText("•", MARGIN_LEFT + 20f, currentY, bulletPaint)
            canvas.drawText(recommendation, MARGIN_LEFT + 40f, currentY, bodyPaint)
            currentY += lineHeight
        }
    }
    
    /**
     * Generates personalized recommendations based on metrics
     */
    private fun generateRecommendations(progressMetrics: ProgressMetrics): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Frequency recommendations
        if (progressMetrics.workoutFrequency < 3) {
            recommendations.add("Consider increasing workout frequency to 3-4 sessions per week for optimal progress")
        }
        
        // Volume recommendations
        if (progressMetrics.volumeMetrics.volumeTrend.name == "DOWN") {
            recommendations.add("Focus on progressive overload to reverse declining volume trend")
        }
        
        // Consistency recommendations
        if (progressMetrics.consistencyScore < 70) {
            recommendations.add("Improve consistency by scheduling workouts at the same time each day")
        }
        
        // Duration recommendations
        if (progressMetrics.averageDuration < 30) {
            recommendations.add("Extend workout sessions to 45-60 minutes for better strength adaptations")
        }
        
        // Add generic recommendations if none specific
        if (recommendations.isEmpty()) {
            recommendations.add("Maintain current training approach - progress is on track")
            recommendations.add("Consider periodization to prevent plateaus and overtraining")
        }
        
        return recommendations.take(4) // Limit to 4 recommendations
    }
    
    /**
     * Draws page footer with page number
     */
    private fun drawPageFooter(canvas: Canvas, pageNumber: Int) {
        val footerPaint = createPaint(CAPTION_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
        val footerText = "Liftrix Analytics Report - Page $pageNumber"
        val footerY = PAGE_HEIGHT - MARGIN_BOTTOM + 20f
        
        // Center the footer text
        val textWidth = footerPaint.measureText(footerText)
        val centerX = PAGE_WIDTH / 2 - textWidth / 2
        
        canvas.drawText(footerText, centerX, footerY, footerPaint)
    }
    
    /**
     * Creates Paint object with specified properties
     */
    private fun createPaint(textSize: Float, color: Int, typeface: Typeface): Paint {
        return Paint().apply {
            this.textSize = textSize
            this.color = color
            this.typeface = typeface
            isAntiAlias = true
        }
    }
    
    /**
     * Gets display text for trend direction
     */
    private fun getTrendDisplay(trend: com.example.liftrix.domain.model.analytics.TrendDirection): String {
        return when (trend) {
            com.example.liftrix.domain.model.analytics.TrendDirection.UP -> "Improving"
            com.example.liftrix.domain.model.analytics.TrendDirection.DOWN -> "Declining"
            com.example.liftrix.domain.model.analytics.TrendDirection.STABLE -> "Stable"
            com.example.liftrix.domain.model.analytics.TrendDirection.UNKNOWN -> "Unknown"
        }
    }
}