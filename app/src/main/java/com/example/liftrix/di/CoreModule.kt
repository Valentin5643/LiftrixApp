package com.example.liftrix.di

import com.example.liftrix.data.error.ErrorHandlerImpl
import com.example.liftrix.domain.usecase.common.ErrorHandler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for Default dispatcher used for CPU-intensive operations.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * Qualifier for IO dispatcher used for I/O operations.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Qualifier for app-lifetime background work.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Dagger Hilt module for core application services and utilities.
 * 
 * Provides dependency injection bindings for fundamental application components
 * including error handling, logging, dispatcher configuration, and other core infrastructure services.
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
    
    companion object {
        
        /**
         * Provides Default dispatcher for CPU-intensive operations.
         * 
         * Used for computational tasks like analytics calculations, data processing,
         * and other CPU-bound operations that should not block the main thread.
         */
        @Provides
        @DefaultDispatcher
        fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
        
        /**
         * Provides IO dispatcher for I/O operations.
         * 
         * Used for database operations, network requests, file operations,
         * and other I/O-bound operations that should not block the main thread.
         */
        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun provideCoroutineExceptionHandler(): CoroutineExceptionHandler =
            CoroutineExceptionHandler { _, throwable ->
                Timber.e(throwable, "Unhandled application coroutine failure")
            }

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(
            @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
            exceptionHandler: CoroutineExceptionHandler
        ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher + exceptionHandler)

        @Provides
        @Singleton
        fun provideUnqualifiedApplicationScope(
            @ApplicationScope applicationScope: CoroutineScope
        ): CoroutineScope = applicationScope
        
    }
}
