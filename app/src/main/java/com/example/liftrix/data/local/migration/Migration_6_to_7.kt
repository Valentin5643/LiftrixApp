package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration from version 6 to 7.
 * Migrates SimpleWorkout data to enhanced Exercise system and removes SimpleWorkout entities.
 * Preserves existing data while transitioning to flexible metrics support.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION")
        try {
            // Step 1: Create new enhanced tables
            createNewTables(database)
            
            // Step 2: Migrate SimpleWorkout data to regular workouts
            migrateSimpleWorkouts(database)
            
            // Step 3: Migrate SimpleExercise data to new Exercise/ExerciseSet structure
            migrateSimpleExercises(database)
            
            // Step 4: Create exercise weight memory table
            createExerciseWeightMemoryTable(database)
            
            // Step 5: Drop obsolete SimpleWorkout tables
            dropObsoleteTables(database)
            
            database.execSQL("COMMIT")
        } catch (e: Exception) {
            database.execSQL("ROLLBACK")
            throw e
        }
    }
    
    private fun createNewTables(database: SupportSQLiteDatabase) {
        // Create exercises table with flexible metrics support
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
        
        // Create exercise_sets table with flexible metrics
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
        
        // Create performance indexes for new tables
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exercises_workout_id ON exercises(workout_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exercises_exercise_library_id ON exercises(exercise_library_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exercises_order_index ON exercises(order_index)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_sets_exercise_id ON exercise_sets(exercise_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_sets_set_number ON exercise_sets(set_number)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_sets_completed_at ON exercise_sets(completed_at)")
    }
    
    private fun migrateSimpleWorkouts(database: SupportSQLiteDatabase) {
        // Note: SimpleWorkouts are already stored as regular workouts in this system
        // This step ensures any remaining simple workout data is preserved
        database.execSQL(
            """
            INSERT OR IGNORE INTO workouts (id, user_id, name, date, exercises_json, status, notes, created_at, updated_at, is_synced, sync_version)
            SELECT 
                sw.id,
                sw.user_id,
                sw.name,
                date('now') as date,
                '[]' as exercises_json,
                'COMPLETED' as status,
                sw.description as notes,
                CASE 
                    WHEN typeof(sw.created_at) = 'text' THEN strftime('%s', sw.created_at) * 1000
                    ELSE sw.created_at
                END as created_at,
                CASE 
                    WHEN typeof(sw.updated_at) = 'text' THEN strftime('%s', sw.updated_at) * 1000
                    ELSE sw.updated_at
                END as updated_at,
                sw.is_synced,
                sw.sync_version
            FROM simple_workouts sw
            WHERE NOT EXISTS (SELECT 1 FROM workouts w WHERE w.id = sw.id)
            """.trimIndent()
        )
    }
    
    private fun migrateSimpleExercises(database: SupportSQLiteDatabase) {
        // Create a mapping of SimpleExercise names to exercise library IDs
        val exerciseNameMappings = mapOf(
            "push-ups" to "push-up",
            "push ups" to "push-up",
            "pushups" to "push-up",
            "bench press" to "bench-press",
            "squats" to "squat",
            "deadlifts" to "deadlift",
            "deadlift" to "deadlift",
            "pull-ups" to "pull-up",
            "pull ups" to "pull-up",
            "pullups" to "pull-up",
            "rows" to "barbell-row",
            "barbell row" to "barbell-row",
            "overhead press" to "overhead-press",
            "shoulder press" to "overhead-press",
            "bicep curls" to "bicep-curl",
            "tricep dips" to "tricep-dip",
            "lunges" to "lunge",
            "planks" to "plank",
            "plank" to "plank"
        )
        
        // Insert exercises from simple_exercises
        database.execSQL(
            """
            INSERT INTO exercises (workout_id, exercise_library_id, order_index, target_sets, target_reps, target_weight_kg, notes, created_at, updated_at)
            SELECT 
                CAST(se.workout_id AS INTEGER) as workout_id,
                CASE 
                    WHEN LOWER(se.name) IN ('push-ups', 'push ups', 'pushups') THEN 'push-up'
                    WHEN LOWER(se.name) = 'bench press' THEN 'bench-press'
                    WHEN LOWER(se.name) = 'squats' THEN 'squat'
                    WHEN LOWER(se.name) IN ('deadlifts', 'deadlift') THEN 'deadlift'
                    WHEN LOWER(se.name) IN ('pull-ups', 'pull ups', 'pullups') THEN 'pull-up'
                    WHEN LOWER(se.name) IN ('rows', 'barbell row') THEN 'barbell-row'
                    WHEN LOWER(se.name) IN ('overhead press', 'shoulder press') THEN 'overhead-press'
                    WHEN LOWER(se.name) = 'bicep curls' THEN 'bicep-curl'
                    WHEN LOWER(se.name) = 'tricep dips' THEN 'tricep-dip'
                    WHEN LOWER(se.name) = 'lunges' THEN 'lunge'
                    WHEN LOWER(se.name) IN ('planks', 'plank') THEN 'plank'
                    ELSE 'custom-exercise'
                END as exercise_library_id,
                se.order_index,
                se.sets as target_sets,
                se.reps as target_reps,
                CAST(se.weight_kg AS REAL) as target_weight_kg,
                'Migrated from: ' || se.name as notes,
                CASE 
                    WHEN typeof(se.created_at) = 'text' THEN strftime('%s', se.created_at) * 1000
                    ELSE se.created_at
                END as created_at,
                CASE 
                    WHEN typeof(se.created_at) = 'text' THEN strftime('%s', se.created_at) * 1000
                    ELSE se.created_at
                END as updated_at
            FROM simple_exercises se
            WHERE EXISTS (SELECT 1 FROM workouts w WHERE CAST(w.id AS TEXT) = se.workout_id)
            """.trimIndent()
        )
        
        // Create exercise sets for each migrated exercise
        database.execSQL(
            """
            INSERT INTO exercise_sets (exercise_id, set_number, reps, weight_kg, rpe, completed_at)
            SELECT 
                e.id as exercise_id,
                1 as set_number,
                se.reps,
                CAST(se.weight_kg AS REAL) as weight_kg,
                se.rpe,
                e.created_at as completed_at
            FROM exercises e
            JOIN simple_exercises se ON (
                CAST(e.workout_id AS TEXT) = se.workout_id 
                AND e.order_index = se.order_index
                AND e.notes LIKE 'Migrated from: %'
            )
            WHERE e.target_reps IS NOT NULL OR e.target_weight_kg IS NOT NULL
            """.trimIndent()
        )
    }
    
    private fun createExerciseWeightMemoryTable(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exercise_weight_memory (
                user_id TEXT NOT NULL,
                exercise_library_id TEXT NOT NULL,
                last_weight_kg REAL NOT NULL,
                last_used_at INTEGER NOT NULL,
                usage_count INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (user_id, exercise_library_id)
            )
            """.trimIndent()
        )
        
        // Populate weight memory from migrated exercise data
        database.execSQL(
            """
            INSERT OR REPLACE INTO exercise_weight_memory (user_id, exercise_library_id, last_weight_kg, last_used_at, usage_count)
            SELECT 
                w.user_id,
                e.exercise_library_id,
                es.weight_kg as last_weight_kg,
                MAX(es.completed_at) as last_used_at,
                COUNT(*) as usage_count
            FROM exercise_sets es
            JOIN exercises e ON es.exercise_id = e.id
            JOIN workouts w ON e.workout_id = CAST(w.id AS INTEGER)
            WHERE es.weight_kg IS NOT NULL AND es.completed_at IS NOT NULL
            GROUP BY w.user_id, e.exercise_library_id
            """.trimIndent()
        )
    }
    
    private fun dropObsoleteTables(database: SupportSQLiteDatabase) {
        // Keep simple workout system tables as they are still being used by the application
        // Only drop simple_exercises table as it's migrated to the new exercise system
        database.execSQL("DROP TABLE IF EXISTS simple_exercises")
        
        // Drop related simple_exercises indexes only
        database.execSQL("DROP INDEX IF EXISTS index_simple_exercises_workout_id_order_index")
        database.execSQL("DROP INDEX IF EXISTS index_simple_exercises_workout_id")
        
        // Keep simple_workouts table and its indexes as they are still in use
    }
}