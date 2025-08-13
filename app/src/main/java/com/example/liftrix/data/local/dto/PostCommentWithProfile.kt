package com.example.liftrix.data.local.dto

import androidx.room.ColumnInfo

/**
 * Data transfer object for post comments with user profile information.
 * Used for efficient JOIN queries in DAOs.
 */
data class PostCommentWithProfile(
    val id: String,
    @ColumnInfo(name = "post_id") val postId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val content: String,
    @ColumnInfo(name = "reply_to_comment_id") val replyToCommentId: String?,
    @ColumnInfo(name = "like_count") val likeCount: Int,
    @ColumnInfo(name = "is_edited") val isEdited: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "edited_at") val editedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean,
    @ColumnInfo(name = "sync_version") val syncVersion: Int,
    val username: String?,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "profile_photo_url") val profilePhotoUrl: String?
)