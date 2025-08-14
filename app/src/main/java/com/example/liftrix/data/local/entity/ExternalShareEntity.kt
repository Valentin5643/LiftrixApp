package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing external platform shares in the local database.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(
    tableName = "external_shares",
    indices = [
        Index(value = ["user_id", "shared_at"], name = "idx_external_shares_user_shared"),
        Index(value = ["content_type", "content_id"], name = "idx_external_shares_content"),
        Index(value = ["platform", "shared_at"], name = "idx_external_shares_platform")
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
data class ExternalShareEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    // Share context
    @ColumnInfo(name = "content_type")
    val contentType: String, // WORKOUT, ROUTINE, PR, PROGRESS

    @ColumnInfo(name = "content_id")
    val contentId: String,

    @ColumnInfo(name = "platform")
    val platform: String, // INSTAGRAM, WHATSAPP, TWITTER, FACEBOOK

    // Generated content
    @ColumnInfo(name = "share_image_url")
    val shareImageUrl: String? = null,

    @ColumnInfo(name = "share_text")
    val shareText: String? = null,

    @ColumnInfo(name = "hashtags")
    val hashtags: String? = null, // JSON array

    // Analytics
    @ColumnInfo(name = "share_method")
    val shareMethod: String? = null, // STORY, POST, MESSAGE

    // Timestamps
    @ColumnInfo(name = "shared_at")
    val sharedAt: Long
)