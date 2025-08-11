package com.example.liftrix.di

import com.example.liftrix.data.error.ErrorHandlerImpl
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.core.cache.CacheManager
import com.example.liftrix.core.cache.CacheConfiguration
import com.example.liftrix.core.cache.CacheKeyGenerator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.minutes
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
        
        /**
         * Provides CacheManager singleton for service-layer caching.
         * 
         * Configures cache with 200 entries max and 15-minute default TTL
         * for optimal performance in analytics data caching scenarios.
         */
        @Provides
        @Singleton
        fun provideCacheManager(): CacheManager = CacheManager(
            maxSize = 200,
            defaultTtl = 15.minutes
        )
        
        /**
         * Provides CacheConfiguration for enhanced cache system.
         * 
         * Configures enhanced cache with appropriate memory and disk limits,
         * TTL settings, and cleanup intervals for optimal performance.
         */
        @Provides
        @Singleton
        fun provideCacheConfiguration(): CacheConfiguration = CacheConfiguration(
            memoryCacheSizeMB = 50,
            diskCacheSizeMB = 200,
            defaultTTL = 15.minutes
        )
        
        /**
         * Provides CacheKeyGenerator singleton for intelligent cache key generation.
         * 
         * The CacheKeyGenerator provides structured, collision-resistant cache keys
         * with proper scoping and pattern-based invalidation support.
         */
        @Provides
        @Singleton
        fun provideCacheKeyGenerator(): CacheKeyGenerator = CacheKeyGenerator
        
    }
}