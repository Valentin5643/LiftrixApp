package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.MetDataDao
import com.example.liftrix.data.local.entity.MetDataEntity
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.MetDataRepository
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MetDataRepository using Room database
 * 
 * Provides efficient access to MET data with comprehensive error handling
 * and performance optimization. Follows the app's offline-first architecture
 * pattern with cached database lookups.
 * 
 * Performance Characteristics:
 * - Indexed queries for fast lookups by exercise type and category
 * - Fuzzy matching for exercise name variations
 * - Fallback mechanisms for missing data
 * - Error handling with detailed logging
 */
@Singleton
class MetDataRepositoryImpl @Inject constructor(
    private val metDataDao: MetDataDao
) : MetDataRepository {
    
    override suspend fun getMetDataByTypeAndCategory(
        exerciseType: String,
        category: String
    ): LiftrixResult<MetDataEntity?> {
        return try {
            val metData = metDataDao.getMetDataByTypeAndCategory(exerciseType, category)
            Result.success(metData)
        } catch (e: Exception) {
            Timber.e(e, "Error getting MET data by type and category: $exerciseType, $category")
            Result.failure(LiftrixError.DatabaseError("Failed to get MET data: ${e.message}"))
        }
    }
    
    override suspend fun getMetDataByTypeAndCategoryAndEquipment(
        exerciseType: String,
        category: String,
        equipment: String?
    ): LiftrixResult<MetDataEntity?> {
        return try {
            val metData = metDataDao.getMetDataByTypeAndCategoryAndEquipment(exerciseType, category, equipment)
            Result.success(metData)
        } catch (e: Exception) {
            Timber.e(e, "Error getting MET data by type, category, and equipment: $exerciseType, $category, $equipment")
            Result.failure(LiftrixError.DatabaseError("Failed to get MET data: ${e.message}"))
        }
    }
    
    override suspend fun getMetDataByCategory(category: String): LiftrixResult<List<MetDataEntity>> {
        return try {
            val metDataList = metDataDao.getMetDataByCategory(category)
            Result.success(metDataList)
        } catch (e: Exception) {
            Timber.e(e, "Error getting MET data by category: $category")
            Result.failure(LiftrixError.DatabaseError("Failed to get MET data by category: ${e.message}"))
        }
    }
    
    override suspend fun getMetDataByPartialName(partialName: String): LiftrixResult<List<MetDataEntity>> {
        return try {
            val metDataList = metDataDao.getMetDataByPartialName(partialName)
            Result.success(metDataList)
        } catch (e: Exception) {
            Timber.e(e, "Error getting MET data by partial name: $partialName")
            Result.failure(LiftrixError.DatabaseError("Failed to get MET data by partial name: ${e.message}"))
        }
    }
    
    override suspend fun getAverageMetForCategory(category: String): LiftrixResult<Float?> {
        return try {
            val averageMet = metDataDao.getAverageMetForCategory(category)
            Result.success(averageMet)
        } catch (e: Exception) {
            Timber.e(e, "Error getting average MET for category: $category")
            Result.failure(LiftrixError.DatabaseError("Failed to get average MET: ${e.message}"))
        }
    }
    
    override suspend fun getMaxMetForCategory(category: String): LiftrixResult<Float?> {
        return try {
            val maxMet = metDataDao.getMaxMetForCategory(category)
            Result.success(maxMet)
        } catch (e: Exception) {
            Timber.e(e, "Error getting max MET for category: $category")
            Result.failure(LiftrixError.DatabaseError("Failed to get max MET: ${e.message}"))
        }
    }
    
    override suspend fun getDistinctCategories(): LiftrixResult<List<String>> {
        return try {
            val categories = metDataDao.getDistinctCategories()
            Result.success(categories)
        } catch (e: Exception) {
            Timber.e(e, "Error getting distinct categories")
            Result.failure(LiftrixError.DatabaseError("Failed to get categories: ${e.message}"))
        }
    }
    
    override suspend fun getDistinctEquipmentTypes(): LiftrixResult<List<String>> {
        return try {
            val equipmentTypes = metDataDao.getDistinctEquipmentTypes()
            Result.success(equipmentTypes)
        } catch (e: Exception) {
            Timber.e(e, "Error getting distinct equipment types")
            Result.failure(LiftrixError.DatabaseError("Failed to get equipment types: ${e.message}"))
        }
    }
    
    override fun getAllMetDataFlow(): Flow<List<MetDataEntity>> {
        return metDataDao.getAllMetData()
    }
    
    override suspend fun getMetDataCount(): LiftrixResult<Int> {
        return try {
            val count = metDataDao.getMetDataCount()
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Error getting MET data count")
            Result.failure(LiftrixError.DatabaseError("Failed to get MET data count: ${e.message}"))
        }
    }
    
    override suspend fun findBestMatchingMetData(
        exerciseName: String,
        exerciseCategory: String,
        equipmentType: String?
    ): LiftrixResult<MetDataEntity?> {
        return try {
            val normalizedExerciseName = exerciseName.lowercase().trim()
            val normalizedCategory = exerciseCategory.uppercase().trim()
            
            // Strategy 1: Exact match by exercise type and category with equipment
            if (equipmentType != null) {
                val exactMatchWithEquipment = metDataDao.getMetDataByTypeAndCategoryAndEquipment(
                    normalizedExerciseName, normalizedCategory, equipmentType
                )
                if (exactMatchWithEquipment != null) {
                    Timber.d("Found exact MET match with equipment: $exerciseName -> ${exactMatchWithEquipment.exerciseType}")
                    return Result.success(exactMatchWithEquipment)
                }
            }
            
            // Strategy 2: Exact match by exercise type and category (without equipment constraint)
            val exactMatch = metDataDao.getMetDataByTypeAndCategory(normalizedExerciseName, normalizedCategory)
            if (exactMatch != null) {
                Timber.d("Found exact MET match: $exerciseName -> ${exactMatch.exerciseType}")
                return Result.success(exactMatch)
            }
            
            // Strategy 3: Partial match within category
            val partialMatches = metDataDao.getMetDataByCategory(normalizedCategory)
            val bestPartialMatch = findBestPartialMatch(normalizedExerciseName, partialMatches, equipmentType)
            if (bestPartialMatch != null) {
                Timber.d("Found partial MET match: $exerciseName -> ${bestPartialMatch.exerciseType}")
                return Result.success(bestPartialMatch)
            }
            
            // Strategy 4: Fuzzy match by partial name across all categories
            val fuzzyMatches = metDataDao.getMetDataByPartialName(normalizedExerciseName)
            val bestFuzzyMatch = findBestPartialMatch(normalizedExerciseName, fuzzyMatches, equipmentType)
            if (bestFuzzyMatch != null) {
                Timber.d("Found fuzzy MET match: $exerciseName -> ${bestFuzzyMatch.exerciseType}")
                return Result.success(bestFuzzyMatch)
            }
            
            // Strategy 5: Category-based fallback (return first available in category)
            val categoryFallback = partialMatches.firstOrNull()
            if (categoryFallback != null) {
                Timber.d("Using category fallback MET: $exerciseName -> ${categoryFallback.exerciseType} (category: $normalizedCategory)")
                return Result.success(categoryFallback)
            }
            
            Timber.d("No MET data found for exercise: $exerciseName (category: $normalizedCategory)")
            Result.success(null)
            
        } catch (e: Exception) {
            Timber.e(e, "Error finding best matching MET data for: $exerciseName")
            Result.failure(LiftrixError.DatabaseError("Failed to find MET data: ${e.message}"))
        }
    }
    
    /**
     * Finds the best partial match from a list of MET data entries
     * 
     * @param exerciseName The normalized exercise name to match
     * @param candidates List of candidate MET data entries
     * @param equipmentType Optional equipment type for additional scoring
     * @return Best matching MetDataEntity or null
     */
    private fun findBestPartialMatch(
        exerciseName: String,
        candidates: List<MetDataEntity>,
        equipmentType: String?
    ): MetDataEntity? {
        if (candidates.isEmpty()) return null
        
        return candidates
            .map { metData -> metData to calculateMatchScore(exerciseName, metData, equipmentType) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }
    
    /**
     * Calculates a match score between an exercise name and MET data entry
     * 
     * @param exerciseName The exercise name to match
     * @param metData The MET data entry to score
     * @param equipmentType Optional equipment type for additional scoring
     * @return Match score (higher is better)
     */
    private fun calculateMatchScore(
        exerciseName: String,
        metData: MetDataEntity,
        equipmentType: String?
    ): Int {
        var score = 0
        val metExerciseType = metData.exerciseType.lowercase()
        
        // Exact substring match gets highest score
        if (exerciseName.contains(metExerciseType) || metExerciseType.contains(exerciseName)) {
            score += 10
        }
        
        // Word-level matches
        val exerciseWords = exerciseName.split("_", " ", "-")
        val metWords = metExerciseType.split("_", " ", "-")
        
        for (exerciseWord in exerciseWords) {
            for (metWord in metWords) {
                if (exerciseWord == metWord) {
                    score += 5
                } else if (exerciseWord.contains(metWord) || metWord.contains(exerciseWord)) {
                    score += 2
                }
            }
        }
        
        // Equipment type bonus
        if (equipmentType != null && metData.appliesToEquipment(equipmentType)) {
            score += 3
        }
        
        // Length similarity bonus (prefer similar length exercises)
        val lengthDiff = kotlin.math.abs(exerciseName.length - metExerciseType.length)
        if (lengthDiff <= 3) {
            score += 1
        }
        
        return score
    }
}