package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.CustomExerciseId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for custom exercise operations
 */
interface CustomExerciseRepository {
    
    /**
     * Creates a new custom exercise for the user
     */
    suspend fun createCustomExercise(
        userId: String,
        name: String,
        primaryMuscle: ExerciseCategory,
        equipment: Equipment,
        secondaryMuscles: Set<ExerciseCategory> = emptySet(),
        difficulty: Int? = null,
        notes: String? = null
    ): Result<CustomExercise>
    
    /**
     * Gets all custom exercises for a user
     */
    fun getAllCustomExercises(userId: String): Flow<List<CustomExercise>>
    
    /**
     * Gets custom exercises by muscle group
     */
    fun getCustomExercisesByMuscleGroup(
        userId: String, 
        muscleGroup: ExerciseCategory
    ): Flow<List<CustomExercise>>
    
    /**
     * Gets custom exercises by equipment
     */
    fun getCustomExercisesByEquipment(
        userId: String,
        equipment: Equipment
    ): Flow<List<CustomExercise>>
    
    /**
     * Searches custom exercises by name
     */
    fun searchCustomExercises(
        userId: String,
        query: String
    ): Flow<List<CustomExercise>>
    
    /**
     * Gets a specific custom exercise by ID
     */
    suspend fun getCustomExercise(
        userId: String,
        exerciseId: CustomExerciseId
    ): Result<CustomExercise>
    
    /**
     * Updates an existing custom exercise
     */
    suspend fun updateCustomExercise(
        userId: String,
        exercise: CustomExercise
    ): Result<CustomExercise>
    
    /**
     * Deletes a custom exercise
     */
    suspend fun deleteCustomExercise(
        userId: String,
        exerciseId: CustomExerciseId
    ): Result<Unit>
    
    /**
     * Checks if a custom exercise name is unique for the user
     */
    suspend fun isExerciseNameUnique(
        userId: String,
        name: String,
        excludeId: CustomExerciseId? = null
    ): Boolean
} 