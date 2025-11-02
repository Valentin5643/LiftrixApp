package com.example.liftrix.di

import android.content.Context
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.repository.SubscriptionRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.service.PreferencesService
import com.example.liftrix.service.FeatureFlagService
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.settings.SettingsCommandUseCase
import com.example.liftrix.domain.usecase.settings.EvaluateFeatureFlagUseCase
import com.example.liftrix.domain.usecase.settings.EnhancedSignOutUseCase
import com.example.liftrix.domain.usecase.settings.InitializeUserThemeUseCase
// Note: Analytics use cases (Calculate*, Generate*, Update*, etc.) have been consolidated into:
// - AnalyticsQueryUseCase (provided via @Inject)
// - AnalyticsExportUseCase (provided via @Inject)
// - DashboardCommandUseCase (provided via @Inject)
// - WidgetPreferencesUseCase (provided via @Inject)
// Note: FolderOperationsUseCase is provided via @Inject constructor
import com.example.liftrix.ui.common.state.StateCleanupManager
import com.example.liftrix.sync.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
     * Provides InitializeUserThemeUseCase with proper dependency injection.
     *
     * @param settingsRepository The settings repository dependency
     * @param context Application context for theme manager access
     * @return Configured InitializeUserThemeUseCase instance
     */
    @Provides
    @Singleton
    fun provideInitializeUserThemeUseCase(
        settingsRepository: SettingsRepository,
        @ApplicationContext context: Context
    ): InitializeUserThemeUseCase {
        return InitializeUserThemeUseCase(settingsRepository, context)
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
        @ApplicationContext context: Context,
        stateCleanupManager: StateCleanupManager
    ): EnhancedSignOutUseCase {
        return EnhancedSignOutUseCase(
            authRepository = authRepository,
            analyticsService = analyticsService,
            settingsRepository = settingsRepository,
            syncManager = syncManager,
            context = context,
            stateCleanupManager = stateCleanupManager
        )
    }

    // NOTE: The following use cases have been consolidated and are now provided via @Inject constructors:
    // - CalculateWorkoutMetricsUseCase -> AnalyticsQueryUseCase
    // - GenerateVolumeCalendarUseCase -> AnalyticsQueryUseCase
    // - UpdateProgressDashboardUseCase -> DashboardCommandUseCase
    // - CalorieAnalyticsUseCase -> deprecated (calorie feature removed)
    // - MigrateWidgetPreferencesUseCase -> WidgetMigrationUseCase
    // - DetectWorkoutAnomaliesUseCase -> AnalyticsQueryUseCase
    // - GetProgressDataUseCase -> AnalyticsQueryUseCase
    // - GetWidgetDataUseCase -> AnalyticsQueryUseCase
    // - CalculateCaloriesUseCase -> deprecated (calorie feature removed)

    /**
     * Provides SettingsCommandUseCase with proper dependency injection.
     *
     * This use case handles settings mutation operations including:
     * - Basic settings (dark mode, notifications)
     * - Weight unit preference
     * - Widget layout and preferences
     * - User experience level
     * - Widget visibility and order
     *
     * @param settingsRepository The settings repository dependency
     * @param preferencesService The preferences service dependency
     * @return Configured SettingsCommandUseCase instance
     */
    @Provides
    @Singleton
    fun provideSettingsCommandUseCase(
        settingsRepository: SettingsRepository,
        preferencesService: PreferencesService
    ): SettingsCommandUseCase {
        return SettingsCommandUseCase(settingsRepository, preferencesService)
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

    // NOTE: ReorderFoldersUseCase has been consolidated into FolderOperationsUseCase
    // which is provided automatically via @Inject constructor

}