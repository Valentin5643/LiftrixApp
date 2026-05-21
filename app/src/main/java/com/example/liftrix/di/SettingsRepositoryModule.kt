package com.example.liftrix.di

import com.example.liftrix.billing.BillingRepository
import com.example.liftrix.billing.BillingRepositoryImpl
import com.example.liftrix.data.repository.SettingsRepositoryImpl
import com.example.liftrix.data.repository.SubscriptionRepositoryImpl
import com.example.liftrix.data.repository.SyncPreferencesRepositoryImpl
import com.example.liftrix.data.repository.UserAccountRepositoryImpl
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.repository.SubscriptionRepository
import com.example.liftrix.domain.repository.SyncPreferencesRepository
import com.example.liftrix.domain.repository.UserAccountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSyncPreferencesRepository(impl: SyncPreferencesRepositoryImpl): SyncPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: SubscriptionRepositoryImpl): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindUserAccountRepository(impl: UserAccountRepositoryImpl): UserAccountRepository

    @Binds
    @Singleton
    abstract fun bindBillingRepository(impl: BillingRepositoryImpl): BillingRepository
}
