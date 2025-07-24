package com.example.liftrix.domain.usecase.profile

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.service.ProfileCompletionCalculator
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Enhanced use case for saving user profile with comprehensive validation.
 * Includes bio validation, privacy controls, and profile completion calculation.
 */
class SaveUserProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val profileCompletionCalculator: ProfileCompletionCalculator
) {

    /**
     * Saves a user profile after validation and completion calculation.
     *
     * @param profile The UserProfile to validate and save
     * @return LiftrixResult<Unit> indicating success or failure with validation error details
     */
    suspend operator fun invoke(profile: UserProfile): LiftrixResult<Unit> {
        return try {
            // Validate user ID presence
            if (profile.userId.isBlank()) {
                val error = IllegalArgumentException("Profile must have a valid user ID")
                Timber.e("Attempted to save profile without user ID")
                return Result.failure(error)
            }

            // Validate display name
            if (profile.displayName.isBlank()) {
                val error = IllegalArgumentException("Display name is required")
                Timber.e("Attempted to save profile without display name")
                return Result.failure(error)
            }

            // Perform comprehensive validation
            val validationResult = validateProfile(profile)
            if (validationResult != null) {
                Timber.e("Profile validation failed for user ${profile.userId}: $validationResult")
                return Result.failure(IllegalArgumentException(validationResult))
            }

            // Calculate profile completion percentage
            val completionPercentage = profileCompletionCalculator.calculateCompletion(profile)
            
            // Update profile with completion percentage and timestamp
            val enhancedProfile = profile.copy(
                profileCompletionPercentage = completionPercentage,
                updatedAt = LocalDateTime.now(),
                completedAt = if (completionPercentage >= 100) LocalDateTime.now() else profile.completedAt
            )

            // Save profile through repository
            val result = profileRepository.saveUserProfile(enhancedProfile)
            
            result.fold(
                onSuccess = {
                    Timber.d("Profile saved successfully for user: ${profile.userId}, completion: $completionPercentage%")
                },
                onFailure = { error ->
                    Timber.e("Failed to save profile for user: ${profile.userId}, error: ${error.message}")
                }
            )
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Exception while saving profile for user: ${profile.userId}")
            Result.failure(e)
        }
    }

    /**
     * Validates all profile fields according to business rules.
     *
     * @param profile The profile to validate
     * @return Error message if validation fails, null if valid
     */
    private fun validateProfile(profile: UserProfile): String? {
        // Validate display name
        val displayNameValidation = validateDisplayName(profile.displayName)
        if (displayNameValidation != null) return displayNameValidation

        // Validate bio if provided
        val bioValidation = validateBio(profile.bio)
        if (bioValidation != null) return bioValidation

        // Validate age
        val ageValidation = validateAge(profile.age)
        if (ageValidation != null) return ageValidation

        // Validate weight
        val weightValidation = validateWeight(profile.weight)
        if (weightValidation != null) return weightValidation

        // Validate equipment selection
        val equipmentValidation = validateEquipment(profile.availableEquipment)
        if (equipmentValidation != null) return equipmentValidation

        // Validate other equipment description
        val otherEquipmentValidation = validateOtherEquipment(profile.otherEquipment)
        if (otherEquipmentValidation != null) return otherEquipmentValidation

        // Validate fitness goals
        val goalsValidation = validateGoals(profile.fitnessGoals)
        if (goalsValidation != null) return goalsValidation

        // Validate goal priorities
        val priorityValidation = validateGoalsPriority(profile.fitnessGoals, profile.goalsPriority)
        if (priorityValidation != null) return priorityValidation

        // Validate streak data consistency
        val streakValidation = validateStreakData(profile)
        if (streakValidation != null) return streakValidation

        return null // All validations passed
    }

    /**
     * Validates display name field.
     */
    private fun validateDisplayName(displayName: String): String? {
        return when {
            displayName.isBlank() -> "Display name is required"
            displayName.length > MAX_DISPLAY_NAME_LENGTH -> "Display name cannot exceed $MAX_DISPLAY_NAME_LENGTH characters"
            displayName.length < MIN_DISPLAY_NAME_LENGTH -> "Display name must be at least $MIN_DISPLAY_NAME_LENGTH characters"
            !displayName.matches(DISPLAY_NAME_REGEX) -> "Display name can only contain letters, numbers, spaces, and basic punctuation"
            else -> null
        }
    }

    /**
     * Validates bio field if provided.
     */
    private fun validateBio(bio: String?): String? {
        if (bio == null) return null // Bio is optional
        
        return when {
            bio.length > MAX_BIO_LENGTH -> "Bio cannot exceed $MAX_BIO_LENGTH characters"
            bio.isBlank() -> "Bio cannot be blank if provided"
            else -> null
        }
    }

    /**
     * Validates age field according to business rules.
     */
    private fun validateAge(age: Int?): String? {
        return when {
            age == null -> null // Age is optional for partial profiles
            age < UserProfile.MIN_AGE -> "Age must be at least ${UserProfile.MIN_AGE} years old"
            age > UserProfile.MAX_AGE -> "Age cannot exceed ${UserProfile.MAX_AGE} years"
            else -> null
        }
    }

    /**
     * Validates weight field and value constraints.
     */
    private fun validateWeight(weight: Weight?): String? {
        return when {
            weight == null -> null // Weight is optional for partial profiles
            weight.kilograms <= 0.0 -> "Weight must be a positive value"
            weight.kilograms > Weight.MAX_WEIGHT_KG -> "Weight cannot exceed ${Weight.MAX_WEIGHT_KG} kg"
            else -> null
        }
    }

    /**
     * Validates equipment selection for duplicates and reasonable limits.
     */
    private fun validateEquipment(equipment: List<Equipment>): String? {
        return when {
            equipment.distinct().size != equipment.size -> "Equipment list contains duplicates"
            equipment.size > MAX_EQUIPMENT_SELECTIONS -> "Cannot select more than $MAX_EQUIPMENT_SELECTIONS equipment types"
            else -> null
        }
    }

    /**
     * Validates other equipment description length and content.
     */
    private fun validateOtherEquipment(otherEquipment: String?): String? {
        return when {
            otherEquipment != null && otherEquipment.length > UserProfile.MAX_OTHER_EQUIPMENT_LENGTH -> {
                "Other equipment description cannot exceed ${UserProfile.MAX_OTHER_EQUIPMENT_LENGTH} characters"
            }
            otherEquipment != null && otherEquipment.isBlank() -> {
                "Other equipment description cannot be blank if provided"
            }
            else -> null
        }
    }

    /**
     * Validates fitness goals selection.
     */
    private fun validateGoals(goals: List<FitnessGoal>): String? {
        return when {
            goals.distinct().size != goals.size -> "Fitness goals list contains duplicates"
            goals.size > MAX_GOAL_SELECTIONS -> "Cannot select more than $MAX_GOAL_SELECTIONS fitness goals"
            else -> null
        }
    }

    /**
     * Validates goal priority mappings.
     */
    private fun validateGoalsPriority(goals: List<FitnessGoal>, priority: Map<FitnessGoal, Int>?): String? {
        if (priority == null) return null // Optional field

        return when {
            !priority.keys.containsAll(goals) -> {
                "Priority map must contain all selected fitness goals"
            }
            priority.values.any { it < MIN_PRIORITY || it > MAX_PRIORITY } -> {
                "Goal priorities must be between $MIN_PRIORITY and $MAX_PRIORITY"
            }
            priority.values.distinct().size != priority.values.size -> {
                "Goal priorities must be unique values"
            }
            else -> null
        }
    }

    /**
     * Validates streak data consistency.
     */
    private fun validateStreakData(profile: UserProfile): String? {
        return when {
            profile.currentStreak < 0 -> "Current streak cannot be negative"
            profile.longestStreak < 0 -> "Longest streak cannot be negative"
            profile.totalWorkouts < 0 -> "Total workouts cannot be negative"
            profile.currentStreak > profile.longestStreak -> "Current streak cannot exceed longest streak"
            profile.profileCompletionPercentage < 0 || profile.profileCompletionPercentage > 100 -> {
                "Profile completion percentage must be between 0 and 100"
            }
            else -> null
        }
    }

    companion object {
        private const val MAX_DISPLAY_NAME_LENGTH = 50
        private const val MIN_DISPLAY_NAME_LENGTH = 2
        private const val MAX_BIO_LENGTH = 500
        private const val MAX_EQUIPMENT_SELECTIONS = 10
        private const val MAX_GOAL_SELECTIONS = 7
        private const val MIN_PRIORITY = 1
        private const val MAX_PRIORITY = 5
        
        private val DISPLAY_NAME_REGEX = Regex("^[a-zA-Z0-9\\s._-]+$")
    }
}