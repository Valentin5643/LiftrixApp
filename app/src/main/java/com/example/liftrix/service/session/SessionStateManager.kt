package com.example.liftrix.service.session

import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.SessionSet
import com.example.liftrix.domain.model.ExerciseId
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing active workout session state.
 * 
 * Responsible for:
 * - Session lifecycle management (start, pause, resume, complete, discard)
 * - Exercise and set manipulation within sessions
 * - Session duration tracking
 * - Current session state queries
 * 
 * Follows Interface Segregation Principle by focusing only on state management.
 */
interface SessionStateManager {
    
    /**
     * StateFlow of the current session, null if no active session.
     */
    val currentSession: StateFlow<UnifiedWorkoutSession?>
    
    /**
     * StateFlow indicating if a session is currently active.
     */
    val hasActiveSession: StateFlow<Boolean>
    
    /**
     * Starts a new workout session.
     * 
     * @param session The session to start
     * @throws IllegalStateException if there's already an active session
     */
    fun startSession(session: UnifiedWorkoutSession)
    
    /**
     * Forces start of a session, discarding any existing session.
     * 
     * @param session The session to start
     */
    fun forceStartSession(session: UnifiedWorkoutSession)
    
    /**
     * Pauses the current active session.
     * 
     * @throws IllegalStateException if no active session
     */
    fun pauseSession()
    
    /**
     * Resumes the current paused session.
     * 
     * @throws IllegalStateException if no paused session
     */
    fun resumeSession()
    
    /**
     * Completes the current session.
     * 
     * @return true if session was completed successfully, false otherwise
     */
    suspend fun completeSession(): Boolean
    
    /**
     * Discards the current session without saving.
     */
    fun discardSession()
    
    /**
     * Adds an exercise to the current session.
     * 
     * @param exercise The exercise to add
     * @throws IllegalStateException if no active session
     */
    fun addExerciseToSession(exercise: SessionExercise)
    
    /**
     * Removes an exercise from the current session.
     * 
     * @param exerciseId The ID of the exercise to remove
     * @throws IllegalStateException if no active session
     */
    fun removeExerciseFromSession(exerciseId: ExerciseId)
    
    /**
     * Updates an exercise in the current session.
     * 
     * @param exerciseId The ID of the exercise to update
     * @param updatedExercise The updated exercise data
     * @throws IllegalStateException if no active session
     */
    fun updateExerciseInSession(exerciseId: ExerciseId, updatedExercise: SessionExercise)
    
    /**
     * Updates a specific set within an exercise.
     * 
     * @param exerciseId The ID of the exercise containing the set
     * @param setNumber The set number to update (1-based)
     * @param updatedSet The updated set data
     * @throws IllegalStateException if no active session
     */
    fun updateSetInSession(exerciseId: ExerciseId, setNumber: Int, updatedSet: SessionSet)
    
    /**
     * Moves to the next exercise in the session.
     */
    fun moveToNextExercise()
    
    /**
     * Moves to the previous exercise in the session.
     */
    fun moveToPreviousExercise()
    
    /**
     * Updates the session notes.
     * 
     * @param notes The updated notes, null to clear
     */
    fun updateSessionNotes(notes: String?)
    
    /**
     * Gets the current session duration in seconds.
     * 
     * @return Duration in seconds, 0 if no active session
     */
    fun getSessionDurationSeconds(): Long
    
    /**
     * Gets the current session duration in milliseconds.
     * 
     * @return Duration in milliseconds, 0 if no active session
     */
    fun getSessionDurationMillis(): Long
    
    /**
     * Refreshes the current session state.
     */
    fun refreshSessionState()
}