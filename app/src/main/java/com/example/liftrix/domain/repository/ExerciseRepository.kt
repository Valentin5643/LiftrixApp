package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.*

/**
 * Repository interface for exercise-related operations with enhanced support for
 * flexible metrics, weight memory, and exercise history tracking
 */
interface ExerciseRepository {
    
    // Core CRUD operations
    suspend fun saveExercise(exercise: Exercise): Result<Exercise>
    suspend fun getExercisesByWorkout(workoutId: WorkoutId): Result<List<Exercise>>
    suspend fun updateExercise(exercise: Exercise): Result<Exercise>
    suspend fun deleteExercise(exerciseId: ExerciseId): Result<Unit>
    
    // Exercise Set operations
    suspend fun saveExerciseSet(exerciseId: ExerciseId, set: ExerciseSet): Result<ExerciseSet>
    suspend fun updateExerciseSet(set: ExerciseSet): Result<ExerciseSet>
    suspend fun deleteExerciseSet(setId: ExerciseSetId): Result<Unit>
    
    // Weight memory functionality
    suspend fun getLastUsedWeight(userId: UserId, exerciseLibraryId: String): Result<Weight?>
    suspend fun updateWeightMemory(userId: UserId, exerciseLibraryId: String, weight: Weight): Result<Unit>
    
    // Exercise history for analytics and progression tracking
    suspend fun getExerciseHistory(
        userId: UserId, 
        exerciseLibraryId: String, 
        limit: Int = 10
    ): Result<List<ExerciseSet>>
    
    // Advanced queries
    suspend fun getExerciseWithSets(exerciseId: ExerciseId): Result<Exercise?>
    suspend fun getExercisesByType(workoutId: WorkoutId, exerciseType: ExerciseType): Result<List<Exercise>>
}