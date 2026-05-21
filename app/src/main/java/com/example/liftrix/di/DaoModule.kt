package com.example.liftrix.di

import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.*
import com.example.liftrix.data.mapper.WorkoutPostMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {
    @Provides fun provideWorkoutDao(database: LiftrixDatabase): WorkoutDao = database.workoutDao()
    @Provides fun provideUserProfileDao(database: LiftrixDatabase): UserProfileDao = database.userProfileDao()
    @Provides fun provideCustomExerciseDao(database: LiftrixDatabase): CustomExerciseDao = database.customExerciseDao()
    @Provides fun provideExerciseLibraryDao(database: LiftrixDatabase): ExerciseLibraryDao = database.exerciseLibraryDao()
    @Provides fun provideExerciseDao(database: LiftrixDatabase): ExerciseDao = database.exerciseDao()
    @Provides fun provideExerciseSetDao(database: LiftrixDatabase): ExerciseSetDao = database.exerciseSetDao()
    @Provides fun provideExerciseWeightMemoryDao(database: LiftrixDatabase): ExerciseWeightMemoryDao = database.exerciseWeightMemoryDao()
    @Provides fun provideWorkoutTemplateDao(database: LiftrixDatabase): WorkoutTemplateDao = database.workoutTemplateDao()
    @Provides fun provideExerciseUsageHistoryDao(database: LiftrixDatabase): ExerciseUsageHistoryDao = database.exerciseUsageHistoryDao()
    @Provides fun provideFriendDao(database: LiftrixDatabase): FriendDao = database.friendDao()
    @Provides fun providePrivacySettingsDao(database: LiftrixDatabase): PrivacySettingsDao = database.privacySettingsDao()
    @Provides fun provideFolderDao(database: LiftrixDatabase): FolderDao = database.folderDao()
    @Provides fun provideSubscriptionDao(database: LiftrixDatabase): SubscriptionDao = database.subscriptionDao()
    @Provides fun provideSettingsDao(database: LiftrixDatabase): SettingsDao = database.settingsDao()
    @Provides fun provideMetDataDao(database: LiftrixDatabase): MetDataDao = database.metDataDao()
    @Provides fun provideConsentDao(database: LiftrixDatabase): ConsentDao = database.consentDao()

    @Provides fun provideWorkoutAnomalyDao(database: LiftrixDatabase): WorkoutAnomalyDao = database.workoutAnomalyDao()
    @Provides fun provideAnomalyDetectionSettingsDao(database: LiftrixDatabase): AnomalyDetectionSettingsDao = database.anomalyDetectionSettingsDao()
    @Provides fun provideExerciseHistoryDao(database: LiftrixDatabase): ExerciseHistoryDao = database.exerciseHistoryDao()
    @Provides fun provideWidgetPreferencesDao(database: LiftrixDatabase): WidgetPreferencesDao = database.widgetPreferencesDao()
    @Provides fun provideAchievementDao(database: LiftrixDatabase): AchievementDao = database.achievementDao()
    @Provides fun provideAnalyticsCacheDao(database: LiftrixDatabase): AnalyticsCacheDao = database.analyticsCacheDao()

    @Provides fun provideUserSearchCacheDao(database: LiftrixDatabase): UserSearchCacheDao = database.userSearchCacheDao()
    @Provides fun provideQRCodeMappingDao(database: LiftrixDatabase): QRCodeMappingDao = database.qrCodeMappingDao()
    @Provides fun provideProfileViewDao(database: LiftrixDatabase): ProfileViewDao = database.profileViewDao()
    @Provides fun providePostCommentDao(database: LiftrixDatabase): PostCommentDao = database.postCommentDao()
    @Provides fun providePostLikeDao(database: LiftrixDatabase): PostLikeDao = database.postLikeDao()
    @Provides fun provideSavedPostDao(database: LiftrixDatabase): SavedPostDao = database.savedPostDao()
    @Provides fun provideWorkoutPostDao(database: LiftrixDatabase): WorkoutPostDao = database.workoutPostDao()
    @Provides fun provideFeedCacheDao(database: LiftrixDatabase): FeedCacheDao = database.feedCacheDao()
    @Provides fun provideSocialProfileDao(database: LiftrixDatabase): SocialProfileDao = database.socialProfileDao()
    @Provides fun provideFollowRelationshipDao(database: LiftrixDatabase): FollowRelationshipDao = database.followRelationshipDao()
    @Provides fun provideFollowRequestDao(database: LiftrixDatabase): FollowRequestDao = database.followRequestDao()
    @Provides fun provideGymBuddyDao(database: LiftrixDatabase): GymBuddyDao = database.gymBuddyDao()
    @Provides fun provideTemplateShareEventDao(database: LiftrixDatabase): TemplateShareEventDao = database.templateShareEventDao()
    @Provides fun providePRNotificationDao(database: LiftrixDatabase): PRNotificationDao = database.prNotificationDao()
    @Provides fun providePRReactionDao(database: LiftrixDatabase): PRReactionDao = database.prReactionDao()
    @Provides fun providePRNotificationPreferencesDao(database: LiftrixDatabase): PRNotificationPreferencesDao = database.prNotificationPreferencesDao()
    @Provides fun providePersonalRecordDao(database: LiftrixDatabase): PersonalRecordDao = database.personalRecordDao()
    @Provides fun provideSocialPrivacySettingsDao(database: LiftrixDatabase): SocialPrivacySettingsDao = database.socialPrivacySettingsDao()
    @Provides fun provideBlockedUserDao(database: LiftrixDatabase): BlockedUserDao = database.blockedUserDao()
    @Provides fun provideContentReportsDao(database: LiftrixDatabase): ContentReportsDao = database.contentReportsDao()

    @Provides fun provideFCMTokenDao(database: LiftrixDatabase): FCMTokenDao = database.fcmTokenDao()
    @Provides fun provideNotificationPreferenceDao(database: LiftrixDatabase): NotificationPreferenceDao = database.notificationPreferenceDao()
    @Provides fun provideNotificationQueueDao(database: LiftrixDatabase): NotificationQueueDao = database.notificationQueueDao()
    @Provides fun provideNotificationMuteDao(database: LiftrixDatabase): NotificationMuteDao = database.notificationMuteDao()
    @Provides fun provideNotificationHistoryDao(database: LiftrixDatabase): NotificationHistoryDao = database.notificationHistoryDao()

    @Provides fun provideUserAccountDao(database: LiftrixDatabase): UserAccountDao = database.userAccountDao()
    @Provides fun provideHelpArticleDao(database: LiftrixDatabase): HelpArticleDao = database.helpArticleDao()
    @Provides fun provideSupportTicketDao(database: LiftrixDatabase): SupportTicketDao = database.supportTicketDao()
    @Provides fun provideAppConfigDao(database: LiftrixDatabase): AppConfigDao = database.appConfigDao()
    @Provides fun provideSettingsAuditDao(database: LiftrixDatabase): SettingsAuditDao = database.settingsAuditDao()

    @Provides fun provideDataExportDao(database: LiftrixDatabase): DataExportDao = database.dataExportDao()
    @Provides fun provideDataImportDao(database: LiftrixDatabase): DataImportDao = database.dataImportDao()
    @Provides fun provideSyncQueueDao(database: LiftrixDatabase): SyncQueueDao = database.syncQueueDao()
    @Provides fun provideDeadLetterQueueDao(database: LiftrixDatabase): DeadLetterQueueDao = database.deadLetterQueueDao()
    @Provides fun provideSyncPreferencesDao(database: LiftrixDatabase): SyncPreferencesDao = database.syncPreferencesDao()
    @Provides fun provideChatPreferencesDao(database: LiftrixDatabase): ChatPreferencesDao = database.chatPreferencesDao()
    @Provides fun provideChatHistoryDao(database: LiftrixDatabase): ChatHistoryDao = database.chatHistoryDao()

    @Provides
    @Singleton
    fun provideWorkoutPostMapper(
        workoutDao: WorkoutDao,
        customExerciseDao: CustomExerciseDao
    ): WorkoutPostMapper = WorkoutPostMapper(workoutDao, customExerciseDao)
}
