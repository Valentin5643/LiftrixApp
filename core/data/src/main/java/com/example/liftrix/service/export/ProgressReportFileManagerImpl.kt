package com.example.liftrix.service.export

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.export.ProgressReportFileActionMetadata
import com.example.liftrix.domain.service.ProgressReportCacheFile
import com.example.liftrix.domain.service.ProgressReportFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressReportFileManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ProgressReportFileManager {
    override suspend fun saveProgressReportToCache(
        fileName: String,
        pdfBytes: ByteArray
    ): LiftrixResult<ProgressReportCacheFile> = liftrixCatching(
        errorMapper = { exception ->
            LiftrixError.FileSystemError(
                errorMessage = "Could not save the report: ${exception.message}",
                operation = "save_progress_report_cache"
            )
        }
    ) {
        val reportDir = File(context.cacheDir, "progress_reports")
        reportDir.mkdirs()
        val file = File(reportDir, fileName)
        file.writeBytes(pdfBytes)
        ProgressReportCacheFile(
            filePath = file.absolutePath,
            fileName = file.name,
            fileSizeBytes = file.length()
        )
    }

    override suspend fun getOpenMetadata(filePath: String): LiftrixResult<ProgressReportFileActionMetadata> =
        metadataForFile(filePath)

    override suspend fun getShareMetadata(filePath: String): LiftrixResult<ProgressReportFileActionMetadata> =
        metadataForFile(filePath)

    override suspend fun saveToDownloads(
        filePath: String,
        fileName: String
    ): LiftrixResult<ProgressReportFileActionMetadata> = liftrixCatching(
        errorMapper = { exception ->
            LiftrixError.FileSystemError(
                errorMessage = "Could not save the report to Downloads: ${exception.message}",
                operation = "save_progress_report_downloads"
            )
        }
    ) {
        val sourceFile = File(filePath)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, PDF_MIME_TYPE)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Downloads provider did not return a URI")
        resolver.openOutputStream(uri)?.use { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("Could not open Downloads output stream")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        ProgressReportFileActionMetadata(
            uriString = uri.toString(),
            mimeType = PDF_MIME_TYPE,
            fileName = fileName
        )
    }

    private fun metadataForFile(filePath: String): LiftrixResult<ProgressReportFileActionMetadata> = liftrixCatching(
        errorMapper = { exception ->
            LiftrixError.FileSystemError(
                errorMessage = "Could not prepare the report file: ${exception.message}",
                operation = "progress_report_file_metadata"
            )
        }
    ) {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        ProgressReportFileActionMetadata(
            uriString = uri.toString(),
            mimeType = PDF_MIME_TYPE,
            fileName = file.name
        )
    }

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }
}
