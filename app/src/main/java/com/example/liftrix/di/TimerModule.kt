package com.example.liftrix.di

import android.app.NotificationManager
import android.content.Context

object TimerModule {
    fun provideNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
}
