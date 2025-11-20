package com.example.liftrix.di

import com.example.liftrix.data.repository.ProfileImageRepositoryImpl
import com.example.liftrix.domain.repository.ProfileImageRepository
import com.example.liftrix.data.service.FirebaseStorageUrlResolver
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.usecase.ValidateProfileInputUseCase
import com.example.liftrix.domain.usecase.profile.ProfileImageOperationsUseCase
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides profile-related dependencies.
 * Use cases are provided with appropriate scoping for lifecycle management.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {

    /**
     * Binds ProfileImageRepositoryImpl to ProfileImageRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindProfileImageRepository(
        profileImageRepositoryImpl: ProfileImageRepositoryImpl
    ): ProfileImageRepository

    companion object {
        /**
         * Provides ValidateProfileInputUseCase with singleton scope.
         * Singleton scope ensures consistent validation rules across all forms.
         *
         * NOTE: SaveProfileUseCase has been replaced by ProfileCommandUseCase which uses @Inject constructor.
         * ProfileCommandUseCase does not require a manual provider.
         */
        @Provides
        @Singleton
        fun provideValidateProfileInputUseCase(): ValidateProfileInputUseCase =
            ValidateProfileInputUseCase()

        /**
         * Provides ProfileImageOperationsUseCase with singleton scope.
         * Consolidates upload, delete, and sync profile image operations.
         */
        @Provides
        @Singleton
        fun provideProfileImageOperationsUseCase(
            profileImageRepository: ProfileImageRepository,
            socialProfileRepository: SocialProfileRepository
        ): ProfileImageOperationsUseCase =
            ProfileImageOperationsUseCase(
                profileImageRepository = profileImageRepository,
                socialProfileRepository = socialProfileRepository
            )

    }
} 