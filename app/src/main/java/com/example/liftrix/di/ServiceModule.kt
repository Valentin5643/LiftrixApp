package com.example.liftrix.di

import com.example.liftrix.data.service.WeightMemoryServiceImpl
import com.example.liftrix.domain.model.analytics.CalorieCalculator
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.MetDataRepository
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.UserRepository
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.service.WeightMemoryService
import com.example.liftrix.domain.repository.backup.BackupService
import com.example.liftrix.domain.repository.sync.SyncService
import com.example.liftrix.data.service.BackupServiceImpl
import com.example.liftrix.data.service.SyncServiceImpl
import com.example.liftrix.service.AnalyticsEngine
import com.example.liftrix.service.AnalyticsService
import com.example.liftrix.service.AnalyticsServiceImpl
import com.example.liftrix.service.CalorieService
import com.example.liftrix.service.CalorieServiceImpl
import com.example.liftrix.service.FeatureFlagService
import com.example.liftrix.service.FeatureFlagServiceImpl
import com.example.liftrix.service.PreferencesService
import com.example.liftrix.service.PreferencesServiceImpl
import com.example.liftrix.service.ProgressDataService
import com.example.liftrix.service.ProgressDataServiceImpl
import com.example.liftrix.domain.service.PRDetectionService
import com.example.liftrix.service.PRDetectionServiceImpl
import com.example.liftrix.ui.progress.components.AnalyticsWidgetManager
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * Dagger Hilt module for service layer dependencies with proper scoping.
 * 
 * This module provides dependency injection bindings for all service layer components
 * following Clean Architecture principles with proper dependency injection and scoping.
 * 
 * Services Provided:
 * - ProgressDataService: Handles progress data operations with repository abstraction
 * - AnalyticsService: Manages analytics widget data and preferences
 * - CalorieService: Provides MET-based calorie calculations and aggregation
 * - PreferencesService: Manages user preferences with atomic updates
 * - FeatureFlagService: Handles feature flags and A/B testing via Firebase Remote Config
 * - WeightMemoryService: Existing service for weight memory functionality
 * 
 * All services are provided as Singleton instances to ensure consistent state and
 * performance optimization throughout the application lifecycle.
 * 
 * Technical Implementation:
 * - Uses @IoDispatcher for background operations where appropriate
 * - Follows existing DI patterns established in CoreModule
 * - Maintains proper separation of concerns between service and repository layers
 * - Integrates with Firebase services for remote configuration management
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    
    /**
     * Binds WeightMemoryService interface to its implementation.
     * 
     * Existing binding for weight memory functionality.
     */
    @Binds
    @Singleton
    abstract fun bindWeightMemoryService(
        weightMemoryServiceImpl: WeightMemoryServiceImpl
    ): WeightMemoryService
    
    /**
     * Binds BackupService interface to its implementation.
     * 
     * Provides backup and restoration capabilities for error recovery scenarios.
     */
    @Binds
    @Singleton
    abstract fun bindBackupService(
        backupServiceImpl: BackupServiceImpl
    ): BackupService
    
    /**
     * Binds SyncService interface to its implementation.
     * 
     * Provides sync operation queuing and retry logic for offline scenarios.
     */
    @Binds
    @Singleton
    abstract fun bindSyncService(
        syncServiceImpl: SyncServiceImpl
    ): SyncService
    
    /**
     * Binds PRDetectionService interface to its implementation.
     * 
     * Provides personal record detection for workout achievements.
     */
    @Binds
    @Singleton
    abstract fun bindPRDetectionService(
        prDetectionServiceImpl: PRDetectionServiceImpl
    ): PRDetectionService
    
    companion object {
        
        /**
         * Provides ProgressDataService implementation with repository dependencies.
         * 
         * The ProgressDataService handles progress data operations including volume,
         * duration, and frequency data retrieval with proper caching and error handling.
         * Uses IoDispatcher for background database operations.
         * 
         * @param progressStatsRepository Repository for progress statistics data access
         * @param ioDispatcher IO dispatcher for background database operations
         * @return ProgressDataService implementation instance
         */
        @Provides
        @Singleton
        fun provideProgressDataService(
            progressStatsRepository: ProgressStatsRepository,
            cacheManager: com.example.liftrix.core.cache.EnhancedCacheManager,
            @IoDispatcher ioDispatcher: CoroutineDispatcher
        ): ProgressDataService = ProgressDataServiceImpl(
            progressStatsRepository = progressStatsRepository,
            cacheManager = cacheManager,
            ioDispatcher = ioDispatcher
        )
        
        /**
         * Provides AnalyticsService implementation with widget management dependencies.
         * 
         * The AnalyticsService handles widget data retrieval, preference management,
         * and widget visibility operations with comprehensive error handling and
         * validation mechanisms. Uses IoDispatcher for background database operations.
         * 
         * @param widgetManager Analytics widget configuration manager
         * @param preferencesRepository Repository for widget preferences persistence
         * @param analyticsEngine Engine for analytics calculations and data processing
         * @param cacheManager Cache manager for widget data caching
         * @param ioDispatcher IO dispatcher for background database operations
         * @return AnalyticsService implementation instance
         */
        @Provides
        @Singleton
        fun provideAnalyticsService(
            widgetManager: AnalyticsWidgetManager,
            preferencesRepository: WidgetPreferencesRepository,
            analyticsEngine: AnalyticsEngine,
            cacheManager: com.example.liftrix.core.cache.CacheManager,
            widgetCacheManager: com.example.liftrix.service.cache.WidgetCacheManager,
            realtimeSyncManager: com.example.liftrix.service.sync.RealtimeSyncManager,
            getWidgetDataUseCase: com.example.liftrix.domain.usecase.analytics.GetWidgetDataUseCase,
            @IoDispatcher ioDispatcher: CoroutineDispatcher
        ): AnalyticsService = AnalyticsServiceImpl(
            widgetManager = widgetManager,
            preferencesRepository = preferencesRepository,
            analyticsEngine = analyticsEngine,
            cacheManager = cacheManager,
            widgetCacheManager = widgetCacheManager,
            realtimeSyncManager = realtimeSyncManager,
            getWidgetDataUseCase = getWidgetDataUseCase,
            ioDispatcher = ioDispatcher
        )
        
        /**
         * Provides CalorieService implementation with MET-based calculation dependencies.
         * 
         * The CalorieService handles calorie tracking, MET-based calculations, and
         * temporal data aggregation with proper user profile integration for
         * personalized calorie estimations.
         * 
         * @param calorieCalculator Engine for MET-based calorie calculations
         * @param metDataRepository Repository for MET data access
         * @param workoutRepository Repository for workout data access
         * @param userRepository Repository for user profile data access
         * @param ioDispatcher IO dispatcher for background processing
         * @return CalorieService implementation instance
         */
        @Provides
        @Singleton
        fun provideCalorieService(
            calorieCalculator: CalorieCalculator,
            metDataRepository: MetDataRepository,
            workoutRepository: WorkoutRepository,
            userRepository: UserRepository,
            @IoDispatcher ioDispatcher: CoroutineDispatcher
        ): CalorieService = CalorieServiceImpl(
            calorieCalculator = calorieCalculator,
            metDataRepository = metDataRepository,
            workoutRepository = workoutRepository,
            userRepository = userRepository,
            dispatcher = ioDispatcher
        )
        
        /**
         * Provides PreferencesService implementation with user preference management.
         * 
         * The PreferencesService handles user preferences management including
         * widget layout, visibility settings, and user level configuration with
         * atomic updates and validation.
         * 
         * @param preferencesRepository Repository for widget preferences persistence
         * @param authRepository Repository for user authentication state
         * @param ioDispatcher IO dispatcher for background operations
         * @return PreferencesService implementation instance
         */
        @Provides
        @Singleton
        fun providePreferencesService(
            preferencesRepository: WidgetPreferencesRepository,
            authRepository: AuthRepository,
            @IoDispatcher ioDispatcher: CoroutineDispatcher
        ): PreferencesService = PreferencesServiceImpl(
            preferencesRepository = preferencesRepository,
            authRepository = authRepository,
            dispatcher = ioDispatcher
        )
        
        /**
         * Provides FeatureFlagService implementation with Firebase Remote Config.
         * 
         * The FeatureFlagService handles feature flag evaluation, A/B testing,
         * and remote configuration management with local caching and fallback
         * mechanisms for consistent user experience.
         * 
         * @param firebaseRemoteConfig Firebase Remote Config instance
         * @param ioDispatcher IO dispatcher for background operations
         * @return FeatureFlagService implementation instance
         */
        @Provides
        @Singleton
        fun provideFeatureFlagService(
            firebaseRemoteConfig: FirebaseRemoteConfig,
            @IoDispatcher ioDispatcher: CoroutineDispatcher
        ): FeatureFlagService = FeatureFlagServiceImpl(
            firebaseRemoteConfig = firebaseRemoteConfig,
            dispatcher = ioDispatcher
        )
    }
} 