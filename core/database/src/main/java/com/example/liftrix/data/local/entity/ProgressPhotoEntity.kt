package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing progress photos in the local database.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "progress_photos",
    indices = [
        Index(value = ["user_id", "taken_at"], name = "idx_progress_photos_user_taken"),
        Index(value = ["body_part", "photo_type"], name = "idx_progress_photos_category"),
        Index(value = ["comparison_group_id"], name = "idx_progress_photos_comparison"),
        Index(value = ["media_id"], name = "idx_progress_photos_media"),
        Index(value = ["is_private"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProgressPhotoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "media_id")
    val mediaId: String,

    // Categorization
    @ColumnInfo(name = "body_part")
    val bodyPart: String? = null, // FULL_BODY, UPPER, LOWER, ARMS, etc.

    @ColumnInfo(name = "photo_type")
    val photoType: String? = null, // FRONT, SIDE, BACK, FLEX

    // Measurements (optional)
    @ColumnInfo(name = "weight_kg")
    val weightKg: Float? = null,

    @ColumnInfo(name = "body_fat_percent")
    val bodyFatPercent: Float? = null,

    // Comparison
    @ColumnInfo(name = "comparison_group_id")
    val comparisonGroupId: String? = null, // For before/after sets

    @ColumnInfo(name = "is_before")
    val isBefore: Boolean? = null,

    // Privacy
    @ColumnInfo(name = "is_private", defaultValue = "1")
    val isPrivate: Boolean = true,

    // Timestamps
    @ColumnInfo(name = "taken_at")
    val takenAt: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Long = 0L,
    
    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = false,
    
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    val lastModified: Long = 0L
)
