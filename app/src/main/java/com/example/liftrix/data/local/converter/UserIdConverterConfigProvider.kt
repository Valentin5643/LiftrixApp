package com.example.liftrix.data.local.converter

import com.example.liftrix.BuildConfig

/**
 * Provider for UserIdConverter configuration.
 *
 * Uses lazy initialization to ensure configuration is available at runtime,
 * supporting both production and test modes.
 *
 * - Production (BuildConfig.DEBUG = false): Graceful degradation
 * - Test: Fail-fast behavior is set via TestConverterModule
 */
object UserIdConverterConfigProvider {
    private var _config: UserIdConverterConfig? = null

    /**
     * Set the configuration (called by DI system at app initialization).
     */
    fun setConfig(config: UserIdConverterConfig) {
        _config = config
    }

    /**
     * Get current configuration or return default.
     */
    fun getConfig(): UserIdConverterConfig {
        return _config ?: UserIdConverterConfig(
            strictMode = false  // Default: production mode (graceful degradation)
        )
    }
}
