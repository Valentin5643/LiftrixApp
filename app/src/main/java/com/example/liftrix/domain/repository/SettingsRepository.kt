package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user settings data.
 * 
 * This interface defines the contract for all settings-related operations,
 * including local persistence with DataStore, offline storage with Room,
 * and background synchronization with Firebase.
 * 
 * The repository follows an offline-first approach where settings are
 * immediately persisted to DataStore for quick access and cached in Room
 * for offline availability.
 */
interface SettingsRepository {
    
    /**
     * Retrieves user settings as a reactive stream.
     * 
     * The Flow will emit the latest settings from DataStore with Room as fallback.
     * Updates to settings will automatically emit new values to observers.
     * 
     * @param userId The ID of the user whose settings to retrieve
     * @return A Flow that emits UserSettings, or null if no settings exist
     */
    fun getUserSettings(userId: String): Flow<UserSettings?>
    
    /**
     * Updates the dark mode setting for a user.
     * 
     * This operation will immediately persist to DataStore for instant UI updates
     * and asynchronously sync to Room for offline availability.
     * 
     * @param userId The ID of the user whose setting to update
     * @param enabled Whether dark mode should be enabled
     * @return A Result indicating success or failure
     */
    suspend fun updateDarkMode(userId: String, enabled: Boolean): Result<Unit>
    
    /**
     * Updates the notification setting for a user.
     * 
     * This operation will immediately persist to DataStore for instant UI updates
     * and asynchronously sync to Room for offline availability.
     * 
     * @param userId The ID of the user whose setting to update
     * @param enabled Whether notifications should be enabled
     * @return A Result indicating success or failure
     */
    suspend fun updateNotifications(userId: String, enabled: Boolean): Result<Unit>
    
    /**
     * Updates the weight unit preference for a user.
     * 
     * This operation will immediately persist to DataStore for instant UI updates
     * and asynchronously sync to Room for offline availability.
     * 
     * @param userId The ID of the user whose setting to update
     * @param weightUnit The preferred weight unit (kg or lbs)
     * @return A Result indicating success or failure
     */
    suspend fun updateWeightUnit(userId: String, weightUnit: WeightUnit): Result<Unit>
    
    /**
     * Saves complete user settings.
     * 
     * This operation will validate the settings and persist them to both
     * DataStore and Room for consistency.
     * 
     * @param settings The complete UserSettings to save
     * @return A Result indicating success or failure
     */
    suspend fun saveSettings(settings: UserSettings): Result<Unit>
    
    /**
     * Deletes all settings for a user.
     * 
     * This operation will remove settings from both DataStore and Room,
     * typically used during user logout or account deletion.
     * 
     * @param userId The ID of the user whose settings to delete
     * @return A Result indicating success or failure
     */
    suspend fun deleteSettings(userId: String): Result<Unit>
    
    /**
     * Checks if settings exist for a user.
     * 
     * @param userId The ID of the user to check
     * @return True if settings exist, false otherwise
     */
    suspend fun hasSettings(userId: String): Boolean
    
    /**
     * Clears all settings from DataStore.
     * 
     * This is typically used during app logout or data cleanup.
     * Room data will remain for offline access unless explicitly cleared.
     * 
     * @return A Result indicating success or failure
     */
    suspend fun clearAllSettings(): Result<Unit>
    
    /**
     * Forces synchronization of settings from DataStore to Room.
     * 
     * This operation ensures that Room database is up-to-date with
     * the latest settings from DataStore.
     * 
     * @param userId The ID of the user whose settings to sync
     * @return A Result indicating success or failure
     */
    suspend fun syncSettings(userId: String): Result<Unit>
}