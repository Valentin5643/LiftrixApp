package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Migration from version 20 to 21
 * 
 * This migration explicitly handles the active_workout_sessions table consistency issue.
 * It verifies the table exists and matches the expected schema, recreating it if necessary.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        Log.d("Migration_20_21", "Starting migration 20→21: Verifying active_workout_sessions table schema")
        
        try {
            // Verify active_workout_sessions table exists and has correct schema
            verifyActiveWorkoutSessionsTable(database)
            
            Log.d("Migration_20_21", "Migration completed successfully")
        } catch (e: Exception) {
            Log.e("Migration_20_21", "Migration failed: ${e.message}", e)
            throw e
        }
    }
    
    private fun verifyActiveWorkoutSessionsTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_20_21", "Verifying active_workout_sessions table schema")
        
        // Check if table exists
        val tableExists = database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='active_workout_sessions'"
        ).use { cursor -> cursor.moveToFirst() }
        
        if (!tableExists) {
            Log.w("Migration_20_21", "active_workout_sessions table does not exist, creating it")
            createActiveWorkoutSessionsTable(database)
            return
        }
        
        // Verify table schema matches expected structure
        val tableInfo = database.query("PRAGMA table_info(active_workout_sessions)")
        val columns = mutableMapOf<String, String>()
        
        tableInfo.use { cursor ->
            while (cursor.moveToNext()) {
                val columnName = cursor.getString(cursor.getColumnIndex("name"))
                val columnType = cursor.getString(cursor.getColumnIndex("type"))
                val notNull = cursor.getInt(cursor.getColumnIndex("notnull")) == 1
                val defaultValue = cursor.getString(cursor.getColumnIndex("dflt_value"))
                
                columns[columnName] = "$columnType${if (notNull) " NOT NULL" else ""}${if (defaultValue != null) " DEFAULT $defaultValue" else ""}"
            }
        }
        
        // Expected columns (21 total)
        val expectedColumns = mapOf(
            "id" to "TEXT NOT NULL",
            "user_id" to "TEXT NOT NULL", 
            "name" to "TEXT NOT NULL",
            "template_id" to "TEXT",
            "exercises_json" to "TEXT NOT NULL",
            "current_exercise_index" to "INTEGER NOT NULL DEFAULT 0",
            "session_state" to "TEXT NOT NULL",
            "started_at" to "TEXT NOT NULL",
            "paused_at" to "TEXT",
            "resumed_at" to "TEXT",
            "total_paused_duration" to "INTEGER NOT NULL DEFAULT 0",
            "notes" to "TEXT",
            "last_modified" to "TEXT NOT NULL",
            "rest_timer_start_time" to "TEXT",
            "rest_timer_duration_seconds" to "INTEGER",
            "rest_timer_paused_at" to "TEXT",
            "auto_save_enabled" to "INTEGER NOT NULL DEFAULT 1",
            "last_auto_save" to "TEXT",
            "recovery_data_json" to "TEXT",
            "is_synced" to "INTEGER NOT NULL DEFAULT 0",
            "sync_version" to "INTEGER NOT NULL DEFAULT 1"
        )
        
        // Check if all expected columns exist
        val missingColumns = expectedColumns.keys - columns.keys
        if (missingColumns.isNotEmpty()) {
            Log.w("Migration_20_21", "Missing columns in active_workout_sessions: $missingColumns")
            Log.w("Migration_20_21", "Recreating active_workout_sessions table with correct schema")
            recreateActiveWorkoutSessionsTable(database)
            return
        }
        
        // Verify indexes exist
        verifyIndexes(database)
        
        Log.d("Migration_20_21", "active_workout_sessions table schema verified successfully")
    }
    
    private fun createActiveWorkoutSessionsTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_20_21", "Creating active_workout_sessions table")
        
        // Create active_workout_sessions table with full schema
        database.execSQL("""
            CREATE TABLE active_workout_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                user_id TEXT NOT NULL,
                name TEXT NOT NULL,
                template_id TEXT,
                exercises_json TEXT NOT NULL,
                current_exercise_index INTEGER NOT NULL DEFAULT 0,
                session_state TEXT NOT NULL,
                started_at TEXT NOT NULL,
                paused_at TEXT,
                resumed_at TEXT,
                total_paused_duration INTEGER NOT NULL DEFAULT 0,
                notes TEXT,
                last_modified TEXT NOT NULL,
                rest_timer_start_time TEXT,
                rest_timer_duration_seconds INTEGER,
                rest_timer_paused_at TEXT,
                auto_save_enabled INTEGER NOT NULL DEFAULT 1,
                last_auto_save TEXT,
                recovery_data_json TEXT,
                is_synced INTEGER NOT NULL DEFAULT 0,
                sync_version INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        
        createIndexes(database)
        
        Log.d("Migration_20_21", "active_workout_sessions table created successfully")
    }
    
    private fun recreateActiveWorkoutSessionsTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_20_21", "Recreating active_workout_sessions table")
        
        // Backup existing data if any
        val hasData = database.query("SELECT COUNT(*) FROM active_workout_sessions").use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) > 0
        }
        
        if (hasData) {
            Log.d("Migration_20_21", "Backing up existing active_workout_sessions data")
            database.execSQL("""
                CREATE TEMPORARY TABLE active_workout_sessions_backup AS 
                SELECT * FROM active_workout_sessions
            """.trimIndent())
        }
        
        // Drop existing table
        database.execSQL("DROP TABLE active_workout_sessions")
        
        // Create new table with correct schema
        createActiveWorkoutSessionsTable(database)
        
        // Restore data if we backed it up
        if (hasData) {
            Log.d("Migration_20_21", "Attempting to restore active_workout_sessions data")
            try {
                database.execSQL("""
                    INSERT INTO active_workout_sessions (
                        id, user_id, name, template_id, exercises_json, current_exercise_index,
                        session_state, started_at, paused_at, resumed_at, total_paused_duration,
                        notes, last_modified, rest_timer_start_time, rest_timer_duration_seconds,
                        rest_timer_paused_at, auto_save_enabled, last_auto_save, recovery_data_json,
                        is_synced, sync_version
                    )
                    SELECT 
                        id, user_id, name, template_id, exercises_json, 
                        COALESCE(current_exercise_index, 0),
                        session_state, started_at, paused_at, resumed_at, 
                        COALESCE(total_paused_duration, 0),
                        notes, last_modified, rest_timer_start_time, rest_timer_duration_seconds,
                        rest_timer_paused_at, COALESCE(auto_save_enabled, 1), last_auto_save, 
                        recovery_data_json, COALESCE(is_synced, 0), COALESCE(sync_version, 1)
                    FROM active_workout_sessions_backup
                """.trimIndent())
                
                Log.d("Migration_20_21", "Successfully restored active_workout_sessions data")
            } catch (e: Exception) {
                Log.w("Migration_20_21", "Failed to restore data, continuing with empty table: ${e.message}")
            }
            
            // Drop backup table
            database.execSQL("DROP TABLE active_workout_sessions_backup")
        }
    }
    
    private fun createIndexes(database: SupportSQLiteDatabase) {
        // Create indexes for active_workout_sessions table
        database.execSQL("CREATE INDEX IF NOT EXISTS index_active_sessions_user_id ON active_workout_sessions(user_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_active_sessions_started_at ON active_workout_sessions(started_at)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_active_sessions_template_id ON active_workout_sessions(template_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_active_sessions_state ON active_workout_sessions(session_state)")
    }
    
    private fun verifyIndexes(database: SupportSQLiteDatabase) {
        val expectedIndexes = setOf(
            "index_active_sessions_user_id",
            "index_active_sessions_started_at", 
            "index_active_sessions_template_id",
            "index_active_sessions_state"
        )
        
        val existingIndexes = mutableSetOf<String>()
        database.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='active_workout_sessions'").use { cursor ->
            while (cursor.moveToNext()) {
                val indexName = cursor.getString(0)
                if (indexName.startsWith("index_active_sessions_")) {
                    existingIndexes.add(indexName)
                }
            }
        }
        
        val missingIndexes = expectedIndexes - existingIndexes
        if (missingIndexes.isNotEmpty()) {
            Log.d("Migration_20_21", "Creating missing indexes: $missingIndexes")
            createIndexes(database)
        }
    }
}