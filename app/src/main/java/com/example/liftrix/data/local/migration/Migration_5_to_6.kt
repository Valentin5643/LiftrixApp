package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration from version 5 to 6.
 * Adds simple workout and exercise tables for the new simplified workout creation flow.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create simple_workouts table
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS simple_workouts (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                name TEXT NOT NULL CHECK(length(name) >= 3 AND length(name) <= 100),
                description TEXT CHECK(description IS NULL OR length(description) <= 500),
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                sync_version INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        
        // Create simple_exercises table with foreign key relationship
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS simple_exercises (
                id TEXT PRIMARY KEY NOT NULL,
                workout_id TEXT NOT NULL,
                name TEXT NOT NULL CHECK(length(name) >= 2 AND length(name) <= 100),
                reps INTEGER NOT NULL CHECK(reps >= 1 AND reps <= 999),
                rpe INTEGER CHECK(rpe IS NULL OR (rpe >= 1 AND rpe <= 10)),
                sets INTEGER NOT NULL CHECK(sets >= 1 AND sets <= 50),
                weight_kg REAL CHECK(weight_kg IS NULL OR (weight_kg >= 0 AND weight_kg <= 999.9)),
                order_index INTEGER NOT NULL CHECK(order_index >= 0),
                created_at TEXT NOT NULL,
                FOREIGN KEY (workout_id) REFERENCES simple_workouts(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        
        // Create performance indexes
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_simple_workouts_user_created " +
            "ON simple_workouts(user_id, created_at DESC)"
        )
        
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_simple_workouts_sync " +
            "ON simple_workouts(user_id, is_synced, sync_version)"
        )
        
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_simple_exercises_workout_order " +
            "ON simple_exercises(workout_id, order_index)"
        )
        
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_simple_exercises_workout " +
            "ON simple_exercises(workout_id)"
        )
    }
}