package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError

/**
 * Centralizes template validation logic previously duplicated across:
 * - CreateWorkoutTemplateUseCase
 * - DuplicateWorkoutTemplateUseCase
 * - CreateTemplateFromSessionUseCase
 *
 * Provides consistent validation rules for workout template operations
 */
interface TemplateValidationService {

    /**
     * Validates template name
     *
     * Rules:
     * - Cannot be blank
     * - Must not exceed MAX_NAME_LENGTH (100 characters)
     * - Should be trimmed
     *
     * @param name Template name to validate
     * @return LiftrixResult.Success with trimmed name or LiftrixResult.Error with ValidationError
     */
    fun validateTemplateName(name: String): LiftrixResult<String>

    /**
     * Validates template exercises list
     *
     * Rules:
     * - Cannot exceed MAX_EXERCISES (20 exercises)
     * - Exercise order indices must be sequential starting from 0
     *
     * @param exercises List of template exercises to validate
     * @return LiftrixResult.Success with validated exercises or LiftrixResult.Error with ValidationError
     */
    fun validateTemplateExercises(exercises: List<TemplateExercise>): LiftrixResult<List<TemplateExercise>>

    /**
     * Validates difficulty level
     *
     * Rules:
     * - Must be between MIN_DIFFICULTY (1) and MAX_DIFFICULTY (10)
     * - Null is acceptable (optional field)
     *
     * @param level Difficulty level to validate (nullable)
     * @return LiftrixResult.Success with validated level or LiftrixResult.Error with ValidationError
     */
    fun validateDifficultyLevel(level: Int?): LiftrixResult<Int?>

    /**
     * Validates template description
     *
     * Rules:
     * - Cannot exceed MAX_DESCRIPTION_LENGTH (500 characters)
     * - Null is acceptable (optional field)
     * - Should be trimmed
     *
     * @param description Template description to validate (nullable)
     * @return LiftrixResult.Success with trimmed description or LiftrixResult.Error with ValidationError
     */
    fun validateTemplateDescription(description: String?): LiftrixResult<String?>

    /**
     * Validates estimated duration
     *
     * Rules:
     * - Must be between MIN_DURATION_MINUTES (5) and MAX_DURATION_MINUTES (300 minutes / 5 hours)
     * - Null is acceptable (optional field)
     *
     * @param durationMinutes Estimated duration in minutes to validate (nullable)
     * @return LiftrixResult.Success with validated duration or LiftrixResult.Error with ValidationError
     */
    fun validateEstimatedDuration(durationMinutes: Int?): LiftrixResult<Int?>

    /**
     * Validates all template fields together
     *
     * Convenience method that validates:
     * - Template name
     * - Description
     * - Exercises
     * - Difficulty level
     * - Estimated duration
     *
     * @param name Template name
     * @param description Template description (nullable)
     * @param exercises List of template exercises
     * @param difficultyLevel Difficulty level (nullable)
     * @param estimatedDurationMinutes Estimated duration (nullable)
     * @return LiftrixResult.Success if all validations pass or LiftrixResult.Error with first ValidationError found
     */
    fun validateTemplateRequest(
        name: String,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        difficultyLevel: Int? = null,
        estimatedDurationMinutes: Int? = null
    ): LiftrixResult<Unit>
}
