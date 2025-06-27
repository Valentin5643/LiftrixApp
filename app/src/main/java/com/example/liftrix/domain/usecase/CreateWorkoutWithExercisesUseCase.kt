package com.example.liftrix.domain.usecase

import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.domain.service.WeightMemoryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Use case for creating unified workouts with exercises from the exercise library
 */
class CreateWorkoutWithExercisesUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    private val weightMemoryService: WeightMemoryService
) {
    
    /**
     * Creates a workout with exercises and integrates weight memory
     */
    suspend operator fun invoke(request: CreateWorkoutRequest): Result<Workout> = withContext(Dispatchers.IO) {
        try {
            // Validate request
            val validationResult = validateWorkoutData(request)
            if (validationResult.hasErrors) {
                return@withContext Result.failure(
                    CreateWorkoutException.InvalidInput("request", validationResult.errorMessage)
                )
            }
            
            // Create exercises with weight memory integration
            val exercises = createExercisesWithWeightMemory(request)
            
            // Create the workout
            val workout = Workout(
                userId = request.userId,
                id = WorkoutId.generate(),
                name = request.name,
                date = request.date ?: LocalDate.now(),
                exercises = exercises,
                status = WorkoutStatus.PLANNED,
                startTime = null,
                endTime = null,
                notes = request.notes,
                templateId = request.templateId,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            
            // Save the workout
            val saveResult = workoutRepository.saveWorkout(workout)
            if (saveResult.isFailure) {
                return@withContext Result.failure(
                    CreateWorkoutException.RepositoryError(saveResult.exceptionOrNull() ?: RuntimeException("Unknown repository error"))
                )
            }
            
            // Update weight memory for all exercises
            updateWeightMemory(request.userId, request.exerciseRequests)
            
            Result.success(workout)
        } catch (e: Exception) {
            Result.failure(CreateWorkoutException.UnknownError(e))
        }
    }
    
    /**
     * Creates exercises with weight memory pre-population
     */
    private suspend fun createExercisesWithWeightMemory(request: CreateWorkoutRequest): List<Exercise> {
        return request.exerciseRequests.mapIndexed { index, exerciseRequest ->
            val libraryExercise = exerciseRequest.libraryExercise
            
            // Get last used weight from memory if available
            val capabilities = ExerciseCapabilities.fromLibraryExercise(libraryExercise)
            val lastUsedWeight = if (capabilities.supportsWeight()) {
                weightMemoryService.getLastUsedWeight(request.userId, libraryExercise.id)
                    .getOrNull()
                    ?.let { Weight.fromKilograms(it.toDouble()) }
            } else null
            
            // Create exercise with pre-populated sets
            Exercise(
                id = ExerciseId.generate(),
                workoutId = WorkoutId.generate(), // Will be updated when workout is created
                libraryExercise = libraryExercise,
                orderIndex = index,
                targetSets = exerciseRequest.targetSets,
                targetReps = exerciseRequest.targetReps,
                targetWeight = exerciseRequest.targetWeight ?: lastUsedWeight,
                targetTime = exerciseRequest.targetTime,
                targetDistance = exerciseRequest.targetDistance,
                sets = createInitialSets(exerciseRequest, lastUsedWeight),
                notes = exerciseRequest.notes,
                createdAt = Instant.now()
            )
        }
    }
    
    /**
     * Creates initial sets for an exercise with weight memory integration
     */
    private fun createInitialSets(
        exerciseRequest: ExerciseRequest,
        lastUsedWeight: Weight?
    ): List<ExerciseSet> {
        val numberOfSets = exerciseRequest.targetSets ?: 3 // Default to 3 sets
        val defaultReps = exerciseRequest.targetReps?.let { Reps(it) }
        val defaultWeight = exerciseRequest.targetWeight ?: lastUsedWeight
        
        return (1..numberOfSets).map { setNumber ->
            ExerciseSet(
                id = ExerciseSetId.generate(),
                setNumber = setNumber,
                reps = defaultReps,
                weight = defaultWeight,
                time = exerciseRequest.targetTime,
                distance = exerciseRequest.targetDistance
            )
        }
    }
    
    /**
     * Validates workout creation request
     */
    private fun validateWorkoutData(request: CreateWorkoutRequest): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate basic fields
        if (request.userId.isBlank()) {
            errors.add("User ID cannot be blank")
        }
        
        if (request.name.isBlank()) {
            errors.add("Workout name cannot be blank")
        }
        
        if (request.name.length > Workout.MAX_NAME_LENGTH) {
            errors.add("Workout name cannot exceed ${Workout.MAX_NAME_LENGTH} characters")
        }
        
        if (request.exerciseRequests.isEmpty()) {
            errors.add("Workout must have at least one exercise")
        }
        
        if (request.exerciseRequests.size > Workout.MAX_EXERCISES) {
            errors.add("Workout cannot have more than ${Workout.MAX_EXERCISES} exercises")
        }
        
        request.notes?.let { notes ->
            if (notes.length > Workout.MAX_NOTES_LENGTH) {
                errors.add("Notes cannot exceed ${Workout.MAX_NOTES_LENGTH} characters")
            }
        }
        
        // Validate individual exercises
        request.exerciseRequests.forEachIndexed { index, exerciseRequest ->
            val exerciseErrors = validateExerciseRequest(exerciseRequest, index)
            errors.addAll(exerciseErrors)
        }
        
        return ValidationResult(
            hasErrors = errors.isNotEmpty(),
            errorMessage = errors.joinToString("; ")
        )
    }
    
    /**
     * Validates individual exercise request
     */
    private fun validateExerciseRequest(exerciseRequest: ExerciseRequest, index: Int): List<String> {
        val errors = mutableListOf<String>()
        val prefix = "Exercise ${index + 1}"
        
        exerciseRequest.targetSets?.let { sets ->
            if (sets <= 0 || sets > Exercise.MAX_SETS) {
                errors.add("$prefix: Target sets must be between 1 and ${Exercise.MAX_SETS}")
            }
        }
        
        exerciseRequest.targetReps?.let { reps ->
            if (reps <= 0) {
                errors.add("$prefix: Target reps must be positive")
            }
        }
        
        exerciseRequest.notes?.let { notes ->
            if (notes.length > Exercise.MAX_NOTES_LENGTH) {
                errors.add("$prefix: Notes cannot exceed ${Exercise.MAX_NOTES_LENGTH} characters")
            }
        }
        
        return errors
    }
    
    /**
     * Updates weight memory for all exercises in the workout
     */
    private suspend fun updateWeightMemory(userId: String, exerciseRequests: List<ExerciseRequest>) {
        exerciseRequests.forEach { exerciseRequest ->
            exerciseRequest.targetWeight?.let { weight ->
                weightMemoryService.updateExerciseWeight(
                    userId = userId,
                    exerciseId = exerciseRequest.libraryExercise.id,
                    weight = weight.kilograms.toFloat(),
                    reps = exerciseRequest.targetReps ?: 0,
                    sets = exerciseRequest.targetSets ?: 0
                )
            }
        }
    }
    
    /**
     * Request data for creating a workout
     */
    data class CreateWorkoutRequest(
        val userId: String,
        val name: String,
        val date: LocalDate? = null,
        val exerciseRequests: List<ExerciseRequest>,
        val notes: String? = null,
        val templateId: WorkoutId? = null
    )
    
    /**
     * Request data for adding an exercise to a workout
     */
    data class ExerciseRequest(
        val libraryExercise: ExerciseLibrary,
        val targetSets: Int? = null,
        val targetReps: Int? = null,
        val targetWeight: Weight? = null,
        val targetTime: java.time.Duration? = null,
        val targetDistance: Distance? = null,
        val notes: String? = null
    )
    
    /**
     * Validation result for workout creation
     */
    data class ValidationResult(
        val hasErrors: Boolean,
        val errorMessage: String
    )
} 