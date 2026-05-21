package com.example.liftrix.di

import com.example.liftrix.data.repository.AchievementRepositoryImpl
import com.example.liftrix.data.repository.AnomalyDetectionRepositoryImpl
import com.example.liftrix.data.repository.PersonalRecordRepositoryImpl
import com.example.liftrix.data.repository.ProgressStatsRepositoryImpl
import com.example.liftrix.data.repository.WidgetPreferencesRepositoryImpl
import com.example.liftrix.data.repository.export.ProgressReportRepositoryImpl
import com.example.liftrix.domain.repository.AchievementRepository
import com.example.liftrix.domain.repository.AnomalyDetectionRepository
import com.example.liftrix.domain.repository.PersonalRecordRepository
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.domain.repository.export.ProgressReportRepository
import com.example.liftrix.domain.sync.ProgressStatsSyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProgressRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindProgressStatsRepository(impl: ProgressStatsRepositoryImpl): ProgressStatsRepository

    @Binds
    @Singleton
    abstract fun bindProgressStatsSyncRepository(impl: ProgressStatsRepositoryImpl): ProgressStatsSyncRepository

    @Binds
    @Singleton
    abstract fun bindAnomalyDetectionRepository(impl: AnomalyDetectionRepositoryImpl): AnomalyDetectionRepository

    @Binds
    @Singleton
    abstract fun bindWidgetPreferencesRepository(impl: WidgetPreferencesRepositoryImpl): WidgetPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindAchievementRepository(impl: AchievementRepositoryImpl): AchievementRepository

    @Binds
    @Singleton
    abstract fun bindPersonalRecordRepository(impl: PersonalRecordRepositoryImpl): PersonalRecordRepository

    @Binds
    @Singleton
    abstract fun bindProgressReportRepository(impl: ProgressReportRepositoryImpl): ProgressReportRepository
}
