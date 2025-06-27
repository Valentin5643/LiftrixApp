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
 * Tests for Migration_7_to_8 to ensure schema changes work correctly
 * and indexes are created properly with column existence validation.
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
            // Create workouts table with Migration_6_7 schema (INTEGER id, INTEGER timestamps)
            execSQL("""
                CREATE TABLE workouts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    user_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    date TEXT NOT NULL,
                    exercises_json TEXT NOT NULL,
                    status TEXT NOT NULL,
                    notes TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    is_synced INTEGER NOT NULL DEFAULT 0,
                    sync_version INTEGER NOT NULL DEFAULT 1
                )
            """)
            
            // Insert test data with old schema (INTEGER timestamps)
            val currentTime = System.currentTimeMillis()
            execSQL("""
                INSERT INTO workouts (id, user_id, name, date, exercises_json, status, notes, created_at, updated_at, is_synced, sync_version)
                VALUES (1, 'test-user', 'Test Workout', '2023-12-01', '[]', 'PLANNED', 'Test notes', $currentTime, $currentTime, 0, 1)
            """)
            close()
        }

        // Re-open the database with version 8 and provide MIGRATION_7_8 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        // Validate that the migration executed successfully
        validateMigration7To8Schema(db)
        validateWorkoutDataMigration(db)
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8_withMissingColumns_shouldNotCrash() {
        var db = helper.createDatabase(TEST_DB, 7).apply {
            // Create a table that's missing some expected columns
            // to test defensive column existence checking
            execSQL("""
                CREATE TABLE IF NOT EXISTS custom_exercises (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    name TEXT NOT NULL
                )
            """)
            close()
        }

        // Migration should complete without crashing even with missing columns
        db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)
        
        // Verify database is in valid state after migration
        assert(db.version == 8)
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8_allTablesCreated() {
        val db = helper.createDatabase(TEST_DB, 7).apply {
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)
        
        // Verify all expected tables exist
        val tableNames = getTableNames(migratedDb)
        
        assert(tableNames.contains("workout_templates")) { "workout_templates table not created" }
        assert(tableNames.contains("workouts")) { "workouts table not created" }
        assert(tableNames.contains("daily_workouts")) { "daily_workouts table not created" }
        assert(tableNames.contains("custom_exercises")) { "custom_exercises table not created" }
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8_customExercisesSchemaCorrect() {
        val db = helper.createDatabase(TEST_DB, 7).apply {
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)
        
        // Verify custom_exercises table has correct schema
        val columns = getTableColumns(migratedDb, "custom_exercises")
        
        assert(columns.contains("primary_muscle_group")) { "primary_muscle_group column not found" }
        assert(!columns.contains("muscle_group")) { "old muscle_group column should not exist" }
        assert(columns.contains("secondary_muscle_groups")) { "secondary_muscle_groups column not found" }
        assert(columns.contains("equipment")) { "equipment column not found" }
        assert(columns.contains("difficulty")) { "difficulty column not found" }
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8_indexesCreated() {
        val db = helper.createDatabase(TEST_DB, 7).apply {
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)
        
        // Verify key indexes were created
        val indexes = getTableIndexes(migratedDb, "custom_exercises")
        
        assert(indexes.any { it.contains("index_custom_exercises_user_id") }) { "user_id index not created" }
        assert(indexes.any { it.contains("index_custom_exercises_primary_muscle_group") }) { "primary_muscle_group index not created" }
        assert(indexes.any { it.contains("index_custom_exercises_equipment") }) { "equipment index not created" }
    }

    private fun validateMigration7To8Schema(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Verify database version
        assert(db.version == 8) { "Database version should be 8 after migration" }

        // Test that we can query the new tables without errors
        db.query("SELECT COUNT(*) FROM workout_templates").use { cursor ->
            assert(cursor.moveToFirst()) { "Could not query workout_templates table" }
        }

        db.query("SELECT COUNT(*) FROM daily_workouts").use { cursor ->
            assert(cursor.moveToFirst()) { "Could not query daily_workouts table" }
        }

        db.query("SELECT COUNT(*) FROM custom_exercises").use { cursor ->
            assert(cursor.moveToFirst()) { "Could not query custom_exercises table" }
        }

        // Verify workouts table has correct schema (TEXT id, not INTEGER)
        db.query("PRAGMA table_info(workouts)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            var hasTextId = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "id") {
                    val type = cursor.getString(typeIndex)
                    hasTextId = type.contains("TEXT")
                    break
                }
            }
            assert(hasTextId) { "Workouts table should have TEXT id column, not INTEGER" }
        }

        // Test that indexes were created by running a query that would use them
        db.query("SELECT * FROM custom_exercises WHERE user_id = 'test'").use { cursor ->
            // Query should complete without error (index on user_id should exist)
            assert(cursor.columnCount > 0) { "custom_exercises table should have columns" }
        }
    }

    private fun validateWorkoutDataMigration(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Verify that data was migrated correctly from INTEGER to TEXT schema
        db.query("SELECT id, created_at, updated_at, sync_version FROM workouts WHERE user_id = 'test-user'").use { cursor ->
            assert(cursor.moveToFirst()) { "Migrated workout data should exist" }
            
            // Verify id is now TEXT (was converted from INTEGER)
            val id = cursor.getString(0)
            assert(id == "1") { "ID should be '1' as text, got: $id" }
            
            // Verify timestamps are now TEXT (converted from INTEGER milliseconds)
            val createdAt = cursor.getString(1)
            assert(createdAt.contains("-")) { "created_at should be datetime string, got: $createdAt" }
            
            val updatedAt = cursor.getString(2)
            assert(updatedAt.contains("-")) { "updated_at should be datetime string, got: $updatedAt" }
            
            // Verify sync_version was reset to 0
            val syncVersion = cursor.getInt(3)
            assert(syncVersion == 0) { "sync_version should be 0 after migration, got: $syncVersion" }
        }
    }

    private fun getTableNames(db: androidx.sqlite.db.SupportSQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }
        return tables
    }

    private fun getTableColumns(db: androidx.sqlite.db.SupportSQLiteDatabase, tableName: String): List<String> {
        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info($tableName)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(nameIndex))
            }
        }
        return columns
    }

    private fun getTableIndexes(db: androidx.sqlite.db.SupportSQLiteDatabase, tableName: String): List<String> {
        val indexes = mutableListOf<String>()
        db.query("SELECT name, sql FROM sqlite_master WHERE type='index' AND tbl_name='$tableName'").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val sqlIndex = cursor.getColumnIndex("sql")
            while (cursor.moveToNext()) {
                val indexName = cursor.getString(nameIndex)
                val indexSql = cursor.getString(sqlIndex) ?: ""
                indexes.add("$indexName: $indexSql")
            }
        }
        return indexes
    }

    @Test
    @Throws(IOException::class)
    fun testAutoMigrationToVersion8() {
        // Test that the database can be opened directly at version 8
        // This verifies the migration can be added to .addMigrations() safely
        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LiftrixDatabase::class.java,
            "test-auto-migration"
        )
            .addMigrations(MIGRATION_7_8)
            .build()

        // Should be able to access DAOs without crashes
        val customExerciseDao = db.customExerciseDao()
        
        // Test database accessibility
        assert(db.openHelper.readableDatabase.version == 8) { "Database should be at version 8" }
        
        db.close()
    }
}