package com.example.liftrix.data.service

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.os.Environment
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.DownloadManagerService
import com.example.liftrix.domain.service.DownloadStatus
import com.example.liftrix.domain.service.DownloadStatusType
import com.example.liftrix.domain.service.DownloadHistoryEntry
import com.example.liftrix.domain.usecase.legal.DownloadRequest
import com.example.liftrix.domain.usecase.legal.DownloadProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Implementation of DownloadManagerService using Android DownloadManager
 * Provides file download capabilities with progress tracking and notifications
 */
class DownloadManagerServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadManagerService {
    
    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    
    companion object {
        private const val DOWNLOADS_SUBDIR = "Liftrix"
        private const val PROGRESS_CHECK_INTERVAL_MS = 500L
    }
    
    override suspend fun startDownload(request: DownloadRequest): Flow<DownloadProgress> = flow {
        try {
            // First copy the source file to downloads directory
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                DOWNLOADS_SUBDIR
            )
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val destFile = File(downloadsDir, request.fileName)
            
            // Copy source file to downloads directory
            copyFile(request.sourceFile, destFile)
            
            val fileSize = destFile.length()
            if (fileSize <= 0L || !destFile.isFile) {
                throw IllegalStateException("Export produced no downloadable content")
            }
            emit(DownloadProgress.Success(destFile.absolutePath, fileSize))
            
            // Save to download history
            saveDownloadHistory(
                fileName = request.fileName,
                fileSize = fileSize,
                filePath = destFile.absolutePath,
                userId = request.userId,
                mimeType = request.mimeType
            )
            
            Timber.d("Download completed: ${destFile.absolutePath}")
            
        } catch (e: Exception) {
            emit(DownloadProgress.Error("Download failed: ${e.message}"))
            Timber.e(e, "Download failed for ${request.fileName}")
        }
    }
    
    override suspend fun validateDownloadConditions(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ValidationError(
                field = "download_conditions",
                violations = listOf("Download validation failed: ${throwable.message}"),
                analyticsContext = mapOf(
                    "operation" to "VALIDATE_DOWNLOAD_CONDITIONS",
                    "user_id" to userId
                )
            )
        }
    ) {
        // Check external storage state
        val storageState = Environment.getExternalStorageState()
        if (storageState != Environment.MEDIA_MOUNTED) {
            throw IllegalStateException("External storage not available for downloads")
        }
        
        // Check downloads directory accessibility
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            throw IllegalStateException("Cannot access Downloads directory")
        }
        
        // Check available space (require at least 50MB)
        val freeSpace = downloadsDir.freeSpace
        if (freeSpace < 50 * 1024 * 1024) {
            throw IllegalStateException("Insufficient storage space for downloads")
        }
        
        // Check if DownloadManager service is available
        try {
            downloadManager
        } catch (e: Exception) {
            throw IllegalStateException("Download Manager service not available")
        }
        
        Unit
    }
    
    override suspend fun cancelDownload(downloadId: Long): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DOWNLOAD_CANCEL_FAILED",
                errorMessage = "Failed to cancel download: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CANCEL_DOWNLOAD",
                    "download_id" to downloadId.toString()
                )
            )
        }
    ) {
        val removed = downloadManager.remove(downloadId)
        if (removed == 0) {
            throw IllegalArgumentException("Download not found or already completed")
        }
        Timber.d("Download cancelled: $downloadId")
        Unit
    }
    
    override suspend fun getDownloadStatus(downloadId: Long): LiftrixResult<DownloadStatus> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DOWNLOAD_STATUS_FAILED",
                errorMessage = "Failed to get download status: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_DOWNLOAD_STATUS",
                    "download_id" to downloadId.toString()
                )
            )
        }
    ) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor: Cursor = downloadManager.query(query)
        
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val fileName = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
            val filePath = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_FILENAME))
            
            cursor.close()
            
            DownloadStatus(
                downloadId = downloadId,
                status = DownloadStatusType.fromValue(status),
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                fileName = fileName,
                filePath = filePath
            )
        } else {
            cursor.close()
            throw IllegalArgumentException("Download not found: $downloadId")
        }
    }
    
    override suspend fun cleanupOldDownloads(maxAgeHours: Int, userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DOWNLOAD_CLEANUP_FAILED",
                errorMessage = "Failed to cleanup old downloads: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CLEANUP_OLD_DOWNLOADS",
                    "user_id" to userId
                )
            )
        }
    ) {
        val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000)
        val downloadsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DOWNLOADS_SUBDIR
        )
        
        if (downloadsDir.exists()) {
            var cleanedCount = 0
            downloadsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        cleanedCount++
                    }
                }
            }
            Timber.d("Cleaned up $cleanedCount old download files")
        }
        
        Unit
    }
    
    override suspend fun getDownloadHistory(userId: String, limit: Int): LiftrixResult<List<DownloadHistoryEntry>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DOWNLOAD_HISTORY_FAILED",
                errorMessage = "Failed to get download history: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_DOWNLOAD_HISTORY",
                    "user_id" to userId
                )
            )
        }
    ) {
        // For this implementation, we'll return an empty list as we're not storing persistent history
        // In a full implementation, you would query a local database or shared preferences
        emptyList<DownloadHistoryEntry>()
    }
    
    /**
     * Copies a source file to destination
     */
    private fun copyFile(sourceFile: File, destFile: File) {
        if (!sourceFile.exists()) {
            throw IllegalArgumentException("Source file does not exist: ${sourceFile.absolutePath}")
        }
        
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
    
    /**
     * Saves download to history (simplified implementation)
     */
    private fun saveDownloadHistory(
        fileName: String,
        fileSize: Long,
        filePath: String,
        userId: String,
        mimeType: String
    ) {
        // In a full implementation, this would save to a local database
        // For now, we just log the download
        Timber.d("Download history: $fileName ($fileSize bytes) for user $userId")
    }
}
