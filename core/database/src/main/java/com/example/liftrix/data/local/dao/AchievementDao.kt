package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.UserAchievementEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user achievements.
 * Provides CRUD operations with proper user scoping for fitness achievement tracking.
 */
@Dao
interface AchievementDao {

    @Query("SELECT * FROM user_achievements WHERE user_id = :userId ORDER BY unlocked_at DESC")
    fun getUserAchievementsFlow(userId: String): Flow<List<UserAchievementEntity>>

    @Query("SELECT * FROM user_achievements WHERE user_id = :userId ORDER BY unlocked_at DESC")
    suspend fun getUserAchievements(userId: String): List<UserAchievementEntity>

    @Query("SELECT * FROM user_achievements WHERE user_id = :userId AND is_displayed = 1 ORDER BY unlocked_at DESC")
    suspend fun getDisplayedAchievements(userId: String): List<UserAchievementEntity>

    @Query("SELECT * FROM user_achievements WHERE user_id = :userId AND achievement_type = :type")
    suspend fun getAchievementsByType(userId: String, type: String): List<UserAchievementEntity>

    @Query("SELECT COUNT(*) FROM user_achievements WHERE user_id = :userId")
    suspend fun getAchievementCount(userId: String): Int

    @Query("SELECT * FROM user_achievements WHERE user_id = :userId AND achievement_type = :type AND achievement_title = :title LIMIT 1")
    suspend fun findExistingAchievement(userId: String, type: String, title: String): UserAchievementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: UserAchievementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<UserAchievementEntity>)

    @Update
    suspend fun updateAchievement(achievement: UserAchievementEntity)

    @Query("UPDATE user_achievements SET is_displayed = :isDisplayed WHERE id = :achievementId AND user_id = :userId")
    suspend fun updateDisplayStatus(achievementId: String, userId: String, isDisplayed: Boolean)

    @Query("DELETE FROM user_achievements WHERE id = :achievementId AND user_id = :userId")
    suspend fun deleteAchievement(achievementId: String, userId: String)

    @Query("DELETE FROM user_achievements WHERE user_id = :userId")
    suspend fun deleteUserAchievements(userId: String)

    @Query("SELECT * FROM user_achievements WHERE is_synced = 0 AND user_id = :userId")
    suspend fun getUnsyncedAchievements(userId: String): List<UserAchievementEntity>

    @Query("UPDATE user_achievements SET is_synced = 1, sync_version = :syncVersion WHERE id = :achievementId")
    suspend fun markAsSynced(achievementId: String, syncVersion: Long)

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert userachievement from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(userAchievement: UserAchievementEntity) {
        val entity = userAchievement.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert userachievement from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(userAchievement: UserAchievementEntity) {
        val local = getUserAchievementForSync(userAchievement.id, userAchievement.userId)
        if (local == null || userAchievement.lastModified > local.lastModified) {
            val entity = userAchievement.copy(
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
    suspend fun _insert(entity: UserAchievementEntity)

    /**
     * Get dirty userachievement that need upload to Firestore.
     */
    @Query("SELECT * FROM user_achievements WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyUserAchievements(userId: String): List<UserAchievementEntity>

    /**
     * Mark userachievement as clean after successful Firestore upload.
     */
    @Query("UPDATE user_achievements SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local userachievement for remote deduplication.
     */
    @Query("SELECT * FROM user_achievements WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getUserAchievementForSync(id: String, userId: String): UserAchievementEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
