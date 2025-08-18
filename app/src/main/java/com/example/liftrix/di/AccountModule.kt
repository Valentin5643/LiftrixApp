package com.example.liftrix.di

import com.example.liftrix.data.repository.UserAccountRepositoryImpl
import com.example.liftrix.domain.repository.UserAccountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for account management components.
 * Part of SPEC-20250116-account-management implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AccountModule {

    @Binds
    @Singleton
    abstract fun bindUserAccountRepository(
        userAccountRepositoryImpl: UserAccountRepositoryImpl
    ): UserAccountRepository
}