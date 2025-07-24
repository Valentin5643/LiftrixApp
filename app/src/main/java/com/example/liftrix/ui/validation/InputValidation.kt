package com.example.liftrix.ui.validation

import com.example.liftrix.domain.model.validation.ValidationResult
import java.time.Duration
import kotlin.math.abs

/**
 * InputValidation - Comprehensive validation utility for all Liftrix form inputs
 * 
 * Provides user-friendly validation rules with clear, actionable error messages
 * following Liftrix design patterns and user experience guidelines.
 * 
 * All validation functions return ValidationResult for consistent error handling
 * and integration with the validation UI components.
 */
object InputValidation {
    
    // Validation constants
    private const val MIN_WORKOUT_NAME_LENGTH = 2
    private const val MAX_WORKOUT_NAME_LENGTH = 50
    private const val MAX_DESCRIPTION_LENGTH = 500
    private const val MAX_WEIGHT_LBS = 2000.0
    private const val MAX_REPS = 1000
    private const val MIN_REPS = 1
    private const val MAX_NOTES_LENGTH = 1000
    private const val MIN_DURATION_MINUTES = 1
    private const val MAX_DURATION_HOURS = 12
    
    /**
     * Validate workout name
     * Required field with length constraints and character validation
     */
    fun validateWorkoutName(name: String): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult.error(
                message = "Workout name is required",
                field = "workoutName"
            )
            name.trim().length < MIN_WORKOUT_NAME_LENGTH -> ValidationResult.error(
                message = "Name must be at least $MIN_WORKOUT_NAME_LENGTH characters",
                field = "workoutName"
            )
            name.trim().length > MAX_WORKOUT_NAME_LENGTH -> ValidationResult.error(
                message = "Name must be less than $MAX_WORKOUT_NAME_LENGTH characters",
                field = "workoutName"
            )
            name.trim() != name -> ValidationResult.error(
                message = "Name cannot start or end with spaces",
                field = "workoutName"
            )
            !isValidWorkoutNameCharacters(name.trim()) -> ValidationResult.error(
                message = "Name can only contain letters, numbers, spaces, and basic punctuation",
                field = "workoutName"
            )
            else -> ValidationResult.success()
        }
    }
    
    /**
     * Validate workout description (optional field)
     */
    fun validateWorkoutDescription(description: String): ValidationResult {
        return when {
            description.length > MAX_DESCRIPTION_LENGTH -> ValidationResult.error(
                message = "Description must be less than $MAX_DESCRIPTION_LENGTH characters",
                field = "workoutDescription"
            )
            else -> ValidationResult.success()
        }
    }
    
    /**
     * Validate weight input
     * Optional field with positive numeric validation and realistic limits
     */
    fun validateWeight(weight: String): ValidationResult {
        // Empty weight is allowed (optional field)
        if (weight.isBlank()) {
            return ValidationResult.success()
        }
        
        val trimmedWeight = weight.trim()
        
        return when {
            !isValidNumericInput(trimmedWeight) -> ValidationResult.error(
                message = "Please enter a valid weight (numbers only)",
                field = "weight"
            )
            else -> {
                val weightValue = trimmedWeight.toDoubleOrNull()
                when {
                    weightValue == null -> ValidationResult.error(
                        message = "Please enter a valid weight",
                        field = "weight"
                    )
                    weightValue < 0 -> ValidationResult.error(
                        message = "Weight cannot be negative",
                        field = "weight"
                    )
                    weightValue == 0.0 -> ValidationResult.error(
                        message = "Weight must be greater than zero",
                        field = "weight"
                    )
                    weightValue > MAX_WEIGHT_LBS -> ValidationResult.error(
                        message = "Weight seems unrealistic (maximum $MAX_WEIGHT_LBS lbs)",
                        field = "weight"
                    )
                    hasExcessiveDecimalPlaces(weightValue) -> ValidationResult.error(
                        message = "Weight can have at most 2 decimal places",
                        field = "weight"
                    )
                    else -> ValidationResult.success()
                }
            }
        }
    }
    
    /**
     * Validate reps input
     * Required field with positive integer validation and realistic limits
     */
    fun validateReps(reps: String): ValidationResult {
        return when {
            reps.isBlank() -> ValidationResult.error(
                message = "Number of reps is required",
                field = "reps"
            )
            !isValidIntegerInput(reps.trim()) -> ValidationResult.error(
                message = "Please enter a valid number of reps",
                field = "reps"
            )
            else -> {
                val repsValue = reps.trim().toIntOrNull()
                when {
                    repsValue == null -> ValidationResult.error(
                        message = "Please enter a valid number",
                        field = "reps"
                    )
                    repsValue < MIN_REPS -> ValidationResult.error(
                        message = "Reps must be at least $MIN_REPS",
                        field = "reps"
                    )
                    repsValue > MAX_REPS -> ValidationResult.error(
                        message = "Reps seems unrealistic (maximum $MAX_REPS)",
                        field = "reps"
                    )
                    else -> ValidationResult.success()
                }
            }
        }
    }
    
    /**
     * Validate session notes (optional field)
     */
    fun validateSessionNotes(notes: String): ValidationResult {
        return when {
            notes.length > MAX_NOTES_LENGTH -> ValidationResult.error(
                message = "Notes must be less than $MAX_NOTES_LENGTH characters",
                field = "sessionNotes"
            )
            else -> ValidationResult.success()
        }
    }
    
    /**
     * Validate workout duration
     * Optional field with realistic time constraints
     */
    fun validateDuration(durationMinutes: String): ValidationResult {
        if (durationMinutes.isBlank()) {
            return ValidationResult.success() // Duration is optional
        }
        
        val trimmedDuration = durationMinutes.trim()
        
        return when {
            !isValidIntegerInput(trimmedDuration) -> ValidationResult.error(
                message = "Please enter duration in minutes (numbers only)",
                field = "duration"
            )
            else -> {
                val minutes = trimmedDuration.toIntOrNull()
                when {
                    minutes == null -> ValidationResult.error(
                        message = "Please enter a valid duration",
                        field = "duration"
                    )
                    minutes < MIN_DURATION_MINUTES -> ValidationResult.error(
                        message = "Duration must be at least $MIN_DURATION_MINUTES minute",
                        field = "duration"
                    )
                    minutes > (MAX_DURATION_HOURS * 60) -> ValidationResult.error(
                        message = "Duration seems unrealistic (maximum $MAX_DURATION_HOURS hours)",
                        field = "duration"
                    )
                    else -> ValidationResult.success()
                }
            }
        }
    }
    
    /**
     * Validate duration object
     */
    fun validateDuration(duration: Duration?): ValidationResult {
        duration ?: return ValidationResult.success()
        
        val hours = duration.toHours()
        val minutes = duration.toMinutes()
        
        return when {
            minutes < MIN_DURATION_MINUTES -> ValidationResult.error(
                message = "Duration must be at least $MIN_DURATION_MINUTES minute",
                field = "duration"
            )
            hours > MAX_DURATION_HOURS -> ValidationResult.error(
                message = "Duration seems unrealistic (maximum $MAX_DURATION_HOURS hours)",
                field = "duration"
            )
            else -> ValidationResult.success()
        }
    }
    
    /**
     * Validate required text field
     * Generic validation for any required text input
     */
    fun validateRequired(value: String, fieldName: String, displayName: String): ValidationResult {
        return ValidationResult.fromCondition(
            condition = value.isNotBlank(),
            errorMessage = "$displayName is required",
            field = fieldName
        )
    }
    
    /**
     * Validate text length constraints
     */
    fun validateLength(
        value: String,
        minLength: Int? = null,
        maxLength: Int? = null,
        fieldName: String,
        displayName: String
    ): ValidationResult {
        val length = value.trim().length
        
        return when {
            minLength != null && length < minLength -> ValidationResult.error(
                message = "$displayName must be at least $minLength characters",
                field = fieldName
            )
            maxLength != null && length > maxLength -> ValidationResult.error(
                message = "$displayName must be less than $maxLength characters", 
                field = fieldName
            )
            else -> ValidationResult.success()
        }
    }
    
    /**
     * Combine validation results for form-level validation
     * Returns first error found, or Success if all validations pass
     */
    fun validateForm(vararg validations: ValidationResult): ValidationResult {
        return ValidationResult.combine(*validations)
    }
    
    /**
     * Validate complete workout creation form
     */
    fun validateWorkoutCreationForm(
        workoutName: String,
        workoutDescription: String
    ): ValidationResult {
        return validateForm(
            validateWorkoutName(workoutName),
            validateWorkoutDescription(workoutDescription)
        )
    }
    
    /**
     * Validate exercise set data
     */
    fun validateExerciseSet(
        weight: String,
        reps: String
    ): ValidationResult {
        return validateForm(
            validateWeight(weight),
            validateReps(reps)
        )
    }
    
    // Private helper functions
    
    /**
     * Check if workout name contains only allowed characters
     */
    private fun isValidWorkoutNameCharacters(name: String): Boolean {
        // Allow letters, numbers, spaces, and basic punctuation
        val allowedPattern = Regex("^[a-zA-Z0-9\\s.,!?'\"\\-()&]+$")
        return name.matches(allowedPattern)
    }
    
    /**
     * Check if input is a valid numeric value (including decimals)
     */
    private fun isValidNumericInput(input: String): Boolean {
        return try {
            input.toDouble()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }
    
    /**
     * Check if input is a valid integer
     */
    private fun isValidIntegerInput(input: String): Boolean {
        return try {
            input.toInt()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }
    
    /**
     * Check if a number has excessive decimal places (more than 2)
     */
    private fun hasExcessiveDecimalPlaces(value: Double): Boolean {
        val decimalString = value.toString()
        if (!decimalString.contains('.')) return false
        
        val decimalPart = decimalString.substringAfter('.')
        // Remove trailing zeros
        val trimmedDecimal = decimalPart.trimEnd('0')
        return trimmedDecimal.length > 2
    }
}

/**
 * Validation presets for common use cases
 */
object ValidationPresets {
    
    /**
     * Standard workout name validation
     */
    fun workoutName(name: String) = InputValidation.validateWorkoutName(name)
    
    /**
     * Standard weight validation
     */
    fun weight(weight: String) = InputValidation.validateWeight(weight)
    
    /**
     * Standard reps validation
     */
    fun reps(reps: String) = InputValidation.validateReps(reps)
    
    /**
     * Quick required field validation
     */
    fun required(value: String, displayName: String) = 
        InputValidation.validateRequired(value, displayName.lowercase(), displayName)
}