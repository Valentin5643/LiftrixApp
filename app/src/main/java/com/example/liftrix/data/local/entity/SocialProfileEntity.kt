package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a user's social profile in the local database.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "social_profiles",
    indices = [
        Index(value = ["user_id"], unique = true),
        Index(value = ["username"], unique = true),
        Index(value = ["member_since"]),
        // Composite indexes for social queries
        Index(value = ["is_private", "hide_from_suggestions", "last_active"], 
              name = "idx_social_profiles_discovery"),
        Index(value = ["workout_count", "follower_count"], 
              name = "idx_social_profiles_popularity"),
        Index(value = ["username", "display_name"], 
              name = "idx_social_profiles_search")
    ],
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SocialProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "display_name")
    val displayName: String? = null,

    @ColumnInfo(name = "bio")
    val bio: String? = null,

    @ColumnInfo(name = "profile_photo_url")
    val profilePhotoUrl: String? = null,

    @ColumnInfo(name = "cover_photo_url")
    val coverPhotoUrl: String? = null,

    // Social stats
    @ColumnInfo(name = "workout_count", defaultValue = "0")
    val workoutCount: Int = 0,

    @ColumnInfo(name = "follower_count", defaultValue = "0")
    val followerCount: Int = 0,

    @ColumnInfo(name = "following_count", defaultValue = "0")
    val followingCount: Int = 0,

    // Profile metadata
    @ColumnInfo(name = "member_since")
    val memberSince: Long,

    @ColumnInfo(name = "last_active")
    val lastActive: Long? = null,

    @ColumnInfo(name = "is_verified", defaultValue = "0")
    val isVerified: Boolean = false,

    // Privacy settings
    @ColumnInfo(name = "is_private", defaultValue = "1")
    val isPrivate: Boolean = true,

    @ColumnInfo(name = "hide_from_suggestions", defaultValue = "0")
    val hideFromSuggestions: Boolean = false,

    @ColumnInfo(name = "allow_friend_requests", defaultValue = "1")
    val allowFriendRequests: Boolean = true,

    // External links
    @ColumnInfo(name = "instagram_handle")
    val instagramHandle: String? = null,

    @ColumnInfo(name = "youtube_channel")
    val youtubeChannel: String? = null,

    @ColumnInfo(name = "personal_website")
    val personalWebsite: String? = null,

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Int = 0,

    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false,
    
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = 0L,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
