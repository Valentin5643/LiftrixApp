package com.example.liftrix.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.data.local.migrations.MIGRATION_7_8
import com.example.liftrix.data.local.migrations.MIGRATION_8_9
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiftrixDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LiftrixDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate8To9_validatesSchema() {
        helper.createDatabase(TEST_DATABASE, 8).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            9,
            true,
            MIGRATION_8_9
        ).close()
    }

    @Test
    fun migrate7To9_validatesSupportedChain() {
        helper.createDatabase(TEST_DATABASE, 7).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            9,
            true,
            MIGRATION_7_8,
            MIGRATION_8_9
        ).close()
    }

    private companion object {
        const val TEST_DATABASE = "liftrix-migration-test"
    }
}
