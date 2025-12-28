package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing follow relationships between users.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * Status values:
 * - PENDING: Follow request sent but not yet accepted
 * - ACCEPTED: Follow request accepted, users are now connected
 * - BLOCKED: User has blocked the follower
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "follow_relationships",
    indices = [
        Index(value = ["follower_id", "status"]),
        Index(value = ["following_id", "status"]),
        Index(value = ["follower_id", "following_id"], unique = true),
        // Composite indexes for relationship queries
        Index(value = ["follower_id", "status", "created_at"], 
              name = "idx_follow_relationships_follower_timeline"),
        Index(value = ["following_id", "status", "created_at"], 
              name = "idx_follow_relationships_following_timeline"),
        Index(value = ["status", "accepted_at"], 
              name = "idx_follow_relationships_accepted")
    ],
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["follower_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["following_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FollowRelationshipEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "follower_id")
    val followerId: String,

    @ColumnInfo(name = "following_id")
    val followingId: String,

    @ColumnInfo(name = "status")
    val status: String, // PENDING, ACCEPTED, BLOCKED

    // Relationship metadata
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "accepted_at")
    val acceptedAt: Long? = null,

    @ColumnInfo(name = "blocked_at")
    val blockedAt: Long? = null,

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Int = 0,

    @ColumnInfo(name = "last_modified", defaultValue = "CURRENT_TIMESTAMP")
    val lastModified: Long = System.currentTimeMillis(),

    // Offline-first architecture fields (SPEC-20241228)
    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACCEPTED = "ACCEPTED"
        const val STATUS_BLOCKED = "BLOCKED"
    }
}