package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 33 to 34
 * Removes calorie_analytics_enabled column from user_settings table
 * 
 * This migration aligns the database schema with the updated SettingsEntity
 * after removing calorie analytics from the settings-based approach in favor
 * of widget-based calorie analytics in the dashboard customization system.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Drop the calorie_analytics_enabled column from user_settings table
        // SQLite doesn't support DROP COLUMN directly, so we need to recreate the table
        
        // 1. Create new table without the calorie_analytics_enabled column
        database.execSQL("""
            CREATE TABLE user_settings_new (
                user_id TEXT NOT NULL PRIMARY KEY,
                dark_mode INTEGER NOT NULL,
                notifications_enabled INTEGER NOT NULL,
                weight_unit TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
        """.trimIndent())
        
        // 2. Copy data from old table to new table (excluding calorie_analytics_enabled)
        database.execSQL("""
            INSERT INTO user_settings_new (user_id, dark_mode, notifications_enabled, weight_unit, updated_at)
            SELECT user_id, dark_mode, notifications_enabled, weight_unit, updated_at
            FROM user_settings
        """.trimIndent())
        
        // 3. Drop the old table
        database.execSQL("DROP TABLE user_settings")
        
        // 4. Rename new table to original name
        database.execSQL("ALTER TABLE user_settings_new RENAME TO user_settings")
    }
}