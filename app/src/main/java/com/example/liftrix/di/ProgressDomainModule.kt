package com.example.liftrix.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProgressDomainModule {
    @Binds
    @Singleton
    abstract fun bindPRDetectionService(
        impl: com.example.liftrix.service.PRDetectionServiceImpl
    ): com.example.liftrix.domain.service.PRDetectionService

    @Binds
    @Singleton
    abstract fun bindWorkoutCompletionNotifier(
        impl: com.example.liftrix.service.GymBuddyWorkoutCompletionNotifier
    ): com.example.liftrix.service.WorkoutCompletionNotifier

    @Binds
    @Singleton
    abstract fun bindHomeWidgetUpdateNotifier(
        impl: com.example.liftrix.service.HomeWidgetUpdateNotifierImpl
    ): com.example.liftrix.service.HomeWidgetUpdateNotifier

    @Binds
    @Singleton
    abstract fun bindAnalyticsCalculationService(
        impl: com.example.liftrix.domain.service.AnalyticsCalculationServiceImpl
    ): com.example.liftrix.domain.service.AnalyticsCalculationService

    @Binds
    @Singleton
    abstract fun bindWidgetOperationsService(
        impl: com.example.liftrix.domain.service.WidgetOperationsServiceImpl
    ): com.example.liftrix.domain.service.WidgetOperationsService

    companion object {
        @Provides
        @Singleton
        fun provideProgressDataService(
            progressStatsRepository: com.example.liftrix.domain.repository.ProgressStatsRepository,
            cacheManager: com.example.liftrix.core.cache.EnhancedCacheManager,
            generateVolumeCalendarUseCase: com.example.liftrix.domain.usecase.analytics.GenerateVolumeCalendarUseCase,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.ProgressDataService =
            com.example.liftrix.service.ProgressDataServiceImpl(
                progressStatsRepository = progressStatsRepository,
                cacheManager = cacheManager,
                generateVolumeCalendarUseCase = generateVolumeCalendarUseCase,
                ioDispatcher = ioDispatcher
            )

        @Provides
        @Singleton
        fun provideAnalyticsService(
            widgetManager: com.example.liftrix.domain.service.analytics.AnalyticsWidgetManager,
            preferencesRepository: com.example.liftrix.domain.repository.WidgetPreferencesRepository,
            analyticsEngine: com.example.liftrix.service.AnalyticsEngine,
            cacheManager: com.example.liftrix.core.cache.CacheManager,
            widgetCacheManager: com.example.liftrix.service.cache.WidgetCacheManager,
            syncScheduler: com.example.liftrix.domain.sync.SyncScheduler,
            getWidgetDataUseCase: com.example.liftrix.domain.usecase.analytics.GetWidgetDataUseCase,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.AnalyticsService =
            com.example.liftrix.service.AnalyticsServiceImpl(
                widgetManager = widgetManager,
                preferencesRepository = preferencesRepository,
                analyticsEngine = analyticsEngine,
                cacheManager = cacheManager,
                widgetCacheManager = widgetCacheManager,
                syncScheduler = syncScheduler,
                getWidgetDataUseCase = getWidgetDataUseCase,
                ioDispatcher = ioDispatcher
            )

        @Provides
        @Singleton
        fun provideCalorieService(
            calorieCalculator: com.example.liftrix.domain.model.analytics.CalorieCalculator,
            metDataRepository: com.example.liftrix.domain.repository.MetDataRepository,
            workoutRepository: com.example.liftrix.domain.repository.workout.WorkoutRepository,
            userRepository: com.example.liftrix.domain.repository.UserRepository,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.CalorieService =
            com.example.liftrix.service.CalorieServiceImpl(
                calorieCalculator = calorieCalculator,
                metDataRepository = metDataRepository,
                workoutRepository = workoutRepository,
                userRepository = userRepository,
                dispatcher = ioDispatcher
            )

        @Provides
        @Singleton
        fun providePreferencesService(
            preferencesRepository: com.example.liftrix.domain.repository.WidgetPreferencesRepository,
            authRepository: com.example.liftrix.domain.repository.AuthRepository,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.PreferencesService =
            com.example.liftrix.service.PreferencesServiceImpl(preferencesRepository, authRepository, ioDispatcher)

        @Provides
        @Singleton
        fun provideDiskCache(
            @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
            gson: com.google.gson.Gson,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.cache.DiskCache =
            com.example.liftrix.service.cache.DiskCache(context, gson, ioDispatcher)

        @Provides
        @Singleton
        fun provideCacheStrategy(): com.example.liftrix.service.cache.CacheStrategy =
            com.example.liftrix.service.cache.CacheStrategyImpl()

        @Provides
        @Singleton
        fun provideWidgetCacheManager(
            memoryCache: com.example.liftrix.core.cache.CacheManager,
            diskCache: com.example.liftrix.service.cache.DiskCache,
            cacheStrategy: com.example.liftrix.service.cache.CacheStrategy,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.cache.WidgetCacheManager =
            com.example.liftrix.service.cache.WidgetCacheManager(memoryCache, diskCache, cacheStrategy, ioDispatcher)

        @Provides
        @Singleton
        fun provideRefreshWidgetDataUseCase(
            analyticsService: com.example.liftrix.service.AnalyticsService
        ): com.example.liftrix.domain.usecase.analytics.RefreshWidgetDataUseCase =
            com.example.liftrix.domain.usecase.analytics.RefreshWidgetDataUseCase(analyticsService)

        @Provides
        @Singleton
        fun provideGetDashboardConfigurationUseCase(
            widgetPreferencesRepository: com.example.liftrix.domain.repository.WidgetPreferencesRepository,
            analyticsService: com.example.liftrix.service.AnalyticsService
        ): com.example.liftrix.domain.usecase.analytics.GetDashboardConfigurationUseCase =
            com.example.liftrix.domain.usecase.analytics.GetDashboardConfigurationUseCase(
                widgetPreferencesRepository,
                analyticsService
            )
    }
}
