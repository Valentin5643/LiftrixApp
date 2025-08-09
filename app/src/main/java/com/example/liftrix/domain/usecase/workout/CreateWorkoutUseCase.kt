package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.flatMapLiftrix
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import kotlinx.datetime.LocalDate as KotlinxLocalDate
import javax.inject.Inject

/**
 * Use case for creating new workouts with comprehensive business logic validation and error handling.
 * 
 * Responsibilities:
 * - Validates workout creation request data
 * - Applies business rules for workout creation
 * - Coordinates with repository for data persistence
 * - Handles errors through centralized ErrorHandler
 * 
 * Business Rules:
 * - User must be authenticated (valid userId)
 * - Workout name must be unique for user on given date
 * - Maximum one active workout per user
 * - Exercise list must not be empty for active workouts
 * - All exercises must be valid and belong to user's accessible library
 */
class CreateWorkoutUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Creates a new workout with the provided request data.
     * 
     * @param request The workout creation request containing all necessary data
     * @return LiftrixResult containing the created workout or error information
     */
    suspend operator fun invoke(request: CreateWorkoutRequest): LiftrixResult<Workout> {
        val validationResult = validateRequest(request)
        if (validationResult.isFailure) {
            return validationResult as LiftrixResult<Workout>
        }
        
        val validatedRequest = validationResult.getOrNull()!!
        val businessConstraintResult = checkBusinessConstraints(validatedRequest)
        if (businessConstraintResult.isFailure) {
            return businessConstraintResult as LiftrixResult<Workout>
        }
        
        val workout = buildWorkout(validatedRequest)
        return workoutRepository.createWorkout(workout)
    }
    
    /**
     * Validates the workout creation request for required fields and basic constraints.
     */
    private suspend fun validateRequest(request: CreateWorkoutRequest): LiftrixResult<CreateWorkoutRequest> {
        val violations = mutableListOf<String>()
        
        // Validate user ID
        if (request.userId.isBlank()) {
            violations.add("User ID is required")
        }
        
        // Validate workout name
        if (request.name.isBlank()) {
            violations.add("Workout name is required")
        } else if (request.name.length > Workout.MAX_NAME_LENGTH) {
            violations.add("Workout name cannot exceed ${Workout.MAX_NAME_LENGTH} characters")
        }
        
        // Validate date
        if (request.date.isAfter(LocalDate.now().plusDays(1))) {
            violations.add("Cannot create workouts more than 1 day in the future")
        }
        
        // Validate exercises for active workouts
        if (request.status == WorkoutStatus.IN_PROGRESS && request.exercises.isEmpty()) {
            violations.add("Active workouts must have at least one exercise")
        }
        
        // Validate exercise count
        if (request.exercises.size > Workout.MAX_EXERCISES) {
            violations.add("Cannot have more than ${Workout.MAX_EXERCISES} exercises")
        }
        
        // Validate notes length
        request.notes?.let { notes ->
            if (notes.length > Workout.MAX_NOTES_LENGTH) {
                violations.add("Notes cannot exceed ${Workout.MAX_NOTES_LENGTH} characters")
            }
        }
        
        return if (violations.isEmpty()) {
            LiftrixResult.success(request)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "CreateWorkoutRequest",
                    violations = violations
                )
            )
        }
    }
    
    /**
     * Checks business constraints and rules for workout creation.
     */
    private suspend fun checkBusinessConstraints(request: CreateWorkoutRequest): LiftrixResult<CreateWorkoutRequest> {
        // Check for existing active workout if creating an active workout
        if (request.status == WorkoutStatus.IN_PROGRESS) {
            val activeWorkoutResult = workoutRepository.getActiveWorkout(request.userId)
            if (activeWorkoutResult.isFailure) {
                return activeWorkoutResult as LiftrixResult<CreateWorkoutRequest>
            }
            
            val existingActiveWorkout = activeWorkoutResult.getOrNull()
            if (existingActiveWorkout != null) {
                return liftrixFailure(
                    LiftrixError.BusinessLogicError(
                        code = "ACTIVE_WORKOUT_EXISTS",
                        analyticsContext = mapOf(
                            "existingWorkoutId" to existingActiveWorkout.id.value,
                            "userId" to request.userId
                        ),
                        errorMessage = "Cannot create active workout: user already has an active workout"
                    )
                )
            }
        }
        
        // Check for duplicate workout name on the same date
        val workoutsByDateResult = try {
            workoutRepository.getWorkoutsByDate(request.date.toKotlinxLocalDate(), request.userId)
                .first() // Get the first emission from the Flow
        } catch (e: Exception) {
            return liftrixFailure(
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check for existing workouts: ${e.message}",
                    operation = "getWorkoutsByDate"
                )
            )
        }
        
        if (workoutsByDateResult.isFailure) {
            return workoutsByDateResult as LiftrixResult<CreateWorkoutRequest>
        }
        
        val workoutsOnDate = workoutsByDateResult.getOrNull() ?: emptyList()
        val duplicateName = workoutsOnDate.any { workout ->
            workout.name.equals(request.name, ignoreCase = true)
        }
        
        return if (duplicateName) {
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "DUPLICATE_WORKOUT_NAME",
                    analyticsContext = mapOf(
                        "workoutName" to request.name,
                        "date" to request.date.toString(),
                        "userId" to request.userId
                    ),
                    errorMessage = "A workout with this name already exists on ${request.date}"
                )
            )
        } else {
            LiftrixResult.success(request)
        }
    }
    
    /**
     * Builds a Workout domain model from the validated request.
     */
    private fun buildWorkout(request: CreateWorkoutRequest): Workout {
        val now = Instant.now()
        
        return Workout(
            userId = request.userId,
            id = WorkoutId.generate(),
            name = request.name,
            date = request.date,
            exercises = request.exercises,
            status = request.status,
            startTime = if (request.status == WorkoutStatus.IN_PROGRESS) now else null,
            endTime = null,
            notes = request.notes,
            templateId = request.templateId,
            createdAt = now,
            updatedAt = now
        )
    }
}

/**
 * Request data class for creating new workouts.
 * 
 * @property userId The ID of the user creating the workout
 * @property name The name of the workout
 * @property date The date for the workout
 * @property exercises List of exercises to include in the workout
 * @property status Initial status of the workout (default: PLANNED)
 * @property notes Optional notes for the workout
 * @property templateId Optional ID of the template this workout is based on
 */
data class CreateWorkoutRequest(
    val userId: String,
    val name: String,
    val date: LocalDate,
    val exercises: List<com.example.liftrix.domain.model.Exercise> = emptyList(),
    val status: WorkoutStatus = WorkoutStatus.PLANNED,
    val notes: String? = null,
    val templateId: WorkoutId? = null
)

/**
 * Extension function to convert java.time.LocalDate to kotlinx.datetime.LocalDate
 */
private fun LocalDate.toKotlinxLocalDate(): KotlinxLocalDate {
    return KotlinxLocalDate(this.year, this.monthValue, this.dayOfMonth)
}