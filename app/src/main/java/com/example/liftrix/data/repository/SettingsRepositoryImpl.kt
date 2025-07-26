package com.example.liftrix.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.liftrix.data.local.dao.SettingsDao
import com.example.liftrix.data.mapper.SettingsMapper
import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.sync.SettingsSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val workManager: WorkManager
) : SettingsRepository {
    
    // Separate coroutine scope for async sync operations to prevent circular dependencies
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
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
                    scheduleAsyncSyncToRoom(dataStoreSettings)
                    dataStoreSettings
                }
                roomEntity != null -> {
                    // DataStore is empty, use Room data and schedule async sync to DataStore
                    val domainSettings = settingsMapper.toDomain(roomEntity)
                    scheduleAsyncSyncToDataStore(domainSettings)
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
            val updatedAt = Instant.now()
            
            // Update DataStore immediately for UI reactivity
            dataStore.edit { preferences ->
                preferences[DARK_MODE_KEY] = enabled
                preferences[USER_ID_KEY] = userId
                preferences[UPDATED_AT_KEY] = updatedAt.toString()
            }
            
            // Update Room for offline persistence
            settingsDao.updateDarkMode(userId, enabled, updatedAt)
            
            // Schedule Firebase sync
            scheduleFirebaseSync(userId)
            
            Timber.d("Dark mode updated successfully: $enabled for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update dark mode for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun updateNotifications(userId: String, enabled: Boolean): Result<Unit> {
        return try {
            val updatedAt = Instant.now()
            
            // Update DataStore immediately for UI reactivity
            dataStore.edit { preferences ->
                preferences[NOTIFICATIONS_ENABLED_KEY] = enabled
                preferences[USER_ID_KEY] = userId
                preferences[UPDATED_AT_KEY] = updatedAt.toString()
            }
            
            // Update Room for offline persistence
            settingsDao.updateNotifications(userId, enabled, updatedAt)
            
            // Schedule Firebase sync
            scheduleFirebaseSync(userId)
            
            Timber.d("Notifications updated successfully: $enabled for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update notifications for user: $userId")
            Result.failure(e)
        }
    }
    
    override suspend fun updateWeightUnit(userId: String, weightUnit: WeightUnit): Result<Unit> {
        return try {
            val updatedAt = Instant.now()
            
            // Update DataStore immediately for UI reactivity
            dataStore.edit { preferences ->
                preferences[WEIGHT_UNIT_KEY] = weightUnit.symbol
                preferences[USER_ID_KEY] = userId
                preferences[UPDATED_AT_KEY] = updatedAt.toString()
            }
            
            // Update Room for offline persistence
            settingsDao.updateWeightUnit(userId, weightUnit, updatedAt)
            
            // Schedule Firebase sync
            scheduleFirebaseSync(userId)
            
            Timber.d("Weight unit updated successfully: ${weightUnit.symbol} for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update weight unit for user: $userId")
            Result.failure(e)
        }
    }
    
    
    override suspend fun saveSettings(settings: UserSettings): Result<Unit> {
        return try {
            settings.validate()
            
            // Save to DataStore for immediate persistence
            dataStore.edit { preferences ->
                preferences[DARK_MODE_KEY] = settings.darkMode
                preferences[NOTIFICATIONS_ENABLED_KEY] = settings.notificationsEnabled
                preferences[WEIGHT_UNIT_KEY] = settings.weightUnit.symbol
                preferences[USER_ID_KEY] = settings.userId
                preferences[UPDATED_AT_KEY] = settings.updatedAt.toString()
            }
            
            // Save to Room for offline storage
            val entity = settingsMapper.toEntity(settings)
            settingsDao.insertSettings(entity)
            
            // Schedule Firebase sync
            scheduleFirebaseSync(settings.userId)
            
            Timber.d("Settings saved successfully for user: ${settings.userId}")
            Result.success(Unit)
            
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
     */
    private fun scheduleAsyncSyncToRoom(settings: UserSettings) {
        syncScope.launch {
            try {
                val entity = settingsMapper.toEntity(settings)
                settingsDao.insertSettings(entity)
                Timber.v("Settings synced to Room asynchronously for user: ${settings.userId}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to async sync settings to Room")
            }
        }
    }
    
    /**
     * Schedules async sync to DataStore to prevent circular dependencies.
     * Uses separate coroutine scope to avoid triggering Flow emission.
     */
    private fun scheduleAsyncSyncToDataStore(settings: UserSettings) {
        syncScope.launch {
            try {
                dataStore.edit { preferences ->
                    preferences[DARK_MODE_KEY] = settings.darkMode
                    preferences[NOTIFICATIONS_ENABLED_KEY] = settings.notificationsEnabled
                    preferences[WEIGHT_UNIT_KEY] = settings.weightUnit.symbol
                    preferences[USER_ID_KEY] = settings.userId
                    preferences[UPDATED_AT_KEY] = settings.updatedAt.toString()
                }
                Timber.v("Settings synced to DataStore asynchronously for user: ${settings.userId}")
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
     * Schedules Firebase sync for user settings.
     */
    private fun scheduleFirebaseSync(userId: String) {
        try {
            val workRequest = OneTimeWorkRequestBuilder<SettingsSyncWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(SettingsSyncWorker.KEY_USER_ID, userId)
                        .build()
                )
                .build()
            
            workManager.enqueue(workRequest)
            Timber.d("Firebase sync scheduled for user: $userId")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule Firebase sync for user: $userId")
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
            weightUnit = this[stringPreferencesKey("weight_unit")]?.let { 
                WeightUnit.fromSymbol(it) 
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