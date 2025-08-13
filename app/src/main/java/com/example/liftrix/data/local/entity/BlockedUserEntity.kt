package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing blocked user relationships.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * When a user is blocked, they cannot:
 * - View the blocker's profile
 * - Send follow requests to the blocker
 * - See the blocker in search results
 * - Receive notifications from the blocker
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "blocked_users",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["user_id", "blocked_user_id"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["blocked_user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BlockedUserEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "blocked_user_id")
    val blockedUserId: String,

    @ColumnInfo(name = "reason")
    val reason: String? = null,

    @ColumnInfo(name = "blocked_at")
    val blockedAt: Long,

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false
)