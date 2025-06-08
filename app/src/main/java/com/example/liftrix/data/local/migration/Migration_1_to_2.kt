package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration from version 1 to 2.
 * Adds the user_profiles table to support user onboarding data collection.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_profiles (
                user_id TEXT PRIMARY KEY NOT NULL,
                age INTEGER,
                weight_kg REAL,
                available_equipment_json TEXT,
                other_equipment TEXT,
                fitness_goals_json TEXT,
                goals_priority_json TEXT,
                completed_at TEXT,
                updated_at TEXT NOT NULL,
                profile_version INTEGER NOT NULL DEFAULT 1,
                is_synced INTEGER NOT NULL DEFAULT 0,
                sync_version INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
} 