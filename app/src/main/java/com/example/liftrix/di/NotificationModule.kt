package com.example.liftrix.di

import com.example.liftrix.data.local.dao.NotificationPreferenceDao
import com.example.liftrix.data.local.dao.NotificationMuteDao
import com.example.liftrix.data.mapper.notifications.NotificationPreferencesMapper
import com.example.liftrix.data.repository.notifications.NotificationPreferencesRepositoryImpl
import com.example.liftrix.data.repository.notifications.NotificationMuteRepositoryImpl
import com.example.liftrix.domain.repository.notifications.NotificationPreferencesRepository
import com.example.liftrix.domain.repository.notifications.NotificationMuteRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for notification-related dependencies.
 * 
 * Provides:
 * - Notification preferences repository with Room DAO integration
 * - Notification mute repository for user privacy controls
 * - Use cases for notification preference management
 * 
 * All implementations follow user-scoped security patterns
 * and proper error handling with LiftrixResult.
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    @Provides
    @Singleton
    fun provideNotificationPreferencesRepository(
        notificationPreferenceDao: NotificationPreferenceDao,
        notificationPreferencesMapper: NotificationPreferencesMapper
    ): NotificationPreferencesRepository {
        return NotificationPreferencesRepositoryImpl(notificationPreferenceDao, notificationPreferencesMapper)
    }

    @Provides
    @Singleton
    fun provideNotificationMuteRepository(
        notificationMuteDao: NotificationMuteDao
    ): NotificationMuteRepository {
        return NotificationMuteRepositoryImpl(notificationMuteDao)
    }
}