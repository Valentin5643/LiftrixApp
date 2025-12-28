package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a workout post in the social feed.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "workout_posts",
    indices = [
        Index(value = ["user_id", "created_at"], name = "idx_workout_posts_user_created"),
        Index(value = ["visibility", "created_at"], name = "idx_workout_posts_visibility"),
        Index(value = ["workout_id"]),
        // Unique constraint to prevent duplicate posts for the same workout
        Index(value = ["user_id", "workout_id"], name = "idx_workout_posts_user_workout_unique", unique = true),
        // Composite indexes for feed queries
        Index(value = ["user_id", "visibility", "created_at"], 
              name = "idx_workout_posts_feed_query"),
        Index(value = ["like_count", "comment_count", "created_at"], 
              name = "idx_workout_posts_engagement"),
        Index(value = ["prs_count", "created_at"], 
              name = "idx_workout_posts_prs"),
        Index(value = ["is_synced", "sync_version"], 
              name = "idx_workout_posts_sync")
    ],
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorkoutPostEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "workout_id")
    val workoutId: String,

    // Content
    @ColumnInfo(name = "caption")
    val caption: String? = null,

    @ColumnInfo(name = "media_urls")
    val mediaUrls: String? = null, // JSON array of photo/video URLs

    @ColumnInfo(name = "media_thumbnails")
    val mediaThumbnails: String? = null, // JSON array of thumbnail URLs

    // Metadata
    @ColumnInfo(name = "workout_duration")
    val workoutDuration: Int? = null,

    @ColumnInfo(name = "total_volume")
    val totalVolume: Double? = null,

    @ColumnInfo(name = "exercises_count")
    val exercisesCount: Int? = null,

    @ColumnInfo(name = "prs_count", defaultValue = "0")
    val prsCount: Int = 0,

    // Engagement metrics
    @ColumnInfo(name = "like_count", defaultValue = "0")
    val likeCount: Int = 0,

    @ColumnInfo(name = "comment_count", defaultValue = "0")
    val commentCount: Int = 0,

    @ColumnInfo(name = "share_count", defaultValue = "0")
    val shareCount: Int = 0,

    @ColumnInfo(name = "save_count", defaultValue = "0")
    val saveCount: Int = 0,

    // Visibility
    @ColumnInfo(name = "visibility", defaultValue = "'FOLLOWERS'")
    val visibility: String = "FOLLOWERS", // PUBLIC, FOLLOWERS, PRIVATE

    // Timestamps
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Int = 0,

    // Offline-first architecture fields (SPEC-20241228)
    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false,

    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = 0L
)