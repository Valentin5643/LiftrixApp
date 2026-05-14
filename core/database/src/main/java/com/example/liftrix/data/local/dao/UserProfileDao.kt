package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface UserProfileDao {
    
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId")
    fun getProfileForUser(userId: String): Flow<UserProfileEntity?>
    
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId")
    suspend fun getProfileForUserSuspend(userId: String): UserProfileEntity?
    
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedProfiles(userId: String): List<UserProfileEntity>
    
    @Query("SELECT COUNT(*) FROM user_profiles WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedProfilesCount(userId: String): Int
    
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId AND completed_at IS NOT NULL")
    suspend fun getCompletedProfiles(userId: String): List<UserProfileEntity>
    
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId AND completed_at IS NULL")
    suspend fun getIncompleteProfiles(userId: String): List<UserProfileEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<UserProfileEntity>): List<Long>
    
    @Update
    suspend fun updateProfile(profile: UserProfileEntity): Int
    
    @Query("UPDATE user_profiles SET is_synced = :isSynced, sync_version = :version WHERE user_id = :userId")
    suspend fun updateSyncStatus(userId: String, isSynced: Boolean, version: Long): Int
    
    @Query("UPDATE user_profiles SET is_synced = 1, sync_version = :version WHERE user_id IN (:userIds)")
    suspend fun markProfilesAsSynced(userIds: List<String>, version: Long): Int
    
    @Query("UPDATE user_profiles SET completed_at = :completedAt WHERE user_id = :userId")
    suspend fun markProfileAsCompleted(userId: String, completedAt: String?): Int
    
    @Delete
    suspend fun deleteProfile(profile: UserProfileEntity): Int
    
    @Query("DELETE FROM user_profiles WHERE user_id = :userId")
    suspend fun deleteProfileForUser(userId: String): Int
    
    @Query("DELETE FROM user_profiles")
    suspend fun deleteAllProfiles(): Int
    
    @Query("SELECT EXISTS(SELECT 1 FROM user_profiles WHERE user_id = :userId)")
    suspend fun hasProfile(userId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM user_profiles WHERE user_id = :userId AND completed_at IS NOT NULL)")
    suspend fun hasCompletedProfile(userId: String): Boolean
    
    // Enhanced profile queries for social features and achievements
    
    @Query("SELECT * FROM user_profiles WHERE is_public = 1 ORDER BY total_workouts DESC LIMIT :limit")
    suspend fun getPublicProfiles(limit: Int = 50): List<UserProfileEntity>
    
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId AND is_public = 1")
    suspend fun getPublicProfile(userId: String): UserProfileEntity?
    
    @Query("UPDATE user_profiles SET is_public = :isPublic WHERE user_id = :userId")
    suspend fun updatePrivacySetting(userId: String, isPublic: Boolean): Int

    @Query("""
        UPDATE user_profiles
        SET is_public = :isPublic,
            is_dirty = 1,
            is_synced = 0,
            last_modified = :lastModified
        WHERE user_id = :userId
    """)
    suspend fun updatePrivacySettingLocal(
        userId: String,
        isPublic: Boolean,
        lastModified: Long = System.currentTimeMillis()
    ): Int
    
    @Query("UPDATE user_profiles SET bio = :bio WHERE user_id = :userId")
    suspend fun updateBio(userId: String, bio: String?): Int
    
    @Query("UPDATE user_profiles SET total_workouts = :totalWorkouts, current_streak = :currentStreak, longest_streak = :longestStreak WHERE user_id = :userId")
    suspend fun updateWorkoutStats(userId: String, totalWorkouts: Int, currentStreak: Int, longestStreak: Int): Int
    
    @Query("UPDATE user_profiles SET profile_completion_percentage = :percentage WHERE user_id = :userId")
    suspend fun updateProfileCompletion(userId: String, percentage: Int): Int
    
    @Query("UPDATE user_profiles SET last_active_at = :lastActiveAt WHERE user_id = :userId")
    suspend fun updateLastActiveAt(userId: String, lastActiveAt: String): Int
    
    @Query("SELECT profile_completion_percentage FROM user_profiles WHERE user_id = :userId")
    suspend fun getProfileCompletionPercentage(userId: String): Int?
    
    @Query("SELECT COUNT(*) FROM user_profiles WHERE user_id = :userId AND profile_completion_percentage >= 80")
    suspend fun getHighCompletionProfilesCount(userId: String): Int
    
    // Profile image management methods
    
    @Query("UPDATE user_profiles SET profile_image_url = :imageUrl, profile_image_updated_at = :updatedAt, has_custom_profile_image = :hasCustom WHERE user_id = :userId")
    suspend fun updateProfileImage(userId: String, imageUrl: String?, updatedAt: String?, hasCustom: Boolean): Int
    
    @Query("SELECT profile_image_url FROM user_profiles WHERE user_id = :userId")
    suspend fun getProfileImageUrl(userId: String): String?
    
    @Query("SELECT has_custom_profile_image FROM user_profiles WHERE user_id = :userId")
    suspend fun hasCustomProfileImage(userId: String): Boolean?
    
    @Query("SELECT * FROM user_profiles WHERE has_custom_profile_image = 1 ORDER BY profile_image_updated_at DESC LIMIT :limit")
    suspend fun getProfilesWithCustomImages(limit: Int = 50): List<UserProfileEntity>
    
    // WAL checkpointing removed - will be handled at database level
    
    // Debug method to get all profiles for verification
    @Query("SELECT * FROM user_profiles ORDER BY updated_at DESC")
    suspend fun getAllProfiles(): List<UserProfileEntity>
    
    // Social and search features
    
    // For now, store keywords in bio field as a temporary solution
    @Query("UPDATE user_profiles SET bio = :keywords WHERE user_id = :userId")
    suspend fun updateSearchKeywords(userId: String, keywords: String): Int
    
    // For now, update last_active_at for profile view tracking  
    @Query("UPDATE user_profiles SET last_active_at = :viewedAt WHERE user_id = :userId")
    suspend fun updateProfileView(userId: String, viewedAt: LocalDateTime): Int

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert userprofile from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(userProfile: UserProfileEntity) {
        val entity = userProfile.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert userprofile from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(userProfile: UserProfileEntity) {
        val local = getUserProfileForSync(userProfile.id, userProfile.userId)
        if (local == null || userProfile.lastModified > local.lastModified) {
            val entity = userProfile.copy(
                isDirty = false,
                isSynced = true,
                syncVersion = System.currentTimeMillis()
            )
            _insert(entity)
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(entity: UserProfileEntity)

    /**
     * Get dirty userprofile that need upload to Firestore.
     */
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyUserProfiles(userId: String): List<UserProfileEntity>

    /**
     * Mark userprofile as clean after successful Firestore upload.
     */
    @Query("UPDATE user_profiles SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local userprofile for remote deduplication.
     */
    @Query("SELECT * FROM user_profiles WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getUserProfileForSync(id: String, userId: String): UserProfileEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
} 
