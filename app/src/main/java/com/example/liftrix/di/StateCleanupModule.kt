package com.example.liftrix.di

import com.example.liftrix.ui.common.state.StateCleanupManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing StateCleanupManager dependency
 */
@Module
@InstallIn(SingletonComponent::class)
object StateCleanupModule {
    
    @Provides
    @Singleton
    fun provideStateCleanupManager(): StateCleanupManager {
        return StateCleanupManager()
    }
}