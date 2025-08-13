package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for managing user notification mutes.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "notification_mutes",
    indices = [
        Index(value = ["user_id", "mute_type"]),
        Index(value = ["muted_until"])
    ]
)
data class NotificationMuteEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    // Mute target
    @ColumnInfo(name = "mute_type")
    val muteType: String, // 'USER', 'CATEGORY', 'ALL'

    @ColumnInfo(name = "muted_user_id")
    val mutedUserId: String? = null,

    @ColumnInfo(name = "muted_category")
    val mutedCategory: String? = null,

    // Duration
    @ColumnInfo(name = "muted_until")
    val mutedUntil: Long? = null, // NULL for permanent

    // Timestamps
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)