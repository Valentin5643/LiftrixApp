package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.OneRmDataPoint
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated use case for exporting analytics data in various formats.
 *
 * This use case consolidates the following legacy use cases:
 * - ExportAnalyticsUseCase (general analytics export)
 * - ExportOneRmDataUseCase (1RM progression export)
 * - ExportVolumeDataUseCase (volume analysis export)
 * - ExportWorkoutFrequencyDataUseCase (frequency data export)
 *
 * Features:
 * - Unified export interface with ExportType parameter
 * - Multiple format support (CSV, PDF, JSON)
 * - Configurable data filtering and formatting
 * - Proper error handling with LiftrixResult<T>
 * - Performance optimized for large datasets
 * - Consistent file naming and metadata
 *
 * Usage:
 * ```
 * // Export 1RM data to CSV
 * val result = analyticsExportUseCase(
 *     userId = userId,
 *     exportType = ExportType.ONE_RM,
 *     timeRange = TimeRangeType.SIX_MONTHS,
 *     format = ExportFormat.CSV
 * )
 *
 * // Export volume data to PDF
 * val result = analyticsExportUseCase.exportVolume(
 *     request = ExportVolumeDataRequest(...)
 * )
 * ```
 */
@Singleton
class AnalyticsExportUseCase @Inject constructor() {

    companion object {
        private const val MAX_EXPORT_TIME_MS = 10_000L
        private const val ONE_RM_FILE_PREFIX = "liftrix_one_rm"
        private const val VOLUME_FILE_PREFIX = "liftrix_volume"
        private const val FREQUENCY_FILE_PREFIX = "liftrix_frequency"
        private const val ANALYTICS_FILE_PREFIX = "liftrix_analytics"
    }

    /**
     * Unified export operation with type parameter.
     *
     * @param userId User ID for scoping (mandatory)
     * @param exportType Type of analytics data to export
     * @param timeRange Time range for the export
     * @param format Export format (CSV, PDF, JSON)
     * @return LiftrixResult containing the exported file or error
     */
    suspend operator fun invoke(
        userId: String,
        exportType: ExportType,
        timeRange: TimeRangeType,
        format: ExportFormat
    ): LiftrixResult<File> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ExportError(
                    errorMessage = "Failed to export ${exportType.name} data to ${format.name}: ${throwable.message}",
                    operation = "EXPORT_ANALYTICS_${exportType.name}_${format.name}",
                    analyticsContext = mapOf(
                        "userId" to userId,
                        "exportType" to exportType.name,
                        "timeRange" to timeRange.name,
                        "format" to format.name
                    )
                )
            }
        ) {
            val startTime = System.currentTimeMillis()
            Timber.d("Starting ${exportType.name} export to ${format.name} for user: $userId, timeRange: $timeRange")

            // Validate inputs
            validateExportRequest(userId, exportType, timeRange)

            // Route to appropriate export handler
            val file = when (exportType) {
                ExportType.ONE_RM -> exportOneRmData(userId, timeRange, format)
                ExportType.VOLUME -> exportVolumeData(userId, timeRange, format)
                ExportType.FREQUENCY -> exportFrequencyData(userId, timeRange, format)
                ExportType.ALL -> exportAllAnalytics(userId, timeRange, format)
            }

            val executionTime = System.currentTimeMillis() - startTime
            Timber.d("${exportType.name} export completed in ${executionTime}ms, file size: ${file.length()} bytes")

            if (executionTime > MAX_EXPORT_TIME_MS) {
                Timber.w("Export took ${executionTime}ms, exceeding target of ${MAX_EXPORT_TIME_MS}ms")
            }

            file
        }
    }

    /**
     * Exports 1RM progression data.
     *
     * @param request Export configuration with 1RM data points
     * @return LiftrixResult containing the exported file
     */
    suspend fun exportOneRm(request: ExportOneRmDataRequest): LiftrixResult<File> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.FileSystemError(
                    "Failed to export 1RM data: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "EXPORT_ONE_RM",
                        "dataPoints" to request.progressionPoints.size.toString(),
                        "exercises" to request.exerciseNames.size.toString(),
                        "timeRange" to request.timeRange.name
                    )
                )
            }
        ) {
            Timber.d("Exporting ${request.progressionPoints.size} 1RM data points")

            val fileName = "${ONE_RM_FILE_PREFIX}_${getCurrentTimestamp()}"
            when (request.format) {
                ExportFormat.CSV -> exportOneRmToCsv(request, fileName)
                ExportFormat.PDF -> exportOneRmToPdf(request, fileName)
                ExportFormat.JSON -> exportOneRmToJson(request, fileName)
            }
        }
    }

    /**
     * Exports volume analysis data.
     *
     * @param request Export configuration with volume data
     * @return LiftrixResult containing the exported file
     */
    suspend fun exportVolume(request: ExportVolumeDataRequest): LiftrixResult<File> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ExportError(
                    errorMessage = "Failed to export volume data: ${throwable.message}",
                    operation = "EXPORT_VOLUME"
                )
            }
        ) {
            validateVolumeRequest(request)
            Timber.d("Exporting ${request.volumePoints.size} volume data points")

            val fileName = "${VOLUME_FILE_PREFIX}_${getCurrentTimestamp()}"
            when (request.format) {
                ExportFormat.CSV -> exportVolumeToCsv(request, fileName)
                ExportFormat.PDF -> exportVolumeToPdf(request, fileName)
                ExportFormat.JSON -> exportVolumeToJson(request, fileName)
            }
        }
    }

    /**
     * Exports workout frequency data.
     *
     * @param request Export configuration with frequency data
     * @return LiftrixResult containing the exported file
     */
    suspend fun exportFrequency(request: ExportWorkoutFrequencyDataRequest): LiftrixResult<File> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ExportError(
                    errorMessage = "Failed to export frequency data: ${throwable.message}",
                    operation = "EXPORT_FREQUENCY"
                )
            }
        ) {
            validateFrequencyRequest(request)
            Timber.d("Exporting ${request.frequencyPoints.size} frequency data points")

            val fileName = "${FREQUENCY_FILE_PREFIX}_${getCurrentTimestamp()}"
            when (request.format) {
                ExportFormat.CSV -> exportFrequencyToCsv(request, fileName)
                ExportFormat.PDF -> exportFrequencyToPdf(request, fileName)
                ExportFormat.JSON -> exportFrequencyToJson(request, fileName)
            }
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Validates common export request parameters
     */
    private fun validateExportRequest(userId: String, exportType: ExportType, timeRange: TimeRangeType) {
        if (userId.isBlank()) {
            throw IllegalArgumentException("User ID cannot be blank for analytics export")
        }
    }

    /**
     * Validates volume export request
     */
    private fun validateVolumeRequest(request: ExportVolumeDataRequest) {
        if (request.volumePoints.isEmpty()) {
            throw IllegalArgumentException("Cannot export empty volume data")
        }
        if (request.volumePoints.size > 20000) {
            throw IllegalArgumentException("Volume export data size exceeds maximum limit (20,000 points)")
        }
    }

    /**
     * Validates frequency export request
     */
    private fun validateFrequencyRequest(request: ExportWorkoutFrequencyDataRequest) {
        if (request.frequencyPoints.isEmpty()) {
            throw IllegalArgumentException("Cannot export empty workout frequency data")
        }
        if (request.frequencyPoints.size > 5000) {
            throw IllegalArgumentException("Frequency export data size exceeds maximum limit (5,000 points)")
        }
    }

    // ========== 1RM EXPORT IMPLEMENTATIONS ==========

    private suspend fun exportOneRmData(userId: String, timeRange: TimeRangeType, format: ExportFormat): File {
        // Placeholder - would fetch data from repository in real implementation
        throw NotImplementedError("1RM export with simple parameters not yet implemented. Use exportOneRm() with full request.")
    }

    private fun exportOneRmToCsv(request: ExportOneRmDataRequest, fileName: String): File {
        val file = createTempFile("$fileName.csv")
        FileWriter(file).use { writer ->
            writer.appendLine("Date,Exercise,Weight (kg),Reps,Actual 1RM (kg),Estimated 1RM (kg),Is Estimated")
            request.progressionPoints.sortedBy { it.date }.forEach { dataPoint ->
                val exerciseName = request.exerciseNames[dataPoint.exerciseId] ?: "Unknown Exercise"
                writer.appendLine(
                    "${dataPoint.date}," +
                    "\"$exerciseName\"," +
                    "${dataPoint.weight}," +
                    "${dataPoint.reps}," +
                    "${dataPoint.actualOneRm ?: ""}," +
                    "${dataPoint.estimatedOneRm}," +
                    "${dataPoint.isEstimated}"
                )
            }
        }
        Timber.i("Successfully exported 1RM data to CSV: ${file.absolutePath}")
        return file
    }

    private fun exportOneRmToPdf(request: ExportOneRmDataRequest, fileName: String): File {
        val file = createTempFile("$fileName.pdf")
        FileWriter(file).use { writer ->
            writer.appendLine("1RM PROGRESSION REPORT")
            writer.appendLine("=".repeat(50))
            writer.appendLine("")
            writer.appendLine("Generated: ${getCurrentTimestamp()}")
            writer.appendLine("Time Range: ${request.timeRange.name}")
            writer.appendLine("Show Estimated: ${request.showEstimated}")
            writer.appendLine("Total Data Points: ${request.progressionPoints.size}")
            writer.appendLine("")
            writer.appendLine("EXERCISES INCLUDED:")
            writer.appendLine("-".repeat(30))
            request.exerciseNames.forEach { (id, name) ->
                val exercisePoints = request.progressionPoints.count { it.exerciseId == id }
                writer.appendLine("• $name ($exercisePoints data points)")
            }
            writer.appendLine("")
            writer.appendLine("DATA POINTS:")
            writer.appendLine("-".repeat(30))
            request.progressionPoints.sortedBy { it.date }.forEach { dataPoint ->
                val exerciseName = request.exerciseNames[dataPoint.exerciseId] ?: "Unknown Exercise"
                writer.appendLine(
                    "${dataPoint.date} - $exerciseName: " +
                    "${dataPoint.weight}kg x ${dataPoint.reps} " +
                    "(1RM: ${dataPoint.actualOneRm?.let { "${it}kg" } ?: "${dataPoint.estimatedOneRm}kg*"})"
                )
            }
            writer.appendLine("")
            writer.appendLine("* Estimated using Epley formula")
        }
        Timber.i("Successfully exported 1RM data to PDF: ${file.absolutePath}")
        return file
    }

    private fun exportOneRmToJson(request: ExportOneRmDataRequest, fileName: String): File {
        val file = createTempFile("$fileName.json")
        FileWriter(file).use { writer ->
            writer.appendLine("{")
            writer.appendLine("  \"metadata\": {")
            writer.appendLine("    \"exportedAt\": \"${getCurrentTimestamp()}\",")
            writer.appendLine("    \"timeRange\": \"${request.timeRange.name}\",")
            writer.appendLine("    \"showEstimated\": ${request.showEstimated},")
            writer.appendLine("    \"totalDataPoints\": ${request.progressionPoints.size}")
            writer.appendLine("  },")
            writer.appendLine("  \"exercises\": {")
            request.exerciseNames.entries.forEachIndexed { index, (id, name) ->
                writer.append("    \"$id\": \"$name\"")
                if (index < request.exerciseNames.size - 1) writer.append(",")
                writer.appendLine()
            }
            writer.appendLine("  },")
            writer.appendLine("  \"dataPoints\": [")
            request.progressionPoints.sortedBy { it.date }.forEachIndexed { index, dataPoint ->
                writer.appendLine("    {")
                writer.appendLine("      \"date\": \"${dataPoint.date}\",")
                writer.appendLine("      \"exerciseId\": \"${dataPoint.exerciseId}\",")
                writer.appendLine("      \"weight\": ${dataPoint.weight},")
                writer.appendLine("      \"reps\": ${dataPoint.reps},")
                writer.appendLine("      \"actualOneRm\": ${dataPoint.actualOneRm ?: "null"},")
                writer.appendLine("      \"estimatedOneRm\": ${dataPoint.estimatedOneRm},")
                writer.append("      \"isEstimated\": ${dataPoint.isEstimated}")
                writer.appendLine()
                writer.append("    }")
                if (index < request.progressionPoints.size - 1) writer.append(",")
                writer.appendLine()
            }
            writer.appendLine("  ]")
            writer.appendLine("}")
        }
        Timber.i("Successfully exported 1RM data to JSON: ${file.absolutePath}")
        return file
    }

    // ========== VOLUME EXPORT IMPLEMENTATIONS ==========

    private suspend fun exportVolumeData(userId: String, timeRange: TimeRangeType, format: ExportFormat): File {
        // Placeholder - would fetch data from repository in real implementation
        throw NotImplementedError("Volume export with simple parameters not yet implemented. Use exportVolume() with full request.")
    }

    private fun exportVolumeToCsv(request: ExportVolumeDataRequest, fileName: String): File {
        val file = createTempFile("$fileName.csv")
        val headers = mutableListOf("Date", "Exercise ID", "Exercise Name", "Muscle Group", "Sets", "Reps", "Weight (lbs)", "Total Volume (lbs)")
        if (request.includeBreakdown) {
            headers.addAll(listOf("Average Weight per Set", "Average Reps per Set", "Volume per Rep"))
        }

        FileWriter(file).use { writer ->
            writer.write(headers.joinToString(",") + "\n")
            request.volumePoints.forEach { point ->
                val baseRow = mutableListOf(
                    point.date.toString(), point.exerciseId, point.exerciseName, point.muscleGroup,
                    point.sets.toString(), point.reps.toString(), point.weight.toString(), point.totalVolume.toString()
                )
                if (request.includeBreakdown) {
                    val avgWeightPerSet = if (point.sets > 0) point.weight / point.sets else 0f
                    val avgRepsPerSet = if (point.sets > 0) point.reps / point.sets else 0f
                    val volumePerRep = if (point.reps > 0) point.totalVolume / point.reps else 0f
                    baseRow.addAll(listOf(avgWeightPerSet.toString(), avgRepsPerSet.toString(), volumePerRep.toString()))
                }
                writer.write(baseRow.joinToString(",") + "\n")
            }
        }
        return file
    }

    private fun exportVolumeToPdf(request: ExportVolumeDataRequest, fileName: String): File {
        val file = createTempFile("$fileName.pdf")
        FileWriter(file).use { writer ->
            writer.write("LIFTRIX VOLUME ANALYSIS REPORT\n")
            writer.write("=" + "=".repeat(40) + "\n\n")
            writer.write("Time Range: ${request.timeRange.name}\n")
            writer.write("Data Points: ${request.volumePoints.size}\n\n")
            writer.write("VOLUME DATA:\n")
            writer.write("-".repeat(15) + "\n")
            request.volumePoints.sortedBy { it.date }.forEach { point ->
                writer.write("${point.date} - ${point.exerciseName}: ${point.totalVolume}kg (${point.sets}x${point.reps} @ ${point.weight}kg)\n")
            }
        }
        return file
    }

    private fun exportVolumeToJson(request: ExportVolumeDataRequest, fileName: String): File {
        val file = createTempFile("$fileName.json")
        // Simplified JSON export implementation
        FileWriter(file).use { writer ->
            writer.appendLine("{")
            writer.appendLine("  \"volumeData\": [")
            request.volumePoints.forEachIndexed { index, point ->
                writer.append("    {\"date\": \"${point.date}\", \"exerciseName\": \"${point.exerciseName}\", \"totalVolume\": ${point.totalVolume}}")
                if (index < request.volumePoints.size - 1) writer.append(",")
                writer.appendLine()
            }
            writer.appendLine("  ]")
            writer.appendLine("}")
        }
        return file
    }

    // ========== FREQUENCY EXPORT IMPLEMENTATIONS ==========

    private suspend fun exportFrequencyData(userId: String, timeRange: TimeRangeType, format: ExportFormat): File {
        // Placeholder - would fetch data from repository in real implementation
        throw NotImplementedError("Frequency export with simple parameters not yet implemented. Use exportFrequency() with full request.")
    }

    private fun exportFrequencyToCsv(request: ExportWorkoutFrequencyDataRequest, fileName: String): File {
        val file = createTempFile("$fileName.csv")
        val headers = mutableListOf("Date", "Day of Week", "Workout Count", "Duration Minutes", "Consistency Score")
        if (request.includeTrends) {
            headers.addAll(listOf("Weekly Average", "Monthly Average", "Trend Direction"))
        }

        FileWriter(file).use { writer ->
            writer.write(headers.joinToString(",") + "\n")
            request.frequencyPoints.forEach { point ->
                val baseRow = mutableListOf(
                    point.date.toString(), point.dayOfWeek, point.workoutCount.toString(),
                    point.durationMinutes.toString(), point.consistencyScore.toString()
                )
                writer.write(baseRow.joinToString(",") + "\n")
            }
        }
        return file
    }

    private fun exportFrequencyToPdf(request: ExportWorkoutFrequencyDataRequest, fileName: String): File {
        val file = createTempFile("$fileName.pdf")
        FileWriter(file).use { writer ->
            writer.write("LIFTRIX WORKOUT FREQUENCY REPORT\n")
            writer.write("=" + "=".repeat(45) + "\n\n")
            writer.write("Time Range: ${request.timeRange.name}\n")
            writer.write("Data Points: ${request.frequencyPoints.size}\n\n")
            writer.write("FREQUENCY DATA:\n")
            writer.write("-".repeat(18) + "\n")
            request.frequencyPoints.sortedBy { it.date }.forEach { point ->
                writer.write("${point.date} - ${point.workoutCount} workouts\n")
            }
        }
        return file
    }

    private fun exportFrequencyToJson(request: ExportWorkoutFrequencyDataRequest, fileName: String): File {
        val file = createTempFile("$fileName.json")
        // Simplified JSON export implementation
        FileWriter(file).use { writer ->
            writer.appendLine("{")
            writer.appendLine("  \"frequencyData\": [")
            request.frequencyPoints.forEachIndexed { index, point ->
                writer.append("    {\"date\": \"${point.date}\", \"workoutCount\": ${point.workoutCount}}")
                if (index < request.frequencyPoints.size - 1) writer.append(",")
                writer.appendLine()
            }
            writer.appendLine("  ]")
            writer.appendLine("}")
        }
        return file
    }

    // ========== ALL ANALYTICS EXPORT ==========

    private suspend fun exportAllAnalytics(userId: String, timeRange: TimeRangeType, format: ExportFormat): File {
        // Placeholder - would aggregate all analytics data
        throw NotImplementedError("Full analytics export not yet implemented")
    }

    // ========== UTILITY METHODS ==========

    /**
     * Creates a temporary file in the app's cache directory
     */
    private fun createTempFile(fileName: String): File {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        return File(tempDir, fileName)
    }

    /**
     * Gets current timestamp for file naming and metadata
     */
    private fun getCurrentTimestamp(): String {
        val now = Clock.System.now()
        val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${localDateTime.year}-${localDateTime.monthNumber.toString().padStart(2, '0')}-${localDateTime.dayOfMonth.toString().padStart(2, '0')}_${localDateTime.hour.toString().padStart(2, '0')}-${localDateTime.minute.toString().padStart(2, '0')}"
    }
}

/**
 * Export type enumeration for analytics data
 */
enum class ExportType {
    ONE_RM,
    VOLUME,
    FREQUENCY,
    ALL
}

/**
 * Export format enumeration
 */
enum class ExportFormat {
    CSV,
    PDF,
    JSON
}

/**
 * Request data class for 1RM export operations
 */
data class ExportOneRmDataRequest(
    val progressionPoints: List<OneRmDataPoint>,
    val exerciseNames: Map<String, String>,
    val timeRange: TimeRangeType,
    val showEstimated: Boolean,
    val format: ExportFormat = ExportFormat.CSV
)

/**
 * Request data class for volume data export operations
 */
data class ExportVolumeDataRequest(
    val volumePoints: List<ExportVolumeDataPoint>,
    val timeRange: TimeRangeType,
    val muscleGroupFilter: String? = null,
    val includeBreakdown: Boolean = true,
    val format: ExportFormat = ExportFormat.CSV
)

/**
 * Request data class for workout frequency data export operations
 */
data class ExportWorkoutFrequencyDataRequest(
    val frequencyPoints: List<WorkoutFrequencyDataPoint>,
    val timeRange: TimeRangeType,
    val includeHeatmap: Boolean = true,
    val includeTrends: Boolean = true,
    val format: ExportFormat = ExportFormat.CSV
)

/**
 * Represents a single volume data point for export
 */
data class ExportVolumeDataPoint(
    val date: kotlinx.datetime.LocalDate,
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val sets: Int,
    val reps: Int,
    val weight: Float,
    val totalVolume: Float
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
