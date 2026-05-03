package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for managing user notification preferences.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * Security Note: All queries against this table MUST include userId filtering 
 * to prevent data leakage between users.
 */
@Entity(tableName = "notification_preferences")
data class NotificationPreferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    // Master controls
    @ColumnInfo(name = "notifications_enabled", defaultValue = "1")
    val notificationsEnabled: Boolean = true,

    // Category toggles
    @ColumnInfo(name = "workout_notifications", defaultValue = "1")
    val workoutNotifications: Boolean = true,

    @ColumnInfo(name = "social_notifications", defaultValue = "1")
    val socialNotifications: Boolean = true,

    @ColumnInfo(name = "achievement_notifications", defaultValue = "1")
    val achievementNotifications: Boolean = true,

    @ColumnInfo(name = "reminder_notifications", defaultValue = "1")
    val reminderNotifications: Boolean = true,

    // Social subcategories
    @ColumnInfo(name = "gym_buddy_prs", defaultValue = "1")
    val gymBuddyPrs: Boolean = true,

    @ColumnInfo(name = "follow_requests", defaultValue = "1")
    val followRequests: Boolean = true,

    @ColumnInfo(name = "post_likes", defaultValue = "1")
    val postLikes: Boolean = true,

    @ColumnInfo(name = "post_comments", defaultValue = "1")
    val postComments: Boolean = true,

    @ColumnInfo(name = "mentions", defaultValue = "1")
    val mentions: Boolean = true,

    // Delivery preferences
    @ColumnInfo(name = "delivery_frequency", defaultValue = "'IMMEDIATE'")
    val deliveryFrequency: String = "IMMEDIATE", // 'IMMEDIATE', 'HOURLY', 'DAILY'

    @ColumnInfo(name = "quiet_hours_enabled", defaultValue = "1")
    val quietHoursEnabled: Boolean = true,

    @ColumnInfo(name = "quiet_hours_start", defaultValue = "22")
    val quietHoursStart: Int = 22, // 10 PM

    @ColumnInfo(name = "quiet_hours_end", defaultValue = "8")
    val quietHoursEnd: Int = 8, // 8 AM

    // Batching preferences
    @ColumnInfo(name = "batch_social_notifications", defaultValue = "1")
    val batchSocialNotifications: Boolean = true,

    @ColumnInfo(name = "batch_window_minutes", defaultValue = "60")
    val batchWindowMinutes: Int = 60,

    // Sound and vibration
    @ColumnInfo(name = "notification_sound", defaultValue = "1")
    val notificationSound: Boolean = true,

    @ColumnInfo(name = "notification_vibration", defaultValue = "1")
    val notificationVibration: Boolean = true,

    // In-app settings
    @ColumnInfo(name = "show_in_app_notifications", defaultValue = "1")
    val showInAppNotifications: Boolean = true,

    // Updated timestamp
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)