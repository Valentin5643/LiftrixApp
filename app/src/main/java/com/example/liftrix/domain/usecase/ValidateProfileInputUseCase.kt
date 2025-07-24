package com.example.liftrix.domain.usecase

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.validation.ProfileValidation
import javax.inject.Inject

/**
 * Validation result for real-time input validation.
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
    data class Loading(val message: String = "Validating...") : ValidationResult()
}

/**
 * Use case for real-time validation of profile input fields during onboarding.
 * Provides immediate feedback for UI validation without requiring complete profile objects.
 */
class ValidateProfileInputUseCase @Inject constructor() {

    /**
     * Validates age input string for real-time feedback.
     */
    fun validateAge(input: String): ValidationResult = ProfileValidation.validateAge(input)

    /**
     * Validates weight input with unit for real-time feedback.
     */
    fun validateWeight(value: String, unit: String): ValidationResult = 
        ProfileValidation.validateWeight(value, unit)

    /**
     * Validates equipment selection for real-time feedback.
     */
    fun validateEquipmentSelection(equipment: List<Equipment>): ValidationResult = 
        ProfileValidation.validateEquipment(equipment)

    /**
     * Validates other equipment description for real-time feedback.
     *
     * @param description The other equipment description text
     * @return ValidationResult indicating if description is valid or contains error message
     */
    fun validateOtherEquipment(description: String): ValidationResult {
        return when {
            description.isBlank() -> ValidationResult.Invalid("Description cannot be empty")
            description.length > UserProfile.MAX_OTHER_EQUIPMENT_LENGTH -> ValidationResult.Invalid(
                "Description cannot exceed ${UserProfile.MAX_OTHER_EQUIPMENT_LENGTH} characters"
            )
            else -> ValidationResult.Valid
        }
    }

    /**
     * Validates fitness goals selection for real-time feedback.
     */
    fun validateGoalSelection(goals: List<FitnessGoal>): ValidationResult = 
        ProfileValidation.validateGoals(goals)

    /**
     * Validates goal priority value for real-time feedback.
     *
     * @param priority The priority value (1-5)
     * @return ValidationResult indicating if priority is valid or contains error message
     */
    fun validateGoalPriority(priority: String): ValidationResult {
        return when {
            priority.isBlank() -> ValidationResult.Invalid("Priority is required")
            priority.toIntOrNull() == null -> ValidationResult.Invalid("Priority must be a valid number")
            else -> {
                val priorityValue = priority.toInt()
                when {
                    priorityValue < MIN_PRIORITY -> ValidationResult.Invalid(
                        "Priority must be at least $MIN_PRIORITY"
                    )
                    priorityValue > MAX_PRIORITY -> ValidationResult.Invalid(
                        "Priority cannot exceed $MAX_PRIORITY"
                    )
                    else -> ValidationResult.Valid
                }
            }
        }
    }

    /**
     * Validates goal priority mapping for real-time feedback.
     */
    fun validateGoalPriorityMapping(
        goals: List<FitnessGoal>,
        priorities: Map<FitnessGoal, Int>
    ): ValidationResult = ProfileValidation.validateGoalPriorities(goals, priorities)

    /**
     * Validates the overall profile completion status.
     *
     * @param age Age value
     * @param weight Weight value
     * @param equipment Equipment list
     * @param goals Goals list
     * @return ValidationResult indicating if profile is complete and valid
     */
    fun validateProfileCompletion(
        age: Int?,
        weight: Weight?,
        equipment: List<Equipment>,
        goals: List<FitnessGoal>
    ): ValidationResult {
        return when {
            age == null -> ValidationResult.Invalid("Age is required for profile completion")
            weight == null -> ValidationResult.Invalid("Weight is required for profile completion")
            equipment.isEmpty() -> ValidationResult.Invalid("At least one equipment type is required")
            goals.isEmpty() -> ValidationResult.Invalid("At least one fitness goal is required")
            else -> ValidationResult.Valid
        }
    }

    companion object {
        private const val MAX_EQUIPMENT_SELECTIONS = 10
        private const val MAX_GOAL_SELECTIONS = 7
        private const val MIN_PRIORITY = 1
        private const val MAX_PRIORITY = 5
    }
} 