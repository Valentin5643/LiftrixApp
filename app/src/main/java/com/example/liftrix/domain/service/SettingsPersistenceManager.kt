package com.example.liftrix.domain.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.liftrix.data.local.dao.SettingsAuditDao
import com.example.liftrix.data.local.dao.SettingsDao
import com.example.liftrix.data.local.entity.SettingsAuditEntity
import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for ensuring reliable persistence of settings across all storage layers.
 * 
 * Implements a triple-store persistence pattern with validation and audit tracking:
 * 1. DataStore (immediate UI updates)
 * 2. Room (offline persistence)
 * 3. Firebase (cloud sync)
 * 
 * Critical for resolving settings persistence issues where preferences don't survive
 * app restarts, especially weight unit preferences.
 */
@Singleton
class SettingsPersistenceManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val settingsDao: SettingsDao,
    private val firestore: FirebaseFirestore,
    private val auditDao: SettingsAuditDao
) {
    
    companion object {
        // DataStore preference keys (consistent with existing SettingsRepositoryImpl)
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        private val WEIGHT_UNIT_KEY = stringPreferencesKey("weight_unit")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val UPDATED_AT_KEY = stringPreferencesKey("updated_at")
        private val SETTINGS_VERSION_KEY = intPreferencesKey("settings_version")
        private val LAST_SYNC_TIMESTAMP_KEY = longPreferencesKey("last_sync_timestamp")
        private val TERMINOLOGY_PREFERENCE_KEY = stringPreferencesKey("terminology_preference")
        private val MIGRATION_COMPLETED_KEY = booleanPreferencesKey("migration_completed")
        private val MIGRATION_EXPLANATION_SEEN_KEY = booleanPreferencesKey("migration_explanation_seen")
    }
    
    /**
     * Persist a single setting to all storage layers with audit tracking.
     * 
     * This is the core method for reliable settings persistence. It ensures
     * the setting is saved to all three stores and creates an audit trail.
     */
    suspend fun persistSetting(
        userId: String,
        key: String,
        value: Any
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PERSIST_SETTING_FAILED",
                errorMessage = "Failed to persist setting: $key",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "setting_key" to key,
                    "setting_type" to value::class.simpleName.orEmpty()
                )
            )
        }
    ) {
        Timber.d("Persisting setting $key for user $userId")
        
        val oldValue = getCurrentSettingValue(userId, key)
        
        // 1. Save to DataStore (immediate UI update)
        dataStore.edit { preferences ->
            when (value) {
                is Boolean -> preferences[booleanPreferencesKey(key)] = value
                is String -> preferences[stringPreferencesKey(key)] = value
                is Int -> preferences[intPreferencesKey(key)] = value
                is Long -> preferences[longPreferencesKey(key)] = value
                is WeightUnit -> preferences[stringPreferencesKey(key)] = value.name
                else -> throw IllegalArgumentException("Unsupported setting value type: ${value::class.simpleName}")
            }
            
            // Update metadata
            preferences[USER_ID_KEY] = userId
            preferences[UPDATED_AT_KEY] = Instant.now().toString()
            preferences[LAST_SYNC_TIMESTAMP_KEY] = System.currentTimeMillis()
            preferences[SETTINGS_VERSION_KEY] = (preferences[SETTINGS_VERSION_KEY] ?: 1) + 1
        }
        
        // 2. Save to Room (offline persistence)
        withContext(Dispatchers.IO) {
            when (key) {
                "dark_mode" -> settingsDao.updateDarkMode(userId, value as Boolean)
                "notifications_enabled" -> settingsDao.updateNotificationsEnabled(userId, value as Boolean)
                "weight_unit" -> settingsDao.updateWeightUnit(userId, (value as WeightUnit).name)
                "terminology_preference" -> settingsDao.updateTerminologyPreference(userId, value as String)
                "migration_completed" -> settingsDao.updateMigrationCompleted(userId, value as Boolean)
                "migration_explanation_seen" -> settingsDao.updateMigrationExplanationSeen(userId, value as Boolean)
                else -> {
                    // For generic settings, update the entity directly
                    val entity = settingsDao.getUserSettingsSync(userId)
                    entity?.let { currentEntity ->
                        val updatedEntity = when (key) {
                            "settings_version" -> currentEntity.copy(settingsVersion = value as Int)
                            "last_sync_timestamp" -> currentEntity.copy(lastSyncTimestamp = value as Long)
                            else -> currentEntity
                        }
                        settingsDao.upsertSettings(updatedEntity.copy(updatedAt = Instant.now()))
                    }
                }
            }
        }
        
        // 3. Queue Firebase sync (best effort)
        try {
            firestore.collection("user_settings")
                .document(userId)
                .update(
                    mapOf(
                        key to when (value) {
                            is WeightUnit -> value.name
                            else -> value
                        },
                        "last_updated" to System.currentTimeMillis(),
                        "settings_version" to (getCurrentSettingsVersion(userId) + 1)
                    )
                )
                .await()
            Timber.d("Settings synced to Firebase for user $userId")
        } catch (e: Exception) {
            Timber.w(e, "Firebase sync failed for user $userId, will retry later")
            // Don't fail the entire operation if Firebase sync fails
        }
        
        // 4. Create audit trail
        val auditEntry = SettingsAuditEntity(
            auditId = UUID.randomUUID().toString(),
            userId = userId,
            settingKey = key,
            oldValue = oldValue,
            newValue = value.toString(),
            source = "USER",
            timestamp = System.currentTimeMillis()
        )
        auditDao.insert(auditEntry)
        
        Timber.d("Setting $key persisted successfully for user $userId")
    }
    
    /**
     * Persist a complete UserSettings object to all storage layers.
     * 
     * This is a convenience method that calls persistSetting for each setting field.
     */
    suspend fun persistSettings(userId: String, settings: UserSettings): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PERSIST_SETTINGS_FAILED",
                errorMessage = "Failed to persist complete settings",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        Timber.d("Persisting complete settings for user $userId")
        
        // Persist each setting individually
        persistSetting(userId, "dark_mode", settings.darkMode).getOrThrow()
        persistSetting(userId, "notifications_enabled", settings.notificationsEnabled).getOrThrow()
        persistSetting(userId, "weight_unit", settings.weightUnit.name).getOrThrow()
        persistSetting(userId, "terminology_preference", settings.terminologyPreference).getOrThrow()
        persistSetting(userId, "migration_completed", settings.migrationCompleted).getOrThrow()
        persistSetting(userId, "migration_explanation_seen", settings.migrationExplanationSeen).getOrThrow()
        
        Timber.d("Complete settings persisted successfully for user $userId")
    }
    
    /**
     * Load settings with fallback priority: DataStore → Room → Firebase → Defaults.
     * 
     * Implements the loading strategy with automatic repair when inconsistencies are detected.
     */
    suspend fun loadSettings(userId: String): UserSettings {
        Timber.d("Loading settings for user $userId")
        
        // Priority 1: DataStore (fastest access)
        val dataStoreSettings = try {
            loadFromDataStore(userId)
        } catch (e: Exception) {
            Timber.w(e, "Failed to load from DataStore")
            null
        }
        
        // Priority 2: Room (offline persistence)
        val roomSettings = try {
            settingsDao.getUserSettingsSync(userId)?.let { entity ->
                UserSettings(
                    userId = entity.userId,
                    darkMode = entity.darkMode,
                    notificationsEnabled = entity.notificationsEnabled,
                    weightUnit = WeightUnit.valueOf(entity.weightUnit.name),
                    terminologyPreference = entity.terminologyPreference,
                    migrationCompleted = entity.migrationCompleted,
                    migrationExplanationSeen = entity.migrationExplanationSeen
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load from Room")
            null
        }
        
        return when {
            dataStoreSettings != null -> {
                // Validate and repair if needed
                if (roomSettings != null && !settingsAreConsistent(dataStoreSettings, roomSettings)) {
                    Timber.w("Settings inconsistency detected, repairing...")
                    repairSettings(userId, dataStoreSettings, roomSettings)
                }
                dataStoreSettings
            }
            roomSettings != null -> {
                // Restore to DataStore
                saveToDataStore(roomSettings)
                roomSettings
            }
            else -> {
                // Load from Firebase or create defaults
                loadFromFirebaseOrDefaults(userId)
            }
        }
    }
    
    /**
     * Validates settings integrity across all storage layers.
     */
    suspend fun validateSettingsIntegrity(userId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "VALIDATE_SETTINGS_INTEGRITY_FAILED",
                errorMessage = "Failed to validate settings integrity",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val dataStoreSettings = loadFromDataStore(userId)
        val roomSettings = settingsDao.getUserSettingsSync(userId)?.let { entity ->
            UserSettings(
                userId = entity.userId,
                darkMode = entity.darkMode,
                notificationsEnabled = entity.notificationsEnabled,
                weightUnit = WeightUnit.valueOf(entity.weightUnit.name),
                terminologyPreference = entity.terminologyPreference,
                migrationCompleted = entity.migrationCompleted,
                migrationExplanationSeen = entity.migrationExplanationSeen
            )
        }
        
        when {
            dataStoreSettings == null && roomSettings == null -> true // No settings exist
            dataStoreSettings != null && roomSettings != null -> 
                settingsAreConsistent(dataStoreSettings, roomSettings)
            else -> false // One store has data, the other doesn't
        }
    }
    
    /**
     * Repairs corrupted settings by using the most recent valid data.
     */
    suspend fun repairCorruptedSettings(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "REPAIR_CORRUPTED_SETTINGS_FAILED",
                errorMessage = "Failed to repair corrupted settings",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        Timber.w("Repairing corrupted settings for user $userId")
        
        val dataStoreSettings = loadFromDataStore(userId)
        val roomSettings = settingsDao.getUserSettingsSync(userId)?.let { entity ->
            UserSettings(
                userId = entity.userId,
                darkMode = entity.darkMode,
                notificationsEnabled = entity.notificationsEnabled,
                weightUnit = WeightUnit.valueOf(entity.weightUnit.name),
                terminologyPreference = entity.terminologyPreference,
                migrationCompleted = entity.migrationCompleted,
                migrationExplanationSeen = entity.migrationExplanationSeen
            )
        }
        
        val settingsToUse = when {
            dataStoreSettings != null && roomSettings != null -> {
                // Use the most recently updated
                if (dataStoreSettings.updatedAt.isAfter(roomSettings.updatedAt)) {
                    dataStoreSettings
                } else {
                    roomSettings
                }
            }
            dataStoreSettings != null -> dataStoreSettings
            roomSettings != null -> roomSettings
            else -> UserSettings.createDefault(userId)
        }
        
        // Force save to both stores
        saveToDataStore(settingsToUse)
        saveToRoom(settingsToUse)
        
        // Create audit entry
        val auditEntry = SettingsAuditEntity(
            auditId = UUID.randomUUID().toString(),
            userId = userId,
            settingKey = "ALL_SETTINGS",
            oldValue = "CORRUPTED",
            newValue = "REPAIRED",
            source = "MIGRATION",
            timestamp = System.currentTimeMillis()
        )
        auditDao.insert(auditEntry)
        
        Timber.i("Settings repaired successfully for user $userId")
    }
    
    /**
     * Forces synchronization of settings to Firebase.
     */
    suspend fun forceSettingsSync(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to sync settings to Firebase",
                networkType = "FIREBASE",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val settings = loadSettings(userId)
        
        val settingsMap = mapOf(
            "dark_mode" to settings.darkMode,
            "notifications_enabled" to settings.notificationsEnabled,
            "weight_unit" to settings.weightUnit.name,
            "terminology_preference" to settings.terminologyPreference,
            "migration_completed" to settings.migrationCompleted,
            "migration_explanation_seen" to settings.migrationExplanationSeen,
            "last_updated" to System.currentTimeMillis(),
            "settings_version" to getCurrentSettingsVersion(userId)
        )
        
        firestore.collection("user_settings")
            .document(userId)
            .set(settingsMap)
            .await()
        
        Timber.i("Settings force synced to Firebase for user $userId")
    }
    
    // Private helper methods
    
    private suspend fun getCurrentSettingValue(userId: String, key: String): String? {
        return try {
            val preferences = dataStore.data.first()
            when (key) {
                "dark_mode" -> preferences[DARK_MODE_KEY]?.toString()
                "notifications_enabled" -> preferences[NOTIFICATIONS_ENABLED_KEY]?.toString()
                "weight_unit" -> preferences[WEIGHT_UNIT_KEY]
                "terminology_preference" -> preferences[TERMINOLOGY_PREFERENCE_KEY]
                "migration_completed" -> preferences[MIGRATION_COMPLETED_KEY]?.toString()
                "migration_explanation_seen" -> preferences[MIGRATION_EXPLANATION_SEEN_KEY]?.toString()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun getCurrentSettingsVersion(userId: String): Int {
        return try {
            val preferences = dataStore.data.first()
            preferences[SETTINGS_VERSION_KEY] ?: 1
        } catch (e: Exception) {
            1
        }
    }
    
    private suspend fun loadFromDataStore(userId: String): UserSettings? {
        val preferences = dataStore.data.first()
        val storedUserId = preferences[USER_ID_KEY]
        
        return if (storedUserId == userId) {
            UserSettings(
                userId = userId,
                darkMode = preferences[DARK_MODE_KEY] ?: false,
                notificationsEnabled = preferences[NOTIFICATIONS_ENABLED_KEY] ?: true,
                weightUnit = preferences[WEIGHT_UNIT_KEY]?.let { 
                    try { WeightUnit.valueOf(it) } catch (e: Exception) { WeightUnit.getSystemDefault() }
                } ?: WeightUnit.getSystemDefault(),
                terminologyPreference = preferences[TERMINOLOGY_PREFERENCE_KEY] ?: "NEW",
                migrationCompleted = preferences[MIGRATION_COMPLETED_KEY] ?: false,
                migrationExplanationSeen = preferences[MIGRATION_EXPLANATION_SEEN_KEY] ?: false,
                updatedAt = preferences[UPDATED_AT_KEY]?.let { 
                    try { Instant.parse(it) } catch (e: Exception) { Instant.now() }
                } ?: Instant.now()
            )
        } else {
            null
        }
    }
    
    private suspend fun saveToDataStore(settings: UserSettings) {
        dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = settings.userId
            preferences[DARK_MODE_KEY] = settings.darkMode
            preferences[NOTIFICATIONS_ENABLED_KEY] = settings.notificationsEnabled
            preferences[WEIGHT_UNIT_KEY] = settings.weightUnit.name
            preferences[TERMINOLOGY_PREFERENCE_KEY] = settings.terminologyPreference
            preferences[MIGRATION_COMPLETED_KEY] = settings.migrationCompleted
            preferences[MIGRATION_EXPLANATION_SEEN_KEY] = settings.migrationExplanationSeen
            preferences[UPDATED_AT_KEY] = settings.updatedAt.toString()
            preferences[LAST_SYNC_TIMESTAMP_KEY] = System.currentTimeMillis()
            preferences[SETTINGS_VERSION_KEY] = (preferences[SETTINGS_VERSION_KEY] ?: 1) + 1
        }
    }
    
    private suspend fun saveToRoom(settings: UserSettings) {
        val entity = com.example.liftrix.data.local.entity.SettingsEntity(
            userId = settings.userId,
            darkMode = settings.darkMode,
            notificationsEnabled = settings.notificationsEnabled,
            weightUnit = settings.weightUnit,
            terminologyPreference = settings.terminologyPreference,
            migrationCompleted = settings.migrationCompleted,
            migrationExplanationSeen = settings.migrationExplanationSeen,
            settingsVersion = getCurrentSettingsVersion(settings.userId),
            lastSyncTimestamp = System.currentTimeMillis(),
            updatedAt = settings.updatedAt
        )
        settingsDao.upsertSettings(entity)
    }
    
    private fun settingsAreConsistent(dataStore: UserSettings, room: UserSettings): Boolean {
        return dataStore.darkMode == room.darkMode &&
                dataStore.notificationsEnabled == room.notificationsEnabled &&
                dataStore.weightUnit == room.weightUnit &&
                dataStore.terminologyPreference == room.terminologyPreference &&
                dataStore.migrationCompleted == room.migrationCompleted &&
                dataStore.migrationExplanationSeen == room.migrationExplanationSeen
    }
    
    private suspend fun repairSettings(userId: String, dataStore: UserSettings, room: UserSettings) {
        // Use DataStore as source of truth (more recent)
        saveToRoom(dataStore)
        
        // Create audit entry
        val auditEntry = SettingsAuditEntity(
            auditId = UUID.randomUUID().toString(),
            userId = userId,
            settingKey = "CONSISTENCY_REPAIR",
            oldValue = "INCONSISTENT",
            newValue = "REPAIRED",
            source = "MIGRATION",
            timestamp = System.currentTimeMillis()
        )
        auditDao.insert(auditEntry)
    }
    
    private suspend fun loadFromFirebaseOrDefaults(userId: String): UserSettings {
        return try {
            val document = firestore.collection("user_settings")
                .document(userId)
                .get()
                .await()
            
            if (document.exists()) {
                val data = document.data!!
                UserSettings(
                    userId = userId,
                    darkMode = data["dark_mode"] as? Boolean ?: false,
                    notificationsEnabled = data["notifications_enabled"] as? Boolean ?: true,
                    weightUnit = (data["weight_unit"] as? String)?.let { 
                        try { WeightUnit.valueOf(it) } catch (e: Exception) { WeightUnit.getSystemDefault() }
                    } ?: WeightUnit.getSystemDefault(),
                    terminologyPreference = data["terminology_preference"] as? String ?: "NEW",
                    migrationCompleted = data["migration_completed"] as? Boolean ?: false,
                    migrationExplanationSeen = data["migration_explanation_seen"] as? Boolean ?: false,
                    updatedAt = Instant.now()
                ).also { settings ->
                    // Save to local stores
                    saveToDataStore(settings)
                    saveToRoom(settings)
                }
            } else {
                UserSettings.createDefault(userId).also { defaultSettings ->
                    saveToDataStore(defaultSettings)
                    saveToRoom(defaultSettings)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load from Firebase, using defaults")
            UserSettings.createDefault(userId).also { defaultSettings ->
                saveToDataStore(defaultSettings)
                saveToRoom(defaultSettings)
            }
        }
    }
    
    /**
     * Creates an audit entry for tracking settings changes.
     * 
     * This method is used to create audit trails for settings operations,
     * particularly useful for debugging and compliance tracking.
     */
    suspend fun createAuditEntry(
        userId: String,
        operation: String,
        settingKey: String,
        oldValue: String?,
        newValue: String,
        source: String
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CREATE_AUDIT_ENTRY_FAILED",
                errorMessage = "Failed to create audit entry",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "operation" to operation,
                    "setting_key" to settingKey
                )
            )
        }
    ) {
        val auditEntry = SettingsAuditEntity(
            auditId = UUID.randomUUID().toString(),
            userId = userId,
            settingKey = settingKey,
            oldValue = oldValue,
            newValue = newValue,
            source = source,
            timestamp = System.currentTimeMillis()
        )
        
        auditDao.insert(auditEntry)
        Timber.d("Audit entry created for user $userId, operation: $operation")
    }
}