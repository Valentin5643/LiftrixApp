package com.example.liftrix.domain.usecase.session

import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import timber.log.Timber
import javax.inject.Inject

/**
 * 🔥 KEY FIX: Use case for toggling set completion with proper state propagation
 * 
 * This use case handles toggling set completion status and ensures that
 * the session state updates properly trigger UI refreshes and stat recalculation.
 * 
 * Key features:
 * - Proper set completion toggling
 * - Immediate state propagation
 * - UI refresh triggering
 * - Error handling and logging
 */
class ToggleSetCompletionUseCase @Inject constructor(
    private val sessionManager: UnifiedWorkoutSessionManager
) {
    /**
     * Toggles completion status of a specific set
     * 
     * @param exerciseId ID of the exercise containing the set
     * @param setNumber Number of the set to toggle (1-based)
     * @return Result indicating success or failure
     */
    suspend fun execute(
        exerciseId: ExerciseId,
        setNumber: Int
    ): Result<Unit> {
        return try {
            val session = sessionManager.getCurrentSession()
            if (session == null) {
                Timber.w("Cannot toggle set completion - no active session")
                return Result.failure(Exception("No active session"))
            }
            
            if (session.sessionStatus == com.example.liftrix.domain.model.UnifiedWorkoutSession.SessionStatus.COMPLETED) {
                Timber.w("Cannot toggle set completion - session completed")
                return Result.failure(Exception("Session already completed"))
            }
            
            // Find the exercise
            val exercise = session.exercises.find { it.exerciseId == exerciseId }
            if (exercise == null) {
                Timber.w("Exercise not found in session: $exerciseId")
                return Result.failure(Exception("Exercise not found"))
            }
            
            // Find the set
            val set = exercise.sets.find { it.setNumber == setNumber }
            if (set == null) {
                Timber.w("Set not found in exercise: set $setNumber")
                return Result.failure(Exception("Set not found"))
            }
            
            // Toggle completion status
            val updatedSet = if (set.isCompleted()) {
                set.uncomplete()
            } else {
                set.complete()
            }
            
            // Update the session with the new set state
            sessionManager.updateSetInSession(exerciseId, setNumber, updatedSet)
            
            // Force a session state refresh to ensure UI updates
            sessionManager.refreshSessionState()
            
            Timber.d("Set completion toggled: exercise=$exerciseId, set=$setNumber, completed=${updatedSet.isCompleted()}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle set completion: exercise=$exerciseId, set=$setNumber")
            Result.failure(e)
        }
    }
    
    /**
     * Marks a specific set as completed with actual values
     * 
     * @param exerciseId ID of the exercise containing the set
     * @param setNumber Number of the set to complete
     * @param actualReps Actual reps performed (optional)
     * @param actualWeight Actual weight used (optional)
     * @param actualTime Actual time taken (optional)
     * @param actualRpe Actual RPE rating (optional)
     * @return Result indicating success or failure
     */
    suspend fun markSetCompleted(
        exerciseId: ExerciseId,
        setNumber: Int,
        actualReps: Int? = null,
        actualWeight: com.example.liftrix.domain.model.Weight? = null,
        actualTime: Long? = null,
        actualRpe: Int? = null
    ): Result<Unit> {
        return try {
            val session = sessionManager.getCurrentSession()
            if (session == null) {
                Timber.w("Cannot mark set completed - no active session")
                return Result.failure(Exception("No active session"))
            }
            
            if (session.sessionStatus == com.example.liftrix.domain.model.UnifiedWorkoutSession.SessionStatus.COMPLETED) {
                Timber.w("Cannot mark set completed - session completed")
                return Result.failure(Exception("Session already completed"))
            }
            
            // Find the exercise
            val exercise = session.exercises.find { it.exerciseId == exerciseId }
            if (exercise == null) {
                Timber.w("Exercise not found in session: $exerciseId")
                return Result.failure(Exception("Exercise not found"))
            }
            
            // Find the set
            val set = exercise.sets.find { it.setNumber == setNumber }
            if (set == null) {
                Timber.w("Set not found in exercise: set $setNumber")
                return Result.failure(Exception("Set not found"))
            }
            
            // Update set with actual values and mark as completed
            val updatedSet = set
                .let { if (actualReps != null) it.updateActualReps(actualReps) else it }
                .let { if (actualWeight != null) it.updateActualWeight(actualWeight) else it }
                .let { if (actualTime != null) it.updateActualTime(actualTime) else it }
                .let { if (actualRpe != null) it.updateActualRpe(actualRpe) else it }
                .complete()
            
            // Update the session with the new set state
            sessionManager.updateSetInSession(exerciseId, setNumber, updatedSet)
            
            // Force a session state refresh to ensure UI updates
            sessionManager.refreshSessionState()
            
            Timber.d("Set marked as completed: exercise=$exerciseId, set=$setNumber")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark set as completed: exercise=$exerciseId, set=$setNumber")
            Result.failure(e)
        }
    }
}