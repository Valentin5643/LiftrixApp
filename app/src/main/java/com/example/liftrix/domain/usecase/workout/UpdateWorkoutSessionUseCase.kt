package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import kotlin.Result
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * UpdateWorkoutSessionUseCase - Updates completed workout session data
 * 
 * This use case handles modification of historical workout session records with:
 * - Direct database record modification with timestamp updates
 * - Data validation preventing corruption of historical records
 * - User scoping for security and data isolation
 * - Firebase sync compatibility for edited historical data
 * 
 * Key Features:
 * - Updates original records directly (no versioning)
 * - Maintains data integrity through comprehensive validation
 * - Preserves original creation timestamps while updating modification time
 * - Supports editing of all session data: exercises, sets, reps, weights, duration
 * - Ensures Firebase sync compatibility for edited data
 * 
 * Use Cases:
 * - User corrects workout data after session completion
 * - User updates session notes or duration
 * - User modifies exercise sets/reps/weights from completed session
 * - User fixes incorrect data entry from past workouts
 */
class UpdateWorkoutSessionUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {

    suspend operator fun invoke(params: UpdateWorkoutSessionRequest): Result<Workout> {
        return Result.runCatching {
            // Get current user ID for security validation
            val currentUserId = getCurrentUserIdUseCase()
            if (currentUserId.isNullOrBlank()) {
                throw IllegalStateException("User not authenticated")
            }
            
            // Validate that the user owns this session
            if (params.updatedSession.userId != currentUserId) {
                throw SecurityException("Cannot update session belonging to another user")
            }
            
            // Validate session data integrity
            val validationResult = validateSessionData(params.updatedSession)
            if (validationResult != null) {
                throw validationResult
            }
            
            // Ensure we preserve original creation data while updating modification time
            val sessionToUpdate = params.updatedSession.copy(
                updatedAt = Instant.now(),
                // Preserve original creation timestamp and user ID
                createdAt = params.originalCreatedAt ?: params.updatedSession.createdAt,
                userId = currentUserId
            )
            
            Timber.d("Updating workout session for user: $currentUserId")
            
            val updateResult = workoutRepository.updateWorkout(sessionToUpdate)
            val updatedSession = updateResult.getOrThrow()
            
            Timber.i("Successfully updated workout session")
            updatedSession
        }
    }
    
    /**
     * Validates session data to prevent corruption
     */
    private fun validateSessionData(session: Workout): Exception? {
        // Validate session has required fields
        if (session.name.isBlank()) {
            return IllegalArgumentException("Session name cannot be empty")
        }
        
        // Validate exercises and sets
        if (session.exercises.isEmpty()) {
            return IllegalArgumentException("Session must contain at least one exercise")
        }
        
        // Validate each exercise has valid data
        session.exercises.forEach { exercise ->
            if (exercise.sets.isEmpty()) {
                return IllegalArgumentException("Exercise must have at least one set")
            }
            
            // Validate set data
            exercise.sets.forEach { set ->
                if (set.reps != null && set.reps!!.count < 0) {
                    return IllegalArgumentException("Reps cannot be negative")
                }
                
                if (set.weight != null && set.weight!!.toPounds() < 0) {
                    return IllegalArgumentException("Weight cannot be negative")
                }
            }
        }
        
        // Validate session timing if present
        if (session.startTime != null && session.endTime != null) {
            if (session.endTime!!.isBefore(session.startTime)) {
                return IllegalArgumentException("Session end time cannot be before start time")
            }
        }
        
        return null // No validation errors
    }
}

/**
 * Request parameters for UpdateWorkoutSessionUseCase
 */
data class UpdateWorkoutSessionRequest(
    val updatedSession: Workout,
    val originalCreatedAt: Instant? = null
)