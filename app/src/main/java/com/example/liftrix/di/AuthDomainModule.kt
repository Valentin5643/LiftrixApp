package com.example.liftrix.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDomainModule {
    @Binds
    @Singleton
    abstract fun bindAuthCommandUseCase(
        impl: com.example.liftrix.domain.usecase.auth.AuthCommandUseCaseImpl
    ): com.example.liftrix.domain.usecase.auth.AuthCommandUseCase

    @Binds
    @Singleton
    abstract fun bindConsentManagementService(
        impl: com.example.liftrix.domain.service.ConsentManagementServiceImpl
    ): com.example.liftrix.domain.service.ConsentManagementService

    @Binds
    @Singleton
    abstract fun bindOnboardingDataStoreContract(
        impl: com.example.liftrix.domain.service.OnboardingDataStoreImpl
    ): com.example.liftrix.domain.service.OnboardingDataStore

    companion object {
        @Provides
        @Singleton
        fun provideValidateProfileInputUseCase(): com.example.liftrix.domain.usecase.ValidateProfileInputUseCase =
            com.example.liftrix.domain.usecase.ValidateProfileInputUseCase()

        @Provides
        @Singleton
        fun provideProfileImageOperationsUseCase(
            profileImageRepository: com.example.liftrix.domain.repository.ProfileImageRepository,
            socialProfileRepository: com.example.liftrix.domain.repository.social.SocialProfileRepository
        ): com.example.liftrix.domain.usecase.profile.ProfileImageOperationsUseCase =
            com.example.liftrix.domain.usecase.profile.ProfileImageOperationsUseCase(
                profileImageRepository = profileImageRepository,
                socialProfileRepository = socialProfileRepository
            )
    }
}
