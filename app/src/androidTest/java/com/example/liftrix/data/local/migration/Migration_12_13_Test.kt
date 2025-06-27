package com.example.liftrix.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.data.local.LiftrixDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class Migration_12_13_Test {
    
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LiftrixDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate12To13_WithExerciseLibraryLikeSchema_ConvertsToCustomExerciseSchema() {
        // Create database at version 12 with problematic custom_exercises schema
        // (composite PK + ExerciseLibrary columns causing Room validation failures)
        var db = helper.createDatabase(TEST_DB, 12).apply {
            // Create custom_exercises table with ExerciseLibrary-like schema that Room incorrectly expects
            execSQL("""
                CREATE TABLE custom_exercises (
                    equipment TEXT NOT NULL,
                    id TEXT NOT NULL,
                    instructions TEXT,
                    name TEXT NOT NULL,
                    primary_muscle_group TEXT NOT NULL,
                    secondary_muscle_groups TEXT,
                    tags TEXT,
                    thumbnail_url TEXT,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (equipment, id)
                )
            """)
            
            // Insert test data with composite primary key
            execSQL("""
                INSERT INTO custom_exercises (
                    equipment, id, name, primary_muscle_group, 
                    secondary_muscle_groups, instructions, tags, 
                    thumbnail_url, updated_at
                ) VALUES 
                ('None', 'push-up', 'Push Up', 'Chest', 'Shoulders,Triceps', 'Bodyweight push up', 'bodyweight,push', 'url1', '2024-01-01T12:00:00Z'),
                ('Barbell', 'bench-press', 'Bench Press', 'Chest', 'Shoulders,Triceps', 'Barbell bench press', 'barbell,press', 'url2', '2024-01-01T13:00:00Z')
            """)
            close()
        }

        // Run the migration
        db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        // Verify the schema is now correct for CustomExerciseEntity
        db.query("PRAGMA table_info(custom_exercises)").use { cursor ->
            val columnNames = mutableSetOf<String>()
            val primaryKeyColumns = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndex("name")
            val pkIndex = cursor.getColumnIndex("pk")
            
            while (cursor.moveToNext()) {
                val columnName = cursor.getString(nameIndex)
                columnNames.add(columnName)
                if (cursor.getInt(pkIndex) > 0) {
                    primaryKeyColumns.add(columnName)
                }
            }
            
            // Verify single primary key
            assert(primaryKeyColumns == setOf("id")) {
                "Expected single PK (id), got: $primaryKeyColumns"
            }
            
            // Verify expected columns exist (CustomExerciseEntity schema)
            val expectedColumns = setOf(
                "id", "user_id", "name", "primary_muscle_group", "equipment",
                "secondary_muscle_groups", "difficulty", "notes", 
                "created_at", "updated_at", "is_synced", "sync_version"
            )
            
            assert(columnNames.containsAll(expectedColumns)) {
                "Missing columns: ${expectedColumns - columnNames}"
            }
            
            // Verify unwanted ExerciseLibrary columns are removed
            val unwantedColumns = setOf("instructions", "tags", "thumbnail_url")
            val presentUnwantedColumns = columnNames.intersect(unwantedColumns)
            
            assert(presentUnwantedColumns.isEmpty()) {
                "Unwanted ExerciseLibrary columns still present: $presentUnwantedColumns"
            }
        }

        // Verify data migration with composite key conversion
        db.query("SELECT * FROM custom_exercises ORDER BY id").use { cursor ->
            assert(cursor.count == 2) { "Expected 2 exercises, got ${cursor.count}" }
            
            val idIndex = cursor.getColumnIndex("id")
            val nameIndex = cursor.getColumnIndex("name")
            val equipmentIndex = cursor.getColumnIndex("equipment")
            val notesIndex = cursor.getColumnIndex("notes")
            val userIdIndex = cursor.getColumnIndex("user_id")
            
            cursor.moveToFirst()
            // First exercise - composite ID should be "None_push-up"
            val firstId = cursor.getString(idIndex)
            assert(firstId == "None_push-up") { "Expected 'None_push-up', got '$firstId'" }
            assert(cursor.getString(nameIndex) == "Push Up") { "Name not preserved" }
            assert(cursor.getString(equipmentIndex) == "None") { "Equipment not preserved" }
            assert(cursor.getString(notesIndex) == "Bodyweight push up") { "Instructions not mapped to notes" }
            assert(cursor.getString(userIdIndex) == "migrated_user") { "Default user_id not set" }
            
            cursor.moveToNext()
            // Second exercise - composite ID should be "Barbell_bench-press"
            val secondId = cursor.getString(idIndex)
            assert(secondId == "Barbell_bench-press") { "Expected 'Barbell_bench-press', got '$secondId'" }
            assert(cursor.getString(nameIndex) == "Bench Press") { "Name not preserved" }
            assert(cursor.getString(equipmentIndex) == "Barbell") { "Equipment not preserved" }
        }
        
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13_WithCorrectSchema_PreservesData() {
        // Create database at version 12 with CORRECT custom_exercises schema
        var db = helper.createDatabase(TEST_DB, 12).apply {
            // Create custom_exercises table with correct CustomExerciseEntity schema
            execSQL("""
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
            
            // Insert test data
            execSQL("""
                INSERT INTO custom_exercises (
                    id, user_id, name, primary_muscle_group, equipment,
                    notes, created_at, updated_at
                ) VALUES (
                    'test-exercise-1', 
                    'user123',
                    'Push Up', 
                    'Chest', 
                    'None',
                    'Custom exercise notes',
                    '2024-01-01T12:00:00Z',
                    '2024-01-01T12:00:00Z'
                )
            """)
            close()
        }

        // Run the migration
        db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        // Verify data is preserved
        db.query("SELECT * FROM custom_exercises WHERE id = 'test-exercise-1'").use { cursor ->
            assert(cursor.moveToFirst()) { "Test exercise not found after migration" }
            
            val userIdIndex = cursor.getColumnIndex("user_id")
            val notesIndex = cursor.getColumnIndex("notes")
            
            assert(cursor.getString(userIdIndex) == "user123") { "Existing user_id not preserved" }
            assert(cursor.getString(notesIndex) == "Custom exercise notes") { "Existing notes not preserved" }
        }
        
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13_WithEmptyTable_CreatesCorrectSchema() {
        // Create database at version 12 with wrong schema but no data
        var db = helper.createDatabase(TEST_DB, 12).apply {
            // Create custom_exercises table with problematic schema
            execSQL("""
                CREATE TABLE custom_exercises (
                    equipment TEXT NOT NULL,
                    id TEXT NOT NULL,
                    instructions TEXT,
                    name TEXT NOT NULL,
                    primary_muscle_group TEXT NOT NULL,
                    secondary_muscle_groups TEXT,
                    tags TEXT,
                    thumbnail_url TEXT,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (equipment, id)
                )
            """)
            // No data inserted
            close()
        }

        // Run the migration
        db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        // Verify correct schema is created
        db.query("PRAGMA table_info(custom_exercises)").use { cursor ->
            val columnNames = mutableSetOf<String>()
            val primaryKeyColumns = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndex("name")
            val pkIndex = cursor.getColumnIndex("pk")
            
            while (cursor.moveToNext()) {
                val columnName = cursor.getString(nameIndex)
                columnNames.add(columnName)
                if (cursor.getInt(pkIndex) > 0) {
                    primaryKeyColumns.add(columnName)
                }
            }
            
            // Verify single primary key
            assert(primaryKeyColumns == setOf("id")) {
                "Expected single PK (id), got: $primaryKeyColumns"
            }
            
            // Verify no data exists (empty table)
            val count = db.query("SELECT COUNT(*) FROM custom_exercises").use { countCursor ->
                if (countCursor.moveToFirst()) countCursor.getInt(0) else -1
            }
            assert(count == 0) { "Expected empty table, got $count rows" }
        }
        
        db.close()
    }
}