package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.GuestSessionEntity
import java.time.Instant

/**
 * DAO for guest session data operations
 */
@Dao
interface GuestSessionDao {

    /**
     * Gets the guest session for a specific user
     */
    @Query("SELECT * FROM guest_sessions WHERE user_id = :userId LIMIT 1")
    suspend fun getGuestSession(userId: String): GuestSessionEntity?

    /**
     * Inserts or updates a guest session
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(guestSession: GuestSessionEntity)

    /**
     * Updates an existing guest session
     */
    @Update
    suspend fun update(guestSession: GuestSessionEntity)

    /**
     * Deletes a guest session for a specific user
     */
    @Query("DELETE FROM guest_sessions WHERE user_id = :userId")
    suspend fun deleteByUserId(userId: String)

    /**
     * Gets all active guest sessions (not at limit)
     */
    @Query("SELECT * FROM guest_sessions WHERE is_limit_reached = 0")
    suspend fun getActiveGuestSessions(): List<GuestSessionEntity>

    /**
     * Gets all guest sessions that have reached their limit
     */
    @Query("SELECT * FROM guest_sessions WHERE is_limit_reached = 1")
    suspend fun getLimitReachedSessions(): List<GuestSessionEntity>

    /**
     * Deletes sessions that haven't been active for the specified number of days
     */
    @Query("DELETE FROM guest_sessions WHERE last_activity_at < :cutoffTime")
    suspend fun deleteInactiveSessions(cutoffTime: Instant): Int

    /**
     * Gets count of guest sessions created in the last 24 hours
     */
    @Query("SELECT COUNT(*) FROM guest_sessions WHERE session_started_at > :cutoffTime")
    suspend fun getSessionsCreatedSince(cutoffTime: Instant): Int

    /**
     * Gets total count of guest sessions
     */
    @Query("SELECT COUNT(*) FROM guest_sessions")
    suspend fun getTotalSessionCount(): Int

    /**
     * Gets average workout count across all guest sessions
     */
    @Query("SELECT AVG(workout_count) FROM guest_sessions")
    suspend fun getAverageWorkoutCount(): Double

    /**
     * Gets count of sessions that completed at least one workout
     */
    @Query("SELECT COUNT(*) FROM guest_sessions WHERE workout_count > 0")
    suspend fun getSessionsWithWorkouts(): Int
}