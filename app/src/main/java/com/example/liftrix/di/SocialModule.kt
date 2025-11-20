package com.example.liftrix.di

import android.content.Context
import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.FollowRequestDao
import com.example.liftrix.data.local.dao.ProfileViewDao
import com.example.liftrix.data.local.dao.GymBuddyDao
import com.example.liftrix.data.local.dao.SocialPrivacySettingsDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.PostCommentDao
import com.example.liftrix.data.local.dao.PostLikeDao
import com.example.liftrix.data.local.dao.SavedPostDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.dao.FeedCacheDao
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.UserAccountDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.repository.social.SocialProfileRepositoryImpl
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.data.repository.social.FeedRepositoryImpl
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.data.repository.social.EngagementRepositoryImpl
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.data.repository.social.FollowRepositoryImpl
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.data.repository.social.SocialPrivacySettingsRepositoryImpl
import com.example.liftrix.domain.repository.social.SocialPrivacySettingsRepository
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.service.PrivacyEnforcementService
import com.example.liftrix.domain.validation.ProfileValidator
import com.example.liftrix.domain.service.MediaUploadService
import com.example.liftrix.data.service.MediaUploadServiceImpl
import com.example.liftrix.domain.service.MediaProcessingService
import com.example.liftrix.service.MediaProcessingServiceImpl
import com.example.liftrix.domain.service.FeedCacheService
import com.example.liftrix.data.service.FeedCacheServiceImpl
import com.example.liftrix.data.mapper.EngagementMapper
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.domain.service.QRCodeService
import com.example.liftrix.service.QRCodeServiceImpl
import com.example.liftrix.domain.share.PlatformShareAdapter
import com.example.liftrix.service.share.PlatformShareAdapterImpl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for social infrastructure dependencies.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * Provides all social-related DAOs, repositories, services, and use cases.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SocialModule {

    // ========================================
    // Service Bindings
    // ========================================

    /**
     * Binds QRCodeService interface to its implementation.
     * 
     * Provides QR code generation, parsing, and validation functionality
     * for gym buddy pairing and profile sharing features.
     */
    @Binds
    @Singleton
    abstract fun bindQRCodeService(
        qrCodeServiceImpl: QRCodeServiceImpl
    ): QRCodeService

    /**
     * Binds MediaProcessingService interface to its implementation.
     * 
     * Provides media processing, compression, and thumbnail generation
     * functionality for social content sharing features.
     */
    @Binds
    @Singleton
    abstract fun bindMediaProcessingService(
        mediaProcessingServiceImpl: MediaProcessingServiceImpl
    ): MediaProcessingService

    /**
     * Binds PlatformShareAdapter interface to its implementation.
     * 
     * Provides external platform sharing functionality for social content
     * including Instagram, WhatsApp, Twitter, Facebook, Telegram, and Discord.
     */
    @Binds
    @Singleton
    abstract fun bindPlatformShareAdapter(
        platformShareAdapterImpl: PlatformShareAdapterImpl
    ): PlatformShareAdapter

    companion object {
        
        // ========================================
        // Social DAOs
        // ========================================

        // SocialProfileDao, FollowRelationshipDao, and FollowRequestDao provided by DatabaseModule

        @Provides
        @Singleton
        fun provideProfileViewDao(database: LiftrixDatabase): ProfileViewDao {
                return database.profileViewDao()
            }

    // GymBuddyDao, SocialPrivacySettingsDao, and BlockedUserDao provided by DatabaseModule

        @Provides
        @Singleton
        fun providePostCommentDao(database: LiftrixDatabase): PostCommentDao {
                return database.postCommentDao()
            }

        @Provides
        @Singleton
        fun providePostLikeDao(database: LiftrixDatabase): PostLikeDao {
                return database.postLikeDao()
            }

        @Provides
        @Singleton
        fun provideSavedPostDao(database: LiftrixDatabase): SavedPostDao {
                return database.savedPostDao()
            }

        @Provides
        @Singleton
        fun provideWorkoutPostDao(database: LiftrixDatabase): WorkoutPostDao {
                return database.workoutPostDao()
            }

        @Provides
        @Singleton
        fun provideFeedCacheDao(database: LiftrixDatabase): FeedCacheDao {
                return database.feedCacheDao()
            }

        // ========================================
        // Social Repositories
        // ========================================

        @Provides
        @Singleton
        fun provideSocialProfileRepository(
            socialProfileDao: SocialProfileDao,
            blockedUserDao: BlockedUserDao,
            userAccountDao: UserAccountDao,
            @ApplicationContext context: Context
            ): SocialProfileRepository {
                return SocialProfileRepositoryImpl(socialProfileDao, blockedUserDao, userAccountDao, context)
            }

        @Provides
        @Singleton
        fun provideFeedRepository(
            workoutPostDao: WorkoutPostDao,
            postLikeDao: PostLikeDao,
            savedPostDao: SavedPostDao,
            socialProfileDao: SocialProfileDao,
            userProfileDao: com.example.liftrix.data.local.dao.UserProfileDao,
            workoutDao: com.example.liftrix.data.local.dao.WorkoutDao,
            workoutPostMapper: WorkoutPostMapper,
            feedCacheService: FeedCacheService,
            feedCacheDao: FeedCacheDao,
            followRelationshipDao: FollowRelationshipDao,
            authRepository: com.example.liftrix.domain.repository.AuthRepository,
            @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
            ): FeedRepository {
                return FeedRepositoryImpl(workoutPostDao, postLikeDao, savedPostDao, socialProfileDao, userProfileDao, workoutDao, workoutPostMapper, feedCacheService, feedCacheDao, followRelationshipDao, authRepository, context)
            }

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
            analyticsTracker: com.example.liftrix.domain.service.AnalyticsTracker
            ): EngagementRepository {
                return EngagementRepositoryImpl(postLikeDao, postCommentDao, savedPostDao, workoutPostDao, socialProfileDao, engagementMapper, workoutPostMapper, privacyEnforcementService, analyticsTracker)
            }

        @Provides
        @Singleton
        fun provideFollowRepository(
            followRelationshipDao: FollowRelationshipDao,
            followRequestDao: FollowRequestDao,
            profileViewDao: ProfileViewDao,
            socialProfileDao: SocialProfileDao,
            blockedUserDao: BlockedUserDao,
            userProfileDao: UserProfileDao,
            safeFollowDao: com.example.liftrix.data.local.dao.SafeFollowRelationshipDaoImpl,
            firestore: com.google.firebase.firestore.FirebaseFirestore
            ): FollowRepository {
                return FollowRepositoryImpl(followRelationshipDao, followRequestDao, profileViewDao, socialProfileDao, blockedUserDao, userProfileDao, safeFollowDao, firestore)
            }

        @Provides
        @Singleton
        fun provideSocialPrivacySettingsRepository(
            socialPrivacySettingsDao: SocialPrivacySettingsDao
            ): SocialPrivacySettingsRepository {
                return SocialPrivacySettingsRepositoryImpl(socialPrivacySettingsDao)
            }

        @Provides
        @Singleton
        fun provideGymBuddyRepository(
            gymBuddyDao: GymBuddyDao
        ): com.example.liftrix.domain.repository.social.GymBuddyRepository {
            return com.example.liftrix.data.repository.social.GymBuddyRepositoryImpl(gymBuddyDao)
        }

        @Provides
        @Singleton
        fun provideNotificationRepository(): com.example.liftrix.domain.repository.NotificationRepository {
            return com.example.liftrix.data.repository.NotificationRepositoryImpl()
        }

    // ========================================
    // Social Services
    // ========================================

        @Provides
        @Singleton
        fun providePrivacyEnforcementService(
            privacySettingsDao: SocialPrivacySettingsDao,
            followRelationshipDao: FollowRelationshipDao,
            blockedUserDao: BlockedUserDao
        ): PrivacyEnforcementService {
            return PrivacyEnforcementService(
            privacySettingsDao = privacySettingsDao,
            followRelationshipDao = followRelationshipDao,
            blockedUserDao = blockedUserDao
        )
        }

    // ========================================
    // Validation
    // ========================================

        @Provides
        @Singleton
        fun provideProfileValidator(): ProfileValidator {
            return ProfileValidator()
        }

    // ========================================
    // Social Use Cases
    // ========================================
    // Note: Consolidated use cases (SocialProfileQueryUseCase, SocialProfileCommandUseCase,
    // SocialSearchUseCase, SocialRelationshipUseCase, PostEngagementUseCase) are provided
    // automatically via @Inject constructor and don't need explicit @Provides methods.

    // ========================================
    // External Services
    // ========================================
    
    // Firebase services provided by AnalyticsModule to avoid duplicate bindings
    
        @Provides
        @Singleton
        fun provideNotificationService(
            firebaseMessaging: FirebaseMessaging,
            settingsRepository: SettingsRepository
        ): com.example.liftrix.domain.service.NotificationService {
            return com.example.liftrix.data.service.NotificationServiceImpl(firebaseMessaging, settingsRepository)
        }
    
        @Provides
        @Singleton
        fun provideAnalyticsTracker(
            firebaseAnalytics: FirebaseAnalytics
        ): com.example.liftrix.domain.service.AnalyticsTracker {
            return com.example.liftrix.data.service.AnalyticsTrackerImpl(firebaseAnalytics)
        }
    
        @Provides
        @Singleton
        fun provideMediaUploadService(
            @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
            firebaseStorage: FirebaseStorage,
            firebaseAuth: FirebaseAuth
        ): MediaUploadService {
            return MediaUploadServiceImpl(context, firebaseStorage, firebaseAuth)
        }

        @Provides
        @Singleton
        fun provideFeedCacheService(
            feedCacheDao: FeedCacheDao,
            workoutPostDao: WorkoutPostDao,
            postLikeDao: PostLikeDao,
            postCommentDao: PostCommentDao,
            followRelationshipDao: FollowRelationshipDao
        ): FeedCacheService {
            return FeedCacheServiceImpl(
            feedCacheDao = feedCacheDao,
            workoutPostDao = workoutPostDao,
            postLikeDao = postLikeDao,
            postCommentDao = postCommentDao,
            followRelationshipDao = followRelationshipDao
        )
        }

    // ========================================
    // Mappers
    // ========================================

        @Provides
        @Singleton
        fun provideEngagementMapper(): EngagementMapper {
            return EngagementMapper()
        }

        }
}