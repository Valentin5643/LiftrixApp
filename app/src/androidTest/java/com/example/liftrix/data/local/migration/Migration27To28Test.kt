package com.example.liftrix.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.migration.MIGRATION_27_28
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Test for database migration from version 27 to 28.
 * 
 * Verifies that the analytics_cache table is created correctly with proper schema,
 * composite indexes for performance optimization, and additional workout table indexes.
 */
@RunWith(AndroidJUnit4::class)
class Migration27To28Test {

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
    fun migrate27To28() {
        var db = helper.createDatabase(TEST_DB, 27).apply {
            // Insert test data before migration
            execSQL("""
                INSERT INTO workouts (id, user_id, name, date, exercises_json, status, 
                created_at, updated_at, is_synced, sync_version)
                VALUES ('workout_1', 'user_1', 'Test Workout', '2024-01-01', '[]', 'COMPLETED', 
                '2024-01-01T10:00:00Z', '2024-01-01T10:00:00Z', 0, 0)
            """)
            close()
        }

        // Re-open the database with version 28 and provide MIGRATION_27_28 as the migration process
        db = helper.runMigrationsAndValidate(TEST_DB, 28, true, MIGRATION_27_28)

        // Verify that the analytics_cache table was created
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='analytics_cache'")
        assert(cursor.moveToFirst()) { "analytics_cache table should exist after migration" }
        cursor.close()

        // Verify table structure by querying table info
        val tableInfoCursor = db.query("PRAGMA table_info(analytics_cache)")
        val columnNames = mutableListOf<String>()
        val columnTypes = mutableListOf<String>()
        val notNullFlags = mutableListOf<Boolean>()
        
        while (tableInfoCursor.moveToNext()) {
            val columnName = tableInfoCursor.getString(1) // Column name is at index 1
            val columnType = tableInfoCursor.getString(2) // Column type is at index 2
            val notNull = tableInfoCursor.getInt(3) == 1 // NOT NULL flag is at index 3
            
            columnNames.add(columnName)
            columnTypes.add(columnType)
            notNullFlags.add(notNull)
        }
        tableInfoCursor.close()

        // Verify expected columns exist with correct types and constraints
        val expectedColumns = mapOf(
            "id" to "TEXT",
            "user_id" to "TEXT",
            "calculation_type" to "TEXT",
            "result" to "TEXT",
            "timestamp" to "INTEGER"
        )
        
        for ((expectedColumn, expectedType) in expectedColumns) {
            val columnIndex = columnNames.indexOf(expectedColumn)
            assert(columnIndex >= 0) { 
                "Column $expectedColumn should exist in analytics_cache table" 
            }
            assert(columnTypes[columnIndex] == expectedType) {
                "Column $expectedColumn should have type $expectedType, got ${columnTypes[columnIndex]}"
            }
            assert(notNullFlags[columnIndex]) {
                "Column $expectedColumn should be NOT NULL"
            }
        }

        // Verify primary key constraint
        val primaryKeyCursor = db.query("PRAGMA table_info(analytics_cache)")
        var primaryKeyFound = false
        while (primaryKeyCursor.moveToNext()) {
            val columnName = primaryKeyCursor.getString(1)
            val isPrimaryKey = primaryKeyCursor.getInt(5) == 1 // Primary key flag is at index 5
            
            if (columnName == "id" && isPrimaryKey) {
                primaryKeyFound = true
                break
            }
        }
        primaryKeyCursor.close()
        
        assert(primaryKeyFound) { "Column 'id' should be the primary key" }

        // Verify composite indexes were created
        val indicesCursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='analytics_cache'")
        val indexNames = mutableListOf<String>()
        while (indicesCursor.moveToNext()) {
            val indexName = indicesCursor.getString(0)
            indexNames.add(indexName)
        }
        indicesCursor.close()

        // Verify expected composite indexes exist
        val expectedAnalyticsCacheIndices = listOf(
            "idx_analytics_cache_user_type",
            "idx_analytics_cache_user_timestamp"
        )
        
        for (expectedIndex in expectedAnalyticsCacheIndices) {
            assert(indexNames.contains(expectedIndex)) { 
                "Index $expectedIndex should exist after migration" 
            }
        }

        // Verify workout table index was created
        val workoutIndicesCursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='workouts'")
        val workoutIndexNames = mutableListOf<String>()
        while (workoutIndicesCursor.moveToNext()) {
            val indexName = workoutIndicesCursor.getString(0)
            workoutIndexNames.add(indexName)
        }
        workoutIndicesCursor.close()

        assert(workoutIndexNames.contains("idx_workout_analytics")) { 
            "Analytics index on workouts table should exist after migration" 
        }

        // Test that we can insert data into the new analytics_cache table
        val currentTimestamp = System.currentTimeMillis()
        db.execSQL("""
            INSERT INTO analytics_cache (
                id, user_id, calculation_type, result, timestamp
            ) VALUES (
                'cache_1', 'user_1', 'VOLUME_CALENDAR', '{"month": "2024-01", "volumes": {}}', $currentTimestamp
            )
        """)

        // Verify the insert worked
        val analyticsCursor = db.query("SELECT * FROM analytics_cache WHERE id = 'cache_1'")
        assert(analyticsCursor.moveToFirst()) { "Should be able to insert analytics cache data" }
        
        val userId = analyticsCursor.getString(analyticsCursor.getColumnIndex("user_id"))
        val calculationType = analyticsCursor.getString(analyticsCursor.getColumnIndex("calculation_type"))
        val result = analyticsCursor.getString(analyticsCursor.getColumnIndex("result"))
        val timestamp = analyticsCursor.getLong(analyticsCursor.getColumnIndex("timestamp"))
        
        assert(userId == "user_1") { "user_id should be 'user_1'" }
        assert(calculationType == "VOLUME_CALENDAR") { "calculation_type should be 'VOLUME_CALENDAR'" }
        assert(result.contains("month")) { "result should contain JSON data" }
        assert(timestamp == currentTimestamp) { "timestamp should match inserted value" }
        
        analyticsCursor.close()

        // Test index effectiveness with a query that should use the composite index
        val indexUsageCursor = db.query("""
            SELECT * FROM analytics_cache 
            WHERE user_id = 'user_1' AND calculation_type = 'VOLUME_CALENDAR'
        """)
        
        assert(indexUsageCursor.moveToFirst()) { "Composite index query should work" }
        indexUsageCursor.close()

        // Test workout analytics index with a query that should use the new index
        val workoutAnalyticsCursor = db.query("""
            SELECT * FROM workouts 
            WHERE user_id = 'user_1' AND date = '2024-01-01' AND status = 'COMPLETED'
        """)
        
        assert(workoutAnalyticsCursor.moveToFirst()) { "Workout analytics index query should work" }
        workoutAnalyticsCursor.close()
    }
    
    @Test
    @Throws(IOException::class)
    fun migrate27To28_withExistingWorkoutData() {
        var db = helper.createDatabase(TEST_DB, 27).apply {
            // Insert multiple workouts to test index performance
            for (i in 1..10) {
                execSQL("""
                    INSERT INTO workouts (id, user_id, name, date, exercises_json, status, 
                    created_at, updated_at, is_synced, sync_version)
                    VALUES ('workout_$i', 'user_1', 'Test Workout $i', '2024-01-0$i', '[]', 'COMPLETED', 
                    '2024-01-0${i}T10:00:00Z', '2024-01-0${i}T10:00:00Z', 0, 0)
                """)
            }
            close()
        }

        // Perform migration
        db = helper.runMigrationsAndValidate(TEST_DB, 28, true, MIGRATION_27_28)

        // Verify existing data is preserved
        val workoutCursor = db.query("SELECT COUNT(*) FROM workouts WHERE user_id = 'user_1'")
        assert(workoutCursor.moveToFirst()) { "Should find existing workout data" }
        val workoutCount = workoutCursor.getInt(0)
        assert(workoutCount == 10) { "Should have 10 workouts after migration, found $workoutCount" }
        workoutCursor.close()

        // Verify new table is empty initially
        val cacheCountCursor = db.query("SELECT COUNT(*) FROM analytics_cache")
        assert(cacheCountCursor.moveToFirst()) { "Should be able to query analytics_cache" }
        val cacheCount = cacheCountCursor.getInt(0)
        assert(cacheCount == 0) { "analytics_cache should be empty after migration" }
        cacheCountCursor.close()
    }
}