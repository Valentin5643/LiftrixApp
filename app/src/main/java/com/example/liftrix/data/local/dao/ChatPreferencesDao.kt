package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.ChatPreferencesEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing AI chatbot preferences with user-scoped queries.
 * Provides methods for preferences CRUD operations and sync management.
 */
@Dao
interface ChatPreferencesDao {
    /**
     * Observes chat preferences for a specific user.
     * Returns a Flow that emits updates when preferences change.
     */
    @Query("SELECT * FROM chat_preferences WHERE user_id = :userId")
    fun getChatPreferences(userId: String): Flow<ChatPreferencesEntity?>
    
    /**
     * Inserts or updates chat preferences for a user.
     * Uses REPLACE strategy to update existing preferences.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePreferences(preferences: ChatPreferencesEntity)
    
    /**
     * Marks preferences as synced with the specified version.
     * Used after successful Firebase sync.
     */
    @Query("UPDATE chat_preferences SET is_synced = 1, sync_version = :version WHERE user_id = :userId")
    suspend fun markAsSynced(userId: String, version: Int)
    
    /**
     * Convenience method to mark preferences as synced with default version.
     * Used by sync workers for simple sync completion marking.
     */
    suspend fun markPreferencesAsSynced(userId: String) {
        markAsSynced(userId, 1)
    }
    
    /**
     * Gets unsynced preferences for a user.
     * Used by sync workers to identify pending sync operations.
     */
    @Query("SELECT * FROM chat_preferences WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedPreferences(userId: String): ChatPreferencesEntity?
    
    /**
     * Updates specific AI response style for a user.
     * Used by settings screen for quick style changes.
     */
    @Query("UPDATE chat_preferences SET ai_response_style = :style, is_synced = 0, updated_at = :timestamp WHERE user_id = :userId")
    suspend fun updateResponseStyle(userId: String, style: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Updates user context prompt for personalized AI responses.
     * Used by settings screen for context customization.
     */
    @Query("UPDATE chat_preferences SET user_context_prompt = :prompt, is_synced = 0, updated_at = :timestamp WHERE user_id = :userId")
    suspend fun updateUserContextPrompt(userId: String, prompt: String?, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Updates usage notification threshold for a user.
     * Used by settings screen for usage alert customization.
     */
    @Query("UPDATE chat_preferences SET usage_notifications_threshold = :threshold, is_synced = 0, updated_at = :timestamp WHERE user_id = :userId")
    suspend fun updateUsageThreshold(userId: String, threshold: Int, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Toggles workout history inclusion for AI context.
     * Used by settings screen for context control.
     */
    @Query("UPDATE chat_preferences SET include_workout_history = :include, is_synced = 0, updated_at = :timestamp WHERE user_id = :userId")
    suspend fun updateWorkoutHistoryInclusion(userId: String, include: Boolean, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Gets current preferences as a single value (non-Flow).
     * Used by settings operations that need immediate access.
     */
    @Query("SELECT * FROM chat_preferences WHERE user_id = :userId")
    suspend fun getChatPreferencesSync(userId: String): ChatPreferencesEntity?
}