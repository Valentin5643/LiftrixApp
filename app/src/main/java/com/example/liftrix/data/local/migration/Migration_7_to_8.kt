package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Room migration from version 7 to 8.
 * Creates the missing workout_templates table and daily_workouts table.
 * Fixes schema consistency with Room entity definitions.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION")
        try {
            Log.d("Migration_7_8", "Starting migration from version 7 to 8")
            
            // Create workout_templates table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS workout_templates (
                    id TEXT PRIMARY KEY NOT NULL,
                    user_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    template_exercises_json TEXT NOT NULL,
                    estimated_duration_minutes INTEGER,
                    difficulty_level INTEGER,
                    tags TEXT,
                    usage_count INTEGER NOT NULL DEFAULT 0,
                    last_used_at TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    sync_version INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            
            // Create indices for workout_templates
            createIndexSafely(database, "index_workout_templates_user_id", "workout_templates", "user_id")
            createUniqueIndexSafely(database, "index_workout_templates_name_user_id", "workout_templates", arrayOf("name", "user_id"))
            createIndexSafely(database, "index_workout_templates_created_at", "workout_templates", "created_at")
            
            // Fix workouts table schema to match WorkoutEntity
            // The table may exist from Migration_6_7 with incompatible schema (INTEGER id, INTEGER timestamps)
            // We need to recreate it with the correct schema (TEXT id, TEXT timestamps)
            recreateWorkoutsTable(database)
            
            // Create indices for workouts
            createIndexSafely(database, "index_workouts_user_id", "workouts", "user_id")
            createIndexSafely(database, "index_workouts_date", "workouts", "date")
            createIndexSafely(database, "index_workouts_status", "workouts", "status")
            
            // Create daily_workouts table - Fixed to match DailyWorkoutEntity
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_workouts (
                    id TEXT PRIMARY KEY NOT NULL,
                    user_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    date TEXT NOT NULL,
                    template_id TEXT,
                    exercises_json TEXT NOT NULL,
                    status TEXT NOT NULL,
                    start_time TEXT,
                    end_time TEXT,
                    duration_minutes INTEGER,
                    total_volume_kg REAL,
                    total_sets INTEGER,
                    total_reps INTEGER,
                    notes TEXT,
                    rating INTEGER,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    sync_version INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY(template_id) REFERENCES workout_templates(id) ON DELETE SET NULL
                )
                """.trimIndent()
            )
            
            // Create indices for daily_workouts - Fixed to match DailyWorkoutEntity indices
            createIndexSafely(database, "index_daily_workouts_user_id", "daily_workouts", "user_id")
            createIndexSafely(database, "index_daily_workouts_date_user_id", "daily_workouts", arrayOf("date", "user_id"))
            createIndexSafely(database, "index_daily_workouts_template_id", "daily_workouts", "template_id")
            createIndexSafely(database, "index_daily_workouts_status", "daily_workouts", "status")
            createIndexSafely(database, "index_daily_workouts_created_at", "daily_workouts", "created_at")
            
            // Create custom_exercises table with correct column names to match CustomExerciseEntity
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS custom_exercises (
                    id TEXT PRIMARY KEY NOT NULL,
                    user_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    primary_muscle_group TEXT NOT NULL,
                    equipment TEXT NOT NULL,
                    secondary_muscle_groups TEXT,
                    difficulty INTEGER,
                    notes TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    sync_version INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            
            // Create indices for custom_exercises with correct column names
            createIndexSafely(database, "index_custom_exercises_user_id", "custom_exercises", "user_id")
            createUniqueIndexSafely(database, "index_custom_exercises_name_user_id", "custom_exercises", arrayOf("name", "user_id"))
            createIndexSafely(database, "index_custom_exercises_primary_muscle_group", "custom_exercises", "primary_muscle_group")
            createIndexSafely(database, "index_custom_exercises_equipment", "custom_exercises", "equipment")
            
            database.execSQL("COMMIT")
            Log.d("Migration_7_8", "Migration completed successfully")
        } catch (e: Exception) {
            database.execSQL("ROLLBACK")
            Log.e("Migration_7_8", "Migration failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Safely creates an index only if the column exists in the table.
     * Prevents crashes due to missing columns.
     */
    private fun createIndexSafely(
        database: SupportSQLiteDatabase,
        indexName: String,
        tableName: String,
        columnName: String
    ) {
        try {
            if (columnExists(database, tableName, columnName)) {
                database.execSQL("CREATE INDEX IF NOT EXISTS $indexName ON $tableName($columnName)")
                Log.d("Migration_7_8", "Created index: $indexName")
            } else {
                Log.w("Migration_7_8", "Skipping index $indexName: column $columnName does not exist in table $tableName")
            }
        } catch (e: Exception) {
            Log.e("Migration_7_8", "Failed to create index $indexName: ${e.message}", e)
        }
    }
    
    /**
     * Safely creates an index on multiple columns only if all columns exist in the table.
     */
    private fun createIndexSafely(
        database: SupportSQLiteDatabase,
        indexName: String,
        tableName: String,
        columnNames: Array<String>
    ) {
        try {
            val allColumnsExist = columnNames.all { columnExists(database, tableName, it) }
            if (allColumnsExist) {
                val columnList = columnNames.joinToString(", ")
                database.execSQL("CREATE INDEX IF NOT EXISTS $indexName ON $tableName($columnList)")
                Log.d("Migration_7_8", "Created index: $indexName")
            } else {
                Log.w("Migration_7_8", "Skipping index $indexName: one or more columns do not exist in table $tableName")
            }
        } catch (e: Exception) {
            Log.e("Migration_7_8", "Failed to create index $indexName: ${e.message}", e)
        }
    }
    
    /**
     * Safely creates a unique index only if the columns exist in the table.
     */
    private fun createUniqueIndexSafely(
        database: SupportSQLiteDatabase,
        indexName: String,
        tableName: String,
        columnNames: Array<String>
    ) {
        try {
            val allColumnsExist = columnNames.all { columnExists(database, tableName, it) }
            if (allColumnsExist) {
                val columnList = columnNames.joinToString(", ")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS $indexName ON $tableName($columnList)")
                Log.d("Migration_7_8", "Created unique index: $indexName")
            } else {
                Log.w("Migration_7_8", "Skipping unique index $indexName: one or more columns do not exist in table $tableName")
            }
        } catch (e: Exception) {
            Log.e("Migration_7_8", "Failed to create unique index $indexName: ${e.message}", e)
        }
    }
    
    /**
     * Recreates the workouts table with the correct schema to match WorkoutEntity.
     * Handles migration from Migration_6_7 schema to the correct schema.
     */
    private fun recreateWorkoutsTable(database: SupportSQLiteDatabase) {
        try {
            // Check if workouts table exists and get its schema
            val tableExists = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='workouts'").use { cursor ->
                cursor.moveToFirst()
            }
            
            if (tableExists) {
                Log.d("Migration_7_8", "Workouts table exists, checking schema compatibility")
                
                // Check if the existing table has the wrong schema (INTEGER id from Migration_6_7)
                val hasIntegerId = database.query("PRAGMA table_info(workouts)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    val typeIndex = cursor.getColumnIndex("type")
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "id" && 
                            cursor.getString(typeIndex).contains("INTEGER")) {
                            return@use true
                        }
                    }
                    false
                }
                
                if (hasIntegerId) {
                    Log.d("Migration_7_8", "Workouts table has incorrect schema, recreating...")
                    
                    // Step 1: Rename existing table
                    database.execSQL("ALTER TABLE workouts RENAME TO workouts_old")
                    
                    // Step 2: Create new table with correct schema
                    database.execSQL(
                        """
                        CREATE TABLE workouts (
                            id TEXT PRIMARY KEY NOT NULL,
                            user_id TEXT NOT NULL,
                            name TEXT NOT NULL,
                            date TEXT NOT NULL,
                            exercises_json TEXT NOT NULL,
                            status TEXT NOT NULL,
                            start_time TEXT,
                            end_time TEXT,
                            notes TEXT,
                            template_id TEXT,
                            created_at TEXT NOT NULL,
                            updated_at TEXT NOT NULL,
                            is_synced INTEGER NOT NULL DEFAULT 0,
                            sync_version INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                    
                    // Step 3: Migrate data with proper type conversions
                    database.execSQL(
                        """
                        INSERT INTO workouts (
                            id, user_id, name, date, exercises_json, status, 
                            start_time, end_time, notes, template_id,
                            created_at, updated_at, is_synced, sync_version
                        )
                        SELECT 
                            CAST(id AS TEXT) as id,
                            user_id,
                            name,
                            date,
                            COALESCE(exercises_json, '[]') as exercises_json,
                            status,
                            NULL as start_time,
                            NULL as end_time,
                            notes,
                            NULL as template_id,
                            datetime(created_at/1000, 'unixepoch') as created_at,
                            datetime(updated_at/1000, 'unixepoch') as updated_at,
                            is_synced,
                            0 as sync_version
                        FROM workouts_old
                        """.trimIndent()
                    )
                    
                    // Step 4: Drop old table
                    database.execSQL("DROP TABLE workouts_old")
                    
                    Log.d("Migration_7_8", "Workouts table recreated successfully")
                } else {
                    Log.d("Migration_7_8", "Workouts table schema is already correct")
                }
            } else {
                Log.d("Migration_7_8", "Creating new workouts table")
                
                // Create table with correct schema
                database.execSQL(
                    """
                    CREATE TABLE workouts (
                        id TEXT PRIMARY KEY NOT NULL,
                        user_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        date TEXT NOT NULL,
                        exercises_json TEXT NOT NULL,
                        status TEXT NOT NULL,
                        start_time TEXT,
                        end_time TEXT,
                        notes TEXT,
                        template_id TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        is_synced INTEGER NOT NULL DEFAULT 0,
                        sync_version INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
            
        } catch (e: Exception) {
            Log.e("Migration_7_8", "Error recreating workouts table: ${e.message}", e)
            throw e
        }
    }

    /**
     * Checks if a column exists in the specified table.
     * Uses PRAGMA table_info to validate column existence.
     */
    private fun columnExists(database: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        return try {
            database.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameColumnIndex = cursor.getColumnIndex("name")
                if (nameColumnIndex < 0) return false
                
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumnIndex) == columnName) {
                        return true
                    }
                }
                false
            }
        } catch (e: Exception) {
            Log.e("Migration_7_8", "Error checking column existence: ${e.message}", e)
            false
        }
    }
}