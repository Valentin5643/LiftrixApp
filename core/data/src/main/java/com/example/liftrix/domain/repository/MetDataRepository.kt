package com.example.liftrix.domain.repository

import com.example.liftrix.data.local.entity.MetDataEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for MET (Metabolic Equivalent of Task) data operations
 * 
 * Provides access to research-backed MET values for accurate calorie calculations.
 * Follows the app's clean architecture pattern with domain layer interfaces.
 * 
 * Key Responsibilities:
 * - Efficient lookup of MET data by exercise characteristics
 * - Category-based fallback values for unknown exercises
 * - Performance optimization through database queries
 * - Error handling with LiftrixResult pattern
 */
interface MetDataRepository {
    
    /**
     * Gets MET data for a specific exercise type and category
     * 
     * @param exerciseType The specific exercise type (e.g., "bench_press", "squats")
     * @param category The exercise category (e.g., "CHEST", "LEGS")
     * @return LiftrixResult containing MetDataEntity or error
     */
    suspend fun getMetDataByTypeAndCategory(
        exerciseType: String,
        category: String
    ): LiftrixResult<MetDataEntity?>
    
    /**
     * Gets MET data for a specific exercise type, category, and equipment combination
     * 
     * @param exerciseType The specific exercise type
     * @param category The exercise category
     * @param equipment The equipment type (e.g., "barbell", "dumbbell", "bodyweight")
     * @return LiftrixResult containing MetDataEntity or error
     */
    suspend fun getMetDataByTypeAndCategoryAndEquipment(
        exerciseType: String,
        category: String,
        equipment: String?
    ): LiftrixResult<MetDataEntity?>
    
    /**
     * Gets all MET data for a specific exercise category
     * 
     * @param category The exercise category
     * @return LiftrixResult containing list of MetDataEntity or error
     */
    suspend fun getMetDataByCategory(category: String): LiftrixResult<List<MetDataEntity>>
    
    /**
     * Gets MET data for exercises matching a partial name
     * 
     * @param partialName Partial exercise name to match
     * @return LiftrixResult containing list of MetDataEntity or error
     */
    suspend fun getMetDataByPartialName(partialName: String): LiftrixResult<List<MetDataEntity>>
    
    /**
     * Gets the average MET value for a category (fallback calculation)
     * 
     * @param category The exercise category
     * @return LiftrixResult containing average MET value or error
     */
    suspend fun getAverageMetForCategory(category: String): LiftrixResult<Float?>
    
    /**
     * Gets the maximum MET value for a category (vigorous exercises)
     * 
     * @param category The exercise category
     * @return LiftrixResult containing maximum MET value or error
     */
    suspend fun getMaxMetForCategory(category: String): LiftrixResult<Float?>
    
    /**
     * Gets all distinct exercise categories from MET data
     * 
     * @return LiftrixResult containing list of category names or error
     */
    suspend fun getDistinctCategories(): LiftrixResult<List<String>>
    
    /**
     * Gets all distinct equipment types from MET data
     * 
     * @return LiftrixResult containing list of equipment types or error
     */
    suspend fun getDistinctEquipmentTypes(): LiftrixResult<List<String>>
    
    /**
     * Gets all MET data as a Flow for reactive updates
     * 
     * @return Flow of list of MetDataEntity
     */
    fun getAllMetDataFlow(): Flow<List<MetDataEntity>>
    
    /**
     * Gets count of MET data entries
     * 
     * @return LiftrixResult containing count or error
     */
    suspend fun getMetDataCount(): LiftrixResult<Int>
    
    /**
     * Finds the best matching MET data for an exercise name and category
     * Uses fuzzy matching logic to find appropriate MET values
     * 
     * @param exerciseName Name of the exercise
     * @param exerciseCategory Category of the exercise
     * @param equipmentType Optional equipment type for more precise matching
     * @return LiftrixResult containing best matching MetDataEntity or null if not found
     */
    suspend fun findBestMatchingMetData(
        exerciseName: String,
        exerciseCategory: String,
        equipmentType: String? = null
    ): LiftrixResult<MetDataEntity?>
}