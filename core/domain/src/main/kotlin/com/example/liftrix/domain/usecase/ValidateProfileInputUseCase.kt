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
 */
class ValidateProfileInputUseCase @Inject constructor() {
    fun validateAge(input: String): ValidationResult = validate {
        input.isRequired("Age")
            .asInt("Age") { age ->
                age.inRange("Age", UserProfile.MIN_AGE, UserProfile.MAX_AGE)
            }
    }

    fun validateWeight(value: String, unit: String): ValidationResult = validate {
        value.isRequired("Weight")
            .asDouble("Weight") { weightValue ->
                weightValue.positive("Weight")

                val weightInKg = if (unit.lowercase() == "lbs") {
                    weightValue * 0.453592
                } else {
                    weightValue
                }

                weightInKg.maxValue("Weight", Weight.MAX_WEIGHT_KG)
            }

        unit.isRequired("Weight unit")
        if (unit.lowercase() !in listOf("kg", "lbs")) {
            errors.add("Weight unit must be kg or lbs")
        }
    }

    fun validateEquipmentSelection(equipment: List<Equipment>): ValidationResult = validate {
        equipment.notEmpty("Equipment")
            .maxSize("Equipment", MAX_EQUIPMENT_SELECTIONS)
            .unique("Equipment")
    }

    fun validateOtherEquipment(description: String): ValidationResult {
        return when {
            description.isBlank() -> ValidationResult.Invalid("Description cannot be empty")
            description.length > UserProfile.MAX_OTHER_EQUIPMENT_LENGTH -> ValidationResult.Invalid(
                "Description cannot exceed ${UserProfile.MAX_OTHER_EQUIPMENT_LENGTH} characters"
            )
            else -> ValidationResult.Valid
        }
    }

    fun validateGoalSelection(goals: List<FitnessGoal>): ValidationResult = validate {
        goals.notEmpty("Fitness goals")
            .maxSize("Fitness goals", MAX_GOAL_SELECTIONS)
            .unique("Fitness goals")
    }

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

    fun validateGoalPriorityMapping(
        goals: List<FitnessGoal>,
        priorities: Map<FitnessGoal, Int>
    ): ValidationResult = validate {
        goals.allMapped("Goal priorities", priorities)
        priorities.valueRange("Goal priorities", MIN_PRIORITY, MAX_PRIORITY)
            .uniqueValues("Goal priorities")
    }

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

    private class ValidationBuilder {
        val errors = mutableListOf<String>()

        fun String.isRequired(fieldName: String) = apply {
            if (isBlank()) errors.add("$fieldName is required")
        }

        fun String.asInt(fieldName: String, block: (Int) -> Unit = {}) = apply {
            toIntOrNull()?.let(block) ?: errors.add("$fieldName must be a valid number")
        }

        fun String.asDouble(fieldName: String, block: (Double) -> Unit = {}) = apply {
            toDoubleOrNull()?.let(block) ?: errors.add("$fieldName must be a valid number")
        }

        fun Int.inRange(fieldName: String, min: Int, max: Int) = apply {
            if (this < min || this > max) errors.add("$fieldName must be between $min and $max")
        }

        fun Double.positive(fieldName: String) = apply {
            if (this <= 0.0) errors.add("$fieldName must be a positive value")
        }

        fun Double.maxValue(fieldName: String, max: Double) = apply {
            if (this > max) errors.add("$fieldName cannot exceed $max")
        }

        fun <T> List<T>.notEmpty(fieldName: String) = apply {
            if (isEmpty()) errors.add("$fieldName must have at least one item")
        }

        fun <T> List<T>.maxSize(fieldName: String, maxSize: Int) = apply {
            if (size > maxSize) errors.add("$fieldName cannot have more than $maxSize items")
        }

        fun <T> List<T>.unique(fieldName: String) = apply {
            if (distinct().size != size) errors.add("$fieldName contains duplicates")
        }

        fun <T> List<T>.allMapped(fieldName: String, mapping: Map<T, *>) = apply {
            if (!mapping.keys.containsAll(this)) errors.add("$fieldName must be mapped for all items")
        }

        fun <T> Map<T, Int>.uniqueValues(fieldName: String) = apply {
            if (values.distinct().size != values.size) errors.add("$fieldName must have unique values")
        }

        fun <T> Map<T, Int>.valueRange(fieldName: String, min: Int, max: Int) = apply {
            if (values.any { it < min || it > max }) {
                errors.add("$fieldName values must be between $min and $max")
            }
        }

        fun build(): ValidationResult = when {
            errors.isEmpty() -> ValidationResult.Valid
            else -> ValidationResult.Invalid(errors.joinToString("; "))
        }
    }

    private fun validate(block: ValidationBuilder.() -> Unit): ValidationResult =
        ValidationBuilder().apply(block).build()

    companion object {
        private const val MAX_EQUIPMENT_SELECTIONS = 10
        private const val MAX_GOAL_SELECTIONS = 7
        private const val MIN_PRIORITY = 1
        private const val MAX_PRIORITY = 5
    }
}
