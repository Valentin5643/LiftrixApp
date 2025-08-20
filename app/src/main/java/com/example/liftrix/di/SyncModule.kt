package com.example.liftrix.di

import com.example.liftrix.data.remote.FirebaseDataSource
import com.example.liftrix.data.remote.FirebaseDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * Hilt module for sync infrastructure components.
 * 
 * This module provides bindings for:
 * - Firebase data source implementation
 * - JSON serialization for sync operations
 * - Sync-related configurations and utilities
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    companion object {
        
        /**
         * Provides kotlinx.serialization.Json instance for sync operations.
         * 
         * This is configured for the sync infrastructure's specific needs:
         * - Lenient parsing for backward compatibility
         * - Ignores unknown keys for future-proofing
         * - Pretty printing disabled for performance
         */
        @Provides
        @Singleton
        fun provideJson(): Json {
            return Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
                prettyPrint = false
                coerceInputValues = true
            }
        }
    }

    /**
     * Binds FirebaseDataSource interface to its implementation.
     * 
     * This allows the OfflineQueueManager and other sync components to depend
     * on the interface while Hilt injects the concrete implementation.
     */
    @Binds
    @Singleton
    abstract fun bindFirebaseDataSource(
        firebaseDataSourceImpl: FirebaseDataSourceImpl
    ): FirebaseDataSource
}