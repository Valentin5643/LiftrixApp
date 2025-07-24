package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import java.time.LocalDateTime

/**
 * Room entity representing a user's fitness achievement in the local database.
 * Tracks milestone accomplishments, streaks, and other fitness achievements.
 */
@Entity(
    tableName = "user_achievements",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["user_id"], unique = false)]
)
@TypeConverters(DateTimeConverters::class)
data class UserAchievementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "achievement_type")
    val achievementType: String,

    @ColumnInfo(name = "achievement_title")
    val achievementTitle: String,

    @ColumnInfo(name = "achievement_description")
    val achievementDescription: String,

    @ColumnInfo(name = "unlocked_at")
    val unlockedAt: LocalDateTime,

    @ColumnInfo(name = "is_displayed", defaultValue = "1")
    val isDisplayed: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime,

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime,

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Long = 1L
)