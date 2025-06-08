package com.example.liftrix.di

import com.example.liftrix.data.service.AnalyticsServiceImpl
import com.example.liftrix.domain.service.AnalyticsService
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(): FirebaseAnalytics {
        return Firebase.analytics.apply {
            // Enable analytics collection
            setAnalyticsCollectionEnabled(true)
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics {
        return FirebaseCrashlytics.getInstance().apply {
            // Enable crashlytics collection
            setCrashlyticsCollectionEnabled(true)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsBindingModule {

    @Binds
    @Singleton
    abstract fun bindAnalyticsService(
        analyticsServiceImpl: AnalyticsServiceImpl
    ): AnalyticsService
} 