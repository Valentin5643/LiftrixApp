package com.example.liftrix.domain.interactor.export

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.export.ProgressReportFileActionMetadata
import com.example.liftrix.domain.model.export.ProgressReportRequest
import com.example.liftrix.domain.model.export.ProgressReportResult
import com.example.liftrix.domain.usecase.export.GenerateProgressReportUseCase
import com.example.liftrix.domain.usecase.export.ProgressReportFileActionsUseCase
import javax.inject.Inject

class ProgressReportInteractor @Inject constructor(
    private val generateProgressReportUseCase: GenerateProgressReportUseCase,
    private val fileActionsUseCase: ProgressReportFileActionsUseCase
) {
    suspend fun generate(
        userId: String,
        request: ProgressReportRequest
    ): LiftrixResult<ProgressReportResult> =
        generateProgressReportUseCase(userId, request)

    suspend fun open(filePath: String): LiftrixResult<ProgressReportFileActionMetadata> =
        fileActionsUseCase.open(filePath)

    suspend fun share(filePath: String): LiftrixResult<ProgressReportFileActionMetadata> =
        fileActionsUseCase.share(filePath)

    suspend fun saveToDownloads(
        filePath: String,
        fileName: String
    ): LiftrixResult<ProgressReportFileActionMetadata> =
        fileActionsUseCase.saveToDownloads(filePath, fileName)
}
