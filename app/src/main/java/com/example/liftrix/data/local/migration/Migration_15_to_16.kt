package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Drop the existing exercise_usage_history table
        database.execSQL("DROP TABLE IF EXISTS exercise_usage_history")
        
        // Create the new exercise_usage_history table with correct structure
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS exercise_usage_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                user_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                weight_used REAL NOT NULL,
                reps_performed INTEGER NOT NULL,
                sets_performed INTEGER NOT NULL,
                used_at TEXT NOT NULL,
                workout_id TEXT,
                FOREIGN KEY(user_id) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(exercise_id) REFERENCES exercise_library(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        
        // Create the required indices
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_usage_history_user_id ON exercise_usage_history (user_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_usage_history_exercise_id ON exercise_usage_history (exercise_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_usage_history_user_id_exercise_id ON exercise_usage_history (user_id, exercise_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_usage_history_user_id_used_at ON exercise_usage_history (user_id, used_at)")
    }
}