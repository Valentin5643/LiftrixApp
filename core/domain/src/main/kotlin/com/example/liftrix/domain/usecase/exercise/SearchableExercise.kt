package com.example.liftrix.domain.usecase.exercise

import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.ExerciseLibrary

/**
 * Sealed class representing different types of searchable exercises.
 * 
 * This abstraction allows the UI to work with both library exercises and custom exercises
 * in a unified way, while maintaining type safety and enabling different behaviors
 * for each exercise type.
 * 
 * Key benefits:
 * - Unified interface for exercise selection UI components
 * - Type-safe handling of library vs custom exercises
 * - Consistent search and display patterns
 * - Easy extension for future exercise types
 */
sealed class SearchableExercise {
    
    /**
     * Abstract property to get the exercise name for display and search purposes.
     */
    abstract val name: String
    
    /**
     * Abstract property to get a unique identifier for the exercise.
     */
    abstract val id: String
    
    /**
     * Represents a library exercise from the built-in exercise database.
     * 
     * @property exercise The library exercise data
     */
    data class LibraryExercise(
        val exercise: ExerciseLibrary
    ) : SearchableExercise() {
        
        override val name: String
            get() = exercise.name
            
        override val id: String 
            get() = exercise.id
    }
    
    /**
     * Represents a custom exercise created by the user.
     * 
     * @property exercise The custom exercise data
     */
    data class CustomExercise(
        val exercise: com.example.liftrix.domain.model.CustomExercise
    ) : SearchableExercise() {
        
        override val name: String
            get() = exercise.name
            
        override val id: String
            get() = exercise.id.value
    }
}

/**
 * Extension function to check if a SearchableExercise is a library exercise.
 */
fun SearchableExercise.isLibraryExercise(): Boolean = this is SearchableExercise.LibraryExercise

/**
 * Extension function to check if a SearchableExercise is a custom exercise.
 */
fun SearchableExercise.isCustomExercise(): Boolean = this is SearchableExercise.CustomExercise

/**
 * Extension function to get the library exercise if this is a LibraryExercise.
 * Returns null if this is not a LibraryExercise.
 */
fun SearchableExercise.asLibraryExercise(): ExerciseLibrary? = 
    (this as? SearchableExercise.LibraryExercise)?.exercise

/**
 * Extension function to get the custom exercise if this is a CustomExercise.
 * Returns null if this is not a CustomExercise.
 */
fun SearchableExercise.asCustomExercise(): com.example.liftrix.domain.model.CustomExercise? = 
    (this as? SearchableExercise.CustomExercise)?.exercise

/**
 * Extension function to calculate a match score for search relevance.
 * Higher scores indicate better matches.
 * 
 * @param query The search query to match against
 * @return A score representing how well this exercise matches the query
 */
fun SearchableExercise.calculateMatchScore(query: String): Int {
    if (query.isBlank()) return 0
    
    val exerciseName = name.lowercase()
    val searchQuery = query.lowercase()
    
    var score = 0
    
    // Exact match gets highest score
    if (exerciseName == searchQuery) {
        score += 100
    }
    
    // Name starts with query gets high score
    if (exerciseName.startsWith(searchQuery)) {
        score += 50
    }
    
    // Name contains query gets medium score
    if (exerciseName.contains(searchQuery)) {
        score += 25
    }
    
    // Check additional searchable terms for library exercises
    when (this) {
        is SearchableExercise.LibraryExercise -> {
            exercise.searchableTerms.forEach { term ->
                val searchableTerm = term.lowercase()
                if (searchableTerm == searchQuery) {
                    score += 40
                } else if (searchableTerm.startsWith(searchQuery)) {
                    score += 20
                } else if (searchableTerm.contains(searchQuery)) {
                    score += 10
                }
            }
        }
        is SearchableExercise.CustomExercise -> {
            // Custom exercises could check notes or other fields if available
            exercise.notes?.let { notes ->
                if (notes.lowercase().contains(searchQuery)) {
                    score += 5
                }
            }
        }
    }
    
    return score
}