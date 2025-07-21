package com.example.liftrix.di

import android.content.Context
import com.example.liftrix.core.cache.CacheManager
import com.example.liftrix.domain.usecase.analytics.GetDashboardConfigurationUseCase
import com.example.liftrix.domain.usecase.analytics.RefreshWidgetDataUseCase
import com.example.liftrix.service.cache.CacheStrategy
import com.example.liftrix.service.cache.CacheStrategyImpl
import com.example.liftrix.service.cache.DiskCache
import com.example.liftrix.service.cache.WidgetCacheManager
import com.example.liftrix.ui.progress.components.AnalyticsWidgetManager
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton
import com.example.liftrix.di.IoDispatcher

/**
 * Dagger Hilt module for analytics dashboard components with proper scoping.
 * 
 * This module provides dependency injection bindings for analytics dashboard-specific
 * components following Clean Architecture principles and existing DI patterns.
 * 
 * Components Provided:
 * - Dashboard configuration management
 * - Widget refresh operations with caching
 * - Analytics widget coordination
 * - Multi-tier caching system (DiskCache, CacheStrategy, WidgetCacheManager)
 * 
 * Note: GetWidgetDataUseCase and SaveWidgetPreferencesUseCase are provided by
 * existing modules (UseCaseModule) to avoid duplication.
 * 
 * Scope Management:
 * - All components are Singleton scoped for consistent state
 * - Integration with existing service and repository layers
 * - Proper dependency coordination with cache and sync modules
 * 
 * Technical Implementation:
 * - Follows established Hilt module patterns from existing codebase
 * - Uses @Provides for concrete implementations requiring complex instantiation
 * - Integrates with IoDispatcher for background operations where appropriate
 * - Maintains proper separation of concerns between dashboard and service layers
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsDashboardModule {
    
    /**
     * Provides DiskCache implementation for persistent widget data storage.
     * 
     * The DiskCache provides cross-session persistence for widget data with:
     * - LRU eviction policy aligned with disk space management
     * - TTL-based expiration matching widget complexity requirements
     * - Efficient serialization for analytics data structures
     * - Background cleanup and maintenance operations
     * 
     * @param context Application context for cache directory access
     * @return Configured DiskCache instance with 100MB storage limit
     */
    @Provides
    @Singleton
    fun provideDiskCache(
        @ApplicationContext context: Context,
        gson: Gson,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): DiskCache {
        return DiskCache(
            context = context,
            gson = gson,
            ioDispatcher = ioDispatcher
        )
    }
    
    /**
     * Provides CacheStrategy implementation for widget complexity-based TTL management.
     * 
     * The CacheStrategy provides intelligent TTL calculation based on:
     * - Widget complexity levels (SIMPLE: 15-30min, MODERATE: 60-120min, COMPLEX: 240-720min)
     * - User activity patterns and data freshness requirements
     * - Performance optimization for frequent vs. infrequent widget access
     * - Memory pressure and storage constraints
     * 
     * @return Configured CacheStrategy instance with complexity mappings
     */
    @Provides
    @Singleton
    fun provideCacheStrategy(): CacheStrategy {
        return CacheStrategyImpl()
    }
    
    /**
     * Provides WidgetCacheManager implementation for coordinated multi-tier caching.
     * 
     * The WidgetCacheManager coordinates between memory and disk caches with:
     * - Multi-tier cache hierarchy (memory → disk → computation)
     * - User-scoped cache operations for data isolation
     * - Performance monitoring and optimization
     * - Smart invalidation patterns for preference and data updates
     * 
     * @param memoryCache Core cache manager for in-memory operations
     * @param diskCache Persistent cache for cross-session storage
     * @param cacheStrategy Widget complexity-based caching strategy
     * @param ioDispatcher IO dispatcher for background cache operations
     * @return Configured WidgetCacheManager instance
     */
    @Provides
    @Singleton
    fun provideWidgetCacheManager(
        memoryCache: CacheManager,
        diskCache: DiskCache,
        cacheStrategy: CacheStrategy,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): WidgetCacheManager {
        return WidgetCacheManager(
            memoryCache = memoryCache,
            diskCache = diskCache,
            cacheStrategy = cacheStrategy,
            ioDispatcher = ioDispatcher
        )
    }
    
    /**
     * Provides RefreshWidgetDataUseCase for batch widget refresh operations.
     * 
     * This use case handles batch refresh operations for multiple widgets
     * with performance optimization and error handling. Uses background
     * processing for non-blocking UI operations.
     * 
     * @param analyticsService Service for analytics data coordination
     * @param widgetCacheManager Cache manager for invalidation and refresh
     * @param ioDispatcher IO dispatcher for background operations
     * @return RefreshWidgetDataUseCase implementation instance
     */
    @Provides
    @Singleton
    fun provideRefreshWidgetDataUseCase(
        analyticsService: com.example.liftrix.service.AnalyticsService
    ): RefreshWidgetDataUseCase = RefreshWidgetDataUseCase(
        analyticsService = analyticsService
    )
    
    /**
     * Provides GetDashboardConfigurationUseCase for layout and preferences.
     * 
     * This use case handles dashboard configuration retrieval including
     * widget visibility, layout modes, and user-specific preferences
     * with proper user scoping and validation.
     * 
     * @param widgetPreferencesRepository Repository for widget preferences persistence
     * @param analyticsWidgetManager Manager for widget configuration
     * @return GetDashboardConfigurationUseCase implementation instance
     */
    @Provides
    @Singleton
    fun provideGetDashboardConfigurationUseCase(
        widgetPreferencesRepository: com.example.liftrix.domain.repository.WidgetPreferencesRepository,
        analyticsService: com.example.liftrix.service.AnalyticsService
    ): GetDashboardConfigurationUseCase = GetDashboardConfigurationUseCase(
        widgetPreferencesRepository = widgetPreferencesRepository,
        analyticsService = analyticsService
    )
}