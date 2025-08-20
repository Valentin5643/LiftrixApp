package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a sync operation in the offline queue.
 * 
 * Used to track pending sync operations when the device is offline or when sync operations fail.
 * Operations are prioritized and retried with exponential backoff.
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["priority", "created_at"]),
        Index(value = ["next_retry_at"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

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
    val priority: Int, // 1 = High, 2 = Medium, 3 = Low

    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: Long? = null
)