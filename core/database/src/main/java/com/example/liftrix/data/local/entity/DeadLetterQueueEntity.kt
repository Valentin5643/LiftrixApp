package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing permanently failed sync operations in the dead letter queue.
 *
 * Used to store operations that have exceeded retry limits or encountered non-retryable errors.
 * These items can be reviewed manually for debugging and potentially retried after fixes.
 */
@Entity(
    tableName = "dead_letter_queue",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["entity_type"]),
        Index(value = ["failed_at"]),
        Index(value = ["error_category"])
    ]
)
data class DeadLetterQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "original_id")
    val originalId: String, // Original sync queue item ID

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String, // "WORKOUT", "TEMPLATE", "PROFILE", "ACHIEVEMENT"

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "operation")
    val operation: String, // "CREATE", "UPDATE", "DELETE"

    @ColumnInfo(name = "data")
    val data: String, // JSON serialized entity data

    @ColumnInfo(name = "priority", defaultValue = "3")
    val priority: Int, // Original priority

    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int, // Final retry count when moved to dead letter

    @ColumnInfo(name = "created_at")
    val createdAt: Long, // Original creation time

    @ColumnInfo(name = "failed_at")
    val failedAt: Long, // When it was moved to dead letter queue

    @ColumnInfo(name = "error_category")
    val errorCategory: String, // Error category that caused permanent failure

    @ColumnInfo(name = "error_message")
    val errorMessage: String, // Last error message

    @ColumnInfo(name = "reviewed", defaultValue = "0")
    val reviewed: Boolean = false, // Whether this item has been manually reviewed

    @ColumnInfo(name = "reviewed_at")
    val reviewedAt: Long? = null, // When it was reviewed

    @ColumnInfo(name = "retry_after_fix", defaultValue = "0")
    val retryAfterFix: Boolean = false // Whether to retry this item after a fix
)