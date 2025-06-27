package com.example.liftrix.data.local.migration

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.migration.MIGRATION_10_11
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Test for Migration_10_11 which fixes SimpleExerciseEntity schema consistency.
 */
@RunWith(AndroidJUnit4::class)
class Migration10To11Test {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LiftrixDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate10To11_addsSimpleExerciseMissingColumns() {
        // Create database version 10 with SimpleExercise data
        val db = helper.createDatabase(TEST_DB, 10).apply {
            // Insert test data into simple_workouts first
            execSQL(
                """
                INSERT INTO simple_workouts (id, user_id, name, description, created_at, updated_at, is_synced, sync_version)
                VALUES ('workout1', 'user1', 'Test Workout', 'Description', '2024-01-01T10:00:00Z', '2024-01-01T10:00:00Z', 0, 0)
                """.trimIndent()
            )
            
            // Insert test data into simple_exercises (Migration_5_6 schema - with created_at)
            execSQL(
                """
                INSERT INTO simple_exercises (id, workout_id, name, reps, rpe, sets, weight_kg, order_index, created_at)
                VALUES ('exercise1', 'workout1', 'Push-ups', 10, 8, 3, 0.0, 0, '2024-01-01T10:00:00Z')
                """.trimIndent()
            )
            close()
        }

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify the migration added missing columns
        migratedDb.query("PRAGMA table_info(simple_exercises)").use { cursor ->
            val columnNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(1)) // Column name is at index 1
            }
            
            // Verify all expected columns exist
            assert(columnNames.contains("photo_url")) { "Missing photo_url column after migration" }
            assert(columnNames.contains("animation_url")) { "Missing animation_url column after migration" }
            assert(columnNames.contains("created_at")) { "Missing created_at column after migration" }
        }

        // Verify data integrity - the migrated row should still exist with null photo/animation URLs
        migratedDb.query("SELECT * FROM simple_exercises WHERE id = 'exercise1'").use { cursor ->
            assert(cursor.moveToFirst()) { "Migrated exercise data not found" }
            
            // Verify existing data is preserved
            assert(cursor.getString(cursor.getColumnIndex("id")) == "exercise1")
            assert(cursor.getString(cursor.getColumnIndex("workout_id")) == "workout1")
            assert(cursor.getString(cursor.getColumnIndex("name")) == "Push-ups")
            assert(cursor.getInt(cursor.getColumnIndex("reps")) == 10)
            assert(cursor.getDouble(cursor.getColumnIndex("rpe")) == 8.0) // Should be REAL now
            assert(cursor.getInt(cursor.getColumnIndex("sets")) == 3)
            assert(cursor.getDouble(cursor.getColumnIndex("weight_kg")) == 0.0)
            assert(cursor.getInt(cursor.getColumnIndex("order_index")) == 0)
            
            // Verify new columns are null (as expected for migrated data)
            assert(cursor.isNull(cursor.getColumnIndex("photo_url"))) { "photo_url should be null for migrated data" }
            assert(cursor.isNull(cursor.getColumnIndex("animation_url"))) { "animation_url should be null for migrated data" }
            
            // Verify created_at is preserved
            assert(cursor.getString(cursor.getColumnIndex("created_at")) == "2024-01-01T10:00:00Z")
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_handlesSimpleExerciseWithoutCreatedAt() {
        // Create database version 10 with SimpleExercise data but without created_at column
        val db = helper.createDatabase(TEST_DB, 10).apply {
            // Insert test data into simple_workouts first
            execSQL(
                """
                INSERT INTO simple_workouts (id, user_id, name, description, created_at, updated_at, is_synced, sync_version)
                VALUES ('workout2', 'user2', 'Test Workout 2', 'Description', '2024-01-01T11:00:00Z', '2024-01-01T11:00:00Z', 0, 0)
                """.trimIndent()
            )
            
            // Create simple_exercises table without created_at (simulating older version)
            execSQL("DROP TABLE IF EXISTS simple_exercises")
            execSQL(
                """
                CREATE TABLE simple_exercises (
                    id TEXT PRIMARY KEY NOT NULL,
                    workout_id TEXT NOT NULL,
                    name TEXT NOT NULL CHECK(length(name) >= 2 AND length(name) <= 100),
                    reps INTEGER NOT NULL CHECK(reps >= 1 AND reps <= 999),
                    rpe INTEGER CHECK(rpe IS NULL OR (rpe >= 1 AND rpe <= 10)),
                    sets INTEGER NOT NULL CHECK(sets >= 1 AND sets <= 50),
                    weight_kg REAL CHECK(weight_kg IS NULL OR (weight_kg >= 0 AND weight_kg <= 999.9)),
                    order_index INTEGER NOT NULL CHECK(order_index >= 0),
                    FOREIGN KEY (workout_id) REFERENCES simple_workouts(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            
            // Insert test data without created_at
            execSQL(
                """
                INSERT INTO simple_exercises (id, workout_id, name, reps, rpe, sets, weight_kg, order_index)
                VALUES ('exercise2', 'workout2', 'Squats', 12, 9, 4, 100.5, 0)
                """.trimIndent()
            )
            close()
        }

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify the migration added all missing columns including created_at
        migratedDb.query("PRAGMA table_info(simple_exercises)").use { cursor ->
            val columnNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(1))
            }
            
            assert(columnNames.contains("photo_url")) { "Missing photo_url column after migration" }
            assert(columnNames.contains("animation_url")) { "Missing animation_url column after migration" }
            assert(columnNames.contains("created_at")) { "Missing created_at column after migration" }
        }

        // Verify data integrity with default created_at
        migratedDb.query("SELECT * FROM simple_exercises WHERE id = 'exercise2'").use { cursor ->
            assert(cursor.moveToFirst()) { "Migrated exercise data not found" }
            
            // Verify existing data is preserved
            assert(cursor.getString(cursor.getColumnIndex("id")) == "exercise2")
            assert(cursor.getString(cursor.getColumnIndex("workout_id")) == "workout2")
            assert(cursor.getString(cursor.getColumnIndex("name")) == "Squats")
            assert(cursor.getInt(cursor.getColumnIndex("reps")) == 12)
            assert(cursor.getDouble(cursor.getColumnIndex("rpe")) == 9.0) // Should be REAL now
            assert(cursor.getInt(cursor.getColumnIndex("sets")) == 4)
            assert(cursor.getDouble(cursor.getColumnIndex("weight_kg")) == 100.5)
            assert(cursor.getInt(cursor.getColumnIndex("order_index")) == 0)
            
            // Verify new columns are null (as expected for migrated data)
            assert(cursor.isNull(cursor.getColumnIndex("photo_url"))) { "photo_url should be null for migrated data" }
            assert(cursor.isNull(cursor.getColumnIndex("animation_url"))) { "animation_url should be null for migrated data" }
            
            // Verify created_at was backfilled with a timestamp (not null and not empty)
            val createdAt = cursor.getString(cursor.getColumnIndex("created_at"))
            assert(!cursor.isNull(cursor.getColumnIndex("created_at"))) { "created_at should have been backfilled" }
            assert(createdAt.isNotEmpty()) { "created_at should not be empty" }
            // Should be a valid timestamp format (basic check)
            assert(createdAt.contains("-") && createdAt.contains(":")) { "created_at should be a valid timestamp format" }
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_correctsRpeDataType() {
        // Create database version 10 with SimpleExercise data containing RPE values
        val db = helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                """
                INSERT INTO simple_workouts (id, user_id, name, description, created_at, updated_at, is_synced, sync_version)
                VALUES ('workout1', 'user1', 'Test Workout', 'Description', '2024-01-01T10:00:00Z', '2024-01-01T10:00:00Z', 0, 0)
                """.trimIndent()
            )
            
            // Insert test data with various RPE values (as INTEGER in pre-migration)
            execSQL(
                """
                INSERT INTO simple_exercises (id, workout_id, name, reps, rpe, sets, weight_kg, order_index, created_at)
                VALUES ('exercise1', 'workout1', 'Squats', 8, 9, 4, 100.5, 0, '2024-01-01T10:00:00Z')
                """.trimIndent()
            )
            
            execSQL(
                """
                INSERT INTO simple_exercises (id, workout_id, name, reps, rpe, sets, weight_kg, order_index, created_at)
                VALUES ('exercise2', 'workout1', 'Bench Press', 5, NULL, 3, 80.0, 1, '2024-01-01T10:00:00Z')
                """.trimIndent()
            )
            close()
        }

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify RPE data type conversion worked correctly
        migratedDb.query("SELECT id, rpe FROM simple_exercises ORDER BY order_index").use { cursor ->
            assert(cursor.moveToFirst()) { "No migrated data found" }
            
            // First exercise - RPE should be converted to REAL
            assert(cursor.getString(0) == "exercise1")
            assert(cursor.getDouble(1) == 9.0) { "RPE not converted to REAL correctly" }
            
            // Second exercise - NULL RPE should remain NULL
            assert(cursor.moveToNext()) { "Second exercise not found" }
            assert(cursor.getString(0) == "exercise2")
            assert(cursor.isNull(1)) { "NULL RPE should remain NULL after migration" }
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_preservesIndices() {
        // Create database version 10
        val db = helper.createDatabase(TEST_DB, 10)
        db.close()

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify all expected indices exist
        migratedDb.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='simple_exercises'").use { cursor ->
            val indexNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                indexNames.add(cursor.getString(0))
            }
            
            // Verify indices that should exist after migration
            assert(indexNames.contains("index_simple_exercises_workout_id")) { "Missing workout_id index" }
            assert(indexNames.contains("index_simple_exercises_workout_id_order_index")) { "Missing workout_id_order_index index" }
            assert(indexNames.contains("index_simple_exercises_name")) { "Missing name index" }
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_handlesEmptyTable() {
        // Create database version 10 with empty simple_exercises table
        val db = helper.createDatabase(TEST_DB, 10)
        db.close()

        // Run the migration on empty table
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify table structure is correct even with no data
        migratedDb.query("PRAGMA table_info(simple_exercises)").use { cursor ->
            val columnNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(1))
            }
            
            assert(columnNames.contains("photo_url")) { "Missing photo_url column in empty table migration" }
            assert(columnNames.contains("animation_url")) { "Missing animation_url column in empty table migration" }
            assert(columnNames.contains("created_at")) { "Missing created_at column in empty table migration" }
        }

        // Verify table is still empty
        migratedDb.query("SELECT COUNT(*) FROM simple_exercises").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 0) { "Table should be empty after migration" }
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_handlesNonExistentTable() {
        // Create database version 10 but drop simple_exercises table to test edge case
        val db = helper.createDatabase(TEST_DB, 10).apply {
            execSQL("DROP TABLE IF EXISTS simple_exercises")
            close()
        }

        // Migration should handle non-existent table gracefully (skip the fix)
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify no errors occurred and database is still functional
        migratedDb.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            val tableNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                tableNames.add(cursor.getString(0))
            }
            // simple_exercises should not exist (was dropped and migration skipped it)
            assert(!tableNames.contains("simple_exercises")) { "simple_exercises table should not exist" }
        }

        migratedDb.close()
    }
    
    @Test
    @Throws(IOException::class)
    fun migrate10To11_handlesComplexScenario() {
        // Test the most complex scenario: mix of missing columns and existing data
        val db = helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                """
                INSERT INTO simple_workouts (id, user_id, name, description, created_at, updated_at, is_synced, sync_version)
                VALUES ('workout3', 'user3', 'Complex Workout', 'Description', '2024-01-01T12:00:00Z', '2024-01-01T12:00:00Z', 0, 0)
                """.trimIndent()
            )
            
            // Simulate a table with some columns missing (like a real production database)
            execSQL("DROP TABLE IF EXISTS simple_exercises")
            execSQL(
                """
                CREATE TABLE simple_exercises (
                    id TEXT PRIMARY KEY NOT NULL,
                    workout_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    reps INTEGER NOT NULL,
                    rpe INTEGER,
                    sets INTEGER NOT NULL,
                    weight_kg REAL,
                    order_index INTEGER NOT NULL
                )
                """.trimIndent()
            )
            
            // Insert data with various edge cases
            execSQL(
                """
                INSERT INTO simple_exercises (id, workout_id, name, reps, rpe, sets, weight_kg, order_index)
                VALUES 
                ('ex1', 'workout3', 'Deadlifts', 5, 10, 3, 140.0, 0),
                ('ex2', 'workout3', 'Planks', 30, NULL, 3, 0.0, 1),
                ('ex3', 'workout3', 'Curls', 15, 7, 2, 25.5, 2)
                """.trimIndent()
            )
            close()
        }

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify all columns exist
        migratedDb.query("PRAGMA table_info(simple_exercises)").use { cursor ->
            val columnNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(1))
            }
            
            val expectedColumns = listOf(
                "id", "workout_id", "name", "reps", "rpe", "sets", 
                "weight_kg", "order_index", "photo_url", "animation_url", "created_at"
            )
            
            for (column in expectedColumns) {
                assert(columnNames.contains(column)) { "Missing column: $column" }
            }
        }

        // Verify all data is preserved with correct types
        migratedDb.query("SELECT * FROM simple_exercises ORDER BY order_index").use { cursor ->
            assert(cursor.count == 3) { "Expected 3 exercises, got ${cursor.count}" }
            
            // First exercise
            assert(cursor.moveToFirst())
            assert(cursor.getString(cursor.getColumnIndex("id")) == "ex1")
            assert(cursor.getDouble(cursor.getColumnIndex("rpe")) == 10.0) // Converted to REAL
            assert(!cursor.isNull(cursor.getColumnIndex("created_at"))) // Default added
            
            // Second exercise (NULL RPE)
            assert(cursor.moveToNext())
            assert(cursor.getString(cursor.getColumnIndex("id")) == "ex2")
            assert(cursor.isNull(cursor.getColumnIndex("rpe"))) // NULL preserved
            
            // Third exercise
            assert(cursor.moveToNext())
            assert(cursor.getString(cursor.getColumnIndex("id")) == "ex3")
            assert(cursor.getDouble(cursor.getColumnIndex("rpe")) == 7.0) // Converted to REAL
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_currentTimestampDefaultWorks() {
        // Create database version 10 and migrate to 11
        val db = helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                """
                INSERT INTO simple_workouts (id, user_id, name, description, created_at, updated_at, is_synced, sync_version)
                VALUES ('workout_test', 'user_test', 'Test Workout', 'Description', '2024-01-01T10:00:00Z', '2024-01-01T10:00:00Z', 0, 0)
                """.trimIndent()
            )
            close()
        }

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Test that new inserts get CURRENT_TIMESTAMP automatically
        migratedDb.execSQL(
            """
            INSERT INTO simple_exercises (id, workout_id, name, reps, rpe, sets, weight_kg, order_index, photo_url, animation_url)
            VALUES ('new_exercise', 'workout_test', 'Test Exercise', 10, 8.0, 3, 50.0, 0, NULL, NULL)
            """.trimIndent()
        )

        // Verify the new exercise got a created_at timestamp automatically
        migratedDb.query("SELECT created_at FROM simple_exercises WHERE id = 'new_exercise'").use { cursor ->
            assert(cursor.moveToFirst()) { "New exercise not found" }
            val createdAt = cursor.getString(0)
            assert(!cursor.isNull(0)) { "created_at should have CURRENT_TIMESTAMP default" }
            assert(createdAt.isNotEmpty()) { "created_at should not be empty" }
            // Should be a valid timestamp format
            assert(createdAt.contains("-") && createdAt.contains(":")) { "created_at should be a valid timestamp format" }
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_fixesWorkoutsTableUndefinedValues() {
        // Create database version 10 with workouts table containing 'undefined' values
        val db = helper.createDatabase(TEST_DB, 10).apply {
            // Create workouts table with some records having 'undefined' created_at
            execSQL(
                """
                INSERT INTO workouts (id, user_id, name, date, exercises_json, status, created_at, updated_at, is_synced, sync_version)
                VALUES 
                ('workout1', 'user1', 'Test Workout 1', '2024-01-01', '[]', 'COMPLETED', 'undefined', '2024-01-01T10:00:00Z', 0, 0),
                ('workout2', 'user2', 'Test Workout 2', '2024-01-02', '[]', 'COMPLETED', '2024-01-02T11:00:00Z', '2024-01-02T11:00:00Z', 0, 0)
                """.trimIndent()
            )
            close()
        }

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify the migration fixed 'undefined' values and preserved good ones
        migratedDb.query("SELECT id, created_at FROM workouts ORDER BY id").use { cursor ->
            assert(cursor.count == 2) { "Expected 2 workouts, got ${cursor.count}" }
            
            // First workout - should have 'undefined' replaced with proper timestamp
            assert(cursor.moveToFirst())
            assert(cursor.getString(cursor.getColumnIndex("id")) == "workout1")
            val createdAt1 = cursor.getString(cursor.getColumnIndex("created_at"))
            assert(createdAt1 != "undefined") { "created_at should not be 'undefined' after migration" }
            assert(createdAt1.isNotEmpty()) { "created_at should not be empty" }
            assert(createdAt1.contains("-") && createdAt1.contains(":")) { "created_at should be a valid timestamp format" }
            
            // Second workout - should preserve existing valid timestamp
            assert(cursor.moveToNext())
            assert(cursor.getString(cursor.getColumnIndex("id")) == "workout2")
            val createdAt2 = cursor.getString(cursor.getColumnIndex("created_at"))
            assert(createdAt2 == "2024-01-02T11:00:00Z") { "Valid created_at should be preserved" }
        }

        // Verify the table has proper DEFAULT CURRENT_TIMESTAMP
        migratedDb.query("PRAGMA table_info(workouts)").use { cursor ->
            val columnNames = mutableListOf<String>()
            val columnDefaults = mutableListOf<String?>()
            
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(1)) // Column name
                columnDefaults.add(cursor.getString(4)) // Default value
            }
            
            val createdAtIndex = columnNames.indexOf("created_at")
            assert(createdAtIndex >= 0) { "created_at column should exist" }
            val defaultValue = columnDefaults[createdAtIndex]
            assert(defaultValue == "CURRENT_TIMESTAMP") { "created_at should have CURRENT_TIMESTAMP default, got: $defaultValue" }
        }

        // Test that new inserts get CURRENT_TIMESTAMP automatically
        migratedDb.execSQL(
            """
            INSERT INTO workouts (id, user_id, name, date, exercises_json, status, updated_at, is_synced, sync_version)
            VALUES ('new_workout', 'user_test', 'Test Workout', '2024-01-03', '[]', 'COMPLETED', datetime('now'), 0, 0)
            """.trimIndent()
        )

        migratedDb.query("SELECT created_at FROM workouts WHERE id = 'new_workout'").use { cursor ->
            assert(cursor.moveToFirst()) { "New workout not found" }
            val createdAt = cursor.getString(0)
            assert(!cursor.isNull(0)) { "created_at should have CURRENT_TIMESTAMP default" }
            assert(createdAt.isNotEmpty()) { "created_at should not be empty" }
            assert(createdAt != "undefined") { "created_at should not be 'undefined'" }
        }

        migratedDb.close()
    }
    
    @Test
    @Throws(IOException::class)
    fun migrate10To11_handlesWorkoutsTableWithoutUndefinedValues() {
        // Create database version 10 with workouts table having only valid timestamps
        val db = helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                """
                INSERT INTO workouts (id, user_id, name, date, exercises_json, status, created_at, updated_at, is_synced, sync_version)
                VALUES 
                ('workout1', 'user1', 'Good Workout 1', '2024-01-01', '[]', 'COMPLETED', '2024-01-01T10:00:00Z', '2024-01-01T10:00:00Z', 0, 0),
                ('workout2', 'user2', 'Good Workout 2', '2024-01-02', '[]', 'COMPLETED', '2024-01-02T11:00:00Z', '2024-01-02T11:00:00Z', 0, 0)
                """.trimIndent()
            )
            close()
        }

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify all valid timestamps are preserved
        migratedDb.query("SELECT id, created_at FROM workouts ORDER BY id").use { cursor ->
            assert(cursor.count == 2) { "Expected 2 workouts, got ${cursor.count}" }
            
            assert(cursor.moveToFirst())
            assert(cursor.getString(cursor.getColumnIndex("id")) == "workout1")
            assert(cursor.getString(cursor.getColumnIndex("created_at")) == "2024-01-01T10:00:00Z")
            
            assert(cursor.moveToNext())
            assert(cursor.getString(cursor.getColumnIndex("id")) == "workout2")
            assert(cursor.getString(cursor.getColumnIndex("created_at")) == "2024-01-02T11:00:00Z")
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_fixesUserProfilesTableUndefinedValues() {
        // Create database version 10 with user_profiles table containing 'undefined' values
        val db = helper.createDatabase(TEST_DB, 10).apply {
            // Create user_profiles table with some records having 'undefined' values
            execSQL(
                """
                INSERT INTO user_profiles (
                    id, user_id, display_name, age, weight_kg, height_cm, fitness_level, goals,
                    available_equipment, workout_frequency, preferred_workout_duration, 
                    created_at, updated_at, is_synced, sync_version
                )
                VALUES 
                ('profile1', 'undefined', 'User One', 25, 70.5, 'undefined', 'build_muscle', 'undefined', 'home', 3, 'undefined', 'undefined', '2024-01-01T10:00:00Z', 0, 1),
                ('profile2', 'user2', 'undefined', 30, 'undefined', 180.0, 'undefined', 'weight_loss', 'gym', 'undefined', 60, '2024-01-02T11:00:00Z', 'undefined', 0, 1)
                """.trimIndent()
            )
            close()
        }

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify the migration fixed 'undefined' values
        migratedDb.query("SELECT * FROM user_profiles ORDER BY id").use { cursor ->
            assert(cursor.count == 2) { "Expected 2 profiles, got ${cursor.count}" }
            
            // First profile
            assert(cursor.moveToFirst())
            assert(cursor.getString(cursor.getColumnIndex("id")) == "profile1")
            assert(cursor.getString(cursor.getColumnIndex("user_id")) == "migrated_user_profile1") { "user_id should be fixed from 'undefined'" }
            assert(cursor.getString(cursor.getColumnIndex("display_name")) == "User One") { "display_name should be preserved" }
            assert(cursor.getInt(cursor.getColumnIndex("age")) == 25) { "age should be preserved" }
            assert(cursor.getDouble(cursor.getColumnIndex("weight_kg")) == 70.5) { "weight_kg should be preserved" }
            assert(cursor.isNull(cursor.getColumnIndex("height_cm"))) { "height_cm 'undefined' should become null" }
            assert(cursor.getString(cursor.getColumnIndex("fitness_level")) == "build_muscle") { "fitness_level should be preserved" }
            assert(cursor.isNull(cursor.getColumnIndex("goals"))) { "goals 'undefined' should become null" }
            assert(cursor.getString(cursor.getColumnIndex("available_equipment")) == "home") { "available_equipment should be preserved" }
            assert(cursor.getInt(cursor.getColumnIndex("workout_frequency")) == 3) { "workout_frequency should be preserved" }
            assert(cursor.isNull(cursor.getColumnIndex("preferred_workout_duration"))) { "preferred_workout_duration 'undefined' should become null" }
            
            val createdAt1 = cursor.getString(cursor.getColumnIndex("created_at"))
            assert(createdAt1 != "undefined") { "created_at should not be 'undefined' after migration" }
            assert(createdAt1.isNotEmpty()) { "created_at should not be empty" }
            
            // Second profile  
            assert(cursor.moveToNext())
            assert(cursor.getString(cursor.getColumnIndex("id")) == "profile2")
            assert(cursor.getString(cursor.getColumnIndex("user_id")) == "user2") { "valid user_id should be preserved" }
            assert(cursor.getString(cursor.getColumnIndex("display_name")) == "User") { "display_name 'undefined' should become 'User'" }
            assert(cursor.getInt(cursor.getColumnIndex("age")) == 30) { "age should be preserved" }
            assert(cursor.isNull(cursor.getColumnIndex("weight_kg"))) { "weight_kg 'undefined' should become null" }
            assert(cursor.getDouble(cursor.getColumnIndex("height_cm")) == 180.0) { "height_cm should be preserved" }
            assert(cursor.isNull(cursor.getColumnIndex("fitness_level"))) { "fitness_level 'undefined' should become null" }
            assert(cursor.getString(cursor.getColumnIndex("goals")) == "weight_loss") { "goals should be preserved" }
            assert(cursor.getString(cursor.getColumnIndex("available_equipment")) == "gym") { "available_equipment should be preserved" }
            assert(cursor.isNull(cursor.getColumnIndex("workout_frequency"))) { "workout_frequency 'undefined' should become null" }
            assert(cursor.getInt(cursor.getColumnIndex("preferred_workout_duration")) == 60) { "preferred_workout_duration should be preserved" }
            
            val createdAt2 = cursor.getString(cursor.getColumnIndex("created_at"))
            assert(createdAt2 == "2024-01-02T11:00:00Z") { "valid created_at should be preserved" }
            
            val updatedAt2 = cursor.getString(cursor.getColumnIndex("updated_at"))
            assert(updatedAt2 != "undefined") { "updated_at should not be 'undefined' after migration" }
            assert(updatedAt2.isNotEmpty()) { "updated_at should not be empty" }
        }

        // Verify the table has proper DEFAULT CURRENT_TIMESTAMP for new records
        migratedDb.query("PRAGMA table_info(user_profiles)").use { cursor ->
            val columnNames = mutableListOf<String>()
            val columnDefaults = mutableListOf<String?>()
            
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(1)) // Column name
                columnDefaults.add(cursor.getString(4)) // Default value
            }
            
            val createdAtIndex = columnNames.indexOf("created_at")
            assert(createdAtIndex >= 0) { "created_at column should exist" }
            val createdAtDefault = columnDefaults[createdAtIndex]
            assert(createdAtDefault == "CURRENT_TIMESTAMP") { "created_at should have CURRENT_TIMESTAMP default, got: $createdAtDefault" }
            
            val updatedAtIndex = columnNames.indexOf("updated_at")
            assert(updatedAtIndex >= 0) { "updated_at column should exist" }
            val updatedAtDefault = columnDefaults[updatedAtIndex]
            assert(updatedAtDefault == "CURRENT_TIMESTAMP") { "updated_at should have CURRENT_TIMESTAMP default, got: $updatedAtDefault" }
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_handlesUserProfilesTableWithoutUndefinedValues() {
        // Create database version 10 with user_profiles table having only valid values
        val db = helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                """
                INSERT INTO user_profiles (
                    id, user_id, display_name, age, weight_kg, height_cm, fitness_level, goals,
                    available_equipment, workout_frequency, preferred_workout_duration, 
                    created_at, updated_at, is_synced, sync_version
                )
                VALUES 
                ('profile1', 'user1', 'John Doe', 28, 75.0, 178.5, 'intermediate', 'strength', 'gym', 4, 45, '2024-01-01T10:00:00Z', '2024-01-01T10:00:00Z', 0, 1),
                ('profile2', 'user2', 'Jane Smith', 32, 65.5, 165.0, 'beginner', 'cardio', 'home', 3, 30, '2024-01-02T11:00:00Z', '2024-01-02T11:00:00Z', 1, 1)
                """.trimIndent()
            )
            close()
        }

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify all valid values are preserved
        migratedDb.query("SELECT * FROM user_profiles ORDER BY id").use { cursor ->
            assert(cursor.count == 2) { "Expected 2 profiles, got ${cursor.count}" }
            
            assert(cursor.moveToFirst())
            assert(cursor.getString(cursor.getColumnIndex("id")) == "profile1")
            assert(cursor.getString(cursor.getColumnIndex("user_id")) == "user1")
            assert(cursor.getString(cursor.getColumnIndex("display_name")) == "John Doe")
            assert(cursor.getInt(cursor.getColumnIndex("age")) == 28)
            assert(cursor.getDouble(cursor.getColumnIndex("weight_kg")) == 75.0)
            assert(cursor.getDouble(cursor.getColumnIndex("height_cm")) == 178.5)
            assert(cursor.getString(cursor.getColumnIndex("fitness_level")) == "intermediate")
            assert(cursor.getString(cursor.getColumnIndex("goals")) == "strength")
            assert(cursor.getString(cursor.getColumnIndex("available_equipment")) == "gym")
            assert(cursor.getInt(cursor.getColumnIndex("workout_frequency")) == 4)
            assert(cursor.getInt(cursor.getColumnIndex("preferred_workout_duration")) == 45)
            assert(cursor.getString(cursor.getColumnIndex("created_at")) == "2024-01-01T10:00:00Z")
            assert(cursor.getString(cursor.getColumnIndex("updated_at")) == "2024-01-01T10:00:00Z")
            
            assert(cursor.moveToNext())
            assert(cursor.getString(cursor.getColumnIndex("id")) == "profile2")
            assert(cursor.getString(cursor.getColumnIndex("user_id")) == "user2")
            assert(cursor.getString(cursor.getColumnIndex("display_name")) == "Jane Smith")
            assert(cursor.getInt(cursor.getColumnIndex("age")) == 32)
            assert(cursor.getDouble(cursor.getColumnIndex("weight_kg")) == 65.5)
            assert(cursor.getDouble(cursor.getColumnIndex("height_cm")) == 165.0)
            assert(cursor.getString(cursor.getColumnIndex("fitness_level")) == "beginner")
            assert(cursor.getString(cursor.getColumnIndex("goals")) == "cardio")
            assert(cursor.getString(cursor.getColumnIndex("available_equipment")) == "home")
            assert(cursor.getInt(cursor.getColumnIndex("workout_frequency")) == 3)
            assert(cursor.getInt(cursor.getColumnIndex("preferred_workout_duration")) == 30)
            assert(cursor.getString(cursor.getColumnIndex("created_at")) == "2024-01-02T11:00:00Z")
            assert(cursor.getString(cursor.getColumnIndex("updated_at")) == "2024-01-02T11:00:00Z")
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_userProfilesCurrentTimestampDefaultWorks() {
        // Create database version 10 and migrate to 11
        val db = helper.createDatabase(TEST_DB, 10)
        db.close()

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Test that new inserts get CURRENT_TIMESTAMP automatically
        migratedDb.execSQL(
            """
            INSERT INTO user_profiles (id, user_id, display_name, age, weight_kg, height_cm, fitness_level, goals, available_equipment, workout_frequency, preferred_workout_duration, is_synced, sync_version)
            VALUES ('new_profile', 'test_user', 'Test User', 25, 70.0, 175.0, 'beginner', 'fitness', 'home', 3, 30, 0, 1)
            """.trimIndent()
        )

        // Verify the new profile got created_at and updated_at timestamps automatically
        migratedDb.query("SELECT created_at, updated_at FROM user_profiles WHERE id = 'new_profile'").use { cursor ->
            assert(cursor.moveToFirst()) { "New profile not found" }
            
            val createdAt = cursor.getString(0)
            val updatedAt = cursor.getString(1)
            
            assert(!cursor.isNull(0)) { "created_at should have CURRENT_TIMESTAMP default" }
            assert(!cursor.isNull(1)) { "updated_at should have CURRENT_TIMESTAMP default" }
            assert(createdAt.isNotEmpty()) { "created_at should not be empty" }
            assert(updatedAt.isNotEmpty()) { "updated_at should not be empty" }
            assert(createdAt != "undefined") { "created_at should not be 'undefined'" }
            assert(updatedAt != "undefined") { "updated_at should not be 'undefined'" }
            // Should be valid timestamp formats
            assert(createdAt.contains("-") && createdAt.contains(":")) { "created_at should be a valid timestamp format" }
            assert(updatedAt.contains("-") && updatedAt.contains(":")) { "updated_at should be a valid timestamp format" }
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_handlesEmptyUserProfilesTable() {
        // Create database version 10 with empty user_profiles table
        val db = helper.createDatabase(TEST_DB, 10)
        db.close()

        // Run the migration on empty table
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Verify table structure is correct even with no data
        migratedDb.query("PRAGMA table_info(user_profiles)").use { cursor ->
            val columnNames = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(1))
            }
            
            val expectedColumns = listOf(
                "id", "user_id", "display_name", "age", "weight_kg", "height_cm", 
                "fitness_level", "goals", "available_equipment", "workout_frequency", 
                "preferred_workout_duration", "completed_at", "created_at", "updated_at", "is_synced", "sync_version"
            )
            
            for (column in expectedColumns) {
                assert(columnNames.contains(column)) { "Missing column: $column" }
            }
        }

        // Verify table is still empty
        migratedDb.query("SELECT COUNT(*) FROM user_profiles").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 0) { "Table should be empty after migration" }
        }

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_completedAtColumnWorksCorrectly() {
        // Create database version 10 and migrate to 11
        val db = helper.createDatabase(TEST_DB, 10)
        db.close()

        // Run the migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // Test inserting a profile without completed_at (incomplete onboarding)
        migratedDb.execSQL(
            """
            INSERT INTO user_profiles (id, user_id, display_name, age, weight_kg, height_cm, fitness_level, goals, available_equipment, workout_frequency, preferred_workout_duration, completed_at, is_synced, sync_version)
            VALUES ('incomplete_profile', 'test_user_1', 'Test User 1', 25, 70.0, 175.0, 'beginner', 'fitness', 'home', 3, 30, NULL, 0, 1)
            """.trimIndent()
        )

        // Test inserting a profile with completed_at (completed onboarding)
        migratedDb.execSQL(
            """
            INSERT INTO user_profiles (id, user_id, display_name, age, weight_kg, height_cm, fitness_level, goals, available_equipment, workout_frequency, preferred_workout_duration, completed_at, is_synced, sync_version)
            VALUES ('complete_profile', 'test_user_2', 'Test User 2', 30, 65.0, 170.0, 'intermediate', 'strength', 'gym', 4, 45, datetime('now'), 0, 1)
            """.trimIndent()
        )

        // Test queries that UserProfileDao would use
        // Check incomplete profiles (completed_at IS NULL)
        migratedDb.query("SELECT COUNT(*) FROM user_profiles WHERE completed_at IS NULL").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 1) { "Should have 1 incomplete profile" }
        }

        // Check completed profiles (completed_at IS NOT NULL)
        migratedDb.query("SELECT COUNT(*) FROM user_profiles WHERE completed_at IS NOT NULL").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 1) { "Should have 1 completed profile" }
        }

        // Check hasCompletedProfile functionality
        migratedDb.query("SELECT EXISTS(SELECT 1 FROM user_profiles WHERE user_id = 'test_user_1' AND completed_at IS NOT NULL)").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 0) { "test_user_1 should not have completed profile" }
        }

        migratedDb.query("SELECT EXISTS(SELECT 1 FROM user_profiles WHERE user_id = 'test_user_2' AND completed_at IS NOT NULL)").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 1) { "test_user_2 should have completed profile" }
        }

        // Test markProfileAsCompleted functionality
        migratedDb.execSQL("UPDATE user_profiles SET completed_at = datetime('now') WHERE user_id = 'test_user_1'")
        
        migratedDb.query("SELECT COUNT(*) FROM user_profiles WHERE completed_at IS NOT NULL").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 2) { "Should now have 2 completed profiles" }
        }

        migratedDb.close()
    }
} 