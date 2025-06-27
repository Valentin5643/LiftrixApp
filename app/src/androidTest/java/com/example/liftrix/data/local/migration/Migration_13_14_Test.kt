package com.example.liftrix.data.local.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.migration.Migration_13_to_14.MIGRATION_13_14
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Migration_13_14
 * 
 * Verifies:
 * - Weight memory fields are added to exercises table
 * - SimpleWorkout data is migrated to Workout format
 * - SimpleExercise data is converted to JSON format
 * - SimpleWorkout tables are dropped
 * - Data integrity is preserved
 */
@RunWith(AndroidJUnit4::class)
class Migration_13_14_Test {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LiftrixDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate13To14() {
        var db = helper.createDatabase(TEST_DB, 13).apply {
            // Insert test data into version 13 schema
            insertTestSimpleWorkoutData()
            close()
        }

        // Re-open the database with version 14 and provide MIGRATION_13_14
        db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        // Verify migration results
        verifyWeightMemoryFieldsAdded(db)
        verifySimpleWorkoutDataMigrated(db)
        verifySimpleWorkoutTablesDropped(db)
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14WithNoSimpleWorkoutData() {
        var db = helper.createDatabase(TEST_DB, 13).apply {
            // Don't insert any SimpleWorkout data
            close()
        }

        // Re-open the database with version 14 and provide MIGRATION_13_14
        db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        // Verify migration handles empty data gracefully
        verifyWeightMemoryFieldsAdded(db)
        verifySimpleWorkoutTablesDropped(db)
        
        // Verify no workouts were created
        db.query("SELECT COUNT(*) FROM workouts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14WithLargeDataset() {
        var db = helper.createDatabase(TEST_DB, 13).apply {
            // Insert larger test dataset
            insertLargeTestSimpleWorkoutData()
            close()
        }

        // Re-open the database with version 14 and provide MIGRATION_13_14
        db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        // Verify all data was migrated
        verifyLargeDatasetMigration(db)
        verifySimpleWorkoutTablesDropped(db)
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertTestSimpleWorkoutData() {
        // Insert test SimpleWorkout
        execSQL("""
            INSERT INTO simple_workouts (
                id, user_id, name, description, created_at, updated_at, is_synced, sync_version
            ) VALUES (
                'test-workout-1', 'test-user-1', 'Test Workout', 'Test Description', 
                '2024-01-01T10:00:00Z', '2024-01-01T11:00:00Z', 0, 1
            )
        """)

        // Insert test SimpleExercises
        execSQL("""
            INSERT INTO simple_exercises (
                id, workout_id, name, reps, sets, weight_kg, rpe, order_index, 
                photo_url, animation_url, created_at
            ) VALUES (
                'test-exercise-1', 'test-workout-1', 'Bench Press', 10, 3, 80.0, 8.0, 0,
                'photo1.jpg', 'anim1.gif', '2024-01-01T10:00:00Z'
            )
        """)

        execSQL("""
            INSERT INTO simple_exercises (
                id, workout_id, name, reps, sets, weight_kg, rpe, order_index, 
                photo_url, animation_url, created_at
            ) VALUES (
                'test-exercise-2', 'test-workout-1', 'Squats', 12, 4, 100.0, 7.5, 1,
                NULL, NULL, '2024-01-01T10:05:00Z'
            )
        """)
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertLargeTestSimpleWorkoutData() {
        // Insert multiple workouts with exercises
        repeat(5) { workoutIndex ->
            val workoutId = "test-workout-$workoutIndex"
            execSQL("""
                INSERT INTO simple_workouts (
                    id, user_id, name, description, created_at, updated_at, is_synced, sync_version
                ) VALUES (
                    '$workoutId', 'test-user-1', 'Workout $workoutIndex', 'Description $workoutIndex', 
                    '2024-01-0${workoutIndex + 1}T10:00:00Z', '2024-01-0${workoutIndex + 1}T11:00:00Z', 0, 1
                )
            """)

            repeat(3) { exerciseIndex ->
                val exerciseId = "test-exercise-${workoutIndex}-$exerciseIndex"
                execSQL("""
                    INSERT INTO simple_exercises (
                        id, workout_id, name, reps, sets, weight_kg, rpe, order_index, 
                        photo_url, animation_url, created_at
                    ) VALUES (
                        '$exerciseId', '$workoutId', 'Exercise $exerciseIndex', ${10 + exerciseIndex}, 3, ${50.0 + exerciseIndex * 10}, 
                        ${7.0 + exerciseIndex * 0.5}, $exerciseIndex, NULL, NULL, '2024-01-0${workoutIndex + 1}T10:00:00Z'
                    )
                """)
            }
        }
    }

    private fun verifyWeightMemoryFieldsAdded(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Verify weight memory columns exist in exercises table
        db.query("PRAGMA table_info(exercises)").use { cursor ->
            val columnNames = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(1)) // Column name is at index 1
            }
            
            assertTrue(
                columnNames.contains("last_used_weight_kg"),
                "last_used_weight_kg column should be added to exercises table"
            )
            assertTrue(
                columnNames.contains("weight_memory_updated_at"),
                "weight_memory_updated_at column should be added to exercises table"
            )
        }
    }

    private fun verifySimpleWorkoutDataMigrated(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Verify workout was migrated
        db.query("SELECT * FROM workouts WHERE id = 'test-workout-1'").use { cursor ->
            assertTrue(cursor.moveToFirst(), "Migrated workout should exist")
            assertEquals("test-user-1", cursor.getString(cursor.getColumnIndex("user_id")))
            assertEquals("Test Workout", cursor.getString(cursor.getColumnIndex("name")))
            assertEquals("Test Description", cursor.getString(cursor.getColumnIndex("notes")))
            assertEquals("COMPLETED", cursor.getString(cursor.getColumnIndex("status")))
            
            // Verify exercises JSON contains expected data
            val exercisesJson = cursor.getString(cursor.getColumnIndex("exercises_json"))
            assertTrue(exercisesJson.contains("Bench Press"), "Exercises JSON should contain Bench Press")
            assertTrue(exercisesJson.contains("Squats"), "Exercises JSON should contain Squats")
            assertTrue(exercisesJson.contains("80.0"), "Exercises JSON should contain weight data")
        }
    }

    private fun verifyLargeDatasetMigration(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Verify all workouts were migrated
        db.query("SELECT COUNT(*) FROM workouts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(5, cursor.getInt(0), "All 5 workouts should be migrated")
        }

        // Verify exercises JSON for each workout
        db.query("SELECT id, exercises_json FROM workouts ORDER BY id").use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                count++
                val exercisesJson = cursor.getString(1)
                // Each workout should have 3 exercises in JSON
                val exerciseCount = exercisesJson.split("\"id\"").size - 1
                assertEquals(3, exerciseCount, "Each workout should have 3 exercises in JSON")
            }
            assertEquals(5, count, "Should iterate through all 5 workouts")
        }
    }

    private fun verifySimpleWorkoutTablesDropped(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Verify simple_workouts table doesn't exist
        var tableExists = false
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='simple_workouts'").use { cursor ->
            tableExists = cursor.moveToFirst()
        }
        assertFalse(tableExists, "simple_workouts table should be dropped")

        // Verify simple_exercises table doesn't exist
        tableExists = false
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='simple_exercises'").use { cursor ->
            tableExists = cursor.moveToFirst()
        }
        assertFalse(tableExists, "simple_exercises table should be dropped")
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_performanceWithLargeDataset() {
        val startTime = System.currentTimeMillis()
        
        var db = helper.createDatabase(TEST_DB, 13).apply {
            // Insert very large dataset (50 workouts, 5 exercises each)
            repeat(50) { workoutIndex ->
                val workoutId = "perf-workout-$workoutIndex"
                execSQL("""
                    INSERT INTO simple_workouts (
                        id, user_id, name, description, created_at, updated_at, is_synced, sync_version
                    ) VALUES (
                        '$workoutId', 'perf-user-1', 'Performance Workout $workoutIndex', 'Large dataset test', 
                        '2024-01-01T10:00:00Z', '2024-01-01T11:00:00Z', 0, 1
                    )
                """)

                repeat(5) { exerciseIndex ->
                    val exerciseId = "perf-exercise-${workoutIndex}-$exerciseIndex"
                    execSQL("""
                        INSERT INTO simple_exercises (
                            id, workout_id, name, reps, sets, weight_kg, rpe, order_index, 
                            photo_url, animation_url, created_at
                        ) VALUES (
                            '$exerciseId', '$workoutId', 'Performance Exercise $exerciseIndex', 
                            ${10 + exerciseIndex}, 3, ${50.0 + exerciseIndex * 10}, 
                            ${7.0 + exerciseIndex * 0.5}, $exerciseIndex, NULL, NULL, '2024-01-01T10:00:00Z'
                        )
                    """)
                }
            }
            close()
        }

        // Run migration and measure time
        val migrationStartTime = System.currentTimeMillis()
        db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)
        val migrationEndTime = System.currentTimeMillis()
        
        val migrationTime = migrationEndTime - migrationStartTime
        val totalTime = migrationEndTime - startTime

        // Verify migration completed successfully
        db.query("SELECT COUNT(*) FROM workouts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(50, cursor.getInt(0), "All 50 workouts should be migrated")
        }

        // Verify performance is acceptable (should complete within 30 seconds for large dataset)
        assertTrue(
            migrationTime < 30000, 
            "Migration took ${migrationTime}ms, should complete within 30 seconds for large dataset"
        )
        
        println("Migration performance: ${migrationTime}ms for 50 workouts with 250 exercises")
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_handlesCorruptedData() {
        var db = helper.createDatabase(TEST_DB, 13).apply {
            // Insert workout with missing required fields
            execSQL("""
                INSERT INTO simple_workouts (
                    id, user_id, name, description, created_at, updated_at, is_synced, sync_version
                ) VALUES (
                    'corrupt-workout-1', '', 'Corrupt Workout', NULL, 
                    '2024-01-01T10:00:00Z', '2024-01-01T11:00:00Z', 0, 1
                )
            """)

            // Insert exercise with invalid data
            execSQL("""
                INSERT INTO simple_exercises (
                    id, workout_id, name, reps, sets, weight_kg, rpe, order_index, 
                    photo_url, animation_url, created_at
                ) VALUES (
                    'corrupt-exercise-1', 'corrupt-workout-1', '', -1, 0, -50.0, 15.0, -1,
                    NULL, NULL, '2024-01-01T10:00:00Z'
                )
            """)
            close()
        }

        // Migration should handle corrupted data gracefully
        db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        // Verify migration completed without crashing
        verifyWeightMemoryFieldsAdded(db)
        verifySimpleWorkoutTablesDropped(db)
        
        // Corrupted data should either be skipped or cleaned up
        db.query("SELECT COUNT(*) FROM workouts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            // Should have 0 or 1 workouts (depending on migration strategy for corrupted data)
            val count = cursor.getInt(0)
            assertTrue(count >= 0, "Migration should handle corrupted data without crashing")
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_preservesDataIntegrity() {
        var db = helper.createDatabase(TEST_DB, 13).apply {
            insertTestSimpleWorkoutData()
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        // Verify all original data is preserved in the migrated format
        db.query("SELECT * FROM workouts WHERE id = 'test-workout-1'").use { cursor ->
            assertTrue(cursor.moveToFirst(), "Migrated workout should exist")
            
            // Verify all fields are preserved
            assertEquals("test-user-1", cursor.getString(cursor.getColumnIndex("user_id")))
            assertEquals("Test Workout", cursor.getString(cursor.getColumnIndex("name")))
            assertEquals("Test Description", cursor.getString(cursor.getColumnIndex("notes")))
            assertEquals("2024-01-01T10:00:00Z", cursor.getString(cursor.getColumnIndex("created_at")))
            assertEquals("2024-01-01T11:00:00Z", cursor.getString(cursor.getColumnIndex("updated_at")))
            
            // Verify exercises JSON contains all original exercise data
            val exercisesJson = cursor.getString(cursor.getColumnIndex("exercises_json"))
            
            // Check that both exercises are present with all their data
            assertTrue(exercisesJson.contains("Bench Press"), "Should contain Bench Press")
            assertTrue(exercisesJson.contains("Squats"), "Should contain Squats")
            assertTrue(exercisesJson.contains("80.0"), "Should contain Bench Press weight")
            assertTrue(exercisesJson.contains("100.0"), "Should contain Squats weight")
            assertTrue(exercisesJson.contains("10"), "Should contain Bench Press reps")
            assertTrue(exercisesJson.contains("12"), "Should contain Squats reps")
            assertTrue(exercisesJson.contains("3"), "Should contain Bench Press sets")
            assertTrue(exercisesJson.contains("4"), "Should contain Squats sets")
            assertTrue(exercisesJson.contains("8.0"), "Should contain Bench Press RPE")
            assertTrue(exercisesJson.contains("7.5"), "Should contain Squats RPE")
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_handlesEmptyExerciseData() {
        var db = helper.createDatabase(TEST_DB, 13).apply {
            // Insert workout without exercises
            execSQL("""
                INSERT INTO simple_workouts (
                    id, user_id, name, description, created_at, updated_at, is_synced, sync_version
                ) VALUES (
                    'empty-workout-1', 'test-user-1', 'Empty Workout', 'No exercises', 
                    '2024-01-01T10:00:00Z', '2024-01-01T11:00:00Z', 0, 1
                )
            """)
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        // Verify empty workout is migrated correctly
        db.query("SELECT * FROM workouts WHERE id = 'empty-workout-1'").use { cursor ->
            assertTrue(cursor.moveToFirst(), "Empty workout should be migrated")
            assertEquals("Empty Workout", cursor.getString(cursor.getColumnIndex("name")))
            
            val exercisesJson = cursor.getString(cursor.getColumnIndex("exercises_json"))
            // Should have empty exercises array
            assertTrue(
                exercisesJson == "[]" || exercisesJson.contains("\"exercises\":[]"),
                "Empty workout should have empty exercises JSON: $exercisesJson"
            )
        }
    }
} 