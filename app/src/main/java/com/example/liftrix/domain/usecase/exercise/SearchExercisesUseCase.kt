package com.example.liftrix.domain.usecase.exercise

import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Data class representing a group of exercise variations
 */
data class ExerciseGroup(
    val movementPattern: String,
    val libraryVariations: List<ExerciseLibrary>,
    val customVariations: List<CustomExercise>
) {
    val allVariations: List<Any>
        get() = libraryVariations + customVariations
    
    val isEmpty: Boolean
        get() = libraryVariations.isEmpty() && customVariations.isEmpty()
}

/**
 * Combined exercise result that can be either library or custom exercise
 */
sealed class SearchableExercise {
    abstract val name: String
    abstract val equipment: Equipment
    abstract val movementPattern: String
    abstract fun calculateMatchScore(query: String): Double
    
    data class LibraryExercise(val exercise: ExerciseLibrary) : SearchableExercise() {
        override val name: String get() = exercise.name
        override val equipment: Equipment get() = exercise.equipment
        override val movementPattern: String get() = exercise.movementPattern
        override fun calculateMatchScore(query: String): Double = exercise.calculateMatchScore(query)
    }
    
    data class CustomExercise(val exercise: com.example.liftrix.domain.model.CustomExercise) : SearchableExercise() {
        override val name: String get() = exercise.name
        override val equipment: Equipment get() = exercise.equipment
        override val movementPattern: String get() = "custom"
        override fun calculateMatchScore(query: String): Double {
            val normalizedQuery = query.lowercase().trim()
            val normalizedName = exercise.name.lowercase()
            
            if (normalizedName == normalizedQuery) return 1.0
            if (normalizedName.startsWith(normalizedQuery)) return 0.9
            if (normalizedName.contains(normalizedQuery)) return 0.7
            
            return 0.0
        }
    }
}

/**
 * Use case for intelligent exercise search with variations and equipment filtering
 */
class SearchExercisesUseCase @Inject constructor(
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    private val customExerciseRepository: CustomExerciseRepository,
    private val authRepository: AuthRepository
) {
    
    /**
     * Search exercises with equipment filtering and variation grouping
     */
    suspend fun search(
        query: String,
        userEquipment: Set<Equipment> = Equipment.values().toSet()
    ): Flow<List<SearchableExercise>> {
        val userId = authRepository.getCurrentUserId()
        
        return if (userId != null) {
            combine(
                exerciseLibraryRepository.searchExercises(query),
                customExerciseRepository.searchCustomExercises(userId, query)
            ) { libraryExercises, customExercises ->
                val libraryResults = libraryExercises
                    .filter { userEquipment.contains(it.equipment) }
                    .map { SearchableExercise.LibraryExercise(it) }
                
                val customResults = customExercises
                    .filter { userEquipment.contains(it.equipment) }
                    .map { SearchableExercise.CustomExercise(it) }
                
                (libraryResults + customResults)
                    .filter { it.calculateMatchScore(query) > 0.0 || query.isBlank() }
                    .sortedWith(compareByDescending<SearchableExercise> { it.calculateMatchScore(query) }
                        .thenBy { it.name })
            }
        } else {
            exerciseLibraryRepository.searchExercises(query).map { libraryExercises ->
                libraryExercises
                    .filter { userEquipment.contains(it.equipment) }
                    .map { SearchableExercise.LibraryExercise(it) }
                    .filter { it.calculateMatchScore(query) > 0.0 || query.isBlank() }
                    .sortedWith(compareByDescending<SearchableExercise> { it.calculateMatchScore(query) }
                        .thenBy { it.name })
            }
        }
    }
    
    /**
     * Get exercise variations grouped by movement pattern
     */
    suspend fun getVariations(baseMovement: String, userEquipment: Set<Equipment> = Equipment.values().toSet()): Flow<List<ExerciseGroup>> {
        val userId = authRepository.getCurrentUserId()
        
        return if (userId != null) {
            combine(
                exerciseLibraryRepository.getVariationsByMovement(baseMovement, userEquipment),
                customExerciseRepository.getAllCustomExercises(userId)
            ) { libraryVariations, customExercises ->
                val customVariationsForMovement = customExercises.filter { 
                    it.notes?.contains(baseMovement, ignoreCase = true) == true &&
                    userEquipment.contains(it.equipment)
                }
                
                if (libraryVariations.isNotEmpty() || customVariationsForMovement.isNotEmpty()) {
                    listOf(ExerciseGroup(
                        movementPattern = baseMovement,
                        libraryVariations = libraryVariations,
                        customVariations = customVariationsForMovement
                    ))
                } else {
                    emptyList()
                }
            }
        } else {
            exerciseLibraryRepository.getVariationsByMovement(baseMovement, userEquipment).map { variations ->
                if (variations.isNotEmpty()) {
                    listOf(ExerciseGroup(
                        movementPattern = baseMovement,
                        libraryVariations = variations,
                        customVariations = emptyList()
                    ))
                } else {
                    emptyList()
                }
            }
        }
    }
    
    /**
     * Search with grouped variations for specific query
     */
    suspend fun searchWithVariations(
        query: String,
        userEquipment: Set<Equipment> = Equipment.values().toSet()
    ): Flow<List<ExerciseGroup>> {
        val userId = authRepository.getCurrentUserId()
        
        return if (userId != null) {
            combine(
                exerciseLibraryRepository.searchExercises(query),
                customExerciseRepository.searchCustomExercises(userId, query)
            ) { libraryExercises, customExercises ->
                val filteredLibrary = libraryExercises.filter { userEquipment.contains(it.equipment) }
                val filteredCustom = customExercises.filter { userEquipment.contains(it.equipment) }
                
                val groupedByMovement = filteredLibrary.groupBy { it.movementPattern }
                
                groupedByMovement.map { (movementPattern, exercises) ->
                    val customForPattern = filteredCustom.filter { 
                        it.notes?.contains(movementPattern, ignoreCase = true) == true 
                    }
                    
                    ExerciseGroup(
                        movementPattern = movementPattern,
                        libraryVariations = exercises.sortedBy { it.difficultyLevel },
                        customVariations = customForPattern
                    )
                }.filter { !it.isEmpty }
                 .sortedByDescending { group ->
                    group.libraryVariations.maxOfOrNull { it.calculateMatchScore(query) } ?: 0.0
                }
            }
        } else {
            exerciseLibraryRepository.searchExercises(query).map { libraryExercises ->
                val filteredLibrary = libraryExercises.filter { userEquipment.contains(it.equipment) }
                val groupedByMovement = filteredLibrary.groupBy { it.movementPattern }
                
                groupedByMovement.map { (movementPattern, exercises) ->
                    ExerciseGroup(
                        movementPattern = movementPattern,
                        libraryVariations = exercises.sortedBy { it.difficultyLevel },
                        customVariations = emptyList()
                    )
                }.filter { !it.isEmpty }
                 .sortedByDescending { group ->
                    group.libraryVariations.maxOfOrNull { it.calculateMatchScore(query) } ?: 0.0
                }
            }
        }
    }
    
    /**
     * Calculate fuzzy match distance between query and exercise name
     */
    private fun fuzzyDistance(query: String, exerciseName: String): Double {
        val normalizedQuery = query.lowercase().trim()
        val normalizedName = exerciseName.lowercase()
        
        if (normalizedQuery.isEmpty()) return 0.0
        if (normalizedName.isEmpty()) return 1.0
        
        // Simple Levenshtein distance approximation
        val distance = levenshteinDistance(normalizedQuery, normalizedName)
        val maxLength = maxOf(normalizedQuery.length, normalizedName.length)
        
        return distance.toDouble() / maxLength
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(str1: String, str2: String): Int {
        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }
        
        for (i in 0..str1.length) {
            dp[i][0] = i
        }
        
        for (j in 0..str2.length) {
            dp[0][j] = j
        }
        
        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                if (str1[i - 1] == str2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    dp[i][j] = 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        
        return dp[str1.length][str2.length]
    }
} 