package com.example.liftrix.data.local.dto

import androidx.room.ColumnInfo

/**
 * Data transfer object for post likes with user profile information.
 * Used for efficient JOIN queries in DAOs.
 */
data class PostLikeWithProfile(
    val id: String,
    @ColumnInfo(name = "post_id") val postId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean,
    val username: String?,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "profile_photo_url") val profilePhotoUrl: String?
)