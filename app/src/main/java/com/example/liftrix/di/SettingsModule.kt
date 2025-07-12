package com.example.liftrix.di

import com.example.liftrix.data.repository.SettingsRepositoryImpl
import com.example.liftrix.data.repository.SubscriptionRepositoryImpl
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.repository.SubscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for settings-related dependencies.
 * 
 * This module provides dependency injection for settings and subscription repositories
 * with proper scoping and lifecycle management. It follows the single responsibility
 * principle by separating settings-related dependencies from other domain concerns.
 * 
 * Dependencies provided:
 * - SettingsRepository: For user settings management with DataStore integration
 * - SubscriptionRepository: For subscription status and Google Play Billing integration
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    /**
     * Binds the SettingsRepository implementation to its interface.
     * 
     * This provides a singleton instance of SettingsRepository that handles
     * user settings with DataStore for immediate persistence and Room for
     * offline storage with proper synchronization.
     * 
     * @param settingsRepositoryImpl The concrete implementation
     * @return The SettingsRepository interface
     */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    /**
     * Binds the SubscriptionRepository implementation to its interface.
     * 
     * This provides a singleton instance of SubscriptionRepository that handles
     * subscription status management with Google Play Billing integration and
     * local caching for offline access.
     * 
     * @param subscriptionRepositoryImpl The concrete implementation
     * @return The SubscriptionRepository interface
     */
    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        subscriptionRepositoryImpl: SubscriptionRepositoryImpl
    ): SubscriptionRepository
}