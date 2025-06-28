package com.example.liftrix.di

import android.app.NotificationManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for timer-related dependencies.
 * Provides system services and bindings needed for timer functionality.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimerModule {

    /**
     * Provides the NotificationManager system service.
     * Required for timer notifications and foreground service notifications.
     */
    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
} 