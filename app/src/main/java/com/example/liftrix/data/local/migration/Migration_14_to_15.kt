package com.example.liftrix.data.local.migration

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        Log.i("LiftrixMigration", "🔄 Migration 14→15 STARTED: Creating exercise_usage_history table")
        Timber.i("🔄 Migration 14→15 STARTED: Creating exercise_usage_history table")
        
        // Verify current version before migration
        val currentVersionQuery = database.query("PRAGMA user_version")
        if (currentVersionQuery.moveToFirst()) {
            val currentVersion = currentVersionQuery.getInt(0)
            Log.d("Migration", "Current database version before migration: $currentVersion")
            Timber.d("Current database version before migration: $currentVersion")
        }
        currentVersionQuery.close()
        
        // Create exercise_usage_history table with the complete schema
        database.execSQL("""
            CREATE TABLE exercise_usage_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                user_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                weight_used REAL NOT NULL,
                reps_performed INTEGER NOT NULL,
                sets_performed INTEGER NOT NULL,
                used_at INTEGER NOT NULL,
                workout_id TEXT,
                FOREIGN KEY(user_id) REFERENCES user_profile(id) ON DELETE CASCADE,
                FOREIGN KEY(exercise_id) REFERENCES exercise(id) ON DELETE CASCADE
            )
        """)
        
        // Create indices for performance
        database.execSQL("CREATE INDEX index_exercise_usage_history_user_id ON exercise_usage_history(user_id)")
        database.execSQL("CREATE INDEX index_exercise_usage_history_exercise_id ON exercise_usage_history(exercise_id)")
        database.execSQL("CREATE INDEX index_exercise_usage_history_user_id_exercise_id ON exercise_usage_history(user_id, exercise_id)")
        database.execSQL("CREATE INDEX index_exercise_usage_history_user_id_used_at ON exercise_usage_history(user_id, used_at)")
        
        // Verify migration completion
        Log.i("LiftrixMigration", "✅ Migration 14→15 COMPLETED: exercise_usage_history table created")
        Timber.i("✅ Migration 14→15 COMPLETED: exercise_usage_history table created")
        
        // Verify table creation
        val tableInfoQuery = database.query("PRAGMA table_info(exercise_usage_history)")
        var columnCount = 0
        while (tableInfoQuery.moveToNext()) {
            columnCount++
            val columnName = tableInfoQuery.getString(1)
            val dataType = tableInfoQuery.getString(2)
            Log.d("Migration", "Created column: $columnName ($dataType)")
        }
        tableInfoQuery.close()
        Log.i("LiftrixMigration", "📊 exercise_usage_history table validated: $columnCount columns")
        Timber.i("📊 exercise_usage_history table validated: $columnCount columns")
    }
}