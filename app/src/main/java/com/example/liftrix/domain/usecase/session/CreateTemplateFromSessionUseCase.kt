package com.example.liftrix.domain.usecase.session

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import timber.log.Timber
import javax.inject.Inject

/**
 * 🔥 TEMPLATE INTEGRATION: Use case for creating templates from workout sessions
 * 
 * This use case ensures that the existing template creation workflow remains
 * intact while working with the new unified session architecture. It handles
 * the conversion from completed sessions to reusable templates.
 * 
 * Key features:
 * - Preserves existing template creation workflow
 * - Handles session-to-template conversion
 * - Maintains template metadata
 * - Proper error handling
 * - Compatible with existing template system
 */
class CreateTemplateFromSessionUseCase @Inject constructor(
    private val sessionManager: UnifiedWorkoutSessionManager,
    private val workoutTemplateRepository: WorkoutTemplateRepository
) {
    /**
     * Creates a template from the current active session
     */
    suspend fun executeFromCurrentSession(
        templateName: String,
        description: String? = null,
        isPublic: Boolean = false
    ): Result<WorkoutTemplate> {
        return try {
            val currentSession = sessionManager.getCurrentSession()
            if (currentSession == null) {
                Timber.w("Cannot create template - no active session")
                return Result.failure(Exception("No active workout session"))
            }
            
            executeFromSession(currentSession, templateName, description, isPublic)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create template from current session")
            Result.failure(e)
        }
    }
    
    /**
     * Creates a template from a specific session
     */
    suspend fun executeFromSession(
        session: UnifiedWorkoutSession,
        templateName: String,
        description: String? = null,
        isPublic: Boolean = false
    ): Result<WorkoutTemplate> {
        return try {
            // Validate input
            if (templateName.isBlank()) {
                return Result.failure(Exception("Template name cannot be blank"))
            }
            
            if (session.exercises.isEmpty()) {
                return Result.failure(Exception("Cannot create template from empty workout"))
            }
            
            // Create template from session
            val template = session.toWorkoutTemplate(
                templateName = templateName.trim(),
                description = description?.trim()
            )
            
            // Save template to repository
            return workoutTemplateRepository.createTemplate(template)
                .fold(
                    onSuccess = {
                        Timber.i("Template created successfully: $templateName")
                        Result.success(template)
                    },
                    onFailure = { exception ->
                        Timber.e(exception, "Failed to save template: $templateName")
                        Result.failure(exception)
                    }
                )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create template from session")
            Result.failure(e)
        }
    }
    
    /**
     * Creates a template from a completed workout session
     */
    suspend fun executeFromCompletedSession(
        sessionId: String,
        templateName: String,
        description: String? = null,
        isPublic: Boolean = false
    ): Result<WorkoutTemplate> {
        return try {
            // Note: This would typically load a completed session from storage
            // For now, we'll work with the current session if it matches
            val currentSession = sessionManager.getCurrentSession()
            
            if (currentSession == null || currentSession.id.value != sessionId) {
                return Result.failure(Exception("Session not found"))
            }
            
            executeFromSession(currentSession, templateName, description, isPublic)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create template from completed session")
            Result.failure(e)
        }
    }
    
    /**
     * Creates a template with smart defaults based on session data
     */
    suspend fun executeWithSmartDefaults(
        session: UnifiedWorkoutSession,
        templateName: String? = null
    ): Result<WorkoutTemplate> {
        return try {
            // Generate smart template name if not provided
            val smartTemplateName = templateName?.takeIf { it.isNotBlank() }
                ?: generateSmartTemplateName(session)
            
            // Generate smart description based on session content
            val smartDescription = generateSmartDescription(session)
            
            // Determine if template should be public based on session quality
            val shouldBePublic = session.exercises.size >= 3 && 
                                session.getCompletionPercentage() >= 80f
            
            executeFromSession(
                session = session,
                templateName = smartTemplateName,
                description = smartDescription,
                isPublic = shouldBePublic
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to create template with smart defaults")
            Result.failure(e)
        }
    }
    
    /**
     * Generates a smart template name based on session content
     */
    private fun generateSmartTemplateName(session: UnifiedWorkoutSession): String {
        // If session already has a template, use that as base
        if (session.templateId != null) {
            return "${session.name} - Modified"
        }
        
        // Generate based on primary muscle groups
        val primaryMuscles = session.exercises
            .map { it.primaryMuscle }
            .distinct()
            .take(2)
        
        val muscleGroupName = when {
            primaryMuscles.size == 1 -> primaryMuscles.first().name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }
            primaryMuscles.size == 2 -> "${primaryMuscles.first().name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }} & ${primaryMuscles.last().name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }}"
            else -> "Full Body"
        }
        
        return "$muscleGroupName Workout"
    }
    
    /**
     * Generates a smart description based on session content
     */
    private fun generateSmartDescription(session: UnifiedWorkoutSession): String {
        val stats = session.getSessionStats()
        val duration = session.getFormattedDuration()
        
        return buildString {
            append("Created from workout session on ${java.time.LocalDate.now()}. ")
            append("${stats.totalExercises} exercises, ")
            append("${stats.totalSets} sets, ")
            append("${stats.totalVolume.toInt()} kg total volume. ")
            append("Duration: $duration.")
        }
    }
    
    /**
     * Validates template creation requirements
     */
    fun validateTemplateCreation(session: UnifiedWorkoutSession): Result<Unit> {
        return try {
            when {
                session.exercises.isEmpty() -> {
                    Result.failure(Exception("Cannot create template from empty workout"))
                }
                session.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE -> {
                    Result.failure(Exception("Cannot create template from active session. Complete or pause the workout first."))
                }
                session.getCompletionPercentage() < 20f -> {
                    Result.failure(Exception("Cannot create template from workout with less than 20% completion"))
                }
                else -> Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}