package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a user's saved workout posts for later reference.
 * Allows users to bookmark workouts they want to try later.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "saved_posts",
    indices = [
        Index(value = ["user_id", "post_id"], unique = true, name = "idx_saved_posts_unique"),
        Index(value = ["user_id", "saved_at"], name = "idx_saved_posts_user_date"),
        Index(value = ["post_id"], name = "idx_saved_posts_post")
    ],
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkoutPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["post_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SavedPostEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "post_id")
    val postId: String,

    @ColumnInfo(name = "saved_at")
    val savedAt: Long
)