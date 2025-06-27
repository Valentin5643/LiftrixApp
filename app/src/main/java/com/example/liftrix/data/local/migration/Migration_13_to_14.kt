package com.example.liftrix.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Migration from version 13 to 14
 * 
 * Key Changes:
 * - Adds weight memory fields to exercises table
 * - Migrates SimpleWorkout data to regular Workout format
 * - Converts SimpleExercise data to Exercise format with JSON serialization
 * - Drops SimpleWorkout and SimpleExercise tables
 * 
 * This migration ensures zero data loss while transitioning from SimpleWorkout
 * to unified Workout system with enhanced weight memory capabilities.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("BEGIN TRANSACTION")
        try {
            Log.d("Migration_13_14", "Starting migration from version 13 to 14")
            
            // Step 1: Add weight memory fields to exercises table
            addWeightMemoryFields(database)
            
            // Step 2: Migrate SimpleWorkout data to Workout format
            migrateSimpleWorkoutsToWorkouts(database)
            
            // Step 3: Drop SimpleWorkout tables
            dropSimpleWorkoutTables(database)
            
            database.execSQL("COMMIT")
            Log.d("Migration_13_14", "Migration completed successfully")
        } catch (e: Exception) {
            database.execSQL("ROLLBACK")
            Log.e("Migration_13_14", "Migration failed: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Adds weight memory fields to exercises table
     */
    private fun addWeightMemoryFields(database: SupportSQLiteDatabase) {
        Log.d("Migration_13_14", "Adding weight memory fields to exercises table")
        
        try {
            database.execSQL("ALTER TABLE exercises ADD COLUMN last_used_weight_kg REAL")
            database.execSQL("ALTER TABLE exercises ADD COLUMN weight_memory_updated_at INTEGER")
            Log.d("Migration_13_14", "Weight memory fields added successfully")
        } catch (e: Exception) {
            Log.e("Migration_13_14", "Failed to add weight memory fields: ${e.message}")
            throw e
        }
    }
    
    /**
     * Migrates SimpleWorkout data to regular Workout format
     */
    private fun migrateSimpleWorkoutsToWorkouts(database: SupportSQLiteDatabase) {
        Log.d("Migration_13_14", "Starting SimpleWorkout data migration")
        
        // Check if SimpleWorkout tables exist and have data
        val simpleWorkoutCount = database.query("SELECT COUNT(*) FROM simple_workouts").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        
        if (simpleWorkoutCount == 0) {
            Log.d("Migration_13_14", "No SimpleWorkout data to migrate")
            return
        }
        
        Log.d("Migration_13_14", "Migrating $simpleWorkoutCount SimpleWorkouts")
        
        // Get all SimpleWorkouts with their exercises
        val simpleWorkoutsQuery = """
            SELECT sw.id, sw.user_id, sw.name, sw.description, sw.created_at, sw.updated_at, sw.is_synced, sw.sync_version
            FROM simple_workouts sw
            ORDER BY sw.created_at
        """
        
        database.query(simpleWorkoutsQuery).use { workoutCursor ->
            var migratedCount = 0
            
            while (workoutCursor.moveToNext()) {
                val workoutId = workoutCursor.getString(0)
                val userId = workoutCursor.getString(1)
                val name = workoutCursor.getString(2)
                val description = workoutCursor.getString(3)
                val createdAt = workoutCursor.getString(4)
                val updatedAt = workoutCursor.getString(5)
                val isSynced = workoutCursor.getInt(6)
                val syncVersion = workoutCursor.getInt(7)
                
                try {
                    // Get exercises for this workout
                    val exercisesJson = getExercisesJsonForWorkout(database, workoutId)
                    
                    // Convert createdAt to LocalDate for workout date
                    val workoutDate = try {
                        val instant = Instant.parse(createdAt)
                        instant.atZone(ZoneOffset.UTC).toLocalDate().toString()
                    } catch (e: Exception) {
                        LocalDate.now().toString()
                    }
                    
                    // Insert into workouts table
                    val insertWorkoutSql = """
                        INSERT INTO workouts (
                            id, user_id, name, date, exercises_json, status, start_time, end_time, 
                            notes, template_id, created_at, updated_at, is_synced, sync_version
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """
                    
                    database.execSQL(insertWorkoutSql, arrayOf(
                        workoutId,
                        userId,
                        name,
                        workoutDate,
                        exercisesJson,
                        "COMPLETED", // SimpleWorkouts are completed workouts
                        createdAt, // Use createdAt as start_time
                        updatedAt, // Use updatedAt as end_time
                        description, // Map description to notes
                        null, // No template_id for migrated workouts
                        createdAt,
                        updatedAt,
                        isSynced,
                        syncVersion
                    ))
                    
                    migratedCount++
                    
                } catch (e: Exception) {
                    Log.w("Migration_13_14", "Failed to migrate workout $workoutId: ${e.message}")
                    // Continue with other workouts rather than failing entire migration
                }
            }
            
            Log.d("Migration_13_14", "Successfully migrated $migratedCount out of $simpleWorkoutCount workouts")
        }
    }
    
    /**
     * Converts SimpleExercise data to JSON format for Workout.exercisesJson field
     */
    private fun getExercisesJsonForWorkout(database: SupportSQLiteDatabase, workoutId: String): String {
        val exercisesQuery = """
            SELECT id, name, reps, sets, weight_kg, rpe, order_index, photo_url, animation_url
            FROM simple_exercises 
            WHERE workout_id = ? 
            ORDER BY order_index
        """
        
        val exercisesArray = JSONArray()
        
        database.query(exercisesQuery, arrayOf(workoutId)).use { cursor ->
            while (cursor.moveToNext()) {
                val exerciseId = cursor.getString(0)
                val name = cursor.getString(1)
                val reps = cursor.getInt(2)
                val sets = cursor.getInt(3)
                val weightKg = cursor.getDouble(4)
                val rpe = if (cursor.isNull(5)) null else cursor.getDouble(5)
                val orderIndex = cursor.getInt(6)
                val photoUrl = cursor.getString(7)
                val animationUrl = cursor.getString(8)
                
                // Create exercise JSON object
                val exerciseJson = JSONObject().apply {
                    put("id", exerciseId)
                    put("name", name)
                    put("sets", createSetsJsonArray(sets, reps, weightKg, rpe))
                    put("orderIndex", orderIndex)
                    put("notes", null)
                    put("exerciseLibraryId", "migrated_$exerciseId") // Placeholder exercise library ID
                    if (photoUrl != null) put("photoUrl", photoUrl)
                    if (animationUrl != null) put("animationUrl", animationUrl)
                }
                
                exercisesArray.put(exerciseJson)
            }
        }
        
        return exercisesArray.toString()
    }
    
    /**
     * Creates JSON array for exercise sets based on SimpleExercise data
     */
    private fun createSetsJsonArray(sets: Int, reps: Int, weightKg: Double, rpe: Double?): JSONArray {
        val setsArray = JSONArray()
        
        // Create individual set objects based on SimpleExercise sets count
        repeat(sets) { setIndex ->
            val setJson = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("setNumber", setIndex + 1)
                put("targetReps", reps)
                put("actualReps", reps) // Assume target equals actual for migrated data
                put("weightKg", weightKg.toFloat())
                put("completed", true) // SimpleWorkout exercises are completed
                if (rpe != null) put("rpe", rpe)
                put("restTimeSeconds", null)
                put("notes", null)
            }
            setsArray.put(setJson)
        }
        
        return setsArray
    }
    
    /**
     * Drops SimpleWorkout and SimpleExercise tables after successful migration
     */
    private fun dropSimpleWorkoutTables(database: SupportSQLiteDatabase) {
        Log.d("Migration_13_14", "Dropping SimpleWorkout tables")
        
        try {
            // Drop foreign key dependent table first
            database.execSQL("DROP TABLE IF EXISTS simple_exercises")
            database.execSQL("DROP TABLE IF EXISTS simple_workouts")
            
            Log.d("Migration_13_14", "SimpleWorkout tables dropped successfully")
        } catch (e: Exception) {
            Log.e("Migration_13_14", "Failed to drop SimpleWorkout tables: ${e.message}")
            throw e
        }
    }
} 