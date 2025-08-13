package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing pending follow requests in a dedicated queue table.
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * This entity manages follow requests that are pending approval:
 * - Tracks requests sent to private profiles
 * - Maintains request metadata and timestamps
 * - Handles request expiration and cleanup
 * 
 * Note: This table is separate from FollowRelationshipEntity to optimize for:
 * - Fast retrieval of pending requests
 * - Easy cleanup of expired/processed requests
 * - Better separation of concerns between relationships and requests
 * 
 * Security Note: All queries against this table MUST include user filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "follow_requests",
    indices = [
        Index(value = ["requester_id", "status"]),
        Index(value = ["target_id", "status"]),
        Index(value = ["requester_id", "target_id"], unique = true),
        Index(value = ["created_at"]),
        Index(value = ["expires_at"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["requester_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["target_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FollowRequestEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "requester_id")
    val requesterId: String,

    @ColumnInfo(name = "target_id")
    val targetId: String,

    @ColumnInfo(name = "status")
    val status: String, // PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED

    @ColumnInfo(name = "request_message")
    val requestMessage: String? = null,

    // Timestamps
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "processed_at")
    val processedAt: Long? = null,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long, // Auto-decline after 30 days

    // Request context for analytics
    @ColumnInfo(name = "request_source")
    val requestSource: String, // PROFILE_VIEW, SEARCH, SUGGESTIONS, MUTUAL_CONNECTION

    @ColumnInfo(name = "notification_sent", defaultValue = "0")
    val notificationSent: Boolean = false,

    @ColumnInfo(name = "reminder_count", defaultValue = "0")
    val reminderCount: Int = 0,

    @ColumnInfo(name = "last_reminder_at")
    val lastReminderAt: Long? = null,

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Int = 0,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACCEPTED = "ACCEPTED"
        const val STATUS_DECLINED = "DECLINED"
        const val STATUS_EXPIRED = "EXPIRED"
        const val STATUS_CANCELLED = "CANCELLED"
        
        const val REQUEST_SOURCE_PROFILE_VIEW = "PROFILE_VIEW"
        const val REQUEST_SOURCE_SEARCH = "SEARCH"
        const val REQUEST_SOURCE_SUGGESTIONS = "SUGGESTIONS"
        const val REQUEST_SOURCE_MUTUAL_CONNECTION = "MUTUAL_CONNECTION"
        const val REQUEST_SOURCE_QR_CODE = "QR_CODE"
        
        // 30 days in milliseconds
        const val REQUEST_EXPIRATION_TIME = 30L * 24L * 60L * 60L * 1000L
        
        // Max reminders per request
        const val MAX_REMINDER_COUNT = 3
        
        // Reminder intervals (7 days)
        const val REMINDER_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}