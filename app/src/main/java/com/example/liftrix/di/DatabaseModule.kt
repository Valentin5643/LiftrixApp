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

        // Database persistence enabled - no clearing on startup

        val database = Room.databaseBuilder(
            context.applicationContext,
            LiftrixDatabase::class.java,
            "liftrix_database" // Standard database name
        )
            .setTransactionExecutor(Dispatchers.IO.asExecutor())
            .setQueryExecutor(Dispatchers.IO.asExecutor())
            .addMigrations(MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37)
            // ✅ PERSISTENCE FIX: Removed destructive migration to preserve user data
            // Only allow destructive migration on downgrade to handle edge cases
            .fallbackToDestructiveMigrationOnDowngrade()
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // WAL mode for better data persistence
            .build()
            
        // Pre-warm the database to establish stable connection and complete any migrations
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // Force database initialization
                database.openHelper.readableDatabase.version
                
                // Populate exercise library if needed
                exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                
                // Populate MET data if needed
                metDataSeedService.populateMetDataIfNeeded(database)
                
            } catch (e: Exception) {
                // Database initialization failed - continue anyway
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





}