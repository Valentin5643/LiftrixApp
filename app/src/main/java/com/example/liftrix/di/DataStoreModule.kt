package com.example.liftrix.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "liftrix_settings")
private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "liftrix_widget_preferences")
private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "liftrix_onboarding_temp")
private val Context.demoModeDataStore: DataStore<Preferences> by preferencesDataStore(name = "liftrix_demo_mode")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    @Named("widgetPreferences")
    fun provideWidgetPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.widgetDataStore

    @Provides
    @Singleton
    @Named("onboardingDataStore")
    fun provideOnboardingDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.onboardingDataStore

    @Provides
    @Singleton
    @Named("demoModeDataStore")
    fun provideDemoModeDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.demoModeDataStore
}
