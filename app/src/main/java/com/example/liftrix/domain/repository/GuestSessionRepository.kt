package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.GuestSession
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Repository interface for managing guest user sessions
 */
interface GuestSessionRepository {

    /**
     * Gets the current guest session for a user
     */
    suspend fun getGuestSession(userId: String): LiftrixResult<GuestSession?>

    /**
     * Saves or updates a guest session
     */
    suspend fun saveGuestSession(guestSession: GuestSession): LiftrixResult<Unit>

    /**
     * Clears the guest session for a user (when they sign up)
     */
    suspend fun clearGuestSession(userId: String): LiftrixResult<Unit>

    /**
     * Gets all active guest sessions (for analytics/monitoring)
     */
    suspend fun getActiveGuestSessions(): LiftrixResult<List<GuestSession>>

    /**
     * Cleans up expired guest sessions
     */
    suspend fun cleanupExpiredSessions(): LiftrixResult<Int>
}