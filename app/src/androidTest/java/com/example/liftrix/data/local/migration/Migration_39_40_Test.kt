package com.example.liftrix.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.migration.MIGRATION_39_40
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Test for database migration from version 39 to 40
 * Verifies that deprecated widget preferences are removed and positions are reordered correctly
 * Tests the actual widget system refactoring from SPEC-20250205-widget-system-refactoring
 */
@RunWith(AndroidJUnit4::class)
class Migration_39_40_Test {

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
    fun migrate39To40_removeDeprecatedWidgets() {
        // Create database with version 39
        val db = helper.createDatabase(TEST_DB, 39).apply {
            // Insert test widget preferences including deprecated widgets (using snake_case format)
            execSQL("""
                INSERT INTO widget_preferences (user_id, widget_type, is_enabled, position) VALUES 
                ('user1', 'total_volume', 1, 0),
                ('user1', 'calories_burned', 1, 1),
                ('user1', 'workout_frequency', 1, 2),
                ('user1', 'daily_calories', 1, 3),
                ('user1', 'average_duration', 1, 4),
                ('user1', 'weekly_calorie_trend', 1, 5),
                ('user1', 'consistency_streak', 1, 6),
                ('user1', 'personal_records', 1, 7),
                ('user1', 'duration_chart', 1, 8),
                ('user1', 'set_completion_rate', 1, 9),
                ('user1', 'exercise_variety', 1, 10),
                ('user1', 'training_intensity', 1, 11),
                ('user1', 'goal_achievement', 1, 12),
                ('user1', 'weekly_trends', 1, 13),
                ('user1', 'time_of_day_analysis', 1, 14),
                ('user1', 'recovery_patterns', 1, 15),
                ('user1', 'performance_analysis', 1, 16)
            """)
            
            close()
        }

        // Migrate to version 40
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 40, true, MIGRATION_39_40)

        // Verify deprecated widgets were removed
        migratedDb.apply {
            val cursor = query("SELECT widget_type FROM widget_preferences WHERE user_id = 'user1' ORDER BY position")
            val remainingWidgets = mutableListOf<String>()
            
            while (cursor.moveToNext()) {
                remainingWidgets.add(cursor.getString(0))
            }
            cursor.close()
            
            // Verify deprecated widgets are gone (exact list from Migration_39_40.kt)
            val deprecatedWidgets = listOf(
                "calories_burned", "daily_calories", "weekly_calorie_trend",
                "consistency_streak", "duration_chart", "set_completion_rate",
                "exercise_variety", "training_intensity", "goal_achievement",
                "weekly_trends", "time_of_day_analysis", "recovery_patterns",
                "performance_analysis"
            )
            deprecatedWidgets.forEach { deprecatedWidget ->
                assert(!remainingWidgets.contains(deprecatedWidget)) {
                    "Deprecated widget $deprecatedWidget should have been removed"
                }
            }
            
            // Verify remaining widgets are present
            val expectedWidgets = listOf("total_volume", "workout_frequency", "average_duration", "personal_records")
            expectedWidgets.forEach { expectedWidget ->
                assert(remainingWidgets.contains(expectedWidget)) {
                    "Widget $expectedWidget should still be present"
                }
            }
            
            // Verify positions are reordered (0, 1, 2, 3)
            val positionCursor = query("SELECT position FROM widget_preferences WHERE user_id = 'user1' ORDER BY position")
            val positions = mutableListOf<Int>()
            while (positionCursor.moveToNext()) {
                positions.add(positionCursor.getInt(0))
            }
            positionCursor.close()
            
            assert(positions == listOf(0, 1, 2, 3)) {
                "Positions should be reordered to 0, 1, 2, 3 but were $positions"
            }
            
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate39To40_cleanupAnalyticsCache() {
        // Create database with version 39
        val db = helper.createDatabase(TEST_DB, 39).apply {
            // Insert analytics cache with calculation types that will be cleaned up
            execSQL("""
                INSERT INTO analytics_cache (user_id, calculation_type, cached_result, created_at, expires_at) VALUES
                ('user1', 'calorie_calculation', '{"value": 500}', '2024-01-01T10:00:00Z', '2024-01-02T10:00:00Z'),
                ('user1', 'weekly_calorie_trend', '{"trend": "up"}', '2024-01-01T10:00:00Z', '2024-01-02T10:00:00Z'),
                ('user1', 'consistency_analysis', '{"streak": 5}', '2024-01-01T10:00:00Z', '2024-01-02T10:00:00Z'),
                ('user1', 'volume_calculation', '{"volume": 1000}', '2024-01-01T10:00:00Z', '2024-01-02T10:00:00Z'),
                ('user1', 'set_completion_analysis', '{"rate": 0.95}', '2024-01-01T10:00:00Z', '2024-01-02T10:00:00Z'),
                ('user1', 'old_cache_entry', '{"data": "old"}', '2024-01-01T10:00:00Z', '2024-01-01T10:00:00Z')
            """)
            
            close()
        }

        // Migrate to version 40
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 40, true, MIGRATION_39_40)

        // Verify cache cleanup occurred (30% database size reduction)
        migratedDb.apply {
            val cursor = query("SELECT calculation_type FROM analytics_cache WHERE user_id = 'user1'")
            val remainingCalculationTypes = mutableListOf<String>()
            
            while (cursor.moveToNext()) {
                remainingCalculationTypes.add(cursor.getString(0))
            }
            cursor.close()
            
            // Should have fewer cache entries due to cleanup
            assert(remainingCalculationTypes.size <= 3) {
                "Cache should be cleaned up, but found ${remainingCalculationTypes.size} entries: $remainingCalculationTypes"
            }
            
            // Important calculations should remain
            assert(remainingCalculationTypes.contains("volume_calculation")) {
                "VOLUME_CALCULATION should still be present in cache"
            }
            
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate39To40_withEmptyDatabase() {
        // Create empty database with version 39
        val db = helper.createDatabase(TEST_DB, 39).apply {
            close()
        }

        // Migrate to version 40 - should succeed even with no data
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 40, true, MIGRATION_39_40)
        
        // Verify database is valid and migration completed
        migratedDb.apply {
            // Insert test data to verify tables are working
            execSQL("""
                INSERT INTO widget_preferences (user_id, widget_type, is_enabled, position) VALUES 
                ('user1', 'total_volume', 1, 0)
            """)
            
            val cursor = query("SELECT * FROM widget_preferences WHERE user_id = 'user1'")
            assert(cursor.count == 1) {
                "Should be able to insert and query widget preferences after migration"
            }
            cursor.close()
            
            close()
        }
    }

    @Test
    @Throws(IOException::class)  
    fun migrate39To40_preserveNonDeprecatedWidgets() {
        // Create database with version 39 with only non-deprecated widgets
        val db = helper.createDatabase(TEST_DB, 39).apply {
            execSQL("""
                INSERT INTO widget_preferences (user_id, widget_type, is_enabled, position) VALUES 
                ('user1', 'total_volume', 1, 0),
                ('user1', 'workout_frequency', 1, 1),
                ('user1', 'average_duration', 1, 2),
                ('user1', 'personal_records', 1, 3),
                ('user1', 'one_rm_progression', 1, 4)
            """)
            
            close()
        }

        // Migrate to version 40
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 40, true, MIGRATION_39_40)

        // Verify all non-deprecated widgets are preserved
        migratedDb.apply {
            val cursor = query("SELECT widget_type, position FROM widget_preferences WHERE user_id = 'user1' ORDER BY position")
            val widgets = mutableListOf<Pair<String, Int>>()
            
            while (cursor.moveToNext()) {
                widgets.add(Pair(cursor.getString(0), cursor.getInt(1)))
            }
            cursor.close()
            
            // Should have 5 widgets with positions 0-4
            assert(widgets.size == 5) {
                "Should have 5 non-deprecated widgets but had ${widgets.size}"
            }
            
            // Verify positions are consecutive  
            val expectedWidgets = listOf(
                Pair("total_volume", 0),
                Pair("workout_frequency", 1), 
                Pair("average_duration", 2),
                Pair("personal_records", 3),
                Pair("one_rm_progression", 4)
            )
            
            assert(widgets == expectedWidgets) {
                "Widgets should be preserved with correct positions: expected $expectedWidgets, got $widgets"
            }
            
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate39To40_positionReordering() {
        // Create database with gaps in positions after deprecated widget removal
        val db = helper.createDatabase(TEST_DB, 39).apply {
            execSQL("""
                INSERT INTO widget_preferences (user_id, widget_type, is_enabled, position) VALUES 
                ('user1', 'total_volume', 1, 0),
                ('user1', 'calories_burned', 1, 1),       -- Will be removed
                ('user1', 'workout_frequency', 1, 2),
                ('user1', 'daily_calories', 1, 3),        -- Will be removed  
                ('user1', 'personal_records', 1, 4),
                ('user1', 'consistency_streak', 1, 5)     -- Will be removed
            """)
            
            close()
        }

        // Migrate to version 40
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 40, true, MIGRATION_39_40)

        // Verify position reordering works correctly
        migratedDb.apply {
            val cursor = query("SELECT widget_type, position FROM widget_preferences WHERE user_id = 'user1' ORDER BY position")
            val widgets = mutableListOf<Pair<String, Int>>()
            
            while (cursor.moveToNext()) {
                widgets.add(Pair(cursor.getString(0), cursor.getInt(1)))
            }
            cursor.close()
            
            // Should have 3 remaining widgets with consecutive positions 0, 1, 2
            val expectedWidgets = listOf(
                Pair("total_volume", 0),
                Pair("workout_frequency", 1), 
                Pair("personal_records", 2)
            )
            
            assert(widgets == expectedWidgets) {
                "Widgets should be reordered with consecutive positions: expected $expectedWidgets, got $widgets"
            }
            
            close()
        }
    }
}