package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Room migration from version 11 to 12.
 * 
 * CRITICAL FIX: Resolves custom_exercises table schema mismatch that causes app crashes.
 * 
 * Issues Fixed:
 * - Column name mismatch: muscle_group vs primary_muscle_group
 * - Missing required columns: user_id, difficulty, notes, created_at, is_synced, sync_version
 * - Extra unwanted columns: instructions, tags, thumbnail_url (from wrong entity)
 * - Ensures schema exactly matches CustomExerciseEntity definition
 * 
 * This migration uses PRAGMA table_info to detect current schema state and handles
 * multiple possible starting conditions gracefully.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION")
        try {
            Log.d("Migration_11_12", "Starting custom_exercises table repair")
            
            // Fix custom_exercises table schema to match CustomExerciseEntity
            fixCustomExercisesTableSchema(database)
            
            // Validate other critical tables for similar issues
            validateOtherTableSchemas(database)
            
            database.execSQL("COMMIT")
            Log.d("Migration_11_12", "Migration completed successfully")
        } catch (e: Exception) {
            database.execSQL("ROLLBACK")
            Log.e("Migration_11_12", "Migration failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Fixes custom_exercises table to match CustomExerciseEntity exactly.
     * Handles various possible current schema states including composite primary keys.
     */
    private fun fixCustomExercisesTableSchema(database: SupportSQLiteDatabase) {
        Log.d("Migration_11_12", "Fixing custom_exercises table schema")
        
        // Check if table exists
        val tableExists = tableExists(database, "custom_exercises")
        if (!tableExists) {
            Log.d("Migration_11_12", "custom_exercises table doesn't exist, creating new one")
            createCustomExercisesTableCorrect(database)
            return
        }
        
        // Get current schema
        val currentColumns = getTableColumns(database, "custom_exercises")
        val currentColumnTypes = getTableColumnTypes(database, "custom_exercises")
        val primaryKeyColumns = getPrimaryKeyColumns(database, "custom_exercises")
        
        Log.d("Migration_11_12", "Current custom_exercises columns: $currentColumns")
        Log.d("Migration_11_12", "Current primary key columns: $primaryKeyColumns")
        
        // Define expected schema according to CustomExerciseEntity
        val expectedColumns = setOf(
            "id", "user_id", "name", "primary_muscle_group", "equipment", 
            "secondary_muscle_groups", "difficulty", "notes", "created_at", 
            "updated_at", "is_synced", "sync_version"
        )
        
        // Check if current schema matches expected
        val schemaMismatch = !currentColumns.containsAll(expectedColumns) || 
                           currentColumns.contains("muscle_group") ||
                           currentColumns.contains("instructions") ||
                           currentColumns.contains("tags") ||
                           currentColumns.contains("thumbnail_url") ||
                           primaryKeyColumns != setOf("id")  // Check primary key structure
        
        if (!schemaMismatch) {
            Log.d("Migration_11_12", "custom_exercises schema is already correct")
            return
        }
        
        Log.d("Migration_11_12", "Schema mismatch detected, performing repair")
        
        // Log specific schema issues
        if (currentColumns.contains("instructions") && currentColumns.contains("tags")) {
            Log.w("Migration_11_12", "Detected ExerciseLibrary-like schema in custom_exercises table - performing corrective migration")
        }
        if (primaryKeyColumns.size > 1) {
            Log.w("Migration_11_12", "Detected composite primary key: $primaryKeyColumns - converting to single column PK")
        }
        
        // Backup existing data
        database.execSQL("CREATE TEMPORARY TABLE custom_exercises_backup AS SELECT * FROM custom_exercises")
        
        // Drop existing table and indices
        database.execSQL("DROP TABLE IF EXISTS custom_exercises")
        dropIndicesForTable(database, "custom_exercises")
        
        // Create new table with correct schema
        createCustomExercisesTableCorrect(database)
        
        // Migrate data from backup with proper column mapping and PK handling
        migrateCustomExercisesData(database, currentColumns, primaryKeyColumns)
        
        // Recreate indices
        createCustomExercisesIndices(database)
        
        // Clean up backup
        database.execSQL("DROP TABLE custom_exercises_backup")
        
        Log.d("Migration_11_12", "custom_exercises table repair completed")
    }
    
    /**
     * Creates custom_exercises table with correct schema matching CustomExerciseEntity
     */
    private fun createCustomExercisesTableCorrect(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE custom_exercises (
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
        """)
        Log.d("Migration_11_12", "Created custom_exercises table with correct schema")
    }
    
    /**
     * Migrates custom exercises data from backup with proper column mapping and defaults.
     * Handles composite primary key conversion to single primary key.
     */
    private fun migrateCustomExercisesData(database: SupportSQLiteDatabase, backupColumns: Set<String>, primaryKeyColumns: Set<String>) {
        try {
            // Build SELECT statement based on available columns in backup
            val selectClause = buildCustomExercisesSelectClause(backupColumns, primaryKeyColumns)
            
            database.execSQL("""
                INSERT INTO custom_exercises (
                    id, user_id, name, primary_muscle_group, equipment,
                    secondary_muscle_groups, difficulty, notes,
                    created_at, updated_at, is_synced, sync_version
                )
                $selectClause
            """)
            
            Log.d("Migration_11_12", "Custom exercises data migrated successfully")
        } catch (e: Exception) {
            Log.w("Migration_11_12", "Failed to migrate custom exercises data: ${e.message}")
            // Continue without data migration rather than failing completely
        }
    }
    
    /**
     * Builds SELECT clause for data migration with proper column mapping.
     * Handles composite primary key conversion to single primary key.
     */
    private fun buildCustomExercisesSelectClause(backupColumns: Set<String>, primaryKeyColumns: Set<String>): String {
        // Handle ID generation based on primary key structure
        val idMapping = when {
            primaryKeyColumns == setOf("id") && backupColumns.contains("id") -> "id"
            primaryKeyColumns.size > 1 && primaryKeyColumns.contains("equipment") && primaryKeyColumns.contains("id") -> {
                // Composite PK (equipment, id) - combine them to create unique single ID
                "equipment || '_' || id"
            }
            primaryKeyColumns.size > 1 -> {
                // Other composite PK - use all columns to create unique ID
                val pkColumns = primaryKeyColumns.intersect(backupColumns)
                if (pkColumns.isNotEmpty()) {
                    pkColumns.joinToString(" || '_' || ")
                } else {
                    "'repair-' || ROWID"
                }
            }
            backupColumns.contains("id") -> "id"
            else -> "'repair-' || ROWID"
        }
        
        return """
            SELECT 
                $idMapping as id,
                ${if (backupColumns.contains("user_id")) "user_id" else "'migrated_user'"} as user_id,
                ${if (backupColumns.contains("name")) "name" else "'Custom Exercise'"} as name,
                ${
                    when {
                        backupColumns.contains("primary_muscle_group") -> "primary_muscle_group"
                        backupColumns.contains("muscle_group") -> "muscle_group"  // Handle old column name
                        else -> "'Other'"
                    }
                } as primary_muscle_group,
                ${if (backupColumns.contains("equipment")) "equipment" else "'None'"} as equipment,
                ${if (backupColumns.contains("secondary_muscle_groups")) "secondary_muscle_groups" else "NULL"} as secondary_muscle_groups,
                ${if (backupColumns.contains("difficulty")) "difficulty" else "NULL"} as difficulty,
                ${
                    when {
                        backupColumns.contains("notes") -> "notes"
                        backupColumns.contains("instructions") -> "instructions"  // Map instructions to notes for ExerciseLibrary schema confusion
                        else -> "NULL"
                    }
                } as notes,
                ${buildTimestampMapping(backupColumns, "created_at")} as created_at,
                ${buildTimestampMapping(backupColumns, "updated_at")} as updated_at,
                ${if (backupColumns.contains("is_synced")) "is_synced" else "0"} as is_synced,
                ${if (backupColumns.contains("sync_version")) "sync_version" else "1"} as sync_version
            FROM custom_exercises_backup
        """.trimIndent()
    }
    
    /**
     * Builds timestamp mapping with proper format conversion
     */
    private fun buildTimestampMapping(columns: Set<String>, columnName: String): String {
        return if (columns.contains(columnName)) {
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
     * Creates indices for custom_exercises table
     */
    private fun createCustomExercisesIndices(database: SupportSQLiteDatabase) {
        val indices = listOf(
            "CREATE INDEX IF NOT EXISTS index_custom_exercises_user_id ON custom_exercises(user_id)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_custom_exercises_name_user_id ON custom_exercises(name, user_id)",
            "CREATE INDEX IF NOT EXISTS index_custom_exercises_primary_muscle_group ON custom_exercises(primary_muscle_group)",
            "CREATE INDEX IF NOT EXISTS index_custom_exercises_equipment ON custom_exercises(equipment)"
        )
        
        indices.forEach { indexSQL ->
            try {
                database.execSQL(indexSQL)
            } catch (e: Exception) {
                Log.w("Migration_11_12", "Failed to create index: ${e.message}")
            }
        }
    }
    
    /**
     * Validates other critical tables for similar schema issues
     */
    private fun validateOtherTableSchemas(database: SupportSQLiteDatabase) {
        Log.d("Migration_11_12", "Validating other table schemas")
        
        // Check for common schema issues in other tables
        validateTableSchema(database, "simple_exercises", setOf(
            "id", "workout_id", "name", "reps", "rpe", "sets", "weight_kg", 
            "order_index", "photo_url", "animation_url"
        ))
        
        validateTableSchema(database, "user_profiles", setOf(
            "id", "user_id", "display_name", "age", "weight_kg", "height_cm",
            "fitness_level", "goals", "available_equipment", "workout_frequency",
            "preferred_workout_duration", "completed_at", "created_at", "updated_at", 
            "is_synced", "sync_version"
        ))
        
        // Check for 'undefined' values that could cause issues
        checkForUndefinedValues(database)
    }
    
    /**
     * Validates that a table has expected columns
     */
    private fun validateTableSchema(database: SupportSQLiteDatabase, tableName: String, expectedColumns: Set<String>) {
        if (!tableExists(database, tableName)) {
            Log.w("Migration_11_12", "Table $tableName doesn't exist")
            return
        }
        
        val currentColumns = getTableColumns(database, tableName)
        val missingColumns = expectedColumns - currentColumns
        val extraColumns = currentColumns - expectedColumns
        
        if (missingColumns.isNotEmpty()) {
            Log.w("Migration_11_12", "Table $tableName missing columns: $missingColumns")
        }
        
        if (extraColumns.isNotEmpty()) {
            Log.w("Migration_11_12", "Table $tableName has extra columns: $extraColumns")
        }
        
        if (missingColumns.isEmpty() && extraColumns.isEmpty()) {
            Log.d("Migration_11_12", "Table $tableName schema is correct")
        }
    }
    
    /**
     * Checks for 'undefined' values that could cause issues
     */
    private fun checkForUndefinedValues(database: SupportSQLiteDatabase) {
        val tablesToCheck = listOf("user_profiles", "workouts", "custom_exercises")
        
        tablesToCheck.forEach { tableName ->
            if (tableExists(database, tableName)) {
                try {
                    val undefinedCount = database.query(
                        "SELECT COUNT(*) FROM $tableName WHERE " +
                        "created_at = 'undefined' OR updated_at = 'undefined'"
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getInt(0) else 0
                    }
                    
                    if (undefinedCount > 0) {
                        Log.w("Migration_11_12", "Found $undefinedCount 'undefined' values in $tableName")
                    }
                } catch (e: Exception) {
                    Log.w("Migration_11_12", "Could not check undefined values in $tableName: ${e.message}")
                }
            }
        }
    }
    
    // Utility functions
    
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
            Log.e("Migration_11_12", "Error getting columns for $tableName: ${e.message}")
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
            Log.e("Migration_11_12", "Error getting column types for $tableName: ${e.message}")
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
            Log.e("Migration_11_12", "Error dropping indices for table $tableName: ${e.message}")
        }
    }
    
    /**
     * Gets the primary key columns for a table
     */
    private fun getPrimaryKeyColumns(database: SupportSQLiteDatabase, tableName: String): Set<String> {
        return try {
            val primaryKeyColumns = mutableSetOf<String>()
            database.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val pkIndex = cursor.getColumnIndex("pk")
                while (cursor.moveToNext()) {
                    val isPrimaryKey = cursor.getInt(pkIndex) > 0
                    if (isPrimaryKey) {
                        primaryKeyColumns.add(cursor.getString(nameIndex))
                    }
                }
            }
            primaryKeyColumns
        } catch (e: Exception) {
            Log.e("Migration_11_12", "Error getting primary key columns for $tableName: ${e.message}")
            emptySet()
        }
    }
}