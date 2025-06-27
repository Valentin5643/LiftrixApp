package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId")
    fun getProfileForUser(userId: String): Flow<UserProfileEntity?>
    
    @Query("SELECT * FROM user_profiles WHERE user_id = :userId")
    suspend fun getProfileForUserSuspend(userId: String): UserProfileEntity?
    
    @Query("SELECT * FROM user_profiles WHERE is_synced = 0")
    suspend fun getUnsyncedProfiles(): List<UserProfileEntity>
    
    @Query("SELECT COUNT(*) FROM user_profiles WHERE is_synced = 0")
    suspend fun getUnsyncedProfilesCount(): Int
    
    @Query("SELECT * FROM user_profiles WHERE completed_at IS NOT NULL")
    suspend fun getCompletedProfiles(): List<UserProfileEntity>
    
    @Query("SELECT * FROM user_profiles WHERE completed_at IS NULL")
    suspend fun getIncompleteProfiles(): List<UserProfileEntity>
    
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
} 