package com.example.liftrix.domain.usecase.exercise

import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseGroup
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for getting exercise variations based on movement patterns and equipment
 */
class GetExerciseVariationsUseCase @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val customExerciseRepository: CustomExerciseRepository,
    private val authRepository: AuthRepository
) {
    
    /**
     * Get variations for a specific exercise by movement pattern
     */
    suspend fun getVariations(
        exerciseId: String,
        userEquipment: Set<Equipment> = Equipment.entries.toSet()
    ): Flow<ExerciseGroup> {
        val userId = authRepository.getCurrentUserId()
        
        return if (userId != null) {
            combine(
                exerciseRepository.getAllExercisesFlow(),
                customExerciseRepository.getAllCustomExercises(userId)
            ) { libraryExercises, customExercises ->
                val baseExercise = libraryExercises.find { it.id == exerciseId }
                
                if (baseExercise != null) {
                    val variations = libraryExercises
                        .filter { 
                            it.movementPattern == baseExercise.movementPattern && 
                            userEquipment.contains(it.equipment) 
                        }
                        .sortedBy { it.difficultyLevel }
                    
                    val customVariations = customExercises.filter { 
                        it.notes?.contains(baseExercise.movementPattern, ignoreCase = true) == true &&
                        userEquipment.contains(it.equipment)
                    }
                    
                    ExerciseGroup(
                        movementPattern = baseExercise.movementPattern,
                        libraryVariations = variations,
                        customVariations = customVariations
                    )
                } else {
                    ExerciseGroup(
                        movementPattern = "unknown",
                        libraryVariations = emptyList(),
                        customVariations = emptyList()
                    )
                }
            }
        } else {
            exerciseRepository.getAllExercisesFlow().map { libraryExercises ->
                val baseExercise = libraryExercises.find { it.id == exerciseId }
                
                if (baseExercise != null) {
                    val variations = libraryExercises
                        .filter { 
                            it.movementPattern == baseExercise.movementPattern && 
                            userEquipment.contains(it.equipment) 
                        }
                        .sortedBy { it.difficultyLevel }
                    
                    ExerciseGroup(
                        movementPattern = baseExercise.movementPattern,
                        libraryVariations = variations,
                        customVariations = emptyList()
                    )
                } else {
                    ExerciseGroup(
                        movementPattern = "unknown",
                        libraryVariations = emptyList(),
                        customVariations = emptyList()
                    )
                }
            }
        }
    }
    
    /**
     * Get variations by movement pattern name
     */
    suspend fun getVariationsByMovement(
        movementPattern: String,
        userEquipment: Set<Equipment> = Equipment.entries.toSet()
    ): Flow<ExerciseGroup> {
        val userId = authRepository.getCurrentUserId()
        
        return if (userId != null) {
            combine(
                exerciseRepository.getVariationsByMovement(movementPattern, userEquipment),
                customExerciseRepository.getAllCustomExercises(userId)
            ) { libraryVariations, customExercises ->
                val customVariations = customExercises.filter { 
                    it.notes?.contains(movementPattern, ignoreCase = true) == true &&
                    userEquipment.contains(it.equipment)
                }
                
                ExerciseGroup(
                    movementPattern = movementPattern,
                    libraryVariations = libraryVariations,
                    customVariations = customVariations
                )
            }
        } else {
            exerciseRepository.getVariationsByMovement(movementPattern, userEquipment).map { variations ->
                ExerciseGroup(
                    movementPattern = movementPattern,
                    libraryVariations = variations,
                    customVariations = emptyList()
                )
            }
        }
    }
    
    /**
     * Get all available movement patterns
     */
    fun getAvailableMovementPatterns(): Flow<List<String>> {
        return exerciseRepository.getAllExercisesFlow().map { exercises ->
            exercises.map { it.movementPattern }.distinct().sorted()
        }
    }
    
    /**
     * Get variations filtered by difficulty
     */
    suspend fun getVariationsByDifficulty(
        movementPattern: String,
        maxDifficulty: Int,
        userEquipment: Set<Equipment> = Equipment.entries.toSet()
    ): Flow<ExerciseGroup> {
        val userId = authRepository.getCurrentUserId()
        
        return if (userId != null) {
            combine(
                exerciseRepository.getFilteredExercises(
                    muscleGroup = null,
                    equipment = null,
                    isCompound = null,
                    maxDifficulty = maxDifficulty
                ),
                customExerciseRepository.getAllCustomExercises(userId)
            ) { libraryExercises, customExercises ->
                val filteredLibrary = libraryExercises
                    .filter { 
                        it.movementPattern == movementPattern && 
                        userEquipment.contains(it.equipment) 
                    }
                    .sortedBy { it.difficultyLevel }
                
                val customVariations = customExercises.filter { 
                    it.notes?.contains(movementPattern, ignoreCase = true) == true &&
                    userEquipment.contains(it.equipment) &&
                    (it.difficulty ?: 1) <= maxDifficulty
                }
                
                ExerciseGroup(
                    movementPattern = movementPattern,
                    libraryVariations = filteredLibrary,
                    customVariations = customVariations
                )
            }
        } else {
            exerciseRepository.getFilteredExercises(
                muscleGroup = null,
                equipment = null,
                isCompound = null,
                maxDifficulty = maxDifficulty
            ).map { libraryExercises ->
                val filteredLibrary = libraryExercises
                    .filter { 
                        it.movementPattern == movementPattern && 
                        userEquipment.contains(it.equipment) 
                    }
                    .sortedBy { it.difficultyLevel }
                
                ExerciseGroup(
                    movementPattern = movementPattern,
                    libraryVariations = filteredLibrary,
                    customVariations = emptyList()
                )
            }
        }
    }
} 