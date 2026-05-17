package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.export.ProgressReportFileActionMetadata

interface ProgressReportFileManager {
    suspend fun saveProgressReportToCache(
        fileName: String,
        pdfBytes: ByteArray
    ): LiftrixResult<ProgressReportCacheFile>

    suspend fun getOpenMetadata(filePath: String): LiftrixResult<ProgressReportFileActionMetadata>

    suspend fun getShareMetadata(filePath: String): LiftrixResult<ProgressReportFileActionMetadata>

    suspend fun saveToDownloads(filePath: String, fileName: String): LiftrixResult<ProgressReportFileActionMetadata>
}

data class ProgressReportCacheFile(
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String = "application/pdf"
)
