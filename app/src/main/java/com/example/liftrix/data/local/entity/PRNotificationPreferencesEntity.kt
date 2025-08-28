package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.liftrix.domain.sync.SyncableEntity

/**
 * PR Notification Preferences Entity for user notification settings
 */
@Entity(
    tableName = "pr_notification_preferences",
    foreignKeys = [
        ForeignKey(
            entity = SocialProfileEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id"], unique = true)
    ]
)
data class PRNotificationPreferencesEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "enable_pr_notifications")
    val enablePRNotifications: Boolean = true,
    
    @ColumnInfo(name = "enable_reaction_notifications")
    val enableReactionNotifications: Boolean = true,
    
    @ColumnInfo(name = "only_from_buddies")
    val onlyFromBuddies: Boolean = true,
    
    @ColumnInfo(name = "quiet_hours_enabled")
    val quietHoursEnabled: Boolean = true,
    
    @ColumnInfo(name = "quiet_hours_start")
    val quietHoursStart: String = "22:00", // 10 PM
    
    @ColumnInfo(name = "quiet_hours_end")
    val quietHoursEnd: String = "08:00", // 8 AM
    
    @ColumnInfo(name = "minimum_pr_significance")
    val minimumPRSignificance: String = "MODERATE", // MINOR, MODERATE, MAJOR, EXCEPTIONAL
    
    @ColumnInfo(name = "max_notifications_per_day")
    val maxNotificationsPerDay: Int = 10,
    
    // Sync metadata
    @ColumnInfo(name = "is_synced")
    override val isSynced: Boolean = false,
    
    @ColumnInfo(name = "sync_version")
    override val syncVersion: Long = 0,
    
    @ColumnInfo(name = "last_modified")
    override val lastModified: Long = System.currentTimeMillis()
) : SyncableEntity