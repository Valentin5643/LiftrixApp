package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing shared workout routines in the local database.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "shared_routines",
    indices = [
        Index(value = ["user_id", "created_at"], name = "idx_shared_routines_user_created"),
        Index(value = ["share_token"], unique = true, name = "idx_shared_routines_token"),
        Index(value = ["is_featured", "import_count"], name = "idx_shared_routines_featured"),
        Index(value = ["is_active"]),
        Index(value = ["parent_routine_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SharedRoutineEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "routine_id")
    val routineId: String,

    // Sharing info
    @ColumnInfo(name = "share_token")
    val shareToken: String,

    @ColumnInfo(name = "share_url")
    val shareUrl: String,

    // Content
    @ColumnInfo(name = "routine_name")
    val routineName: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "routine_data")
    val routineData: String, // JSON structure

    @ColumnInfo(name = "preview_image_url")
    val previewImageUrl: String? = null,

    // Metadata
    @ColumnInfo(name = "exercise_count")
    val exerciseCount: Int? = null,

    @ColumnInfo(name = "estimated_duration")
    val estimatedDuration: Int? = null,

    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: String? = null,

    @ColumnInfo(name = "equipment_needed")
    val equipmentNeeded: String? = null, // JSON array

    // Analytics
    @ColumnInfo(name = "view_count", defaultValue = "0")
    val viewCount: Int = 0,

    @ColumnInfo(name = "import_count", defaultValue = "0")
    val importCount: Int = 0,

    @ColumnInfo(name = "like_count", defaultValue = "0")
    val likeCount: Int = 0,

    // Versioning
    @ColumnInfo(name = "version", defaultValue = "1")
    val version: Int = 1,

    @ColumnInfo(name = "parent_routine_id")
    val parentRoutineId: String? = null,

    // Status
    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    @ColumnInfo(name = "is_featured", defaultValue = "0")
    val isFeatured: Boolean = false,

    // Timestamps
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

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
