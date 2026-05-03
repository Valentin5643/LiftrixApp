package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a comment on a workout post.
 * Supports nested replies through reply_to_comment_id.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "post_comments",
    indices = [
        Index(value = ["post_id", "created_at"], name = "idx_post_comments_post"),
        Index(value = ["user_id"], name = "idx_post_comments_user"),
        Index(value = ["reply_to_comment_id"]),
        // P0-PERF-001: User comment history - critical for "My Comments" activity feed
        Index(value = ["user_id", "created_at"], name = "idx_post_comments_user_timeline"),
        // P0-PERF-001: Reply threading - optimizes nested comment loading
        Index(value = ["reply_to_comment_id", "created_at"], name = "idx_post_comments_replies")
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
        ),
        ForeignKey(
            entity = PostCommentEntity::class,
            parentColumns = ["id"],
            childColumns = ["reply_to_comment_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PostCommentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "post_id")
    val postId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    // Content
    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "reply_to_comment_id")
    val replyToCommentId: String? = null, // For nested replies

    // Metadata
    @ColumnInfo(name = "like_count", defaultValue = "0")
    val likeCount: Int = 0,

    @ColumnInfo(name = "is_edited", defaultValue = "0")
    val isEdited: Boolean = false,

    // Timestamps
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "edited_at")
    val editedAt: Long? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Long = 0L,

    // Offline-first architecture fields (SPEC-20241228)
    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false,

    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = 0L
)