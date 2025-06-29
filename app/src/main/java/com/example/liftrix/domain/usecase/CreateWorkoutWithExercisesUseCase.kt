package com.example.liftrix.domain.usecase

import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.builders.plannedWorkout
import com.example.liftrix.domain.model.builders.exercise
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.domain.service.WeightMemoryService
import com.example.liftrix.domain.validation.WorkoutValidation
import com.example.liftrix.domain.validation.combine
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
            
            // Pre-fetch weight memory data for all exercises
            val weightMemoryMap = mutableMapOf<String, Weight?>()
            request.exerciseRequests.forEach { exerciseRequest ->
                val capabilities = ExerciseCapabilities.fromLibraryExercise(exerciseRequest.libraryExercise)
                if (capabilities.supportsWeight()) {
                    val lastUsedWeight = weightMemoryService.getLastUsedWeight(request.userId, exerciseRequest.libraryExercise.id)
                        .getOrNull()
                        ?.let { Weight.fromKilograms(it.toDouble()) }
                    weightMemoryMap[exerciseRequest.libraryExercise.id] = lastUsedWeight
                }
            }
            
            // Create the workout using DSL
            val workout = plannedWorkout(
                userId = request.userId,
                name = request.name,
                date = request.date ?: LocalDate.now()
            ) {
                notes = request.notes
                templateId = request.templateId
                
                // Add exercises with weight memory integration
                exercises {
                    request.exerciseRequests.forEachIndexed { index, exerciseRequest ->
                        exercise {
                            libraryExercise = exerciseRequest.libraryExercise
                            orderIndex = index
                            targetSets = exerciseRequest.targetSets
                            targetReps = exerciseRequest.targetReps
                            targetTime = exerciseRequest.targetTime
                            targetDistance = exerciseRequest.targetDistance
                            notes = exerciseRequest.notes
                            
                            // Use pre-fetched weight memory data
                            val lastUsedWeight = weightMemoryMap[exerciseRequest.libraryExercise.id]
                            targetWeight = exerciseRequest.targetWeight ?: lastUsedWeight
                            
                            // Create initial sets based on request
                            sets = createInitialSets(exerciseRequest, targetWeight)
                        }
                    }
                }
            }
            
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
     * Validates workout creation request using validation extensions
     */
    private fun validateWorkoutData(request: CreateWorkoutRequest): ValidationResult {
        val workoutValidation = WorkoutValidation.validateWorkoutBasics(
            userId = request.userId,
            name = request.name,
            exerciseCount = request.exerciseRequests.size,
            notes = request.notes
        )
        
        val exerciseValidations = request.exerciseRequests.mapIndexed { index, exerciseRequest ->
            WorkoutValidation.validateExercise(
                exerciseIndex = index,
                targetSets = exerciseRequest.targetSets,
                targetReps = exerciseRequest.targetReps,
                notes = exerciseRequest.notes
            )
        }
        
        return listOf(workoutValidation, *exerciseValidations.toTypedArray()).combine().let { result ->
            when (result) {
                is com.example.liftrix.domain.usecase.ValidationResult.Valid -> ValidationResult(false, "")
                is com.example.liftrix.domain.usecase.ValidationResult.Invalid -> ValidationResult(true, result.message)
                else -> ValidationResult(true, "Unknown validation error")
            }
        }
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