package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.flatMapLiftrix
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for retrieving a specific workout by ID with user authorization and error handling.
 * 
 * Responsibilities:
 * - Validates workout ID and user authorization
 * - Retrieves workout from repository with proper error handling
 * - Applies security constraints for user data access
 * - Handles not found scenarios gracefully
 * 
 * Business Rules:
 * - User can only access their own workouts
 * - Invalid workout IDs return appropriate error
 * - Non-existent workouts return null result (not error)
 * - User authorization is enforced at use case level
 */
class GetWorkoutByIdUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Retrieves a workout by ID for the specified user.
     * 
     * @param request The request containing workout ID and user ID
     * @return LiftrixResult containing the workout if found, null if not found, or error
     */
    suspend operator fun invoke(request: GetWorkoutByIdRequest): LiftrixResult<Workout?> {
        Timber.d("🔥 EDIT-WORKOUT-DEBUG: GetWorkoutByIdUseCase starting - workoutId: ${request.workoutId.value}, userId: ${request.userId}")
        
        val validationResult = validateRequest(request)
        if (validationResult.isFailure) {
            Timber.e("🔥 EDIT-WORKOUT-DEBUG: Request validation failed - ${validationResult.exceptionOrNull()?.message}")
            return Result.failure(validationResult.exceptionOrNull()!!)
        }
        
        val validatedRequest = validationResult.getOrThrow()
        Timber.d("🔥 EDIT-WORKOUT-DEBUG: Request validation passed, calling repository - workoutId: ${validatedRequest.workoutId.value}, userId: ${validatedRequest.userId}")
        
        val workoutResult = workoutRepository.getWorkoutById(validatedRequest.workoutId, validatedRequest.userId)
        if (workoutResult.isFailure) {
            Timber.e("🔥 EDIT-WORKOUT-DEBUG: Repository call failed - ${workoutResult.exceptionOrNull()?.message}")
            return workoutResult
        }
        
        val workout = workoutResult.getOrThrow()
        Timber.d("🔥 EDIT-WORKOUT-DEBUG: Repository returned workout - ${if (workout != null) "FOUND" else "NOT FOUND"}")
        
        // Additional authorization check if workout is found
        if (workout != null && workout.userId != request.userId) {
            Timber.e("🔥 EDIT-WORKOUT-DEBUG: Authorization failed - workout.userId: ${workout.userId}, request.userId: ${request.userId}")
            return liftrixFailure(
                LiftrixError.AuthenticationError(
                    errorMessage = "Access denied: workout belongs to different user",
                    errorCode = "WORKOUT_ACCESS_DENIED",
                    analyticsContext = mapOf(
                        "workoutId" to request.workoutId.value,
                        "requestedByUserId" to request.userId,
                        "actualUserId" to workout.userId
                    )
                )
            )
        } else {
            if (workout != null) {
                Timber.d("🔥 EDIT-WORKOUT-DEBUG: Authorization passed, returning workout - id: ${workout.id.value}, name: ${workout.name}")
            } else {
                Timber.d("🔥 EDIT-WORKOUT-DEBUG: Workout is null, returning null result")
            }
            return Result.success(workout)
        }
    }
    
    /**
     * Validates the request parameters for retrieving a workout.
     */
    private fun validateRequest(request: GetWorkoutByIdRequest): LiftrixResult<GetWorkoutByIdRequest> {
        val violations = mutableListOf<String>()
        
        // Validate user ID
        if (request.userId.isBlank()) {
            violations.add("User ID is required")
        }
        
        // Validate workout ID format (basic validation)
        if (request.workoutId.value.isBlank()) {
            violations.add("Workout ID cannot be blank")
        }
        
        return if (violations.isEmpty()) {
            LiftrixResult.success(request)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "GetWorkoutByIdRequest",
                    violations = violations
                )
            )
        }
    }
}

/**
 * Request data class for retrieving a workout by ID.
 * 
 * @property workoutId The ID of the workout to retrieve
 * @property userId The ID of the user requesting the workout (for authorization)
 */
data class GetWorkoutByIdRequest(
    val workoutId: WorkoutId,
    val userId: String
)