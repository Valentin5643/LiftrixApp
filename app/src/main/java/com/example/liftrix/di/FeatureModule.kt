package com.example.liftrix.di

import android.content.Context
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.*
import com.example.liftrix.data.mapper.EngagementMapper
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.data.repository.ChatRepositoryImpl
import com.example.liftrix.data.repository.NotificationRepositoryImpl
import com.example.liftrix.data.repository.social.*
import com.example.liftrix.data.service.*
import com.example.liftrix.domain.repository.*
import com.example.liftrix.domain.repository.social.*
import com.example.liftrix.domain.service.*
import com.example.liftrix.domain.share.PlatformShareAdapter
import com.example.liftrix.domain.validation.ProfileValidator
import com.example.liftrix.service.MediaProcessingServiceImpl
import com.example.liftrix.service.QRCodeServiceImpl
import com.example.liftrix.service.share.PlatformShareAdapterImpl
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * FeatureModule - Feature-Specific Dependencies
 *
 * PURPOSE: Feature-specific dependencies (social, chat, notifications, export, specialized)
 *
 * CONSOLIDATES:
 * - SocialModule (28 bindings) - Social features, QR, media, privacy
 * - NotificationModule (6 bindings) - FCM, notification preferences, routing
 * - ChatModule (4 bindings) - AI chat, abuse prevention, rate limiting
 * - WidgetSyncModule (3 bindings) - Real-time sync, conflict resolution
 * - Specialized modules (LocationModule, TimerModule, StateCleanupModule)
 *
 * TOTAL BINDINGS: ~45
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureModule {

    // ========================================
    // SOCIAL FEATURES
    // ========================================

    // --- Social Services ---

    @Binds
    @Singleton
    abstract fun bindQRCodeService(impl: QRCodeServiceImpl): QRCodeService

    @Binds
    @Singleton
    abstract fun bindMediaProcessingService(impl: MediaProcessingServiceImpl): MediaProcessingService

    @Binds
    @Singleton
    abstract fun bindPlatformShareAdapter(impl: PlatformShareAdapterImpl): PlatformShareAdapter

    // ========================================
    // NOTIFICATION SYSTEM
    // ========================================

    @Binds
    @Singleton
    abstract fun bindNotificationRouter(impl: NotificationRouterImpl): NotificationRouter

    // ========================================
    // CHAT & AI SYSTEM
    // ========================================

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindAIChatService(impl: AIChatServiceImpl): AIChatService

    // ========================================
    // WIDGET SYNC & REAL-TIME
    // ========================================
    // All providers in companion object

    companion object {

        // ========================================
        // SOCIAL DAOs
        // ========================================

        @Provides
        @Singleton
        fun provideProfileViewDao(database: LiftrixDatabase): ProfileViewDao =
            database.profileViewDao()

        @Provides
        @Singleton
        fun providePostCommentDao(database: LiftrixDatabase): PostCommentDao =
            database.postCommentDao()

        @Provides
        @Singleton
        fun providePostLikeDao(database: LiftrixDatabase): PostLikeDao =
            database.postLikeDao()

        @Provides
        @Singleton
        fun provideSavedPostDao(database: LiftrixDatabase): SavedPostDao =
            database.savedPostDao()

        @Provides
        @Singleton
        fun provideWorkoutPostDao(database: LiftrixDatabase): WorkoutPostDao =
            database.workoutPostDao()

        @Provides
        @Singleton
        fun provideFeedCacheDao(database: LiftrixDatabase): FeedCacheDao =
            database.feedCacheDao()

        // ========================================
        // SOCIAL REPOSITORIES
        // ========================================

        @Provides
        @Singleton
        fun provideSocialProfileRepository(
            socialProfileDao: SocialProfileDao,
            blockedUserDao: BlockedUserDao,
            userAccountDao: UserAccountDao,
            @ApplicationContext context: Context
        ): SocialProfileRepository =
            SocialProfileRepositoryImpl(socialProfileDao, blockedUserDao, userAccountDao, context)

        @Provides
        @Singleton
        fun provideFeedRepository(
            workoutPostDao: WorkoutPostDao,
            postLikeDao: PostLikeDao,
            savedPostDao: SavedPostDao,
            socialProfileDao: SocialProfileDao,
            userProfileDao: UserProfileDao,
            workoutDao: WorkoutDao,
            workoutPostMapper: WorkoutPostMapper,
            feedCacheService: FeedCacheService,
            feedCacheDao: FeedCacheDao,
            followRelationshipDao: FollowRelationshipDao,
            authRepository: AuthRepository,
            @ApplicationContext context: Context
        ): FeedRepository =
            FeedRepositoryImpl(
                workoutPostDao, postLikeDao, savedPostDao, socialProfileDao,
                userProfileDao, workoutDao, workoutPostMapper, feedCacheService,
                feedCacheDao, followRelationshipDao, authRepository, context
            )

        @Provides
        @Singleton
        fun provideEngagementRepository(
            postLikeDao: PostLikeDao,
            postCommentDao: PostCommentDao,
            savedPostDao: SavedPostDao,
            workoutPostDao: WorkoutPostDao,
            socialProfileDao: SocialProfileDao,
            engagementMapper: EngagementMapper,
            workoutPostMapper: WorkoutPostMapper,
            privacyEnforcementService: PrivacyEnforcementService,
            analyticsTracker: AnalyticsTracker
        ): EngagementRepository =
            EngagementRepositoryImpl(
                postLikeDao, postCommentDao, savedPostDao, workoutPostDao,
                socialProfileDao, engagementMapper, workoutPostMapper,
                privacyEnforcementService, analyticsTracker
            )

        @Provides
        @Singleton
        fun provideFollowRepository(
            followRelationshipDao: FollowRelationshipDao,
            followRequestDao: FollowRequestDao,
            profileViewDao: ProfileViewDao,
            socialProfileDao: SocialProfileDao,
            blockedUserDao: BlockedUserDao,
            userProfileDao: UserProfileDao,
            safeFollowDao: SafeFollowRelationshipDaoImpl,
            firestore: FirebaseFirestore
        ): FollowRepository =
            FollowRepositoryImpl(
                followRelationshipDao, followRequestDao, profileViewDao,
                socialProfileDao, blockedUserDao, userProfileDao,
                safeFollowDao, firestore
            )

        @Provides
        @Singleton
        fun provideSocialPrivacySettingsRepository(
            socialPrivacySettingsDao: SocialPrivacySettingsDao
        ): SocialPrivacySettingsRepository =
            SocialPrivacySettingsRepositoryImpl(socialPrivacySettingsDao)

        @Provides
        @Singleton
        fun provideGymBuddyRepository(
            gymBuddyDao: GymBuddyDao
        ): GymBuddyRepository =
            GymBuddyRepositoryImpl(gymBuddyDao)

        @Provides
        @Singleton
        fun provideNotificationRepository(): NotificationRepository =
            NotificationRepositoryImpl()

        // ========================================
        // SOCIAL SERVICES
        // ========================================

        @Provides
        @Singleton
        fun providePrivacyEnforcementService(
            privacySettingsDao: SocialPrivacySettingsDao,
            followRelationshipDao: FollowRelationshipDao,
            blockedUserDao: BlockedUserDao
        ): PrivacyEnforcementService =
            PrivacyEnforcementService(
                privacySettingsDao = privacySettingsDao,
                followRelationshipDao = followRelationshipDao,
                blockedUserDao = blockedUserDao
            )

        @Provides
        @Singleton
        fun provideProfileValidator(): ProfileValidator =
            ProfileValidator()

        @Provides
        @Singleton
        fun provideNotificationService(
            firebaseMessaging: FirebaseMessaging,
            settingsRepository: SettingsRepository
        ): NotificationService =
            NotificationServiceImpl(firebaseMessaging, settingsRepository)

        @Provides
        @Singleton
        fun provideAnalyticsTracker(
            firebaseAnalytics: FirebaseAnalytics
        ): AnalyticsTracker =
            AnalyticsTrackerImpl(firebaseAnalytics)

        @Provides
        @Singleton
        fun provideMediaUploadService(
            @ApplicationContext context: Context,
            firebaseStorage: FirebaseStorage,
            firebaseAuth: FirebaseAuth
        ): MediaUploadService =
            MediaUploadServiceImpl(context, firebaseStorage, firebaseAuth)

        @Provides
        @Singleton
        fun provideFeedCacheService(
            feedCacheDao: FeedCacheDao,
            workoutPostDao: WorkoutPostDao,
            postLikeDao: PostLikeDao,
            postCommentDao: PostCommentDao,
            followRelationshipDao: FollowRelationshipDao,
            privacyEnforcementService: PrivacyEnforcementService
        ): FeedCacheService =
            FeedCacheServiceImpl(
                feedCacheDao = feedCacheDao,
                workoutPostDao = workoutPostDao,
                postLikeDao = postLikeDao,
                postCommentDao = postCommentDao,
                followRelationshipDao = followRelationshipDao,
                privacyEnforcementService = privacyEnforcementService
            )

        // ========================================
        // MAPPERS
        // ========================================

        @Provides
        @Singleton
        fun provideEngagementMapper(): EngagementMapper =
            EngagementMapper()

        // ========================================
        // NOTIFICATION REPOSITORIES & SERVICES
        // ========================================

        @Provides
        @Singleton
        fun provideNotificationPreferencesRepository(
            notificationPreferenceDao: NotificationPreferenceDao,
            notificationPreferencesMapper: com.example.liftrix.data.mapper.notifications.NotificationPreferencesMapper
        ): com.example.liftrix.domain.repository.notifications.NotificationPreferencesRepository =
            com.example.liftrix.data.repository.notifications.NotificationPreferencesRepositoryImpl(
                notificationPreferenceDao,
                notificationPreferencesMapper
            )

        @Provides
        @Singleton
        fun provideNotificationMuteRepository(
            notificationMuteDao: NotificationMuteDao
        ): com.example.liftrix.domain.repository.notifications.NotificationMuteRepository =
            com.example.liftrix.data.repository.notifications.NotificationMuteRepositoryImpl(notificationMuteDao)

        @Provides
        @Singleton
        fun provideFCMTokenRepository(
            fcmTokenDao: FCMTokenDao,
            mapper: com.example.liftrix.data.mapper.FCMTokenMapper
        ): com.example.liftrix.domain.repository.FCMTokenRepository =
            com.example.liftrix.data.repository.FCMTokenRepositoryImpl(fcmTokenDao, mapper)

        @Provides
        @Singleton
        fun provideNotificationChannelManager(
            @ApplicationContext context: Context
        ): com.example.liftrix.services.NotificationChannelManager =
            com.example.liftrix.services.NotificationChannelManager(context)

        @Provides
        @Singleton
        fun provideNotificationHandler(
            @ApplicationContext context: Context,
            notificationChannelManager: com.example.liftrix.services.NotificationChannelManager
        ): NotificationHandler =
            com.example.liftrix.data.service.NotificationHandlerImpl(context, notificationChannelManager)

        // ========================================
        // CHAT SERVICES
        // ========================================

        @Provides
        @Singleton
        fun provideAbusePreventionService(
            chatRepository: ChatRepository,
            remoteConfig: com.example.liftrix.data.remote.config.RemoteConfigManager,
            analyticsTracker: AnalyticsTracker
        ): com.example.liftrix.data.service.AbusePreventionService =
            com.example.liftrix.data.service.AbusePreventionService(
                chatRepository = chatRepository,
                remoteConfig = remoteConfig,
                analyticsTracker = analyticsTracker
            )

        @Provides
        @Singleton
        fun provideRateLimitingService(
            chatRepository: ChatRepository,
            chatHistoryDao: ChatHistoryDao,
            remoteConfig: com.example.liftrix.data.remote.config.RemoteConfigManager,
            analyticsTracker: AnalyticsTracker
        ): com.example.liftrix.data.service.RateLimitingService =
            com.example.liftrix.data.service.RateLimitingService(
                chatRepository = chatRepository,
                chatHistoryDao = chatHistoryDao,
                remoteConfig = remoteConfig,
                analyticsTracker = analyticsTracker
            )

        // ========================================
        // WIDGET SYNC & REAL-TIME
        // ========================================

        @Provides
        @Singleton
        fun provideSyncStrategy(): com.example.liftrix.service.sync.SyncStrategy =
            com.example.liftrix.service.sync.SyncStrategy.SmartPollingStrategy.forModerateWidgets()

        @Provides
        @Singleton
        fun provideConflictResolver(): com.example.liftrix.service.sync.ConflictResolver =
            com.example.liftrix.service.sync.ConflictResolver()

        @Provides
        @Singleton
        fun provideRealtimeSyncManager(
            firestore: FirebaseFirestore,
            auth: FirebaseAuth,
            @ApplicationContext context: Context,
            syncStrategy: com.example.liftrix.service.sync.SyncStrategy,
            conflictResolver: com.example.liftrix.service.sync.ConflictResolver
        ): com.example.liftrix.service.sync.RealtimeSyncManager =
            com.example.liftrix.service.sync.RealtimeSyncManager(
                firestore = firestore,
                auth = auth,
                context = context,
                syncStrategy = syncStrategy,
                conflictResolver = conflictResolver
            )
    }
}
