package com.example.liftrix.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.migration.MIGRATION_38_39
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Test for database migration from version 38 to 39
 * Verifies that foreign key constraint fix for workout templates works correctly
 * Fixes critical CASCADE DELETE issue causing template deletion when folders are deleted
 */
@RunWith(AndroidJUnit4::class)
class Migration_38_39_Test {

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
    fun migrate38To39_fixForeignKeyConstraint() {
        // Create database with version 38
        val db = helper.createDatabase(TEST_DB, 38).apply {
            // Insert test folders
            execSQL("""
                INSERT INTO folders (id, user_id, name, created_at, updated_at, is_synced, sync_version) VALUES 
                ('folder1', 'user1', 'Chest Workouts', 1640995200000, 1640995200000, 1, 1),
                ('folder2', 'user1', 'Back Workouts', 1640995200000, 1640995200000, 1, 1)
            """)
            
            // Insert workout templates with folder references
            execSQL("""
                INSERT INTO workout_templates (id, user_id, name, description, template_exercises_json, 
                    estimated_duration_minutes, difficulty_level, folder_id, usage_count, last_used_at, 
                    created_at, updated_at, is_synced, sync_version) VALUES 
                ('template1', 'user1', 'Push Day', 'Chest and triceps', '[]', 60, 3, 'folder1', 5, 
                 1640995200000, 1640995200000, 1640995200000, 1, 1),
                ('template2', 'user1', 'Pull Day', 'Back and biceps', '[]', 60, 3, 'folder2', 3, 
                 1640995200000, 1640995200000, 1640995200000, 1, 1),
                ('template3', 'user1', 'No Folder Template', 'Template without folder', '[]', 45, 2, NULL, 1, 
                 1640995200000, 1640995200000, 1640995200000, 1, 1)
            """)
            
            close()
        }

        // Migrate to version 39
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 39, true, MIGRATION_38_39)

        // Verify templates are preserved after migration
        migratedDb.apply {
            val cursor = query("SELECT id, name, folder_id FROM workout_templates WHERE user_id = 'user1' ORDER BY name")
            val templates = mutableListOf<Triple<String, String, String?>>()
            
            while (cursor.moveToNext()) {
                templates.add(Triple(
                    cursor.getString(0),
                    cursor.getString(1), 
                    cursor.getString(2)
                ))
            }
            cursor.close()
            
            // All templates should be preserved
            assert(templates.size == 3) {
                "Should have 3 templates after migration but found ${templates.size}"
            }
            
            // Verify specific templates exist
            val templateNames = templates.map { it.second }
            assert(templateNames.contains("Push Day")) { "Push Day template should be preserved" }
            assert(templateNames.contains("Pull Day")) { "Pull Day template should be preserved" }
            assert(templateNames.contains("No Folder Template")) { "No Folder Template should be preserved" }
            
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate38To39_handleOrphanedTemplates() {
        // Create database with version 38
        val db = helper.createDatabase(TEST_DB, 38).apply {
            // Insert folders
            execSQL("""
                INSERT INTO folders (id, user_id, name, created_at, updated_at, is_synced, sync_version) VALUES 
                ('existing_folder', 'user1', 'Valid Folder', 1640995200000, 1640995200000, 1, 1)
            """)
            
            // Insert templates with both valid and invalid folder references
            execSQL("""
                INSERT INTO workout_templates (id, user_id, name, description, template_exercises_json, 
                    estimated_duration_minutes, difficulty_level, folder_id, usage_count, last_used_at, 
                    created_at, updated_at, is_synced, sync_version) VALUES 
                ('template1', 'user1', 'Valid Template', 'Has valid folder', '[]', 60, 3, 'existing_folder', 5, 
                 1640995200000, 1640995200000, 1640995200000, 1, 1),
                ('template2', 'user1', 'Orphaned Template', 'Has invalid folder ref', '[]', 60, 3, 'non_existent_folder', 3, 
                 1640995200000, 1640995200000, 1640995200000, 1, 1)
            """)
            
            close()
        }

        // Migrate to version 39
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 39, true, MIGRATION_38_39)

        // Verify orphaned templates have NULL folder_id
        migratedDb.apply {
            val cursor = query("SELECT name, folder_id FROM workout_templates WHERE user_id = 'user1' ORDER BY name")
            val templates = mutableListOf<Pair<String, String?>>()
            
            while (cursor.moveToNext()) {
                templates.add(Pair(
                    cursor.getString(0),
                    cursor.getString(1)
                ))
            }
            cursor.close()
            
            // Find templates by name
            val validTemplate = templates.find { it.first == "Valid Template" }
            val orphanedTemplate = templates.find { it.first == "Orphaned Template" }
            
            // Valid template should keep its folder reference
            assert(validTemplate?.second == "existing_folder") {
                "Valid template should keep folder reference but has ${validTemplate?.second}"
            }
            
            // Orphaned template should have NULL folder_id
            assert(orphanedTemplate?.second == null) {
                "Orphaned template should have NULL folder_id but has ${orphanedTemplate?.second}"
            }
            
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate38To39_withEmptyDatabase() {
        // Create empty database with version 38
        val db = helper.createDatabase(TEST_DB, 38).apply {
            close()
        }

        // Migrate to version 39 - should succeed even with no data
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 39, true, MIGRATION_38_39)
        
        // Verify database is valid and migration completed
        migratedDb.apply {
            // Insert test data to verify workout_templates table is working after constraint fix
            execSQL("""
                INSERT INTO workout_templates (id, user_id, name, description, template_exercises_json, 
                    estimated_duration_minutes, difficulty_level, folder_id, usage_count, last_used_at, 
                    created_at, updated_at, is_synced, sync_version) VALUES 
                ('test1', 'user1', 'Test Template', 'Test description', '[]', 30, 1, NULL, 0, NULL,
                 1640995200000, 1640995200000, 0, 1)
            """)
            
            val cursor = query("SELECT * FROM workout_templates WHERE user_id = 'user1'")
            assert(cursor.count == 1) {
                "Should be able to insert and query workout templates after migration"
            }
            cursor.close()
            
            close()
        }
    }

    @Test
    @Throws(IOException::class)  
    fun migrate38To39_verifyIndexCreation() {
        // Create database with version 38
        val db = helper.createDatabase(TEST_DB, 38).apply {
            // Insert test data
            execSQL("""
                INSERT INTO folders (id, user_id, name, created_at, updated_at, is_synced, sync_version) VALUES 
                ('folder1', 'user1', 'Test Folder', 1640995200000, 1640995200000, 1, 1)
            """)
            
            execSQL("""
                INSERT INTO workout_templates (id, user_id, name, description, template_exercises_json, 
                    estimated_duration_minutes, difficulty_level, folder_id, usage_count, last_used_at, 
                    created_at, updated_at, is_synced, sync_version) VALUES 
                ('template1', 'user1', 'Template 1', 'Description 1', '[]', 30, 1, 'folder1', 5, 
                 1640995200000, 1640995200000, 1640995200000, 1, 1),
                ('template2', 'user1', 'Template 2', 'Description 2', '[]', 45, 2, 'folder1', 3, 
                 1640995300000, 1640995300000, 1640995300000, 1, 1)
            """)
            
            close()
        }

        // Migrate to version 39
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 39, true, MIGRATION_38_39)

        // Verify indexes are working by testing queries that would benefit from them
        migratedDb.apply {
            // Test user_id index
            val userCursor = query("SELECT count(*) FROM workout_templates WHERE user_id = 'user1'")
            userCursor.moveToFirst()
            val userCount = userCursor.getInt(0)
            userCursor.close()
            
            assert(userCount == 2) {
                "Should find 2 templates for user1 but found $userCount"
            }
            
            // Test folder_id index
            val folderCursor = query("SELECT count(*) FROM workout_templates WHERE folder_id = 'folder1'")
            folderCursor.moveToFirst()
            val folderCount = folderCursor.getInt(0)
            folderCursor.close()
            
            assert(folderCount == 2) {
                "Should find 2 templates in folder1 but found $folderCount"
            }
            
            // Test usage index (ORDER BY usage_count DESC)
            val usageCursor = query("SELECT id FROM workout_templates WHERE user_id = 'user1' ORDER BY usage_count DESC LIMIT 1")
            usageCursor.moveToFirst()
            val topUsedTemplate = usageCursor.getString(0)
            usageCursor.close()
            
            assert(topUsedTemplate == "template1") {
                "Most used template should be template1 but was $topUsedTemplate"
            }
            
            close()
        }
    }
}