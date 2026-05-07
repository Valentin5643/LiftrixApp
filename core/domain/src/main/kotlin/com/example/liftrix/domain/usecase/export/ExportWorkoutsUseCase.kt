package com.example.liftrix.domain.usecase.export

import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

interface ExportWorkoutsUseCase {
    suspend fun invoke(userId: String, request: ExportRequest): LiftrixResult<ExportResult>
    fun getExportProgress(exportId: String): Flow<ExportProgress>
    suspend fun cancelExport(exportId: String, userId: String): LiftrixResult<Unit>
}

data class ExportRequest(
    val format: ExportFormat,
    val dataTypes: Set<DataType>,
    val dateRange: DateRange? = null
)

data class ExportResult(
    val exportId: String,
    val file: File,
    val recordCount: Int,
    val format: ExportFormat
)

data class ExportProgress(
    val exportId: String,
    val progressPercentage: Int,
    val statusMessage: String
)

enum class ExportFormat {
    JSON, CSV, FIT, TCX
}

enum class DataType {
    WORKOUTS, EXERCISES, CUSTOM_EXERCISES, TEMPLATES
}

data class DateRange(
    val start: LocalDate,
    val end: LocalDate
)

data class WorkoutExportData(
    val id: String,
    val name: String,
    val date: LocalDateTime,
    val duration: Long?,
    val exercises: List<ExerciseExportData>
)

data class ExerciseExportData(
    val name: String,
    val category: String?,
    val sets: List<SetExportData>
)

data class SetExportData(
    val reps: Int?,
    val weight: Double?,
    val distance: Double?,
    val duration: Long?,
    val completed: Boolean
)
