package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.common.LiftrixResult
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
     * Advanced search exercises with optional equipment and muscle group filtering
     * @param query Search query string
     * @param equipment Set of equipment types to filter by (null for no filter)
     * @param muscleGroups Set of muscle groups to filter by (null for no filter)
     * @return Flow of filtered and sorted exercises
     */
    suspend fun searchExercises(
        query: String,
        equipment: Set<Equipment>? = null,
        muscleGroups: Set<ExerciseCategory>? = null
    ): LiftrixResult<List<ExerciseLibrary>>
    
    /**
     * Get recent exercises used by a specific user
     * @param userId The user ID to get recent exercises for
     * @param limit Maximum number of exercises to return
     * @return Result containing list of recent exercises
     */
    suspend fun getRecentExercises(userId: String, limit: Int = 10): LiftrixResult<List<ExerciseLibrary>>
    
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