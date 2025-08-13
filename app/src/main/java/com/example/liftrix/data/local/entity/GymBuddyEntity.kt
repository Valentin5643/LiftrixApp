package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing gym buddy relationships (inner circle connections).
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * Gym buddies are closer relationships than regular follows, typically established
 * through QR code pairing at the gym. They receive special notifications like PR alerts.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "gym_buddies",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["user_id", "buddy_id"], unique = true)
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
            childColumns = ["buddy_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GymBuddyEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "buddy_id")
    val buddyId: String,

    // Buddy metadata
    @ColumnInfo(name = "buddy_nickname")
    val buddyNickname: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_pr_notification_sent")
    val lastPrNotificationSent: Long? = null,

    @ColumnInfo(name = "notification_cooldown_hours", defaultValue = "24")
    val notificationCooldownHours: Int = 24,

    // QR code pairing
    @ColumnInfo(name = "paired_via_qr", defaultValue = "1")
    val pairedViaQr: Boolean = true,

    @ColumnInfo(name = "pairing_location")
    val pairingLocation: String? = null,

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Int = 0
)