package com.example.liftrix.domain.validation

import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.usecase.ValidationResult

/**
 * DSL for chainable validation with improved readability and reduced boilerplate
 */
class ValidationBuilder {
    internal val errors = mutableListOf<String>()
    
    fun String.isRequired(fieldName: String) = apply {
        if (isBlank()) errors.add("$fieldName is required")
    }
    
    fun String.maxLength(fieldName: String, maxLength: Int) = apply {
        if (length > maxLength) errors.add("$fieldName cannot exceed $maxLength characters")
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
        if (values.any { it < min || it > max }) errors.add("$fieldName values must be between $min and $max")
    }
    
    fun build(): ValidationResult = when {
        errors.isEmpty() -> ValidationResult.Valid
        else -> ValidationResult.Invalid(errors.joinToString("; "))
    }
}

/**
 * Extension function to create validation DSL
 */
inline fun validate(block: ValidationBuilder.() -> Unit): ValidationResult = 
    ValidationBuilder().apply(block).build()

/**
 * Profile validation extensions
 */
object ProfileValidation {
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
                } else weightValue
                
                weightInKg.maxValue("Weight", Weight.MAX_WEIGHT_KG)
            }
        
        unit.isRequired("Weight unit")
        if (unit.lowercase() !in listOf("kg", "lbs")) {
            errors.add("Weight unit must be kg or lbs")
        }
    }
    
    fun validateEquipment(equipment: List<Equipment>): ValidationResult = validate {
        equipment.notEmpty("Equipment")
            .maxSize("Equipment", 10)
            .unique("Equipment")
    }
    
    fun validateGoals(goals: List<FitnessGoal>): ValidationResult = validate {
        goals.notEmpty("Fitness goals")
            .maxSize("Fitness goals", 7)
            .unique("Fitness goals")
    }
    
    fun validateGoalPriorities(
        goals: List<FitnessGoal>, 
        priorities: Map<FitnessGoal, Int>
    ): ValidationResult = validate {
        goals.allMapped("Goal priorities", priorities)
        priorities.valueRange("Goal priorities", 1, 5)
            .uniqueValues("Goal priorities")
    }
}

/**
 * Workout validation extensions
 */
object WorkoutValidation {
    fun validateWorkoutBasics(
        userId: String, 
        name: String, 
        exerciseCount: Int, 
        notes: String? = null
    ): ValidationResult = validate {
        userId.isRequired("User ID")
        name.isRequired("Workout name")
            .maxLength("Workout name", Workout.MAX_NAME_LENGTH)
        
        if (exerciseCount <= 0) errors.add("Workout must have at least one exercise")
        if (exerciseCount > Workout.MAX_EXERCISES) {
            errors.add("Workout cannot have more than ${Workout.MAX_EXERCISES} exercises")
        }
        
        notes?.maxLength("Workout notes", Workout.MAX_NOTES_LENGTH)
    }
    
    fun validateExercise(
        exerciseIndex: Int,
        targetSets: Int? = null,
        targetReps: Int? = null,
        notes: String? = null
    ): ValidationResult = validate {
        val prefix = "Exercise ${exerciseIndex + 1}"
        
        targetSets?.let { sets ->
            sets.inRange("$prefix target sets", 1, Exercise.MAX_SETS)
        }
        
        targetReps?.let { reps ->
            if (reps <= 0) errors.add("$prefix target reps must be positive")
        }
        
        notes?.maxLength("$prefix notes", Exercise.MAX_NOTES_LENGTH)
    }
}

/**
 * Auth validation extensions
 */
object AuthValidation {
    fun validateEmail(email: String): ValidationResult = validate {
        email.isRequired("Email")
        if (!email.contains("@") || !email.contains(".")) {
            errors.add("Email must be a valid email address")
        }
    }
    
    fun validatePassword(password: String): ValidationResult = validate {
        password.isRequired("Password")
        if (password.length < 8) errors.add("Password must be at least 8 characters long")
        if (!password.any { it.isUpperCase() }) errors.add("Password must contain at least one uppercase letter")
        if (!password.any { it.isLowerCase() }) errors.add("Password must contain at least one lowercase letter")
        if (!password.any { it.isDigit() }) errors.add("Password must contain at least one digit")
    }
}

/**
 * Validation result extensions for better chaining
 */
fun ValidationResult.andThen(nextValidation: () -> ValidationResult): ValidationResult = when (this) {
    is ValidationResult.Valid -> nextValidation()
    else -> this
}

fun ValidationResult.orElse(fallbackValidation: () -> ValidationResult): ValidationResult = when (this) {
    is ValidationResult.Valid -> this
    else -> fallbackValidation()
}

/**
 * Validation result collection for multiple validations
 */
fun List<ValidationResult>.combine(): ValidationResult {
    val errors = filterIsInstance<ValidationResult.Invalid>()
        .map { it.message }
    
    return when {
        errors.isEmpty() -> ValidationResult.Valid
        else -> ValidationResult.Invalid(errors.joinToString("; "))
    }
}