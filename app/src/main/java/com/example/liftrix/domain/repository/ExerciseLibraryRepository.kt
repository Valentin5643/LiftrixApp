package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for exercise library operations
 */
interface ExerciseLibraryRepository {
    
    /**
     * Search exercises by query string with fuzzy matching
     */
    fun searchExercises(query: String): Flow<List<ExerciseLibrary>>
    
    /**
     * Get all exercises from the library
     */
    fun getAllExercises(): Flow<List<ExerciseLibrary>>
    
    /**
     * Get exercises by primary muscle group
     */
    fun getExercisesByMuscleGroup(muscleGroup: ExerciseCategory): Flow<List<ExerciseLibrary>>
    
    /**
     * Get exercises by equipment type
     */
    fun getExercisesByEquipment(equipment: Equipment): Flow<List<ExerciseLibrary>>
    
    /**
     * Get exercise variations by movement pattern
     */
    fun getVariationsByMovement(
        movementPattern: String, 
        availableEquipment: Set<Equipment>
    ): Flow<List<ExerciseLibrary>>
    
    /**
     * Get exercises filtered by multiple criteria
     */
    fun getFilteredExercises(
        muscleGroup: ExerciseCategory? = null,
        equipment: Equipment? = null,
        isCompound: Boolean? = null,
        maxDifficulty: Int? = null
    ): Flow<List<ExerciseLibrary>>
    
    /**
     * Get compound exercises for a specific muscle group
     */
    fun getCompoundExercisesForMuscle(muscleGroup: ExerciseCategory): Flow<List<ExerciseLibrary>>
} 