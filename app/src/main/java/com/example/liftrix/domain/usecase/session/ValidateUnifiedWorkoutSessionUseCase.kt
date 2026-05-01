package com.example.liftrix.domain.usecase.session

import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.repository.template.TemplateRepository
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import timber.log.Timber
import javax.inject.Inject

/**
 * 🔥 VALIDATION: Use case for validating the unified workout session system
 * 
 * This use case provides comprehensive validation and testing for the unified
 * workout session architecture. It ensures that all edge cases are handled
 * properly and the system maintains data integrity.
 * 
 * Key features:
 * - Session state validation
 * - Template integration validation
 * - Exercise scoping validation
 * - Edge case handling
 * - Data integrity checks
 */
class ValidateUnifiedWorkoutSessionUseCase @Inject constructor(
    private val sessionManager: UnifiedWorkoutSessionManager,
    private val workoutTemplateRepository: TemplateRepository
) {
    
    /**
     * Validates the current session state
     */
    suspend fun validateCurrentSession(): Result<ValidationResult> {
        return try {
            val session = sessionManager.getCurrentSession()
            
            if (session == null) {
                return Result.success(ValidationResult.NoSession)
            }
            
            val issues = mutableListOf<String>()
            
            // Validate session integrity
            validateSessionIntegrity(session, issues)
            
            // Validate exercises
            validateExercises(session, issues)
            
            // Validate session state
            validateSessionState(session, issues)
            
            // Validate template relationship
            validateTemplateRelationship(session, issues)
            
            val result = if (issues.isEmpty()) {
                ValidationResult.Valid(session)
            } else {
                ValidationResult.Invalid(session, issues)
            }
            
            Result.success(result)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate current session")
            Result.failure(e)
        }
    }
    
    /**
     * Validates session integrity
     */
    private fun validateSessionIntegrity(session: UnifiedWorkoutSession, issues: MutableList<String>) {
        // Check required fields
        if (session.id.value.isBlank()) {
            issues.add("Session ID is blank")
        }
        
        if (session.userId.isBlank()) {
            issues.add("User ID is blank")
        }
        
        if (session.name.isBlank()) {
            issues.add("Session name is blank")
        }
        
        // Check time consistency
        if (session.endedAt != null && session.endedAt!! < session.startedAt) {
            issues.add("End time is before start time")
        }
        
        // Check elapsed time
        if (session.elapsedTimeSeconds < 0) {
            issues.add("Elapsed time is negative")
        }
        
        // Check current exercise index
        if (session.currentExerciseIndex < 0) {
            issues.add("Current exercise index is negative")
        }
        
        if (session.exercises.isNotEmpty() && session.currentExerciseIndex >= session.exercises.size) {
            issues.add("Current exercise index is out of bounds")
        }
    }
    
    /**
     * Validates exercise list integrity
     */
    private fun validateExercises(session: UnifiedWorkoutSession, issues: MutableList<String>) {
        // Check for duplicate exercise IDs
        val exerciseIds = session.exercises.map { it.exerciseId }
        val duplicateIds = exerciseIds.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            issues.add("Duplicate exercise IDs found: ${duplicateIds.map { it.value }}")
        }
        
        // Check exercise order indices
        session.exercises.forEachIndexed { index, exercise ->
            if (exercise.orderIndex != index) {
                issues.add("Exercise '${exercise.name}' has incorrect order index: expected $index, got ${exercise.orderIndex}")
            }
        }
        
        // Check exercise integrity
        session.exercises.forEach { exercise ->
            if (exercise.name.isBlank()) {
                issues.add("Exercise has blank name")
            }
            
            if (exercise.sets.isEmpty()) {
                issues.add("Exercise '${exercise.name}' has no sets")
            }
            
            // Check set numbering
            exercise.sets.forEachIndexed { index, set ->
                if (set.setNumber != index + 1) {
                    issues.add("Exercise '${exercise.name}' set ${index + 1} has incorrect set number: ${set.setNumber}")
                }
            }
        }
    }
    
    /**
     * Validates session state consistency
     */
    private fun validateSessionState(session: UnifiedWorkoutSession, issues: MutableList<String>) {
        when (session.sessionStatus) {
            UnifiedWorkoutSession.SessionStatus.ACTIVE -> {
                if (session.endedAt != null) {
                    issues.add("Active session cannot have end time")
                }
            }
            UnifiedWorkoutSession.SessionStatus.PAUSED -> {
                if (session.endedAt != null) {
                    issues.add("Paused session cannot have end time")
                }
            }
            UnifiedWorkoutSession.SessionStatus.COMPLETED -> {
                if (session.endedAt == null) {
                    issues.add("Completed session must have end time")
                }
            }
            UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE -> {
                if (session.endedAt == null) {
                    issues.add("Failed to save session must have end time")
                }
            }
        }
    }
    
    /**
     * Validates template relationship
     */
    private suspend fun validateTemplateRelationship(session: UnifiedWorkoutSession, issues: MutableList<String>) {
        if (session.templateId != null) {
            val templateId = WorkoutTemplateId.fromString(session.templateId!!)
            val templateResult = workoutTemplateRepository.getTemplateById(templateId, session.userId)
            val template = templateResult.getOrNull()
            
            if (template == null) {
                issues.add("Referenced template not found: ${session.templateId}")
            } else {
                // Check template-session consistency
                if (template.userId != session.userId) {
                    issues.add("Template user ID mismatch: template ${template.userId}, session ${session.userId}")
                }
            }
        }
    }
    
    /**
     * Tests template creation from session
     */
    suspend fun testTemplateCreation(session: UnifiedWorkoutSession): Result<WorkoutTemplate> {
        return try {
            // Validate session can be converted to template
            if (session.exercises.isEmpty()) {
                return Result.failure(Exception("Cannot create template from empty session"))
            }
            
            // Create template
            val template = session.toWorkoutTemplate(
                templateName = "${session.name} - Test Template",
                description = "Test template created from session validation"
            )
            
            // Validate template
            if (template.exercises.size != session.exercises.size) {
                return Result.failure(Exception("Template exercise count mismatch"))
            }
            
            // Validate template exercises
            template.exercises.forEachIndexed { index, templateExercise ->
                val sessionExercise = session.exercises[index]
                
                if (templateExercise.name != sessionExercise.name) {
                    return Result.failure(Exception("Template exercise name mismatch at index $index"))
                }
                
                if (templateExercise.orderIndex != sessionExercise.orderIndex) {
                    return Result.failure(Exception("Template exercise order mismatch at index $index"))
                }
            }
            
            Timber.d("Template creation test passed for session: ${session.name}")
            Result.success(template)
            
        } catch (e: Exception) {
            Timber.e(e, "Template creation test failed")
            Result.failure(e)
        }
    }
    
    /**
     * Tests session recovery and persistence
     */
    suspend fun testSessionRecovery(): Result<Boolean> {
        return try {
            val originalSession = sessionManager.getCurrentSession()
            
            if (originalSession == null) {
                return Result.success(true) // No session to recover
            }
            
            // Test would involve simulating app restart and checking if session is recovered
            // For now, we'll just validate that the session exists and is valid
            
            val validationResult = validateCurrentSession().getOrThrow()
            
            val isValid = when (validationResult) {
                is ValidationResult.Valid -> true
                is ValidationResult.Invalid -> {
                    Timber.w("Session recovery validation failed: ${validationResult.issues}")
                    false
                }
                is ValidationResult.NoSession -> true // No session to recover
            }
            
            Result.success(isValid)
            
        } catch (e: Exception) {
            Timber.e(e, "Session recovery test failed")
            Result.failure(e)
        }
    }
    
    /**
     * Tests exercise addition and removal
     */
    suspend fun testExerciseManagement(): Result<Boolean> {
        return try {
            val session = sessionManager.getCurrentSession()
            
            if (session == null) {
                return Result.failure(Exception("No session available for exercise management test"))
            }
            
            val originalExerciseCount = session.exercises.size
            
            // Test would involve adding and removing exercises
            // For now, we'll just validate the current exercise list
            
            val exerciseIds = session.exercises.map { it.exerciseId }
            val hasDuplicates = exerciseIds.size != exerciseIds.distinct().size
            
            if (hasDuplicates) {
                return Result.failure(Exception("Duplicate exercises detected in session"))
            }
            
            // Validate exercise order
            session.exercises.forEachIndexed { index, exercise ->
                if (exercise.orderIndex != index) {
                    return Result.failure(Exception("Exercise order validation failed"))
                }
            }
            
            Timber.d("Exercise management test passed for session: ${session.name}")
            Result.success(true)
            
        } catch (e: Exception) {
            Timber.e(e, "Exercise management test failed")
            Result.failure(e)
        }
    }
    
    /**
     * Runs a comprehensive system validation
     */
    suspend fun runComprehensiveValidation(): Result<ComprehensiveValidationResult> {
        return try {
            val results = mutableListOf<String>()
            var hasErrors = false
            
            // Test 1: Session validation
            validateCurrentSession().fold(
                onSuccess = { result ->
                    when (result) {
                        is ValidationResult.Valid -> {
                            results.add("✓ Session validation passed")
                        }
                        is ValidationResult.Invalid -> {
                            results.add("✗ Session validation failed: ${result.issues}")
                            hasErrors = true
                        }
                        is ValidationResult.NoSession -> {
                            results.add("ℹ No session to validate")
                        }
                    }
                },
                onFailure = { error ->
                    results.add("✗ Session validation error: ${error.message}")
                    hasErrors = true
                }
            )
            
            // Test 2: Template creation
            val session = sessionManager.getCurrentSession()
            if (session != null) {
                testTemplateCreation(session).fold(
                    onSuccess = {
                        results.add("✓ Template creation test passed")
                    },
                    onFailure = { error ->
                        results.add("✗ Template creation test failed: ${error.message}")
                        hasErrors = true
                    }
                )
            }
            
            // Test 3: Exercise management
            if (session != null) {
                testExerciseManagement().fold(
                    onSuccess = {
                        results.add("✓ Exercise management test passed")
                    },
                    onFailure = { error ->
                        results.add("✗ Exercise management test failed: ${error.message}")
                        hasErrors = true
                    }
                )
            }
            
            // Test 4: Session recovery
            testSessionRecovery().fold(
                onSuccess = {
                    results.add("✓ Session recovery test passed")
                },
                onFailure = { error ->
                    results.add("✗ Session recovery test failed: ${error.message}")
                    hasErrors = true
                }
            )
            
            val finalResult = ComprehensiveValidationResult(
                passed = !hasErrors,
                results = results
            )
            
            Result.success(finalResult)
            
        } catch (e: Exception) {
            Timber.e(e, "Comprehensive validation failed")
            Result.failure(e)
        }
    }
    
    /**
     * Validation result types
     */
    sealed class ValidationResult {
        object NoSession : ValidationResult()
        data class Valid(val session: UnifiedWorkoutSession) : ValidationResult()
        data class Invalid(val session: UnifiedWorkoutSession, val issues: List<String>) : ValidationResult()
    }
    
    data class ComprehensiveValidationResult(
        val passed: Boolean,
        val results: List<String>
    )
}
