package com.example.liftrix.di

import com.example.liftrix.data.error.ErrorHandlerImpl
import com.example.liftrix.domain.usecase.common.ErrorHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for core application services and utilities.
 * 
 * Provides dependency injection bindings for fundamental application components
 * including error handling, logging, and other core infrastructure services.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {
    
    /**
     * Binds ErrorHandler interface to its implementation.
     * 
     * Provides singleton ErrorHandler instance for centralized error processing
     * throughout the application. The ErrorHandlerImpl integrates with:
     * - AnalyticsService for error reporting to Firebase Crashlytics
     * - Timber for structured error logging
     * - ErrorMapper for user-friendly error messages
     * - RetryPolicyFactory for intelligent retry logic
     */
    @Binds
    @Singleton
    abstract fun bindErrorHandler(
        errorHandlerImpl: ErrorHandlerImpl
    ): ErrorHandler
}