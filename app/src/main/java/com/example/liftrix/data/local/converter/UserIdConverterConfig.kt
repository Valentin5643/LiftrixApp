package com.example.liftrix.data.local.converter

/**
 * Configuration for UserIdConverter behavior.
 * Injected via DI to avoid global mutable state.
 *
 * @property strictMode
 * - TRUE (test environment): Fail-fast, throw exceptions for invalid data
 * - FALSE (production): Graceful degradation, return null, log to Crashlytics
 */
data class UserIdConverterConfig(
    val strictMode: Boolean
)
