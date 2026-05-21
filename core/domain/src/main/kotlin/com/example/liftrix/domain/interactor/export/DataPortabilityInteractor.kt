package com.example.liftrix.domain.interactor.export

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.data_import.ImportOptions
import com.example.liftrix.domain.usecase.data_import.DataImportUseCase
import com.example.liftrix.domain.usecase.data_import.ImportProgress
import com.example.liftrix.domain.usecase.data_import.ImportResult
import com.example.liftrix.domain.usecase.data_import.ImportValidation
import com.example.liftrix.domain.usecase.export.ExportProgress
import com.example.liftrix.domain.usecase.export.ExportRequest
import com.example.liftrix.domain.usecase.export.ExportResult
import com.example.liftrix.domain.usecase.export.ExportWorkoutsUseCase
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DataPortabilityInteractor @Inject constructor(
    private val exportWorkoutsUseCase: ExportWorkoutsUseCase,
    private val dataImportUseCase: DataImportUseCase
) {
    suspend fun exportWorkouts(userId: String, request: ExportRequest): LiftrixResult<ExportResult> =
        exportWorkoutsUseCase.invoke(userId, request)

    fun exportProgress(exportId: String): Flow<ExportProgress> =
        exportWorkoutsUseCase.getExportProgress(exportId)

    suspend fun cancelExport(exportId: String, userId: String): LiftrixResult<Unit> =
        exportWorkoutsUseCase.cancelExport(exportId, userId)

    suspend fun validateImportFile(uri: Any, inputStream: InputStream): LiftrixResult<ImportValidation> =
        dataImportUseCase.validateFile(uri, inputStream)

    suspend fun importData(
        userId: String,
        uri: Any,
        inputStream: InputStream,
        options: ImportOptions
    ): LiftrixResult<ImportResult> = dataImportUseCase.import(userId, uri, inputStream, options)

    fun importProgress(importId: String): Flow<ImportProgress> =
        dataImportUseCase.getImportProgress(importId)
}
