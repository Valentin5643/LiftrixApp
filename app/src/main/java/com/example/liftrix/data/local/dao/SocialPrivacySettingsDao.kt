package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.SocialPrivacySettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for social privacy settings with mandatory user scoping.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface SocialPrivacySettingsDao {

    // ========================================
    // Privacy Settings Retrieval (User-Scoped)
    // ========================================

    @Query("SELECT * FROM privacy_settings WHERE user_id = :userId")
    suspend fun getPrivacySettings(userId: String): SocialPrivacySettingsEntity?

    @Query("SELECT * FROM privacy_settings WHERE user_id = :userId")
    fun observePrivacySettings(userId: String): Flow<SocialPrivacySettingsEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM privacy_settings WHERE user_id = :userId)")
    suspend fun hasPrivacySettings(userId: String): Boolean

    // ========================================
    // Master Control Queries
    // ========================================

    @Query("SELECT social_enabled FROM privacy_settings WHERE user_id = :userId")
    suspend fun isSocialEnabled(userId: String): Boolean?

    @Query("SELECT profile_visibility FROM privacy_settings WHERE user_id = :userId")
    suspend fun getProfileVisibility(userId: String): String?

    @Query("""
        SELECT social_enabled FROM privacy_settings 
        WHERE user_id = :userId
    """)
    fun observeSocialEnabled(userId: String): Flow<Boolean?>

    // ========================================
    // Feature Toggle Queries
    // ========================================

    @Query("SELECT allow_follow_requests FROM privacy_settings WHERE user_id = :userId")
    suspend fun allowsFollowRequests(userId: String): Boolean?

    @Query("SELECT workout_sharing_enabled FROM privacy_settings WHERE user_id = :userId")
    suspend fun isWorkoutSharingEnabled(userId: String): Boolean?

    @Query("SELECT gym_buddies_enabled FROM privacy_settings WHERE user_id = :userId")
    suspend fun isGymBuddiesEnabled(userId: String): Boolean?

    @Query("SELECT community_participation FROM privacy_settings WHERE user_id = :userId")
    suspend fun isCommunityParticipationEnabled(userId: String): Boolean?

    @Query("SELECT challenge_participation FROM privacy_settings WHERE user_id = :userId")
    suspend fun isChallengeParticipationEnabled(userId: String): Boolean?

    @Query("SELECT routine_sharing_enabled FROM privacy_settings WHERE user_id = :userId")
    suspend fun isRoutineSharingEnabled(userId: String): Boolean?

    // ========================================
    // Content Visibility Queries
    // ========================================

    @Query("SELECT default_workout_visibility FROM privacy_settings WHERE user_id = :userId")
    suspend fun getDefaultWorkoutVisibility(userId: String): String?

    @Query("SELECT show_workout_stats FROM privacy_settings WHERE user_id = :userId")
    suspend fun showsWorkoutStats(userId: String): Boolean?

    @Query("SELECT show_achievements FROM privacy_settings WHERE user_id = :userId")
    suspend fun showsAchievements(userId: String): Boolean?

    @Query("SELECT show_workout_streak FROM privacy_settings WHERE user_id = :userId")
    suspend fun showsWorkoutStreak(userId: String): Boolean?

    // ========================================
    // Discovery Control Queries
    // ========================================

    @Query("SELECT hide_from_suggestions FROM privacy_settings WHERE user_id = :userId")
    suspend fun isHiddenFromSuggestions(userId: String): Boolean?

    @Query("SELECT hide_from_search FROM privacy_settings WHERE user_id = :userId")
    suspend fun isHiddenFromSearch(userId: String): Boolean?

    // ========================================
    // Privacy Settings Management
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrivacySettings(settings: SocialPrivacySettingsEntity): Long

    @Update
    suspend fun updatePrivacySettings(settings: SocialPrivacySettingsEntity): Int

    // ========================================
    // Master Control Updates
    // ========================================

    @Query("""
        UPDATE privacy_settings 
        SET social_enabled = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateSocialEnabled(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE privacy_settings 
        SET profile_visibility = :visibility, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateProfileVisibility(userId: String, visibility: String, updatedAt: Long): Int

    // ========================================
    // Feature Toggle Updates
    // ========================================

    @Query("""
        UPDATE privacy_settings 
        SET allow_follow_requests = :allow, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateFollowRequestsAllowed(userId: String, allow: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE privacy_settings 
        SET workout_sharing_enabled = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateWorkoutSharingEnabled(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE privacy_settings 
        SET gym_buddies_enabled = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateGymBuddiesEnabled(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE privacy_settings 
        SET community_participation = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateCommunityParticipation(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE privacy_settings 
        SET challenge_participation = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateChallengeParticipation(userId: String, enabled: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE privacy_settings 
        SET routine_sharing_enabled = :enabled, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateRoutineSharingEnabled(userId: String, enabled: Boolean, updatedAt: Long): Int

    // ========================================
    // Content Visibility Updates
    // ========================================

    @Query("""
        UPDATE privacy_settings 
        SET default_workout_visibility = :visibility, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateDefaultWorkoutVisibility(userId: String, visibility: String, updatedAt: Long): Int

    @Query("""
        UPDATE privacy_settings 
        SET show_workout_stats = :show, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateShowWorkoutStats(userId: String, show: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE privacy_settings 
        SET show_achievements = :show, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateShowAchievements(userId: String, show: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE privacy_settings 
        SET show_workout_streak = :show, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateShowWorkoutStreak(userId: String, show: Boolean, updatedAt: Long): Int

    // ========================================
    // Discovery Control Updates
    // ========================================

    @Query("""
        UPDATE privacy_settings 
        SET hide_from_suggestions = :hide, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateHideFromSuggestions(userId: String, hide: Boolean, updatedAt: Long): Int

    @Query("""
        UPDATE privacy_settings 
        SET hide_from_search = :hide, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateHideFromSearch(userId: String, hide: Boolean, updatedAt: Long): Int

    // ========================================
    // Notification Settings Updates
    // ========================================

    @Query("""
        UPDATE privacy_settings 
        SET notification_settings = :notificationSettings, updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateNotificationSettings(
        userId: String, 
        notificationSettings: String, 
        updatedAt: Long
    ): Int

    // ========================================
    // Privacy Settings Deletion
    // ========================================

    @Delete
    suspend fun deletePrivacySettings(settings: SocialPrivacySettingsEntity): Int

    @Query("DELETE FROM privacy_settings WHERE user_id = :userId")
    suspend fun deletePrivacySettingsForUser(userId: String): Int

    // ========================================
    // Sync Management
    // ========================================

    @Query("SELECT * FROM privacy_settings WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedPrivacySettings(userId: String): List<SocialPrivacySettingsEntity>

    @Query("""
        UPDATE privacy_settings 
        SET is_synced = :isSynced, sync_version = :version
        WHERE user_id = :userId
    """)
    suspend fun updateSyncStatus(userId: String, isSynced: Boolean, version: Int): Int

    // ========================================
    // Batch Privacy Operations
    // ========================================

    @Query("""
        UPDATE privacy_settings 
        SET 
            social_enabled = :socialEnabled,
            allow_follow_requests = :allowFollowRequests,
            workout_sharing_enabled = :workoutSharingEnabled,
            gym_buddies_enabled = :gymBuddiesEnabled,
            updated_at = :updatedAt
        WHERE user_id = :userId
    """)
    suspend fun updateSocialFeatureToggles(
        userId: String,
        socialEnabled: Boolean,
        allowFollowRequests: Boolean,
        workoutSharingEnabled: Boolean,
        gymBuddiesEnabled: Boolean,
        updatedAt: Long
    ): Int

    // ========================================
    // Analytics Support
    // ========================================

    @Query("SELECT COUNT(*) FROM privacy_settings WHERE user_id = :userId AND social_enabled = 1")
    suspend fun getSocialEnabledCount(userId: String): Int
}