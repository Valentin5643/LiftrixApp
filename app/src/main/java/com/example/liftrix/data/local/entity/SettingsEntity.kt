package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.WeightUnitConverter
import com.example.liftrix.domain.model.WeightUnit
import java.time.Instant

/**
 * Room entity representing user settings in local database
 * Stores theme preferences, notification settings, and other user configurations
 * 
 * Uses snake_case column names following existing database conventions
 * and proper type converters for Instant and WeightUnit fields
 */
@Entity(tableName = "user_settings")
@TypeConverters(DateTimeConverters::class, WeightUnitConverter::class)
data class SettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "dark_mode")
    val darkMode: Boolean = false,
    
    @ColumnInfo(name = "notifications_enabled")
    val notificationsEnabled: Boolean = true,
    
    @ColumnInfo(name = "weight_unit")
    val weightUnit: WeightUnit = WeightUnit.getSystemDefault(),
    
    @ColumnInfo(name = "terminology_preference", defaultValue = "'NEW'")
    val terminologyPreference: String = "NEW", // NEW or LEGACY
    
    @ColumnInfo(name = "migration_completed", defaultValue = "0")
    val migrationCompleted: Boolean = false,
    
    @ColumnInfo(name = "migration_explanation_seen", defaultValue = "0")
    val migrationExplanationSeen: Boolean = false,
    
    @ColumnInfo(name = "settings_version", defaultValue = "1")
    val settingsVersion: Int = 1,
    
    @ColumnInfo(name = "last_sync_timestamp")
    val lastSyncTimestamp: Long? = null,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
) {
    companion object {
        /**
         * Creates default settings for a new user
         * 
         * @param userId The user's unique identifier
         * @return SettingsEntity with default values
         */
        fun createDefault(userId: String): SettingsEntity = SettingsEntity(
            userId = userId,
            darkMode = false,
            notificationsEnabled = true,
            weightUnit = WeightUnit.getSystemDefault(),
            terminologyPreference = "NEW",
            migrationCompleted = false,
            migrationExplanationSeen = false,
            settingsVersion = 1,
            lastSyncTimestamp = null,
            updatedAt = Instant.now()
        )
    }
} 