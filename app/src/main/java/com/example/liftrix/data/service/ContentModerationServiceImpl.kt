package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.AccountRestrictionDao
import com.example.liftrix.data.local.dao.ContentReportsDao
import com.example.liftrix.data.local.dao.ModerationActionDao
import com.example.liftrix.data.local.dao.PostCommentDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.entity.AccountRestrictionEntity
import com.example.liftrix.data.local.entity.ContentReportEntity
import com.example.liftrix.data.local.entity.ModerationActionEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.ContentModerationService
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ContentModerationService for admin moderation operations.
 *
 * Provides:
 * - Content hiding/deletion (posts, comments)
 * - User warnings/suspensions
 * - Moderation action audit logging
 * - Content report status updates
 *
 * IMPORTANT: All operations require admin authentication (verified at API layer).
 * This service assumes admin check has already been performed.
 *
 * Part of Google Play UGC compliance (SPEC-20251230-google-play-compliance).
 */
@Singleton
class ContentModerationServiceImpl @Inject constructor(
    private val workoutPostDao: WorkoutPostDao,
    private val postCommentDao: PostCommentDao,
    private val moderationActionDao: ModerationActionDao,
    private val accountRestrictionDao: AccountRestrictionDao,
    private val contentReportsDao: ContentReportsDao
) : ContentModerationService {

    override suspend fun hideContent(
        contentId: String,
        contentType: ContentModerationService.ContentType,
        reason: String,
        adminUserId: String,
        reportId: String?
    ): LiftrixResult<ModerationActionEntity> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "HIDE_CONTENT_FAILED",
                errorMessage = "Failed to hide content: ${throwable.message}",
                analyticsContext = mapOf(
                    "admin_user_id" to adminUserId,
                    "content_id" to contentId,
                    "content_type" to contentType.name
                )
            )
        }
    ) {
        val currentTime = System.currentTimeMillis()

        // Update content with hidden flag
        when (contentType) {
            ContentModerationService.ContentType.POST -> {
                val post = workoutPostDao.getPostById(contentId)
                    ?: throw IllegalArgumentException("Post not found: $contentId")

                val updatedPost = post.copy(
                    isHidden = true,
                    hiddenReason = reason,
                    hiddenAt = currentTime,
                    hiddenByUserId = adminUserId,
                    isDirty = true, // Trigger sync to Firestore
                    lastModified = currentTime
                )
                workoutPostDao.updatePost(updatedPost)

                Timber.i("Post hidden: $contentId by admin $adminUserId")
            }
            ContentModerationService.ContentType.COMMENT -> {
                // Follow-up: Add is_hidden fields to PostCommentEntity (requires DB migration)
                // For now, delete comment instead of hiding
                val comment = postCommentDao.getCommentById(contentId)
                    ?: throw IllegalArgumentException("Comment not found: $contentId")

                postCommentDao.deleteComment(comment)
                Timber.w("Comment deleted (hiding not yet implemented): $contentId by admin $adminUserId")
            }
            ContentModerationService.ContentType.PROFILE -> {
                throw UnsupportedOperationException("Profile hiding not yet implemented")
            }
        }

        // Create moderation action audit log
        val action = ModerationActionEntity(
            id = UUID.randomUUID().toString(),
            adminUserId = adminUserId,
            actionType = when (contentType) {
                ContentModerationService.ContentType.POST -> "HIDE_POST"
                ContentModerationService.ContentType.COMMENT -> "HIDE_COMMENT"
                ContentModerationService.ContentType.PROFILE -> "HIDE_PROFILE"
            },
            targetType = contentType.name,
            targetId = contentId,
            reportId = reportId,
            reason = reason,
            createdAt = currentTime
        )
        moderationActionDao.insert(action)

        // Update related reports to ACTIONED status
        if (reportId != null) {
            contentReportsDao.updateReportStatus(
                reportId = reportId,
                status = ContentReportEntity.STATUS_ACTIONED,
                reviewedBy = adminUserId,
                reviewedAt = currentTime,
                actionTaken = action.actionType
            )

            Timber.i("Updated report $reportId status to ACTIONED")
        }

        // Update all related reports for this content
        val relatedReports = contentReportsDao.getReportsForContent(contentId)
        relatedReports.forEach { report ->
            if (report.status == ContentReportEntity.STATUS_PENDING) {
                contentReportsDao.updateReportStatus(
                    reportId = report.id,
                    status = ContentReportEntity.STATUS_ACTIONED,
                    reviewedBy = adminUserId,
                    reviewedAt = currentTime,
                    actionTaken = action.actionType
                )
            }
        }

        Timber.i("Moderation action created: ${action.id} - ${action.actionType}")

        action
    }

    override suspend fun deleteContent(
        contentId: String,
        contentType: ContentModerationService.ContentType,
        reason: String,
        adminUserId: String,
        reportId: String?
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DELETE_CONTENT_FAILED",
                errorMessage = "Failed to delete content: ${throwable.message}",
                analyticsContext = mapOf(
                    "admin_user_id" to adminUserId,
                    "content_id" to contentId,
                    "content_type" to contentType.name
                )
            )
        }
    ) {
        val currentTime = System.currentTimeMillis()

        // Delete content permanently
        when (contentType) {
            ContentModerationService.ContentType.POST -> {
                val post = workoutPostDao.getPostById(contentId)
                    ?: throw IllegalArgumentException("Post not found: $contentId")

                workoutPostDao.deletePost(post)
                Timber.i("Post deleted: $contentId by admin $adminUserId")
            }
            ContentModerationService.ContentType.COMMENT -> {
                val comment = postCommentDao.getCommentById(contentId)
                    ?: throw IllegalArgumentException("Comment not found: $contentId")

                postCommentDao.deleteComment(comment)
                Timber.i("Comment deleted: $contentId by admin $adminUserId")
            }
            ContentModerationService.ContentType.PROFILE -> {
                throw UnsupportedOperationException("Profile deletion requires full account deletion flow")
            }
        }

        // Create moderation action audit log
        val action = ModerationActionEntity(
            id = UUID.randomUUID().toString(),
            adminUserId = adminUserId,
            actionType = when (contentType) {
                ContentModerationService.ContentType.POST -> "DELETE_POST"
                ContentModerationService.ContentType.COMMENT -> "DELETE_COMMENT"
                ContentModerationService.ContentType.PROFILE -> "DELETE_PROFILE"
            },
            targetType = contentType.name,
            targetId = contentId,
            reportId = reportId,
            reason = reason,
            createdAt = currentTime
        )
        moderationActionDao.insert(action)

        // Update related reports
        if (reportId != null) {
            contentReportsDao.updateReportStatus(
                reportId = reportId,
                status = ContentReportEntity.STATUS_ACTIONED,
                reviewedBy = adminUserId,
                reviewedAt = currentTime,
                actionTaken = action.actionType
            )
        }

        // Update all related reports for this content
        val relatedReports = contentReportsDao.getReportsForContent(contentId)
        relatedReports.forEach { report ->
            if (report.status == ContentReportEntity.STATUS_PENDING) {
                contentReportsDao.updateReportStatus(
                    reportId = report.id,
                    status = ContentReportEntity.STATUS_ACTIONED,
                    reviewedBy = adminUserId,
                    reviewedAt = currentTime,
                    actionTaken = action.actionType
                )
            }
        }

        Timber.i("Moderation action created: ${action.id} - ${action.actionType}")
    }

    override suspend fun warnUser(
        userId: String,
        reason: String,
        adminUserId: String,
        reportId: String?
    ): LiftrixResult<ModerationActionEntity> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "WARN_USER_FAILED",
                errorMessage = "Failed to warn user: ${throwable.message}",
                analyticsContext = mapOf(
                    "admin_user_id" to adminUserId,
                    "target_user_id" to userId
                )
            )
        }
    ) {
        val currentTime = System.currentTimeMillis()

        // Create account restriction (warning)
        val restriction = AccountRestrictionEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            restrictionType = "WARNED",
            reason = reason,
            startTime = currentTime,
            endTime = null, // Warnings don't expire
            createdBy = adminUserId,
            createdAt = currentTime
        )
        accountRestrictionDao.insert(restriction)

        // Create moderation action audit log
        val action = ModerationActionEntity(
            id = UUID.randomUUID().toString(),
            adminUserId = adminUserId,
            actionType = "WARN_USER",
            targetType = "USER",
            targetId = userId,
            reportId = reportId,
            reason = reason,
            createdAt = currentTime
        )
        moderationActionDao.insert(action)

        // Update report if provided
        if (reportId != null) {
            contentReportsDao.updateReportStatus(
                reportId = reportId,
                status = ContentReportEntity.STATUS_ACTIONED,
                reviewedBy = adminUserId,
                reviewedAt = currentTime,
                actionTaken = action.actionType
            )
        }

        Timber.i("User warned: $userId by admin $adminUserId")

        // Follow-up: Send notification to user about warning
        // notificationService.sendWarningNotification(userId, reason)

        action
    }

    override suspend fun suspendUser(
        userId: String,
        reason: String,
        durationDays: Int?,
        adminUserId: String,
        reportId: String?
    ): LiftrixResult<ModerationActionEntity> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SUSPEND_USER_FAILED",
                errorMessage = "Failed to suspend user: ${throwable.message}",
                analyticsContext = mapOf(
                    "admin_user_id" to adminUserId,
                    "target_user_id" to userId,
                    "duration_days" to (durationDays?.toString() ?: "permanent")
                )
            )
        }
    ) {
        val currentTime = System.currentTimeMillis()
        val endTime = durationDays?.let {
            currentTime + (it * 24 * 60 * 60 * 1000L)
        } // null = permanent suspension

        // Create account restriction (suspension)
        val restriction = AccountRestrictionEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            restrictionType = "SUSPENDED",
            reason = reason,
            startTime = currentTime,
            endTime = endTime,
            createdBy = adminUserId,
            createdAt = currentTime
        )
        accountRestrictionDao.insert(restriction)

        // Create moderation action audit log
        val action = ModerationActionEntity(
            id = UUID.randomUUID().toString(),
            adminUserId = adminUserId,
            actionType = "SUSPEND_USER",
            targetType = "USER",
            targetId = userId,
            reportId = reportId,
            reason = reason,
            createdAt = currentTime
        )
        moderationActionDao.insert(action)

        // Update report if provided
        if (reportId != null) {
            contentReportsDao.updateReportStatus(
                reportId = reportId,
                status = ContentReportEntity.STATUS_ACTIONED,
                reviewedBy = adminUserId,
                reviewedAt = currentTime,
                actionTaken = action.actionType
            )
        }

        val suspensionType = if (durationDays == null) "permanently" else "for $durationDays days"
        Timber.i("User suspended $suspensionType: $userId by admin $adminUserId")

        // Follow-up: Send notification to user about suspension
        // notificationService.sendSuspensionNotification(userId, reason, endTime)

        action
    }

    override suspend fun dismissReport(
        reportId: String,
        reason: String,
        adminUserId: String
    ): LiftrixResult<ModerationActionEntity> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DISMISS_REPORT_FAILED",
                errorMessage = "Failed to dismiss report: ${throwable.message}",
                analyticsContext = mapOf(
                    "admin_user_id" to adminUserId,
                    "report_id" to reportId
                )
            )
        }
    ) {
        val currentTime = System.currentTimeMillis()

        // Get report to extract target info
        val report = contentReportsDao.getReportsForContent(reportId).firstOrNull()
            ?: throw IllegalArgumentException("Report not found: $reportId")

        // Update report status to DISMISSED
        contentReportsDao.updateReportStatus(
            reportId = reportId,
            status = ContentReportEntity.STATUS_DISMISSED,
            reviewedBy = adminUserId,
            reviewedAt = currentTime,
            actionTaken = "DISMISSED"
        )

        // Create moderation action audit log
        val action = ModerationActionEntity(
            id = UUID.randomUUID().toString(),
            adminUserId = adminUserId,
            actionType = "DISMISS_REPORT",
            targetType = report.contentType,
            targetId = report.contentId,
            reportId = reportId,
            reason = reason,
            createdAt = currentTime
        )
        moderationActionDao.insert(action)

        Timber.i("Report dismissed: $reportId by admin $adminUserId - reason: $reason")

        action
    }
}

