package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Room migration from version 10 to 11.
 * Fixes SimpleExerciseEntity schema mismatch with Migration_5_6.
 * Adds missing columns and corrects data types.
 * Fixes user_profiles table 'undefined' default values issue.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION")
        try {
            Log.d("Migration_10_11", "Starting migration from version 10 to 11")
            
            // Fix SimpleExerciseEntity schema mismatch
            fixSimpleExercisesTable(database)
            
            // Fix WorkoutEntity schema mismatch
            fixWorkoutsTable(database)
            
            // Fix UserProfileEntity schema mismatch and 'undefined' values
            fixUserProfilesTable(database)
            
            database.execSQL("COMMIT")
            Log.d("Migration_10_11", "Migration completed successfully")
        } catch (e: Exception) {
            database.execSQL("ROLLBACK")
            Log.e("Migration_10_11", "Migration failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Fixes simple_exercises table schema to match SimpleExerciseEntity.
     * 
     * Issues from Migration_5_6:
     * - Missing: photo_url, animation_url columns
     * - Missing: created_at column (may not exist in some versions)
     * - Type mismatch: rpe as INTEGER instead of REAL (for Double?)
     * - Entity expects created_at column (which migration creates)
     */
    private fun fixSimpleExercisesTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_10_11", "Fixing simple_exercises table schema")
        
        // Check if table exists first
        val tableExists = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='simple_exercises'")
            .use { cursor -> cursor.moveToFirst() }
        
        if (!tableExists) {
            Log.w("Migration_10_11", "simple_exercises table doesn't exist, skipping fix")
            return
        }
        
        // Step 1: Add missing columns that SimpleExerciseEntity expects
        try {
            // Add photo_url column if it doesn't exist
            if (!columnExists(database, "simple_exercises", "photo_url")) {
                database.execSQL("ALTER TABLE simple_exercises ADD COLUMN photo_url TEXT")
                Log.d("Migration_10_11", "Added photo_url column to simple_exercises")
            }
            
            // Add animation_url column if it doesn't exist
            if (!columnExists(database, "simple_exercises", "animation_url")) {
                database.execSQL("ALTER TABLE simple_exercises ADD COLUMN animation_url TEXT")
                Log.d("Migration_10_11", "Added animation_url column to simple_exercises")
            }
            
            // Add created_at column if it doesn't exist
            if (!columnExists(database, "simple_exercises", "created_at")) {
                database.execSQL("ALTER TABLE simple_exercises ADD COLUMN created_at TEXT DEFAULT CURRENT_TIMESTAMP")
                Log.d("Migration_10_11", "Added created_at column to simple_exercises")
                
                // Backfill existing rows since ALTER TABLE defaults don't apply retroactively
                database.execSQL("UPDATE simple_exercises SET created_at = datetime('now') WHERE created_at IS NULL")
                Log.d("Migration_10_11", "Backfilled created_at values for existing rows")
            }
        } catch (e: Exception) {
            Log.e("Migration_10_11", "Failed to add missing columns: ${e.message}", e)
            throw e
        }
        
        // Step 2: Fix data type for rpe column (INTEGER -> REAL for Double?)
        try {
            // This requires table recreation since SQLite doesn't support ALTER COLUMN TYPE
            database.execSQL(
                """
                CREATE TABLE simple_exercises_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    workout_id TEXT NOT NULL,
                    name TEXT NOT NULL CHECK(length(name) >= 2 AND length(name) <= 100),
                    reps INTEGER NOT NULL CHECK(reps >= 1 AND reps <= 999),
                    rpe REAL CHECK(rpe IS NULL OR (rpe >= 1.0 AND rpe <= 10.0)),
                    sets INTEGER NOT NULL CHECK(sets >= 1 AND sets <= 50),
                    weight_kg REAL NOT NULL CHECK(weight_kg >= 0 AND weight_kg <= 999.9),
                    order_index INTEGER NOT NULL CHECK(order_index >= 0),
                    photo_url TEXT,
                    animation_url TEXT,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (workout_id) REFERENCES simple_workouts(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            
            // Check if created_at column exists in the original table
            val hasCreatedAt = columnExists(database, "simple_exercises", "created_at")
            
            // Copy data with type conversion for rpe column and handle missing created_at
            val insertSQL = if (hasCreatedAt) {
                """
                INSERT INTO simple_exercises_new 
                (id, workout_id, name, reps, rpe, sets, weight_kg, order_index, photo_url, animation_url, created_at)
                SELECT 
                    id, 
                    workout_id, 
                    name, 
                    reps, 
                    CAST(rpe AS REAL) as rpe,  -- Convert INTEGER to REAL
                    sets, 
                    weight_kg, 
                    order_index,
                    COALESCE(photo_url, NULL) as photo_url,      -- Handle potentially missing column
                    COALESCE(animation_url, NULL) as animation_url, -- Handle potentially missing column
                    created_at
                FROM simple_exercises
                """.trimIndent()
            } else {
                """
                INSERT INTO simple_exercises_new 
                (id, workout_id, name, reps, rpe, sets, weight_kg, order_index, photo_url, animation_url, created_at)
                SELECT 
                    id, 
                    workout_id, 
                    name, 
                    reps, 
                    CAST(rpe AS REAL) as rpe,  -- Convert INTEGER to REAL
                    sets, 
                    weight_kg, 
                    order_index,
                                         COALESCE(photo_url, NULL) as photo_url,      -- Handle potentially missing column
                     COALESCE(animation_url, NULL) as animation_url, -- Handle potentially missing column
                     CURRENT_TIMESTAMP as created_at               -- Default value for missing created_at
                FROM simple_exercises
                """.trimIndent()
            }
            
            database.execSQL(insertSQL)
            
            // Drop old table and rename new one
            database.execSQL("DROP TABLE simple_exercises")
            database.execSQL("ALTER TABLE simple_exercises_new RENAME TO simple_exercises")
            
            // Recreate indices to match SimpleExerciseEntity
            database.execSQL("CREATE INDEX IF NOT EXISTS index_simple_exercises_workout_id ON simple_exercises(workout_id)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_simple_exercises_workout_id_order_index ON simple_exercises(workout_id, order_index)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_simple_exercises_name ON simple_exercises(name)")
            
            Log.d("Migration_10_11", "Successfully recreated simple_exercises table with correct schema")
            
        } catch (e: Exception) {
            Log.e("Migration_10_11", "Failed to fix rpe column type: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Fixes workouts table schema to match WorkoutEntity.
     * 
     * Issues:
     * - created_at column may have 'undefined' values instead of proper timestamps
     * - created_at column should have CURRENT_TIMESTAMP default
     */
    private fun fixWorkoutsTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_10_11", "Fixing workouts table schema")
        
        // Check if table exists first
        val tableExists = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='workouts'")
            .use { cursor -> cursor.moveToFirst() }
        
        if (!tableExists) {
            Log.w("Migration_10_11", "workouts table doesn't exist, skipping fix")
            return
        }
        
        try {
            // Step 1: Fix any 'undefined' values in created_at column
            val undefinedCount = database.query("SELECT COUNT(*) FROM workouts WHERE created_at = 'undefined'")
                .use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            
            if (undefinedCount > 0) {
                Log.d("Migration_10_11", "Found $undefinedCount workouts with 'undefined' created_at, fixing...")
                database.execSQL(
                    """
                    UPDATE workouts 
                    SET created_at = datetime('now') 
                    WHERE created_at = 'undefined'
                    """.trimIndent()
                )
                Log.d("Migration_10_11", "Fixed $undefinedCount undefined created_at values")
            }
            
            // Step 2: Recreate table with proper DEFAULT CURRENT_TIMESTAMP
            // This requires table recreation since SQLite doesn't support modifying column defaults
            database.execSQL(
                """
                CREATE TABLE workouts_new (
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
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT NOT NULL,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    sync_version INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            
            // Copy all data to new table
            database.execSQL(
                """
                INSERT INTO workouts_new 
                (id, user_id, name, date, exercises_json, status, start_time, end_time, notes, template_id, created_at, updated_at, is_synced, sync_version)
                SELECT 
                    id, user_id, name, date, exercises_json, status, start_time, end_time, notes, template_id,
                    CASE 
                        WHEN created_at = 'undefined' THEN datetime('now')
                        ELSE created_at 
                    END as created_at,
                    updated_at, is_synced, sync_version
                FROM workouts
                """.trimIndent()
            )
            
            // Drop old table and rename new one
            database.execSQL("DROP TABLE workouts")
            database.execSQL("ALTER TABLE workouts_new RENAME TO workouts")
            
            Log.d("Migration_10_11", "Successfully recreated workouts table with correct schema")
            
        } catch (e: Exception) {
            Log.e("Migration_10_11", "Failed to fix workouts table: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Fixes user_profiles table schema to match UserProfileEntity.
     * 
     * Issues:
     * - Columns may have 'undefined' values instead of proper defaults
     * - created_at, updated_at columns should have proper CURRENT_TIMESTAMP defaults
     * - Ensures schema matches UserProfileEntity definition
     */
    private fun fixUserProfilesTable(database: SupportSQLiteDatabase) {
        Log.d("Migration_10_11", "Fixing user_profiles table schema")
        
        // Check if table exists first
        val tableExists = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='user_profiles'")
            .use { cursor -> cursor.moveToFirst() }
        
        if (!tableExists) {
            Log.w("Migration_10_11", "user_profiles table doesn't exist, skipping fix")
            return
        }
        
        try {
            // Step 1: Fix any 'undefined' values in critical columns
            val undefinedColumns = listOf(
                "created_at", "updated_at", "goals", "height_cm", "preferred_workout_duration", 
                "weight_kg", "workout_frequency", "user_id", "display_name", "fitness_level", 
                "available_equipment"
            )
            
            var totalUndefinedCount = 0
            
            undefinedColumns.forEach { columnName ->
                // Check if column exists before trying to fix it
                if (columnExists(database, "user_profiles", columnName)) {
                    val undefinedCount = database.query("SELECT COUNT(*) FROM user_profiles WHERE $columnName = 'undefined'")
                        .use { cursor ->
                            if (cursor.moveToFirst()) cursor.getInt(0) else 0
                        }
                    
                    if (undefinedCount > 0) {
                        totalUndefinedCount += undefinedCount
                        Log.d("Migration_10_11", "Found $undefinedCount records with 'undefined' $columnName, fixing...")
                        
                        // Fix based on column type and semantics
                        when (columnName) {
                            "created_at", "updated_at" -> {
                                database.execSQL(
                                    """
                                    UPDATE user_profiles 
                                    SET $columnName = datetime('now') 
                                    WHERE $columnName = 'undefined'
                                    """.trimIndent()
                                )
                            }
                            "user_id" -> {
                                database.execSQL(
                                    """
                                    UPDATE user_profiles 
                                    SET user_id = 'migrated_user_' || id 
                                    WHERE user_id = 'undefined'
                                    """.trimIndent()
                                )
                            }
                            "display_name" -> {
                                database.execSQL(
                                    """
                                    UPDATE user_profiles 
                                    SET display_name = 'User' 
                                    WHERE display_name = 'undefined'
                                    """.trimIndent()
                                )
                            }
                            "goals", "fitness_level", "available_equipment" -> {
                                // Set nullable text fields to null instead of 'undefined'
                                database.execSQL(
                                    """
                                    UPDATE user_profiles 
                                    SET $columnName = NULL 
                                    WHERE $columnName = 'undefined'
                                    """.trimIndent()
                                )
                            }
                            "height_cm", "weight_kg", "workout_frequency", "preferred_workout_duration" -> {
                                // Set nullable numeric fields to null instead of 'undefined'
                                database.execSQL(
                                    """
                                    UPDATE user_profiles 
                                    SET $columnName = NULL 
                                    WHERE $columnName = 'undefined'
                                    """.trimIndent()
                                )
                            }
                        }
                        
                        Log.d("Migration_10_11", "Fixed $undefinedCount undefined $columnName values")
                    }
                }
            }
            
            if (totalUndefinedCount > 0) {
                Log.d("Migration_10_11", "Fixed total of $totalUndefinedCount undefined values across all columns")
            }
            
            // Step 2: Recreate table with proper defaults to prevent future issues
            database.execSQL(
                """
                CREATE TABLE user_profiles_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    user_id TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    age INTEGER,
                    weight_kg REAL,
                    height_cm REAL,
                    fitness_level TEXT,
                    goals TEXT,
                    available_equipment TEXT,
                    workout_frequency INTEGER,
                    preferred_workout_duration INTEGER,
                    completed_at TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    sync_version INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            
            // Copy all data to new table, ensuring no 'undefined' values
            database.execSQL(
                """
                INSERT INTO user_profiles_new 
                (id, user_id, display_name, age, weight_kg, height_cm, fitness_level, goals, 
                 available_equipment, workout_frequency, preferred_workout_duration, completed_at,
                 created_at, updated_at, is_synced, sync_version)
                SELECT 
                    id,
                    CASE 
                        WHEN user_id = 'undefined' THEN 'migrated_user_' || id
                        ELSE user_id 
                    END as user_id,
                    CASE 
                        WHEN display_name = 'undefined' THEN 'User'
                        ELSE display_name 
                    END as display_name,
                    CASE 
                        WHEN age = 'undefined' THEN NULL
                        ELSE age 
                    END as age,
                    CASE 
                        WHEN weight_kg = 'undefined' THEN NULL
                        ELSE weight_kg 
                    END as weight_kg,
                    CASE 
                        WHEN height_cm = 'undefined' THEN NULL
                        ELSE height_cm 
                    END as height_cm,
                    CASE 
                        WHEN fitness_level = 'undefined' THEN NULL
                        ELSE fitness_level 
                    END as fitness_level,
                    CASE 
                        WHEN goals = 'undefined' THEN NULL
                        ELSE goals 
                    END as goals,
                    CASE 
                        WHEN available_equipment = 'undefined' THEN NULL
                        ELSE available_equipment 
                    END as available_equipment,
                    CASE 
                        WHEN workout_frequency = 'undefined' THEN NULL
                        ELSE workout_frequency 
                    END as workout_frequency,
                    CASE 
                        WHEN preferred_workout_duration = 'undefined' THEN NULL
                        ELSE preferred_workout_duration 
                    END as preferred_workout_duration,
                    NULL as completed_at,  -- Add missing completed_at column for onboarding tracking
                    CASE 
                        WHEN created_at = 'undefined' THEN datetime('now')
                        ELSE created_at 
                    END as created_at,
                    CASE 
                        WHEN updated_at = 'undefined' THEN datetime('now')
                        ELSE updated_at 
                    END as updated_at,
                    COALESCE(is_synced, 0) as is_synced,
                    COALESCE(sync_version, 1) as sync_version
                FROM user_profiles
                """.trimIndent()
            )
            
            // Drop old table and rename new one
            database.execSQL("DROP TABLE user_profiles")
            database.execSQL("ALTER TABLE user_profiles_new RENAME TO user_profiles")
            
            Log.d("Migration_10_11", "Successfully recreated user_profiles table with correct schema")
            
        } catch (e: Exception) {
            Log.e("Migration_10_11", "Failed to fix user_profiles table: ${e.message}", e)
            throw e
        }
    }

    /**
     * Checks if a column exists in a table
     */
    private fun columnExists(database: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        return try {
            database.query("PRAGMA table_info($tableName)").use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == columnName) {
                        return true
                    }
                }
                false
            }
        } catch (e: Exception) {
            Log.e("Migration_10_11", "Failed to check column existence: ${e.message}", e)
            false
        }
    }
} 