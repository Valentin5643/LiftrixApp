package com.example.liftrix.data.local.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.data.local.LiftrixDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Test for Migration_7_to_8 to ensure schema changes are applied correctly
 */
@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LiftrixDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate7To8() {
        var db = helper.createDatabase(TEST_DB, 7).apply {
            // Database has schema version 7. Insert some data using SQL queries.
            // You cannot use DAO classes because they expect the latest schema.
            
            // Insert test data for existing tables that should exist in version 7
            execSQL("INSERT INTO simple_workouts (id, user_id, name, description, created_at, updated_at, is_synced, sync_version) VALUES ('sw1', 'user1', 'Test Workout', 'Test Description', '2023-01-01T00:00:00Z', '2023-01-01T00:00:00Z', 0, 1)")
            
            close()
        }

        // Re-open the database with version 8 and provide MIGRATION_7_8 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        // Validate that the new tables were created with correct schema
        db.query("SELECT * FROM workout_templates LIMIT 0").use { cursor ->
            val columnNames = cursor.columnNames
            assert(columnNames.contains("id"))
            assert(columnNames.contains("user_id"))
            assert(columnNames.contains("name"))
            assert(columnNames.contains("description"))
            assert(columnNames.contains("template_exercises_json"))
            assert(columnNames.contains("estimated_duration_minutes"))
            assert(columnNames.contains("difficulty_level"))
            assert(columnNames.contains("tags"))
            assert(columnNames.contains("usage_count"))
            assert(columnNames.contains("last_used_at"))
            assert(columnNames.contains("created_at"))
            assert(columnNames.contains("updated_at"))
            assert(columnNames.contains("is_synced"))
            assert(columnNames.contains("sync_version"))
        }

        db.query("SELECT * FROM workouts LIMIT 0").use { cursor ->
            val columnNames = cursor.columnNames
            assert(columnNames.contains("id"))
            assert(columnNames.contains("user_id"))
            assert(columnNames.contains("name"))
            assert(columnNames.contains("date"))
            assert(columnNames.contains("exercises_json"))
            assert(columnNames.contains("status"))
            assert(columnNames.contains("start_time"))
            assert(columnNames.contains("end_time"))
            assert(columnNames.contains("notes"))
            assert(columnNames.contains("template_id"))
            assert(columnNames.contains("created_at"))
            assert(columnNames.contains("updated_at"))
            assert(columnNames.contains("is_synced"))
            assert(columnNames.contains("sync_version"))
        }

        db.query("SELECT * FROM daily_workouts LIMIT 0").use { cursor ->
            val columnNames = cursor.columnNames
            assert(columnNames.contains("id"))
            assert(columnNames.contains("user_id"))
            assert(columnNames.contains("name"))
            assert(columnNames.contains("date"))
            assert(columnNames.contains("template_id"))
            assert(columnNames.contains("exercises_json"))
            assert(columnNames.contains("status"))
            assert(columnNames.contains("start_time"))
            assert(columnNames.contains("end_time"))
            assert(columnNames.contains("duration_minutes"))
            assert(columnNames.contains("total_volume_kg"))
            assert(columnNames.contains("total_sets"))
            assert(columnNames.contains("total_reps"))
            assert(columnNames.contains("notes"))
            assert(columnNames.contains("rating"))
            assert(columnNames.contains("created_at"))
            assert(columnNames.contains("updated_at"))
            assert(columnNames.contains("is_synced"))
            assert(columnNames.contains("sync_version"))
        }

        db.query("SELECT * FROM custom_exercises LIMIT 0").use { cursor ->
            val columnNames = cursor.columnNames
            assert(columnNames.contains("id"))
            assert(columnNames.contains("user_id"))
            assert(columnNames.contains("name"))
            assert(columnNames.contains("primary_muscle_group")) // Verify correct column name
            assert(columnNames.contains("equipment"))
            assert(columnNames.contains("secondary_muscle_groups"))
            assert(columnNames.contains("difficulty"))
            assert(columnNames.contains("notes"))
            assert(columnNames.contains("created_at"))
            assert(columnNames.contains("updated_at"))
            assert(columnNames.contains("is_synced"))
            assert(columnNames.contains("sync_version"))
        }

        // Verify indices were created successfully
        verifyIndexExists(db, "index_workout_templates_user_id")
        verifyIndexExists(db, "index_workout_templates_name_user_id")
        verifyIndexExists(db, "index_workout_templates_created_at")
        
        verifyIndexExists(db, "index_workouts_user_id")
        verifyIndexExists(db, "index_workouts_date")
        verifyIndexExists(db, "index_workouts_status")
        
        verifyIndexExists(db, "index_daily_workouts_user_id")
        verifyIndexExists(db, "index_daily_workouts_date_user_id")
        verifyIndexExists(db, "index_daily_workouts_template_id")
        verifyIndexExists(db, "index_daily_workouts_status")
        verifyIndexExists(db, "index_daily_workouts_created_at")
        
        verifyIndexExists(db, "index_custom_exercises_user_id")
        verifyIndexExists(db, "index_custom_exercises_name_user_id")
        verifyIndexExists(db, "index_custom_exercises_primary_muscle_group")
        verifyIndexExists(db, "index_custom_exercises_equipment")

        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMigrationWithExistingData() {
        // Test that migration preserves existing data
        var db = helper.createDatabase(TEST_DB, 7).apply {
            // Insert test data that should be preserved
            execSQL("INSERT INTO simple_workouts (id, user_id, name, description, created_at, updated_at, is_synced, sync_version) VALUES ('sw1', 'user1', 'Existing Workout', 'This should remain', '2023-01-01T00:00:00Z', '2023-01-01T00:00:00Z', 0, 1)")
            close()
        }

        // Run migration
        db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        // Verify existing data is preserved
        db.query("SELECT * FROM simple_workouts WHERE id = 'sw1'").use { cursor ->
            assert(cursor.moveToFirst())
            val nameIndex = cursor.getColumnIndex("name")
            assert(cursor.getString(nameIndex) == "Existing Workout")
        }

        db.close()
    }

    @Test
    fun testFullDatabaseCreation() {
        // Test that we can create a new database from scratch with version 8
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LiftrixDatabase::class.java
        ).build()

        // Verify we can access all DAOs without crashes
        val workoutDao = db.workoutDao()
        val userProfileDao = db.userProfileDao()
        val customExerciseDao = db.customExerciseDao()
        val workoutTemplateDao = db.workoutTemplateDao()
        val dailyWorkoutDao = db.dailyWorkoutDao()

        // Basic smoke test - these should not throw exceptions
        assert(workoutDao != null)
        assert(userProfileDao != null)
        assert(customExerciseDao != null)
        assert(workoutTemplateDao != null)
        assert(dailyWorkoutDao != null)

        db.close()
    }

    private fun verifyIndexExists(db: androidx.sqlite.db.SupportSQLiteDatabase, indexName: String) {
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='$indexName'").use { cursor ->
            assert(cursor.moveToFirst()) { "Index $indexName was not created" }
        }
    }
}