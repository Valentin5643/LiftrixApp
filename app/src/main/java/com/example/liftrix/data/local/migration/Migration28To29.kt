package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migration from version 28 to 29
 * 
 * Fixes schema integrity issues by ensuring the WorkoutEntity index
 * is properly applied to resolve hash mismatch errors.
 * 
 * Changes:
 * - Ensures idx_workout_analytics index exists on workouts table
 * - No-op migration as the index should already exist from v27->v28
 * - This migration exists to force proper schema validation
 */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Ensure the workout analytics index exists
        // This is a safety measure to handle cases where the previous migration wasn't applied
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_workout_analytics 
            ON workouts(user_id, date, status)
        """)
    }
}