package com.example.liftrix.di

import com.example.liftrix.data.local.converter.UserIdConverter
import com.example.liftrix.data.local.converter.UserIdConverterConfig
import com.example.liftrix.data.local.converter.UserIdConverterConfigProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ConverterModule - Room TypeConverter Dependency Injection
 *
 * PURPOSE: Provides singleton TypeConverters with DI-injected configuration
 *
 * RESPONSIBILITIES:
 * - Provide UserIdConverterConfig with environment-specific settings
 * - Provide UserIdConverter with resilience strategies
 * - Support fail-fast in tests, graceful degradation in production
 *
 * BINDINGS:
 * - UserIdConverterConfig: Environment-specific configuration
 * - UserIdConverter: Room TypeConverter with resilience
 */
@Module
@InstallIn(SingletonComponent::class)
object ConverterModule {

    @Provides
    @Singleton
    fun provideUserIdConverterConfig(): UserIdConverterConfig {
        val config = UserIdConverterConfig(
            strictMode = false  // Production: graceful degradation
        )
        // Initialize the converter's lazy config provider
        UserIdConverterConfigProvider.setConfig(config)
        return config
    }
}
