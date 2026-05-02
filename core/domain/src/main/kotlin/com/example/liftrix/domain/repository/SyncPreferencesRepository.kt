package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user sync preferences.
 * Part of Phase 2: Sync Infrastructure Enhancement from SPEC-20250901-todo-implementation.
 * 
 * Provides operations for:
 * - User sync preference management with proper user scoping
 * - Auto-sync toggle functionality
 * - Sync interval configuration
 * - Last sync time tracking
 * - Sync category preferences (workouts, profile, social, settings)
 */
interface SyncPreferencesRepository {
    
    /**
     * Gets auto sync enabled status for a specific user.
     * @param userId The user ID to check (mandatory user scoping)
     * @return LiftrixResult with true if auto sync is enabled, false otherwise
     */
    suspend fun getAutoSyncEnabled(userId: String): LiftrixResult<Boolean>
    
    /**
     * Sets auto sync enabled status for a specific user.
     * @param userId The user ID to update (mandatory user scoping)
     * @param enabled Whether auto sync should be enabled
     * @return LiftrixResult indicating success or failure
     */
    suspend fun setAutoSyncEnabled(userId: String, enabled: Boolean): LiftrixResult<Unit>
    
    /**
     * Observes auto sync enabled status changes for a specific user.
     * @param userId The user ID to observe (mandatory user scoping)
     * @return Flow of boolean values indicating auto sync status
     */
    fun observeAutoSyncStatus(userId: String): Flow<Boolean>
    
    /**
     * Gets last sync time for a specific user.
     * @param userId The user ID to check (mandatory user scoping)
     * @return LiftrixResult with last sync timestamp or null if never synced
     */
    suspend fun getLastSyncTime(userId: String): LiftrixResult<Long?>
    
    /**
     * Updates last sync time for a specific user.
     * @param userId The user ID to update (mandatory user scoping)
     * @param timestamp The new last sync timestamp
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateLastSyncTime(userId: String, timestamp: Long): LiftrixResult<Unit>
    
    /**
     * Gets sync interval in minutes for a specific user.
     * @param userId The user ID to check (mandatory user scoping)
     * @return LiftrixResult with sync interval in minutes
     */
    suspend fun getSyncInterval(userId: String): LiftrixResult<Long>
    
    /**
     * Sets sync interval in minutes for a specific user.
     * @param userId The user ID to update (mandatory user scoping)
     * @param intervalMinutes Sync interval in minutes (must be between 5 and 1440)
     * @return LiftrixResult indicating success or failure
     */
    suspend fun setSyncInterval(userId: String, intervalMinutes: Long): LiftrixResult<Unit>
    
    /**
     * Gets all sync preferences for a specific user.
     * @param userId The user ID to get preferences for (mandatory user scoping)
     * @return LiftrixResult with SyncPreferences object
     */
    suspend fun getSyncPreferences(userId: String): LiftrixResult<SyncPreferences>
    
    /**
     * Updates sync preferences for a specific user.
     * @param userId The user ID to update (mandatory user scoping)
     * @param preferences The new sync preferences
     * @return LiftrixResult indicating success or failure
     */
    suspend fun updateSyncPreferences(userId: String, preferences: SyncPreferences): LiftrixResult<Unit>
    
    /**
     * Gets users who are due for sync based on their preferences.
     * Used by sync coordinator for scheduling operations.
     * @param currentTimestamp Current timestamp to check against
     * @return LiftrixResult with list of user IDs due for sync
     */
    suspend fun getUsersDueForSync(currentTimestamp: Long): LiftrixResult<List<String>>
    
    /**
     * Gets all users with auto sync enabled.
     * Used for bulk sync operations.
     * @return LiftrixResult with list of user IDs with auto sync enabled
     */
    suspend fun getUsersWithAutoSyncEnabled(): LiftrixResult<List<String>>
    
    /**
     * Checks if a user should sync based on their preferences and conditions.
     * @param userId The user ID to check (mandatory user scoping)
     * @param isWifiConnected Whether device is connected to WiFi
     * @param isBatterySaverOn Whether battery saver mode is active
     * @return LiftrixResult with true if user should sync, false otherwise
     */
    suspend fun shouldUserSync(
        userId: String, 
        isWifiConnected: Boolean,
        isBatterySaverOn: Boolean
    ): LiftrixResult<Boolean>
}

/**
 * Data class representing user sync preferences.
 */
data class SyncPreferences(
    val autoSyncEnabled: Boolean = true,
    val syncIntervalMinutes: Long = 15L,
    val syncOnWifiOnly: Boolean = false,
    val syncOnBatterySaver: Boolean = false,
    val lastSyncTimestamp: Long? = null,
    val syncWorkoutData: Boolean = true,
    val syncProfileData: Boolean = true,
    val syncSocialData: Boolean = true,
    val syncSettings: Boolean = true
) {
    init {
        require(syncIntervalMinutes >= 5) { "Sync interval must be at least 5 minutes" }
        require(syncIntervalMinutes <= 1440) { "Sync interval cannot exceed 24 hours (1440 minutes)" }
    }
}