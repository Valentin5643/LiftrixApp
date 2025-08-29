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
     * Updates the auto-sync preference for a user.
     * 
     * This operation will immediately persist to DataStore for instant UI updates
     * and asynchronously sync to Room for offline availability.
     * 
     * @param userId The ID of the user whose setting to update
     * @param enabled Whether auto-sync should be enabled
     * @return A Result indicating success or failure
     */
    suspend fun updateAutoSyncEnabled(userId: String, enabled: Boolean): Result<Unit>
    
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
    
    /**
     * Updates the terminology preference for terminology migration.
     * 
     * @param userId The ID of the user whose preference to update
     * @param preference The terminology preference (NEW or LEGACY)
     * @return A Result indicating success or failure
     */
    suspend fun updateTerminologyPreference(userId: String, preference: String): Result<Unit>
    
    /**
     * Updates whether the user has seen the migration explanation.
     * 
     * @param userId The ID of the user whose status to update
     * @param seen Whether the explanation has been shown
     * @return A Result indicating success or failure
     */
    suspend fun updateMigrationExplanationSeen(userId: String, seen: Boolean): Result<Unit>
    
    /**
     * Updates whether the user has completed the terminology migration.
     * 
     * @param userId The ID of the user whose status to update
     * @param completed Whether the migration is complete
     * @return A Result indicating success or failure
     */
    suspend fun updateMigrationCompleted(userId: String, completed: Boolean): Result<Unit>
    
    /**
     * Retrieves user settings synchronously (for migration service usage).
     * 
     * @param userId The ID of the user whose settings to retrieve
     * @return UserSettings or null if no settings exist
     */
    suspend fun getUserSettingsSync(userId: String): UserSettings?
    
    // New methods for enhanced settings persistence (SPEC-20250116-settings-persistence)
    
    /**
     * Ensures all user settings are properly persisted across all storage layers.
     * 
     * @param userId The ID of the user whose settings to ensure
     * @return A Result indicating success or failure
     */
    suspend fun ensureSettingsPersisted(userId: String): Result<Unit>
    
    /**
     * Validates settings integrity across DataStore, Room, and Firebase.
     * 
     * @param userId The ID of the user whose settings to validate
     * @return A Result containing true if settings are consistent, false otherwise
     */
    suspend fun validateSettingsIntegrity(userId: String): Result<Boolean>
    
    /**
     * Repairs corrupted or inconsistent settings using the most recent valid data.
     * 
     * @param userId The ID of the user whose settings to repair
     * @return A Result indicating success or failure
     */
    suspend fun repairCorruptedSettings(userId: String): Result<Unit>
    
    /**
     * Observes weight unit changes reactively for the WeightUnitManager.
     * 
     * @param userId The ID of the user whose weight unit to observe
     * @return A Flow that emits weight unit changes
     */
    fun observeWeightUnit(userId: String): Flow<WeightUnit>
    
    /**
     * Forces synchronization of settings to Firebase.
     * 
     * @param userId The ID of the user whose settings to sync
     * @return A Result indicating success or failure
     */
    suspend fun forceSettingsSync(userId: String): Result<Unit>
}