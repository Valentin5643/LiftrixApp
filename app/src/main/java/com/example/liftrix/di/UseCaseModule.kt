package com.example.liftrix.di

import androidx.work.WorkManager
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.repository.SubscriptionRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.service.ProgressDataService
import com.example.liftrix.service.CalorieService
import com.example.liftrix.service.PreferencesService
import com.example.liftrix.service.FeatureFlagService
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.analytics.CalculateWorkoutMetricsUseCase
import com.example.liftrix.domain.usecase.analytics.CalorieAnalyticsUseCase
import com.example.liftrix.domain.usecase.analytics.GenerateVolumeCalendarUseCase
import com.example.liftrix.domain.usecase.analytics.GetWidgetPreferencesUseCase
import com.example.liftrix.domain.usecase.analytics.MigrateWidgetPreferencesUseCase
import com.example.liftrix.domain.usecase.analytics.ResetWidgetPreferencesUseCase
import com.example.liftrix.domain.usecase.analytics.SaveWidgetPreferencesUseCase
import com.example.liftrix.domain.usecase.analytics.UpdateProgressDashboardUseCase
import com.example.liftrix.domain.usecase.analytics.UpdateWidgetVisibilityUseCase
import com.example.liftrix.domain.usecase.anomaly.DetectWorkoutAnomaliesUseCase
import com.example.liftrix.domain.usecase.progress.GetProgressDataUseCase
import com.example.liftrix.domain.usecase.analytics.GetWidgetDataUseCase
import com.example.liftrix.domain.usecase.analytics.CalculateCaloriesUseCase
import com.example.liftrix.domain.usecase.settings.UpdateUserPreferencesUseCase
import com.example.liftrix.domain.usecase.settings.EvaluateFeatureFlagUseCase
import com.example.liftrix.domain.usecase.settings.EnhancedSignOutUseCase
import com.example.liftrix.domain.usecase.settings.GetSubscriptionStatusUseCase
import com.example.liftrix.domain.usecase.settings.GetUserSettingsUseCase
import com.example.liftrix.domain.usecase.settings.UpdateSettingsUseCase
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.domain.repository.AnomalyDetectionRepository
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.domain.model.analytics.CalorieCalculator
import com.example.liftrix.service.AnalyticsEngine
import com.example.liftrix.sync.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing use case dependencies.
 * 
 * This module handles dependency injection for all use cases in the application,
 * following the single responsibility principle by separating use case injection
 * from repository and data layer injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    /**
     * Provides GetUserSettingsUseCase with proper dependency injection.
     * 
     * @param settingsRepository The settings repository dependency
     * @return Configured GetUserSettingsUseCase instance
     */
    @Provides
    @Singleton
    fun provideGetUserSettingsUseCase(
        settingsRepository: SettingsRepository
    ): GetUserSettingsUseCase {
        return GetUserSettingsUseCase(settingsRepository)
    }

    /**
     * Provides UpdateSettingsUseCase with proper dependency injection.
     * 
     * @param settingsRepository The settings repository dependency
     * @return Configured UpdateSettingsUseCase instance
     */
    @Provides
    @Singleton
    fun provideUpdateSettingsUseCase(
        settingsRepository: SettingsRepository
    ): UpdateSettingsUseCase {
        return UpdateSettingsUseCase(settingsRepository)
    }

    /**
     * Provides GetSubscriptionStatusUseCase with proper dependency injection.
     * 
     * @param subscriptionRepository The subscription repository dependency
     * @return Configured GetSubscriptionStatusUseCase instance
     */
    @Provides
    @Singleton
    fun provideGetSubscriptionStatusUseCase(
        subscriptionRepository: SubscriptionRepository
    ): GetSubscriptionStatusUseCase {
        return GetSubscriptionStatusUseCase(subscriptionRepository)
    }

    /**
     * Provides EnhancedSignOutUseCase with proper dependency injection.
     * 
     * This use case handles comprehensive logout functionality including
     * Firebase sign out, local data cleanup, analytics tracking, and
     * background service termination.
     * 
     * @param authRepository The authentication repository dependency
     * @param analyticsService The analytics service dependency
     * @param settingsRepository The settings repository dependency
     * @param syncManager The sync manager dependency
     * @param workManager The work manager dependency
     * @return Configured EnhancedSignOutUseCase instance
     */
    @Provides
    @Singleton
    fun provideEnhancedSignOutUseCase(
        authRepository: AuthRepository,
        analyticsService: AnalyticsService,
        settingsRepository: SettingsRepository,
        syncManager: SyncManager,
        workManager: WorkManager
    ): EnhancedSignOutUseCase {
        return EnhancedSignOutUseCase(
            authRepository = authRepository,
            analyticsService = analyticsService,
            settingsRepository = settingsRepository,
            syncManager = syncManager,
            workManager = workManager
        )
    }

    /**
     * Provides CalculateWorkoutMetricsUseCase with proper dependency injection.
     * 
     * @param analyticsEngine The analytics engine dependency
     * @param errorHandler The error handler dependency
     * @return Configured CalculateWorkoutMetricsUseCase instance
     */
    @Provides
    @Singleton
    fun provideCalculateWorkoutMetricsUseCase(
        analyticsEngine: AnalyticsEngine,
        errorHandler: ErrorHandler
    ): CalculateWorkoutMetricsUseCase {
        return CalculateWorkoutMetricsUseCase(analyticsEngine, errorHandler)
    }

    /**
     * Provides GenerateVolumeCalendarUseCase with proper dependency injection.
     * 
     * @param analyticsEngine The analytics engine dependency
     * @param errorHandler The error handler dependency
     * @return Configured GenerateVolumeCalendarUseCase instance
     */
    @Provides
    @Singleton
    fun provideGenerateVolumeCalendarUseCase(
        analyticsEngine: AnalyticsEngine,
        errorHandler: ErrorHandler
    ): GenerateVolumeCalendarUseCase {
        return GenerateVolumeCalendarUseCase(analyticsEngine, errorHandler)
    }

    /**
     * Provides UpdateProgressDashboardUseCase with proper dependency injection.
     * 
     * @param analyticsEngine The analytics engine dependency
     * @param errorHandler The error handler dependency
     * @return Configured UpdateProgressDashboardUseCase instance
     */
    @Provides
    @Singleton
    fun provideUpdateProgressDashboardUseCase(
        analyticsEngine: AnalyticsEngine,
        errorHandler: ErrorHandler
    ): UpdateProgressDashboardUseCase {
        return UpdateProgressDashboardUseCase(analyticsEngine, errorHandler)
    }


    /**
     * Provides CalorieAnalyticsUseCase with proper dependency injection.
     * 
     * @param workoutDao The workout DAO dependency
     * @param calorieCalculator The calorie calculator dependency
     * @return Configured CalorieAnalyticsUseCase instance
     */
    @Provides
    @Singleton
    fun provideCalorieAnalyticsUseCase(
        workoutDao: WorkoutDao,
        calorieCalculator: CalorieCalculator
    ): CalorieAnalyticsUseCase {
        return CalorieAnalyticsUseCase(workoutDao, calorieCalculator)
    }

    /**
     * Provides GetWidgetPreferencesUseCase with proper dependency injection.
     * 
     * @param widgetPreferencesRepository The widget preferences repository dependency
     * @return Configured GetWidgetPreferencesUseCase instance
     */
    @Provides
    @Singleton
    fun provideGetWidgetPreferencesUseCase(
        widgetPreferencesRepository: WidgetPreferencesRepository
    ): GetWidgetPreferencesUseCase {
        return GetWidgetPreferencesUseCase(widgetPreferencesRepository)
    }

    /**
     * Provides SaveWidgetPreferencesUseCase with proper dependency injection.
     * 
     * @param widgetPreferencesRepository The widget preferences repository dependency
     * @return Configured SaveWidgetPreferencesUseCase instance
     */
    @Provides
    @Singleton
    fun provideSaveWidgetPreferencesUseCase(
        widgetPreferencesRepository: WidgetPreferencesRepository
    ): SaveWidgetPreferencesUseCase {
        return SaveWidgetPreferencesUseCase(widgetPreferencesRepository)
    }

    /**
     * Provides UpdateWidgetVisibilityUseCase with proper dependency injection.
     * 
     * @param widgetPreferencesRepository The widget preferences repository dependency
     * @return Configured UpdateWidgetVisibilityUseCase instance
     */
    @Provides
    @Singleton
    fun provideUpdateWidgetVisibilityUseCase(
        widgetPreferencesRepository: WidgetPreferencesRepository
    ): UpdateWidgetVisibilityUseCase {
        return UpdateWidgetVisibilityUseCase(widgetPreferencesRepository)
    }

    /**
     * Provides ResetWidgetPreferencesUseCase with proper dependency injection.
     * 
     * @param widgetPreferencesRepository The widget preferences repository dependency
     * @return Configured ResetWidgetPreferencesUseCase instance
     */
    @Provides
    @Singleton
    fun provideResetWidgetPreferencesUseCase(
        widgetPreferencesRepository: WidgetPreferencesRepository
    ): ResetWidgetPreferencesUseCase {
        return ResetWidgetPreferencesUseCase(widgetPreferencesRepository)
    }

    /**
     * Provides MigrateWidgetPreferencesUseCase with proper dependency injection.
     * 
     * @param widgetPreferencesRepository The widget preferences repository dependency
     * @return Configured MigrateWidgetPreferencesUseCase instance
     */
    @Provides
    @Singleton
    fun provideMigrateWidgetPreferencesUseCase(
        widgetPreferencesRepository: WidgetPreferencesRepository
    ): MigrateWidgetPreferencesUseCase {
        return MigrateWidgetPreferencesUseCase(widgetPreferencesRepository)
    }

    /**
     * Provides DetectWorkoutAnomaliesUseCase with proper dependency injection.
     * 
     * @param anomalyDetectionRepository The anomaly detection repository dependency
     * @return Configured DetectWorkoutAnomaliesUseCase instance
     */
    @Provides
    @Singleton
    fun provideDetectWorkoutAnomaliesUseCase(
        anomalyDetectionRepository: AnomalyDetectionRepository
    ): DetectWorkoutAnomaliesUseCase {
        return DetectWorkoutAnomaliesUseCase(anomalyDetectionRepository)
    }

    /**
     * Provides GetProgressDataUseCase with proper dependency injection.
     * 
     * This use case handles retrieval of progress data including volume, duration,
     * and frequency metrics through the ProgressDataService abstraction layer.
     * 
     * @param progressDataService The progress data service dependency
     * @return Configured GetProgressDataUseCase instance
     */
    @Provides
    @Singleton
    fun provideGetProgressDataUseCase(
        progressDataService: ProgressDataService
    ): GetProgressDataUseCase {
        return GetProgressDataUseCase(progressDataService)
    }

    /**
     * Provides GetWidgetDataUseCase with proper dependency injection.
     * 
     * This use case handles retrieval of widget data and analytics through
     * the AnalyticsService abstraction layer with proper error handling.
     * 
     * @param analyticsService The analytics service dependency
     * @return Configured GetWidgetDataUseCase instance
     */
    @Provides
    @Singleton
    fun provideGetWidgetDataUseCase(
        analyticsService: AnalyticsService
    ): GetWidgetDataUseCase {
        return GetWidgetDataUseCase(analyticsService)
    }

    /**
     * Provides CalculateCaloriesUseCase with proper dependency injection.
     * 
     * This use case handles MET-based calorie calculations and temporal
     * aggregation through the CalorieService abstraction layer.
     * 
     * @param calorieService The calorie service dependency
     * @return Configured CalculateCaloriesUseCase instance
     */
    @Provides
    @Singleton
    fun provideCalculateCaloriesUseCase(
        calorieService: CalorieService
    ): CalculateCaloriesUseCase {
        return CalculateCaloriesUseCase(calorieService)
    }

    /**
     * Provides UpdateUserPreferencesUseCase with proper dependency injection.
     * 
     * This use case handles user preference updates including widget layout,
     * visibility settings, and user level configuration through the
     * PreferencesService abstraction layer.
     * 
     * @param preferencesService The preferences service dependency
     * @return Configured UpdateUserPreferencesUseCase instance
     */
    @Provides
    @Singleton
    fun provideUpdateUserPreferencesUseCase(
        preferencesService: PreferencesService
    ): UpdateUserPreferencesUseCase {
        return UpdateUserPreferencesUseCase(preferencesService)
    }

    /**
     * Provides EvaluateFeatureFlagUseCase with proper dependency injection.
     * 
     * This use case handles feature flag evaluation, A/B testing, and
     * remote configuration management through the FeatureFlagService
     * abstraction layer.
     * 
     * @param featureFlagService The feature flag service dependency
     * @return Configured EvaluateFeatureFlagUseCase instance
     */
    @Provides
    @Singleton
    fun provideEvaluateFeatureFlagUseCase(
        featureFlagService: FeatureFlagService
    ): EvaluateFeatureFlagUseCase {
        return EvaluateFeatureFlagUseCase(featureFlagService)
    }


}