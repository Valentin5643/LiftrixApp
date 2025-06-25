package com.example.liftrix.domain.usecase

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.Weight
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
     *
     * @param input The age input string from UI
     * @return ValidationResult indicating if input is valid or contains error message
     */
    fun validateAge(input: String): ValidationResult {
        return when {
            input.isBlank() -> ValidationResult.Invalid("Age is required")
            input.toIntOrNull() == null -> ValidationResult.Invalid("Age must be a valid number")
            else -> {
                val age = input.toInt()
                when {
                    age < UserProfile.MIN_AGE -> ValidationResult.Invalid(
                        "Age must be at least ${UserProfile.MIN_AGE} years old"
                    )
                    age > UserProfile.MAX_AGE -> ValidationResult.Invalid(
                        "Age cannot exceed ${UserProfile.MAX_AGE} years"
                    )
                    else -> ValidationResult.Valid
                }
            }
        }
    }

    /**
     * Validates weight input with unit for real-time feedback.
     *
     * @param value The weight value string from UI
     * @param unit The weight unit ("kg" or "lbs")
     * @return ValidationResult indicating if weight is valid or contains error message
     */
    fun validateWeight(value: String, unit: String): ValidationResult {
        return when {
            value.isBlank() -> ValidationResult.Invalid("Weight is required")
            value.toDoubleOrNull() == null -> ValidationResult.Invalid("Weight must be a valid number")
            unit.isBlank() -> ValidationResult.Invalid("Weight unit is required")
            unit.lowercase() !in listOf("kg", "lbs") -> ValidationResult.Invalid("Weight unit must be kg or lbs")
            else -> {
                val weightValue = value.toDouble()
                when {
                    weightValue <= 0.0 -> ValidationResult.Invalid("Weight must be a positive value")
                    else -> {
                        // Convert to kilograms for validation
                        val weightInKg = if (unit.lowercase() == "lbs") {
                            weightValue * 0.453592
                        } else {
                            weightValue
                        }
                        
                        when {
                            weightInKg > Weight.MAX_WEIGHT_KG -> ValidationResult.Invalid(
                                "Weight cannot exceed ${Weight.MAX_WEIGHT_KG} kg (${(Weight.MAX_WEIGHT_KG / 0.453592).toInt()} lbs)"
                            )
                            else -> ValidationResult.Valid
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates equipment selection for real-time feedback.
     *
     * @param equipment List of selected equipment
     * @return ValidationResult indicating if selection is valid or contains error message
     */
    fun validateEquipmentSelection(equipment: List<Equipment>): ValidationResult {
        return when {
            equipment.isEmpty() -> ValidationResult.Invalid("At least one equipment type must be selected")
            equipment.distinct().size != equipment.size -> ValidationResult.Invalid(
                "Equipment list contains duplicates"
            )
            equipment.size > MAX_EQUIPMENT_SELECTIONS -> ValidationResult.Invalid(
                "Cannot select more than $MAX_EQUIPMENT_SELECTIONS equipment types"
            )
            else -> ValidationResult.Valid
        }
    }

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
     *
     * @param goals List of selected fitness goals
     * @return ValidationResult indicating if selection is valid or contains error message
     */
    fun validateGoalSelection(goals: List<FitnessGoal>): ValidationResult {
        return when {
            goals.isEmpty() -> ValidationResult.Invalid("At least one fitness goal must be selected")
            goals.distinct().size != goals.size -> ValidationResult.Invalid(
                "Goals list contains duplicates"
            )
            goals.size > MAX_GOAL_SELECTIONS -> ValidationResult.Invalid(
                "Cannot select more than $MAX_GOAL_SELECTIONS fitness goals"
            )
            else -> ValidationResult.Valid
        }
    }

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
     *
     * @param goals Selected fitness goals
     * @param priorities Map of goal to priority values
     * @return ValidationResult indicating if priority mapping is valid or contains error message
     */
    fun validateGoalPriorityMapping(
        goals: List<FitnessGoal>,
        priorities: Map<FitnessGoal, Int>
    ): ValidationResult {
        return when {
            !priorities.keys.containsAll(goals) -> ValidationResult.Invalid(
                "Priority must be set for all selected goals"
            )
            priorities.values.any { it < MIN_PRIORITY || it > MAX_PRIORITY } -> ValidationResult.Invalid(
                "All priorities must be between $MIN_PRIORITY and $MAX_PRIORITY"
            )
            priorities.values.distinct().size != priorities.values.size -> ValidationResult.Invalid(
                "All priorities must be unique values"
            )
            else -> ValidationResult.Valid
        }
    }

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