package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for managing notification queue for batching and scheduling.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "notification_queue",
    indices = [
        Index(value = ["user_id", "status", "scheduled_for"]),
        Index(value = ["batch_key"]),
        Index(value = ["expires_at"])
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
data class NotificationQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    // Notification data
    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "data")
    val data: String? = null, // JSON payload

    // Routing
    @ColumnInfo(name = "priority", defaultValue = "'NORMAL'")
    val priority: String = "NORMAL", // 'HIGH', 'NORMAL', 'LOW'

    @ColumnInfo(name = "channel_id")
    val channelId: String,

    // Batching
    @ColumnInfo(name = "batch_key")
    val batchKey: String? = null, // For grouping similar notifications

    @ColumnInfo(name = "can_batch", defaultValue = "1")
    val canBatch: Boolean = true,

    // Scheduling
    @ColumnInfo(name = "scheduled_for")
    val scheduledFor: Long? = null,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,

    // Status
    @ColumnInfo(name = "status", defaultValue = "'PENDING'")
    val status: String = "PENDING", // 'PENDING', 'SENT', 'FAILED', 'EXPIRED'

    @ColumnInfo(name = "sent_at")
    val sentAt: Long? = null,

    @ColumnInfo(name = "failure_reason")
    val failureReason: String? = null,

    // Creation
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)