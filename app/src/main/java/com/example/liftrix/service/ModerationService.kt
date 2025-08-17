package com.example.liftrix.service

import com.example.liftrix.data.local.dao.ContentReportsDao
import com.example.liftrix.data.local.entity.ContentReportEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.ContentReport
import com.example.liftrix.domain.model.social.ContentType
import com.example.liftrix.domain.model.social.ReportReason
import com.example.liftrix.domain.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for content moderation and reporting.
 * Part of social privacy and moderation system from SPEC-20250116-social-privacy-moderation.
 * 
 * This service handles content reporting, automatic hiding based on thresholds,
 * and syncing reports to Firebase for admin review.
 */
@Singleton
class ModerationService @Inject constructor(
    private val contentReportsDao: ContentReportsDao,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {

    companion object {
        private const val AUTO_HIDE_THRESHOLD = 5 // Hide content after 5 reports
    }

    /**
     * Report content for moderation review.
     * 
     * @param contentType Type of content being reported (POST, COMMENT, PROFILE)
     * @param contentId ID of the content being reported
     * @param reason Reason for reporting
     * @param description Optional additional description
     * @return LiftrixResult indicating success or failure
     */
    suspend fun reportContent(
        contentType: ContentType,
        contentId: String,
        reason: ReportReason,
        description: String? = null
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "REPORT_CONTENT_FAILED",
                errorMessage = "Failed to report content: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "REPORT_CONTENT",
                    "contentType" to contentType.name,
                    "contentId" to contentId,
                    "reason" to reason.name
                )
            )
        }
    ) {
        val reporterId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User not authenticated")

        // Check if user has already reported this content
        if (contentReportsDao.hasUserReported(reporterId, contentId)) {
            throw IllegalStateException("Content already reported by this user")
        }

        // Create report
        val report = ContentReportEntity(
            id = UUID.randomUUID().toString(),
            reporterUserId = reporterId,
            contentType = contentType.name,
            contentId = contentId,
            reason = reason.name,
            description = description,
            reportedAt = System.currentTimeMillis(),
            status = ContentReportEntity.STATUS_PENDING
        )

        // Save locally
        contentReportsDao.insert(report)

        // Sync to Firebase for admin review
        try {
            firestore.collection("content_reports")
                .document(report.id)
                .set(report.toFirebaseMap())
                .await()

            // Mark as synced
            contentReportsDao.markAsSynced(listOf(report.id))
            
            Timber.d("Successfully synced report to Firebase: ${report.id}")
        } catch (e: Exception) {
            Timber.w("Failed to sync report to Firebase: ${e.message}")
            // Report is still saved locally, sync will be retried later
        }

        // Check if content should be auto-hidden
        val reportCount = contentReportsDao.getReportCount(contentId)
        if (reportCount >= AUTO_HIDE_THRESHOLD) {
            hideContent(contentType, contentId)
        }

        Timber.i("Content reported: $contentId by $reporterId (reason: ${reason.name})")
    }

    /**
     * Get all report reasons available for selection.
     * 
     * @return List of available report reasons
     */
    fun getReportReasons(): List<ReportReason> = listOf(
        ReportReason.SPAM,
        ReportReason.INAPPROPRIATE_CONTENT,
        ReportReason.HARASSMENT,
        ReportReason.MISINFORMATION,
        ReportReason.COPYRIGHT,
        ReportReason.OTHER
    )

    /**
     * Get reports submitted by the current user.
     * 
     * @return LiftrixResult containing list of user's reports
     */
    suspend fun getUserReports(): LiftrixResult<List<ContentReport>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_USER_REPORTS_FAILED",
                errorMessage = "Failed to get user reports: ${throwable.message}",
                analyticsContext = mapOf("operation" to "GET_USER_REPORTS")
            )
        }
    ) {
        val userId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User not authenticated")

        contentReportsDao.getReportsByUser(userId)
            .map { it.toDomainModel() }
    }

    /**
     * Check if content has been reported by the current user.
     * 
     * @param contentId ID of the content to check
     * @return LiftrixResult containing true if reported, false otherwise
     */
    suspend fun hasUserReportedContent(contentId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CHECK_REPORT_STATUS_FAILED",
                errorMessage = "Failed to check report status: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CHECK_REPORT_STATUS",
                    "contentId" to contentId
                )
            )
        }
    ) {
        val userId = authRepository.getCurrentUserId()
            ?: return@liftrixCatching false

        contentReportsDao.hasUserReported(userId, contentId)
    }

    /**
     * Get report count for specific content.
     * 
     * @param contentId ID of the content
     * @return LiftrixResult containing report count
     */
    suspend fun getContentReportCount(contentId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GET_REPORT_COUNT_FAILED",
                errorMessage = "Failed to get report count: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_REPORT_COUNT",
                    "contentId" to contentId
                )
            )
        }
    ) {
        contentReportsDao.getReportCount(contentId)
    }

    /**
     * Sync unsynced reports to Firebase.
     * Called periodically by sync service.
     */
    suspend fun syncUnsyncedReports(): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SYNC_REPORTS_FAILED",
                errorMessage = "Failed to sync reports: ${throwable.message}",
                analyticsContext = mapOf("operation" to "SYNC_REPORTS")
            )
        }
    ) {
        val unsyncedReports = contentReportsDao.getUnsyncedReports()
        
        if (unsyncedReports.isEmpty()) {
            return@liftrixCatching
        }

        var syncedCount = 0
        val syncedIds = mutableListOf<String>()
        
        for (report in unsyncedReports) {
            try {
                firestore.collection("content_reports")
                    .document(report.id)
                    .set(report.toFirebaseMap())
                    .await()

                syncedIds.add(report.id)
                syncedCount++
            } catch (e: Exception) {
                Timber.w("Failed to sync report ${report.id}: ${e.message}")
                // Continue with other reports
            }
        }

        if (syncedIds.isNotEmpty()) {
            contentReportsDao.markAsSynced(syncedIds)
        }

        Timber.i("Synced $syncedCount of ${unsyncedReports.size} reports to Firebase")
    }

    /**
     * Hide content that has reached the auto-hide threshold.
     * 
     * @param contentType Type of content to hide
     * @param contentId ID of the content to hide
     */
    private suspend fun hideContent(
        contentType: ContentType,
        contentId: String
    ) {
        try {
            when (contentType) {
                ContentType.POST -> {
                    // Mark post as hidden
                    firestore.collection("workout_posts")
                        .document(contentId)
                        .update("isHidden", true, "hiddenAt", System.currentTimeMillis())
                        .await()
                        
                    Timber.i("Auto-hidden post $contentId due to report threshold")
                }
                ContentType.COMMENT -> {
                    // Mark comment as deleted
                    firestore.collection("post_comments")
                        .document(contentId)
                        .update("isDeleted", true, "deletedAt", System.currentTimeMillis())
                        .await()
                        
                    Timber.i("Auto-deleted comment $contentId due to report threshold")
                }
                ContentType.PROFILE -> {
                    // Flag profile for review (don't auto-hide profiles)
                    firestore.collection("users")
                        .document(contentId)
                        .update("flaggedForReview", true, "flaggedAt", System.currentTimeMillis())
                        .await()
                        
                    Timber.i("Flagged profile $contentId for review due to report threshold")
                }
            }
        } catch (e: Exception) {
            Timber.w("Failed to hide content $contentId: ${e.message}")
            // Don't throw - hiding failure shouldn't fail the report submission
        }
    }

    /**
     * Process content that has reached the reporting threshold.
     * Called periodically to check for content that needs auto-hiding.
     */
    suspend fun processContentReachingThreshold(): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROCESS_THRESHOLD_CONTENT_FAILED",
                errorMessage = "Failed to process threshold content: ${throwable.message}",
                analyticsContext = mapOf("operation" to "PROCESS_THRESHOLD_CONTENT")
            )
        }
    ) {
        val contentReachingThreshold = contentReportsDao.getContentReachingThreshold(AUTO_HIDE_THRESHOLD)
        
        for (content in contentReachingThreshold) {
            val contentType = ContentType.valueOf(content.contentType)
            hideContent(contentType, content.contentId)
            
            // Mark reports for this content as reviewed
            contentReportsDao.getReportsForContent(content.contentId).forEach { report ->
                contentReportsDao.updateReportStatus(
                    reportId = report.id,
                    status = ContentReportEntity.STATUS_ACTIONED,
                    reviewedBy = "SYSTEM",
                    reviewedAt = System.currentTimeMillis(),
                    actionTaken = "AUTO_HIDDEN"
                )
            }
        }

        if (contentReachingThreshold.isNotEmpty()) {
            Timber.i("Processed ${contentReachingThreshold.size} content items reaching threshold")
        }
    }

    /**
     * Clean up old processed reports.
     * Called periodically for maintenance.
     */
    suspend fun cleanupOldReports(): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CLEANUP_REPORTS_FAILED",
                errorMessage = "Failed to cleanup old reports: ${throwable.message}",
                analyticsContext = mapOf("operation" to "CLEANUP_REPORTS")
            )
        }
    ) {
        // Delete reports older than 90 days that are already processed
        val ninetyDaysAgo = System.currentTimeMillis() - (90 * 24 * 60 * 60 * 1000L)
        contentReportsDao.deleteOldReports(ninetyDaysAgo)
        
        Timber.d("Cleaned up old reports")
    }
}

/**
 * Extension function to convert ContentReportEntity to domain model
 */
private fun ContentReportEntity.toDomainModel(): ContentReport = ContentReport(
    id = id,
    reporterUserId = reporterUserId,
    contentType = ContentType.valueOf(contentType),
    contentId = contentId,
    reason = ReportReason.valueOf(reason),
    description = description,
    reportedAt = reportedAt,
    status = status
)