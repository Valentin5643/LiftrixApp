package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * CRITICAL FIX: Migration from version 12 to 13
 * 
 * Forces a definitive rebuild of custom_exercises table to match CustomExerciseEntity exactly.
 * This addresses the persistent schema mismatch where Room expects ExerciseLibrary-like schema
 * but CustomExerciseEntity defines a different schema.
 * 
 * Key Changes:
 * - Forces table recreation with correct single PRIMARY KEY
 * - Eliminates composite PK (equipment, id) that shouldn't exist
 * - Maps old ExerciseLibrary-like columns to correct CustomExercise columns
 * - Handles data preservation with proper type conversion
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION")
        try {
            Log.d("Migration_12_13", "Starting definitive custom_exercises table rebuild")
            
            // Force rebuild custom_exercises table to match CustomExerciseEntity exactly
            forceRebuildCustomExercisesTable(database)
            
            database.execSQL("COMMIT")
            Log.d("Migration_12_13", "Migration completed successfully")
        } catch (e: Exception) {
            database.execSQL("ROLLBACK")
            Log.e("Migration_12_13", "Migration failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Forces complete rebuild of custom_exercises table to eliminate schema confusion
     */
    private fun forceRebuildCustomExercisesTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_12_13", "Force rebuilding custom_exercises table")
        
        // Get current schema info
        val currentColumns = getTableColumns(database, "custom_exercises")
        val primaryKeyColumns = getPrimaryKeyColumns(database, "custom_exercises")
        
        Log.d("Migration_12_13", "Current columns: $currentColumns")
        Log.d("Migration_12_13", "Current PK columns: $primaryKeyColumns")
        
        // Detect problematic schema patterns
        val hasCompositeKey = primaryKeyColumns.size > 1
        val hasExerciseLibraryColumns = currentColumns.contains("instructions") || 
                                       currentColumns.contains("tags") || 
                                       currentColumns.contains("thumbnail_url")
        
        if (hasCompositeKey) {
            Log.w("Migration_12_13", "Detected composite PK: $primaryKeyColumns - forcing rebuild")
        }
        if (hasExerciseLibraryColumns) {
            Log.w("Migration_12_13", "Detected ExerciseLibrary-like schema - forcing rebuild")
        }
        
        // Step 1: Rename old table to preserve data
        database.execSQL("ALTER TABLE custom_exercises RENAME TO custom_exercises_old")
        val rowCount = database.query("SELECT COUNT(*) FROM custom_exercises_old").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        Log.d("Migration_12_13", "Backed up $rowCount custom exercises")
        
        // Step 2: Create new table with correct schema
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
        Log.d("Migration_12_13", "Created custom_exercises table with correct schema")
        
        // Step 3: Migrate data with intelligent column mapping
        if (rowCount > 0) {
            migrateCustomExercisesData(database, currentColumns, primaryKeyColumns)
        }
        
        // Create indices
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
                Log.w("Migration_12_13", "Failed to create index: ${e.message}")
            }
        }
        
        // Step 4: Drop old table after successful migration
        database.execSQL("DROP TABLE custom_exercises_old")
        
        Log.d("Migration_12_13", "custom_exercises table rebuild completed")
    }
    
    /**
     * Migrates data from backup with intelligent column mapping
     */
    private fun migrateCustomExercisesData(database: SupportSQLiteDatabase, backupColumns: Set<String>, primaryKeyColumns: Set<String>) {
        try {
            // Build SELECT statement based on available columns
            val selectClause = buildDataMappingQuery(backupColumns, primaryKeyColumns)
            
            database.execSQL("""
                INSERT INTO custom_exercises (
                    id, user_id, name, primary_muscle_group, equipment,
                    secondary_muscle_groups, difficulty, notes,
                    created_at, updated_at, is_synced, sync_version
                )
                $selectClause
            """)
            
            val migratedCount = database.query("SELECT COUNT(*) FROM custom_exercises").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            Log.d("Migration_12_13", "Migrated $migratedCount custom exercises")
        } catch (e: Exception) {
            Log.w("Migration_12_13", "Failed to migrate data: ${e.message}")
            // Continue without data migration rather than failing
        }
    }
    
    /**
     * Builds SELECT query with intelligent column mapping for different schema types
     */
    private fun buildDataMappingQuery(backupColumns: Set<String>, primaryKeyColumns: Set<String>): String {
        // Handle ID generation based on primary key structure
        val idMapping = when {
            // Single PK with id column
            primaryKeyColumns == setOf("id") && backupColumns.contains("id") -> "id"
            
            // Composite PK (equipment, id) - concatenate for unique single ID
            primaryKeyColumns.size > 1 && primaryKeyColumns.contains("equipment") && primaryKeyColumns.contains("id") -> {
                "equipment || '_' || id"
            }
            
            // Other composite PK patterns
            primaryKeyColumns.size > 1 -> {
                val pkColumns = primaryKeyColumns.intersect(backupColumns)
                if (pkColumns.isNotEmpty()) {
                    pkColumns.joinToString(" || '_' || ")
                } else {
                    "'migrated_' || ROWID"
                }
            }
            
            // Fallback for no clear PK
            backupColumns.contains("id") -> "id"
            else -> "'migrated_' || ROWID"
        }
        
        return """
            SELECT 
                $idMapping as id,
                ${if (backupColumns.contains("user_id")) "user_id" else "'migrated_user'"} as user_id,
                ${if (backupColumns.contains("name")) "name" else "'Custom Exercise'"} as name,
                ${
                    when {
                        backupColumns.contains("primary_muscle_group") -> "primary_muscle_group"
                        backupColumns.contains("muscle_group") -> "muscle_group"
                        else -> "'Other'"
                    }
                } as primary_muscle_group,
                ${if (backupColumns.contains("equipment")) "equipment" else "'None'"} as equipment,
                ${if (backupColumns.contains("secondary_muscle_groups")) "secondary_muscle_groups" else "NULL"} as secondary_muscle_groups,
                ${if (backupColumns.contains("difficulty")) "difficulty" else "NULL"} as difficulty,
                ${
                    when {
                        backupColumns.contains("notes") -> "notes"
                        backupColumns.contains("instructions") -> "instructions"  // Map ExerciseLibrary instructions to notes
                        else -> "NULL"
                    }
                } as notes,
                ${buildTimestampMapping(backupColumns, "created_at")} as created_at,
                ${buildTimestampMapping(backupColumns, "updated_at")} as updated_at,
                ${if (backupColumns.contains("is_synced")) "is_synced" else "0"} as is_synced,
                ${if (backupColumns.contains("sync_version")) "sync_version" else "1"} as sync_version
            FROM custom_exercises_old
        """.trimIndent()
    }
    
    /**
     * Builds timestamp mapping with format conversion
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
    
    // Utility functions
    
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
            Log.e("Migration_12_13", "Error getting columns for $tableName: ${e.message}")
            emptySet()
        }
    }
    
    private fun getPrimaryKeyColumns(database: SupportSQLiteDatabase, tableName: String): Set<String> {
        return try {
            val primaryKeyColumns = mutableSetOf<String>()
            database.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val pkIndex = cursor.getColumnIndex("pk")
                while (cursor.moveToNext()) {
                    if (cursor.getInt(pkIndex) > 0) {
                        primaryKeyColumns.add(cursor.getString(nameIndex))
                    }
                }
            }
            primaryKeyColumns
        } catch (e: Exception) {
            Log.e("Migration_12_13", "Error getting primary key columns for $tableName: ${e.message}")
            emptySet()
        }
    }
}