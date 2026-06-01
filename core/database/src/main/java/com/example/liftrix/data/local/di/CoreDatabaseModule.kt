package com.example.liftrix.data.local.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.liftrix.data.local.DatabasePassphraseProvider
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.*
import com.example.liftrix.data.local.migrations.MIGRATION_7_8
import com.example.liftrix.data.local.migrations.MIGRATION_8_9
import com.example.liftrix.data.local.migrations.MIGRATION_9_10
import com.example.liftrix.data.local.migrations.MIGRATION_10_11
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreDatabaseModule {
    private const val ENCRYPTED_DATABASE_NAME = LiftrixDatabase.DATABASE_NAME

    @Provides
    @Singleton
    fun provideLiftrixDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider
    ): LiftrixDatabase {
        System.loadLibrary("sqlcipher")

        val database = Room.databaseBuilder(
            context.applicationContext,
            LiftrixDatabase::class.java,
            ENCRYPTED_DATABASE_NAME
        )
            .openHelperFactory(SupportOpenHelperFactory(passphraseProvider.getPassphrase()))
            .setTransactionExecutor(Dispatchers.IO.asExecutor())
            .setQueryExecutor(Dispatchers.IO.asExecutor())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    Timber.d("Encrypted database created successfully")
                    logSqlCipherVersion(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    repairSettingsUpdatedAt(db)
                    Timber.d("Encrypted database opened successfully")
                }
            })
            .build()

        return database
    }

    @Provides fun provideWorkoutDao(database: LiftrixDatabase): WorkoutDao = database.workoutDao()
    @Provides fun provideUserProfileDao(database: LiftrixDatabase): UserProfileDao = database.userProfileDao()
    @Provides fun provideUserAccountDao(database: LiftrixDatabase): UserAccountDao = database.userAccountDao()
    @Provides fun provideCustomExerciseDao(database: LiftrixDatabase): CustomExerciseDao = database.customExerciseDao()
    @Provides fun provideWorkoutTemplateDao(database: LiftrixDatabase): WorkoutTemplateDao = database.workoutTemplateDao()
    @Provides fun provideExerciseLibraryDao(database: LiftrixDatabase): ExerciseLibraryDao = database.exerciseLibraryDao()
    @Provides fun provideExerciseDao(database: LiftrixDatabase): ExerciseDao = database.exerciseDao()
    @Provides fun provideExerciseSetDao(database: LiftrixDatabase): ExerciseSetDao = database.exerciseSetDao()
    @Provides fun provideExerciseWeightMemoryDao(database: LiftrixDatabase): ExerciseWeightMemoryDao = database.exerciseWeightMemoryDao()
    @Provides fun provideExerciseUsageHistoryDao(database: LiftrixDatabase): ExerciseUsageHistoryDao = database.exerciseUsageHistoryDao()
    @Provides fun provideFriendDao(database: LiftrixDatabase): FriendDao = database.friendDao()
    @Provides fun providePrivacySettingsDao(database: LiftrixDatabase): PrivacySettingsDao = database.privacySettingsDao()
    @Provides fun provideFolderDao(database: LiftrixDatabase): FolderDao = database.folderDao()
    @Provides fun provideSettingsDao(database: LiftrixDatabase): SettingsDao = database.settingsDao()
    @Provides fun provideSubscriptionDao(database: LiftrixDatabase): SubscriptionDao = database.subscriptionDao()
    @Provides fun provideMetDataDao(database: LiftrixDatabase): MetDataDao = database.metDataDao()
    @Provides fun provideAnalyticsCacheDao(database: LiftrixDatabase): AnalyticsCacheDao = database.analyticsCacheDao()
    @Provides fun provideGuestSessionDao(database: LiftrixDatabase): GuestSessionDao = database.guestSessionDao()
    @Provides fun provideWorkoutAnomalyDao(database: LiftrixDatabase): WorkoutAnomalyDao = database.workoutAnomalyDao()
    @Provides fun provideAnomalyDetectionSettingsDao(database: LiftrixDatabase): AnomalyDetectionSettingsDao = database.anomalyDetectionSettingsDao()
    @Provides fun provideExerciseHistoryDao(database: LiftrixDatabase): ExerciseHistoryDao = database.exerciseHistoryDao()
    @Provides fun provideWidgetPreferencesDao(database: LiftrixDatabase): WidgetPreferencesDao = database.widgetPreferencesDao()
    @Provides fun provideAchievementDao(database: LiftrixDatabase): AchievementDao = database.achievementDao()
    @Provides fun provideUserSearchCacheDao(database: LiftrixDatabase): UserSearchCacheDao = database.userSearchCacheDao()
    @Provides fun provideQRCodeMappingDao(database: LiftrixDatabase): QRCodeMappingDao = database.qrCodeMappingDao()
    @Provides fun provideSocialProfileDao(database: LiftrixDatabase): SocialProfileDao = database.socialProfileDao()
    @Provides fun provideFollowRelationshipDao(database: LiftrixDatabase): FollowRelationshipDao = database.followRelationshipDao()
    @Provides fun provideFollowRequestDao(database: LiftrixDatabase): FollowRequestDao = database.followRequestDao()
    @Provides fun provideGymBuddyDao(database: LiftrixDatabase): GymBuddyDao = database.gymBuddyDao()
    @Provides fun provideSocialPrivacySettingsDao(database: LiftrixDatabase): SocialPrivacySettingsDao = database.socialPrivacySettingsDao()
    @Provides fun provideBlockedUserDao(database: LiftrixDatabase): BlockedUserDao = database.blockedUserDao()
    @Provides fun provideProfileViewDao(database: LiftrixDatabase): ProfileViewDao = database.profileViewDao()
    @Provides fun provideWorkoutPostDao(database: LiftrixDatabase): WorkoutPostDao = database.workoutPostDao()
    @Provides fun providePostLikeDao(database: LiftrixDatabase): PostLikeDao = database.postLikeDao()
    @Provides fun providePostCommentDao(database: LiftrixDatabase): PostCommentDao = database.postCommentDao()
    @Provides fun provideFeedCacheDao(database: LiftrixDatabase): FeedCacheDao = database.feedCacheDao()
    @Provides fun provideSavedPostDao(database: LiftrixDatabase): SavedPostDao = database.savedPostDao()
    @Provides fun provideFCMTokenDao(database: LiftrixDatabase): FCMTokenDao = database.fcmTokenDao()
    @Provides fun provideNotificationPreferenceDao(database: LiftrixDatabase): NotificationPreferenceDao = database.notificationPreferenceDao()
    @Provides fun provideNotificationQueueDao(database: LiftrixDatabase): NotificationQueueDao = database.notificationQueueDao()
    @Provides fun provideNotificationMuteDao(database: LiftrixDatabase): NotificationMuteDao = database.notificationMuteDao()
    @Provides fun provideNotificationHistoryDao(database: LiftrixDatabase): NotificationHistoryDao = database.notificationHistoryDao()
    @Provides fun provideMediaItemDao(database: LiftrixDatabase): MediaItemDao = database.mediaItemDao()
    @Provides fun provideSharedRoutineDao(database: LiftrixDatabase): SharedRoutineDao = database.sharedRoutineDao()
    @Provides fun provideExternalShareDao(database: LiftrixDatabase): ExternalShareDao = database.externalShareDao()
    @Provides fun provideProgressPhotoDao(database: LiftrixDatabase): ProgressPhotoDao = database.progressPhotoDao()
    @Provides fun provideQRCodeSessionDao(database: LiftrixDatabase): QRCodeSessionDao = database.qrCodeSessionDao()
    @Provides fun providePRNotificationDao(database: LiftrixDatabase): PRNotificationDao = database.prNotificationDao()
    @Provides fun provideGymBuddyActivityDao(database: LiftrixDatabase): GymBuddyActivityDao = database.gymBuddyActivityDao()
    @Provides fun provideContentReportsDao(database: LiftrixDatabase): ContentReportsDao = database.contentReportsDao()
    @Provides fun provideDataExportDao(database: LiftrixDatabase): DataExportDao = database.dataExportDao()
    @Provides fun provideDataImportDao(database: LiftrixDatabase): DataImportDao = database.dataImportDao()
    @Provides fun provideHelpArticleDao(database: LiftrixDatabase): HelpArticleDao = database.helpArticleDao()
    @Provides fun provideSupportTicketDao(database: LiftrixDatabase): SupportTicketDao = database.supportTicketDao()
    @Provides fun provideAppConfigDao(database: LiftrixDatabase): AppConfigDao = database.appConfigDao()
    @Provides fun provideSettingsAuditDao(database: LiftrixDatabase): SettingsAuditDao = database.settingsAuditDao()
    @Provides fun provideSyncQueueDao(database: LiftrixDatabase): SyncQueueDao = database.syncQueueDao()
    @Provides fun provideDeadLetterQueueDao(database: LiftrixDatabase): DeadLetterQueueDao = database.deadLetterQueueDao()
    @Provides fun provideSyncPreferencesDao(database: LiftrixDatabase): SyncPreferencesDao = database.syncPreferencesDao()
    @Provides fun provideChatPreferencesDao(database: LiftrixDatabase): ChatPreferencesDao = database.chatPreferencesDao()
    @Provides fun provideChatHistoryDao(database: LiftrixDatabase): ChatHistoryDao = database.chatHistoryDao()
    @Provides fun providePRReactionDao(database: LiftrixDatabase): PRReactionDao = database.prReactionDao()
    @Provides fun providePRNotificationPreferencesDao(database: LiftrixDatabase): PRNotificationPreferencesDao = database.prNotificationPreferencesDao()
    @Provides fun providePersonalRecordDao(database: LiftrixDatabase): PersonalRecordDao = database.personalRecordDao()
    @Provides fun provideConsentDao(database: LiftrixDatabase): ConsentDao = database.consentDao()
    @Provides fun provideAccountRestrictionDao(database: LiftrixDatabase): AccountRestrictionDao = database.accountRestrictionDao()
    @Provides fun provideModerationActionDao(database: LiftrixDatabase): ModerationActionDao = database.moderationActionDao()
    @Provides fun provideTemplateShareEventDao(database: LiftrixDatabase): TemplateShareEventDao = database.templateShareEventDao()
    @Provides fun provideAnalyticsReadModelDao(database: LiftrixDatabase): AnalyticsReadModelDao = database.analyticsReadModelDao()

    private fun repairSettingsUpdatedAt(db: SupportSQLiteDatabase) {
        try {
            db.execSQL(
                """
                UPDATE user_settings
                SET updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
                WHERE updated_at IS NULL OR TRIM(updated_at) = ''
                """.trimIndent()
            )
        } catch (error: Exception) {
            Timber.w(error, "Failed to repair null user_settings.updated_at values")
        }
    }

    private fun logSqlCipherVersion(db: SupportSQLiteDatabase) {
        try {
            db.query("PRAGMA cipher_version;").use { result ->
                if (result.moveToFirst()) {
                    Timber.i("SQLCipher version: ${result.getString(0)} - encryption active")
                }
            }
        } catch (error: Exception) {
            Timber.e(error, "Failed to verify SQLCipher encryption")
        }
    }
}
