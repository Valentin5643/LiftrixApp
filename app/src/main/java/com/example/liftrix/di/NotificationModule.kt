package com.example.liftrix.di

import com.example.liftrix.data.local.dao.FCMTokenDao
import com.example.liftrix.data.local.dao.NotificationPreferenceDao
import com.example.liftrix.data.local.dao.NotificationMuteDao
import com.example.liftrix.data.mapper.FCMTokenMapper
import com.example.liftrix.data.mapper.notifications.NotificationPreferencesMapper
import com.example.liftrix.data.repository.FCMTokenRepositoryImpl
import com.example.liftrix.data.repository.notifications.NotificationPreferencesRepositoryImpl
import com.example.liftrix.data.repository.notifications.NotificationMuteRepositoryImpl
import com.example.liftrix.data.service.NotificationHandlerImpl
import com.example.liftrix.domain.repository.FCMTokenRepository
import com.example.liftrix.domain.repository.notifications.NotificationPreferencesRepository
import com.example.liftrix.domain.repository.notifications.NotificationMuteRepository
import com.example.liftrix.domain.service.NotificationHandler
import com.example.liftrix.services.NotificationChannelManager
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
 * - FCM token repository for push notification management
 * - Notification handler for system and in-app notifications
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

    @Provides
    @Singleton
    fun provideFCMTokenRepository(
        fcmTokenDao: FCMTokenDao,
        mapper: FCMTokenMapper
    ): FCMTokenRepository {
        return FCMTokenRepositoryImpl(fcmTokenDao, mapper)
    }

    @Provides
    @Singleton
    fun provideNotificationChannelManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): NotificationChannelManager {
        return NotificationChannelManager(context)
    }

    @Provides
    @Singleton
    fun provideNotificationHandler(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        notificationChannelManager: NotificationChannelManager
    ): NotificationHandler {
        return NotificationHandlerImpl(context, notificationChannelManager)
    }
}