package com.example.liftrix.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.ActiveWorkoutSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Data Access Object for active workout sessions.
 * Handles persistence of ongoing workout sessions for background recovery.
 */
@Dao
interface ActiveWorkoutSessionDao {
    
    /**
     * Inserts a new active workout session
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ActiveWorkoutSessionEntity): Long

    /**
     * Updates an existing active workout session
     */
    @Update
    suspend fun updateSession(session: ActiveWorkoutSessionEntity)

    /**
     * Deletes an active workout session (when completed or cancelled)
     */
    @Delete
    suspend fun deleteSession(session: ActiveWorkoutSessionEntity)

    /**
     * Gets an active session by ID
     */
    @Query("SELECT * FROM active_workout_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): ActiveWorkoutSessionEntity?

    /**
     * Gets the current active session for a user (should only be one)
     */
    @Query("SELECT * FROM active_workout_sessions WHERE user_id = :userId ORDER BY started_at DESC LIMIT 1")
    suspend fun getCurrentSessionForUser(userId: String): ActiveWorkoutSessionEntity?

    /**
     * Gets the current active session for a user as Flow for real-time updates
     */
    @Query("SELECT * FROM active_workout_sessions WHERE user_id = :userId ORDER BY started_at DESC LIMIT 1")
    fun getCurrentSessionForUserFlow(userId: String): Flow<ActiveWorkoutSessionEntity?>

    /**
     * Gets all active sessions for a user (for recovery scenarios where multiple exist)
     */
    @Query("SELECT * FROM active_workout_sessions WHERE user_id = :userId ORDER BY started_at DESC")
    suspend fun getAllSessionsForUser(userId: String): List<ActiveWorkoutSessionEntity>

    /**
     * Gets sessions that need auto-save (haven't been saved recently)
     */
    @Query("""
        SELECT * FROM active_workout_sessions 
        WHERE auto_save_enabled = 1 
        AND (last_auto_save IS NULL OR last_auto_save < :cutoffTime)
        ORDER BY last_modified ASC
    """)
    suspend fun getSessionsNeedingAutoSave(cutoffTime: Instant): List<ActiveWorkoutSessionEntity>

    /**
     * Gets sessions that might need recovery (old sessions still active)
     */
    @Query("""
        SELECT * FROM active_workout_sessions 
        WHERE started_at < :cutoffTime 
        AND session_state != 'COMPLETED'
        ORDER BY started_at ASC
    """)
    suspend fun getStaleActiveSessions(cutoffTime: Instant): List<ActiveWorkoutSessionEntity>

    /**
     * Updates session state
     */
    @Query("UPDATE active_workout_sessions SET session_state = :newState, last_modified = :lastModified WHERE id = :sessionId")
    suspend fun updateSessionState(sessionId: String, newState: String, lastModified: Instant)

    /**
     * Updates current exercise index
     */
    @Query("UPDATE active_workout_sessions SET current_exercise_index = :newIndex, last_modified = :lastModified WHERE id = :sessionId")
    suspend fun updateCurrentExerciseIndex(sessionId: String, newIndex: Int, lastModified: Instant)

    /**
     * Updates exercises JSON (when exercises are modified during workout)
     */
    @Query("UPDATE active_workout_sessions SET exercises_json = :exercisesJson, last_modified = :lastModified WHERE id = :sessionId")
    suspend fun updateExercisesJson(sessionId: String, exercisesJson: String, lastModified: Instant)

    /**
     * Updates pause/resume state
     */
    @Query("""
        UPDATE active_workout_sessions 
        SET paused_at = :pausedAt, 
            resumed_at = :resumedAt, 
            total_paused_duration = :totalPausedDuration,
            last_modified = :lastModified
        WHERE id = :sessionId
    """)
    suspend fun updatePauseResumeState(
        sessionId: String, 
        pausedAt: Instant?, 
        resumedAt: Instant?, 
        totalPausedDuration: Long,
        lastModified: Instant
    )

    /**
     * Updates rest timer state
     */
    @Query("""
        UPDATE active_workout_sessions 
        SET rest_timer_start_time = :startTime,
            rest_timer_duration_seconds = :durationSeconds,
            rest_timer_paused_at = :pausedAt,
            last_modified = :lastModified
        WHERE id = :sessionId
    """)
    suspend fun updateRestTimerState(
        sessionId: String,
        startTime: Instant?,
        durationSeconds: Int?,
        pausedAt: Instant?,
        lastModified: Instant
    )

    /**
     * Updates session notes
     */
    @Query("UPDATE active_workout_sessions SET notes = :notes, last_modified = :lastModified WHERE id = :sessionId")
    suspend fun updateSessionNotes(sessionId: String, notes: String?, lastModified: Instant)

    /**
     * Updates auto-save timestamp
     */
    @Query("UPDATE active_workout_sessions SET last_auto_save = :timestamp WHERE id = :sessionId")
    suspend fun updateAutoSaveTimestamp(sessionId: String, timestamp: Instant)

    /**
     * Updates recovery data
     */
    @Query("UPDATE active_workout_sessions SET recovery_data_json = :recoveryData, last_modified = :lastModified WHERE id = :sessionId")
    suspend fun updateRecoveryData(sessionId: String, recoveryData: String?, lastModified: Instant)

    /**
     * Counts active sessions for a user
     */
    @Query("SELECT COUNT(*) FROM active_workout_sessions WHERE user_id = :userId")
    suspend fun countActiveSessionsForUser(userId: String): Int

    /**
     * Checks if user has any active sessions
     */
    @Query("SELECT EXISTS(SELECT 1 FROM active_workout_sessions WHERE user_id = :userId LIMIT 1)")
    suspend fun hasActiveSession(userId: String): Boolean

    /**
     * Gets sessions by template ID (for analytics)
     */
    @Query("SELECT * FROM active_workout_sessions WHERE template_id = :templateId ORDER BY started_at DESC")
    suspend fun getSessionsByTemplate(templateId: String): List<ActiveWorkoutSessionEntity>

    /**
     * Gets sessions in specific state
     */
    @Query("SELECT * FROM active_workout_sessions WHERE user_id = :userId AND session_state = :state ORDER BY started_at DESC")
    suspend fun getSessionsByState(userId: String, state: String): List<ActiveWorkoutSessionEntity>

    /**
     * Gets sessions within date range
     */
    @Query("""
        SELECT * FROM active_workout_sessions 
        WHERE user_id = :userId 
        AND started_at BETWEEN :startDate AND :endDate 
        ORDER BY started_at DESC
    """)
    suspend fun getSessionsInDateRange(userId: String, startDate: Instant, endDate: Instant): List<ActiveWorkoutSessionEntity>

    /**
     * Deletes sessions older than specified time (cleanup)
     */
    @Query("DELETE FROM active_workout_sessions WHERE started_at < :cutoffTime")
    suspend fun deleteSessionsOlderThan(cutoffTime: Instant): Int

    /**
     * Deletes all sessions for a user (for account cleanup)
     */
    @Query("DELETE FROM active_workout_sessions WHERE user_id = :userId")
    suspend fun deleteAllSessionsForUser(userId: String): Int

    /**
     * Gets session statistics for analytics
     */
    @Query("""
        SELECT 
            COUNT(*) as total_sessions,
            AVG(CASE WHEN paused_at IS NOT NULL THEN 1.0 ELSE 0.0 END) as pause_rate,
            AVG(total_paused_duration) as avg_pause_duration,
            MAX(started_at) as last_session_time
        FROM active_workout_sessions 
        WHERE user_id = :userId
    """)
    suspend fun getSessionStatistics(userId: String): SessionStatistics?

    /**
     * Updates sync status
     */
    @Query("UPDATE active_workout_sessions SET is_synced = :isSynced, sync_version = :syncVersion WHERE id = :sessionId")
    suspend fun updateSyncStatus(sessionId: String, isSynced: Boolean, syncVersion: Int)

    /**
     * Gets unsynced sessions
     */
    @Query("SELECT * FROM active_workout_sessions WHERE is_synced = 0 ORDER BY last_modified ASC")
    suspend fun getUnsyncedSessions(): List<ActiveWorkoutSessionEntity>

    /**
     * Bulk insert sessions (for sync/import)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ActiveWorkoutSessionEntity>)

    /**
     * Gets sessions that have been modified since last sync
     */
    @Query("""
        SELECT * FROM active_workout_sessions 
        WHERE user_id = :userId 
        AND (is_synced = 0 OR last_modified > :lastSyncTime)
        ORDER BY last_modified ASC
    """)
    suspend fun getSessionsModifiedSince(userId: String, lastSyncTime: Instant): List<ActiveWorkoutSessionEntity>
}

/**
 * Data class for session statistics query result
 */
data class SessionStatistics(
    @ColumnInfo(name = "total_sessions") val totalSessions: Int,
    @ColumnInfo(name = "pause_rate") val pauseRate: Double, // 0.0 to 1.0
    @ColumnInfo(name = "avg_pause_duration") val avgPauseDuration: Double, // seconds
    @ColumnInfo(name = "last_session_time") val lastSessionTime: Instant?
)