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
import timber.log.Timber

/**
 * Comprehensive migration validation tests to prevent database corruption.
 * 
 * These tests ensure that all migrations in the chain work correctly and
 * don't cause schema validation failures in production.
 * 
 * @author Claude Code Assistant
 */
@RunWith(AndroidJUnit4::class)
class MigrationValidationTest {
    
    private val TEST_DB = "migration-test"
    
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LiftrixDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )
    
    /**
     * Test complete migration chain from version 27 to 38.
     * 
     * This ensures all migrations work together without corruption.
     */
    @Test
    fun testCompleteMigrationChain_27_to_38() {
        // Start with version 27 database
        var db = helper.createDatabase(TEST_DB, 27)
        
        // Insert test data to ensure migration preserves data
        db.execSQL("""
            INSERT INTO workouts (id, user_id, name, date, exercises_json, status, created_at, updated_at)
            VALUES ('test-id', 'user-123', 'Test Workout', '2024-01-01', '[]', 'COMPLETED', 1704067200, 1704067200)
        """.trimIndent())
        
        db.close()
        
        // Apply all migrations sequentially
        db = helper.runMigrationsAndValidate(
            TEST_DB, 
            38,
            true,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36,
            MIGRATION_36_37,
            MIGRATION_37_38
        )
        
        // Verify final schema matches expected
        val cursor = db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='workouts'")
        cursor.moveToFirst()
        val createSql = cursor.getString(0)
        cursor.close()
        
        // Verify sync_version column exists with correct default
        assert(createSql.contains("sync_version")) {
            "Migration failed: sync_version column missing in final schema"
        }
        
        assert(createSql.contains("DEFAULT 0")) {
            "Migration failed: sync_version default value incorrect"
        }
        
        // Verify test data survived migration
        val dataCursor = db.query("SELECT id, name FROM workouts WHERE id = 'test-id'")
        assert(dataCursor.moveToFirst()) {
            "Migration failed: test data was lost during migration"
        }
        
        val workoutName = dataCursor.getString(1)
        dataCursor.close()
        
        assert(workoutName == "Test Workout") {
            "Migration failed: test data was corrupted during migration"
        }
        
        Timber.i("✅ Complete migration chain validation successful")
    }
    
    /**
     * Test individual migration steps to isolate potential issues.
     */
    @Test
    fun testIndividualMigrations() {
        val migrationSteps = listOf(
            27 to 28,
            28 to 29,
            29 to 30,
            30 to 31,
            31 to 32,
            32 to 33,
            33 to 34,
            34 to 35,
            35 to 36,
            36 to 37,
            37 to 38
        )
        
        migrationSteps.forEach { (from, to) ->
            Timber.d("Testing migration $from -> $to")
            
            val db = helper.createDatabase("test-$from-$to", from)
            db.close()
            
            try {
                val migratedDb = helper.runMigrationsAndValidate(
                    "test-$from-$to",
                    to,
                    true,
                    getMigrationForVersions(from, to)
                )
                migratedDb.close()
                Timber.i("✅ Migration $from -> $to successful")
            } catch (e: Exception) {
                Timber.e(e, "❌ Migration $from -> $to failed")
                throw AssertionError("Migration $from -> $to failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Test schema validation after fresh database creation.
     * 
     * This ensures the current schema definition is valid.
     */
    @Test
    fun testFreshDatabaseSchema() {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            LiftrixDatabase::class.java
        ).build()
        
        try {
            // Force database creation and validation
            val version = database.openHelper.readableDatabase.version
            assert(version == 38) {
                "Fresh database version should be 38, got $version"
            }
            
            // Test basic operations
            val workoutDao = database.workoutDao()
            // Basic query should not throw exception
            workoutDao.getAllWorkoutsForUser("test-user")
            
            Timber.i("✅ Fresh database schema validation successful")
            
        } finally {
            database.close()
        }
    }
    
    /**
     * Get the appropriate migration for the given version range.
     */
    private fun getMigrationForVersions(from: Int, to: Int) = when {
        from == 27 && to == 28 -> MIGRATION_27_28
        from == 28 && to == 29 -> MIGRATION_28_29
        from == 29 && to == 30 -> MIGRATION_29_30
        from == 30 && to == 31 -> MIGRATION_30_31
        from == 31 && to == 32 -> MIGRATION_31_32
        from == 32 && to == 33 -> MIGRATION_32_33
        from == 33 && to == 34 -> MIGRATION_33_34
        from == 34 && to == 35 -> MIGRATION_34_35
        from == 35 && to == 36 -> MIGRATION_35_36
        from == 36 && to == 37 -> MIGRATION_36_37
        from == 37 && to == 38 -> MIGRATION_37_38
        else -> throw IllegalArgumentException("No migration available for $from -> $to")
    }
}