package com.example.liftrix.domain.repository.session

import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Repository interface for active workout session management following single responsibility principle.
 * 
 * Handles:
 * - Active session state management
 * - Session creation and termination
 * - Real-time session updates
 * - Session persistence and recovery
 * 
 * Does NOT handle:
 * - Session analytics and statistics (see SessionAnalyticsRepository)
 * - Session history and past workouts (see WorkoutHistoryRepository)
 * - Session sharing and social features (see SessionSharingRepository)
 * - Template creation from sessions (see TemplateCreationRepository)
 */
interface SessionRepository {
    
    /**
     * Start a new workout session from a template for the specified user.
     * 
     * @param template The workout template to use for the session
     * @param userId The user ID for data scoping
     * @return LiftrixResult with the created active session
     */
    suspend fun startSession(template: WorkoutTemplate, userId: String): LiftrixResult<UnifiedWorkoutSession>
    
    /**
     * Start a new blank workout session for the specified user.
     * 
     * @param userId The user ID for data scoping
     * @param sessionName Optional name for the session
     * @return LiftrixResult with the created blank session
     */
    suspend fun startBlankSession(userId: String, sessionName: String? = null): LiftrixResult<UnifiedWorkoutSession>
    
    /**
     * Get the currently active session for a user.
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with active session if exists, null otherwise
     */
    suspend fun getActiveSession(userId: String): LiftrixResult<UnifiedWorkoutSession?>
    
    /**
     * Observe the active session state for real-time updates.
     * 
     * @param userId The user ID for data scoping
     * @return Flow of LiftrixResult with session state changes
     */
    fun observeActiveSession(userId: String): Flow<LiftrixResult<UnifiedWorkoutSession?>>
    
    /**
     * Update the active session state.
     * 
     * @param session The updated session data
     * @return LiftrixResult with updated session
     */
    suspend fun updateSession(session: UnifiedWorkoutSession): LiftrixResult<UnifiedWorkoutSession>
    
    /**
     * Add an exercise to the active session.
     * 
     * @param userId The user ID for data scoping and authorization
     * @param exercise The exercise to add to the session
     * @return LiftrixResult with updated session
     */
    suspend fun addExerciseToSession(userId: String, exercise: SessionExercise): LiftrixResult<UnifiedWorkoutSession>
    
    /**
     * Remove an exercise from the active session.
     * 
     * @param userId The user ID for data scoping and authorization
     * @param exerciseId The ID of the exercise to remove
     * @return LiftrixResult with updated session
     */
    suspend fun removeExerciseFromSession(userId: String, exerciseId: String): LiftrixResult<UnifiedWorkoutSession>
    
    /**
     * Add a set to an exercise in the active session.
     * 
     * @param userId The user ID for data scoping and authorization
     * @param exerciseId The exercise ID to add the set to
     * @param exerciseSet The set to add
     * @return LiftrixResult with updated session
     */
    suspend fun addSetToExercise(userId: String, exerciseId: String, exerciseSet: ExerciseSet): LiftrixResult<UnifiedWorkoutSession>
    
    /**
     * Update a set in the active session.
     * 
     * @param userId The user ID for data scoping and authorization
     * @param exerciseId The exercise ID containing the set
     * @param setIndex The index of the set to update
     * @param exerciseSet The updated set data
     * @return LiftrixResult with updated session
     */
    suspend fun updateSetInSession(
        userId: String, 
        exerciseId: String, 
        setIndex: Int, 
        exerciseSet: ExerciseSet
    ): LiftrixResult<UnifiedWorkoutSession>
    
    /**
     * Remove a set from an exercise in the active session.
     * 
     * @param userId The user ID for data scoping and authorization
     * @param exerciseId The exercise ID containing the set
     * @param setIndex The index of the set to remove
     * @return LiftrixResult with updated session
     */
    suspend fun removeSetFromExercise(userId: String, exerciseId: String, setIndex: Int): LiftrixResult<UnifiedWorkoutSession>
    
    /**
     * Complete the active workout session and convert it to a finished workout.
     * 
     * @param userId The user ID for data scoping and authorization
     * @return LiftrixResult indicating successful session completion
     */
    suspend fun completeSession(userId: String): LiftrixResult<Unit>
    
    /**
     * Cancel the active workout session without saving.
     * 
     * @param userId The user ID for data scoping and authorization
     * @return LiftrixResult indicating successful session cancellation
     */
    suspend fun cancelSession(userId: String): LiftrixResult<Unit>
    
    /**
     * Pause the active workout session (preserves session state).
     * 
     * @param userId The user ID for data scoping and authorization
     * @return LiftrixResult with updated session in paused state
     */
    suspend fun pauseSession(userId: String): LiftrixResult<UnifiedWorkoutSession>
    
    /**
     * Resume a paused workout session.
     * 
     * @param userId The user ID for data scoping and authorization
     * @return LiftrixResult with updated session in active state
     */
    suspend fun resumeSession(userId: String): LiftrixResult<UnifiedWorkoutSession>
    
    /**
     * Check if there is an active session for the user.
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with true if active session exists, false otherwise
     */
    suspend fun hasActiveSession(userId: String): LiftrixResult<Boolean>
    
    /**
     * Get session duration in minutes for the active session.
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with session duration in minutes, 0 if no active session
     */
    suspend fun getSessionDuration(userId: String): LiftrixResult<Long>
    
    /**
     * Save session state for recovery purposes (auto-save functionality).
     * 
     * @param session The session to save
     * @return LiftrixResult indicating successful save
     */
    suspend fun saveSessionState(session: UnifiedWorkoutSession): LiftrixResult<Unit>
    
    /**
     * Recover session state after app restart or crash.
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with recovered session if exists, null otherwise
     */
    suspend fun recoverSessionState(userId: String): LiftrixResult<UnifiedWorkoutSession?>
}