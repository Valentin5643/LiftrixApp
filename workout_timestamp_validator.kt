package com.example.liftrix.domain.validation

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import timber.log.Timber

/**
 * Validates workout timestamps to prevent data pipeline inconsistencies
 */
object WorkoutTimestampValidator {
    
    /**
     * Validates that a workout date is reasonable (not too far in future/past)
     */
    fun validateWorkoutDate(workoutDate: LocalDate): ValidationResult {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val maxFutureDays = 1 // Allow 1 day in future for timezone differences
        val maxPastDays = 365 * 2 // Allow 2 years in past
        
        val daysDifference = workoutDate.toEpochDays() - today.toEpochDays()
        
        return when {
            daysDifference > maxFutureDays -> {
                Timber.w("🔍 TIMESTAMP-VALIDATION: Workout date $workoutDate is ${daysDifference} days in future")
                ValidationResult.Invalid("Workout date cannot be more than $maxFutureDays day in the future")
            }
            daysDifference < -maxPastDays -> {
                ValidationResult.Invalid("Workout date cannot be more than $maxPastDays days in the past")
            }
            else -> {
                ValidationResult.Valid
            }
        }
    }
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
}

// INTEGRATION POINT: Add to workout creation flow
// Before saving WorkoutEntity:
// val validation = WorkoutTimestampValidator.validateWorkoutDate(workoutEntity.date)
// if (validation is ValidationResult.Invalid) {
//     throw IllegalArgumentException("Invalid workout date: ${validation.reason}")
// }