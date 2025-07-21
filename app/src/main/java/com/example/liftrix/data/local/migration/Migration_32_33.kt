package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 32 to 33
 * Adds calorie_analytics_enabled column to user_settings table
 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add calorie_analytics_enabled column to user_settings table
        // Default to false (opt-in preference)
        database.execSQL(
            "ALTER TABLE user_settings ADD COLUMN calorie_analytics_enabled INTEGER NOT NULL DEFAULT 0"
        )
    }
}