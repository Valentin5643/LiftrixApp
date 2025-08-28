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
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.FollowRequestDao
import com.example.liftrix.data.local.dao.GymBuddyDao
import com.example.liftrix.data.local.dao.SocialPrivacySettingsDao
import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.dao.FCMTokenDao
import com.example.liftrix.data.local.dao.NotificationPreferenceDao
import com.example.liftrix.data.local.dao.NotificationQueueDao
import com.example.liftrix.data.local.dao.NotificationMuteDao
import com.example.liftrix.data.local.dao.NotificationHistoryDao
import com.example.liftrix.data.local.dao.UserAccountDao
import com.example.liftrix.data.local.dao.HelpArticleDao
import com.example.liftrix.data.local.dao.SupportTicketDao
import com.example.liftrix.data.local.dao.AppConfigDao
import com.example.liftrix.data.local.dao.SettingsAuditDao
import com.example.liftrix.data.local.dao.SyncQueueDao
import com.example.liftrix.data.local.dao.ChatPreferencesDao
import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.example.liftrix.data.local.dao.PRNotificationDao
import com.example.liftrix.data.local.dao.PRReactionDao
import com.example.liftrix.data.local.dao.PRNotificationPreferencesDao
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.data.local.seed.ExerciseLibrarySeedData
import com.example.liftrix.data.local.seed.MetDataSeedService

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
import kotlinx.coroutines.runBlocking
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
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // WAL mode for better data persistence
            // 🛡️ DATABASE LIFECYCLE: Add callback for database lifecycle events
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    Timber.d("Database created successfully")
                }
                
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Timber.d("Database opened successfully")
                }
                
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    Timber.w("Destructive migration occurred - data may have been lost")
                }
            })
            .build()
            
        // CRITICAL: Initialize database synchronously to prevent early access issues
        runBlocking(Dispatchers.IO) {
            try {
                Timber.d("Starting database initialization...")
                
                // 🔍 MIGRATION VALIDATION: Check database integrity before proceeding
                val dbVersion = try {
                    database.openHelper.readableDatabase.version
                } catch (e: Exception) {
                    Timber.e(e, "Database version check failed - possible corruption")
                    // In development, attempt recovery by clearing corrupted database
                    if (BuildConfig.DEBUG) {
                        try {
                            val dbFile = File(context.getDatabasePath("liftrix_database").absolutePath)
                            if (dbFile.exists()) {
                                dbFile.delete()
                                Timber.w("Cleared corrupted database file for recovery")
                                // Retry database initialization after clearing
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
                
                // Populate exercise library if needed
                exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                
                // Populate MET data if needed
                metDataSeedService.populateMetDataIfNeeded(database)
                
                Timber.d("Database initialization completed successfully")
                
            } catch (e: Exception) {
                Timber.e(e, "Database initialization failed - repositories may experience connection errors")
                // Continue anyway to avoid blocking app startup completely
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

    // ========================================
    // Social Infrastructure DAOs
    // ========================================

    @Provides
    fun provideSocialProfileDao(database: LiftrixDatabase): SocialProfileDao {
        return database.socialProfileDao()
    }

    @Provides
    fun provideFollowRelationshipDao(database: LiftrixDatabase): FollowRelationshipDao {
        return database.followRelationshipDao()
    }

    @Provides
    fun provideFollowRequestDao(database: LiftrixDatabase): FollowRequestDao {
        return database.followRequestDao()
    }

    @Provides
    fun provideGymBuddyDao(database: LiftrixDatabase): GymBuddyDao {
        return database.gymBuddyDao()
    }

    @Provides
    fun providePRNotificationDao(database: LiftrixDatabase): PRNotificationDao {
        return database.prNotificationDao()
    }

    @Provides
    fun providePRReactionDao(database: LiftrixDatabase): PRReactionDao {
        return database.prReactionDao()
    }

    @Provides
    fun providePRNotificationPreferencesDao(database: LiftrixDatabase): PRNotificationPreferencesDao {
        return database.prNotificationPreferencesDao()
    }

    @Provides
    fun provideSocialPrivacySettingsDao(database: LiftrixDatabase): SocialPrivacySettingsDao {
        return database.socialPrivacySettingsDao()
    }

    @Provides
    fun provideBlockedUserDao(database: LiftrixDatabase): BlockedUserDao {
        return database.blockedUserDao()
    }

    // ========================================
    // Notification System DAOs
    // ========================================

    @Provides
    fun provideFCMTokenDao(database: LiftrixDatabase): FCMTokenDao {
        return database.fcmTokenDao()
    }

    @Provides
    fun provideNotificationPreferenceDao(database: LiftrixDatabase): NotificationPreferenceDao {
        return database.notificationPreferenceDao()
    }

    @Provides
    fun provideNotificationQueueDao(database: LiftrixDatabase): NotificationQueueDao {
        return database.notificationQueueDao()
    }

    @Provides
    fun provideNotificationMuteDao(database: LiftrixDatabase): NotificationMuteDao {
        return database.notificationMuteDao()
    }

    @Provides
    fun provideNotificationHistoryDao(database: LiftrixDatabase): NotificationHistoryDao {
        return database.notificationHistoryDao()
    }

    @Provides
    fun provideUserAccountDao(database: LiftrixDatabase): UserAccountDao {
        return database.userAccountDao()
    }

    // ========================================
    // Help and Support System DAOs
    // ========================================

    @Provides
    fun provideHelpArticleDao(database: LiftrixDatabase): HelpArticleDao {
        return database.helpArticleDao()
    }

    @Provides
    fun provideSupportTicketDao(database: LiftrixDatabase): SupportTicketDao {
        return database.supportTicketDao()
    }

    @Provides
    fun provideAppConfigDao(database: LiftrixDatabase): AppConfigDao {
        return database.appConfigDao()
    }

    @Provides
    fun provideSettingsAuditDao(database: LiftrixDatabase): SettingsAuditDao {
        return database.settingsAuditDao()
    }

    // ========================================
    // Data Export/Import DAOs
    // ========================================

    @Provides
    fun provideDataExportDao(database: LiftrixDatabase): com.example.liftrix.data.local.dao.DataExportDao {
        return database.dataExportDao()
    }

    @Provides
    fun provideDataImportDao(database: LiftrixDatabase): com.example.liftrix.data.local.dao.DataImportDao {
        return database.dataImportDao()
    }

    // ========================================
    // Sync Infrastructure DAOs
    // ========================================

    @Provides
    fun provideSyncQueueDao(database: LiftrixDatabase): com.example.liftrix.data.local.dao.SyncQueueDao {
        return database.syncQueueDao()
    }
    
    @Provides
    fun provideChatPreferencesDao(database: LiftrixDatabase): ChatPreferencesDao {
        return database.chatPreferencesDao()
    }
    
    @Provides
    fun provideChatHistoryDao(database: LiftrixDatabase): ChatHistoryDao {
        return database.chatHistoryDao()
    }
    
    // ========================================
    // Mappers (DAO-dependent)
    // ========================================
    
    @Provides
    @javax.inject.Singleton
    fun provideWorkoutPostMapper(
        workoutDao: WorkoutDao,
        customExerciseDao: CustomExerciseDao
    ): WorkoutPostMapper {
        return WorkoutPostMapper(workoutDao, customExerciseDao)
    }
}