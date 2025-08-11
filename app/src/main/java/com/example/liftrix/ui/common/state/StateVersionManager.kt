package com.example.liftrix.ui.common.state

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Manages state versioning and migration for StatefulDetailViewModels.
 * 
 * Provides a centralized system for handling state structure changes when the app
 * is updated. Ensures graceful migration of persisted state across app versions
 * without losing user preferences or causing crashes.
 * 
 * Key Features:
 * - Version-aware state migration with fallback support
 * - JSON-based state transformation for complex objects
 * - Validation of migrated state integrity
 * - Performance-optimized migration with minimal overhead
 * - Comprehensive logging for debugging migration issues
 * 
 * Usage:
 * ```kotlin
 * class MyDetailViewModel : StatefulDetailViewModel<State, Event>(...) {
 *     override fun handleStateVersionUpgrade(fromVersion: Int, toVersion: Int) {
 *         StateVersionManager.migrateDetailScreenState(
 *             savedStateHandle = savedStateHandle,
 *             screenId = "my_screen",
 *             fromVersion = fromVersion,
 *             toVersion = toVersion
 *         )
 *     }
 * }
 * ```
 */
object StateVersionManager {
    
    /**
     * Current state version for all detail screens.
     * Increment this when making breaking changes to state structure.
     */
    const val CURRENT_STATE_VERSION = 1
    
    /**
     * State version history and migration rules.
     */
    private val VERSION_MIGRATIONS = mapOf(
        0 to 1 to ::migrateFromV0ToV1
    )
    
    /**
     * Migrates detail screen state from one version to another.
     * 
     * @param savedStateHandle The SavedStateHandle containing persisted state
     * @param screenId Unique identifier for the detail screen
     * @param fromVersion The previous state version
     * @param toVersion The target state version
     */
    fun migrateDetailScreenState(
        savedStateHandle: androidx.lifecycle.SavedStateHandle,
        screenId: String,
        fromVersion: Int,
        toVersion: Int
    ) {
        if (fromVersion >= toVersion) {
            Timber.d("No migration needed: $fromVersion -> $toVersion")
            return
        }
        
        Timber.i("Starting state migration for $screenId: v$fromVersion -> v$toVersion")
        
        try {
            // Apply incremental migrations
            var currentVersion = fromVersion
            while (currentVersion < toVersion) {
                val nextVersion = currentVersion + 1
                val migrationKey = currentVersion to nextVersion
                
                val migrationFunction = VERSION_MIGRATIONS[migrationKey]
                if (migrationFunction != null) {
                    Timber.d("Applying migration: v$currentVersion -> v$nextVersion")
                    migrationFunction(savedStateHandle, screenId)
                    currentVersion = nextVersion
                } else {
                    Timber.w("No migration function found for v$currentVersion -> v$nextVersion")
                    // Clear state if we can't migrate safely
                    clearIncompatibleState(savedStateHandle, screenId)
                    break
                }
            }
            
            Timber.i("State migration completed for $screenId")
            
        } catch (e: Exception) {
            Timber.e(e, "State migration failed for $screenId, clearing state")
            clearIncompatibleState(savedStateHandle, screenId)
        }
    }
    
    /**
     * Migration from version 0 to version 1.
     * 
     * Version 1 changes:
     * - Standardized state key naming conventions
     * - Added validation for enum values
     * - Introduced complex object serialization
     */
    private fun migrateFromV0ToV1(
        savedStateHandle: androidx.lifecycle.SavedStateHandle,
        screenId: String
    ) {
        Timber.d("Migrating $screenId from v0 to v1")
        
        // No specific migrations needed for v0 -> v1 as this is the initial version
        // Future migrations would handle specific state transformations here
        
        // Example of state key renaming:
        // migrateStateKey(savedStateHandle, "old_key", "new_key")
        
        // Example of enum validation:
        // validateEnumState<TimeRange>(savedStateHandle, "time_range", TimeRange.MONTH)
    }
    
    /**
     * Migrates a state key from old name to new name.
     * 
     * @param savedStateHandle The SavedStateHandle to update
     * @param oldKey The previous state key
     * @param newKey The new state key
     */
    private fun migrateStateKey(
        savedStateHandle: androidx.lifecycle.SavedStateHandle,
        oldKey: String,
        newKey: String
    ) {
        try {
            val value = savedStateHandle.get<Any>(oldKey)
            if (value != null) {
                savedStateHandle[newKey] = value
                savedStateHandle.remove<Any>(oldKey)
                Timber.d("Migrated state key: $oldKey -> $newKey")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to migrate state key: $oldKey -> $newKey")
        }
    }
    
    /**
     * Validates and corrects enum state values.
     * 
     * @param T The enum type
     * @param savedStateHandle The SavedStateHandle to validate
     * @param key The state key to validate
     * @param defaultValue Default value if validation fails
     */
    private inline fun <reified T : Enum<T>> validateEnumState(
        savedStateHandle: androidx.lifecycle.SavedStateHandle,
        key: String,
        defaultValue: T
    ) {
        try {
            val value = savedStateHandle.get<T>(key)
            if (value == null || !enumValues<T>().contains(value)) {
                Timber.w("Invalid enum state for key '$key', resetting to default")
                savedStateHandle[key] = defaultValue
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate enum state for key '$key'")
            savedStateHandle[key] = defaultValue
        }
    }
    
    /**
     * Migrates complex object state using JSON transformation.
     * 
     * @param T The object type
     * @param savedStateHandle The SavedStateHandle to update
     * @param key The state key for the complex object
     * @param transformer Function to transform the old object to new format
     * @param defaultValue Default value if migration fails
     */
    private inline fun <reified T> migrateComplexState(
        savedStateHandle: androidx.lifecycle.SavedStateHandle,
        key: String,
        noinline transformer: (String) -> T?,
        defaultValue: T
    ) where T : @Serializable Any {
        try {
            val jsonKey = "${key}_json"
            val oldJson = savedStateHandle.get<String>(jsonKey)
            
            if (oldJson != null) {
                val transformedObject = transformer(oldJson)
                if (transformedObject != null) {
                    val newJson = Json.encodeToString(transformedObject)
                    savedStateHandle[jsonKey] = newJson
                    Timber.d("Migrated complex state for key '$key'")
                } else {
                    Timber.w("Failed to transform complex state for key '$key', using default")
                    val defaultJson = Json.encodeToString(defaultValue)
                    savedStateHandle[jsonKey] = defaultJson
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to migrate complex state for key '$key'")
            try {
                val defaultJson = Json.encodeToString(defaultValue)
                savedStateHandle["${key}_json"] = defaultJson
            } catch (fallbackException: Exception) {
                Timber.e(fallbackException, "Failed to set default value for complex state")
            }
        }
    }
    
    /**
     * Clears incompatible state when migration is not possible.
     * 
     * @param savedStateHandle The SavedStateHandle to clear
     * @param screenId The screen identifier for logging
     */
    private fun clearIncompatibleState(
        savedStateHandle: androidx.lifecycle.SavedStateHandle,
        screenId: String
    ) {
        try {
            val keysToRemove = savedStateHandle.keys().toList()
            keysToRemove.forEach { key ->
                // Keep version key to prevent repeated clearing
                if (key != "state_version") {
                    savedStateHandle.remove<Any>(key)
                }
            }
            Timber.w("Cleared incompatible state for $screenId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear incompatible state for $screenId")
        }
    }
    
    /**
     * Validates the integrity of migrated state.
     * 
     * @param savedStateHandle The SavedStateHandle to validate
     * @param requiredKeys List of keys that must be present after migration
     * @return True if validation passes, false otherwise
     */
    fun validateMigratedState(
        savedStateHandle: androidx.lifecycle.SavedStateHandle,
        requiredKeys: List<String>
    ): Boolean {
        return try {
            val missingKeys = requiredKeys.filter { key ->
                !savedStateHandle.contains(key)
            }
            
            if (missingKeys.isNotEmpty()) {
                Timber.w("Missing required state keys after migration: $missingKeys")
                false
            } else {
                Timber.d("State validation passed")
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "State validation failed")
            false
        }
    }
    
    /**
     * Gets the current state version.
     * 
     * @return The current state version number
     */
    fun getCurrentVersion(): Int = CURRENT_STATE_VERSION
    
    /**
     * Checks if a migration is available from the specified version.
     * 
     * @param fromVersion The version to migrate from
     * @return True if migration is supported, false otherwise
     */
    fun isMigrationSupported(fromVersion: Int): Boolean {
        if (fromVersion >= CURRENT_STATE_VERSION) return true
        
        // Check if we have a complete migration path
        var version = fromVersion
        while (version < CURRENT_STATE_VERSION) {
            val nextVersion = version + 1
            if (!VERSION_MIGRATIONS.containsKey(version to nextVersion)) {
                return false
            }
            version = nextVersion
        }
        
        return true
    }
}

/**
 * Data class representing state migration metadata.
 * 
 * Used for tracking migration history and debugging migration issues.
 */
@Serializable
data class StateMigrationInfo(
    val screenId: String,
    val fromVersion: Int,
    val toVersion: Int,
    val migrationTimestamp: Long,
    val successful: Boolean,
    val errorMessage: String? = null
) {
    companion object {
        /**
         * Creates a successful migration info record.
         */
        fun success(screenId: String, fromVersion: Int, toVersion: Int): StateMigrationInfo {
            return StateMigrationInfo(
                screenId = screenId,
                fromVersion = fromVersion,
                toVersion = toVersion,
                migrationTimestamp = System.currentTimeMillis(),
                successful = true
            )
        }
        
        /**
         * Creates a failed migration info record.
         */
        fun failure(
            screenId: String,
            fromVersion: Int,
            toVersion: Int,
            error: String
        ): StateMigrationInfo {
            return StateMigrationInfo(
                screenId = screenId,
                fromVersion = fromVersion,
                toVersion = toVersion,
                migrationTimestamp = System.currentTimeMillis(),
                successful = false,
                errorMessage = error
            )
        }
    }
}