package com.example.liftrix.di

import android.content.Context
import com.example.liftrix.billing.BillingClientManager
import com.example.liftrix.billing.BillingRepository
import com.example.liftrix.billing.BillingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Google Play Billing dependencies.
 * Provides billing client manager and repository implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    /**
     * Bind BillingRepositoryImpl to BillingRepository interface
     */
    @Binds
    @Singleton
    abstract fun bindBillingRepository(
        billingRepositoryImpl: BillingRepositoryImpl
    ): BillingRepository

    companion object {
        /**
         * Provide BillingClientManager singleton
         */
        @Provides
        @Singleton
        fun provideBillingClientManager(
            @ApplicationContext context: Context
        ): BillingClientManager {
            return BillingClientManager(context)
        }
    }
}