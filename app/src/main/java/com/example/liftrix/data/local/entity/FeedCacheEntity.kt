package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Room entity representing cached feed data for performance optimization.
 * Stores pre-calculated relevance scores for personalized feed generation.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "feed_cache",
    primaryKeys = ["user_id", "post_id"],
    indices = [
        Index(value = ["user_id", "score"], name = "idx_feed_cache_user_score"),
        Index(value = ["post_id"], name = "idx_feed_cache_post")
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
data class FeedCacheEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "post_id")
    val postId: String,

    @ColumnInfo(name = "score")
    val score: Double, // Relevance score for ordering

    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
    
    @ColumnInfo(name = "feed_type", defaultValue = "'HOME'")
    val feedType: String = "HOME" // HOME, DISCOVERY, etc.
)