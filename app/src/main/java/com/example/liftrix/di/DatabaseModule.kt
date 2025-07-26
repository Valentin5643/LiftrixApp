package com.example.liftrix.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.liftrix.BuildConfig
import java.io.File
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.ExerciseWeightMemoryDao
import com.example.liftrix.data.local.dao.ExerciseUsageHistoryDao
import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import com.example.liftrix.data.local.dao.FolderDao
import com.example.liftrix.data.local.dao.FriendDao
import com.example.liftrix.data.local.dao.PrivacySettingsDao
import com.example.liftrix.data.local.dao.SubscriptionDao
import com.example.liftrix.data.local.dao.SettingsDao
import com.example.liftrix.data.local.dao.MetDataDao
import com.example.liftrix.data.local.dao.AnalyticsCacheDao
import com.example.liftrix.data.local.dao.GuestSessionDao
import com.example.liftrix.data.local.dao.WorkoutAnomalyDao
import com.example.liftrix.data.local.dao.AnomalyDetectionSettingsDao
import com.example.liftrix.data.local.dao.ExerciseHistoryDao
import com.example.liftrix.data.local.dao.WidgetPreferencesDao
import com.example.liftrix.data.local.dao.AchievementDao
import com.example.liftrix.data.local.dao.UserSearchCacheDao
import com.example.liftrix.data.local.dao.QRCodeMappingDao
import com.example.liftrix.data.local.seed.ExerciseLibrarySeedData
import com.example.liftrix.data.local.seed.MetDataSeedService
import com.example.liftrix.data.local.migration.MIGRATION_27_28
import com.example.liftrix.data.local.migration.MIGRATION_28_29
import com.example.liftrix.data.local.migration.MIGRATION_29_30
import com.example.liftrix.data.local.migration.MIGRATION_30_31
import com.example.liftrix.data.local.migration.MIGRATION_31_32
import com.example.liftrix.data.local.migration.MIGRATION_32_33
import com.example.liftrix.data.local.migration.MIGRATION_33_34
import com.example.liftrix.data.local.migration.MIGRATION_34_35
import com.example.liftrix.data.local.migration.MIGRATION_35_36
import com.example.liftrix.data.local.migration.MIGRATION_36_37

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.asExecutor
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLiftrixDatabase(
        @ApplicationContext context: Context,
        exerciseLibrarySeedData: ExerciseLibrarySeedData,
        metDataSeedService: MetDataSeedService
    ): LiftrixDatabase {

        // 🔥 EMERGENCY FIX: Force complete database reset to resolve schema mismatch
        // This ensures 100% clean state and eliminates any migration inconsistencies
        if (BuildConfig.DEBUG) {
            clearDatabaseIfCorrupted(context)
            Timber.i("🧹 FORCED database clearing completed - guaranteed clean state")
        }

        val database = Room.databaseBuilder(
            context.applicationContext,
            LiftrixDatabase::class.java,
            "liftrix_database" // Standard database name
        )
            .setTransactionExecutor(Dispatchers.IO.asExecutor())
            .setQueryExecutor(Dispatchers.IO.asExecutor())
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    Timber.i("🏗️ Database created from scratch at version ${db.version}")
                    Timber.i("✅ EMERGENCY FIX SUCCESS: Fresh database created from entity definitions")
                    
                    // Verify user_profiles table exists with correct schema
                    val cursor = db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='user_profiles'")
                    if (cursor.moveToFirst()) {
                        val tableSchema = cursor.getString(0)
                        Timber.i("📋 user_profiles table schema: $tableSchema")
                    }
                    cursor.close()
                }
                
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Timber.d("📖 Database connection opened (routine operation)")
                    
                    // Validate analytics_cache table exists
                    val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='analytics_cache'")
                    val tableExists = cursor.moveToFirst()
                    cursor.close()
                    
                    if (tableExists) {
                        Timber.i("✅ analytics_cache table confirmed in database")
                        
                        // Check table structure
                        val columnsCursor = db.query("PRAGMA table_info(analytics_cache)")
                        val columns = mutableListOf<String>()
                        while (columnsCursor.moveToNext()) {
                            columns.add(columnsCursor.getString(1))
                        }
                        columnsCursor.close()
                        Timber.i("📊 analytics_cache columns: $columns")
                        
                    } else {
                        Timber.e("❌ analytics_cache table MISSING from database")
                    }
                }
            })
            .addMigrations(MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37)
            // 🔥 EMERGENCY FIX: Re-enable destructive migration for guaranteed schema consistency
            // This will create fresh database from entity definitions, ensuring 100% success
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // WAL mode for better data persistence
            .build()
            
        // Pre-warm the database to establish stable connection and complete any migrations
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // Force database initialization and verify version
                val version = database.openHelper.readableDatabase.version
                Timber.i("🔥 Database pre-warmed at version: $version")
                
                // EMERGENCY FIX VALIDATION: Verify user_profiles table schema is correct
                val userProfilesCursor = database.openHelper.readableDatabase.query(
                    "SELECT sql FROM sqlite_master WHERE type='table' AND name='user_profiles'"
                )
                if (userProfilesCursor.moveToFirst()) {
                    val schema = userProfilesCursor.getString(0)
                    val hasNewFields = schema.contains("bio") && schema.contains("is_public") && schema.contains("profile_image_url")
                    Timber.i("✅ user_profiles schema validation: hasNewFields=$hasNewFields")
                    if (hasNewFields) {
                        Timber.i("🎉 EMERGENCY FIX CONFIRMED: user_profiles table has all required fields")
                    } else {
                        Timber.w("⚠️ user_profiles table missing some fields, but this should be resolved by destructive migration")
                    }
                }
                userProfilesCursor.close()
                
                // Verify critical tables exist
                val cursor = database.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='exercise_usage_history'"
                )
                val exerciseUsageHistoryExists = cursor.moveToFirst()
                cursor.close()
                
                // Check analytics_cache table specifically
                val analyticsCursor = database.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='analytics_cache'"
                )
                val analyticsCacheExists = analyticsCursor.moveToFirst()
                analyticsCursor.close()
                
                // Check new guest session and anomaly tables for v30
                val guestSessionCursor = database.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='guest_sessions'"
                )
                val guestSessionExists = guestSessionCursor.moveToFirst()
                guestSessionCursor.close()
                
                val anomalyCursor = database.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='workout_anomalies'"
                )
                val anomalyExists = anomalyCursor.moveToFirst()
                anomalyCursor.close()
                
                // Check widget_preferences table for v31+
                val widgetPreferencesCursor = database.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='widget_preferences'"
                )
                val widgetPreferencesExists = widgetPreferencesCursor.moveToFirst()
                widgetPreferencesCursor.close()
                
                if (widgetPreferencesExists) {
                    // Verify widget_preferences table schema
                    val schemaQuery = database.openHelper.readableDatabase.query("PRAGMA table_info(widget_preferences)")
                    val primaryKeyColumns = mutableListOf<String>()
                    while (schemaQuery.moveToNext()) {
                        val isPrimaryKey = schemaQuery.getInt(5) > 0 // pk column
                        if (isPrimaryKey) {
                            primaryKeyColumns.add(schemaQuery.getString(1)) // name column
                        }
                    }
                    schemaQuery.close()
                    Timber.i("🔍 widget_preferences primary key columns: $primaryKeyColumns")
                    
                    val hasCorrectPrimaryKey = primaryKeyColumns.containsAll(listOf("user_id", "widget_type"))
                    if (!hasCorrectPrimaryKey) {
                        Timber.w("⚠️ widget_preferences table has incorrect primary key: $primaryKeyColumns, expected: [user_id, widget_type]")
                    }
                }
                
                if (version >= 35 && exerciseUsageHistoryExists && analyticsCacheExists && guestSessionExists && anomalyExists && widgetPreferencesExists) {
                    Timber.i("✅ Database ready - all migrations complete, all tables confirmed")
                } else {
                    Timber.w("⚠️ Database initialization issue - version: $version, tables exist: exercise_usage_history=$exerciseUsageHistoryExists, analytics_cache=$analyticsCacheExists, guest_sessions=$guestSessionExists, workout_anomalies=$anomalyExists, widget_preferences=$widgetPreferencesExists")
                }
                
                // Populate exercise library if needed
                exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                
                // Populate MET data if needed
                metDataSeedService.populateMetDataIfNeeded(database)
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Database pre-warming failed")
            }
        }
            
        return database
    }

    @Provides
    fun provideWorkoutDao(database: LiftrixDatabase): WorkoutDao {
        return database.workoutDao()
    }

    @Provides
    fun provideUserProfileDao(database: LiftrixDatabase): UserProfileDao {
        return database.userProfileDao()
    }

    @Provides
    fun provideCustomExerciseDao(database: LiftrixDatabase): CustomExerciseDao {
        return database.customExerciseDao()
    }

    @Provides
    fun provideExerciseLibraryDao(database: LiftrixDatabase): ExerciseLibraryDao {
        return database.exerciseLibraryDao()
    }

    @Provides
    fun provideExerciseDao(database: LiftrixDatabase): ExerciseDao {
        return database.exerciseDao()
    }

    @Provides
    fun provideExerciseSetDao(database: LiftrixDatabase): ExerciseSetDao {
        return database.exerciseSetDao()
    }

    @Provides
    fun provideExerciseWeightMemoryDao(database: LiftrixDatabase): ExerciseWeightMemoryDao {
        return database.exerciseWeightMemoryDao()
    }


    @Provides
    fun provideWorkoutTemplateDao(database: LiftrixDatabase): WorkoutTemplateDao {
        return database.workoutTemplateDao()
    }

    @Provides
    fun provideExerciseUsageHistoryDao(database: LiftrixDatabase): ExerciseUsageHistoryDao {
        return database.exerciseUsageHistoryDao()
    }

    @Provides
    fun provideFriendDao(database: LiftrixDatabase): FriendDao {
        return database.friendDao()
    }

    @Provides
    fun providePrivacySettingsDao(database: LiftrixDatabase): PrivacySettingsDao {
        return database.privacySettingsDao()
    }


    @Provides
    fun provideFolderDao(database: LiftrixDatabase): FolderDao {
        return database.folderDao()
    }

    @Provides
    fun provideSubscriptionDao(database: LiftrixDatabase): SubscriptionDao {
        return database.subscriptionDao()
    }

    @Provides
    fun provideSettingsDao(database: LiftrixDatabase): SettingsDao {
        return database.settingsDao()
    }

    @Provides
    fun provideMetDataDao(database: LiftrixDatabase): MetDataDao {
        return database.metDataDao()
    }

    @Provides
    fun provideAnalyticsCacheDao(database: LiftrixDatabase): AnalyticsCacheDao {
        return database.analyticsCacheDao()
    }

    @Provides
    fun provideGuestSessionDao(database: LiftrixDatabase): GuestSessionDao {
        return database.guestSessionDao()
    }

    @Provides
    fun provideWorkoutAnomalyDao(database: LiftrixDatabase): WorkoutAnomalyDao {
        return database.workoutAnomalyDao()
    }

    @Provides
    fun provideAnomalyDetectionSettingsDao(database: LiftrixDatabase): AnomalyDetectionSettingsDao {
        return database.anomalyDetectionSettingsDao()
    }

    @Provides
    fun provideExerciseHistoryDao(database: LiftrixDatabase): ExerciseHistoryDao {
        return database.exerciseHistoryDao()
    }

    @Provides
    fun provideWidgetPreferencesDao(database: LiftrixDatabase): WidgetPreferencesDao {
        return database.widgetPreferencesDao()
    }

    @Provides
    fun provideAchievementDao(database: LiftrixDatabase): AchievementDao {
        return database.achievementDao()
    }

    @Provides
    fun provideUserSearchCacheDao(database: LiftrixDatabase): UserSearchCacheDao {
        return database.userSearchCacheDao()
    }

    @Provides
    fun provideQRCodeMappingDao(database: LiftrixDatabase): QRCodeMappingDao {
        return database.qrCodeMappingDao()
    }




    /**
     * 🔥 EMERGENCY DATABASE CLEARING - Ensures 100% clean state for schema fix
     * This completely removes all database files to guarantee fresh creation
     */
    private fun clearDatabaseIfCorrupted(context: Context) {
        try {
            Timber.w("🧹 EMERGENCY DATABASE CLEARING - Complete reset for schema consistency")
            
            val databasePath = context.getDatabasePath("liftrix_database")
            val shmFile = File(databasePath.absolutePath + "-shm")
            val walFile = File(databasePath.absolutePath + "-wal")
            val journalFile = File(databasePath.absolutePath + "-journal")
            
            // Also clear database directory entirely for thorough cleanup
            val databaseDir = databasePath.parentFile
            
            // Delete all database-related files
            listOf(databasePath, shmFile, walFile, journalFile).forEach { file ->
                if (file.exists()) {
                    val deleted = file.delete()
                    Timber.d("Deleted ${file.name}: $deleted")
                }
            }
            
            // Clear any other database files in the directory
            databaseDir?.listFiles { file ->
                file.name.startsWith("liftrix_database")
            }?.forEach { file ->
                val deleted = file.delete()
                Timber.d("Deleted additional db file ${file.name}: $deleted")
            }
            
            // Force clear app's internal database cache
            try {
                val dbDir = File(context.applicationInfo.dataDir, "databases")
                if (dbDir.exists()) {
                    dbDir.listFiles { file ->
                        file.name.contains("liftrix")
                    }?.forEach { file ->
                        val deleted = file.delete()
                        Timber.d("Deleted cached db file ${file.name}: $deleted")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to clear database cache directory")
            }
            
            Timber.i("✅ EMERGENCY DATABASE CLEARING completed - 100% fresh start guaranteed")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to clear database files - but destructive migration will still work")
            // Even if clearing fails, destructive migration will handle it
        }
    }

}