package com.example.liftrix.domain.model

import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.ExerciseLibrary

/**
 * Represents a group of exercise variations organized by movement pattern.
 * 
 * This data class groups exercises that share similar movement patterns (e.g., squats, presses, pulls)
 * and provides both library-defined variations and user-created custom variations.
 * 
 * @param movementPattern The movement pattern that defines this group (e.g., "squat", "press", "pull")
 * @param libraryVariations List of exercise variations from the standard exercise library
 * @param customVariations List of user-created custom exercise variations
 */
data class ExerciseGroup(
    val movementPattern: String,
    val libraryVariations: List<ExerciseLibrary>,
    val customVariations: List<CustomExercise>
) {
    
    /**
     * Returns all exercise variations in this group (both library and custom)
     */
    val allVariations: List<Any>
        get() = libraryVariations + customVariations
    
    /**
     * Returns the total number of variations in this group
     */
    val totalCount: Int
        get() = libraryVariations.size + customVariations.size
    
    /**
     * Checks if this group has any variations
     */
    val hasVariations: Boolean
        get() = libraryVariations.isNotEmpty() || customVariations.isNotEmpty()
    
    /**
     * Filters library variations by compatible equipment
     */
    fun getLibraryVariationsForEquipment(availableEquipment: Set<String>): List<ExerciseLibrary> {
        return libraryVariations.filter { exercise ->
            exercise.equipment.name in availableEquipment
        }
    }
    
    /**
     * Filters custom variations by compatible equipment
     */
    fun getCustomVariationsForEquipment(availableEquipment: Set<String>): List<CustomExercise> {
        return customVariations.filter { exercise ->
            exercise.equipment.name in availableEquipment
        }
    }
    
    /**
     * Returns library variations sorted by difficulty level
     */
    fun getLibraryVariationsByDifficulty(): List<ExerciseLibrary> {
        return libraryVariations.sortedBy { it.difficultyLevel ?: 0 }
    }
}