package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for managing notification history for user viewing.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "notification_history",
    indices = [
        Index(value = ["user_id", "received_at"]),
        Index(value = ["is_read"]),
        // P0-PERF-001: Unread notifications priority - critical for notification center loading
        Index(value = ["user_id", "is_read", "received_at"], name = "idx_notification_history_unread"),
        // P0-PERF-001: Notification type filtering - supports category-based queries
        Index(value = ["user_id", "type", "received_at"], name = "idx_notification_history_type")
    ],
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NotificationHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    // Notification content
    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "data")
    val data: String? = null, // JSON payload

    // Interaction
    @ColumnInfo(name = "is_read", defaultValue = "0")
    val isRead: Boolean = false,

    @ColumnInfo(name = "read_at")
    val readAt: Long? = null,

    @ColumnInfo(name = "action_taken")
    val actionTaken: String? = null, // 'OPENED', 'DISMISSED', 'ACTED'

    // Timestamps
    @ColumnInfo(name = "received_at")
    val receivedAt: Long
)