package com.example.liftrix.di

import com.example.liftrix.domain.progress.ProgressAnalyticsServicePort
import com.example.liftrix.domain.progress.ProgressAnomalyPort
import com.example.liftrix.domain.progress.ProgressAuthPort
import com.example.liftrix.domain.progress.ProgressCaloriePort
import com.example.liftrix.domain.progress.ProgressDashboardGateway
import com.example.liftrix.domain.progress.ProgressDataPort
import com.example.liftrix.domain.progress.ProgressDetailAnalyticsGateway
import com.example.liftrix.domain.progress.ProgressExerciseCatalogPort
import com.example.liftrix.domain.progress.ProgressFeatureFlagPort
import com.example.liftrix.domain.progress.ProgressPreferencesPort
import com.example.liftrix.domain.progress.ProgressSessionSummaryPort
import com.example.liftrix.domain.progress.ProgressSettingsPort
import com.example.liftrix.domain.progress.ProgressUnitConversionPort
import com.example.liftrix.domain.progress.ProgressWidgetResolverPort
import com.example.liftrix.di.feature.progress.AppProgressAnalyticsServicePort
import com.example.liftrix.di.feature.progress.AppProgressAnomalyPort
import com.example.liftrix.di.feature.progress.AppProgressAuthPort
import com.example.liftrix.di.feature.progress.AppProgressCaloriePort
import com.example.liftrix.di.feature.progress.AppProgressDashboardGateway
import com.example.liftrix.di.feature.progress.AppProgressDataPort
import com.example.liftrix.di.feature.progress.AppProgressDetailAnalyticsGateway
import com.example.liftrix.di.feature.progress.AppProgressExerciseCatalogPort
import com.example.liftrix.di.feature.progress.AppProgressFeatureFlagPort
import com.example.liftrix.di.feature.progress.AppProgressPreferencesPort
import com.example.liftrix.di.feature.progress.AppProgressSessionSummaryPort
import com.example.liftrix.di.feature.progress.AppProgressSettingsPort
import com.example.liftrix.di.feature.progress.AppProgressUnitConversionPort
import com.example.liftrix.di.feature.progress.AppProgressWidgetResolverPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProgressFeatureModule {
    @Binds
    @Singleton
    abstract fun bindProgressCaloriePort(impl: AppProgressCaloriePort): ProgressCaloriePort

    @Binds
    @Singleton
    abstract fun bindProgressDataPort(impl: AppProgressDataPort): ProgressDataPort

    @Binds
    @Singleton
    abstract fun bindProgressAnalyticsServicePort(impl: AppProgressAnalyticsServicePort): ProgressAnalyticsServicePort

    @Binds
    @Singleton
    abstract fun bindProgressPreferencesPort(impl: AppProgressPreferencesPort): ProgressPreferencesPort

    @Binds
    @Singleton
    abstract fun bindProgressFeatureFlagPort(impl: AppProgressFeatureFlagPort): ProgressFeatureFlagPort

    @Binds
    @Singleton
    abstract fun bindProgressWidgetResolverPort(impl: AppProgressWidgetResolverPort): ProgressWidgetResolverPort

    @Binds
    @Singleton
    abstract fun bindProgressSessionSummaryPort(impl: AppProgressSessionSummaryPort): ProgressSessionSummaryPort

    @Binds
    @Singleton
    abstract fun bindProgressDashboardGateway(impl: AppProgressDashboardGateway): ProgressDashboardGateway

    @Binds
    @Singleton
    abstract fun bindProgressDetailAnalyticsGateway(impl: AppProgressDetailAnalyticsGateway): ProgressDetailAnalyticsGateway

    @Binds
    @Singleton
    abstract fun bindProgressAnomalyPort(impl: AppProgressAnomalyPort): ProgressAnomalyPort

    @Binds
    @Singleton
    abstract fun bindProgressAuthPort(impl: AppProgressAuthPort): ProgressAuthPort

    @Binds
    @Singleton
    abstract fun bindProgressExerciseCatalogPort(impl: AppProgressExerciseCatalogPort): ProgressExerciseCatalogPort

    @Binds
    @Singleton
    abstract fun bindProgressSettingsPort(impl: AppProgressSettingsPort): ProgressSettingsPort

    @Binds
    @Singleton
    abstract fun bindProgressUnitConversionPort(impl: AppProgressUnitConversionPort): ProgressUnitConversionPort
}
