package com.example.liftrix.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.liftrix.ui.theme.ThemeVersion
import com.example.liftrix.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Theme Preferences Data Layer
 * Handles persistence of theme-related preferences including theme version selection
 * Complements ThemeManager by providing data layer abstraction
 */
class ThemePreferences(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "liftrix_theme_preferences_v2"
        private const val KEY_THEME_VERSION = "theme_version"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled"
        private const val KEY_MIGRATION_COMPLETED = "migration_completed"
        private const val KEY_ROLLBACK_COUNT = "rollback_count"
        
        @Volatile
        private var INSTANCE: ThemePreferences? = null
        
        fun getInstance(context: Context): ThemePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val _themeVersion = MutableStateFlow(getThemeVersion())
    val themeVersion: StateFlow<ThemeVersion> = _themeVersion.asStateFlow()
    
    /**
     * Gets the currently selected theme version
     */
    fun getThemeVersion(): ThemeVersion {
        val savedVersion = sharedPreferences.getString(KEY_THEME_VERSION, ThemeVersion.V2.name)
        return try {
            ThemeVersion.valueOf(savedVersion ?: ThemeVersion.V2.name)
        } catch (e: IllegalArgumentException) {
            Timber.w("ThemePreferences: Invalid theme version $savedVersion, defaulting to V2")
            ThemeVersion.V2
        }
    }
    
    /**
     * Sets the theme version with validation and persistence
     */
    fun setThemeVersion(version: ThemeVersion): Boolean {
        return try {
            sharedPreferences.edit()
                .putString(KEY_THEME_VERSION, version.name)
                .putLong("last_updated", System.currentTimeMillis())
                .apply()
            
            _themeVersion.value = version
            
            Timber.i("ThemePreferences: Theme version updated to $version")
            true
        } catch (e: Exception) {
            Timber.e(e, "ThemePreferences: Failed to save theme version $version")
            false
        }
    }
    
    /**
     * Gets the preferred theme mode
     */
    fun getThemeMode(): ThemeMode {
        val savedMode = sharedPreferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(savedMode ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            Timber.w("ThemePreferences: Invalid theme mode $savedMode, defaulting to SYSTEM")
            ThemeMode.SYSTEM
        }
    }
    
    /**
     * Sets the theme mode with persistence
     */
    fun setThemeMode(mode: ThemeMode): Boolean {
        return try {
            sharedPreferences.edit()
                .putString(KEY_THEME_MODE, mode.name)
                .apply()
            
            Timber.i("ThemePreferences: Theme mode updated to $mode")
            true
        } catch (e: Exception) {
            Timber.e(e, "ThemePreferences: Failed to save theme mode $mode")
            false
        }
    }
    
    /**
     * Checks if automatic theme switching is enabled
     */
    fun isAutoSwitchEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_SWITCH_ENABLED, false)
    }
    
    /**
     * Sets automatic theme switching preference
     */
    fun setAutoSwitchEnabled(enabled: Boolean): Boolean {
        return try {
            sharedPreferences.edit()
                .putBoolean(KEY_AUTO_SWITCH_ENABLED, enabled)
                .apply()
            
            Timber.i("ThemePreferences: Auto switch enabled: $enabled")
            true
        } catch (e: Exception) {
            Timber.e(e, "ThemePreferences: Failed to save auto switch preference $enabled")
            false
        }
    }
    
    /**
     * Checks if migration from V1 to V2 has been completed
     */
    fun isMigrationCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_MIGRATION_COMPLETED, false)
    }
    
    /**
     * Marks migration from V1 to V2 as completed
     */
    fun setMigrationCompleted(completed: Boolean): Boolean {
        return try {
            sharedPreferences.edit()
                .putBoolean(KEY_MIGRATION_COMPLETED, completed)
                .putLong("migration_timestamp", System.currentTimeMillis())
                .apply()
            
            Timber.i("ThemePreferences: Migration completion status: $completed")
            true
        } catch (e: Exception) {
            Timber.e(e, "ThemePreferences: Failed to save migration status $completed")
            false
        }
    }
    
    /**
     * Gets the number of times user has rolled back to V1
     */
    fun getRollbackCount(): Int {
        return sharedPreferences.getInt(KEY_ROLLBACK_COUNT, 0)
    }
    
    /**
     * Increments rollback count for analytics and feature flag decisions
     */
    fun incrementRollbackCount(): Int {
        val newCount = getRollbackCount() + 1
        sharedPreferences.edit()
            .putInt(KEY_ROLLBACK_COUNT, newCount)
            .apply()
        
        Timber.i("ThemePreferences: Rollback count incremented to $newCount")
        return newCount
    }
    
    /**
     * Resets rollback count when user settles on V2
     */
    fun resetRollbackCount(): Boolean {
        return try {
            sharedPreferences.edit()
                .putInt(KEY_ROLLBACK_COUNT, 0)
                .apply()
            
            Timber.i("ThemePreferences: Rollback count reset")
            true
        } catch (e: Exception) {
            Timber.e(e, "ThemePreferences: Failed to reset rollback count")
            false
        }
    }
    
    /**
     * Gets all theme preferences as a summary for debugging
     */
    fun getPreferencesSummary(): ThemePreferencesSummary {
        return ThemePreferencesSummary(
            themeVersion = getThemeVersion(),
            themeMode = getThemeMode(),
            autoSwitchEnabled = isAutoSwitchEnabled(),
            migrationCompleted = isMigrationCompleted(),
            rollbackCount = getRollbackCount(),
            lastUpdated = sharedPreferences.getLong("last_updated", 0L)
        )
    }
    
    /**
     * Clears all theme preferences (for debugging/testing)
     */
    fun clearAllPreferences(): Boolean {
        return try {
            sharedPreferences.edit().clear().apply()
            _themeVersion.value = ThemeVersion.V2 // Reset to default
            
            Timber.w("ThemePreferences: All preferences cleared")
            true
        } catch (e: Exception) {
            Timber.e(e, "ThemePreferences: Failed to clear preferences")
            false
        }
    }
    
    /**
     * Validates that stored preferences are consistent
     */
    fun validatePreferences(): ValidationResult {
        val issues = mutableListOf<String>()
        
        try {
            // Validate theme version
            getThemeVersion()
        } catch (e: Exception) {
            issues.add("Invalid theme version: ${e.message}")
        }
        
        try {
            // Validate theme mode
            getThemeMode()
        } catch (e: Exception) {
            issues.add("Invalid theme mode: ${e.message}")
        }
        
        // Check for corrupted preference file
        if (sharedPreferences.all.isEmpty() && getRollbackCount() > 0) {
            issues.add("Preferences appear to be corrupted")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }
}

/**
 * Data class representing current theme preferences summary
 */
data class ThemePreferencesSummary(
    val themeVersion: ThemeVersion,
    val themeMode: ThemeMode,
    val autoSwitchEnabled: Boolean,
    val migrationCompleted: Boolean,
    val rollbackCount: Int,
    val lastUpdated: Long
)

/**
 * Data class representing validation result
 */
data class ValidationResult(
    val isValid: Boolean,
    val issues: List<String>
)