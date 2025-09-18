package com.example.liftrix.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import com.example.liftrix.data.local.converter.UserProfileConverters
import com.example.liftrix.data.local.converter.WorkoutConverters
import com.example.liftrix.data.local.converter.ExerciseConverters
import com.example.liftrix.data.local.converter.SubscriptionConverters
import com.example.liftrix.data.local.converter.WeightUnitConverter
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.ExerciseWeightMemoryDao
import com.example.liftrix.data.local.dao.ExerciseUsageHistoryDao
import com.example.liftrix.data.local.dao.FriendDao
import com.example.liftrix.data.local.dao.PrivacySettingsDao
import com.example.liftrix.data.local.dao.FolderDao
import com.example.liftrix.data.local.dao.SettingsDao
import com.example.liftrix.data.local.dao.SubscriptionDao
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
import com.example.liftrix.data.local.dao.ProfileViewDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.PostLikeDao
import com.example.liftrix.data.local.dao.PostCommentDao
import com.example.liftrix.data.local.dao.FeedCacheDao
import com.example.liftrix.data.local.dao.SavedPostDao
import com.example.liftrix.data.local.dao.FCMTokenDao
import com.example.liftrix.data.local.dao.NotificationPreferenceDao
import com.example.liftrix.data.local.dao.NotificationQueueDao
import com.example.liftrix.data.local.dao.NotificationMuteDao
import com.example.liftrix.data.local.dao.NotificationHistoryDao
import com.example.liftrix.data.local.dao.MediaItemDao
import com.example.liftrix.data.local.dao.SharedRoutineDao
import com.example.liftrix.data.local.dao.ExternalShareDao
import com.example.liftrix.data.local.dao.ProgressPhotoDao
import com.example.liftrix.data.local.dao.QRCodeSessionDao
import com.example.liftrix.data.local.dao.PRNotificationDao
import com.example.liftrix.data.local.dao.GymBuddyActivityDao
import com.example.liftrix.data.local.dao.ContentReportsDao
import com.example.liftrix.data.local.dao.DataExportDao
import com.example.liftrix.data.local.dao.DataImportDao
import com.example.liftrix.data.local.dao.UserAccountDao
import com.example.liftrix.data.local.dao.HelpArticleDao
import com.example.liftrix.data.local.dao.SupportTicketDao
import com.example.liftrix.data.local.dao.AppConfigDao
import com.example.liftrix.data.local.dao.SettingsAuditDao
import com.example.liftrix.data.local.dao.SyncQueueDao
import com.example.liftrix.data.local.dao.DeadLetterQueueDao
import com.example.liftrix.data.local.dao.SyncPreferencesDao
import com.example.liftrix.data.local.dao.ChatPreferencesDao
import com.example.liftrix.data.local.dao.ChatHistoryDao
import com.example.liftrix.data.local.dao.PRReactionDao
import com.example.liftrix.data.local.dao.PRNotificationPreferencesDao

import com.example.liftrix.data.local.entity.UserProfileEntity
import com.example.liftrix.data.local.entity.ChatPreferencesEntity
import com.example.liftrix.data.local.entity.ChatHistoryEntity
import com.example.liftrix.data.local.entity.PRReactionEntity
import com.example.liftrix.data.local.entity.PRNotificationPreferencesEntity
import com.example.liftrix.data.local.entity.UserAccountEntity
import com.example.liftrix.data.local.entity.FolderEntity
import com.example.liftrix.data.local.entity.SettingsEntity
import com.example.liftrix.data.local.entity.SubscriptionEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.local.entity.CustomExerciseEntity
import com.example.liftrix.data.local.entity.WorkoutTemplateEntity
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.data.local.entity.ExerciseEntity
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.data.local.entity.ExerciseWeightMemoryEntity
import com.example.liftrix.data.local.entity.ExerciseUsageHistoryEntity
import com.example.liftrix.data.local.entity.FriendEntity
import com.example.liftrix.data.local.entity.PrivacySettingsEntity
import com.example.liftrix.data.local.entity.AnalyticsCacheEntity
import com.example.liftrix.data.local.entity.MetDataEntity
import com.example.liftrix.data.local.entity.GuestSessionEntity
import com.example.liftrix.data.local.entity.WorkoutAnomalyEntity
import com.example.liftrix.data.local.entity.AnomalyDetectionSettingsEntity
import com.example.liftrix.data.local.entity.ExerciseHistoryEntity
import com.example.liftrix.data.local.entity.WidgetPreferenceEntity
import com.example.liftrix.data.local.entity.DashboardConfigurationEntity
import com.example.liftrix.data.local.entity.UserAchievementEntity
import com.example.liftrix.data.local.entity.UserSearchCacheEntity
import com.example.liftrix.data.local.entity.QRCodeMappingEntity
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.data.local.entity.FollowRelationshipEntity
import com.example.liftrix.data.local.entity.FollowRequestEntity
import com.example.liftrix.data.local.entity.GymBuddyEntity
import com.example.liftrix.data.local.entity.SocialPrivacySettingsEntity
import com.example.liftrix.data.local.entity.BlockedUserEntity
import com.example.liftrix.data.local.entity.ProfileViewEntity
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.data.local.entity.PostLikeEntity
import com.example.liftrix.data.local.entity.PostCommentEntity
import com.example.liftrix.data.local.entity.FeedCacheEntity
import com.example.liftrix.data.local.entity.SavedPostEntity
import com.example.liftrix.data.local.entity.FCMTokenEntity
import com.example.liftrix.data.local.entity.NotificationPreferenceEntity
import com.example.liftrix.data.local.entity.NotificationQueueEntity
import com.example.liftrix.data.local.entity.NotificationMuteEntity
import com.example.liftrix.data.local.entity.NotificationHistoryEntity
import com.example.liftrix.data.local.entity.MediaItemEntity
import com.example.liftrix.data.local.entity.SharedRoutineEntity
import com.example.liftrix.data.local.entity.ExternalShareEntity
import com.example.liftrix.data.local.entity.ProgressPhotoEntity
import com.example.liftrix.data.local.entity.QRCodeSessionEntity
import com.example.liftrix.data.local.entity.PRNotificationEntity
import com.example.liftrix.data.local.entity.GymBuddyActivityEntity
import com.example.liftrix.data.local.entity.ContentReportEntity
import com.example.liftrix.data.local.entity.DataExportEntity
import com.example.liftrix.data.local.entity.DataImportEntity
import com.example.liftrix.data.local.entity.HelpArticleEntity
import com.example.liftrix.data.local.entity.SupportTicketEntity
import com.example.liftrix.data.local.entity.AppConfigEntity
import com.example.liftrix.data.local.entity.SettingsAuditEntity
import com.example.liftrix.data.local.entity.SyncQueueEntity
import com.example.liftrix.data.local.entity.DeadLetterQueueEntity
import com.example.liftrix.data.local.entity.SyncPreferencesEntity



@Database(
    entities = [
        WorkoutEntity::class,
        UserProfileEntity::class,
        UserAccountEntity::class,
        CustomExerciseEntity::class,
        WorkoutTemplateEntity::class,
        ExerciseLibraryEntity::class,
        ExerciseEntity::class,
        ExerciseSetEntity::class,
        ExerciseWeightMemoryEntity::class,
        ExerciseUsageHistoryEntity::class,
        FriendEntity::class,
        PrivacySettingsEntity::class,
        FolderEntity::class,
        SettingsEntity::class,
        SubscriptionEntity::class,
        AnalyticsCacheEntity::class,
        MetDataEntity::class,
        GuestSessionEntity::class,
        WorkoutAnomalyEntity::class,
        AnomalyDetectionSettingsEntity::class,
        ExerciseHistoryEntity::class,
        WidgetPreferenceEntity::class,
        DashboardConfigurationEntity::class,
        UserAchievementEntity::class,
        UserSearchCacheEntity::class,
        QRCodeMappingEntity::class,
        SocialProfileEntity::class,
        FollowRelationshipEntity::class,
        FollowRequestEntity::class,
        GymBuddyEntity::class,
        SocialPrivacySettingsEntity::class,
        BlockedUserEntity::class,
        ProfileViewEntity::class,
        WorkoutPostEntity::class,
        PostLikeEntity::class,
        PostCommentEntity::class,
        FeedCacheEntity::class,
        SavedPostEntity::class,
        FCMTokenEntity::class,
        NotificationPreferenceEntity::class,
        NotificationQueueEntity::class,
        NotificationMuteEntity::class,
        NotificationHistoryEntity::class,
        MediaItemEntity::class,
        SharedRoutineEntity::class,
        ExternalShareEntity::class,
        ProgressPhotoEntity::class,
        QRCodeSessionEntity::class,
        PRNotificationEntity::class,
        GymBuddyActivityEntity::class,
        ContentReportEntity::class,
        DataExportEntity::class,
        DataImportEntity::class,
        HelpArticleEntity::class,
        SupportTicketEntity::class,
        AppConfigEntity::class,
        SettingsAuditEntity::class,
        SyncQueueEntity::class,
        DeadLetterQueueEntity::class,
        SyncPreferencesEntity::class,
        ChatPreferencesEntity::class,
        ChatHistoryEntity::class,
        PRReactionEntity::class,
        PRNotificationPreferencesEntity::class,
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(
    DateTimeConverters::class,
    WorkoutConverters::class,
    UserProfileConverters::class,
    ExerciseConverters::class,
    SubscriptionConverters::class,
    WeightUnitConverter::class
)
abstract class LiftrixDatabase : RoomDatabase() {
    
    abstract fun workoutDao(): WorkoutDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun customExerciseDao(): CustomExerciseDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun exerciseLibraryDao(): ExerciseLibraryDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun exerciseSetDao(): ExerciseSetDao
    abstract fun exerciseWeightMemoryDao(): ExerciseWeightMemoryDao
    abstract fun exerciseUsageHistoryDao(): ExerciseUsageHistoryDao
    abstract fun friendDao(): FriendDao
    abstract fun privacySettingsDao(): PrivacySettingsDao
    abstract fun folderDao(): FolderDao
    abstract fun settingsDao(): SettingsDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun metDataDao(): MetDataDao
    abstract fun analyticsCacheDao(): AnalyticsCacheDao
    abstract fun guestSessionDao(): GuestSessionDao
    abstract fun workoutAnomalyDao(): WorkoutAnomalyDao
    abstract fun anomalyDetectionSettingsDao(): AnomalyDetectionSettingsDao
    abstract fun exerciseHistoryDao(): ExerciseHistoryDao
    abstract fun widgetPreferencesDao(): WidgetPreferencesDao
    abstract fun achievementDao(): AchievementDao
    abstract fun userSearchCacheDao(): UserSearchCacheDao
    abstract fun qrCodeMappingDao(): QRCodeMappingDao
    abstract fun socialProfileDao(): SocialProfileDao
    abstract fun followRelationshipDao(): FollowRelationshipDao
    abstract fun followRequestDao(): FollowRequestDao
    abstract fun gymBuddyDao(): GymBuddyDao
    abstract fun socialPrivacySettingsDao(): SocialPrivacySettingsDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun profileViewDao(): ProfileViewDao
    abstract fun workoutPostDao(): WorkoutPostDao
    abstract fun postLikeDao(): PostLikeDao
    abstract fun postCommentDao(): PostCommentDao
    abstract fun feedCacheDao(): FeedCacheDao
    abstract fun savedPostDao(): SavedPostDao
    abstract fun fcmTokenDao(): FCMTokenDao
    abstract fun notificationPreferenceDao(): NotificationPreferenceDao
    abstract fun notificationQueueDao(): NotificationQueueDao
    abstract fun notificationMuteDao(): NotificationMuteDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun sharedRoutineDao(): SharedRoutineDao
    abstract fun externalShareDao(): ExternalShareDao
    abstract fun progressPhotoDao(): ProgressPhotoDao
    abstract fun qrCodeSessionDao(): QRCodeSessionDao
    abstract fun prNotificationDao(): PRNotificationDao
    abstract fun gymBuddyActivityDao(): GymBuddyActivityDao
    abstract fun contentReportsDao(): ContentReportsDao
    abstract fun dataExportDao(): DataExportDao
    abstract fun dataImportDao(): DataImportDao
    abstract fun helpArticleDao(): HelpArticleDao
    abstract fun supportTicketDao(): SupportTicketDao
    abstract fun appConfigDao(): AppConfigDao
    abstract fun settingsAuditDao(): SettingsAuditDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun deadLetterQueueDao(): DeadLetterQueueDao
    abstract fun syncPreferencesDao(): SyncPreferencesDao
    abstract fun chatPreferencesDao(): ChatPreferencesDao
    abstract fun chatHistoryDao(): ChatHistoryDao
    abstract fun prReactionDao(): PRReactionDao
    abstract fun prNotificationPreferencesDao(): PRNotificationPreferencesDao
}