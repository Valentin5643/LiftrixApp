package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a like on a workout post.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "post_likes",
    indices = [
        Index(value = ["post_id"], name = "idx_post_likes_post"),
        Index(value = ["user_id"], name = "idx_post_likes_user"),
        Index(value = ["post_id", "user_id"], unique = true, name = "idx_post_likes_unique"),
        // P0-PERF-001: User activity timeline - critical for "My Liked Posts" feed
        Index(value = ["user_id", "created_at"], name = "idx_post_likes_user_timeline"),
        // P0-PERF-001: Post engagement chronology - supports "Recent Likes" on post detail
        Index(value = ["post_id", "created_at"], name = "idx_post_likes_post_timeline")
    ],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["post_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PostLikeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "post_id")
    val postId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false,
    
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = 0L,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Long = 0L,

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false
)
