package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.legal.DownloadRequest
import com.example.liftrix.domain.usecase.legal.DownloadProgress
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for managing file downloads using Android Download Manager
 * Handles progress tracking, notifications, and proper file management
 */
interface DownloadManagerService {
    
    /**
     * Starts a download and returns a flow of progress updates
     * 
     * @param request Download request with file details and user context
     * @return Flow of download progress updates
     */
    suspend fun startDownload(request: DownloadRequest): Flow<DownloadProgress>
    
    /**
     * Validates download conditions including permissions and storage availability
     * 
     * @param userId User identifier for scoped validation
     * @return LiftrixResult indicating if download conditions are met
     */
    suspend fun validateDownloadConditions(userId: String): LiftrixResult<Unit>
    
    /**
     * Cancels an ongoing download
     * 
     * @param downloadId Download identifier returned from startDownload
     * @return LiftrixResult indicating cancellation success
     */
    suspend fun cancelDownload(downloadId: Long): LiftrixResult<Unit>
    
    /**
     * Gets download status for a specific download
     * 
     * @param downloadId Download identifier
     * @return LiftrixResult containing download status information
     */
    suspend fun getDownloadStatus(downloadId: Long): LiftrixResult<DownloadStatus>
    
    /**
     * Cleans up completed downloads older than specified age
     * 
     * @param maxAgeHours Maximum age in hours for completed downloads
     * @param userId User identifier for scoped cleanup
     * @return LiftrixResult indicating cleanup success
     */
    suspend fun cleanupOldDownloads(maxAgeHours: Int = 168, userId: String): LiftrixResult<Unit>
    
    /**
     * Gets user's download history
     * 
     * @param userId User identifier
     * @param limit Maximum number of entries to return
     * @return LiftrixResult containing list of download history entries
     */
    suspend fun getDownloadHistory(userId: String, limit: Int = 50): LiftrixResult<List<DownloadHistoryEntry>>
}

/**
 * Download status information
 */
data class DownloadStatus(
    val downloadId: Long,
    val status: DownloadStatusType,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val fileName: String,
    val filePath: String?
) {
    val progress: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
}

/**
 * Download status types matching Android DownloadManager constants
 */
enum class DownloadStatusType(val value: Int) {
    PENDING(1),        // DownloadManager.STATUS_PENDING
    RUNNING(2),        // DownloadManager.STATUS_RUNNING
    PAUSED(4),         // DownloadManager.STATUS_PAUSED
    SUCCESSFUL(8),     // DownloadManager.STATUS_SUCCESSFUL
    FAILED(16);        // DownloadManager.STATUS_FAILED
    
    companion object {
        fun fromValue(value: Int): DownloadStatusType {
            return values().find { it.value == value } ?: FAILED
        }
    }
}

/**
 * Download history entry for user tracking
 */
data class DownloadHistoryEntry(
    val downloadId: Long,
    val fileName: String,
    val fileSize: Long,
    val downloadedAt: Long,
    val status: DownloadStatusType,
    val filePath: String?,
    val userId: String,
    val mimeType: String
) {
    val isSuccessful: Boolean
        get() = status == DownloadStatusType.SUCCESSFUL
        
    val downloadedDate: String
        get() = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(downloadedAt))
}