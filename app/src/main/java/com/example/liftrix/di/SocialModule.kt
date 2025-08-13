package com.example.liftrix.di

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
import com.example.liftrix.data.repository.social.SocialProfileRepositoryImpl
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.data.repository.social.FeedRepositoryImpl
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.data.repository.social.EngagementRepositoryImpl
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.data.repository.social.FollowRepositoryImpl
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.domain.service.PrivacyEnforcementService
import com.example.liftrix.domain.usecase.social.CheckUsernameAvailabilityUseCase
import com.example.liftrix.domain.usecase.social.CreateSocialProfileUseCase
import com.example.liftrix.domain.usecase.social.GetDiscoverableSocialProfilesUseCase
import com.example.liftrix.domain.usecase.social.GetSocialProfileUseCase
import com.example.liftrix.domain.usecase.social.SearchSocialProfilesUseCase
import com.example.liftrix.domain.usecase.social.UpdateSocialProfileUseCase
import com.example.liftrix.domain.validation.ProfileValidator
import com.example.liftrix.domain.service.MediaUploadService
import com.example.liftrix.data.service.MediaUploadServiceImpl
import com.example.liftrix.domain.service.FeedCacheService
import com.example.liftrix.data.service.FeedCacheServiceImpl
import com.example.liftrix.data.mapper.EngagementMapper
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
object SocialModule {

    // ========================================
    // Social DAOs
    // ========================================

    @Provides
    @Singleton
    fun provideSocialProfileDao(database: LiftrixDatabase): SocialProfileDao {
        return database.socialProfileDao()
    }

    @Provides
    @Singleton
    fun provideFollowRelationshipDao(database: LiftrixDatabase): FollowRelationshipDao {
        return database.followRelationshipDao()
    }

    @Provides
    @Singleton
    fun provideFollowRequestDao(database: LiftrixDatabase): FollowRequestDao {
        return database.followRequestDao()
    }

    @Provides
    @Singleton
    fun provideProfileViewDao(database: LiftrixDatabase): ProfileViewDao {
        return database.profileViewDao()
    }

    @Provides
    @Singleton
    fun provideGymBuddyDao(database: LiftrixDatabase): GymBuddyDao {
        return database.gymBuddyDao()
    }

    @Provides
    @Singleton
    fun provideSocialPrivacySettingsDao(database: LiftrixDatabase): SocialPrivacySettingsDao {
        return database.socialPrivacySettingsDao()
    }

    @Provides
    @Singleton
    fun provideBlockedUserDao(database: LiftrixDatabase): BlockedUserDao {
        return database.blockedUserDao()
    }

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
        blockedUserDao: BlockedUserDao
    ): SocialProfileRepository {
        return SocialProfileRepositoryImpl(socialProfileDao, blockedUserDao)
    }

    @Provides
    @Singleton
    fun provideFeedRepository(
        workoutPostDao: WorkoutPostDao,
        postLikeDao: PostLikeDao,
        savedPostDao: SavedPostDao,
        socialProfileDao: SocialProfileDao,
        workoutDao: com.example.liftrix.data.local.dao.WorkoutDao,
        workoutPostMapper: WorkoutPostMapper,
        feedCacheService: FeedCacheService
    ): FeedRepository {
        return FeedRepositoryImpl(workoutPostDao, postLikeDao, savedPostDao, socialProfileDao, workoutDao, workoutPostMapper, feedCacheService)
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
        workoutPostMapper: WorkoutPostMapper
    ): EngagementRepository {
        return EngagementRepositoryImpl(postLikeDao, postCommentDao, savedPostDao, workoutPostDao, socialProfileDao, engagementMapper, workoutPostMapper)
    }

    @Provides
    @Singleton
    fun provideFollowRepository(
        followRelationshipDao: FollowRelationshipDao,
        followRequestDao: FollowRequestDao,
        profileViewDao: ProfileViewDao,
        socialProfileDao: SocialProfileDao,
        blockedUserDao: BlockedUserDao,
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): FollowRepository {
        return FollowRepositoryImpl(followRelationshipDao, followRequestDao, profileViewDao, socialProfileDao, blockedUserDao, firestore)
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

    @Provides
    @Singleton
    fun provideCreateSocialProfileUseCase(
        repository: SocialProfileRepository,
        validator: ProfileValidator,
        getCurrentUserIdUseCase: com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
    ): CreateSocialProfileUseCase {
        return CreateSocialProfileUseCase(repository, validator, getCurrentUserIdUseCase)
    }

    @Provides
    @Singleton
    fun provideGetSocialProfileUseCase(
        repository: SocialProfileRepository,
        getCurrentUserIdUseCase: com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
    ): GetSocialProfileUseCase {
        return GetSocialProfileUseCase(repository, getCurrentUserIdUseCase)
    }

    @Provides
    @Singleton
    fun provideUpdateSocialProfileUseCase(
        repository: SocialProfileRepository,
        validator: ProfileValidator,
        getCurrentUserIdUseCase: com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
    ): UpdateSocialProfileUseCase {
        return UpdateSocialProfileUseCase(repository, validator, getCurrentUserIdUseCase)
    }

    @Provides
    @Singleton
    fun provideCheckUsernameAvailabilityUseCase(
        repository: SocialProfileRepository,
        validator: ProfileValidator
    ): CheckUsernameAvailabilityUseCase {
        return CheckUsernameAvailabilityUseCase(repository, validator)
    }

    @Provides
    @Singleton
    fun provideSearchSocialProfilesUseCase(
        repository: SocialProfileRepository,
        getCurrentUserIdUseCase: com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
    ): SearchSocialProfilesUseCase {
        return SearchSocialProfilesUseCase(repository, getCurrentUserIdUseCase)
    }

    @Provides
    @Singleton
    fun provideGetDiscoverableSocialProfilesUseCase(
        repository: SocialProfileRepository,
        getCurrentUserIdUseCase: com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
    ): GetDiscoverableSocialProfilesUseCase {
        return GetDiscoverableSocialProfilesUseCase(repository, getCurrentUserIdUseCase)
    }
    
    // ========================================
    // External Services
    // ========================================
    
    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging {
        return FirebaseMessaging.getInstance()
    }
    
    @Provides
    @Singleton
    fun provideFirebaseAnalytics(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): FirebaseAnalytics {
        return FirebaseAnalytics.getInstance(context)
    }
    
    @Provides
    @Singleton
    fun provideNotificationService(
        firebaseMessaging: FirebaseMessaging
    ): com.example.liftrix.domain.service.NotificationService {
        return com.example.liftrix.data.service.NotificationServiceImpl(firebaseMessaging)
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
        firebaseStorage: FirebaseStorage
    ): MediaUploadService {
        return MediaUploadServiceImpl(context, firebaseStorage)
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

    @Provides
    @Singleton
    fun provideWorkoutPostMapper(): WorkoutPostMapper {
        return WorkoutPostMapper()
    }
}