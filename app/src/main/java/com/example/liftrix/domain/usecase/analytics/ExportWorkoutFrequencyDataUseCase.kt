package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Use case for exporting workout frequency data in various formats
 * 
 * Responsibilities:
 * - Export frequency data to PDF format with heatmaps and trends
 * - Export frequency data to CSV format for external analysis
 * - Handle consistency scoring and pattern analysis
 * - Generate performance-optimized exports with insights
 * 
 * Business Rules:
 * - Frequency data includes workout counts, consistency scores, and patterns
 * - PDF exports include visual heatmaps and streak analysis
 * - CSV exports provide raw frequency data with calculated metrics
 * - Export files include time range and frequency patterns
 * - Maximum export processing time of 8 seconds for performance
 */
class ExportWorkoutFrequencyDataUseCase @Inject constructor() {
    
    companion object {
        private const val MAX_EXPORT_TIME_MS = 8_000L
        private const val PDF_FILE_PREFIX = "liftrix_workout_frequency"
        private const val CSV_FILE_PREFIX = "liftrix_frequency_data"
    }
    
    /**
     * Exports workout frequency data to PDF format with visual heatmaps
     * 
     * @param request Export request containing frequency data and formatting options
     * @return LiftrixResult containing generated PDF file or error
     */
    suspend fun exportToPdf(request: ExportWorkoutFrequencyDataRequest): LiftrixResult<File> = withContext(Dispatchers.IO) {
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ExportError(
                    errorMessage = "Failed to export workout frequency data to PDF: ${throwable.message}",
                    operation = "EXPORT_FREQUENCY_PDF"
                )
            }
        ) {
            val startTime = System.currentTimeMillis()
            Timber.d("Starting workout frequency PDF export for ${request.frequencyPoints.size} data points")
            
            // Validate export request
            validateExportRequest(request)
            
            // Prepare export data with frequency analysis
            val exportData = prepareFrequencyExportData(request)
            
            // Generate PDF with frequency heatmaps (simplified implementation)
            val pdfFile = java.io.File.createTempFile(generatePdfFilename(request.timeRange), ".pdf")
            
            // For now, create a text-based report file
            pdfFile.writer().use { writer ->
                writer.write("LIFTRIX WORKOUT FREQUENCY REPORT\n")
                writer.write("=" + "=".repeat(45) + "\n\n")
                writer.write("Time Range: ${request.timeRange.name}\n")
                writer.write("Data Points: ${exportData.frequencyPoints.size}\n")
                writer.write("Export Timestamp: ${java.util.Date(exportData.exportTimestamp)}\n\n")
                
                writer.write("FREQUENCY SUMMARY:\n")
                writer.write("-".repeat(20) + "\n")
                writer.write("Total Workouts: ${exportData.summary.totalWorkouts}\n")
                writer.write("Average Workouts per Week: ${exportData.summary.averageWorkoutsPerWeek}\n")
                writer.write("Consistency Score: ${exportData.summary.averageConsistencyScore}%\n")
                writer.write("Longest Streak: ${exportData.summary.bestConsistencyStreak} days\n\n")
                
                writer.write("FREQUENCY DATA:\n")
                writer.write("-".repeat(18) + "\n")
                exportData.frequencyPoints.sortedBy { it.date }.forEach { point ->
                    writer.write("${point.date} - ${point.workoutCount} workouts\n")
                }
            }
            val executionTime = System.currentTimeMillis() - startTime
            
            Timber.d("Frequency PDF export completed in ${executionTime}ms, file size: ${pdfFile.length()} bytes")
            
            if (executionTime > MAX_EXPORT_TIME_MS) {
                Timber.w("Frequency PDF export exceeded ${MAX_EXPORT_TIME_MS}ms target: ${executionTime}ms")
            }
            
            pdfFile
        }
    }
    
    /**
     * Exports workout frequency data to CSV format for external analysis
     * 
     * @param request Export request containing frequency data and formatting options
     * @return LiftrixResult containing generated CSV file or error
     */
    suspend fun exportToCsv(request: ExportWorkoutFrequencyDataRequest): LiftrixResult<File> = withContext(Dispatchers.IO) {
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ExportError(
                    errorMessage = "Failed to export workout frequency data to CSV: ${throwable.message}",
                    operation = "EXPORT_FREQUENCY_CSV"
                )
            }
        ) {
            val startTime = System.currentTimeMillis()
            Timber.d("Starting frequency CSV export for ${request.frequencyPoints.size} data points")
            
            // Validate export request
            validateExportRequest(request)
            
            // Prepare CSV data structure
            val csvData = prepareFrequencyCsvData(request)
            
            // Generate CSV file (simplified implementation)
            val csvFile = java.io.File.createTempFile(generateCsvFilename(request.timeRange), ".csv")
            
            csvFile.writer().use { writer ->
                // Write headers
                writer.write(csvData.headers.joinToString(",") + "\n")
                
                // Write data rows
                csvData.rows.forEach { row ->
                    writer.write(row.joinToString(",") + "\n")
                }
            }
            val executionTime = System.currentTimeMillis() - startTime
            
            Timber.d("Frequency CSV export completed in ${executionTime}ms, file size: ${csvFile.length()} bytes")
            
            if (executionTime > MAX_EXPORT_TIME_MS) {
                Timber.w("Frequency CSV export exceeded ${MAX_EXPORT_TIME_MS}ms target: ${executionTime}ms")
            }
            
            csvFile
        }
    }
    
    /**
     * Validates the frequency export request parameters
     */
    private fun validateExportRequest(request: ExportWorkoutFrequencyDataRequest) {
        if (request.frequencyPoints.isEmpty()) {
            throw IllegalArgumentException("Cannot export empty workout frequency data")
        }
        
        if (request.frequencyPoints.size > 5000) {
            throw IllegalArgumentException("Frequency export data size exceeds maximum limit (5,000 points)")
        }
        
        // Validate that frequency points have valid data
        val invalidPoints = request.frequencyPoints.filter { it.workoutCount < 0 }
        if (invalidPoints.isNotEmpty()) {
            throw IllegalArgumentException("Invalid frequency data points detected: ${invalidPoints.size} points")
        }
    }
    
    /**
     * Prepares frequency data for PDF export with analysis and metadata
     */
    private fun prepareFrequencyExportData(request: ExportWorkoutFrequencyDataRequest): FrequencyExportData {
        return FrequencyExportData(
            frequencyPoints = request.frequencyPoints,
            timeRange = request.timeRange,
            includeHeatmap = request.includeHeatmap,
            includeTrends = request.includeTrends,
            summary = calculateFrequencySummary(request.frequencyPoints),
            patterns = analyzeWorkoutPatterns(request.frequencyPoints),
            streaks = calculateWorkoutStreaks(request.frequencyPoints),
            exportTimestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Prepares CSV data structure for frequency export
     */
    private fun prepareFrequencyCsvData(request: ExportWorkoutFrequencyDataRequest): FrequencyCsvData {
        val headers = mutableListOf(
            "Date",
            "Day of Week",
            "Workout Count",
            "Duration Minutes",
            "Consistency Score"
        )
        
        if (request.includeTrends) {
            headers.addAll(listOf(
                "Weekly Average",
                "Monthly Average",
                "Trend Direction"
            ))
        }
        
        val rows = request.frequencyPoints.mapIndexed { index, point ->
            val baseRow = mutableListOf(
                point.date.toString(),
                point.dayOfWeek,
                point.workoutCount.toString(),
                point.durationMinutes.toString(),
                point.consistencyScore.toString()
            )
            
            if (request.includeTrends) {
                val weeklyAverage = calculateWeeklyAverage(request.frequencyPoints, index)
                val monthlyAverage = calculateMonthlyAverage(request.frequencyPoints, index)
                val trendDirection = determineTrendDirection(request.frequencyPoints, index)
                
                baseRow.addAll(listOf(
                    weeklyAverage.toString(),
                    monthlyAverage.toString(),
                    trendDirection
                ))
            }
            
            baseRow
        }
        
        return FrequencyCsvData(headers = headers, rows = rows)
    }
    
    /**
     * Calculates summary statistics for workout frequency
     */
    private fun calculateFrequencySummary(frequencyPoints: List<WorkoutFrequencyDataPoint>): FrequencySummary {
        if (frequencyPoints.isEmpty()) {
            return FrequencySummary(
                totalWorkouts = 0,
                averageWorkoutsPerWeek = 0f,
                averageWorkoutsPerMonth = 0f,
                bestConsistencyStreak = 0,
                currentStreak = 0,
                averageConsistencyScore = 0f,
                mostActiveDay = null,
                dataPointCount = 0
            )
        }
        
        val totalWorkouts = frequencyPoints.sumOf { it.workoutCount }
        val averageConsistencyScore = frequencyPoints.map { it.consistencyScore }.average().toFloat()
        
        // Calculate time-based averages
        val timeSpanDays = frequencyPoints.size
        val averageWorkoutsPerWeek = (totalWorkouts.toFloat() / timeSpanDays) * 7f
        val averageWorkoutsPerMonth = (totalWorkouts.toFloat() / timeSpanDays) * 30f
        
        // Find most active day
        val dayFrequency = frequencyPoints.groupBy { it.dayOfWeek }
            .mapValues { (_, points) -> points.sumOf { it.workoutCount } }
        val mostActiveDay = dayFrequency.maxByOrNull { it.value }?.key
        
        // Calculate streaks
        val streaks = calculateWorkoutStreaks(frequencyPoints)
        
        return FrequencySummary(
            totalWorkouts = totalWorkouts,
            averageWorkoutsPerWeek = averageWorkoutsPerWeek,
            averageWorkoutsPerMonth = averageWorkoutsPerMonth,
            bestConsistencyStreak = streaks.maxByOrNull { it.length }?.length ?: 0,
            currentStreak = streaks.lastOrNull()?.length ?: 0,
            averageConsistencyScore = averageConsistencyScore,
            mostActiveDay = mostActiveDay,
            dataPointCount = frequencyPoints.size
        )
    }
    
    /**
     * Analyzes workout patterns and identifies trends
     */
    private fun analyzeWorkoutPatterns(frequencyPoints: List<WorkoutFrequencyDataPoint>): WorkoutPatterns {
        val dayOfWeekCounts = frequencyPoints.groupBy { it.dayOfWeek }
            .mapValues { (_, points) -> points.sumOf { it.workoutCount } }
        
        val preferredDays = dayOfWeekCounts.toList().sortedByDescending { it.second }.take(3).map { it.first }
        
        // Analyze time trends
        val sortedPoints = frequencyPoints.sortedBy { it.date }
        val firstHalf = sortedPoints.take(sortedPoints.size / 2)
        val secondHalf = sortedPoints.drop(sortedPoints.size / 2)
        
        val firstHalfAvg = firstHalf.map { it.workoutCount }.average().toFloat()
        val secondHalfAvg = secondHalf.map { it.workoutCount }.average().toFloat()
        
        val overallTrend = when {
            secondHalfAvg > firstHalfAvg * 1.1f -> "Increasing"
            secondHalfAvg < firstHalfAvg * 0.9f -> "Decreasing"
            else -> "Stable"
        }
        
        return WorkoutPatterns(
            preferredDays = preferredDays,
            overallTrend = overallTrend,
            trendStrength = kotlin.math.abs(secondHalfAvg - firstHalfAvg) / firstHalfAvg,
            dayOfWeekDistribution = dayOfWeekCounts
        )
    }
    
    /**
     * Calculates workout streaks from frequency data
     */
    private fun calculateWorkoutStreaks(frequencyPoints: List<WorkoutFrequencyDataPoint>): List<WorkoutStreak> {
        val streaks = mutableListOf<WorkoutStreak>()
        var currentStreak: WorkoutStreak? = null
        
        frequencyPoints.sortedBy { it.date }.forEach { point ->
            if (point.workoutCount > 0) {
                if (currentStreak == null) {
                    currentStreak = WorkoutStreak(
                        startDate = point.date,
                        endDate = point.date,
                        length = 1
                    )
                } else {
                    currentStreak = currentStreak!!.copy(
                        endDate = point.date,
                        length = currentStreak!!.length + 1
                    )
                }
            } else {
                // Streak broken
                if (currentStreak != null && currentStreak!!.length > 0) {
                    streaks.add(currentStreak!!)
                    currentStreak = null
                }
            }
        }
        
        // Add final streak if exists
        if (currentStreak != null && currentStreak!!.length > 0) {
            streaks.add(currentStreak!!)
        }
        
        return streaks
    }
    
    /**
     * Calculates weekly average for trend analysis
     */
    private fun calculateWeeklyAverage(frequencyPoints: List<WorkoutFrequencyDataPoint>, currentIndex: Int): Float {
        val startIndex = maxOf(0, currentIndex - 6)
        val weekData = frequencyPoints.subList(startIndex, currentIndex + 1)
        return weekData.map { it.workoutCount }.average().toFloat()
    }
    
    /**
     * Calculates monthly average for trend analysis
     */
    private fun calculateMonthlyAverage(frequencyPoints: List<WorkoutFrequencyDataPoint>, currentIndex: Int): Float {
        val startIndex = maxOf(0, currentIndex - 29)
        val monthData = frequencyPoints.subList(startIndex, currentIndex + 1)
        return monthData.map { it.workoutCount }.average().toFloat()
    }
    
    /**
     * Determines trend direction for a specific point
     */
    private fun determineTrendDirection(frequencyPoints: List<WorkoutFrequencyDataPoint>, currentIndex: Int): String {
        if (currentIndex < 6) return "Insufficient Data"
        
        val recentAvg = calculateWeeklyAverage(frequencyPoints, currentIndex)
        val previousAvg = calculateWeeklyAverage(frequencyPoints, currentIndex - 7)
        
        return when {
            recentAvg > previousAvg * 1.05f -> "Increasing"
            recentAvg < previousAvg * 0.95f -> "Decreasing"
            else -> "Stable"
        }
    }
    
    /**
     * Generates filename for CSV export
     */
    private fun generateCsvFilename(timeRange: TimeRangeType): String {
        val timestamp = System.currentTimeMillis()
        return "${CSV_FILE_PREFIX}_${timeRange.name.lowercase()}_${timestamp}.csv"
    }
    
    /**
     * Generates filename for PDF export
     */
    private fun generatePdfFilename(timeRange: TimeRangeType): String {
        val timestamp = System.currentTimeMillis()
        return "${PDF_FILE_PREFIX}_${timeRange.name.lowercase()}_${timestamp}.pdf"
    }
}

/**
 * Request data class for workout frequency data export operations
 */
data class ExportWorkoutFrequencyDataRequest(
    val frequencyPoints: List<WorkoutFrequencyDataPoint>,
    val timeRange: TimeRangeType,
    val includeHeatmap: Boolean = true,
    val includeTrends: Boolean = true
)

/**
 * Data structure for PDF export containing formatted frequency data
 */
data class FrequencyExportData(
    val frequencyPoints: List<WorkoutFrequencyDataPoint>,
    val timeRange: TimeRangeType,
    val includeHeatmap: Boolean,
    val includeTrends: Boolean,
    val summary: FrequencySummary,
    val patterns: WorkoutPatterns,
    val streaks: List<WorkoutStreak>,
    val exportTimestamp: Long
)

/**
 * Data structure for CSV export containing tabular frequency data
 */
data class FrequencyCsvData(
    val headers: List<String>,
    val rows: List<List<String>>
)

/**
 * Summary statistics for workout frequency analysis
 */
data class FrequencySummary(
    val totalWorkouts: Int,
    val averageWorkoutsPerWeek: Float,
    val averageWorkoutsPerMonth: Float,
    val bestConsistencyStreak: Int,
    val currentStreak: Int,
    val averageConsistencyScore: Float,
    val mostActiveDay: String?,
    val dataPointCount: Int
)

/**
 * Analysis of workout patterns and trends
 */
data class WorkoutPatterns(
    val preferredDays: List<String>,
    val overallTrend: String,
    val trendStrength: Float,
    val dayOfWeekDistribution: Map<String, Int>
)

/**
 * Represents a workout consistency streak
 */
data class WorkoutStreak(
    val startDate: kotlinx.datetime.LocalDate,
    val endDate: kotlinx.datetime.LocalDate,
    val length: Int
)

/**
 * Represents a single workout frequency data point for export
 */
data class WorkoutFrequencyDataPoint(
    val date: kotlinx.datetime.LocalDate,
    val dayOfWeek: String,
    val workoutCount: Int,
    val durationMinutes: Int,
    val consistencyScore: Float
)