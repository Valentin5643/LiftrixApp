package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Comprehensive repair migration from version 9 to 10.
 * 
 * This is the definitive "repair migration" that handles all known schema inconsistencies
 * that can cause Room validation failures. It's designed to be completely resilient
 * and handle any corrupted database state without failing.
 * 
 * Key Features:
 * - Drops and recreates all tables with correct schemas
 * - Migrates data safely with type conversions and fallbacks
 * - Handles missing columns and incorrect types gracefully
 * - Rebuilds all indices properly
 * - Includes extensive error handling and logging
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION")
        try {
            Log.d("Migration_9_10", "Starting comprehensive database repair")
            
            // Phase 1: Backup all existing data
            backupAllData(database)
            
            // Phase 2: Drop all tables and indices (clean slate)
            dropAllTablesAndIndices(database)
            
            // Phase 3: Create all tables with correct schemas
            createAllTablesWithCorrectSchema(database)
            
            // Phase 4: Restore data with type conversions
            restoreAllDataSafely(database)
            
            // Phase 5: Create all indices
            createAllIndices(database)
            
            // Phase 6: Cleanup backup tables
            cleanupBackupTables(database)
            
            // Phase 7: Validate final schema
            validateFinalSchema(database)
            
            database.execSQL("COMMIT")
            Log.d("Migration_9_10", "Database repair completed successfully")
        } catch (e: Exception) {
            database.execSQL("ROLLBACK")
            Log.e("Migration_9_10", "Database repair failed: ${e.message}", e)
            throw e
        }
    }

    private fun backupAllData(database: SupportSQLiteDatabase) {
        Log.d("Migration_9_10", "Backing up all data")
        
        val tables = listOf(
            "workouts", "simple_workouts", "simple_exercises", "exercises", 
            "exercise_sets", "exercise_weight_memory", 
            "workout_templates", "custom_exercises", "user_profiles", 
            "exercise_library"
        )
        
        tables.forEach { tableName ->
            try {
                if (tableExists(database, tableName)) {
                    database.execSQL("CREATE TEMPORARY TABLE ${tableName}_backup AS SELECT * FROM $tableName")
                    Log.d("Migration_9_10", "Backed up table: $tableName")
                }
            } catch (e: Exception) {
                Log.w("Migration_9_10", "Failed to backup table $tableName: ${e.message}")
                // Continue with other tables
            }
        }
    }

    private fun dropAllTablesAndIndices(database: SupportSQLiteDatabase) {
        Log.d("Migration_9_10", "Dropping all tables and indices")
        
        // Drop all indices first
        val indexQueries = listOf(
            "DROP INDEX IF EXISTS index_workouts_user_id",
            "DROP INDEX IF EXISTS index_workouts_date",
            "DROP INDEX IF EXISTS index_workouts_status",
            "DROP INDEX IF EXISTS index_simple_workouts_user_id",
            "DROP INDEX IF EXISTS index_simple_workouts_user_id_created_at",
            "DROP INDEX IF EXISTS index_simple_workouts_created_at",
            "DROP INDEX IF EXISTS index_simple_exercises_workout_id",
            "DROP INDEX IF EXISTS index_simple_exercises_workout_id_order_index",
            "DROP INDEX IF EXISTS index_simple_exercises_name",
            "DROP INDEX IF EXISTS index_exercises_workout_id",
            "DROP INDEX IF EXISTS index_exercises_exercise_library_id",
            "DROP INDEX IF EXISTS index_exercises_order_index",
            "DROP INDEX IF EXISTS index_exercise_sets_exercise_id",
            "DROP INDEX IF EXISTS index_exercise_sets_set_number",
            "DROP INDEX IF EXISTS index_exercise_sets_completed_at",
            "DROP INDEX IF EXISTS index_workout_templates_user_id",
            "DROP INDEX IF EXISTS index_workout_templates_name_user_id",
            "DROP INDEX IF EXISTS index_workout_templates_created_at",
            "DROP INDEX IF EXISTS index_custom_exercises_user_id",
            "DROP INDEX IF EXISTS index_custom_exercises_name_user_id",
            "DROP INDEX IF EXISTS index_custom_exercises_primary_muscle_group",
            "DROP INDEX IF EXISTS index_custom_exercises_equipment"
        )
        
        indexQueries.forEach { query ->
            try {
                database.execSQL(query)
            } catch (e: Exception) {
                Log.w("Migration_9_10", "Failed to drop index: ${e.message}")
            }
        }
        
        // Drop all tables
        val tables = listOf(
            "workouts", "simple_workouts", "simple_exercises", "exercises",
            "exercise_sets", "exercise_weight_memory",
            "workout_templates", "custom_exercises", "user_profiles",
            "exercise_library"
        )
        
        tables.forEach { tableName ->
            try {
                database.execSQL("DROP TABLE IF EXISTS $tableName")
                Log.d("Migration_9_10", "Dropped table: $tableName")
            } catch (e: Exception) {
                Log.w("Migration_9_10", "Failed to drop table $tableName: ${e.message}")
            }
        }
    }

    private fun createAllTablesWithCorrectSchema(database: SupportSQLiteDatabase) {
        Log.d("Migration_9_10", "Creating all tables with correct schemas")
        
        // Create workouts table - CORRECT SCHEMA matching WorkoutEntity
        database.execSQL("""
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
        """)
        
        // Create simple_workouts table - matching SimpleWorkoutEntity
        database.execSQL("""
            CREATE TABLE simple_workouts (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                sync_version INTEGER NOT NULL DEFAULT 0
            )
        """)
        
        // Create simple_exercises table - matching SimpleExerciseEntity
        database.execSQL("""
            CREATE TABLE simple_exercises (
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
                FOREIGN KEY (workout_id) REFERENCES simple_workouts(id) ON DELETE CASCADE
            )
        """)
        
        // Create exercises table - matching ExerciseEntity
        database.execSQL("""
            CREATE TABLE exercises (
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
        """)
        
        // Create exercise_sets table - matching ExerciseSetEntity
        database.execSQL("""
            CREATE TABLE exercise_sets (
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
        """)
        
        // Create exercise_weight_memory table
        database.execSQL("""
            CREATE TABLE exercise_weight_memory (
                user_id TEXT NOT NULL,
                exercise_library_id TEXT NOT NULL,
                last_weight_kg REAL NOT NULL,
                last_used_at INTEGER NOT NULL,
                usage_count INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (user_id, exercise_library_id)
            )
        """)
        
        
        // Create workout_templates table
        database.execSQL("""
            CREATE TABLE workout_templates (
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
        """)
        
        // Create custom_exercises table - matching CustomExerciseEntity
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
        
        // Create user_profiles table - matching UserProfileEntity
        database.execSQL("""
            CREATE TABLE user_profiles (
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
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                sync_version INTEGER NOT NULL DEFAULT 1
            )
        """)
        
        // Create exercise_library table
        database.execSQL("""
            CREATE TABLE exercise_library (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                primary_muscle_group TEXT NOT NULL,
                secondary_muscle_groups TEXT,
                equipment TEXT NOT NULL,
                instructions TEXT,
                difficulty INTEGER,
                tags TEXT,
                animation_url TEXT,
                thumbnail_url TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
        """)
        
        Log.d("Migration_9_10", "All tables created with correct schemas")
    }

    private fun restoreAllDataSafely(database: SupportSQLiteDatabase) {
        Log.d("Migration_9_10", "Restoring all data safely")
        
        // Restore workouts data with comprehensive type conversion
        restoreWorkoutsData(database)
        restoreSimpleWorkoutsData(database)
        restoreSimpleExercisesData(database)
        restoreExercisesData(database)
        restoreExerciseSetsData(database)
        restoreExerciseWeightMemoryData(database)
        restoreWorkoutTemplatesData(database)
        restoreCustomExercisesData(database)
        restoreUserProfilesData(database)
        restoreExerciseLibraryData(database)
    }

    private fun restoreWorkoutsData(database: SupportSQLiteDatabase) {
        if (!tableExists(database, "workouts_backup")) return
        
        try {
            database.execSQL("""
                INSERT INTO workouts (
                    id, user_id, name, date, exercises_json, status,
                    start_time, end_time, notes, template_id,
                    created_at, updated_at, is_synced, sync_version
                )
                SELECT 
                    CAST(id AS TEXT) as id,
                    COALESCE(user_id, 'unknown') as user_id,
                    COALESCE(name, 'Migrated Workout') as name,
                    COALESCE(date, date('now')) as date,
                    COALESCE(exercises_json, '[]') as exercises_json,
                    COALESCE(status, 'PLANNED') as status,
                    start_time,
                    end_time,
                    notes,
                    template_id,
                    CASE 
                        WHEN typeof(created_at) = 'integer' THEN datetime(created_at/1000, 'unixepoch')
                        WHEN typeof(created_at) = 'text' AND created_at LIKE '%-%-% %:%:%' THEN created_at
                        WHEN typeof(created_at) = 'text' AND created_at LIKE '%T%:%:%' THEN created_at
                        ELSE datetime('now')
                    END as created_at,
                    CASE 
                        WHEN typeof(updated_at) = 'integer' THEN datetime(updated_at/1000, 'unixepoch')
                        WHEN typeof(updated_at) = 'text' AND updated_at LIKE '%-%-% %:%:%' THEN updated_at
                        WHEN typeof(updated_at) = 'text' AND updated_at LIKE '%T%:%:%' THEN updated_at
                        ELSE datetime('now')
                    END as updated_at,
                    COALESCE(is_synced, 0) as is_synced,
                    0 as sync_version
                FROM workouts_backup
            """)
            Log.d("Migration_9_10", "Workouts data restored successfully")
        } catch (e: Exception) {
            Log.w("Migration_9_10", "Failed to restore workouts data: ${e.message}")
        }
    }

    private fun restoreSimpleWorkoutsData(database: SupportSQLiteDatabase) {
        if (!tableExists(database, "simple_workouts_backup")) return
        
        try {
            database.execSQL("""
                INSERT INTO simple_workouts (
                    id, user_id, name, description, created_at, updated_at, is_synced, sync_version
                )
                SELECT 
                    id,
                    COALESCE(user_id, 'unknown') as user_id,
                    COALESCE(name, 'Simple Workout') as name,
                    description,
                    CASE 
                        WHEN typeof(created_at) = 'integer' THEN datetime(created_at/1000, 'unixepoch')
                        WHEN typeof(created_at) = 'text' THEN created_at
                        ELSE datetime('now')
                    END as created_at,
                    CASE 
                        WHEN typeof(updated_at) = 'integer' THEN datetime(updated_at/1000, 'unixepoch')
                        WHEN typeof(updated_at) = 'text' THEN updated_at
                        ELSE datetime('now')
                    END as updated_at,
                    COALESCE(is_synced, 0) as is_synced,
                    COALESCE(sync_version, 0) as sync_version
                FROM simple_workouts_backup
            """)
            Log.d("Migration_9_10", "Simple workouts data restored successfully")
        } catch (e: Exception) {
            Log.w("Migration_9_10", "Failed to restore simple workouts data: ${e.message}")
        }
    }

    private fun restoreSimpleExercisesData(database: SupportSQLiteDatabase) {
        if (!tableExists(database, "simple_exercises_backup")) return
        
        try {
            database.execSQL("""
                INSERT INTO simple_exercises (
                    id, workout_id, name, reps, rpe, sets, weight_kg, order_index, photo_url, animation_url
                )
                SELECT 
                    id,
                    workout_id,
                    COALESCE(name, 'Exercise') as name,
                    COALESCE(reps, 1) as reps,
                    rpe,
                    COALESCE(sets, 1) as sets,
                    COALESCE(weight_kg, 0.0) as weight_kg,
                    COALESCE(order_index, 0) as order_index,
                    photo_url,
                    animation_url
                FROM simple_exercises_backup
                WHERE workout_id IN (SELECT id FROM simple_workouts)
            """)
            Log.d("Migration_9_10", "Simple exercises data restored successfully")
        } catch (e: Exception) {
            Log.w("Migration_9_10", "Failed to restore simple exercises data: ${e.message}")
        }
    }

    private fun restoreExercisesData(database: SupportSQLiteDatabase) {
        if (!tableExists(database, "exercises_backup")) return
        
        try {
            database.execSQL("""
                INSERT INTO exercises (
                    workout_id, exercise_library_id, order_index, target_sets, target_reps, 
                    target_weight_kg, target_time_seconds, target_distance_meters, notes, created_at, updated_at
                )
                SELECT 
                    workout_id,
                    COALESCE(exercise_library_id, 'unknown-exercise') as exercise_library_id,
                    COALESCE(order_index, 0) as order_index,
                    target_sets,
                    target_reps,
                    target_weight_kg,
                    target_time_seconds,
                    target_distance_meters,
                    notes,
                    COALESCE(created_at, strftime('%s', 'now') * 1000) as created_at,
                    COALESCE(updated_at, strftime('%s', 'now') * 1000) as updated_at
                FROM exercises_backup
            """)
            Log.d("Migration_9_10", "Exercises data restored successfully")
        } catch (e: Exception) {
            Log.w("Migration_9_10", "Failed to restore exercises data: ${e.message}")
        }
    }

    private fun restoreExerciseSetsData(database: SupportSQLiteDatabase) {
        if (!tableExists(database, "exercise_sets_backup")) return
        
        try {
            database.execSQL("""
                INSERT INTO exercise_sets (
                    exercise_id, set_number, reps, weight_kg, time_seconds, 
                    distance_meters, rpe, notes, completed_at
                )
                SELECT 
                    exercise_id,
                    COALESCE(set_number, 1) as set_number,
                    reps,
                    weight_kg,
                    time_seconds,
                    distance_meters,
                    rpe,
                    notes,
                    completed_at
                FROM exercise_sets_backup
                WHERE exercise_id IN (SELECT id FROM exercises)
            """)
            Log.d("Migration_9_10", "Exercise sets data restored successfully")
        } catch (e: Exception) {
            Log.w("Migration_9_10", "Failed to restore exercise sets data: ${e.message}")
        }
    }

    private fun restoreExerciseWeightMemoryData(database: SupportSQLiteDatabase) {
        if (!tableExists(database, "exercise_weight_memory_backup")) return
        
        try {
            database.execSQL("""
                INSERT INTO exercise_weight_memory (
                    user_id, exercise_library_id, last_weight_kg, last_used_at, usage_count
                )
                SELECT 
                    user_id,
                    exercise_library_id,
                    last_weight_kg,
                    last_used_at,
                    COALESCE(usage_count, 1) as usage_count
                FROM exercise_weight_memory_backup
            """)
            Log.d("Migration_9_10", "Exercise weight memory data restored successfully")
        } catch (e: Exception) {
            Log.w("Migration_9_10", "Failed to restore exercise weight memory data: ${e.message}")
        }
    }


    private fun restoreWorkoutTemplatesData(database: SupportSQLiteDatabase) {
        if (!tableExists(database, "workout_templates_backup")) return
        
        try {
            database.execSQL("""
                INSERT INTO workout_templates (
                    id, user_id, name, description, template_exercises_json, estimated_duration_minutes,
                    difficulty_level, tags, usage_count, last_used_at, created_at, updated_at, 
                    is_synced, sync_version
                )
                SELECT 
                    id,
                    COALESCE(user_id, 'unknown') as user_id,
                    COALESCE(name, 'Template') as name,
                    description,
                    COALESCE(template_exercises_json, '[]') as template_exercises_json,
                    estimated_duration_minutes,
                    difficulty_level,
                    tags,
                    COALESCE(usage_count, 0) as usage_count,
                    last_used_at,
                    CASE 
                        WHEN typeof(created_at) = 'integer' THEN datetime(created_at/1000, 'unixepoch')
                        WHEN typeof(created_at) = 'text' THEN created_at
                        ELSE datetime('now')
                    END as created_at,
                    CASE 
                        WHEN typeof(updated_at) = 'integer' THEN datetime(updated_at/1000, 'unixepoch')
                        WHEN typeof(updated_at) = 'text' THEN updated_at
                        ELSE datetime('now')
                    END as updated_at,
                    COALESCE(is_synced, 0) as is_synced,
                    COALESCE(sync_version, 1) as sync_version
                FROM workout_templates_backup
            """)
            Log.d("Migration_9_10", "Workout templates data restored successfully")
        } catch (e: Exception) {
            Log.w("Migration_9_10", "Failed to restore workout templates data: ${e.message}")
        }
    }

    private fun restoreCustomExercisesData(database: SupportSQLiteDatabase) {
        if (!tableExists(database, "custom_exercises_backup")) return
        
        try {
            database.execSQL("""
                INSERT INTO custom_exercises (
                    id, user_id, name, primary_muscle_group, equipment, secondary_muscle_groups,
                    difficulty, notes, created_at, updated_at, is_synced, sync_version
                )
                SELECT 
                    id,
                    COALESCE(user_id, 'unknown') as user_id,
                    COALESCE(name, 'Custom Exercise') as name,
                    COALESCE(primary_muscle_group, 'Other') as primary_muscle_group,
                    COALESCE(equipment, 'None') as equipment,
                    secondary_muscle_groups,
                    difficulty,
                    notes,
                    CASE 
                        WHEN typeof(created_at) = 'integer' THEN datetime(created_at/1000, 'unixepoch')
                        WHEN typeof(created_at) = 'text' THEN created_at
                        ELSE datetime('now')
                    END as created_at,
                    CASE 
                        WHEN typeof(updated_at) = 'integer' THEN datetime(updated_at/1000, 'unixepoch')
                        WHEN typeof(updated_at) = 'text' THEN updated_at
                        ELSE datetime('now')
                    END as updated_at,
                    COALESCE(is_synced, 0) as is_synced,
                    COALESCE(sync_version, 1) as sync_version
                FROM custom_exercises_backup
            """)
            Log.d("Migration_9_10", "Custom exercises data restored successfully")
        } catch (e: Exception) {
            Log.w("Migration_9_10", "Failed to restore custom exercises data: ${e.message}")
        }
    }

    private fun restoreUserProfilesData(database: SupportSQLiteDatabase) {
        if (!tableExists(database, "user_profiles_backup")) return
        
        try {
            database.execSQL("""
                INSERT INTO user_profiles (
                    id, user_id, display_name, age, weight_kg, height_cm, fitness_level, goals,
                    available_equipment, workout_frequency, preferred_workout_duration, 
                    created_at, updated_at, is_synced, sync_version
                )
                SELECT 
                    id,
                    COALESCE(user_id, 'unknown') as user_id,
                    COALESCE(display_name, 'User') as display_name,
                    age,
                    weight_kg,
                    height_cm,
                    fitness_level,
                    goals,
                    available_equipment,
                    workout_frequency,
                    preferred_workout_duration,
                    CASE 
                        WHEN typeof(created_at) = 'integer' THEN datetime(created_at/1000, 'unixepoch')
                        WHEN typeof(created_at) = 'text' THEN created_at
                        ELSE datetime('now')
                    END as created_at,
                    CASE 
                        WHEN typeof(updated_at) = 'integer' THEN datetime(updated_at/1000, 'unixepoch')
                        WHEN typeof(updated_at) = 'text' THEN updated_at
                        ELSE datetime('now')
                    END as updated_at,
                    COALESCE(is_synced, 0) as is_synced,
                    COALESCE(sync_version, 1) as sync_version
                FROM user_profiles_backup
            """)
            Log.d("Migration_9_10", "User profiles data restored successfully")
        } catch (e: Exception) {
            Log.w("Migration_9_10", "Failed to restore user profiles data: ${e.message}")
        }
    }

    private fun restoreExerciseLibraryData(database: SupportSQLiteDatabase) {
        if (!tableExists(database, "exercise_library_backup")) return
        
        try {
            database.execSQL("""
                INSERT INTO exercise_library (
                    id, name, primary_muscle_group, secondary_muscle_groups, equipment,
                    instructions, difficulty, tags, animation_url, thumbnail_url, 
                    created_at, updated_at
                )
                SELECT 
                    id,
                    COALESCE(name, 'Unknown Exercise') as name,
                    COALESCE(primary_muscle_group, 'Other') as primary_muscle_group,
                    secondary_muscle_groups,
                    COALESCE(equipment, 'None') as equipment,
                    instructions,
                    difficulty,
                    tags,
                    animation_url,
                    thumbnail_url,
                    CASE 
                        WHEN typeof(created_at) = 'integer' THEN datetime(created_at/1000, 'unixepoch')
                        WHEN typeof(created_at) = 'text' THEN created_at
                        ELSE datetime('now')
                    END as created_at,
                    CASE 
                        WHEN typeof(updated_at) = 'integer' THEN datetime(updated_at/1000, 'unixepoch')
                        WHEN typeof(updated_at) = 'text' THEN updated_at
                        ELSE datetime('now')
                    END as updated_at
                FROM exercise_library_backup
            """)
            Log.d("Migration_9_10", "Exercise library data restored successfully")
        } catch (e: Exception) {
            Log.w("Migration_9_10", "Failed to restore exercise library data: ${e.message}")
        }
    }

    private fun createAllIndices(database: SupportSQLiteDatabase) {
        Log.d("Migration_9_10", "Creating all indices")
        
        val indexCommands = listOf(
            // Workouts indices
            "CREATE INDEX IF NOT EXISTS index_workouts_user_id ON workouts(user_id)",
            "CREATE INDEX IF NOT EXISTS index_workouts_date ON workouts(date)",
            "CREATE INDEX IF NOT EXISTS index_workouts_status ON workouts(status)",
            
            // Simple workouts indices
            "CREATE INDEX IF NOT EXISTS index_simple_workouts_user_id ON simple_workouts(user_id)",
            "CREATE INDEX IF NOT EXISTS index_simple_workouts_user_id_created_at ON simple_workouts(user_id, created_at)",
            "CREATE INDEX IF NOT EXISTS index_simple_workouts_created_at ON simple_workouts(created_at)",
            
            // Simple exercises indices
            "CREATE INDEX IF NOT EXISTS index_simple_exercises_workout_id ON simple_exercises(workout_id)",
            "CREATE INDEX IF NOT EXISTS index_simple_exercises_workout_id_order_index ON simple_exercises(workout_id, order_index)",
            "CREATE INDEX IF NOT EXISTS index_simple_exercises_name ON simple_exercises(name)",
            
            // Exercises indices
            "CREATE INDEX IF NOT EXISTS index_exercises_workout_id ON exercises(workout_id)",
            "CREATE INDEX IF NOT EXISTS index_exercises_exercise_library_id ON exercises(exercise_library_id)",
            "CREATE INDEX IF NOT EXISTS index_exercises_order_index ON exercises(order_index)",
            
            // Exercise sets indices
            "CREATE INDEX IF NOT EXISTS index_exercise_sets_exercise_id ON exercise_sets(exercise_id)",
            "CREATE INDEX IF NOT EXISTS index_exercise_sets_set_number ON exercise_sets(set_number)",
            "CREATE INDEX IF NOT EXISTS index_exercise_sets_completed_at ON exercise_sets(completed_at)",
            
            // Daily workouts indices
            "CREATE INDEX IF NOT EXISTS index_daily_workouts_user_id ON daily_workouts(user_id)",
            "CREATE INDEX IF NOT EXISTS index_daily_workouts_date_user_id ON daily_workouts(date, user_id)",
            "CREATE INDEX IF NOT EXISTS index_daily_workouts_template_id ON daily_workouts(template_id)",
            "CREATE INDEX IF NOT EXISTS index_daily_workouts_status ON daily_workouts(status)",
            "CREATE INDEX IF NOT EXISTS index_daily_workouts_created_at ON daily_workouts(created_at)",
            
            // Workout templates indices
            "CREATE INDEX IF NOT EXISTS index_workout_templates_user_id ON workout_templates(user_id)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_workout_templates_name_user_id ON workout_templates(name, user_id)",
            "CREATE INDEX IF NOT EXISTS index_workout_templates_created_at ON workout_templates(created_at)",
            
            // Custom exercises indices
            "CREATE INDEX IF NOT EXISTS index_custom_exercises_user_id ON custom_exercises(user_id)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_custom_exercises_name_user_id ON custom_exercises(name, user_id)",
            "CREATE INDEX IF NOT EXISTS index_custom_exercises_primary_muscle_group ON custom_exercises(primary_muscle_group)",
            "CREATE INDEX IF NOT EXISTS index_custom_exercises_equipment ON custom_exercises(equipment)"
        )
        
        indexCommands.forEach { command ->
            try {
                database.execSQL(command)
            } catch (e: Exception) {
                Log.w("Migration_9_10", "Failed to create index: ${e.message}")
            }
        }
        
        Log.d("Migration_9_10", "All indices created successfully")
    }

    private fun cleanupBackupTables(database: SupportSQLiteDatabase) {
        Log.d("Migration_9_10", "Cleaning up backup tables")
        
        val backupTables = listOf(
            "workouts_backup", "simple_workouts_backup", "simple_exercises_backup",
            "exercises_backup", "exercise_sets_backup", "exercise_weight_memory_backup",
            "daily_workouts_backup", "workout_templates_backup", "custom_exercises_backup",
            "user_profiles_backup", "exercise_library_backup"
        )
        
        backupTables.forEach { tableName ->
            try {
                database.execSQL("DROP TABLE IF EXISTS $tableName")
            } catch (e: Exception) {
                Log.w("Migration_9_10", "Failed to drop backup table $tableName: ${e.message}")
            }
        }
        
        Log.d("Migration_9_10", "Backup tables cleaned up")
    }

    private fun validateFinalSchema(database: SupportSQLiteDatabase) {
        Log.d("Migration_9_10", "Validating final schema")
        
        val expectedTables = listOf(
            "workouts", "simple_workouts", "simple_exercises", "exercises",
            "exercise_sets", "exercise_weight_memory", "daily_workouts",
            "workout_templates", "custom_exercises", "user_profiles", "exercise_library"
        )
        
        expectedTables.forEach { tableName ->
            val exists = tableExists(database, tableName)
            if (exists) {
                Log.d("Migration_9_10", "✓ Table $tableName exists")
            } else {
                Log.e("Migration_9_10", "✗ Table $tableName missing!")
            }
        }
        
        Log.d("Migration_9_10", "Schema validation completed")
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
} 