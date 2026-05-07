package com.example.liftrix.domain.service

import com.example.liftrix.data.local.entity.ModerationActionEntity
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for content moderation operations.
 *
 * Provides admin moderation capabilities:
 * - Hide/delete content (posts, comments)
 * - Warn/suspend users
 * - Log moderation actions for audit trail
 * - Update content report statuses
 *
 * All operations require admin authentication (verified via Firebase custom claims).
 *
 * Part of Google Play UGC compliance (SPEC-20251230-google-play-compliance).
 */
interface ContentModerationService {

    /**
     * Hide content from public view.
     * Sets is_hidden flag, logs moderation action, updates report status.
     *
     * @param contentId Content ID (post ID or comment ID)
     * @param contentType Content type: POST or COMMENT
     * @param reason Moderation reason (displayed to content owner)
     * @param adminUserId Admin user ID (must have admin custom claim)
     * @param reportId Optional report ID that triggered this action
     * @return ModerationActionEntity or error
     */
    suspend fun hideContent(
        contentId: String,
        contentType: ContentType,
        reason: String,
        adminUserId: String,
        reportId: String? = null
    ): LiftrixResult<ModerationActionEntity>

    /**
     * Permanently delete content.
     * Deletes from Room + Firestore, logs action, updates report status.
     *
     * @param contentId Content ID
     * @param contentType Content type: POST or COMMENT
     * @param reason Deletion reason
     * @param adminUserId Admin user ID
     * @param reportId Optional report ID
     * @return Unit or error
     */
    suspend fun deleteContent(
        contentId: String,
        contentType: ContentType,
        reason: String,
        adminUserId: String,
        reportId: String? = null
    ): LiftrixResult<Unit>

    /**
     * Warn user about policy violation.
     * Creates AccountRestriction with type=WARNED, sends notification.
     *
     * @param userId User ID to warn
     * @param reason Warning reason
     * @param adminUserId Admin user ID
     * @param reportId Optional report ID
     * @return ModerationActionEntity or error
     */
    suspend fun warnUser(
        userId: String,
        reason: String,
        adminUserId: String,
        reportId: String? = null
    ): LiftrixResult<ModerationActionEntity>

    /**
     * Suspend user account.
     * Creates AccountRestriction with type=SUSPENDED, sets end time.
     *
     * @param userId User ID to suspend
     * @param reason Suspension reason
     * @param durationDays Suspension duration (null = permanent)
     * @param adminUserId Admin user ID
     * @param reportId Optional report ID
     * @return ModerationActionEntity or error
     */
    suspend fun suspendUser(
        userId: String,
        reason: String,
        durationDays: Int?,
        adminUserId: String,
        reportId: String? = null
    ): LiftrixResult<ModerationActionEntity>

    /**
     * Dismiss report without action.
     * Updates report status to DISMISSED, logs action.
     *
     * @param reportId Report ID to dismiss
     * @param reason Dismissal reason
     * @param adminUserId Admin user ID
     * @return ModerationActionEntity or error
     */
    suspend fun dismissReport(
        reportId: String,
        reason: String,
        adminUserId: String
    ): LiftrixResult<ModerationActionEntity>

    /**
     * Content types for moderation.
     */
    enum class ContentType {
        POST,
        COMMENT,
        PROFILE
    }

    /**
     * Action types for moderation.
     */
    enum class ActionType {
        HIDE_POST,
        DELETE_POST,
        HIDE_COMMENT,
        DELETE_COMMENT,
        WARN_USER,
        SUSPEND_USER,
        DISMISS_REPORT
    }
}
