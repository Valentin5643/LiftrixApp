package com.example.liftrix.data.repository.social

import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.local.dao.ContentReportsDao
import com.example.liftrix.data.local.entity.ContentReportEntity
import com.example.liftrix.data.remote.legacy.LegacyReportFirestoreDataSource
import com.example.liftrix.data.sync.OfflineQueueManager
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.ReportReason
import com.example.liftrix.domain.repository.social.ReportRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ReportRepository for managing user reports
 * 
 * Note: Reports are only stored in Firebase for moderation purposes,
 * not synced to local database to maintain reporter privacy
 */
@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val contentReportsDao: ContentReportsDao,
    private val legacyDataSource: LegacyReportFirestoreDataSource,
    private val offlineQueueManager: OfflineQueueManager
) : ReportRepository {
    
    override suspend fun submitReport(
        reporterId: String,
        targetUserId: String,
        reason: ReportReason,
        description: String?,
        contentType: ReportRepository.ContentType,
        targetAuthorId: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to submit report: ${throwable.message}",
                isRecoverable = true
            )
        }
    ) {
        val reportId = UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()
        
        if (OfflineArchitectureFlags.FIX_REPORT_REPOSITORY || contentType != ReportRepository.ContentType.PROFILE) {
            val entity = ContentReportEntity(
                id = reportId,
                reporterUserId = reporterId,
                contentType = contentType.name,
                contentId = targetUserId,
                reason = reason.name,
                description = description,
                reportedAt = currentTime,
                status = ContentReportEntity.STATUS_PENDING,
                isSynced = false
            )
            contentReportsDao.upsertLocal(entity)
            offlineQueueManager.queueSocialMutation(reporterId, "CONTENT_REPORT", reportId, "CREATE").getOrThrow()
        } else {
            legacyDataSource.submitReport(
                reportId = reportId,
                reporterId = reporterId,
                targetUserId = targetUserId,
                reason = reason,
                description = description,
                createdAt = currentTime
            )
        }
        
        Timber.d("Report submitted: $reportId type=$contentType content=$targetUserId author=$targetAuthorId")
        
        Unit
    }
    
    override suspend fun hasExistingReport(
        reporterId: String,
        targetUserId: String,
        contentType: ReportRepository.ContentType
    ): Boolean {
        return try {
            if (OfflineArchitectureFlags.FIX_REPORT_REPOSITORY || contentType != ReportRepository.ContentType.PROFILE) {
                contentReportsDao.hasUserReported(reporterId, targetUserId)
            } else {
                legacyDataSource.hasExistingReport(reporterId, targetUserId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check existing reports")
            false
        }
    }
    
    override suspend fun getReportCount(userId: String): Int {
        return try {
            if (OfflineArchitectureFlags.FIX_REPORT_REPOSITORY) {
                contentReportsDao.getReportCount(userId)
            } else {
                legacyDataSource.getReportCount(userId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get report count")
            0
        }
    }
}
