package com.example.liftrix.domain.usecase.session

import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * 🔥 KEY FIX: Use case for adding exercises to session-scoped list only
 * 
 * This use case handles adding exercises to the current workout session
 * without affecting global exercise lists or other sessions. It replaces
 * the complex global exercise injection system with clean session-scoped
 * exercise management.
 * 
 * Key features:
 * - Session-scoped exercise addition
 * - Proper exercise validation
 * - Automatic set generation
 * - No global state pollution
 */
class AddExerciseToSessionUseCase @Inject constructor(
    private val sessionManager: UnifiedWorkoutSessionManager,
    private val exerciseLibraryRepository: ExerciseLibraryRepository
) {
    /**
     * Adds an exercise to the current session by exercise ID
     */
    suspend fun execute(exerciseId: ExerciseId): Result<Unit> {
        return try {
            // Get current session
            val currentSession = sessionManager.getCurrentSession()
            if (currentSession == null) {
                Timber.w("Cannot add exercise - no active session")
                return Result.failure(Exception("No active workout session"))
            }
            
            // Check if exercise already exists in session
            val existingExercise = currentSession.exercises.find { it.exerciseId == exerciseId }
            if (existingExercise != null) {
                Timber.w("Exercise already exists in session: ${exerciseId.value}")
                return Result.failure(Exception("Exercise already added to this workout"))
            }
            
            // Get exercise from library
            val allExercises = exerciseLibraryRepository.getAllExercises().first()
            val libraryExercise = allExercises.find { it.id == exerciseId.value }
            
            if (libraryExercise == null) {
                Timber.w("Exercise not found in library: ${exerciseId.value}")
                return Result.failure(Exception("Exercise not found"))
            }
            
            // Create session exercise with proper defaults
            val sessionExercise = SessionExercise.createBlank(
                exerciseId = exerciseId,
                name = libraryExercise.name,
                category = libraryExercise.primaryMuscleGroup,
                primaryMuscle = libraryExercise.primaryMuscleGroup,
                equipment = libraryExercise.equipment,
                orderIndex = currentSession.exercises.size,
                initialSets = 3 // Default to 3 sets
            )
            
            // Add to session
            sessionManager.addExerciseToSession(sessionExercise)
            
            Timber.d("Exercise added to session: ${libraryExercise.name}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to add exercise to session")
            Result.failure(e)
        }
    }
    
    /**
     * Adds a custom exercise to the current session
     */
    suspend fun executeCustom(
        name: String,
        category: com.example.liftrix.domain.model.ExerciseCategory,
        primaryMuscle: com.example.liftrix.domain.model.ExerciseCategory,
        initialSets: Int = 3
    ): Result<Unit> {
        return try {
            // Get current session
            val currentSession = sessionManager.getCurrentSession()
            if (currentSession == null) {
                Timber.w("Cannot add custom exercise - no active session")
                return Result.failure(Exception("No active workout session"))
            }
            
            // Validate input
            if (name.isBlank()) {
                return Result.failure(Exception("Exercise name cannot be blank"))
            }
            
            if (initialSets < 1 || initialSets > 10) {
                return Result.failure(Exception("Initial sets must be between 1 and 10"))
            }
            
            // Check if exercise with same name already exists in session
            val existingExercise = currentSession.exercises.find { 
                it.name.equals(name, ignoreCase = true) 
            }
            if (existingExercise != null) {
                Timber.w("Exercise with name '$name' already exists in session")
                return Result.failure(Exception("Exercise with this name already exists in workout"))
            }
            
            // Create session exercise
            val sessionExercise = SessionExercise.createBlank(
                exerciseId = ExerciseId.generate(),
                name = name.trim(),
                category = category,
                primaryMuscle = primaryMuscle,
                equipment = Equipment.BODYWEIGHT_ONLY, // Default for custom exercises
                orderIndex = currentSession.exercises.size,
                initialSets = initialSets
            )
            
            // Add to session
            sessionManager.addExerciseToSession(sessionExercise)
            
            Timber.d("Custom exercise added to session: $name")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to add custom exercise to session")
            Result.failure(e)
        }
    }
    
    /**
     * Adds multiple exercises to the current session
     */
    suspend fun executeMultiple(exerciseIds: List<ExerciseId>): Result<Unit> {
        return try {
            val currentSession = sessionManager.getCurrentSession()
            if (currentSession == null) {
                Timber.w("Cannot add exercises - no active session")
                return Result.failure(Exception("No active workout session"))
            }
            
            if (exerciseIds.isEmpty()) {
                return Result.failure(Exception("No exercises to add"))
            }
            
            // Filter out exercises already in session
            val newExerciseIds = exerciseIds.filter { exerciseId ->
                !currentSession.exercises.any { it.exerciseId == exerciseId }
            }
            
            if (newExerciseIds.isEmpty()) {
                return Result.failure(Exception("All selected exercises are already in the workout"))
            }
            
            // Add each exercise
            var successCount = 0
            val errors = mutableListOf<String>()
            
            for (exerciseId in newExerciseIds) {
                val result = execute(exerciseId)
                if (result.isSuccess) {
                    successCount++
                } else {
                    errors.add("${exerciseId.value}: ${result.exceptionOrNull()?.message}")
                }
            }
            
            if (successCount > 0) {
                Timber.d("Added $successCount exercises to session")
                if (errors.isNotEmpty()) {
                    Timber.w("Some exercises failed to add: $errors")
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to add any exercises: ${errors.joinToString(", ")}"))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to add multiple exercises to session")
            Result.failure(e)
        }
    }
}