package com.example.liftrix.data.local.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.migration.MIGRATION_9_10
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class Migration9To10Test {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LiftrixDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate9To10_WithCorruptedWorkoutsTable_RepairsSuccessfully() {
        // Create database version 9 with corrupted workouts table
        var db = helper.createDatabase(TEST_DB, 9).apply {
            // Create corrupted workouts table with wrong schema (INTEGER id, INTEGER timestamps)
            execSQL("""
                CREATE TABLE workouts (
                    id INTEGER PRIMARY KEY NOT NULL,
                    user_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    date TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    sync_version INTEGER NOT NULL DEFAULT 0
                )
            """)

            // Insert test data with wrong types
            execSQL("""
                INSERT INTO workouts (id, user_id, name, date, status, created_at, updated_at, is_synced, sync_version)
                VALUES (1, 'user1', 'Test Workout', '2023-12-01', 'PLANNED', 1701388800000, 1701388800000, 0, 0)
            """)

            close()
        }

        // Run migration to version 10
        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

        // Verify workouts table schema is corrected
        val workoutsCursor = db.query("PRAGMA table_info(workouts)")
        val columnTypes = mutableMapOf<String, String>()
        
        workoutsCursor.use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            while (cursor.moveToNext()) {
                columnTypes[cursor.getString(nameIndex)] = cursor.getString(typeIndex)
            }
        }

        // Verify correct column types
        assertEquals("TEXT", columnTypes["id"])
        assertEquals("TEXT", columnTypes["user_id"])
        assertEquals("TEXT", columnTypes["name"])
        assertEquals("TEXT", columnTypes["date"])
        assertEquals("TEXT", columnTypes["created_at"])
        assertEquals("TEXT", columnTypes["updated_at"])
        
        // Verify exercises_json column exists
        assertTrue(columnTypes.containsKey("exercises_json"))
        assertEquals("TEXT", columnTypes["exercises_json"])

        // Verify data was migrated
        val dataCursor = db.query("SELECT * FROM workouts WHERE user_id = 'user1'")
        dataCursor.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("1", cursor.getString(cursor.getColumnIndex("id")))
            assertEquals("Test Workout", cursor.getString(cursor.getColumnIndex("name")))
            assertNotNull(cursor.getString(cursor.getColumnIndex("exercises_json")))
        }

        db.close()
    }

    @Test
    fun migrate9To10_WithMissingExercisesJsonColumn_AddsColumn() {
        // Create database version 9 with missing exercises_json column
        var db = helper.createDatabase(TEST_DB, 9).apply {
            execSQL("""
                CREATE TABLE workouts (
                    id TEXT PRIMARY KEY NOT NULL,
                    user_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    date TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    sync_version INTEGER NOT NULL DEFAULT 0
                )
            """)

            execSQL("""
                INSERT INTO workouts (id, user_id, name, date, status, created_at, updated_at)
                VALUES ('1', 'user1', 'Test Workout', '2023-12-01', 'PLANNED', '2023-12-01T10:00:00Z', '2023-12-01T10:00:00Z')
            """)

            close()
        }

        // Run migration
        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

        // Verify exercises_json column was added
        val columnsCursor = db.query("PRAGMA table_info(workouts)")
        val columns = mutableSetOf<String>()
        
        columnsCursor.use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(nameIndex))
            }
        }

        assertTrue(columns.contains("exercises_json"))

        // Verify default value was set
        val dataCursor = db.query("SELECT exercises_json FROM workouts WHERE id = '1'")
        dataCursor.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("[]", cursor.getString(0))
        }

        db.close()
    }

    @Test
    fun migrate9To10_WithSimpleWorkoutsData_PreservesData() {
        // Create database version 9 with simple workouts data
        var db = helper.createDatabase(TEST_DB, 9).apply {
            execSQL("""
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

            execSQL("""
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
                    animation_url TEXT
                )
            """)

            execSQL("""
                INSERT INTO simple_workouts (id, user_id, name, description, created_at, updated_at)
                VALUES ('sw1', 'user1', 'Simple Workout', 'Basic workout', '2023-12-01T10:00:00Z', '2023-12-01T10:00:00Z')
            """)

            execSQL("""
                INSERT INTO simple_exercises (id, workout_id, name, reps, sets, weight_kg, order_index)
                VALUES ('se1', 'sw1', 'Push Ups', 10, 3, 0.0, 1)
            """)

            close()
        }

        // Run migration
        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

        // Verify simple workouts data was preserved
        val workoutsCursor = db.query("SELECT * FROM simple_workouts WHERE id = 'sw1'")
        workoutsCursor.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Simple Workout", cursor.getString(cursor.getColumnIndex("name")))
            assertEquals("Basic workout", cursor.getString(cursor.getColumnIndex("description")))
        }

        // Verify simple exercises data was preserved
        val exercisesCursor = db.query("SELECT * FROM simple_exercises WHERE id = 'se1'")
        exercisesCursor.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Push Ups", cursor.getString(cursor.getColumnIndex("name")))
            assertEquals(10, cursor.getInt(cursor.getColumnIndex("reps")))
            assertEquals(3, cursor.getInt(cursor.getColumnIndex("sets")))
        }

        db.close()
    }

    @Test
    fun migrate9To10_WithTimestampTypeMismatch_ConvertsCorrectly() {
        // Create database version 9 with INTEGER timestamps
        var db = helper.createDatabase(TEST_DB, 9).apply {
            execSQL("""
                CREATE TABLE simple_workouts (
                    id TEXT PRIMARY KEY NOT NULL,
                    user_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    sync_version INTEGER NOT NULL DEFAULT 0
                )
            """)

            // Insert with INTEGER timestamps (milliseconds)
            execSQL("""
                INSERT INTO simple_workouts (id, user_id, name, description, created_at, updated_at)
                VALUES ('sw1', 'user1', 'Test Workout', 'Description', 1701388800000, 1701388800000)
            """)

            close()
        }

        // Run migration
        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

        // Verify timestamps were converted to TEXT format
        val cursor = db.query("SELECT created_at, updated_at FROM simple_workouts WHERE id = 'sw1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            val createdAt = it.getString(0)
            val updatedAt = it.getString(1)
            
            // Verify the timestamp is now in ISO format
            assertTrue(createdAt.contains("-") && createdAt.contains(":"))
            assertTrue(updatedAt.contains("-") && updatedAt.contains(":"))
        }

        db.close()
    }

    @Test
    fun migrate9To10_WithAllTablesPresent_CreatesAllIndices() {
        // Create database version 9 with all tables
        var db = helper.createDatabase(TEST_DB, 9).apply {
            // Create minimal required tables
            execSQL("""
                CREATE TABLE workouts (
                    id TEXT PRIMARY KEY NOT NULL,
                    user_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    date TEXT NOT NULL,
                    exercises_json TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    sync_version INTEGER NOT NULL DEFAULT 0
                )
            """)

            execSQL("""
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

            close()
        }

        // Run migration
        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

        // Verify indices were created
        val indicesCursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'index_%'")
        val indices = mutableSetOf<String>()
        
        indicesCursor.use { cursor ->
            while (cursor.moveToNext()) {
                indices.add(cursor.getString(0))
            }
        }

        // Check for essential indices
        assertTrue(indices.contains("index_workouts_user_id"))
        assertTrue(indices.contains("index_workouts_date"))
        assertTrue(indices.contains("index_simple_workouts_user_id"))

        db.close()
    }

    @Test
    fun migrate9To10_WithEmptyDatabase_CreatesAllTables() {
        // Create empty database version 9
        var db = helper.createDatabase(TEST_DB, 9)
        db.close()

        // Run migration
        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

        // Verify all expected tables were created
        val tablesCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
        val tables = mutableSetOf<String>()
        
        tablesCursor.use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }

        val expectedTables = setOf(
            "workouts", "simple_workouts", "simple_exercises", "exercises",
            "exercise_sets", "exercise_weight_memory", "daily_workouts",
            "workout_templates", "custom_exercises", "user_profiles", "exercise_library"
        )

        expectedTables.forEach { expectedTable ->
            assertTrue("Missing table: $expectedTable", tables.contains(expectedTable))
        }

        db.close()
    }
} 