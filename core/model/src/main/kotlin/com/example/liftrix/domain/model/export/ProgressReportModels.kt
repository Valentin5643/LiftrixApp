package com.example.liftrix.domain.model.export

import java.time.LocalDate
import java.time.LocalDateTime

data class ProgressReportRequest(
    val dateRange: ProgressReportDateRange = ProgressReportDateRange.Last30Days,
    val includeOptions: ProgressReportIncludeOptions = ProgressReportIncludeOptions(),
    val privacyOptions: ProgressReportPrivacyOptions = ProgressReportPrivacyOptions(),
    val generatedAt: LocalDateTime = LocalDateTime.now()
)

sealed class ProgressReportDateRange {
    data object Last30Days : ProgressReportDateRange()
    data object Last6Months : ProgressReportDateRange()
    data object AllTime : ProgressReportDateRange()
    data class Custom(val start: LocalDate, val end: LocalDate) : ProgressReportDateRange()

    fun label(today: LocalDate = LocalDate.now()): String {
        return when (this) {
            Last30Days -> "Last 30 days"
            Last6Months -> "Last 6 months"
            AllTime -> "All time"
            is Custom -> "${start} to ${end.coerceAtMost(today)}"
        }
    }
}

data class ProgressReportResolvedDateRange(
    val start: LocalDate,
    val end: LocalDate,
    val label: String
)

data class ProgressReportPrivacyOptions(
    val hideBodyweight: Boolean = true,
    val hidePersonalNotes: Boolean = true,
    val hideEmailAccountInfo: Boolean = true
)

data class ProgressReportIncludeOptions(
    val workoutSummary: Boolean = true,
    val strengthProgress: Boolean = true,
    val volumeAnalysis: Boolean = true,
    val personalRecords: Boolean = true,
    val consistencySummary: Boolean = true,
    val aiCoachInsights: Boolean = true,
    val detailedWorkoutList: Boolean = true
)

data class ProgressReportData(
    val generatedAt: LocalDateTime,
    val range: ProgressReportResolvedDateRange,
    val title: String,
    val summary: ProgressReportSummaryMetrics,
    val strengthRows: List<ProgressReportStrengthRow>,
    val weeklyVolumeRows: List<ProgressReportWeeklyVolumeRow>,
    val muscleGroupRows: List<ProgressReportMuscleGroupRow>,
    val consistencyRows: List<ProgressReportConsistencyRow>,
    val personalRecordRows: List<ProgressReportPersonalRecordRow>,
    val workoutRows: List<ProgressReportWorkoutRow>,
    val aiSummary: ProgressReportAiSummary,
    val syncStatus: ProgressReportSyncStatus,
    val isMinimalReport: Boolean
)

data class ProgressReportSummaryMetrics(
    val workoutsCompleted: Int,
    val totalVolumeKg: Double,
    val activeTrainingDays: Int,
    val newPersonalRecords: Int,
    val bestStreakDays: Int,
    val estimatedOneRmImprovementKg: Double?,
    val averageWorkoutsPerWeek: Double,
    val averageDurationMinutes: Long?
)

data class ProgressReportStrengthRow(
    val exerciseId: String,
    val exerciseName: String,
    val startEstimatedOneRmKg: Double,
    val bestEstimatedOneRmKg: Double,
    val improvementKg: Double
)

data class ProgressReportWeeklyVolumeRow(
    val weekLabel: String,
    val workoutCount: Int,
    val totalVolumeKg: Double,
    val setCount: Int
)

data class ProgressReportMuscleGroupRow(
    val muscleGroup: String,
    val totalVolumeKg: Double,
    val exerciseCount: Int,
    val setCount: Int
)

data class ProgressReportConsistencyRow(
    val weekLabel: String,
    val workoutCount: Int,
    val activeDays: Int
)

data class ProgressReportPersonalRecordRow(
    val date: LocalDate,
    val exerciseName: String,
    val recordType: String,
    val newValue: String,
    val previousValue: String?
)

data class ProgressReportWorkoutRow(
    val date: LocalDate,
    val name: String,
    val durationMinutes: Long?,
    val synced: Boolean
)

data class ProgressReportAiSummary(
    val summary: String,
    val recommendations: List<String>,
    val contextUsed: List<String>
) {
    companion object {
        const val INSUFFICIENT_HISTORY_MESSAGE =
            "Not enough training history is available to generate a detailed AI coach summary yet. Complete more workouts to unlock deeper insights."
    }
}

data class ProgressReportSyncStatus(
    val pendingSyncItems: Int,
    val lastSyncTimestampMillis: Long?,
    val generatedOffline: Boolean = true
)

data class ProgressReportResult(
    val exportId: String,
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String = "application/pdf",
    val recordCount: Int,
    val isMinimalReport: Boolean
)

data class ProgressReportFileActionMetadata(
    val uriString: String,
    val mimeType: String,
    val fileName: String
)
