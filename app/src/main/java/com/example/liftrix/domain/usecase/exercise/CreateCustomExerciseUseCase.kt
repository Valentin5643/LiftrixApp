package com.example.liftrix.domain.usecase.exercise

import android.net.Uri
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseType
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import javax.inject.Inject

/**
 * Input data for creating a custom exercise with enhanced fields
 */
data class CreateCustomExerciseInput(
    val name: String,
    val description: String? = null,
    val exerciseType: ExerciseType,
    val primaryMuscle: ExerciseCategory,
    val equipment: Equipment,
    val secondaryMuscles: Set<ExerciseCategory> = emptySet(),
    val difficulty: Int? = null,
    val instructions: List<String> = emptyList(),
    val mainImage: Uri? = null,
    val additionalImages: List<Uri> = emptyList(),
    val videoUrl: String? = null,
    val tags: List<String> = emptyList(),
    val categories: List<ExerciseCategory> = emptyList(),
    val notes: String? = null
) {
    init {
        require(name.isNotBlank()) { "Exercise name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Exercise name cannot exceed $MAX_NAME_LENGTH characters: ${name.length}" 
        }
        
        description?.let { desc ->
            require(desc.length <= MAX_DESCRIPTION_LENGTH) {
                "Description cannot exceed $MAX_DESCRIPTION_LENGTH characters: ${desc.length}"
            }
        }
        
        require(!secondaryMuscles.contains(primaryMuscle)) { 
            "Primary muscle cannot be included in secondary muscles" 
        }
        
        difficulty?.let { level ->
            require(level in MIN_DIFFICULTY..MAX_DIFFICULTY) { 
                "Difficulty must be between $MIN_DIFFICULTY and $MAX_DIFFICULTY: $level" 
            }
        }
        
        require(instructions.size <= MAX_INSTRUCTIONS) {
            "Cannot have more than $MAX_INSTRUCTIONS instructions: ${instructions.size}"
        }
        
        instructions.forEach { instruction ->
            require(instruction.length <= MAX_INSTRUCTION_LENGTH) {
                "Each instruction cannot exceed $MAX_INSTRUCTION_LENGTH characters: ${instruction.length}"
            }
        }
        
        require(additionalImages.size <= MAX_ADDITIONAL_IMAGES) {
            "Cannot have more than $MAX_ADDITIONAL_IMAGES additional images: ${additionalImages.size}"
        }
        
        require(tags.size <= MAX_TAGS) {
            "Cannot have more than $MAX_TAGS tags: ${tags.size}"
        }
        
        tags.forEach { tag ->
            require(tag.length <= MAX_TAG_LENGTH) {
                "Each tag cannot exceed $MAX_TAG_LENGTH characters: ${tag.length}"
            }
        }
        
        require(categories.size <= MAX_CATEGORIES) {
            "Cannot have more than $MAX_CATEGORIES categories: ${categories.size}"
        }
        
        notes?.let { note ->
            require(note.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${note.length}" 
            }
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        const val MAX_DESCRIPTION_LENGTH: Int = 300
        const val MAX_NOTES_LENGTH: Int = 500
        const val MAX_INSTRUCTION_LENGTH: Int = 200
        const val MAX_INSTRUCTIONS: Int = 10
        const val MIN_DIFFICULTY: Int = 1
        const val MAX_DIFFICULTY: Int = 10
        const val MAX_ADDITIONAL_IMAGES: Int = 5
        const val MAX_TAGS: Int = 10
        const val MAX_TAG_LENGTH: Int = 30
        const val MAX_CATEGORIES: Int = 5
        const val MAX_SECONDARY_MUSCLES: Int = 3
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
    data class ImageUploadError(val reason: String) : CreateCustomExerciseException("Image upload failed: $reason")
    data class RepositoryError(override val cause: Throwable) : CreateCustomExerciseException("Database error: ${cause.message}")
}

/**
 * Enhanced use case for creating custom exercises with validation, image upload support, and user-scoped storage.
 * Supports rich metadata including instructions, images, videos, tags, and categories.
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
            when (val validationResult = validateInput(input, userId.value)) {
                is ValidationResult.Success -> {
                    // Create the exercise with all enhanced fields
                    customExerciseRepository.createCustomExercise(
                        userId = userId.value,
                        name = input.name.trim(),
                        description = input.description?.trim()?.takeIf { it.isNotBlank() },
                        exerciseType = input.exerciseType,
                        primaryMuscle = input.primaryMuscle,
                        equipment = input.equipment,
                        secondaryMuscles = input.secondaryMuscles,
                        difficulty = input.difficulty,
                        instructions = input.instructions.map { it.trim() }.filter { it.isNotBlank() },
                        mainImage = input.mainImage,
                        additionalImages = input.additionalImages,
                        videoUrl = input.videoUrl?.trim()?.takeIf { it.isNotBlank() },
                        tags = input.tags.map { it.trim() }.filter { it.isNotBlank() },
                        categories = input.categories,
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
        if (input.secondaryMuscles.size > CreateCustomExerciseInput.MAX_SECONDARY_MUSCLES) {
            return ValidationResult.Error("Cannot have more than ${CreateCustomExerciseInput.MAX_SECONDARY_MUSCLES} secondary muscle groups")
        }
        
        // Check for inappropriate muscle combinations
        if (input.primaryMuscle == ExerciseCategory.CARDIO && input.secondaryMuscles.isNotEmpty()) {
            return ValidationResult.Error("Cardio exercises should not have secondary muscle groups")
        }
        
        // Validate exercise type and muscle group combinations
        when (input.exerciseType) {
            ExerciseType.CARDIO -> {
                if (input.primaryMuscle != ExerciseCategory.CARDIO) {
                    return ValidationResult.Error("Cardio exercise type must have CARDIO as primary muscle group")
                }
            }
            ExerciseType.BODYWEIGHT -> {
                if (input.equipment != Equipment.BODYWEIGHT_ONLY) {
                    return ValidationResult.Error("Bodyweight exercise type must use BODYWEIGHT_ONLY equipment")
                }
            }
            else -> { /* Other types are flexible */ }
        }
        
        // Validate video URL format if provided
        input.videoUrl?.let { url ->
            if (!isValidVideoUrl(url)) {
                return ValidationResult.Error("Invalid video URL format")
            }
        }
        
        // Validate tag uniqueness
        if (input.tags.distinct().size != input.tags.size) {
            return ValidationResult.Error("Duplicate tags are not allowed")
        }
        
        // Validate category uniqueness
        if (input.categories.distinct().size != input.categories.size) {
            return ValidationResult.Error("Duplicate categories are not allowed")
        }
        
        return ValidationResult.Success
    }
    
    /**
     * Validates video URL format
     */
    private fun isValidVideoUrl(url: String): Boolean {
        val trimmedUrl = url.trim()
        return trimmedUrl.startsWith("http://") || 
               trimmedUrl.startsWith("https://") ||
               trimmedUrl.contains("youtube.com") ||
               trimmedUrl.contains("youtu.be") ||
               trimmedUrl.contains("vimeo.com")
    }
    
    companion object {
        // MAX_SECONDARY_MUSCLES moved to CreateCustomExerciseInput companion object
    }
} 