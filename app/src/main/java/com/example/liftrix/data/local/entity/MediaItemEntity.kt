package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing media items (photos/videos) in the local database.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["user_id", "created_at"], name = "idx_media_items_user_created"),
        Index(value = ["post_id"]),
        Index(value = ["processing_status"]),
        Index(value = ["is_public"])
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
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class MediaItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "post_id")
    val postId: String? = null,

    // Media info
    @ColumnInfo(name = "type")
    val type: String, // PHOTO, VIDEO

    @ColumnInfo(name = "original_url")
    val originalUrl: String,

    @ColumnInfo(name = "cdn_url")
    val cdnUrl: String? = null,

    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,

    @ColumnInfo(name = "blurhash")
    val blurhash: String? = null, // Placeholder while loading

    // Metadata
    @ColumnInfo(name = "width")
    val width: Int? = null,

    @ColumnInfo(name = "height")
    val height: Int? = null,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long? = null,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int? = null, // For videos

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    // Processing
    @ColumnInfo(name = "processing_status", defaultValue = "'PENDING'")
    val processingStatus: String = "PENDING", // PENDING, PROCESSING, COMPLETED, FAILED

    @ColumnInfo(name = "processed_at")
    val processedAt: Long? = null,

    @ColumnInfo(name = "compression_ratio")
    val compressionRatio: Float? = null,

    // Privacy
    @ColumnInfo(name = "is_public", defaultValue = "0")
    val isPublic: Boolean = false,

    // Timestamps
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null, // For temporary media

    // Sync metadata
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "0")
    val syncVersion: Int = 0
)