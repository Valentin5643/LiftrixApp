package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a moderation action taken by an admin.
 *
 * Provides audit trail for all moderation decisions:
 * - HIDE_POST: Content hidden from public view
 * - DELETE_POST: Content permanently deleted
 * - HIDE_COMMENT: Comment hidden from public view
 * - DELETE_COMMENT: Comment permanently deleted
 * - WARN_USER: User warned about policy violations
 * - SUSPEND_USER: User account suspended
 * - DISMISS_REPORT: Report reviewed and dismissed
 *
 * All actions are logged for transparency and compliance.
 */
@Entity(
    tableName = "moderation_actions",
    indices = [
        Index(value = ["admin_user_id", "created_at"]),
        Index(value = ["target_type", "target_id"])
    ]
)
data class ModerationActionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /**
     * Admin user ID who performed the action.
     */
    @ColumnInfo(name = "admin_user_id")
    val adminUserId: String,

    /**
     * Type of action: HIDE_POST, DELETE_POST, WARN_USER, SUSPEND_USER, DISMISS_REPORT
     */
    @ColumnInfo(name = "action_type")
    val actionType: String,

    /**
     * Type of target: POST, COMMENT, USER
     */
    @ColumnInfo(name = "target_type")
    val targetType: String,

    /**
     * ID of the target entity (post ID, comment ID, user ID)
     */
    @ColumnInfo(name = "target_id")
    val targetId: String,

    /**
     * Reference to ContentReportEntity if action was taken from a report.
     */
    @ColumnInfo(name = "report_id")
    val reportId: String? = null,

    @ColumnInfo(name = "reason")
    val reason: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
