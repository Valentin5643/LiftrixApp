package com.example.liftrix.di

import android.content.Context
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.*
import com.example.liftrix.data.mapper.EngagementMapper
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.data.remote.legacy.LegacyFollowFirestoreDataSource
import com.example.liftrix.data.repository.social.*
import com.example.liftrix.data.service.*
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.domain.repository.social.GymBuddyRepository
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.repository.social.SocialPrivacySettingsRepository
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.service.FeedCacheService
import com.example.liftrix.domain.service.MediaProcessingService
import com.example.liftrix.domain.service.MediaUploadService
import com.example.liftrix.domain.service.PrivacyEnforcementService
import com.example.liftrix.domain.service.ProfileSyncService
import com.example.liftrix.domain.service.QRCodeService
import com.example.liftrix.domain.share.PlatformShareAdapter
import com.example.liftrix.domain.sync.SyncScheduler
import com.example.liftrix.domain.validation.ProfileValidator
import com.example.liftrix.service.MediaProcessingServiceImpl
import com.example.liftrix.service.QRCodeServiceImpl
import com.example.liftrix.service.share.PlatformShareAdapterImpl
import com.example.liftrix.sync.ProfileSyncServiceAdapter
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SocialFeatureModule {
    @Binds
    @Singleton
    abstract fun bindQRCodeService(impl: QRCodeServiceImpl): QRCodeService

    @Binds
    @Singleton
    abstract fun bindMediaProcessingService(impl: MediaProcessingServiceImpl): MediaProcessingService

    @Binds
    @Singleton
    abstract fun bindPlatformShareAdapter(impl: PlatformShareAdapterImpl): PlatformShareAdapter

    @Binds
    @Singleton
    abstract fun bindProfileSyncService(impl: ProfileSyncServiceAdapter): ProfileSyncService

    companion object {
        @Provides
        @Singleton
        fun provideSocialProfileRepository(
            socialProfileDao: SocialProfileDao,
            blockedUserDao: BlockedUserDao,
            userAccountDao: UserAccountDao,
            database: LiftrixDatabase,
            syncScheduler: SyncScheduler
        ): SocialProfileRepository = SocialProfileRepositoryImpl(
            socialProfileDao,
            blockedUserDao,
            userAccountDao,
            database,
            syncScheduler
        )

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
            syncScheduler: SyncScheduler
        ): FeedRepository = FeedRepositoryImpl(
            workoutPostDao, postLikeDao, savedPostDao, socialProfileDao,
            userProfileDao, workoutDao, workoutPostMapper, feedCacheService,
            feedCacheDao, followRelationshipDao, authRepository, syncScheduler
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
        ): EngagementRepository = EngagementRepositoryImpl(
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
            userAccountDao: UserAccountDao,
            safeFollowDao: SafeFollowRelationshipDaoImpl,
            legacyFollowDataSource: LegacyFollowFirestoreDataSource
        ): FollowRepository = FollowRepositoryImpl(
            followRelationshipDao, followRequestDao, profileViewDao,
            socialProfileDao, blockedUserDao, userProfileDao, userAccountDao,
            safeFollowDao, legacyFollowDataSource
        )

        @Provides
        @Singleton
        fun provideSocialPrivacySettingsRepository(
            socialPrivacySettingsDao: SocialPrivacySettingsDao,
            userProfileDao: UserProfileDao
        ): SocialPrivacySettingsRepository =
            SocialPrivacySettingsRepositoryImpl(socialPrivacySettingsDao, userProfileDao)

        @Provides
        @Singleton
        fun provideGymBuddyRepository(gymBuddyDao: GymBuddyDao): GymBuddyRepository =
            GymBuddyRepositoryImpl(gymBuddyDao)

        @Provides
        @Singleton
        fun providePrivacyEnforcementService(
            privacySettingsDao: SocialPrivacySettingsDao,
            followRelationshipDao: FollowRelationshipDao,
            blockedUserDao: BlockedUserDao
        ): PrivacyEnforcementService = com.example.liftrix.service.PrivacyEnforcementServiceImpl(
            privacySettingsDao = privacySettingsDao,
            followRelationshipDao = followRelationshipDao,
            blockedUserDao = blockedUserDao
        )

        @Provides
        @Singleton
        fun provideProfileValidator(): ProfileValidator = ProfileValidator()

        @Provides
        @Singleton
        fun provideCacheInvalidationService(
            analyticsQueryUseCase: com.example.liftrix.domain.usecase.analytics.AnalyticsQueryUseCase
        ): com.example.liftrix.service.CacheInvalidationService =
            com.example.liftrix.service.CacheInvalidationServiceImpl(
                analyticsQueryUseCase
            )

        @Provides
        @Singleton
        fun provideAnalyticsTracker(firebaseAnalytics: FirebaseAnalytics): AnalyticsTracker =
            AnalyticsTrackerImpl(firebaseAnalytics)

        @Provides
        @Singleton
        fun provideMediaUploadService(
            @ApplicationContext context: Context,
            firebaseStorage: FirebaseStorage,
            firebaseAuth: FirebaseAuth
        ): MediaUploadService = MediaUploadServiceImpl(context, firebaseStorage, firebaseAuth)

        @Provides
        @Singleton
        fun provideFeedCacheService(
            feedCacheDao: FeedCacheDao,
            workoutPostDao: WorkoutPostDao,
            postLikeDao: PostLikeDao,
            postCommentDao: PostCommentDao,
            savedPostDao: SavedPostDao,
            followRelationshipDao: FollowRelationshipDao,
            privacyEnforcementService: PrivacyEnforcementService
        ): FeedCacheService = FeedCacheServiceImpl(
            feedCacheDao = feedCacheDao,
            workoutPostDao = workoutPostDao,
            postLikeDao = postLikeDao,
            postCommentDao = postCommentDao,
            savedPostDao = savedPostDao,
            followRelationshipDao = followRelationshipDao,
            privacyEnforcementService = privacyEnforcementService
        )

        @Provides
        @Singleton
        fun provideEngagementMapper(): EngagementMapper = EngagementMapper()
    }
}
