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
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing DataStore dependencies.
 * 
 * This module provides the DataStore instance for user preferences
 * storage with proper scoping and lifecycle management.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    
    /**
     * Extension property to create DataStore instance.
     * This creates a singleton DataStore instance that's tied to the application lifecycle.
     */
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "liftrix_settings")
    
    /**
     * Provides a singleton DataStore instance for user preferences.
     * 
     * @param context The application context
     * @return DataStore instance for preferences
     */
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}