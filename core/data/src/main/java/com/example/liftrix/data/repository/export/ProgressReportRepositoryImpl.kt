package com.example.liftrix.data.repository.export

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.export.ProgressReportData
import com.example.liftrix.domain.model.export.ProgressReportRequest
import com.example.liftrix.domain.model.export.ProgressReportResult
import com.example.liftrix.domain.repository.export.ProgressReportRepository
import com.example.liftrix.domain.service.ProgressReportFileManager
import com.example.liftrix.service.export.PdfReportGenerator
import com.example.liftrix.service.export.ProgressReportDataBuilder
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressReportRepositoryImpl @Inject constructor(
    private val dataBuilder: ProgressReportDataBuilder,
    private val pdfReportGenerator: PdfReportGenerator,
    private val fileManager: ProgressReportFileManager
) : ProgressReportRepository {
    override suspend fun generateLocalReport(
        userId: String,
        request: ProgressReportRequest
    ): LiftrixResult<ProgressReportResult> {
        return liftrixCatching(
            errorMapper = { throwable ->
                throwable as? LiftrixError ?: LiftrixError.ExportError(
                    errorMessage = "Could not generate the report. Please try again.",
                    operation = "generate_progress_report"
                )
            }
        ) {
            val data = dataBuilder.build(userId, request)
            if (data.summary.workoutsCompleted == 0) {
                throw LiftrixError.ValidationError(
                    field = "workouts",
                    violations = listOf(ProgressReportData.NO_WORKOUT_DATA_MESSAGE),
                    errorMessage = ProgressReportData.NO_WORKOUT_DATA_MESSAGE
                )
            }
            val pdfBytes = pdfReportGenerator.generateChartlessProgressReport(data).getOrThrow()
            val fileName = "Liftrix_Progress_Report_${request.generatedAt.toLocalDate().format(DateTimeFormatter.ISO_DATE)}.pdf"
            val cacheFile = fileManager.saveProgressReportToCache(fileName, pdfBytes).getOrThrow()

            ProgressReportResult(
                exportId = UUID.randomUUID().toString(),
                filePath = cacheFile.filePath,
                fileName = cacheFile.fileName,
                fileSizeBytes = cacheFile.fileSizeBytes,
                mimeType = cacheFile.mimeType,
                recordCount = data.summary.workoutsCompleted,
                isMinimalReport = data.isMinimalReport
            )
        }
    }
}
