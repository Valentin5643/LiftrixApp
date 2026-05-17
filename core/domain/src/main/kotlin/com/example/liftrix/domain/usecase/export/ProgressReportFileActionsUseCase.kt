package com.example.liftrix.domain.usecase.export

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.export.ProgressReportFileActionMetadata
import com.example.liftrix.domain.service.ProgressReportFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ProgressReportFileActionsUseCase @Inject constructor(
    private val fileManager: ProgressReportFileManager
) {
    suspend fun open(filePath: String): LiftrixResult<ProgressReportFileActionMetadata> =
        withContext(Dispatchers.IO) { fileManager.getOpenMetadata(filePath) }

    suspend fun share(filePath: String): LiftrixResult<ProgressReportFileActionMetadata> =
        withContext(Dispatchers.IO) { fileManager.getShareMetadata(filePath) }

    suspend fun saveToDownloads(
        filePath: String,
        fileName: String
    ): LiftrixResult<ProgressReportFileActionMetadata> =
        withContext(Dispatchers.IO) { fileManager.saveToDownloads(filePath, fileName) }
}
