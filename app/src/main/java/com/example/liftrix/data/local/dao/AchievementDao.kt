package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
}