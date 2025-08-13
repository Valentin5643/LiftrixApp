package com.example.liftrix.di

import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.GymBuddyDao
import com.example.liftrix.data.local.dao.SocialPrivacySettingsDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.repository.social.SocialProfileRepositoryImpl
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.service.PrivacyEnforcementService
import com.example.liftrix.domain.usecase.social.CheckUsernameAvailabilityUseCase
import com.example.liftrix.domain.usecase.social.CreateSocialProfileUseCase
import com.example.liftrix.domain.usecase.social.GetDiscoverableSocialProfilesUseCase
import com.example.liftrix.domain.usecase.social.GetSocialProfileUseCase
import com.example.liftrix.domain.usecase.social.SearchSocialProfilesUseCase
import com.example.liftrix.domain.usecase.social.UpdateSocialProfileUseCase
import com.example.liftrix.domain.validation.ProfileValidator
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
    fun provideNotificationService(
        firebaseMessaging: com.google.firebase.messaging.FirebaseMessaging
    ): com.example.liftrix.domain.service.NotificationService {
        return com.example.liftrix.data.service.NotificationServiceImpl(firebaseMessaging)
    }
    
    @Provides
    @Singleton
    fun provideAnalyticsTracker(
        firebaseAnalytics: com.google.firebase.analytics.FirebaseAnalytics
    ): com.example.liftrix.domain.service.AnalyticsTracker {
        return com.example.liftrix.data.service.AnalyticsTrackerImpl(firebaseAnalytics)
    }
}