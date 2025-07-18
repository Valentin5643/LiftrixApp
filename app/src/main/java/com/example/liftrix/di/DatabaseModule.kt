package com.example.liftrix.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.example.liftrix.data.local.seed.ExerciseLibrarySeedData
import com.example.liftrix.data.local.seed.MetDataSeedService
import com.example.liftrix.data.local.migration.MIGRATION_27_28
import com.example.liftrix.data.local.migration.MIGRATION_28_29
import com.example.liftrix.data.local.migration.MIGRATION_29_30

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
                    Timber.i("🏗️ Database created from scratch at version 29")
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
            .addMigrations(MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30)
            .fallbackToDestructiveMigration()
            .build()
            
        // Pre-warm the database to establish stable connection and complete any migrations
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // Force database initialization and verify version
                val version = database.openHelper.readableDatabase.version
                Timber.i("🔥 Database pre-warmed at version: $version")
                
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
                
                if (version == 30 && exerciseUsageHistoryExists && analyticsCacheExists && guestSessionExists && anomalyExists) {
                    Timber.i("✅ Database ready - all migrations complete, all tables confirmed")
                } else {
                    Timber.w("⚠️ Database initialization issue - version: $version, tables exist: exercise_usage_history=$exerciseUsageHistoryExists, analytics_cache=$analyticsCacheExists, guest_sessions=$guestSessionExists, workout_anomalies=$anomalyExists")
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

}