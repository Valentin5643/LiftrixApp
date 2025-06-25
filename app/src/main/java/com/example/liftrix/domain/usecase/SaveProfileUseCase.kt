package com.example.liftrix.domain.usecase

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.repository.ProfileRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for saving user profile with comprehensive validation.
 * Enforces business rules and data integrity before persisting to repository.
 */
class SaveProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {

    /**
     * Saves a user profile after validating all input data.
     *
     * @param profile The UserProfile to validate and save
     * @return Result<Unit> indicating success or failure with validation error details
     */
    suspend operator fun invoke(profile: UserProfile): Result<Unit> {
        return try {
            // Validate user ID presence
            if (profile.userId.isBlank()) {
                val error = IllegalArgumentException("Profile must have a valid user ID")
                Timber.e("Attempted to save profile without user ID")
                return Result.failure(error)
            }

            // Perform comprehensive validation
            val validationResult = validateProfile(profile)
            if (validationResult != null) {
                Timber.e("Profile validation failed for user ${profile.userId}: $validationResult")
                return Result.failure(IllegalArgumentException(validationResult))
            }

            // Save profile through repository
            val result = profileRepository.saveProfile(profile)
            
            if (result.isSuccess) {
                Timber.d("Profile saved successfully for user: ${profile.userId}")
            } else {
                Timber.e("Failed to save profile for user: ${profile.userId}")
            }
            
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

        return null // All validations passed
    }

    /**
     * Validates age field according to business rules.
     */
    private fun validateAge(age: Int?): String? {
        return when {
            age == null -> "Age is required for profile completion"
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
            weight == null -> "Weight is required for profile completion"
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
            equipment.isEmpty() -> "At least one equipment type must be selected"
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
            goals.isEmpty() -> "At least one fitness goal must be selected"
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

    companion object {
        private const val MAX_EQUIPMENT_SELECTIONS = 10
        private const val MAX_GOAL_SELECTIONS = 7
        private const val MIN_PRIORITY = 1
        private const val MAX_PRIORITY = 5
    }
} 