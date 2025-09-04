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

/**
 * Dagger Hilt module for providing DataStore dependencies.
 * 
 * This module provides DataStore instances for user preferences and widget preferences
 * storage with proper scoping and lifecycle management. Each DataStore is isolated
 * to prevent conflicts and ensure optimal performance.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    
    /**
     * Extension property to create main DataStore instance for user settings.
     * This creates a singleton DataStore instance that's tied to the application lifecycle.
     */
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "liftrix_settings")
    
    /**
     * Extension property to create widget preferences DataStore instance.
     * This creates a separate DataStore instance specifically for widget configurations
     * to improve performance and maintainability.
     */
    private val Context.widgetPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "liftrix_widget_preferences")
    
    /**
     * Extension property to create onboarding temporary data DataStore instance.
     * This creates a separate DataStore instance specifically for temporary onboarding data
     * that survives authentication transitions (guest → authenticated user).
     */
    private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "liftrix_onboarding_temp")
    
    /**
     * Provides a singleton DataStore instance for user preferences.
     * 
     * @param context The application context
     * @return DataStore instance for general user preferences
     */
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
    
    /**
     * Provides a singleton DataStore instance for widget preferences.
     * 
     * @param context The application context
     * @return DataStore instance for widget preferences
     */
    @Provides
    @Singleton
    @Named("widgetPreferences")
    fun provideWidgetPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.widgetPreferencesDataStore
    }
    
    /**
     * Provides a singleton DataStore instance for temporary onboarding data.
     * 
     * @param context The application context
     * @return DataStore instance for onboarding temporary storage
     */
    @Provides
    @Singleton
    @Named("onboardingDataStore")
    fun provideOnboardingDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.onboardingDataStore
    }
}