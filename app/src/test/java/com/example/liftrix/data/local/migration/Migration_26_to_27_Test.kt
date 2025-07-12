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
 * Test for database migration from version 26 to 27.
 * Verifies that the user_subscriptions table is created correctly
 * with proper foreign key constraints and indices.
 */
@RunWith(AndroidJUnit4::class)
class Migration_26_to_27_Test {

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
    fun migrate26To27() {
        var db = helper.createDatabase(TEST_DB, 26).apply {
            // Insert test data before migration
            execSQL("""
                INSERT INTO user_profiles (id, user_id, display_name, age, weight_kg, height_cm, 
                fitness_level, goals, available_equipment, workout_frequency, 
                preferred_workout_duration, completed_at, created_at, updated_at, is_synced, sync_version)
                VALUES ('test_user_1', 'test_user_1', 'Test User', 25, 70.0, 175.0, 
                'intermediate', 'strength', 'gym', 3, 60, 
                '2023-01-01T10:00:00Z', '2023-01-01T10:00:00Z', '2023-01-01T10:00:00Z', 0, 1)
            """)
            close()
        }

        // Re-open the database with version 27 and provide MIGRATION_26_27 as the migration process.
        db = helper.runMigrationsAndValidate(TEST_DB, 27, true, MIGRATION_26_27)

        // Verify that the user_subscriptions table was created
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='user_subscriptions'")
        assert(cursor.moveToFirst()) { "user_subscriptions table should exist after migration" }
        cursor.close()

        // Verify table structure by querying table info
        val tableInfoCursor = db.query("PRAGMA table_info(user_subscriptions)")
        val columnNames = mutableListOf<String>()
        while (tableInfoCursor.moveToNext()) {
            val columnName = tableInfoCursor.getString(1) // Column name is at index 1
            columnNames.add(columnName)
        }
        tableInfoCursor.close()

        // Verify expected columns exist
        val expectedColumns = listOf(
            "user_id", "tier", "status", "provider", "product_id", "subscription_id",
            "started_at", "expires_at", "cancelled_at", "trial_ends_at", "auto_renew",
            "price_cents", "currency", "created_at", "updated_at", "is_synced", "sync_version"
        )
        
        for (expectedColumn in expectedColumns) {
            assert(columnNames.contains(expectedColumn)) { 
                "Column $expectedColumn should exist in user_subscriptions table" 
            }
        }

        // Verify foreign key constraint exists
        val foreignKeysCursor = db.query("PRAGMA foreign_key_list(user_subscriptions)")
        var foreignKeyExists = false
        while (foreignKeysCursor.moveToNext()) {
            val table = foreignKeysCursor.getString(2) // Referenced table is at index 2
            val fromColumn = foreignKeysCursor.getString(3) // From column is at index 3
            val toColumn = foreignKeysCursor.getString(4) // To column is at index 4
            
            if (table == "user_profiles" && fromColumn == "user_id" && toColumn == "id") {
                foreignKeyExists = true
                break
            }
        }
        foreignKeysCursor.close()
        
        assert(foreignKeyExists) { 
            "Foreign key constraint from user_subscriptions.user_id to user_profiles.id should exist" 
        }

        // Verify indices were created
        val indicesCursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='user_subscriptions'")
        val indexNames = mutableListOf<String>()
        while (indicesCursor.moveToNext()) {
            val indexName = indicesCursor.getString(0)
            indexNames.add(indexName)
        }
        indicesCursor.close()

        // Verify key indices exist
        val expectedIndices = listOf(
            "index_user_subscriptions_user_id",
            "index_user_subscriptions_tier",
            "index_user_subscriptions_status",
            "index_user_subscriptions_tier_status"
        )
        
        for (expectedIndex in expectedIndices) {
            assert(indexNames.contains(expectedIndex)) { 
                "Index $expectedIndex should exist after migration" 
            }
        }

        // Test that we can insert data into the new table
        val currentTime = java.time.Instant.now().toString()
        db.execSQL("""
            INSERT INTO user_subscriptions (
                user_id, tier, status, provider, started_at, created_at, updated_at
            ) VALUES (
                'test_user_1', 'PREMIUM', 'active', 'google_play', '$currentTime', '$currentTime', '$currentTime'
            )
        """)

        // Verify the insert worked
        val subscriptionCursor = db.query("SELECT * FROM user_subscriptions WHERE user_id = 'test_user_1'")
        assert(subscriptionCursor.moveToFirst()) { "Should be able to insert subscription data" }
        
        val tier = subscriptionCursor.getString(subscriptionCursor.getColumnIndex("tier"))
        val status = subscriptionCursor.getString(subscriptionCursor.getColumnIndex("status"))
        
        assert(tier == "PREMIUM") { "Tier should be PREMIUM" }
        assert(status == "active") { "Status should be active" }
        
        subscriptionCursor.close()
    }
}