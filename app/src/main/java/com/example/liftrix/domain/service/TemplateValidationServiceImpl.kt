package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TemplateValidationService
 *
 * Centralizes validation logic for workout templates using consistent
 * patterns and error handling
 */
@Singleton
class TemplateValidationServiceImpl @Inject constructor() : TemplateValidationService {

    override fun validateTemplateName(name: String): LiftrixResult<String> {
        val violations = mutableListOf<String>()

        if (name.isBlank()) {
            violations.add("Template name cannot be blank")
        }

        val trimmedName = name.trim()
        if (trimmedName.length > WorkoutTemplate.MAX_NAME_LENGTH) {
            violations.add(
                "Template name cannot exceed ${WorkoutTemplate.MAX_NAME_LENGTH} characters (currently ${trimmedName.length})"
            )
        }

        return if (violations.isEmpty()) {
            Result.success(trimmedName)
        } else {
            Result.failure(
                LiftrixError.ValidationError(
                    field = "name",
                    violations = violations,
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_TEMPLATE_NAME",
                        "nameLength" to trimmedName.length.toString()
                    )
                )
            )
        }
    }

    override fun validateTemplateExercises(exercises: List<TemplateExercise>): LiftrixResult<List<TemplateExercise>> {
        val violations = mutableListOf<String>()

        if (exercises.size > WorkoutTemplate.MAX_EXERCISES) {
            violations.add(
                "Template cannot have more than ${WorkoutTemplate.MAX_EXERCISES} exercises (currently ${exercises.size})"
            )
        }

        // Validate exercise order is sequential
        if (exercises.isNotEmpty()) {
            val orderIndices = exercises.map { it.orderIndex }.sorted()
            val expectedOrder = (0 until exercises.size).toList()
            if (orderIndices != expectedOrder) {
                violations.add(
                    "Exercise order indices must be sequential starting from 0. Expected: $expectedOrder, got: $orderIndices"
                )
            }
        }

        return if (violations.isEmpty()) {
            Result.success(exercises)
        } else {
            Result.failure(
                LiftrixError.ValidationError(
                    field = "exercises",
                    violations = violations,
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_TEMPLATE_EXERCISES",
                        "exerciseCount" to exercises.size.toString()
                    )
                )
            )
        }
    }

    override fun validateDifficultyLevel(level: Int?): LiftrixResult<Int?> {
        // Null is acceptable (optional field)
        if (level == null) {
            return Result.success(null)
        }

        val violations = mutableListOf<String>()

        if (level !in WorkoutTemplate.MIN_DIFFICULTY..WorkoutTemplate.MAX_DIFFICULTY) {
            violations.add(
                "Difficulty level must be between ${WorkoutTemplate.MIN_DIFFICULTY} and ${WorkoutTemplate.MAX_DIFFICULTY} (currently $level)"
            )
        }

        return if (violations.isEmpty()) {
            Result.success(level)
        } else {
            Result.failure(
                LiftrixError.ValidationError(
                    field = "difficultyLevel",
                    violations = violations,
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_DIFFICULTY_LEVEL",
                        "level" to level.toString()
                    )
                )
            )
        }
    }

    override fun validateTemplateDescription(description: String?): LiftrixResult<String?> {
        // Null is acceptable (optional field)
        if (description == null) {
            return Result.success(null)
        }

        val trimmedDescription = description.trim().takeIf { it.isNotBlank() }
        val violations = mutableListOf<String>()

        trimmedDescription?.let { desc ->
            if (desc.length > WorkoutTemplate.MAX_DESCRIPTION_LENGTH) {
                violations.add(
                    "Description cannot exceed ${WorkoutTemplate.MAX_DESCRIPTION_LENGTH} characters (currently ${desc.length})"
                )
            }
        }

        return if (violations.isEmpty()) {
            Result.success(trimmedDescription)
        } else {
            Result.failure(
                LiftrixError.ValidationError(
                    field = "description",
                    violations = violations,
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_TEMPLATE_DESCRIPTION",
                        "descriptionLength" to (trimmedDescription?.length ?: 0).toString()
                    )
                )
            )
        }
    }

    override fun validateEstimatedDuration(durationMinutes: Int?): LiftrixResult<Int?> {
        // Null is acceptable (optional field)
        if (durationMinutes == null) {
            return Result.success(null)
        }

        val violations = mutableListOf<String>()

        if (durationMinutes !in WorkoutTemplate.MIN_DURATION_MINUTES..WorkoutTemplate.MAX_DURATION_MINUTES) {
            violations.add(
                "Estimated duration must be between ${WorkoutTemplate.MIN_DURATION_MINUTES} and ${WorkoutTemplate.MAX_DURATION_MINUTES} minutes (currently $durationMinutes)"
            )
        }

        return if (violations.isEmpty()) {
            Result.success(durationMinutes)
        } else {
            Result.failure(
                LiftrixError.ValidationError(
                    field = "estimatedDurationMinutes",
                    violations = violations,
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_ESTIMATED_DURATION",
                        "duration" to durationMinutes.toString()
                    )
                )
            )
        }
    }

    override fun validateTemplateRequest(
        name: String,
        description: String?,
        exercises: List<TemplateExercise>,
        difficultyLevel: Int?,
        estimatedDurationMinutes: Int?
    ): LiftrixResult<Unit> {
        // Validate each field and collect all errors
        val nameResult = validateTemplateName(name)
        val descriptionResult = validateTemplateDescription(description)
        val exercisesResult = validateTemplateExercises(exercises)
        val difficultyResult = validateDifficultyLevel(difficultyLevel)
        val durationResult = validateEstimatedDuration(estimatedDurationMinutes)

        // Return first error found (fail-fast approach)
        return when {
            nameResult.isFailure -> Result.failure(nameResult.exceptionOrNull()!!)
            descriptionResult.isFailure -> Result.failure(descriptionResult.exceptionOrNull()!!)
            exercisesResult.isFailure -> Result.failure(exercisesResult.exceptionOrNull()!!)
            difficultyResult.isFailure -> Result.failure(difficultyResult.exceptionOrNull()!!)
            durationResult.isFailure -> Result.failure(durationResult.exceptionOrNull()!!)
            else -> Result.success(Unit)
        }
    }
}
