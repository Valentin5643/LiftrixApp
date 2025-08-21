package com.example.liftrix.service.export

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.liftrix.core.workmanager.WorkManagerProvider
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.service.AnalyticsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Comprehensive export manager implementing dual export system with background processing.
 * 
 * Features:
 * - Analytics Reports: PDF/CSV with visualizations and calculated metrics
 * - Raw Data Export: JSON/CSV/FIT/TCX for cross-platform compatibility
 * - Background Processing: WorkManager with progress notifications and cancellation
 * - Date Range Selection: 30 days to all-time history with data filtering
 * - Privacy Controls: Selective data export with granular permissions
 * 
 * Architecture:
 * - WorkManager for background processing with progress tracking
 * - Flow-based progress updates for reactive UI
 * - Scoped storage for file management and sharing
 * - Comprehensive error handling with LiftrixResult<T>
 * 
 * Performance Targets:
 * - Analytics exports: <10s for monthly reports with visualizations
 * - Raw data exports: <5s for quarterly data in any format
 * - Progress notifications: Real-time updates every 100ms
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analyticsService: AnalyticsService
) {
    
    private val workManager: WorkManager
        get() = WorkManagerProvider.getInstance(context)
    
    companion object {
        private const val EXPORT_WORK_NAME_PREFIX = "export_work"
        private const val MAX_EXPORT_RETRIES = 3
        private const val PROGRESS_UPDATE_INTERVAL_MS = 100L
    }
    
    /**
     * Exports analytics report with calculated metrics, trends, and visualizations
     * 
     * @param userId User ID for data export
     * @param dateRange Date range for export (30 days to all-time)
     * @param format Export format (PDF with charts or CSV with raw calculations)
     * @return Flow of export progress with final file path or error
     */
    suspend fun exportAnalyticsReport(
        userId: String,
        dateRange: TimeRange,
        format: ExportFormat
    ): Flow<ExportProgress> {
        return liftrixCatching(
            errorMapper = { exception ->
                when (exception) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = "export_parameters",
                        violations = listOf("Invalid export parameters: ${exception.message}")
                    )
                    else -> LiftrixError.ExportError(
                        errorMessage = "Failed to start analytics export: ${exception.message}",
                        operation = "exportAnalyticsReport"
                    )
                }
            }
        ) {
            Timber.d("Starting analytics export - userId: $userId, format: $format, dateRange: $dateRange")
            
            // Validate inputs
            validateExportParameters(userId, dateRange, format)
            
            // Create work request for analytics export
            val workId = generateWorkId("analytics", userId, format)
            val inputData = createAnalyticsExportData(userId, dateRange, format)
            
            val workRequest = OneTimeWorkRequestBuilder<ExportWorker>()
                .setInputData(inputData)
                .setConstraints(createExportConstraints())
                .addTag(workId)
                .build()
            
            // Enqueue work with unique name to prevent duplicates
            workManager.enqueueUniqueWork(
                workId,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            Timber.d("Export work enqueued with ID: ${workRequest.id}")
            
            // Return Flow tracking work progress
            workManager.getWorkInfoByIdFlow(workRequest.id)
                .map { workInfo -> mapWorkInfoToProgress(workInfo) }
                
        }.getOrThrow()
    }
    
    /**
     * Exports raw data in industry-standard formats for cross-platform compatibility
     * 
     * @param userId User ID for data export
     * @param dateRange Date range for export
     * @param format Raw data format (JSON, CSV, FIT, TCX)
     * @param dataTypes Selective data types to include (workouts, progress, preferences)
     * @return Flow of export progress with final file path or error
     */
    suspend fun exportRawData(
        userId: String,
        dateRange: TimeRange,
        format: RawDataFormat,
        dataTypes: Set<RawDataType> = setOf(RawDataType.WORKOUTS, RawDataType.PROGRESS)
    ): Flow<ExportProgress> {
        return liftrixCatching(
            errorMapper = { exception ->
                when (exception) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = "raw_export_parameters",
                        violations = listOf("Invalid raw export parameters: ${exception.message}")
                    )
                    else -> LiftrixError.ExportError(
                        errorMessage = "Failed to start raw data export: ${exception.message}",
                        operation = "exportRawData"
                    )
                }
            }
        ) {
            Timber.d("Starting raw data export - userId: $userId, format: $format, types: $dataTypes")
            
            // Validate inputs
            validateRawExportParameters(userId, dateRange, format, dataTypes)
            
            // Create work request for raw data export
            val workId = generateWorkId("raw_data", userId, format)
            val inputData = createRawDataExportData(userId, dateRange, format, dataTypes)
            
            val workRequest = OneTimeWorkRequestBuilder<ExportWorker>()
                .setInputData(inputData)
                .setConstraints(createExportConstraints())
                .addTag(workId)
                .build()
            
            // Enqueue work with unique name
            workManager.enqueueUniqueWork(
                workId,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            Timber.d("Raw data export work enqueued with ID: ${workRequest.id}")
            
            // Return Flow tracking work progress
            workManager.getWorkInfoByIdFlow(workRequest.id)
                .map { workInfo -> mapWorkInfoToProgress(workInfo) }
                
        }.getOrThrow()
    }
    
    /**
     * Cancels ongoing export operation
     * 
     * @param userId User ID to cancel exports for
     * @return LiftrixResult indicating cancellation success
     */
    suspend fun cancelExport(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { exception ->
                LiftrixError.ExportError(
                    errorMessage = "Failed to cancel export: ${exception.message}",
                    operation = "cancelExport"
                )
            }
        ) {
            Timber.d("Cancelling exports for user: $userId")
            
            // Cancel all work with user ID tag
            workManager.cancelAllWorkByTag(userId)
            
            Timber.d("Export cancellation requested for user: $userId")
        }
    }
    
    /**
     * Gets current export status for a user
     * 
     * @param userId User ID to check export status for
     * @return Flow of current export operations and their progress
     */
    fun getExportStatus(userId: String): Flow<List<ExportProgress>> {
        return workManager.getWorkInfosByTagFlow(userId)
            .map { workInfoList ->
                workInfoList.map { workInfo -> mapWorkInfoToProgress(workInfo) }
            }
    }
    
    /**
     * Validates export parameters for analytics reports
     */
    private fun validateExportParameters(userId: String, dateRange: TimeRange, format: ExportFormat) {
        if (userId.isBlank()) {
            throw IllegalArgumentException("User ID cannot be blank")
        }
        
        if (!dateRange.isValid()) {
            throw IllegalArgumentException("Invalid date range: $dateRange")
        }
        
        if (dateRange.getDurationInDays() > 365 * 2) {
            throw IllegalArgumentException("Date range too large: maximum 2 years allowed")
        }
    }
    
    /**
     * Validates raw data export parameters
     */
    private fun validateRawExportParameters(
        userId: String,
        dateRange: TimeRange,
        format: RawDataFormat,
        dataTypes: Set<RawDataType>
    ) {
        if (userId.isBlank()) {
            throw IllegalArgumentException("User ID cannot be blank")
        }
        
        if (!dateRange.isValid()) {
            throw IllegalArgumentException("Invalid date range: $dateRange")
        }
        
        if (dataTypes.isEmpty()) {
            throw IllegalArgumentException("At least one data type must be selected")
        }
        
        // FIT and TCX formats only support workout data
        if ((format == RawDataFormat.FIT || format == RawDataFormat.TCX) && 
            !dataTypes.contains(RawDataType.WORKOUTS)) {
            throw IllegalArgumentException("FIT and TCX formats require workout data")
        }
    }
    
    /**
     * Creates work constraints for export operations
     */
    private fun createExportConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
    }
    
    /**
     * Creates input data for analytics export work
     */
    private fun createAnalyticsExportData(
        userId: String,
        dateRange: TimeRange,
        format: ExportFormat
    ): Data {
        return workDataOf(
            ExportWorker.KEY_EXPORT_TYPE to ExportWorker.EXPORT_TYPE_ANALYTICS,
            ExportWorker.KEY_USER_ID to userId,
            ExportWorker.KEY_START_DATE to dateRange.startDate.time,
            ExportWorker.KEY_END_DATE to dateRange.endDate.time,
            ExportWorker.KEY_FORMAT to format.name,
            ExportWorker.KEY_TIMESTAMP to System.currentTimeMillis()
        )
    }
    
    /**
     * Creates input data for raw data export work
     */
    private fun createRawDataExportData(
        userId: String,
        dateRange: TimeRange,
        format: RawDataFormat,
        dataTypes: Set<RawDataType>
    ): Data {
        return workDataOf(
            ExportWorker.KEY_EXPORT_TYPE to ExportWorker.EXPORT_TYPE_RAW_DATA,
            ExportWorker.KEY_USER_ID to userId,
            ExportWorker.KEY_START_DATE to dateRange.startDate.time,
            ExportWorker.KEY_END_DATE to dateRange.endDate.time,
            ExportWorker.KEY_RAW_FORMAT to format.name,
            ExportWorker.KEY_DATA_TYPES to dataTypes.map { it.name }.toTypedArray(),
            ExportWorker.KEY_TIMESTAMP to System.currentTimeMillis()
        )
    }
    
    /**
     * Generates unique work ID for export operations
     */
    private fun generateWorkId(type: String, userId: String, format: Any): String {
        val userIdSafe = userId.take(8)
        val timestamp = System.currentTimeMillis()
        return "${EXPORT_WORK_NAME_PREFIX}_${type}_${userIdSafe}_${format}_${timestamp}"
    }
    
    /**
     * Maps WorkInfo to ExportProgress for UI consumption
     */
    private fun mapWorkInfoToProgress(workInfo: WorkInfo): ExportProgress {
        return when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> ExportProgress.Queued
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress.getInt(ExportWorker.KEY_PROGRESS, 0)
                val currentStep = workInfo.progress.getString(ExportWorker.KEY_CURRENT_STEP) ?: "Processing..."
                ExportProgress.InProgress(progress, currentStep)
            }
            WorkInfo.State.SUCCEEDED -> {
                val filePath = workInfo.outputData.getString(ExportWorker.KEY_FILE_PATH)
                val fileSize = workInfo.outputData.getLong(ExportWorker.KEY_FILE_SIZE, 0L)
                if (filePath != null) {
                    ExportProgress.Completed(filePath, fileSize)
                } else {
                    ExportProgress.Failed("Export completed but file path missing")
                }
            }
            WorkInfo.State.FAILED -> {
                val error = workInfo.outputData.getString(ExportWorker.KEY_ERROR_MESSAGE) 
                    ?: "Export failed with unknown error"
                ExportProgress.Failed(error)
            }
            WorkInfo.State.CANCELLED -> ExportProgress.Cancelled
            WorkInfo.State.BLOCKED -> ExportProgress.Queued
        }
    }
}

/**
 * Export progress states for reactive UI updates
 */
sealed class ExportProgress {
    object Queued : ExportProgress()
    data class InProgress(val progress: Int, val currentStep: String) : ExportProgress()
    data class Completed(val filePath: String, val fileSize: Long) : ExportProgress()
    data class Failed(val errorMessage: String) : ExportProgress()
    object Cancelled : ExportProgress()
}

/**
 * Analytics export formats with visualizations
 */
enum class ExportFormat {
    PDF, // Visual reports with charts and metrics
    CSV  // Raw calculated data for external analysis
}

/**
 * Raw data export formats for cross-platform compatibility
 */
enum class RawDataFormat {
    JSON,   // Structured data for APIs and tools
    CSV,    // Tabular data for spreadsheets
    FIT,    // Garmin Connect, Strava, TrainingPeaks
    TCX     // Training Center XML for sports platforms
}

/**
 * Selective data types for privacy-controlled exports
 */
enum class RawDataType {
    WORKOUTS,       // Exercise sessions and sets
    PROGRESS,       // Performance metrics and trends
    PREFERENCES,    // User settings and configurations
    ANALYTICS       // Calculated metrics and insights
}