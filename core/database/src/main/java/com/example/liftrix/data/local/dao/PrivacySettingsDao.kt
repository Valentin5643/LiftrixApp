package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.PrivacySettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivacySettingsDao {
    
    @Query("SELECT * FROM user_privacy_settings WHERE user_id = :userId")
    fun getPrivacySettings(userId: String): Flow<PrivacySettingsEntity?>
    
    @Query("SELECT * FROM user_privacy_settings WHERE user_id = :userId")
    suspend fun getPrivacySettingsOnce(userId: String): PrivacySettingsEntity?
    
    @Query("SELECT online_status_visibility FROM user_privacy_settings WHERE user_id = :userId")
    suspend fun getOnlineStatusVisibility(userId: String): String?
    
    @Query("SELECT workout_sharing_default FROM user_privacy_settings WHERE user_id = :userId")
    suspend fun getWorkoutSharingDefault(userId: String): String?
    
    @Query("SELECT allow_friend_requests FROM user_privacy_settings WHERE user_id = :userId")
    suspend fun getAllowFriendRequests(userId: String): Boolean?
    
    @Query("SELECT COUNT(*) FROM user_privacy_settings WHERE user_id = :userId")
    suspend fun hasPrivacySettings(userId: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrivacySettings(settings: PrivacySettingsEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrivacySettingsList(settingsList: List<PrivacySettingsEntity>): List<Long>
    
    @Update
    suspend fun updatePrivacySettings(settings: PrivacySettingsEntity): Int
    
    @Query("UPDATE user_privacy_settings SET online_status_visibility = :visibility, updated_at = :updatedAt WHERE user_id = :userId")
    suspend fun updateOnlineStatusVisibility(userId: String, visibility: String, updatedAt: Long): Int
    
    @Query("UPDATE user_privacy_settings SET workout_sharing_default = :sharingDefault, updated_at = :updatedAt WHERE user_id = :userId")
    suspend fun updateWorkoutSharingDefault(userId: String, sharingDefault: String, updatedAt: Long): Int
    
    @Query("UPDATE user_privacy_settings SET allow_friend_requests = :allowRequests, updated_at = :updatedAt WHERE user_id = :userId")
    suspend fun updateAllowFriendRequests(userId: String, allowRequests: Boolean, updatedAt: Long): Int
    
    @Query("UPDATE user_privacy_settings SET updated_at = :updatedAt WHERE user_id = :userId")
    suspend fun updateLastModified(userId: String, updatedAt: Long): Int
    
    @Delete
    suspend fun deletePrivacySettings(settings: PrivacySettingsEntity): Int
    
    @Query("DELETE FROM user_privacy_settings WHERE user_id = :userId")
    suspend fun deletePrivacySettingsForUser(userId: String): Int
    
    @Query("DELETE FROM user_privacy_settings")
    suspend fun deleteAllPrivacySettings(): Int
} 