package com.example.liftrix.domain.service

/**
 * Boundary for clearing user-scoped presentation state during sign-out.
 */
interface SessionStateCleanup {
    fun cleanupAllState(): Int
    fun getRegisteredCount(): Int
}
