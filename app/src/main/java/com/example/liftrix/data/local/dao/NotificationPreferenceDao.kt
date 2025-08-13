package com.example.liftrix.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.NotificationPreferenceEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for notification preference operations with mandatory user scoping.
 * Part of notification system from SPEC-20250113-notifications-privacy.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface NotificationPreferenceDao {

    // ========================================
    // Preference Retrieval (User-Scoped)
    // ========================================

    @Query("SELECT * FROM notification_preferences WHERE user_id = :userId")
    fun observePreferences(userId: String): Flow<NotificationPreferenceEntity?>

    @Query("SELECT * FROM notification_preferences WHERE user_id = :userId")
    suspend fun getPreferences(userId: String): NotificationPreferenceEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM notification_preferences WHERE user_id = :userId)")
    suspend fun hasPreferences(userId: String): Boolean

    // ========================================
    // Master Controls
    // ========================================

    @Query("SELECT notifications_enabled FROM notification_preferences WHERE user_id = :userId")
    suspend fun areNotificationsEnabled(userId: String): Boolean?

    @Query("""
        UPDATE notification_preferences 
        SET notifications_enabled = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateMasterNotifications(userId: String, enabled: Boolean, updatedAt: Long): Int

    // ========================================
    // Category Controls
    // ========================================

    @Query("""
        UPDATE notification_preferences 
        SET workout_notifications = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateWorkoutNotifications(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE notification_preferences 
        SET social_notifications = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateSocialNotifications(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE notification_preferences 
        SET achievement_notifications = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateAchievementNotifications(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE notification_preferences 
        SET reminder_notifications = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateReminderNotifications(userId: String, enabled: Boolean, updatedAt: Long): Int

    // ========================================
    // Social Subcategory Controls
    // ========================================

    @Query("""
        UPDATE notification_preferences 
        SET gym_buddy_prs = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateGymBuddyPRs(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE notification_preferences 
        SET follow_requests = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateFollowRequests(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE notification_preferences 
        SET post_likes = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updatePostLikes(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE notification_preferences 
        SET post_comments = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updatePostComments(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE notification_preferences 
        SET mentions = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateMentions(userId: String, enabled: Boolean, updatedAt: Long): Int

    // ========================================
    // Delivery Preferences
    // ========================================

    @Query("""
        UPDATE notification_preferences 
        SET delivery_frequency = :frequency, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateDeliveryFrequency(userId: String, frequency: String, updatedAt: Long): Int

    @Query("""
        UPDATE notification_preferences 
        SET quiet_hours_enabled = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateQuietHoursEnabled(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE notification_preferences 
        SET quiet_hours_start = :startHour, quiet_hours_end = :endHour, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateQuietHours(userId: String, startHour: Int, endHour: Int, updatedAt: Long): Int

    // ========================================
    // Batching Preferences
    // ========================================

    @Query("""
        UPDATE notification_preferences 
        SET batch_social_notifications = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateBatchSocialNotifications(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE notification_preferences 
        SET batch_window_minutes = :minutes, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateBatchWindow(userId: String, minutes: Int, updatedAt: Long): Int

    // ========================================
    // Sound and Vibration
    // ========================================

    @Query("""
        UPDATE notification_preferences 
        SET notification_sound = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateNotificationSound(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE notification_preferences 
        SET notification_vibration = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateNotificationVibration(userId: String, enabled: Boolean, updatedAt: Long): Int

    // ========================================
    // In-App Settings
    // ========================================

    @Query("""
        UPDATE notification_preferences 
        SET show_in_app_notifications = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateInAppNotifications(userId: String, enabled: Boolean, updatedAt: Long): Int

    // ========================================
    // Preference Management
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferences(preferences: NotificationPreferenceEntity): Long

    @Update
    suspend fun updatePreferences(preferences: NotificationPreferenceEntity): Int

    @Query("DELETE FROM notification_preferences WHERE user_id = :userId")
    suspend fun deletePreferencesForUser(userId: String): Int

    // ========================================
    // Bulk Preference Checks
    // ========================================

    @Query("""
        SELECT notifications_enabled AND social_notifications AND gym_buddy_prs 
        FROM notification_preferences 
        WHERE user_id = :userId
    """)
    suspend fun canReceiveGymBuddyPRs(userId: String): Boolean?

    @Query("""
        SELECT notifications_enabled AND social_notifications AND follow_requests 
        FROM notification_preferences 
        WHERE user_id = :userId
    """)
    suspend fun canReceiveFollowRequests(userId: String): Boolean?

    @Query("""
        SELECT notifications_enabled AND social_notifications AND post_likes 
        FROM notification_preferences 
        WHERE user_id = :userId
    """)
    suspend fun canReceivePostLikes(userId: String): Boolean?

    @Query("""
        SELECT notifications_enabled AND social_notifications AND post_comments 
        FROM notification_preferences 
        WHERE user_id = :userId
    """)
    suspend fun canReceivePostComments(userId: String): Boolean?

    @Query("""
        SELECT quiet_hours_enabled, quiet_hours_start, quiet_hours_end 
        FROM notification_preferences 
        WHERE user_id = :userId
    """)
    suspend fun getQuietHoursSettings(userId: String): QuietHoursSettings?

    data class QuietHoursSettings(
        @ColumnInfo(name = "quiet_hours_enabled") val quietHoursEnabled: Boolean,
        @ColumnInfo(name = "quiet_hours_start") val quietHoursStart: Int,
        @ColumnInfo(name = "quiet_hours_end") val quietHoursEnd: Int
    )

    // ========================================
    // Additional Methods for Repository
    // ========================================

    @Query("SELECT * FROM notification_preferences WHERE user_id = :userId")
    suspend fun getNotificationPreferences(userId: String): NotificationPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotificationPreferences(preferences: NotificationPreferenceEntity)

    @Query("SELECT COUNT(*) FROM notification_preferences WHERE notifications_enabled = 0")
    suspend fun getDisabledNotificationsCount(): Int
}