package com.example.liftrix.di

import androidx.work.WorkManager
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.repository.SubscriptionRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.usecase.settings.EnhancedSignOutUseCase
import com.example.liftrix.domain.usecase.settings.GetSubscriptionStatusUseCase
import com.example.liftrix.domain.usecase.settings.GetUserSettingsUseCase
import com.example.liftrix.domain.usecase.settings.UpdateSettingsUseCase
import com.example.liftrix.sync.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing use case dependencies.
 * 
 * This module handles dependency injection for all use cases in the application,
 * following the single responsibility principle by separating use case injection
 * from repository and data layer injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    /**
     * Provides GetUserSettingsUseCase with proper dependency injection.
     * 
     * @param settingsRepository The settings repository dependency
     * @return Configured GetUserSettingsUseCase instance
     */
    @Provides
    @Singleton
    fun provideGetUserSettingsUseCase(
        settingsRepository: SettingsRepository
    ): GetUserSettingsUseCase {
        return GetUserSettingsUseCase(settingsRepository)
    }

    /**
     * Provides UpdateSettingsUseCase with proper dependency injection.
     * 
     * @param settingsRepository The settings repository dependency
     * @return Configured UpdateSettingsUseCase instance
     */
    @Provides
    @Singleton
    fun provideUpdateSettingsUseCase(
        settingsRepository: SettingsRepository
    ): UpdateSettingsUseCase {
        return UpdateSettingsUseCase(settingsRepository)
    }

    /**
     * Provides GetSubscriptionStatusUseCase with proper dependency injection.
     * 
     * @param subscriptionRepository The subscription repository dependency
     * @return Configured GetSubscriptionStatusUseCase instance
     */
    @Provides
    @Singleton
    fun provideGetSubscriptionStatusUseCase(
        subscriptionRepository: SubscriptionRepository
    ): GetSubscriptionStatusUseCase {
        return GetSubscriptionStatusUseCase(subscriptionRepository)
    }

    /**
     * Provides EnhancedSignOutUseCase with proper dependency injection.
     * 
     * This use case handles comprehensive logout functionality including
     * Firebase sign out, local data cleanup, analytics tracking, and
     * background service termination.
     * 
     * @param authRepository The authentication repository dependency
     * @param analyticsService The analytics service dependency
     * @param settingsRepository The settings repository dependency
     * @param syncManager The sync manager dependency
     * @param workManager The work manager dependency
     * @return Configured EnhancedSignOutUseCase instance
     */
    @Provides
    @Singleton
    fun provideEnhancedSignOutUseCase(
        authRepository: AuthRepository,
        analyticsService: AnalyticsService,
        settingsRepository: SettingsRepository,
        syncManager: SyncManager,
        workManager: WorkManager
    ): EnhancedSignOutUseCase {
        return EnhancedSignOutUseCase(
            authRepository = authRepository,
            analyticsService = analyticsService,
            settingsRepository = settingsRepository,
            syncManager = syncManager,
            workManager = workManager
        )
    }
}