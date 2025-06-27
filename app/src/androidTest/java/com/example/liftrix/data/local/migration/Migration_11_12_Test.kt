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

@RunWith(AndroidJUnit4::class)
class Migration_11_12_Test {
    
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LiftrixDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate11To12_WithExerciseLibraryLikeSchema_ConvertsToCustomExerciseSchema() {
        // Create database at version 11 with corrupted custom_exercises schema
        var db = helper.createDatabase(TEST_DB, 11).apply {
            // Create custom_exercises table with ExerciseLibrary-like schema (the problematic state)
            execSQL("""
                CREATE TABLE custom_exercises (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    primary_muscle_group TEXT NOT NULL,
                    equipment TEXT NOT NULL,
                    secondary_muscle_groups TEXT,
                    instructions TEXT,
                    tags TEXT,
                    thumbnail_url TEXT,
                    updated_at TEXT NOT NULL
                )
            """)
            
            // Insert test data
            execSQL("""
                INSERT INTO custom_exercises (
                    id, name, primary_muscle_group, equipment, 
                    secondary_muscle_groups, instructions, tags, 
                    thumbnail_url, updated_at
                ) VALUES (
                    'test-exercise-1', 
                    'Push Up', 
                    'Chest', 
                    'None',
                    'Shoulders,Triceps',
                    'Place hands on floor and push up',
                    'bodyweight,push',
                    'https://example.com/pushup.jpg',
                    '2024-01-01T12:00:00Z'
                )
            """)
            close()
        }

        // Run the migration
        db = helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12)

        // Verify the schema is now correct for CustomExerciseEntity
        db.query("PRAGMA table_info(custom_exercises)").use { cursor ->
            val columnNames = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndex("name")
            
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(nameIndex))
            }
            
            // Verify expected columns exist
            val expectedColumns = setOf(
                "id", "user_id", "name", "primary_muscle_group", "equipment",
                "secondary_muscle_groups", "difficulty", "notes", 
                "created_at", "updated_at", "is_synced", "sync_version"
            )
            
            assert(columnNames.containsAll(expectedColumns)) {
                "Missing columns: ${expectedColumns - columnNames}"
            }
            
            // Verify unwanted columns are removed
            val unwantedColumns = setOf("instructions", "tags", "thumbnail_url")
            val presentUnwantedColumns = columnNames.intersect(unwantedColumns)
            
            assert(presentUnwantedColumns.isEmpty()) {
                "Unwanted columns still present: $presentUnwantedColumns"
            }
        }

        // Verify data migration worked
        db.query("SELECT * FROM custom_exercises WHERE id = 'test-exercise-1'").use { cursor ->
            assert(cursor.moveToFirst()) { "Test exercise not found after migration" }
            
            val nameIndex = cursor.getColumnIndex("name")
            val notesIndex = cursor.getColumnIndex("notes")
            val userIdIndex = cursor.getColumnIndex("user_id")
            
            assert(cursor.getString(nameIndex) == "Push Up") { "Name not preserved" }
            assert(cursor.getString(notesIndex) == "Place hands on floor and push up") { "Instructions not mapped to notes" }
            assert(cursor.getString(userIdIndex) == "migrated_user") { "Default user_id not set" }
        }
        
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate11To12_WithCompositePrimaryKey_ConvertsToSinglePrimaryKey() {
        // Create database at version 11 with composite primary key (equipment, id)
        var db = helper.createDatabase(TEST_DB, 11).apply {
            // Create custom_exercises table with composite primary key and ExerciseLibrary-like schema
            execSQL("""
                CREATE TABLE custom_exercises (
                    equipment TEXT NOT NULL,
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    primary_muscle_group TEXT NOT NULL,
                    secondary_muscle_groups TEXT,
                    instructions TEXT,
                    tags TEXT,
                    thumbnail_url TEXT,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (equipment, id)
                )
            """)
            
            // Insert test data with composite keys
            execSQL("""
                INSERT INTO custom_exercises (
                    equipment, id, name, primary_muscle_group, 
                    secondary_muscle_groups, instructions, updated_at
                ) VALUES 
                ('None', 'push-up', 'Push Up', 'Chest', 'Shoulders,Triceps', 'Bodyweight push up', '2024-01-01T12:00:00Z'),
                ('Barbell', 'bench-press', 'Bench Press', 'Chest', 'Shoulders,Triceps', 'Barbell bench press', '2024-01-01T13:00:00Z')
            """)
            close()
        }

        // Run the migration
        db = helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12)

        // Verify the schema has single primary key now
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
            
            // Verify schema is correct
            val expectedColumns = setOf(
                "id", "user_id", "name", "primary_muscle_group", "equipment",
                "secondary_muscle_groups", "difficulty", "notes", 
                "created_at", "updated_at", "is_synced", "sync_version"
            )
            assert(columnNames.containsAll(expectedColumns)) {
                "Missing columns: ${expectedColumns - columnNames}"
            }
        }

        // Verify data migration with composite key conversion
        db.query("SELECT * FROM custom_exercises ORDER BY id").use { cursor ->
            assert(cursor.count == 2) { "Expected 2 exercises, got ${cursor.count}" }
            
            cursor.moveToFirst()
            val idIndex = cursor.getColumnIndex("id")
            val nameIndex = cursor.getColumnIndex("name")
            val equipmentIndex = cursor.getColumnIndex("equipment")
            val notesIndex = cursor.getColumnIndex("notes")
            
            // First exercise - composite ID should be "None_push-up"
            val firstId = cursor.getString(idIndex)
            assert(firstId == "None_push-up") { "Expected 'None_push-up', got '$firstId'" }
            assert(cursor.getString(nameIndex) == "Push Up") { "Name not preserved" }
            assert(cursor.getString(equipmentIndex) == "None") { "Equipment not preserved" }
            assert(cursor.getString(notesIndex) == "Bodyweight push up") { "Instructions not mapped to notes" }
            
            cursor.moveToNext()
            // Second exercise - composite ID should be "Barbell_bench-press"
            val secondId = cursor.getString(idIndex)
            assert(secondId == "Barbell_bench-press") { "Expected 'Barbell_bench-press', got '$secondId'" }
            assert(cursor.getString(nameIndex) == "Bench Press") { "Name not preserved" }
            assert(cursor.getString(equipmentIndex) == "Barbell") { "Equipment not preserved" }
        }
        
        db.close()
    }

        // Verify the schema is now correct for CustomExerciseEntity
        db.query("PRAGMA table_info(custom_exercises)").use { cursor ->
            val columnNames = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndex("name")
            
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(nameIndex))
            }
            
            // Verify expected columns exist
            val expectedColumns = setOf(
                "id", "user_id", "name", "primary_muscle_group", "equipment",
                "secondary_muscle_groups", "difficulty", "notes", 
                "created_at", "updated_at", "is_synced", "sync_version"
            )
            
            assert(columnNames.containsAll(expectedColumns)) {
                "Missing columns: ${expectedColumns - columnNames}"
            }
            
            // Verify unwanted columns are removed
            val unwantedColumns = setOf("instructions", "tags", "thumbnail_url")
            val presentUnwantedColumns = columnNames.intersect(unwantedColumns)
            
            assert(presentUnwantedColumns.isEmpty()) {
                "Unwanted columns still present: $presentUnwantedColumns"
            }
        }

        // Verify data migration worked
        db.query("SELECT * FROM custom_exercises WHERE id = 'test-exercise-1'").use { cursor ->
            assert(cursor.moveToFirst()) { "Test exercise not found after migration" }
            
            val nameIndex = cursor.getColumnIndex("name")
            val notesIndex = cursor.getColumnIndex("notes")
            val userIdIndex = cursor.getColumnIndex("user_id")
            
            assert(cursor.getString(nameIndex) == "Push Up") { "Name not preserved" }
            assert(cursor.getString(notesIndex) == "Place hands on floor and push up") { "Instructions not mapped to notes" }
            assert(cursor.getString(userIdIndex) == "migrated_user") { "Default user_id not set" }
        }
        
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate11To12_WithCorrectSchema_DoesNothing() {
        // Create database at version 11 with correct custom_exercises schema
        var db = helper.createDatabase(TEST_DB, 11).apply {
            // Create custom_exercises table with correct schema
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
                    created_at, updated_at
                ) VALUES (
                    'test-exercise-1', 
                    'user123',
                    'Push Up', 
                    'Chest', 
                    'None',
                    '2024-01-01T12:00:00Z',
                    '2024-01-01T12:00:00Z'
                )
            """)
            close()
        }

        // Run the migration
        db = helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12)

        // Verify data is preserved
        db.query("SELECT * FROM custom_exercises WHERE id = 'test-exercise-1'").use { cursor ->
            assert(cursor.moveToFirst()) { "Test exercise not found after migration" }
            
            val userIdIndex = cursor.getColumnIndex("user_id")
            assert(cursor.getString(userIdIndex) == "user123") { "Existing user_id not preserved" }
        }
        
        db.close()
    }
}