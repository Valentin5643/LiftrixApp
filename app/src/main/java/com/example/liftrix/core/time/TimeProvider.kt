package com.example.liftrix.core.time

/**
 * Time provider interface for testable time operations.
 * Abstracts system time to enable deterministic testing of time-dependent logic.
 */
interface TimeProvider {
    /**
     * Returns current system time in milliseconds since epoch.
     */
    fun currentTimeMillis(): Long
}

/**
 * Production implementation of TimeProvider using system time.
 */
class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}