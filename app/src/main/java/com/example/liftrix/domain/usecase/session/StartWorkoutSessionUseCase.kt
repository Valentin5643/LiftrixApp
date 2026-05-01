package com.example.liftrix.domain.usecase.session

import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.repository.template.TemplateRepository
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import timber.log.Timber
import javax.inject.Inject

/**
 * 🔥 KEY FIX: Use case for starting workout sessions with proper template handling
 * 
 * This use case handles starting new workout sessions from templates or as blank
 * workouts. It ensures proper template-to-session conversion while maintaining
 * session-scoped exercise management.
 * 
 * Key features:
 * - Template-based session creation
 * - Blank session creation
 * - Session-scoped exercise conversion
 * - Proper error handling
 * - Template workflow preservation
 */
class StartWorkoutSessionUseCase @Inject constructor(
    private val sessionManager: UnifiedWorkoutSessionManager,
    private val workoutTemplateRepository: TemplateRepository
) {
    /**
     * Starts a new workout session from a template
     */
    suspend fun executeFromTemplate(
        userId: String,
        templateId: WorkoutTemplateId,
        customName: String? = null
    ): Result<UnifiedWorkoutSession> {
        return try {
            // Check if there's already an active session
            if (sessionManager.hasActiveSession()) {
                Timber.w("Cannot start new session - session already active")
                return Result.failure(Exception("Another workout session is already active"))
            }
            
            // Get the template
            val templateResult = workoutTemplateRepository.getTemplateById(templateId, userId)
            if (templateResult.isFailure) {
                Timber.w("Failed to get template: ${templateId.value}")
                return Result.failure(templateResult.exceptionOrNull() ?: Exception("Failed to get template"))
            }
            
            val template = templateResult.getOrNull()
            if (template == null) {
                Timber.w("Template not found: ${templateId.value}")
                return Result.failure(Exception("Workout template not found"))
            }
            
            // Create session from template
            val session = UnifiedWorkoutSession.fromTemplate(
                userId = userId,
                template = template,
                customName = customName
            )
            
            // Start the session
            sessionManager.startSession(session)
            
            Timber.i("Workout session started from template: ${template.name}")
            Result.success(session)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start workout session from template")
            Result.failure(e)
        }
    }
    
    /**
     * Starts a new blank workout session
     */
    suspend fun executeBlank(
        userId: String,
        workoutName: String
    ): Result<UnifiedWorkoutSession> {
        return try {
            // Check if there's already an active session
            if (sessionManager.hasActiveSession()) {
                Timber.w("Cannot start new session - session already active")
                return Result.failure(Exception("Another workout session is already active"))
            }
            
            // Validate input
            if (workoutName.isBlank()) {
                return Result.failure(Exception("Workout name cannot be blank"))
            }
            
            // Create blank session
            val session = UnifiedWorkoutSession.createBlank(
                userId = userId,
                name = workoutName.trim()
            )
            
            // Start the session
            sessionManager.startSession(session)
            
            Timber.i("Blank workout session started: $workoutName")
            Result.success(session)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start blank workout session")
            Result.failure(e)
        }
    }
    
    /**
     * Starts a "Quick Start" session with common exercises
     */
    suspend fun executeQuickStart(
        userId: String,
        workoutType: QuickStartType = QuickStartType.GENERAL
    ): Result<UnifiedWorkoutSession> {
        return try {
            // Check if there's already an active session
            if (sessionManager.hasActiveSession()) {
                Timber.w("Cannot start new session - session already active")
                return Result.failure(Exception("Another workout session is already active"))
            }
            
            val workoutName = when (workoutType) {
                QuickStartType.GENERAL -> "Quick Workout"
                QuickStartType.UPPER_BODY -> "Upper Body Quick"
                QuickStartType.LOWER_BODY -> "Lower Body Quick"
                QuickStartType.CARDIO -> "Cardio Quick"
                QuickStartType.STRENGTH -> "Strength Quick"
            }
            
            // Create session
            val session = UnifiedWorkoutSession.createBlank(
                userId = userId,
                name = workoutName
            )
            
            // Start the session
            sessionManager.startSession(session)
            
            Timber.i("Quick start workout session started: $workoutName")
            Result.success(session)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start quick start workout session")
            Result.failure(e)
        }
    }
    
    /**
     * Resumes an existing session (if any)
     */
    suspend fun resumeExistingSession(): Result<UnifiedWorkoutSession?> {
        return try {
            val currentSession = sessionManager.getCurrentSession()
            
            if (currentSession == null) {
                Timber.d("No existing session to resume")
                return Result.success(null)
            }
            
            if (currentSession.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
                Timber.d("Session already completed, cannot resume")
                return Result.success(null)
            }
            
            // Resume if paused
            if (currentSession.sessionStatus == UnifiedWorkoutSession.SessionStatus.PAUSED) {
                sessionManager.resumeSession()
                Timber.i("Existing session resumed: ${currentSession.name}")
            }
            
            Result.success(currentSession)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to resume existing session")
            Result.failure(e)
        }
    }
    
    /**
     * Quick start workout types
     */
    enum class QuickStartType {
        GENERAL,
        UPPER_BODY,
        LOWER_BODY,
        CARDIO,
        STRENGTH
    }
}
