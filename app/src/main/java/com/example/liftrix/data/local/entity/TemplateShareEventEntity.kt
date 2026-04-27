package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_share_events",
    indices = [
        Index(value = ["sender_id", "receiver_id", "status"], name = "idx_template_share_sender_receiver_status"),
        Index(value = ["sender_id", "status"], name = "idx_template_share_sender_status"),
        Index(value = ["receiver_id", "status"], name = "idx_template_share_receiver_status")
    ]
)
data class TemplateShareEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "sender_id")
    val senderId: String,
    @ColumnInfo(name = "receiver_id")
    val receiverId: String?,
    @ColumnInfo(name = "template_id")
    val templateId: String,
    @ColumnInfo(name = "delivery_mode")
    val deliveryMode: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,
    @ColumnInfo(name = "accepted_at")
    val acceptedAt: Long?,
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,
    @ColumnInfo(name = "is_dirty", defaultValue = "1")
    val isDirty: Boolean = true,
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = System.currentTimeMillis()
)

