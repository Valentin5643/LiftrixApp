package com.example.liftrix.di

import android.content.Context
import com.example.liftrix.data.local.dao.FCMTokenDao
import com.example.liftrix.data.local.dao.NotificationMuteDao
import com.example.liftrix.data.local.dao.NotificationPreferenceDao
import com.example.liftrix.data.mapper.FCMTokenMapper
import com.example.liftrix.data.mapper.notifications.NotificationPreferencesMapper
import com.example.liftrix.data.repository.FCMTokenRepositoryImpl
import com.example.liftrix.data.repository.NotificationRepositoryImpl
import com.example.liftrix.data.repository.notifications.NotificationMuteRepositoryImpl
import com.example.liftrix.data.repository.notifications.NotificationPreferencesRepositoryImpl
import com.example.liftrix.data.service.NotificationHandlerImpl
import com.example.liftrix.data.service.NotificationRouterImpl
import com.example.liftrix.data.service.NotificationServiceImpl
import com.example.liftrix.domain.repository.FCMTokenRepository
import com.example.liftrix.domain.repository.NotificationRepository
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.repository.notifications.NotificationMuteRepository
import com.example.liftrix.domain.repository.notifications.NotificationPreferencesRepository
import com.example.liftrix.domain.service.NotificationHandler
import com.example.liftrix.domain.service.NotificationRouter
import com.example.liftrix.domain.service.NotificationService
import com.example.liftrix.services.NotificationChannelManager
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds
    @Singleton
    abstract fun bindNotificationRouter(impl: NotificationRouterImpl): NotificationRouter

    companion object {
        @Provides
        @Singleton
        fun provideNotificationManager(
            @ApplicationContext context: Context
        ): android.app.NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        @Provides
        @Singleton
        fun provideNotificationRepository(): NotificationRepository = NotificationRepositoryImpl()

        @Provides
        @Singleton
        fun provideNotificationService(
            firebaseMessaging: FirebaseMessaging,
            settingsRepository: SettingsRepository
        ): NotificationService = NotificationServiceImpl(firebaseMessaging, settingsRepository)

        @Provides
        @Singleton
        fun provideWorkoutRemoteNotificationSender(
            firebaseMessaging: FirebaseMessaging
        ): com.example.liftrix.domain.service.WorkoutRemoteNotificationSender =
            com.example.liftrix.service.FirebaseWorkoutRemoteNotificationSender(firebaseMessaging)

        @Provides
        @Singleton
        fun provideWorkoutNotificationTokenSource(
            fcmTokenRepository: FCMTokenRepository
        ): com.example.liftrix.domain.service.WorkoutNotificationTokenSource =
            com.example.liftrix.service.WorkoutNotificationTokenSourceAdapter(fcmTokenRepository)

        @Provides
        @Singleton
        fun provideWorkoutLocalNotificationPresenter(
            notificationHandler: NotificationHandler
        ): com.example.liftrix.domain.service.WorkoutLocalNotificationPresenter =
            com.example.liftrix.service.WorkoutLocalNotificationPresenterAdapter(notificationHandler)

        @Provides
        @Singleton
        fun provideNotificationPreferencesRepository(
            notificationPreferenceDao: NotificationPreferenceDao,
            notificationPreferencesMapper: NotificationPreferencesMapper
        ): NotificationPreferencesRepository =
            NotificationPreferencesRepositoryImpl(notificationPreferenceDao, notificationPreferencesMapper)

        @Provides
        @Singleton
        fun provideNotificationMuteRepository(
            notificationMuteDao: NotificationMuteDao
        ): NotificationMuteRepository = NotificationMuteRepositoryImpl(notificationMuteDao)

        @Provides
        @Singleton
        fun provideFCMTokenRepository(
            fcmTokenDao: FCMTokenDao,
            mapper: FCMTokenMapper
        ): FCMTokenRepository = FCMTokenRepositoryImpl(fcmTokenDao, mapper)

        @Provides
        @Singleton
        fun provideNotificationChannelManager(
            @ApplicationContext context: Context
        ): NotificationChannelManager = NotificationChannelManager(context)

        @Provides
        @Singleton
        fun provideNotificationHandler(
            @ApplicationContext context: Context,
            notificationChannelManager: NotificationChannelManager
        ): NotificationHandler = NotificationHandlerImpl(context, notificationChannelManager)
    }
}
