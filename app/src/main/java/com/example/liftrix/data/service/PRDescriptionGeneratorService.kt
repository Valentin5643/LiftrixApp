package com.example.liftrix.data.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import com.example.liftrix.domain.service.PRSignificance
import com.example.liftrix.domain.service.PRType
import com.example.liftrix.domain.service.PersonalRecord
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for generating dynamic PR descriptions based on PR data and context.
 * Part of Phase 1: User Profile Data Integration from SPEC-20250901-todo-implementation.
 */
@Singleton
class PRDescriptionGeneratorService @Inject constructor(
    private val exerciseRepository: ExerciseRepository
) {
    
    /**
     * Generates a descriptive PR description based on PR data.
     * @param prId The PR ID for context (used for analytics)
     * @param userId The user ID who achieved the PR (mandatory for user scoping)
     * @return LiftrixResult with generated PR description
     */
    suspend fun generateDescription(
        prId: String,
        userId: String
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PR_DESCRIPTION_GENERATION_FAILED",
                errorMessage = "Failed to generate PR description",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "pr_id" to prId
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(prId.isNotBlank()) { "PR ID cannot be blank" }
        
        // For now, return a generic description
        // In a full implementation, you would:
        // 1. Fetch PR data by prId from PRRepository
        // 2. Get exercise name from exercise library
        // 3. Generate contextual description based on PR type and significance
        
        "Personal Record achieved!"
    }
    
    /**
     * Generates a PR description from PersonalRecord data.
     * @param personalRecord The PersonalRecord containing PR details
     * @param userId The user ID (mandatory for user scoping)
     * @return LiftrixResult with generated description
     */
    suspend fun generateDescriptionFromPR(
        personalRecord: PersonalRecord,
        userId: String
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PR_DESCRIPTION_FROM_PR_FAILED",
                errorMessage = "Failed to generate PR description from PersonalRecord",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "exercise_name" to personalRecord.exerciseName,
                    "pr_type" to personalRecord.prType.name
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        
        val exercise = exerciseRepository.getExerciseById(personalRecord.exerciseName).fold(
            onSuccess = { it },
            onFailure = { null }
        )
        
        val exerciseName = exercise?.name ?: personalRecord.exerciseName
        
        generateDescriptionText(personalRecord, exerciseName)
    }
    
    /**
     * Generates description for a PR based on type and significance.
     * @param prType Type of personal record
     * @param exerciseName Name of the exercise
     * @param value The value achieved (weight, reps, or volume)
     * @param significance The significance level of the PR
     * @param userId User ID for analytics context
     * @return LiftrixResult with generated description
     */
    suspend fun generateDescription(
        prType: PRType,
        exerciseName: String,
        value: Double,
        significance: PRSignificance,
        userId: String
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PR_DESCRIPTION_GENERATION_FAILED",
                errorMessage = "Failed to generate PR description",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "exercise_name" to exerciseName,
                    "pr_type" to prType.name,
                    "significance" to significance.name
                )
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(exerciseName.isNotBlank()) { "Exercise name cannot be blank" }
        
        val exercise = exerciseRepository.getExerciseById(exerciseName).fold(
            onSuccess = { it },
            onFailure = { null }
        )
        
        val displayName = exercise?.name ?: exerciseName
        
        when (prType) {
            PRType.ONE_RM -> "${significance.displayName}: ${value.toInt()}lb 1RM $displayName"
            PRType.VOLUME -> "${significance.displayName}: ${value.toInt()}lb total volume $displayName"
            PRType.REPS -> "${significance.displayName}: ${value.toInt()} reps $displayName"
            PRType.MAX_WEIGHT -> "${significance.displayName}: ${value.toInt()}lb max weight $displayName"
        }
    }
    
    /**
     * Private helper to generate descriptive text from PersonalRecord data.
     */
    private fun generateDescriptionText(personalRecord: PersonalRecord, exerciseName: String): String {
        val significance = calculateSignificance(personalRecord)
        
        return when (personalRecord.prType) {
            PRType.ONE_RM -> {
                val oneRM = personalRecord.estimatedOneRM?.toInt() ?: 0
                "${significance.displayName}: ${oneRM}lb 1RM $exerciseName"
            }
            PRType.VOLUME -> {
                val volume = personalRecord.volume?.toInt() ?: 0
                "${significance.displayName}: ${volume}lb volume $exerciseName"
            }
            PRType.REPS -> {
                val weight = personalRecord.weight?.toInt() ?: 0
                "${significance.displayName}: ${personalRecord.reps} reps @ ${weight}lb $exerciseName"
            }
            PRType.MAX_WEIGHT -> {
                val weight = personalRecord.weight?.toInt() ?: 0
                "${significance.displayName}: ${weight}lb max $exerciseName"
            }
        }
    }
    
    /**
     * Calculates significance based on improvement percentage.
     */
    private fun calculateSignificance(personalRecord: PersonalRecord): PRSignificance {
        val improvementPercent = personalRecord.improvementPercent ?: 0.0
        
        return when {
            improvementPercent >= PRSignificance.EXCEPTIONAL.threshold -> PRSignificance.EXCEPTIONAL
            improvementPercent >= PRSignificance.MAJOR.threshold -> PRSignificance.MAJOR
            improvementPercent >= PRSignificance.MODERATE.threshold -> PRSignificance.MODERATE
            else -> PRSignificance.MINOR
        }
    }
}