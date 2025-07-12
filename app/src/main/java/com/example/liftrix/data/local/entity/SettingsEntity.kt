package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import java.time.Instant

/**
 * Room entity representing user settings in local database
 * Stores theme preferences, notification settings, and other user configurations
 * 
 * Uses snake_case column names following existing database conventions
 * and proper type converters for Instant fields
 */
@Entity(tableName = "user_settings")
@TypeConverters(DateTimeConverters::class)
data class SettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "dark_mode")
    val darkMode: Boolean = false,
    
    @ColumnInfo(name = "notifications_enabled")
    val notificationsEnabled: Boolean = true,
    
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
            updatedAt = Instant.now()
        )
    }
} 