package com.example.liftrix.domain.repository.exercise

import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for exercise library and search operations following single responsibility principle.
 * 
 * Handles:
 * - Exercise library search and filtering with fuzzy matching
 * - Exercise metadata and categorization
 * - Equipment and muscle group filtering
 * - Recent exercise tracking for users
 * - Database seeding and placeholder fallback
 * - Exercise variations by movement pattern
 * 
 * Does NOT handle:
 * - Workout exercise instances (see WorkoutExerciseRepository)
 * - Exercise sets and repetitions (see ExerciseSetRepository)
 * - Exercise history and progression (see ExerciseHistoryRepository)
 * - Custom exercise creation (see CustomExerciseRepository)
 */
interface ExerciseRepository {
    
    /**
     * Search exercises in the library by query string with fuzzy matching.
     * Uses intelligent scoring to rank results by relevance.
     * 
     * @param query Search term for exercise name or description
     * @param limit Maximum number of results to return (default: 50)
     * @return LiftrixResult with list of matching exercises sorted by relevance
     */
    suspend fun searchExercises(query: String, limit: Int = 50): LiftrixResult<List<ExerciseLibrary>>
    
    /**
     * Search exercises with reactive flow for real-time UI updates.
     * Includes database seeding and placeholder fallback logic.
     * 
     * @param query Search term for exercise name or description  
     * @return Flow of exercises matching the query
     */
    fun searchExercisesFlow(query: String): Flow<List<ExerciseLibrary>>
    
    /**
     * Advanced search exercises with equipment and muscle group filtering.
     * 
     * @param query Search query string
     * @param equipment Set of equipment types to filter by (null for no filter)
     * @param muscleGroups Set of muscle groups to filter by (null for no filter)
     * @return LiftrixResult with filtered and sorted exercises
     */
    suspend fun searchExercisesAdvanced(
        query: String,
        equipment: Set<Equipment>? = null,
        muscleGroups: Set<ExerciseCategory>? = null
    ): LiftrixResult<List<ExerciseLibrary>>
    
    /**
     * Get recent exercises used by a specific user.
     * 
     * @param userId The user ID to get recent exercises for
     * @param limit Maximum number of exercises to return (default: 10)
     * @return LiftrixResult containing list of recent exercises
     */
    suspend fun getRecentExercises(userId: String, limit: Int = 10): LiftrixResult<List<ExerciseLibrary>>
    
    /**
     * Get all exercises from the library.
     * 
     * @return Flow of LiftrixResult with complete exercise library
     */
    fun getAllExercises(): Flow<LiftrixResult<List<ExerciseLibrary>>>
    
    /**
     * Get all exercises from the library with reactive flow.
     * Includes database seeding and placeholder fallback logic.
     * 
     * @return Flow of complete exercise library
     */
    fun getAllExercisesFlow(): Flow<List<ExerciseLibrary>>
    
    /**
     * Get exercises filtered by muscle group.
     * 
     * @param muscleGroup The target muscle group to filter by
     * @return LiftrixResult with exercises targeting the specified muscle group
     */
    suspend fun getExercisesByMuscleGroup(muscleGroup: ExerciseCategory): LiftrixResult<List<ExerciseLibrary>>
    
    /**
     * Get exercises filtered by muscle group with reactive flow.
     * 
     * @param muscleGroup The target muscle group to filter by
     * @return Flow of exercises targeting the specified muscle group
     */
    fun getExercisesByMuscleGroupFlow(muscleGroup: ExerciseCategory): Flow<List<ExerciseLibrary>>
    
    /**
     * Get exercises filtered by equipment type.
     * 
     * @param equipment The equipment type to filter by
     * @return LiftrixResult with exercises using the specified equipment
     */
    suspend fun getExercisesByEquipment(equipment: Equipment): LiftrixResult<List<ExerciseLibrary>>
    
    /**
     * Get exercises filtered by equipment type with reactive flow.
     * 
     * @param equipment The equipment type to filter by
     * @return Flow of exercises using the specified equipment
     */
    fun getExercisesByEquipmentFlow(equipment: Equipment): Flow<List<ExerciseLibrary>>
    
    /**
     * Get exercise variations by movement pattern.
     * 
     * @param movementPattern The movement pattern to search for
     * @param availableEquipment Set of available equipment to filter variations
     * @return Flow of exercise variations for the movement pattern
     */
    fun getVariationsByMovement(
        movementPattern: String, 
        availableEquipment: Set<Equipment>
    ): Flow<List<ExerciseLibrary>>
    
    /**
     * Get exercises filtered by multiple criteria.
     * 
     * @param muscleGroup Target muscle group (null for no filter)
     * @param equipment Equipment type (null for no filter)
     * @param isCompound Whether to filter for compound exercises (null for no filter)
     * @param maxDifficulty Maximum difficulty level (null for no filter)
     * @return Flow of exercises matching the criteria
     */
    fun getFilteredExercises(
        muscleGroup: ExerciseCategory? = null,
        equipment: Equipment? = null,
        isCompound: Boolean? = null,
        maxDifficulty: Int? = null
    ): Flow<List<ExerciseLibrary>>
    
    /**
     * Get compound exercises for a specific muscle group.
     * 
     * @param muscleGroup The target muscle group
     * @return Flow of compound exercises targeting the muscle group
     */
    fun getCompoundExercisesForMuscle(muscleGroup: ExerciseCategory): Flow<List<ExerciseLibrary>>
    
    /**
     * Get exercises filtered by multiple muscle groups (compound movements).
     * 
     * @param muscleGroups List of muscle groups to filter by
     * @return LiftrixResult with exercises targeting any of the specified muscle groups
     */
    suspend fun getExercisesByMuscleGroups(muscleGroups: List<ExerciseCategory>): LiftrixResult<List<ExerciseLibrary>>
    
    /**
     * Get exercises filtered by multiple equipment types.
     * 
     * @param equipmentList List of equipment types to filter by
     * @return LiftrixResult with exercises using any of the specified equipment
     */
    suspend fun getExercisesByEquipmentList(equipmentList: List<Equipment>): LiftrixResult<List<ExerciseLibrary>>
    
    /**
     * Get a specific exercise from the library by ID.
     * 
     * @param exerciseId The ID of the exercise to retrieve
     * @return LiftrixResult with exercise if found, null otherwise
     */
    suspend fun getExerciseById(exerciseId: String): LiftrixResult<ExerciseLibrary?>
    
    /**
     * Get exercises by difficulty level.
     * 
     * @param difficultyLevel Difficulty level (1-5, where 1 is beginner and 5 is expert)
     * @return LiftrixResult with exercises at the specified difficulty level
     */
    suspend fun getExercisesByDifficulty(difficultyLevel: Int): LiftrixResult<List<ExerciseLibrary>>
    
    /**
     * Get all available muscle groups in the exercise library.
     * 
     * @return LiftrixResult with list of all muscle groups
     */
    suspend fun getAllMuscleGroups(): LiftrixResult<List<ExerciseCategory>>
    
    /**
     * Get all available equipment types in the exercise library.
     * 
     * @return LiftrixResult with list of all equipment types
     */
    suspend fun getAllEquipment(): LiftrixResult<List<Equipment>>
    
    /**
     * Get recommended exercises based on user preferences and muscle groups.
     * 
     * @param muscleGroups Target muscle groups for recommendations
     * @param excludeEquipment Equipment to exclude from recommendations
     * @param limit Maximum number of recommendations (default: 10)
     * @return LiftrixResult with recommended exercises
     */
    suspend fun getRecommendedExercises(
        muscleGroups: List<ExerciseCategory>,
        excludeEquipment: List<Equipment> = emptyList(),
        limit: Int = 10
    ): LiftrixResult<List<ExerciseLibrary>>
    
    /**
     * Check if an exercise exists in the library.
     * 
     * @param exerciseId The exercise ID to check
     * @return LiftrixResult with true if exercise exists, false otherwise
     */
    suspend fun exerciseExists(exerciseId: String): LiftrixResult<Boolean>
    
    /**
     * Get total count of exercises in the library.
     * 
     * @return LiftrixResult with total exercise count
     */
    suspend fun getExerciseCount(): LiftrixResult<Int>
}