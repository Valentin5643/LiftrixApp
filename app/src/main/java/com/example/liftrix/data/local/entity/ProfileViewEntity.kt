package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing profile view tracking for analytics and user suggestions.
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * This entity tracks when users view other users' profiles to:
 * - Generate analytics insights
 * - Improve user suggestion algorithms
 * - Track profile engagement metrics
 * 
 * Security Note: All queries against this table MUST include viewer_id filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "profile_views",
    indices = [
        Index(value = ["viewer_id", "viewed_at"]),
        Index(value = ["profile_id", "viewed_at"]),
        Index(value = ["viewer_id", "profile_id", "viewed_at"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["viewer_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProfileViewEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "viewer_id")
    val viewerId: String,

    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "viewed_at")
    val viewedAt: Long,

    // View context for analytics
    @ColumnInfo(name = "view_source")
    val viewSource: String, // SEARCH, SUGGESTIONS, PROFILE_LINK, MUTUAL_CONNECTIONS

    @ColumnInfo(name = "view_duration_ms")
    val viewDurationMs: Long? = null,

    @ColumnInfo(name = "interaction_type")
    val interactionType: String? = null, // FOLLOW, UNFOLLOW, MESSAGE, NONE

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {
    companion object {
        const val VIEW_SOURCE_SEARCH = "SEARCH"
        const val VIEW_SOURCE_SUGGESTIONS = "SUGGESTIONS"
        const val VIEW_SOURCE_PROFILE_LINK = "PROFILE_LINK"
        const val VIEW_SOURCE_MUTUAL_CONNECTIONS = "MUTUAL_CONNECTIONS"
        const val VIEW_SOURCE_FOLLOWERS_LIST = "FOLLOWERS_LIST"
        const val VIEW_SOURCE_FOLLOWING_LIST = "FOLLOWING_LIST"
        
        const val INTERACTION_FOLLOW = "FOLLOW"
        const val INTERACTION_UNFOLLOW = "UNFOLLOW"
        const val INTERACTION_MESSAGE = "MESSAGE"
        const val INTERACTION_NONE = "NONE"
    }
}