package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.PRNotificationPreferencesEntity

/**
 * DAO for PR Notification Preferences with mandatory user scoping.
 * 
 * CRITICAL SECURITY: All queries MUST include userId filtering to prevent data leakage.
 */
@Dao
interface PRNotificationPreferencesDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePreferences(preferences: PRNotificationPreferencesEntity)
    
    @Update
    suspend fun updatePreferences(preferences: PRNotificationPreferencesEntity)
    
    @Query("SELECT * FROM pr_notification_preferences WHERE user_id = :userId")
    suspend fun getPreferencesForUser(userId: String): PRNotificationPreferencesEntity?
    
    @Query("DELETE FROM pr_notification_preferences WHERE user_id = :userId")
    suspend fun deletePreferencesForUser(userId: String)
}