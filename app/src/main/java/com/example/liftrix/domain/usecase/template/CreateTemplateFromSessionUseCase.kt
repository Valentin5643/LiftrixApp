package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for converting an active workout session to a reusable template.
 * 
 * Key features:
 * - Removes session-specific data (timer duration, completion timestamps, notes)
 * - Preserves exercise structure and target values
 * - Creates clean, reusable template
 * - Estimates duration based on exercise count and sets
 * - Assigns appropriate difficulty level
 * 
 * The conversion process ensures templates are free of session-specific data
 * while maintaining the core workout structure for reuse.
 */
@Singleton
class CreateTemplateFromSessionUseCase @Inject constructor(
    private val templateRepository: WorkoutTemplateRepository,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Creates a workout template from an active session
     * 
     * @param session The active workout session to convert
     * @param templateName Custom name for the template
     * @param templateDescription Optional description for the template
     * @return LiftrixResult containing the created template or error
     */
    suspend operator fun invoke(
        session: UnifiedWorkoutSession,
        templateName: String,
        templateDescription: String? = null
    ): LiftrixResult<WorkoutTemplate> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when (throwable) {
                    is IllegalArgumentException -> LiftrixError.ValidationError(
                        field = when {
                            throwable.message?.contains("Template name") == true -> "templateName"
                            throwable.message?.contains("empty workout") == true -> "session"
                            else -> "input"
                        },
                        violations = listOf(throwable.message ?: "Invalid input parameters")
                    )
                    else -> LiftrixError.DatabaseError(
                        errorMessage = "Failed to create template from session",
                        operation = "createTemplate"
                    )
                }
            }
        ) {
            validateInput(session, templateName)
            
            val template = convertSessionToTemplate(
                session = session,
                templateName = templateName.trim(),
                templateDescription = templateDescription?.trim()?.takeIf { it.isNotBlank() }
            )
            
            templateRepository.createTemplate(template).getOrThrow()
        }
    }
    
    /**
     * Validates input parameters
     */
    private fun validateInput(session: UnifiedWorkoutSession, templateName: String) {
        require(templateName.isNotBlank()) { "Template name cannot be blank" }
        require(templateName.length <= WorkoutTemplate.MAX_NAME_LENGTH) { 
            "Template name too long: ${templateName.length} > ${WorkoutTemplate.MAX_NAME_LENGTH}" 
        }
        require(session.exercises.isNotEmpty()) { "Cannot create template from empty workout" }
    }
    
    /**
     * Converts an active session to a template, removing session-specific data
     */
    private fun convertSessionToTemplate(
        session: UnifiedWorkoutSession,
        templateName: String,
        templateDescription: String?
    ): WorkoutTemplate {
        
        // Convert session exercises to template exercises
        val templateExercises = session.exercises.mapIndexed { index: Int, sessionExercise: com.example.liftrix.domain.model.SessionExercise ->
            TemplateExercise(
                exerciseId = sessionExercise.exerciseId,
                name = sessionExercise.name,
                primaryMuscle = sessionExercise.primaryMuscle,
                equipment = Equipment.BODYWEIGHT_ONLY, // Default equipment - could be enhanced with lookup
                orderIndex = index,
                
                // Use target values from session exercise, or derive from completed sets
                targetSets = deriveTargetSets(sessionExercise),
                targetReps = deriveTargetReps(sessionExercise),
                targetWeight = deriveTargetWeight(sessionExercise),
                restTimeSeconds = sessionExercise.restTimeSeconds,
                
                notes = null // Remove session-specific notes
            )
        }
        
        return WorkoutTemplate(
            id = WorkoutTemplateId.generate(),
            userId = session.userId,
            name = templateName,
            description = templateDescription,
            exercises = templateExercises,
            
            // Estimate workout characteristics
            estimatedDurationMinutes = estimateWorkoutDuration(templateExercises),
            difficultyLevel = estimateDifficultyLevel(templateExercises),
            folderId = "uncategorized_${session.userId}",
            
            // Template metadata
            usageCount = 0,
            lastUsedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Derives target sets from completed session data
     */
    private fun deriveTargetSets(sessionExercise: com.example.liftrix.domain.model.SessionExercise): Int? {
        return if (sessionExercise.sets.isNotEmpty()) {
            sessionExercise.sets.size
        } else null
    }
    
    /**
     * Derives target reps from completed session data
     */
    private fun deriveTargetReps(sessionExercise: com.example.liftrix.domain.model.SessionExercise): Reps? {
        val completedSets = sessionExercise.sets.filter { it.completedAt != null && it.actualReps != null }
        return if (completedSets.isNotEmpty()) {
            // Use the most common rep count or average
            val repCounts = completedSets.mapNotNull { it.actualReps }
            val averageReps = repCounts.average().toInt()
            Reps(averageReps)
        } else null
    }
    
    /**
     * Derives target weight from completed session data
     */
    private fun deriveTargetWeight(sessionExercise: com.example.liftrix.domain.model.SessionExercise): Weight? {
        val completedSets = sessionExercise.sets.filter { it.completedAt != null && it.actualWeight != null }
        return if (completedSets.isNotEmpty()) {
            // Use the most common weight or average
            val weights = completedSets.mapNotNull { it.actualWeight?.kilograms }
            val averageWeight = weights.average()
            Weight(averageWeight)
        } else null
    }
    
    /**
     * Estimates workout duration based on exercises and sets
     */
    private fun estimateWorkoutDuration(exercises: List<TemplateExercise>): Int {
        var totalMinutes = 0
        
        exercises.forEach { exercise: TemplateExercise ->
            val sets = exercise.targetSets ?: 3 // Default to 3 sets
            val reps = exercise.targetReps?.count ?: 10 // Default to 10 reps
            
            // Estimate time per set based on exercise type
            val timePerSetSeconds = when {
                exercise.primaryMuscle in listOf(ExerciseCategory.CHEST, ExerciseCategory.BACK, ExerciseCategory.LEGS) -> 45 // Compound movements take longer
                reps > 15 -> 30 // High rep sets
                else -> 25 // Standard sets
            }
            
            // Add rest time
            val restSeconds = exercise.restTimeSeconds ?: 90
            
            // Total time for this exercise
            val exerciseTimeMinutes = ((timePerSetSeconds + restSeconds) * sets) / 60
            totalMinutes += exerciseTimeMinutes
        }
        
        // Add warm-up and cool-down time
        totalMinutes += 10
        
        return totalMinutes.coerceIn(15, 180) // Between 15 minutes and 3 hours
    }
    
    /**
     * Estimates difficulty level based on exercise complexity and volume
     */
    private fun estimateDifficultyLevel(exercises: List<TemplateExercise>): Int {
        val complexityScore = exercises.sumOf { exercise: TemplateExercise ->
            when (exercise.primaryMuscle) {
                ExerciseCategory.CHEST, ExerciseCategory.BACK, ExerciseCategory.LEGS -> 3 // Compound movements
                ExerciseCategory.CORE, ExerciseCategory.CARDIO -> 1 // Easier exercises
                else -> 2 // Isolation movements
            }
        }
        
        val volumeScore = exercises.sumOf { exercise: TemplateExercise ->
            (exercise.targetSets ?: 3) * (exercise.targetReps?.count ?: 10)
        }
        
        val totalExercises = exercises.size
        
        return when {
            complexityScore >= totalExercises * 2.5 || volumeScore >= 200 -> 5 // Advanced
            complexityScore >= totalExercises * 2 || volumeScore >= 150 -> 4 // Intermediate-Advanced
            complexityScore >= totalExercises * 1.5 || volumeScore >= 100 -> 3 // Intermediate
            complexityScore >= totalExercises || volumeScore >= 50 -> 2 // Beginner-Intermediate
            else -> 1 // Beginner
        }.coerceIn(1, 5)
    }
    
}