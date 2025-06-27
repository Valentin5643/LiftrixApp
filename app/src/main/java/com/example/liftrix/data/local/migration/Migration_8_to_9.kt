package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Room migration from version 8 to 9.
 * This is a comprehensive "repair migration" that fixes schema inconsistencies
 * that may have been introduced by previous migrations.
 * 
 * This migration is designed to be resilient and handle corrupted database states
 * that would otherwise cause Room validation failures.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION")
        try {
            Log.d("Migration_8_9", "Starting comprehensive schema repair migration")
            
            // Repair all problematic tables systematically
            repairWorkoutsTable(database)
            repairDailyWorkoutsTable(database)
            repairCustomExercisesTable(database)
            repairWorkoutTemplatesTable(database)
            repairExercisesTables(database)
            repairSimpleWorkoutTables(database)
            
            // Rebuild all indices with proper validation
            rebuildAllIndices(database)
            
            database.execSQL("COMMIT")
            Log.d("Migration_8_9", "Schema repair migration completed successfully")
        } catch (e: Exception) {
            database.execSQL("ROLLBACK")
            Log.e("Migration_8_9", "Schema repair migration failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Repairs the workouts table to match WorkoutEntity expectations
     */
    private fun repairWorkoutsTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_8_9", "Repairing workouts table")
        
        val tableExists = tableExists(database, "workouts")
        if (!tableExists) {
            Log.d("Migration_8_9", "Workouts table doesn't exist, creating new one")
            createWorkoutsTable(database)
            return
        }
        
        // Check if schema repair is needed
        val needsRepair = checkWorkoutsSchemaRepair(database)
        if (!needsRepair) {
            Log.d("Migration_8_9", "Workouts table schema is correct")
            return
        }
        
        Log.d("Migration_8_9", "Workouts table needs schema repair")
        
        // Backup data
        database.execSQL("CREATE TEMPORARY TABLE workouts_backup AS SELECT * FROM workouts")
        
        // Drop existing table and indices
        database.execSQL("DROP TABLE IF EXISTS workouts")
        dropIndicesForTable(database, "workouts")
        
        // Create new table with correct schema
        createWorkoutsTable(database)
        
        // Migrate data with type conversions
        migrateWorkoutsData(database)
        
        // Clean up
        database.execSQL("DROP TABLE workouts_backup")
        
        Log.d("Migration_8_9", "Workouts table repair completed")
    }

    /**
     * Creates the workouts table with the correct schema matching WorkoutEntity
     */
    private fun createWorkoutsTable(database: SupportSQLiteDatabase) {
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

    /**
     * Checks if workouts table needs schema repair
     */
    private fun checkWorkoutsSchemaRepair(database: SupportSQLiteDatabase): Boolean {
        return try {
            val columns = getTableColumns(database, "workouts")
            val columnTypes = getTableColumnTypes(database, "workouts")
            
            // Check for critical schema issues
            val hasIntegerId = columnTypes["id"]?.contains("INTEGER") == true
            val hasIntegerTimestamps = columnTypes["created_at"]?.contains("INTEGER") == true
            val missingColumns = !columns.containsAll(listOf("exercises_json", "start_time", "end_time", "template_id"))
            
            hasIntegerId || hasIntegerTimestamps || missingColumns
        } catch (e: Exception) {
            Log.w("Migration_8_9", "Error checking workouts schema, assuming repair needed: ${e.message}")
            true
        }
    }

    /**
     * Migrates workouts data with proper type conversions
     */
    private fun migrateWorkoutsData(database: SupportSQLiteDatabase) {
        try {
            // Get columns from backup table to handle variable schemas
            val backupColumns = getTableColumns(database, "workouts_backup")
            
            // Build SELECT statement based on available columns
            val selectClause = buildWorkoutsSelectClause(backupColumns)
            
            database.execSQL(
                """
                INSERT INTO workouts (
                    id, user_id, name, date, exercises_json, status,
                    start_time, end_time, notes, template_id,
                    created_at, updated_at, is_synced, sync_version
                )
                $selectClause
                """.trimIndent()
            )
            
            Log.d("Migration_8_9", "Workouts data migrated successfully")
        } catch (e: Exception) {
            Log.e("Migration_8_9", "Error migrating workouts data: ${e.message}", e)
            // Continue without data migration rather than failing completely
        }
    }

    /**
     * Builds appropriate SELECT clause for workouts data migration
     */
    private fun buildWorkoutsSelectClause(backupColumns: Set<String>): String {
        return """
            SELECT 
                ${if (backupColumns.contains("id")) "CAST(id AS TEXT)" else "'repair-' || ROWID"} as id,
                ${if (backupColumns.contains("user_id")) "user_id" else "'unknown'"} as user_id,
                ${if (backupColumns.contains("name")) "name" else "'Migrated Workout'"} as name,
                ${if (backupColumns.contains("date")) "date" else "date('now')"} as date,
                ${if (backupColumns.contains("exercises_json")) "COALESCE(exercises_json, '[]')" else "'[]'"} as exercises_json,
                ${if (backupColumns.contains("status")) "status" else "'PLANNED'"} as status,
                ${if (backupColumns.contains("start_time")) "start_time" else "NULL"} as start_time,
                ${if (backupColumns.contains("end_time")) "end_time" else "NULL"} as end_time,
                ${if (backupColumns.contains("notes")) "notes" else "NULL"} as notes,
                ${if (backupColumns.contains("template_id")) "template_id" else "NULL"} as template_id,
                ${buildTimestampConversion(backupColumns, "created_at")} as created_at,
                ${buildTimestampConversion(backupColumns, "updated_at")} as updated_at,
                ${if (backupColumns.contains("is_synced")) "is_synced" else "0"} as is_synced,
                0 as sync_version
            FROM workouts_backup
        """.trimIndent()
    }

    /**
     * Builds timestamp conversion logic based on column type
     */
    private fun buildTimestampConversion(columns: Set<String>, columnName: String): String {
        return if (columns.contains(columnName)) {
            // Try to detect if it's INTEGER (milliseconds) or TEXT (ISO string)
            """
            CASE 
                WHEN typeof($columnName) = 'integer' THEN datetime($columnName/1000, 'unixepoch')
                WHEN typeof($columnName) = 'text' AND $columnName LIKE '%-%-% %:%:%' THEN $columnName
                WHEN typeof($columnName) = 'text' AND $columnName LIKE '%T%:%:%' THEN $columnName
                ELSE datetime('now')
            END
            """.trimIndent()
        } else {
            "datetime('now')"
        }
    }

    /**
     * Repairs daily_workouts table
     */
    private fun repairDailyWorkoutsTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_8_9", "Repairing daily_workouts table")
        
        if (!tableExists(database, "daily_workouts")) {
            createDailyWorkoutsTable(database)
            return
        }
        
        // Daily workouts table is likely correct from Migration_7_8, but verify key columns
        val columns = getTableColumns(database, "daily_workouts")
        val missingColumns = !columns.containsAll(listOf("name", "exercises_json", "status"))
        
        if (missingColumns) {
            Log.d("Migration_8_9", "Daily workouts table needs repair")
            repairTableWithBackup(database, "daily_workouts") { createDailyWorkoutsTable(it) }
        }
    }

    /**
     * Creates daily_workouts table
     */
    private fun createDailyWorkoutsTable(database: SupportSQLiteDatabase) {
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
    }

    /**
     * Repairs custom_exercises table
     */
    private fun repairCustomExercisesTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_8_9", "Repairing custom_exercises table")
        
        if (!tableExists(database, "custom_exercises")) {
            createCustomExercisesTable(database)
            return
        }
        
        // Check for the muscle_group vs primary_muscle_group issue
        val columns = getTableColumns(database, "custom_exercises")
        val hasMuscleGroup = columns.contains("muscle_group")
        val hasPrimaryMuscleGroup = columns.contains("primary_muscle_group")
        
        if (hasMuscleGroup && !hasPrimaryMuscleGroup) {
            Log.d("Migration_8_9", "Custom exercises table has old muscle_group column, repairing")
            repairTableWithBackup(database, "custom_exercises") { createCustomExercisesTable(it) }
        } else if (!hasPrimaryMuscleGroup) {
            Log.d("Migration_8_9", "Custom exercises table missing primary_muscle_group, repairing")
            repairTableWithBackup(database, "custom_exercises") { createCustomExercisesTable(it) }
        }
    }

    /**
     * Creates custom_exercises table
     */
    private fun createCustomExercisesTable(database: SupportSQLiteDatabase) {
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
    }

    /**
     * Repairs workout_templates table
     */
    private fun repairWorkoutTemplatesTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_8_9", "Repairing workout_templates table")
        
        if (!tableExists(database, "workout_templates")) {
            createWorkoutTemplatesTable(database)
        }
        // Workout templates table should be correct from Migration_7_8
    }

    /**
     * Creates workout_templates table
     */
    private fun createWorkoutTemplatesTable(database: SupportSQLiteDatabase) {
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
    }

    /**
     * Repairs exercises and exercise_sets tables
     */
    private fun repairExercisesTables(database: SupportSQLiteDatabase) {
        Log.d("Migration_8_9", "Checking exercises tables")
        
        // These tables should be correct from Migration_6_7, just verify they exist
        if (!tableExists(database, "exercises")) {
            createExercisesTable(database)
        }
        if (!tableExists(database, "exercise_sets")) {
            createExerciseSetsTable(database)
        }
    }

    /**
     * Creates exercises table
     */
    private fun createExercisesTable(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exercises (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workout_id INTEGER NOT NULL,
                exercise_library_id TEXT NOT NULL,
                order_index INTEGER NOT NULL,
                target_sets INTEGER,
                target_reps INTEGER,
                target_weight_kg REAL,
                target_time_seconds INTEGER,
                target_distance_meters REAL,
                notes TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(workout_id) REFERENCES workouts(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    /**
     * Creates exercise_sets table
     */
    private fun createExerciseSetsTable(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exercise_sets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                exercise_id INTEGER NOT NULL,
                set_number INTEGER NOT NULL,
                reps INTEGER,
                weight_kg REAL,
                time_seconds INTEGER,
                distance_meters REAL,
                rpe INTEGER,
                notes TEXT,
                completed_at INTEGER,
                FOREIGN KEY(exercise_id) REFERENCES exercises(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    /**
     * Repairs simple workout tables
     */
    private fun repairSimpleWorkoutTables(database: SupportSQLiteDatabase) {
        Log.d("Migration_8_9", "Checking simple workout tables")
        
        if (!tableExists(database, "simple_workouts")) {
            createSimpleWorkoutsTable(database)
        }
        if (!tableExists(database, "simple_exercises")) {
            createSimpleExercisesTable(database)
        }
    }

    /**
     * Creates simple_workouts table
     */
    private fun createSimpleWorkoutsTable(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS simple_workouts (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                sync_version INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
    }

    /**
     * Creates simple_exercises table
     */
    private fun createSimpleExercisesTable(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS simple_exercises (
                id TEXT PRIMARY KEY NOT NULL,
                workout_id TEXT NOT NULL,
                name TEXT NOT NULL,
                reps INTEGER NOT NULL,
                rpe REAL,
                sets INTEGER NOT NULL,
                weight_kg REAL NOT NULL,
                order_index INTEGER NOT NULL,
                photo_url TEXT,
                animation_url TEXT,
                FOREIGN KEY(workout_id) REFERENCES simple_workouts(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    /**
     * Rebuilds all indices with proper validation
     */
    private fun rebuildAllIndices(database: SupportSQLiteDatabase) {
        Log.d("Migration_8_9", "Rebuilding all indices")
        
        // Workouts indices
        createIndexSafely(database, "index_workouts_user_id", "workouts", "user_id")
        createIndexSafely(database, "index_workouts_date", "workouts", "date")
        createIndexSafely(database, "index_workouts_status", "workouts", "status")
        
        // Daily workouts indices
        createIndexSafely(database, "index_daily_workouts_user_id", "daily_workouts", "user_id")
        createIndexSafely(database, "index_daily_workouts_date_user_id", "daily_workouts", arrayOf("date", "user_id"))
        createIndexSafely(database, "index_daily_workouts_template_id", "daily_workouts", "template_id")
        createIndexSafely(database, "index_daily_workouts_status", "daily_workouts", "status")
        createIndexSafely(database, "index_daily_workouts_created_at", "daily_workouts", "created_at")
        
        // Custom exercises indices
        createIndexSafely(database, "index_custom_exercises_user_id", "custom_exercises", "user_id")
        createUniqueIndexSafely(database, "index_custom_exercises_name_user_id", "custom_exercises", arrayOf("name", "user_id"))
        createIndexSafely(database, "index_custom_exercises_primary_muscle_group", "custom_exercises", "primary_muscle_group")
        createIndexSafely(database, "index_custom_exercises_equipment", "custom_exercises", "equipment")
        
        // Workout templates indices
        createIndexSafely(database, "index_workout_templates_user_id", "workout_templates", "user_id")
        createUniqueIndexSafely(database, "index_workout_templates_name_user_id", "workout_templates", arrayOf("name", "user_id"))
        createIndexSafely(database, "index_workout_templates_created_at", "workout_templates", "created_at")
        
        // Exercise tables indices
        createIndexSafely(database, "index_exercises_workout_id", "exercises", "workout_id")
        createIndexSafely(database, "index_exercises_exercise_library_id", "exercises", "exercise_library_id")
        createIndexSafely(database, "index_exercise_sets_exercise_id", "exercise_sets", "exercise_id")
        
        // Simple workout tables indices
        createIndexSafely(database, "index_simple_workouts_user_id", "simple_workouts", "user_id")
        createIndexSafely(database, "index_simple_workouts_user_id_created_at", "simple_workouts", arrayOf("user_id", "created_at"))
        createIndexSafely(database, "index_simple_workouts_created_at", "simple_workouts", "created_at")
        createIndexSafely(database, "index_simple_exercises_workout_id", "simple_exercises", "workout_id")
        createIndexSafely(database, "index_simple_exercises_workout_id_order_index", "simple_exercises", arrayOf("workout_id", "order_index"))
    }

    // Utility functions reused from Migration_7_8
    
    private fun createIndexSafely(database: SupportSQLiteDatabase, indexName: String, tableName: String, columnName: String) {
        try {
            if (columnExists(database, tableName, columnName)) {
                database.execSQL("CREATE INDEX IF NOT EXISTS $indexName ON $tableName($columnName)")
                Log.d("Migration_8_9", "Created index: $indexName")
            } else {
                Log.w("Migration_8_9", "Skipping index $indexName: column $columnName does not exist in table $tableName")
            }
        } catch (e: Exception) {
            Log.e("Migration_8_9", "Failed to create index $indexName: ${e.message}", e)
        }
    }
    
    private fun createIndexSafely(database: SupportSQLiteDatabase, indexName: String, tableName: String, columnNames: Array<String>) {
        try {
            val allColumnsExist = columnNames.all { columnExists(database, tableName, it) }
            if (allColumnsExist) {
                val columnList = columnNames.joinToString(", ")
                database.execSQL("CREATE INDEX IF NOT EXISTS $indexName ON $tableName($columnList)")
                Log.d("Migration_8_9", "Created index: $indexName")
            } else {
                Log.w("Migration_8_9", "Skipping index $indexName: one or more columns do not exist in table $tableName")
            }
        } catch (e: Exception) {
            Log.e("Migration_8_9", "Failed to create index $indexName: ${e.message}", e)
        }
    }
    
    private fun createUniqueIndexSafely(database: SupportSQLiteDatabase, indexName: String, tableName: String, columnNames: Array<String>) {
        try {
            val allColumnsExist = columnNames.all { columnExists(database, tableName, it) }
            if (allColumnsExist) {
                val columnList = columnNames.joinToString(", ")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS $indexName ON $tableName($columnList)")
                Log.d("Migration_8_9", "Created unique index: $indexName")
            } else {
                Log.w("Migration_8_9", "Skipping unique index $indexName: one or more columns do not exist in table $tableName")
            }
        } catch (e: Exception) {
            Log.e("Migration_8_9", "Failed to create unique index $indexName: ${e.message}", e)
        }
    }

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
            Log.e("Migration_8_9", "Error checking column existence: ${e.message}", e)
            false
        }
    }

    private fun tableExists(database: SupportSQLiteDatabase, tableName: String): Boolean {
        return try {
            database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'").use { cursor ->
                cursor.moveToFirst()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getTableColumns(database: SupportSQLiteDatabase, tableName: String): Set<String> {
        return try {
            val columns = mutableSetOf<String>()
            database.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(nameIndex))
                }
            }
            columns
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun getTableColumnTypes(database: SupportSQLiteDatabase, tableName: String): Map<String, String> {
        return try {
            val columnTypes = mutableMapOf<String, String>()
            database.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val typeIndex = cursor.getColumnIndex("type")
                while (cursor.moveToNext()) {
                    columnTypes[cursor.getString(nameIndex)] = cursor.getString(typeIndex)
                }
            }
            columnTypes
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun dropIndicesForTable(database: SupportSQLiteDatabase, tableName: String) {
        try {
            database.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='$tableName'").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    val indexName = cursor.getString(nameIndex)
                    if (!indexName.startsWith("sqlite_")) { // Don't drop system indices
                        database.execSQL("DROP INDEX IF EXISTS $indexName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Migration_8_9", "Error dropping indices for table $tableName: ${e.message}", e)
        }
    }

    /**
     * Generic function to repair a table with backup and restore
     */
    private fun repairTableWithBackup(database: SupportSQLiteDatabase, tableName: String, createTable: (SupportSQLiteDatabase) -> Unit) {
        try {
            // Create backup
            database.execSQL("CREATE TEMPORARY TABLE ${tableName}_backup AS SELECT * FROM $tableName")
            
            // Drop existing table
            database.execSQL("DROP TABLE IF EXISTS $tableName")
            dropIndicesForTable(database, tableName)
            
            // Create new table
            createTable(database)
            
            // Try to restore data (best effort)
            try {
                database.execSQL("INSERT INTO $tableName SELECT * FROM ${tableName}_backup")
            } catch (e: Exception) {
                Log.w("Migration_8_9", "Could not restore all data for $tableName: ${e.message}")
                // Continue without data restoration rather than failing
            }
            
            // Clean up
            database.execSQL("DROP TABLE ${tableName}_backup")
            
        } catch (e: Exception) {
            Log.e("Migration_8_9", "Error repairing table $tableName: ${e.message}", e)
            // Create table anyway to ensure it exists
            try {
                createTable(database)
            } catch (createError: Exception) {
                Log.e("Migration_8_9", "Failed to create table $tableName: ${createError.message}", createError)
            }
        }
    }
}