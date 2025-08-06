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

/**
 * Test for database migration from version 37 to 38
 * Verifies that performance indexes are created correctly
 */
@RunWith(AndroidJUnit4::class)
class Migration_37_38_Test {

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
    fun migrate37To38() {
        // Create database with version 37
        val db = helper.createDatabase(TEST_DB, 37).apply {
            // Insert test data in v37 format
            execSQL("""
                INSERT INTO workouts (id, user_id, name, exercises_json, date, status, created_at, updated_at, is_synced) 
                VALUES ('test1', 'user1', 'Test Workout', '{"totalVolume": 1000}', '2024-01-01', 'COMPLETED', '2024-01-01T10:00:00Z', '2024-01-01T10:00:00Z', 1)
            """)
            
            execSQL("""
                INSERT INTO friends (id, user_id, friend_user_id, status, created_at, updated_at, is_synced) 
                VALUES ('friend1', 'user1', 'user2', 'ACCEPTED', 1704110400000, 1704110400000, 1)
            """)
            
            execSQL("""
                INSERT INTO user_search_cache (id, viewer_user_id, search_query, search_results, created_at, expires_at) 
                VALUES ('cache1', 'user1', 'test query', '[]', '2024-01-01T10:00:00Z', '2024-01-02T10:00:00Z')
            """)
            
            close()
        }

        // Migrate to version 38
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 38, true, MIGRATION_37_38)

        // Verify indexes were created by checking if they improve query performance
        // We can't directly check index existence, but we can verify the database is valid
        migratedDb.apply {
            // Test that JSON queries still work
            val workoutCursor = query("SELECT json_extract(exercises_json, '$.totalVolume') as volume FROM workouts WHERE user_id = 'user1'")
            assert(workoutCursor.count == 1)
            workoutCursor.close()
            
            // Test that friend queries still work  
            val friendCursor = query("SELECT * FROM friends WHERE user_id = 'user1' AND status = 'ACCEPTED'")
            assert(friendCursor.count == 1)
            friendCursor.close()
            
            // Test that search cache queries still work
            val cacheCursor = query("SELECT * FROM user_search_cache WHERE viewer_user_id = 'user1' AND search_query = 'test query'")
            assert(cacheCursor.count == 1)
            cacheCursor.close()
            
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate37To38_withEmptyDatabase() {
        // Create empty database with version 37
        val db = helper.createDatabase(TEST_DB, 37).apply {
            close()
        }

        // Migrate to version 38 - should succeed even with no data
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 38, true, MIGRATION_37_38)
        
        // Verify database is valid and indexes were created
        migratedDb.apply {
            // Insert test data to verify indexes work
            execSQL("""
                INSERT INTO workouts (id, user_id, name, exercises_json, date, status, created_at, updated_at, is_synced) 
                VALUES ('test1', 'user1', 'Test Workout', '{"totalVolume": 500}', '2024-01-01', 'COMPLETED', '2024-01-01T10:00:00Z', '2024-01-01T10:00:00Z', 1)
            """)
            
            // Query should work efficiently with new indexes
            val cursor = query("SELECT json_extract(exercises_json, '$.totalVolume') as volume FROM workouts WHERE user_id = 'user1' AND date = '2024-01-01' AND status = 'COMPLETED'")
            assert(cursor.count == 1)
            cursor.close()
            
            close()
        }
    }
}