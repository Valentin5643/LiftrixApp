package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.ActiveWorkoutSession
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutSessionId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Repository interface for managing active workout sessions.
 * Handles session lifecycle, persistence, and real-time updates.
 */
interface ActiveWorkoutSessionRepository {
    
    /**
     * Creates and saves a new active workout session
     */
    suspend fun createSession(session: ActiveWorkoutSession): Result<ActiveWorkoutSession>
    
    /**
     * Gets an active session by ID
     */
    suspend fun getSessionById(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession?>
    
    /**
     * Gets the current active session for a user
     */
    suspend fun getCurrentSessionForUser(userId: String): Result<ActiveWorkoutSession?>
    
    /**
     * Gets the current active session for a user as a Flow for real-time updates
     */
    fun getCurrentSessionForUserFlow(userId: String): Flow<ActiveWorkoutSession?>
    
    /**
     * Updates an active session
     */
    suspend fun updateSession(session: ActiveWorkoutSession): Result<ActiveWorkoutSession>
    
    /**
     * Saves session changes (background persistence)
     */
    suspend fun saveSessionChanges(session: ActiveWorkoutSession): Result<Unit>
    
    /**
     * Pauses an active session
     */
    suspend fun pauseSession(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession>
    
    /**
     * Resumes a paused session
     */
    suspend fun resumeSession(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession>
    
    /**
     * Starts rest period for a session
     */
    suspend fun startRest(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession>
    
    /**
     * Ends rest period for a session
     */
    suspend fun endRest(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession>
    
    /**
     * Moves to next exercise in session
     */
    suspend fun moveToNextExercise(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession>
    
    /**
     * Moves to previous exercise in session
     */
    suspend fun moveToPreviousExercise(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession>
    
    /**
     * Moves to specific exercise index in session
     */
    suspend fun moveToExercise(sessionId: WorkoutSessionId, exerciseIndex: Int): Result<ActiveWorkoutSession>
    
    /**
     * Adds an exercise to the active session
     */
    suspend fun addExerciseToSession(
        sessionId: WorkoutSessionId, 
        exercise: SessionExercise
    ): Result<ActiveWorkoutSession>
    
    /**
     * Removes an exercise from the active session
     */
    suspend fun removeExerciseFromSession(
        sessionId: WorkoutSessionId, 
        exerciseId: ExerciseId
    ): Result<ActiveWorkoutSession>
    
    /**
     * Updates an exercise in the active session
     */
    suspend fun updateExerciseInSession(
        sessionId: WorkoutSessionId,
        exerciseId: ExerciseId,
        updatedExercise: SessionExercise
    ): Result<ActiveWorkoutSession>
    
    /**
     * Completes a set within an exercise
     */
    suspend fun completeSet(
        sessionId: WorkoutSessionId,
        exerciseId: ExerciseId,
        setIndex: Int,
        actualReps: Int?,
        actualWeight: com.example.liftrix.domain.model.Weight?,
        actualRpe: Int?
    ): Result<ActiveWorkoutSession>
    
    /**
     * Marks a set as incomplete
     */
    suspend fun uncompleteSet(
        sessionId: WorkoutSessionId,
        exerciseId: ExerciseId,
        setIndex: Int
    ): Result<ActiveWorkoutSession>
    
    /**
     * Adds a set to an exercise in the session
     */
    suspend fun addSetToExercise(
        sessionId: WorkoutSessionId,
        exerciseId: ExerciseId
    ): Result<ActiveWorkoutSession>
    
    /**
     * Removes the last set from an exercise in the session
     */
    suspend fun removeLastSetFromExercise(
        sessionId: WorkoutSessionId,
        exerciseId: ExerciseId
    ): Result<ActiveWorkoutSession>
    
    /**
     * Updates session notes
     */
    suspend fun updateSessionNotes(
        sessionId: WorkoutSessionId,
        notes: String?
    ): Result<ActiveWorkoutSession>
    
    /**
     * Completes the session and converts it to a completed workout
     */
    suspend fun completeSession(sessionId: WorkoutSessionId): Result<Workout>
    
    /**
     * Cancels/abandons the session
     */
    suspend fun cancelSession(sessionId: WorkoutSessionId): Result<Unit>
    
    /**
     * Deletes a session (cleanup after completion/cancellation)
     */
    suspend fun deleteSession(sessionId: WorkoutSessionId): Result<Unit>
    
    /**
     * Gets all active sessions for a user (for recovery scenarios)
     */
    suspend fun getAllSessionsForUser(userId: String): Result<List<ActiveWorkoutSession>>
    
    /**
     * Gets sessions that need auto-save
     */
    suspend fun getSessionsNeedingAutoSave(cutoffTime: Instant): Result<List<ActiveWorkoutSession>>
    
    /**
     * Gets stale active sessions (for cleanup)
     */
    suspend fun getStaleActiveSessions(cutoffTime: Instant): Result<List<ActiveWorkoutSession>>
    
    /**
     * Updates auto-save timestamp
     */
    suspend fun updateAutoSaveTimestamp(sessionId: WorkoutSessionId): Result<Unit>
    
    /**
     * Updates recovery data for session
     */
    suspend fun updateRecoveryData(
        sessionId: WorkoutSessionId,
        recoveryData: String?
    ): Result<Unit>
    
    /**
     * Checks if user has any active sessions
     */
    suspend fun hasActiveSession(userId: String): Result<Boolean>
    
    /**
     * Counts active sessions for a user
     */
    suspend fun countActiveSessionsForUser(userId: String): Result<Int>
    
    /**
     * Gets sessions by template ID (for analytics)
     */
    suspend fun getSessionsByTemplate(templateId: String): Result<List<ActiveWorkoutSession>>
    
    /**
     * Gets sessions in specific state
     */
    suspend fun getSessionsByState(
        userId: String, 
        state: ActiveWorkoutSession.SessionState
    ): Result<List<ActiveWorkoutSession>>
    
    /**
     * Gets sessions within date range
     */
    suspend fun getSessionsInDateRange(
        userId: String,
        startDate: Instant,
        endDate: Instant
    ): Result<List<ActiveWorkoutSession>>
    
    /**
     * Bulk cleanup of old sessions
     */
    suspend fun cleanupOldSessions(cutoffTime: Instant): Result<Int>
    
    /**
     * Gets session statistics for analytics
     */
    suspend fun getSessionStatistics(userId: String): Result<SessionStatistics>
    
    /**
     * Syncs sessions with remote backend
     */
    suspend fun syncSessions(userId: String): Result<Unit>
    
    /**
     * Gets unsynced sessions
     */
    suspend fun getUnsyncedSessions(): Result<List<ActiveWorkoutSession>>
    
    /**
     * Updates sync status for a session
     */
    suspend fun updateSyncStatus(
        sessionId: WorkoutSessionId,
        isSynced: Boolean,
        syncVersion: Int
    ): Result<Unit>
}

/**
 * Data class for session statistics
 */
data class SessionStatistics(
    val totalSessions: Int,
    val pauseRate: Double, // 0.0 to 1.0
    val avgPauseDuration: Double, // seconds
    val lastSessionTime: Instant?,
    val avgSessionDuration: Double, // seconds
    val mostUsedTemplateId: String?,
    val avgExercisesPerSession: Double,
    val completionRate: Double // 0.0 to 1.0
)