package com.example.liftrix.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.liftrix.BuildConfig
import com.example.liftrix.core.security.DatabaseEncryption
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.migrations.MIGRATION_7_8
import com.example.liftrix.data.local.migrations.MIGRATION_8_9
import com.example.liftrix.data.local.migrations.MIGRATION_9_10
import com.example.liftrix.data.local.seed.ExerciseLibrarySeedData
import com.example.liftrix.data.local.seed.MetDataSeedService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val ENCRYPTED_DATABASE_NAME = "liftrix_database_encrypted"

    @Provides
    @Singleton
    fun provideLiftrixDatabase(
        @ApplicationContext context: Context,
        exerciseLibrarySeedData: ExerciseLibrarySeedData,
        metDataSeedService: MetDataSeedService,
        databaseEncryption: DatabaseEncryption
    ): LiftrixDatabase {
        System.loadLibrary("sqlcipher")

        if (!databaseEncryption.validateEncryptionSetup()) {
            throw SecurityException("Database encryption validation failed")
        }

        val passphrase = databaseEncryption.getSQLCipherPassphrase()
        val factory = SupportOpenHelperFactory(passphrase.toByteArray())

        val database = Room.databaseBuilder(
            context.applicationContext,
            LiftrixDatabase::class.java,
            ENCRYPTED_DATABASE_NAME
        )
            .openHelperFactory(factory)
            .setTransactionExecutor(Dispatchers.IO.asExecutor())
            .setQueryExecutor(Dispatchers.IO.asExecutor())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    Timber.d("Encrypted database created successfully")

                    try {
                        val result = db.query("PRAGMA cipher_version;")
                        if (result.moveToFirst()) {
                            val version = result.getString(0)
                            Timber.i("SQLCipher version: $version - encryption active")
                        }
                        result.close()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to verify SQLCipher encryption")
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    repairSettingsUpdatedAt(db)
                    Timber.d("Encrypted database opened successfully")
                }
            })
            .build()

        runBlocking(Dispatchers.IO) {
            try {
                Timber.d("Starting database initialization...")

                val dbVersion = try {
                    database.openHelper.readableDatabase.version
                } catch (e: Exception) {
                    Timber.e(e, "Database version check failed - possible corruption")
                    if (BuildConfig.DEBUG) {
                        try {
                            val dbFile = context.getDatabasePath(ENCRYPTED_DATABASE_NAME)
                            if (dbFile.exists()) {
                                context.deleteDatabase(ENCRYPTED_DATABASE_NAME)
                                Timber.w("Cleared corrupted database file for recovery")
                                database.openHelper.readableDatabase.version
                            } else {
                                throw e
                            }
                        } catch (recoveryException: Exception) {
                            Timber.e(recoveryException, "Database recovery failed")
                            throw recoveryException
                        }
                    } else {
                        throw e
                    }
                }

                Timber.d("Database version: $dbVersion - initialization successful")
                exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                metDataSeedService.populateMetDataIfNeeded(database)
                Timber.d("Database initialization completed successfully")
            } catch (e: Exception) {
                Timber.e(e, "Database initialization failed - repositories may experience connection errors")
            }
        }

        return database
    }

    @Provides
    @Singleton
    fun provideDatabaseEncryption(
        @ApplicationContext context: Context
    ): DatabaseEncryption = DatabaseEncryption(context)

    private fun repairSettingsUpdatedAt(db: SupportSQLiteDatabase) {
        try {
            db.execSQL(
                """
                UPDATE user_settings
                SET updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
                WHERE updated_at IS NULL OR TRIM(updated_at) = ''
                """.trimIndent()
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to repair null user_settings.updated_at values")
        }
    }
}
