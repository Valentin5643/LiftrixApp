package com.example.liftrix.domain.usecase.exercise

import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import javax.inject.Inject

/**
 * Input data for creating a custom exercise
 */
data class CreateCustomExerciseInput(
    val name: String,
    val primaryMuscle: ExerciseCategory,
    val equipment: Equipment,
    val secondaryMuscles: Set<ExerciseCategory> = emptySet(),
    val difficulty: Int? = null,
    val notes: String? = null
) {
    init {
        require(name.isNotBlank()) { "Exercise name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Exercise name cannot exceed $MAX_NAME_LENGTH characters: ${name.length}" 
        }
        require(!secondaryMuscles.contains(primaryMuscle)) { 
            "Primary muscle cannot be included in secondary muscles" 
        }
        difficulty?.let { level ->
            require(level in MIN_DIFFICULTY..MAX_DIFFICULTY) { 
                "Difficulty must be between $MIN_DIFFICULTY and $MAX_DIFFICULTY: $level" 
            }
        }
        notes?.let { note ->
            require(note.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${note.length}" 
            }
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        const val MAX_NOTES_LENGTH: Int = 500
        const val MIN_DIFFICULTY: Int = 1
        const val MAX_DIFFICULTY: Int = 10
    }
}

/**
 * Validation result for create custom exercise input
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

/**
 * Custom exceptions for exercise creation
 */
sealed class CreateCustomExerciseException(message: String) : Exception(message) {
    object UserNotAuthenticated : CreateCustomExerciseException("User must be authenticated to create custom exercises")
    data class DuplicateName(val name: String) : CreateCustomExerciseException("Exercise name '$name' already exists")
    data class InvalidInput(val field: String, val reason: String) : CreateCustomExerciseException("Invalid $field: $reason")
    data class RepositoryError(override val cause: Throwable) : CreateCustomExerciseException("Database error: ${cause.message}")
}

/**
 * Use case for creating custom exercises with validation and user-scoped storage
 */
class CreateCustomExerciseUseCase @Inject constructor(
    private val customExerciseRepository: CustomExerciseRepository,
    private val authRepository: AuthRepository
) {
    
    /**
     * Creates a custom exercise for the authenticated user
     */
    suspend operator fun invoke(input: CreateCustomExerciseInput): Result<CustomExercise> {
        return try {
            // Get authenticated user
            val userId = authRepository.getCurrentUserId()
                ?: return Result.failure(CreateCustomExerciseException.UserNotAuthenticated)
            
            // Validate input
            when (val validationResult = validateInput(input, userId)) {
                is ValidationResult.Success -> {
                    // Create the exercise
                    customExerciseRepository.createCustomExercise(
                        userId = userId,
                        name = input.name.trim(),
                        primaryMuscle = input.primaryMuscle,
                        equipment = input.equipment,
                        secondaryMuscles = input.secondaryMuscles,
                        difficulty = input.difficulty,
                        notes = input.notes?.trim()?.takeIf { it.isNotBlank() }
                    ).fold(
                        onSuccess = { Result.success(it) },
                        onFailure = { Result.failure(CreateCustomExerciseException.RepositoryError(it)) }
                    )
                }
                is ValidationResult.Error -> {
                    Result.failure(CreateCustomExerciseException.InvalidInput("input", validationResult.message))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Validates the input for creating a custom exercise
     */
    private suspend fun validateInput(input: CreateCustomExerciseInput, userId: String): ValidationResult {
        // Check name uniqueness
        val isNameUnique = customExerciseRepository.isExerciseNameUnique(userId, input.name.trim())
        if (!isNameUnique) {
            return ValidationResult.Error("Exercise name '${input.name}' already exists")
        }
        
        // Additional business rules validation
        if (input.secondaryMuscles.size > MAX_SECONDARY_MUSCLES) {
            return ValidationResult.Error("Cannot have more than $MAX_SECONDARY_MUSCLES secondary muscle groups")
        }
        
        // Check for inappropriate muscle combinations
        if (input.primaryMuscle == ExerciseCategory.CARDIO && input.secondaryMuscles.isNotEmpty()) {
            return ValidationResult.Error("Cardio exercises should not have secondary muscle groups")
        }
        
        return ValidationResult.Success
    }
    
    companion object {
        private const val MAX_SECONDARY_MUSCLES = 3
    }
} 