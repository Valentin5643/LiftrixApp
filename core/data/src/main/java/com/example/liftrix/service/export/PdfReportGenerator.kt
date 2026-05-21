package com.example.liftrix.service.export

import android.graphics.pdf.PdfDocument
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.Color
import com.example.liftrix.domain.model.analytics.StrengthForecastPointType
import com.example.liftrix.domain.model.analytics.ProgressMetrics
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.export.ProgressReportData
import kotlinx.datetime.daysUntil
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
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
        private val REPORT_LOCALE: Locale = Locale.ENGLISH
        
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

    suspend fun generateChartlessProgressReport(
        reportData: ProgressReportData
    ): LiftrixResult<ByteArray> {
        return liftrixCatching(
            errorMapper = { exception ->
                LiftrixError.ExportError(
                    errorMessage = "PDF generation failed: ${exception.message}",
                    operation = "generateChartlessProgressReport",
                    format = "PDF"
                )
            }
        ) {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), 1).create()
            try {
                drawProgressSummaryPage(pdfDocument, pageInfo, reportData)
                drawProgressStrengthPage(pdfDocument, pageInfo, reportData)
                drawProgressVolumePage(pdfDocument, pageInfo, reportData)
                drawProgressConsistencyPage(pdfDocument, pageInfo, reportData)
                drawProgressRecordsPage(pdfDocument, pageInfo, reportData)
                drawProgressCoachPage(pdfDocument, pageInfo, reportData)
                drawProgressStrengthForecastPage(pdfDocument, pageInfo, reportData)

                val outputStream = ByteArrayOutputStream()
                pdfDocument.writeTo(outputStream)
                outputStream.toByteArray()
            } finally {
                pdfDocument.close()
            }
        }
    }

    private fun drawProgressSummaryPage(
        pdfDocument: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        data: ProgressReportData
    ) {
        val page = pdfDocument.startPage(pageInfo)
        try {
            val canvas = page.canvas
            drawProgressPageChrome(canvas, "Liftrix Progress Report", 1)
            var y = MARGIN_TOP + 45f
            y = drawWrappedText(canvas, data.title, MARGIN_LEFT, y, CONTENT_WIDTH, createPaint(TITLE_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD), 30f)
            y += 14f
            y = drawKeyValue(canvas, "Generated", data.generatedAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", REPORT_LOCALE)), y)
            y = drawKeyValue(canvas, "Range", "${formatReportDate(data.range.start)} to ${formatReportDate(data.range.end)}", y)
            y = drawKeyValue(canvas, "Source", "Local device data - Not synced to cloud", y)
            y = drawKeyValue(canvas, "Sync", formatSyncStatus(data), y)
            y = drawKeyValue(canvas, "Privacy", data.privacyApplied.joinToString(", "), y)
            y += 20f
            val sessionLabel = if (data.summary.rawWorkoutEntries > data.summary.workoutsCompleted) {
                "${data.summary.workoutsCompleted} sessions (${data.summary.rawWorkoutEntries} entries)"
            } else {
                data.summary.workoutsCompleted.toString()
            }
            val metrics = listOf(
                "Sessions" to sessionLabel,
                "Weighted volume" to formatVolumeSummary(data),
                "Unique active days" to data.summary.activeTrainingDays.toString(),
                "PR events" to data.summary.newPersonalRecords.toString(),
                "Best streak" to "${data.summary.bestStreakDays} days",
                "1RM progress" to data.summary.oneRmStatusLabel
            )
            drawMetricGrid(canvas, y, metrics)
        } finally {
            pdfDocument.finishPage(page)
        }
    }

    private fun drawProgressStrengthPage(
        pdfDocument: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        data: ProgressReportData
    ) {
        val page = pdfDocument.startPage(pageInfo)
        try {
            val canvas = page.canvas
            drawProgressPageChrome(canvas, "Strength Progress", 2)
            var y = MARGIN_TOP + 55f
            y = drawTable(
                canvas = canvas,
                yPosition = y,
                headers = listOf("Exercise", "Start 1RM", "Best 1RM", "Gain"),
                rows = data.strengthRows.map {
                    listOf(
                        it.exerciseName,
                        formatKg(it.startEstimatedOneRmKg, 1),
                        formatKg(it.bestEstimatedOneRmKg, 1),
                        if (it.improvementKg > 0.0) formatKg(it.improvementKg, 1) else "Baseline"
                    )
                }.ifEmpty { listOf(listOf("Log same weighted exercise in 2+ sessions", "-", "-", "-")) }
            )
            y += 24f
            drawWrappedText(
                canvas,
                data.strengthRows.firstOrNull { it.improvementKg > 0.0 }?.let { "Top strength improvement came from ${it.exerciseName}, with an estimated ${formatKg(it.improvementKg, 1)} increase." }
                    ?: data.summary.estimatedOneRmBaselineKg?.let { "Baseline estimated 1RM is ${formatKg(it, 1)}. Log this lift again to calculate progress." }
                    ?: "Log weighted exercises in the 1-10 rep range to see estimated 1RM progress.",
                MARGIN_LEFT,
                y,
                CONTENT_WIDTH,
                createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT),
                18f
            )
        } finally {
            pdfDocument.finishPage(page)
        }
    }

    private fun drawProgressVolumePage(
        pdfDocument: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        data: ProgressReportData
    ) {
        val page = pdfDocument.startPage(pageInfo)
        try {
            val canvas = page.canvas
            drawProgressPageChrome(canvas, "Training Volume Analysis", 3)
            var y = MARGIN_TOP + 55f
            y = drawTable(
                canvas,
                y,
                listOf("Week", "Sessions", "Unique active days", "Weighted volume", "Reps", "Sets"),
                data.weeklyVolumeRows.map {
                    listOf(
                        it.weekLabel,
                        it.workoutCount.toString(),
                        it.activeDays.toString(),
                        formatWeightedVolume(it.totalVolumeKg, it.repCount),
                        it.repCount.takeIf { reps -> reps > 0 }?.toString() ?: "-",
                        it.setCount.toString()
                    )
                }.ifEmpty { listOf(listOf("No weekly activity", "-", "-", "-", "-", "-")) }
            )
            y += 24f
            y = drawTable(
                canvas,
                y,
                listOf("Muscle group", "Weighted volume", "Reps", "Exercises", "Sets"),
                data.muscleGroupRows.map {
                    listOf(
                        it.muscleGroup,
                        formatWeightedVolume(it.totalVolumeKg, it.repCount),
                        it.repCount.takeIf { reps -> reps > 0 }?.toString() ?: "-",
                        it.exerciseCount.toString(),
                        it.setCount.toString()
                    )
                }.ifEmpty { listOf(listOf("No muscle mapping available", "-", "-", "-", "-")) }
            )
            y += 20f
            drawWrappedText(
                canvas,
                if (data.summary.totalVolumeKg <= 0.0 && data.summary.totalReps > 0) {
                    "No external weight logged - showing rep-based activity from local completed sets."
                } else {
                    "Weighted volume is calculated from completed sets with external load. Reps and sets remain visible for bodyweight or unweighted activity."
                },
                MARGIN_LEFT,
                y,
                CONTENT_WIDTH,
                createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT),
                18f
            )
        } finally {
            pdfDocument.finishPage(page)
        }
    }

    private fun drawProgressStrengthForecastPage(
        pdfDocument: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        data: ProgressReportData
    ) {
        val page = pdfDocument.startPage(pageInfo)
        try {
            val canvas = page.canvas
            drawProgressPageChrome(canvas, "Strength Forecast", 7)
            var y = MARGIN_TOP + 55f
            val section = data.strengthForecast
            y = drawWrappedText(
                canvas,
                section?.generatedForExerciseName?.let { "Forecast exercise: $it" } ?: "Strength Forecast",
                MARGIN_LEFT,
                y,
                CONTENT_WIDTH,
                createPaint(HEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD),
                24f
            )
            y += 12f
            val forecast = section?.forecast
            if (forecast == null || forecast.forecastPoints.isEmpty()) {
                drawWrappedText(
                    canvas,
                    section?.message ?: "Not enough data to generate forecast",
                    MARGIN_LEFT,
                    y,
                    CONTENT_WIDTH,
                    createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT),
                    18f
                )
                return
            }

            drawForecastGraph(canvas, forecast, y)
            y += CHART_HEIGHT + 36f
            drawWrappedText(
                canvas,
                forecast.summary.message,
                MARGIN_LEFT,
                y,
                CONTENT_WIDTH,
                createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT),
                18f
            )
        } finally {
            pdfDocument.finishPage(page)
        }
    }

    private fun drawForecastGraph(
        canvas: Canvas,
        forecast: com.example.liftrix.domain.model.analytics.StrengthExerciseForecast,
        yPosition: Float
    ) {
        val points = forecast.allPoints
        if (points.isEmpty()) return
        val left = MARGIN_LEFT
        val top = yPosition
        val width = CONTENT_WIDTH
        val height = CHART_HEIGHT
        val values = points.map { it.estimatedOneRmKg }
        val minValue = (values.minOrNull() ?: 0.0) - 2.5
        val maxValue = (values.maxOrNull() ?: 1.0) + 2.5
        val valueRange = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
        val firstDate = points.minOf { it.date }
        val lastDate = points.maxOf { it.date }
        val daySpan = firstDate.daysUntil(lastDate).coerceAtLeast(1)

        fun pointOffset(point: com.example.liftrix.domain.model.analytics.StrengthForecastPoint): android.graphics.PointF {
            val x = left + width * (firstDate.daysUntil(point.date).toFloat() / daySpan.toFloat())
            val y = top + height * (1f - ((point.estimatedOneRmKg - minValue) / valueRange).toFloat())
            return android.graphics.PointF(x, y)
        }

        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE0E0E0.toInt()
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(left, top, left, top + height, axisPaint)
        canvas.drawLine(left, top + height, left + width, top + height, axisPaint)

        val historyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY_COLOR
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val forecastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PRIMARY_COLOR
            strokeWidth = 3f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
        }
        drawForecastPath(canvas, points.filter { it.type == StrengthForecastPointType.HISTORICAL }, historyPaint, ::pointOffset)
        drawForecastPath(canvas, points.filter { it.type == StrengthForecastPointType.FORECAST }, forecastPaint, ::pointOffset)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRIMARY_COLOR }
        points.forEach { point ->
            val offset = pointOffset(point)
            if (point.type == StrengthForecastPointType.HISTORICAL) {
                dotPaint.style = Paint.Style.FILL
                canvas.drawCircle(offset.x, offset.y, 4f, dotPaint)
            } else {
                dotPaint.style = Paint.Style.STROKE
                dotPaint.strokeWidth = 2f
                canvas.drawCircle(offset.x, offset.y, 4f, dotPaint)
            }
        }
    }

    private fun drawForecastPath(
        canvas: Canvas,
        points: List<com.example.liftrix.domain.model.analytics.StrengthForecastPoint>,
        paint: Paint,
        pointOffset: (com.example.liftrix.domain.model.analytics.StrengthForecastPoint) -> android.graphics.PointF
    ) {
        if (points.isEmpty()) return
        val path = Path()
        points.forEachIndexed { index, point ->
            val offset = pointOffset(point)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawProgressConsistencyPage(
        pdfDocument: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        data: ProgressReportData
    ) {
        val page = pdfDocument.startPage(pageInfo)
        try {
            val canvas = page.canvas
            drawProgressPageChrome(canvas, "Consistency", 4)
            var y = MARGIN_TOP + 55f
            y = drawKeyValue(canvas, "Consistency score", "${data.summary.consistencyScore} / 100", y)
            y = drawKeyValue(canvas, "Score window", data.summary.consistencyWindowLabel, y)
            y = drawKeyValue(canvas, "Most active day", data.summary.mostActiveDay ?: "Not enough data", y)
            y = drawKeyValue(canvas, "Unique active days", data.summary.activeTrainingDays.toString(), y)
            y = drawKeyValue(canvas, "Rest days", data.summary.restDays.toString(), y)
            y = drawKeyValue(canvas, "Average workouts per week", formatDecimal(data.summary.averageWorkoutsPerWeek, 1), y)
            data.summary.activeWeekAverageWorkouts?.let { activeAverage ->
                y = drawKeyValue(canvas, "Active-week average", formatDecimal(activeAverage, 1), y)
            }
            y = drawKeyValue(canvas, "Best streak", "${data.summary.bestStreakDays} days", y)
            y = drawKeyValue(
                canvas,
                "Average duration",
                data.summary.averageDurationMinutes?.let { "$it minutes (${data.summary.validDurationWorkoutCount} recorded)" } ?: "Enable duration tracking to see workout length trends",
                y
            )
            y += 18f
            y = drawTable(
                canvas,
                y,
                listOf("Week", "Sessions", "Unique active days"),
                data.consistencyRows.map {
                    listOf(it.weekLabel, it.workoutCount.toString(), it.activeDays.toString())
                }.ifEmpty { listOf(listOf("No consistency data", "-", "-")) }
            )
            y += 20f
            drawWrappedText(
                canvas,
                buildConsistencyInsight(data),
                MARGIN_LEFT,
                y,
                CONTENT_WIDTH,
                createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT),
                18f
            )
        } finally {
            pdfDocument.finishPage(page)
        }
    }

    private fun drawProgressRecordsPage(
        pdfDocument: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        data: ProgressReportData
    ) {
        val page = pdfDocument.startPage(pageInfo)
        try {
            val canvas = page.canvas
            drawProgressPageChrome(canvas, "Personal Records", 5)
            var y = MARGIN_TOP + 55f
            y = drawBadgeRow(canvas, y, data)
            y += 20f
            y = drawTable(
                canvas,
                y,
                listOf("Date", "Exercise", "New PR", "Previous"),
                data.personalRecordRows.map {
                    listOf(formatReportDate(it.date), it.exerciseName, "${it.recordType}: ${it.newValue}", it.previousValue ?: "First recorded")
                }.ifEmpty { listOf(listOf("No new records", "-", "-", "-")) },
                maxRows = 8
            )
            y += 20f
            canvas.drawText("Detailed workouts", MARGIN_LEFT, y, createPaint(SUBHEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD))
            y += 24f
            y = drawTable(
                canvas,
                y,
                listOf("Date", "Workout", "Duration", "Load", "Sets"),
                data.workoutRows.map {
                    listOf(
                        formatReportDate(it.date),
                        formatWorkoutRowName(it.name, it.workoutId),
                        it.durationMinutes?.let { minutes -> "$minutes min" } ?: "-",
                        formatWorkoutRowLoad(it.volumeKg, it.repCount),
                        it.setCount.takeIf { setCount -> setCount > 0 }?.toString() ?: "-"
                    )
                }.ifEmpty { listOf(listOf("Workout list not included", "-", "-", "-")) },
                maxRows = 8
            )
            if (data.workoutRows.size > 8) {
                y += 16f
                drawWrappedText(
                    canvas,
                    "Additional workouts omitted to keep this report within 6 pages.",
                    MARGIN_LEFT,
                    y,
                    CONTENT_WIDTH,
                    createPaint(CAPTION_SIZE, TEXT_SECONDARY, Typeface.DEFAULT),
                    16f
                )
            }
            y += 18f
            drawWrappedText(
                canvas,
                "Estimated 1RM is less reliable above 10 reps.",
                MARGIN_LEFT,
                y,
                CONTENT_WIDTH,
                createPaint(CAPTION_SIZE, TEXT_SECONDARY, Typeface.DEFAULT),
                16f
            )
        } finally {
            pdfDocument.finishPage(page)
        }
    }

    private fun drawProgressCoachPage(
        pdfDocument: PdfDocument,
        pageInfo: PdfDocument.PageInfo,
        data: ProgressReportData
    ) {
        val page = pdfDocument.startPage(pageInfo)
        try {
            val canvas = page.canvas
            drawProgressPageChrome(canvas, "AI Coach Summary", 6)
            var y = MARGIN_TOP + 55f
            y = drawWrappedText(canvas, data.aiSummary.summary, MARGIN_LEFT, y, CONTENT_WIDTH, createPaint(BODY_SIZE, TEXT_PRIMARY, Typeface.DEFAULT), 18f)
            y += 22f
            canvas.drawText("Next week", MARGIN_LEFT, y, createPaint(SUBHEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD))
            y += 24f
            val recommendations = data.aiSummary.recommendations.ifEmpty { listOf("Complete more workouts to unlock deeper insights.") }
            recommendations.take(3).forEachIndexed { index, recommendation ->
                y = drawWrappedText(canvas, "${index + 1}. $recommendation", MARGIN_LEFT + 12f, y, CONTENT_WIDTH - 12f, createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT), 18f)
            }
            y += 20f
            canvas.drawText("Context used", MARGIN_LEFT, y, createPaint(SUBHEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD))
            y += 24f
            data.aiSummary.contextUsed.take(6).forEach { context ->
                y = drawWrappedText(canvas, "- $context", MARGIN_LEFT + 12f, y, CONTENT_WIDTH - 12f, createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT), 18f)
            }
            y += 20f
            drawWrappedText(
                canvas,
                "This local summary is generated from training data on this device and is not medical advice.",
                MARGIN_LEFT,
                y,
                CONTENT_WIDTH,
                createPaint(CAPTION_SIZE, TEXT_SECONDARY, Typeface.DEFAULT),
                16f
            )
        } finally {
            pdfDocument.finishPage(page)
        }
    }

    private fun drawProgressPageChrome(canvas: Canvas, title: String, pageNumber: Int) {
        canvas.drawColor(Color.WHITE)
        val titlePaint = createPaint(HEADING_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
        val accentPaint = Paint().apply {
            color = PRIMARY_COLOR
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH, 14f, accentPaint)
        canvas.drawText(title, MARGIN_LEFT, MARGIN_TOP - 20f, titlePaint)
        drawProgressReportFooter(canvas, pageNumber)
    }

    private fun drawMetricGrid(canvas: Canvas, yPosition: Float, metrics: List<Pair<String, String>>) {
        val cardPaint = Paint().apply {
            color = BACKGROUND_COLOR
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val labelPaint = createPaint(CAPTION_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
        val valuePaint = createPaint(SUBHEADING_SIZE, PRIMARY_COLOR, Typeface.DEFAULT_BOLD)
        val cardWidth = (CONTENT_WIDTH - 18f) / 2f
        val cardHeight = 74f
        metrics.forEachIndexed { index, metric ->
            val column = index % 2
            val row = index / 2
            val left = MARGIN_LEFT + column * (cardWidth + 18f)
            val top = yPosition + row * (cardHeight + 16f)
            canvas.drawRoundRect(android.graphics.RectF(left, top, left + cardWidth, top + cardHeight), 8f, 8f, cardPaint)
            canvas.drawText(metric.first, left + 14f, top + 24f, labelPaint)
            canvas.drawText(metric.second, left + 14f, top + 52f, valuePaint)
        }
    }

    private fun drawKeyValue(canvas: Canvas, label: String, value: String, yPosition: Float): Float {
        canvas.drawText(label, MARGIN_LEFT, yPosition, createPaint(BODY_SIZE, TEXT_SECONDARY, Typeface.DEFAULT_BOLD))
        canvas.drawText(value, MARGIN_LEFT + 190f, yPosition, createPaint(BODY_SIZE, TEXT_PRIMARY, Typeface.DEFAULT))
        return yPosition + 24f
    }

    private fun buildConsistencyInsight(data: ProgressReportData): String {
        return when {
            data.summary.workoutsCompleted == 0 -> ProgressReportData.NO_WORKOUT_DATA_MESSAGE
            data.summary.hasUnusuallyHighWorkoutFrequency -> "This period has unusually high workout frequency; bulk logging or imports may make weekly averages look higher than normal training."
            data.summary.consistencyScore >= 80 -> "Consistency is strong in this range, with regular active days and stable training frequency."
            data.summary.averageWorkoutsPerWeek >= 3.0 -> "Training frequency is strong for this range; keep rest days planned around harder sessions."
            else -> "A repeatable weekly schedule would improve consistency and make future trends easier to read."
        }
    }

    private fun drawTable(
        canvas: Canvas,
        yPosition: Float,
        headers: List<String>,
        rows: List<List<String>>,
        maxRows: Int = 12
    ): Float {
        val headerPaint = createPaint(CAPTION_SIZE, TEXT_PRIMARY, Typeface.DEFAULT_BOLD)
        val bodyPaint = createPaint(CAPTION_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
        val rowPaint = Paint().apply {
            color = 0xFFF1F3F4.toInt()
            style = Paint.Style.FILL
        }
        val columns = headers.size.coerceAtLeast(1)
        val colWidth = CONTENT_WIDTH / columns
        var y = yPosition
        canvas.drawRoundRect(android.graphics.RectF(MARGIN_LEFT, y - 16f, MARGIN_LEFT + CONTENT_WIDTH, y + 10f), 6f, 6f, rowPaint)
        headers.forEachIndexed { index, header ->
            canvas.drawText(ellipsize(header, 22), MARGIN_LEFT + index * colWidth + 6f, y, headerPaint)
        }
        y += 24f
        rows.take(maxRows).forEach { row ->
            row.take(columns).forEachIndexed { index, value ->
                canvas.drawText(ellipsize(value, 24), MARGIN_LEFT + index * colWidth + 6f, y, bodyPaint)
            }
            y += 22f
        }
        return y
    }

    private fun ellipsize(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        return value.take((maxChars - 3).coerceAtLeast(1)) + "..."
    }

    private fun drawBadgeRow(canvas: Canvas, yPosition: Float, data: ProgressReportData): Float {
        val badges = buildList {
            if (data.personalRecordRows.any { it.recordType.contains("ONE", ignoreCase = true) }) add("New Strength PR")
            if (data.personalRecordRows.any { it.recordType.contains("VOLUME", ignoreCase = true) }) add("Volume PR")
            if (data.summary.bestStreakDays >= 3) add("Consistency PR")
            if (isEmpty()) add("No new PR badges")
        }
        val badgePaint = Paint().apply {
            color = 0xFFE8F5F3.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val textPaint = createPaint(CAPTION_SIZE, PRIMARY_COLOR, Typeface.DEFAULT_BOLD)
        var x = MARGIN_LEFT
        badges.forEach { badge ->
            val width = textPaint.measureText(badge) + 24f
            canvas.drawRoundRect(android.graphics.RectF(x, yPosition - 16f, x + width, yPosition + 10f), 12f, 12f, badgePaint)
            canvas.drawText(badge, x + 12f, yPosition, textPaint)
            x += width + 10f
        }
        return yPosition + 24f
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        yPosition: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float
    ): Float {
        var y = yPosition
        var line = ""
        text.split(" ").forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, y, paint)
                y += lineHeight
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun drawProgressReportFooter(canvas: Canvas, pageNumber: Int) {
        val footerPaint = createPaint(CAPTION_SIZE, TEXT_SECONDARY, Typeface.DEFAULT)
        val footerText = "Generated by Liftrix - page $pageNumber / 6"
        val textWidth = footerPaint.measureText(footerText)
        canvas.drawText(footerText, PAGE_WIDTH / 2 - textWidth / 2, PAGE_HEIGHT - MARGIN_BOTTOM + 20f, footerPaint)
    }

    private fun formatSyncStatus(data: ProgressReportData): String {
        val pending = if (data.syncStatus.pendingSyncItems == 0) "No pending sync items" else "${data.syncStatus.pendingSyncItems} pending sync items"
        val lastSync = data.syncStatus.lastSyncTimestampMillis?.let {
            SimpleDateFormat("MMM dd, yyyy", REPORT_LOCALE).format(Date(it))
        } ?: if (data.syncStatus.syncedWorkoutCount > 0) {
            "Synced workouts present; account sync time unavailable"
        } else {
            "Never synced"
        }
        return "$pending, last sync: $lastSync"
    }

    private fun formatDecimal(value: Double, digits: Int): String {
        return String.format(REPORT_LOCALE, "%.${digits}f", value)
    }

    private fun formatKg(value: Double, digits: Int): String {
        return "${formatDecimal(value, digits)} kg"
    }

    private fun formatWorkoutRowName(name: String, workoutId: String): String {
        val suffix = workoutId.takeIf { it.isNotBlank() }?.take(8) ?: return name
        return "$name #$suffix"
    }

    private fun formatWorkoutRowLoad(volumeKg: Double, repCount: Int): String {
        return when {
            volumeKg > 0.0 -> formatKg(volumeKg, 0)
            repCount > 0 -> "$repCount reps"
            else -> "-"
        }
    }

    private fun formatWeightedVolume(weightedVolumeKg: Double, reps: Int): String {
        return when {
            weightedVolumeKg > 0.0 -> formatKg(weightedVolumeKg, 0)
            reps > 0 -> "No external weight"
            else -> "-"
        }
    }

    private fun formatVolumeSummary(data: ProgressReportData): String {
        return when {
            data.summary.totalVolumeKg > 0.0 && data.summary.totalReps > 0 ->
                "${formatKg(data.summary.totalVolumeKg, 0)} + ${data.summary.totalReps} reps"
            data.summary.totalVolumeKg > 0.0 -> formatKg(data.summary.totalVolumeKg, 0)
            data.summary.totalReps > 0 -> "Bodyweight / rep-based activity"
            else -> "No set activity logged"
        }
    }

    private fun formatReportDate(date: java.time.LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", REPORT_LOCALE))
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
