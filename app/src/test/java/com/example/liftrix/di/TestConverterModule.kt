package com.example.liftrix.di

import com.example.liftrix.data.local.converter.UserIdConverter
import com.example.liftrix.data.local.converter.UserIdConverterConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * TestConverterModule - Test-specific TypeConverter Configuration
 *
 * PURPOSE: Overrides production ConverterModule for test environment
 *
 * STRATEGIES:
 * - strictMode = true: Fail-fast on invalid data (tests catch corruption immediately)
 * - Prevents silent data quality issues from going undetected
 *
 * USAGE: Automatically applied by @HiltAndroidTest on test classes
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [ConverterModule::class]
)
object TestConverterModule {

    @Provides
    @Singleton
    fun provideUserIdConverterConfig(): UserIdConverterConfig {
        val config = UserIdConverterConfig(
            strictMode = true  // Tests: fail-fast
        )
        // Initialize the converter's lazy config provider for tests
        UserIdConverterConfigProvider.setConfig(config)
        return config
    }
}
