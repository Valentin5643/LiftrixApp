package com.example.liftrix.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.liftrix.data.local.dao.SettingsDao
import com.example.liftrix.data.mapper.SettingsMapper
import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.service.SettingsPersistenceManager
import com.example.liftrix.domain.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SettingsRepository using DataStore for immediate persistence
 * and Room for offline storage.
 * 
 * This repository follows a hybrid approach:
 * - DataStore: For immediate persistence and reactive UI updates
 * - Room: For offline storage and complex queries
 * 
 * The DataStore is the primary source of truth for settings, with Room acting
 * as a backup for offline scenarios.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDao: SettingsDao,
    private val settingsMapper: SettingsMapper,
    private val dataStore: DataStore<Preferences>,
    private val persistenceManager: SettingsPersistenceManager,
    private val syncScheduler: SyncScheduler
) : SettingsRepository {
    
    // Separate coroutine scope for async sync operations to prevent circular dependencies
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Track last synced state to prevent redundant operations
    @Volatile
    private var lastSyncedToRoom: UserSettings? = null
    @Volatile
    private var lastSyncedToDataStore: UserSettings? = null
    @Volatile
    private var lastSyncLogTime: Long = 0L
    
    companion object {
        // DataStore preference keys
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        private val WEIGHT_UNIT_KEY = stringPreferencesKey("weight_unit")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val UPDATED_AT_KEY = stringPreferencesKey("updated_at")
    }
    
    override fun getUserSettings(userId: String): Flow<UserSettings?> {
        return combine(
            dataStore.data.catch { exception ->
                Timber.e(exception, "Error reading from DataStore")
                emit(emptyMap<Preferences.Key<*>, Any>() as Preferences)
            },
            settingsDao.getUserSettings(userId).catch { exception ->
                Timber.e(exception, "Error reading from Room database")
                emit(null)
            }
        ) { preferences, roomEntity ->
            // Priority: DataStore first, then Room as fallback
            val dataStoreSettings = preferences.toUserSettings(userId)
            
            when {
                dataStoreSettings != null -> {
                    // DataStore has data, use it and schedule async sync to Room if needed
                    scheduleAsyncSyncToRoom(dataStoreSettings, roomEntity)
                    dataStoreSettings
                }
                roomEntity != null -> {
                    // DataStore is empty, use Room data and schedule async sync to DataStore
                    val domainSettings = settingsMapper.toDomain(roomEntity)
                    scheduleAsyncSyncToDataStore(domainSettings, null)
                    domainSettings
                }
                else -> {
                    // No data in either source, return null
                    null
                }
            }
        }.distinctUntilChanged() // Prevent duplicate emissions
    }
    
    override suspend fun updateDarkMode(userId: String, enabled: Boolean): Result<Unit> {
        return try {
            // Use SettingsPersistenceManager for reliable triple-store persistence
            val result = persistenceManager.persistSetting(userId, "dark_mode", enabled)
            
            result.fold(
                onSuccess = {
                    Timber.d("Dark mode updated successfully: $enabled for user: $userId")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to update dark mode for user: $userId")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update dark mode for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun updateNotifications(userId: String, enabled: Boolean): Result<Unit> {
        return try {
            // Use SettingsPersistenceManager for reliable triple-store persistence
            val result = persistenceManager.persistSetting(userId, "notifications_enabled", enabled)
            
            result.fold(
                onSuccess = {
                    Timber.d("Notifications updated successfully: $enabled for user: $userId")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to update notifications for user: $userId")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update notifications for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun updateWeightUnit(userId: String, weightUnit: WeightUnit): Result<Unit> {
        return try {
            // Use SettingsPersistenceManager for reliable triple-store persistence
            val result = persistenceManager.persistSetting(userId, "weight_unit", weightUnit)
            
            result.fold(
                onSuccess = {
                    Timber.d("Weight unit updated successfully: ${weightUnit.name} for user: $userId")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to update weight unit for user: $userId")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update weight unit for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun updateAutoSyncEnabled(userId: String, enabled: Boolean): Result<Unit> {
        return try {
            // Use SettingsPersistenceManager for reliable triple-store persistence
            val result = persistenceManager.persistSetting(userId, "auto_sync_enabled", enabled)
            
            result.fold(
                onSuccess = {
                    Timber.d("Auto-sync setting updated successfully: $enabled for user: $userId")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to update auto-sync setting for user: $userId")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception updating auto-sync setting for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun saveSettings(settings: UserSettings): Result<Unit> {
        return try {
            settings.validate()
            
            // Use SettingsPersistenceManager for complete settings save
            // Save each setting individually to ensure audit tracking
            var hasError = false
            var lastError: Throwable? = null
            
            val settingsMap = mapOf(
                "dark_mode" to settings.darkMode,
                "notifications_enabled" to settings.notificationsEnabled,
                "weight_unit" to settings.weightUnit,
                "terminology_preference" to settings.terminologyPreference,
                "migration_completed" to settings.migrationCompleted,
                "migration_explanation_seen" to settings.migrationExplanationSeen
            )
            
            settingsMap.forEach { (key, value) ->
                val result = persistenceManager.persistSetting(settings.userId, key, value)
                result.onFailure { error ->
                    hasError = true
                    lastError = error
                    Timber.e(error, "Failed to save setting $key for user: ${settings.userId}")
                }
            }
            
            if (hasError) {
                Result.failure(lastError ?: Exception("Unknown error during settings save"))
            } else {
                Timber.d("Settings saved successfully for user: ${settings.userId}")
                Result.success(Unit)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to save settings for user: ${settings.userId}")
            Result.failure(e)
        }
    }
    
    override suspend fun deleteSettings(userId: String): Result<Unit> {
        return try {
            // Clear from DataStore
            dataStore.edit { preferences ->
                preferences.clear()
            }
            
            // Delete from Room
            settingsDao.deleteUserSettings(userId)
            
            Timber.d("Settings deleted successfully for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete settings for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun hasSettings(userId: String): Boolean {
        return try {
            // Check DataStore first
            val dataStoreHasData = dataStore.data.first().let { preferences ->
                preferences[USER_ID_KEY] == userId && 
                preferences.contains(DARK_MODE_KEY) && 
                preferences.contains(NOTIFICATIONS_ENABLED_KEY)
                // Note: Not checking WEIGHT_UNIT_KEY for backward compatibility
            }
            
            if (dataStoreHasData) {
                true
            } else {
                // Fallback to Room
                settingsDao.hasSettings(userId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check if settings exist for user: $userId")
            false
        }
    }
    
    override suspend fun clearAllSettings(): Result<Unit> {
        return try {
            // Clear DataStore
            dataStore.edit { preferences ->
                preferences.clear()
            }
            
            Timber.d("All settings cleared from DataStore")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear all settings")
            Result.failure(e)
        }
    }
    
    override suspend fun syncSettings(userId: String): Result<Unit> {
        return try {
            // Get current settings from DataStore
            val dataStoreSettings = dataStore.data.first().toUserSettings(userId)
            
            if (dataStoreSettings != null) {
                // Sync to Room
                val entity = settingsMapper.toEntity(dataStoreSettings)
                settingsDao.insertSettings(entity)
                
                Timber.d("Settings synced to Room for user: $userId")
            } else {
                // No DataStore data, try to sync from Room to DataStore
                val roomEntity = settingsDao.getUserSettingsSync(userId)
                if (roomEntity != null) {
                    val domainSettings = settingsMapper.toDomain(roomEntity)
                    syncToDataStore(domainSettings)
                    
                    Timber.d("Settings synced from Room to DataStore for user: $userId")
                }
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync settings for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun getUserSettingsSync(userId: String): UserSettings? {
        return try {
            val roomEntity = settingsDao.getUserSettingsSync(userId)
            roomEntity?.let { settingsMapper.toDomain(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get settings synchronously for user: $userId")
            null
        }
    }
    
    override suspend fun updateTerminologyPreference(userId: String, preference: String): Result<Unit> {
        return try {
            val updatedAt = Instant.now()
            settingsDao.updateTerminologyPreference(userId, preference, updatedAt)
            
            // Schedule Firebase sync
            scheduleFirebaseSync(userId)
            
            Timber.d("Terminology preference updated successfully: $preference for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update terminology preference for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun updateMigrationExplanationSeen(userId: String, seen: Boolean): Result<Unit> {
        return try {
            val updatedAt = Instant.now()
            settingsDao.updateMigrationExplanationSeen(userId, seen, updatedAt)
            
            // Schedule Firebase sync
            scheduleFirebaseSync(userId)
            
            Timber.d("Migration explanation seen updated successfully: $seen for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update migration explanation seen for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun updateMigrationCompleted(userId: String, completed: Boolean): Result<Unit> {
        return try {
            val updatedAt = Instant.now()
            settingsDao.updateMigrationCompleted(userId, completed, updatedAt)
            
            // Schedule Firebase sync
            scheduleFirebaseSync(userId)
            
            Timber.d("Migration completed updated successfully: $completed for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update migration completed for user: $userId")
            Result.failure(e)
        }
    }
    
    /**
     * Schedules async sync to Room database to prevent circular dependencies.
     * Uses separate coroutine scope to avoid triggering Flow emission.
     * Only syncs if data has actually changed to prevent redundant operations.
     */
    private fun scheduleAsyncSyncToRoom(settings: UserSettings, currentRoomEntity: com.example.liftrix.data.local.entity.SettingsEntity?) {
        // Skip sync if data hasn't changed
        if (lastSyncedToRoom == settings) {
            return
        }
        
        // Check if Room already has the same data
        if (currentRoomEntity != null) {
            val currentRoomSettings = settingsMapper.toDomain(currentRoomEntity)
            if (currentRoomSettings == settings) {
                lastSyncedToRoom = settings
                return
            }
        }
        
        syncScope.launch {
            try {
                val entity = settingsMapper.toEntity(settings)
                settingsDao.insertSettings(entity)
                lastSyncedToRoom = settings
                
                // Throttled debug logging (max once per 30 seconds)
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSyncLogTime > 30_000) {
                    Timber.d("Settings synced to Room for user: ${settings.userId}")
                    lastSyncLogTime = currentTime
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to async sync settings to Room")
            }
        }
    }
    
    /**
     * Schedules async sync to DataStore to prevent circular dependencies.
     * Uses separate coroutine scope to avoid triggering Flow emission.
     * Only syncs if data has actually changed to prevent redundant operations.
     */
    private fun scheduleAsyncSyncToDataStore(settings: UserSettings, currentDataStoreSettings: UserSettings?) {
        // Skip sync if data hasn't changed
        if (lastSyncedToDataStore == settings) {
            return
        }
        
        // Check if DataStore already has the same data
        if (currentDataStoreSettings == settings) {
            lastSyncedToDataStore = settings
            return
        }
        
        syncScope.launch {
            try {
                dataStore.edit { preferences ->
                    preferences[DARK_MODE_KEY] = settings.darkMode
                    preferences[NOTIFICATIONS_ENABLED_KEY] = settings.notificationsEnabled
                    preferences[WEIGHT_UNIT_KEY] = settings.weightUnit.symbol
                    preferences[USER_ID_KEY] = settings.userId
                    preferences[UPDATED_AT_KEY] = settings.updatedAt.toString()
                }
                lastSyncedToDataStore = settings
                
                // Throttled debug logging (max once per 30 seconds)
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSyncLogTime > 30_000) {
                    Timber.d("Settings synced to DataStore for user: ${settings.userId}")
                    lastSyncLogTime = currentTime
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to async sync settings to DataStore")
            }
        }
    }
    
    /**
     * Synchronizes settings to Room database.
     */
    private suspend fun syncToRoom(settings: UserSettings) {
        try {
            val entity = settingsMapper.toEntity(settings)
            settingsDao.insertSettings(entity)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync settings to Room")
        }
    }
    
    /**
     * Synchronizes settings to DataStore.
     */
    private suspend fun syncToDataStore(settings: UserSettings) {
        try {
            dataStore.edit { preferences ->
                preferences[DARK_MODE_KEY] = settings.darkMode
                preferences[NOTIFICATIONS_ENABLED_KEY] = settings.notificationsEnabled
                preferences[WEIGHT_UNIT_KEY] = settings.weightUnit.symbol
                preferences[USER_ID_KEY] = settings.userId
                preferences[UPDATED_AT_KEY] = settings.updatedAt.toString()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync settings to DataStore")
        }
    }
    
    /**
     * Schedules Firebase sync for user settings using enhanced sync worker.
     */
    private fun scheduleFirebaseSync(userId: String) {
        try {
            syncScheduler.enqueueSettingsSync(userId, forceSync = false)
            Timber.d("Enhanced Firebase sync scheduled for user: $userId")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule enhanced Firebase sync for user: $userId")
        }
    }
    
    /**
     * Updates distance unit preference with sync
     */
    suspend fun updateDistanceUnit(userId: String, distanceUnit: String): Result<Unit> {
        return try {
            settingsDao.updateDistanceUnit(userId, distanceUnit, Instant.now())
            
            // Schedule Firebase sync
            scheduleFirebaseSync(userId)
            
            Timber.d("Distance unit updated successfully: $distanceUnit for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update distance unit for user: $userId")
            Result.failure(e)
        }
    }
    
    /**
     * Updates privacy settings with sync
     */
    suspend fun updatePrivacySettings(userId: String, privateProfile: Boolean, hideStats: Boolean): Result<Unit> {
        return try {
            settingsDao.updatePrivacySettings(userId, privateProfile, hideStats, Instant.now())
            
            // Schedule Firebase sync
            scheduleFirebaseSync(userId)
            
            Timber.d("Privacy settings updated successfully for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update privacy settings for user: $userId")
            Result.failure(e)
        }
    }
    
    /**
     * Updates communication settings with sync
     */
    suspend fun updateCommunicationSettings(userId: String, allowMessages: Boolean, autoPlayVideos: Boolean): Result<Unit> {
        return try {
            settingsDao.updateCommunicationSettings(userId, allowMessages, autoPlayVideos, Instant.now())
            
            // Schedule Firebase sync
            scheduleFirebaseSync(userId)
            
            Timber.d("Communication settings updated successfully for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update communication settings for user: $userId")
            Result.failure(e)
        }
    }
    
    /**
     * Forces sync for all user settings (used when user preferences need immediate sync)
     */
    suspend fun forceSyncUserSettings(userId: String): Result<Unit> {
        return try {
            syncScheduler.enqueueSettingsSync(userId, forceSync = true)
            Timber.d("Force sync scheduled for user settings: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule force sync for user: $userId")
            Result.failure(e)
        }
    }
    
    // New methods for enhanced settings persistence
    
    override suspend fun ensureSettingsPersisted(userId: String): Result<Unit> {
        return try {
            val settings = getUserSettingsSync(userId) ?: UserSettings.createDefault(userId)
            
            // Use SettingsPersistenceManager to ensure settings are in all stores
            val result = persistenceManager.validateSettingsIntegrity(userId)
            
            result.fold(
                onSuccess = { isValid ->
                    if (!isValid) {
                        // Repair corrupted settings
                        persistenceManager.repairCorruptedSettings(userId)
                    } else {
                        Result.success(Unit)
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to validate settings integrity for user: $userId")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to ensure settings persisted for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun validateSettingsIntegrity(userId: String): Result<Boolean> {
        return try {
            persistenceManager.validateSettingsIntegrity(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate settings integrity for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun repairCorruptedSettings(userId: String): Result<Unit> {
        return try {
            val result = persistenceManager.repairCorruptedSettings(userId)
            
            result.fold(
                onSuccess = {
                    Timber.i("Settings repaired successfully for user: $userId")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to repair corrupted settings for user: $userId")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to repair corrupted settings for user: $userId")
            Result.failure(e)
        }
    }
    
    override fun observeWeightUnit(userId: String): Flow<WeightUnit> {
        return getUserSettings(userId)
            .mapNotNull { it?.weightUnit }
            .distinctUntilChanged()
    }
    
    override suspend fun forceSettingsSync(userId: String): Result<Unit> {
        return try {
            val result = persistenceManager.forceSettingsSync(userId)
            
            result.fold(
                onSuccess = {
                    Timber.d("Settings force synced successfully for user: $userId")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to force sync settings for user: $userId")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to force sync settings for user: $userId")
            Result.failure(e)
        }
    }
}

/**
 * Extension function to convert Preferences to UserSettings.
 */
private fun Preferences.toUserSettings(userId: String): UserSettings? {
    val storedUserId = this[stringPreferencesKey("user_id")]
    
    return if (storedUserId == userId && this.contains(booleanPreferencesKey("dark_mode"))) {
        UserSettings(
            userId = userId,
            darkMode = this[booleanPreferencesKey("dark_mode")] ?: false,
            notificationsEnabled = this[booleanPreferencesKey("notifications_enabled")] ?: true,
            weightUnit = this[stringPreferencesKey("weight_unit")]?.let { value ->
                try {
                    // Try to parse as enum name first (new format)
                    WeightUnit.valueOf(value.uppercase())
                } catch (e: IllegalArgumentException) {
                    // Fallback to symbol parsing (legacy format)
                    WeightUnit.fromSymbol(value) ?: WeightUnit.getSystemDefault()
                }
            } ?: WeightUnit.getSystemDefault(),
            updatedAt = this[stringPreferencesKey("updated_at")]?.let { 
                try { 
                    Instant.parse(it) 
                } catch (e: Exception) { 
                    Instant.now() 
                } 
            } ?: Instant.now()
        )
    } else {
        null
    }
}
