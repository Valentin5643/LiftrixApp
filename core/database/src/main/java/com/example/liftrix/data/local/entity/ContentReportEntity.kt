package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing content reports for moderation.
 * Part of social privacy and moderation system from SPEC-20250116-social-privacy-moderation.
 * 
 * This entity stores reports submitted by users about inappropriate content,
 * including posts, comments, and profiles. Reports are reviewed by moderators
 * and can trigger automatic hiding based on thresholds.
 * 
 * Security Note: All queries against this table MUST include proper filtering 
 * to prevent unauthorized access to report data.
 */
@Entity(
    tableName = "content_reports",
    indices = [
        Index(value = ["status"]),
        Index(value = ["content_type", "content_id"]),
        Index(value = ["reporter_user_id"]),
        Index(value = ["reported_at"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["reporter_user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ContentReportEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "reporter_user_id")
    val reporterUserId: String,

    @ColumnInfo(name = "content_type")
    val contentType: String, // POST, COMMENT, PROFILE

    @ColumnInfo(name = "content_id")
    val contentId: String,

    @ColumnInfo(name = "reason")
    val reason: String, // SPAM, INAPPROPRIATE, HARASSMENT, OTHER

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "reported_at")
    val reportedAt: Long,

    @ColumnInfo(name = "status", defaultValue = "PENDING")
    val status: String = "PENDING", // PENDING, REVIEWED, ACTIONED, DISMISSED

    @ColumnInfo(name = "reviewed_by")
    val reviewedBy: String? = null,

    @ColumnInfo(name = "reviewed_at")
    val reviewedAt: Long? = null,

    @ColumnInfo(name = "action_taken")
    val actionTaken: String? = null,

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Long = 0L,
    
    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false,
    
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = 0L
) {
    companion object {
        // Content types
        const val CONTENT_TYPE_POST = "POST"
        const val CONTENT_TYPE_COMMENT = "COMMENT"
        const val CONTENT_TYPE_PROFILE = "PROFILE"

        // Report reasons
        const val REASON_SPAM = "SPAM"
        const val REASON_INAPPROPRIATE = "INAPPROPRIATE"
        const val REASON_HARASSMENT = "HARASSMENT"
        const val REASON_MISINFORMATION = "MISINFORMATION"
        const val REASON_COPYRIGHT = "COPYRIGHT"
        const val REASON_OTHER = "OTHER"

        // Report statuses
        const val STATUS_PENDING = "PENDING"
        const val STATUS_REVIEWED = "REVIEWED"
        const val STATUS_ACTIONED = "ACTIONED"
        const val STATUS_DISMISSED = "DISMISSED"

        // Auto-hide threshold
        const val AUTO_HIDE_THRESHOLD = 5
    }

    /**
     * Converts to Firebase map for syncing
     */
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "reporterUserId" to reporterUserId,
        "contentType" to contentType,
        "contentId" to contentId,
        "reason" to reason,
        "description" to description,
        "reportedAt" to reportedAt,
        "status" to status,
        "reviewedBy" to reviewedBy,
        "reviewedAt" to reviewedAt,
        "actionTaken" to actionTaken
    )
}
