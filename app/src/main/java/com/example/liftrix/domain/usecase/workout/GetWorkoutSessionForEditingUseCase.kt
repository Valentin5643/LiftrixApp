package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * GetWorkoutSessionForEditingUseCase - Retrieves workout session data for editing
 * 
 * Comprehensive implementation for loading workout sessions with proper validation,
 * security checks, and editing context. Supports both historical and active sessions.
 */
class GetWorkoutSessionForEditingUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {

    suspend operator fun invoke(params: GetWorkoutSessionForEditingRequest): LiftrixResult<WorkoutSessionEditingData> {
        return try {
            // Validate request parameters
            val validationResult = validateRequest(params)
            if (validationResult.isFailure) {
                return Result.failure(validationResult.exceptionOrNull() as LiftrixError)
            }
            
            // Get current user ID for security validation
            val currentUserId = getCurrentUserIdUseCase()
            if (currentUserId.isNullOrBlank()) {
                return Result.failure(
                    LiftrixError.AuthenticationError(
                        errorMessage = "User not authenticated",
                        errorCode = "USER_NOT_AUTHENTICATED",
                        analyticsContext = mapOf("operation" to "get_workout_for_editing")
                    )
                )
            }
            
            Timber.d("Loading workout session for editing: ${params.sessionId.value} for user: $currentUserId")
            
            // Load the workout session from repository
            val sessionResult = workoutRepository.getWorkoutById(params.sessionId, currentUserId)
            
            return sessionResult.fold(
                onSuccess = { session ->
                    if (session == null) {
                        return@fold Result.failure(
                            LiftrixError.NotFoundError(
                                errorMessage = "Workout session not found or access denied",
                                resourceType = "workout_session",
                                resourceId = params.sessionId.value
                            )
                        )
                    }
                    
                    // Validate edit permissions
                    val editPermissions = validateEditPermissions(session, currentUserId, params)
                    if (!editPermissions.canEdit) {
                        return@fold Result.failure(
                            LiftrixError.PermissionError(
                                errorMessage = editPermissions.reason ?: "Edit access denied",
                                permission = "EDIT_WORKOUT",
                                analyticsContext = mapOf(
                                    "workout_id" to params.sessionId.value,
                                    "user_id" to currentUserId,
                                    "reason" to (editPermissions.reason ?: "unknown")
                                )
                            )
                        )
                    }
                    
                    // Create comprehensive editing data
                    val editingData = createEditingData(session, editPermissions)
                    
                    Timber.i("Successfully loaded workout session for editing: ${session.name} (${editingData.totalExercises} exercises, ${editingData.totalSets} sets)")
                    Result.success(editingData)
                },
                onFailure = { error ->
                    Timber.e("Failed to load workout from repository: $error")
                    val liftrixError = when (error) {
                        is LiftrixError -> error
                        else -> LiftrixError.DatabaseError(
                            errorMessage = "Failed to load workout session: ${error.message}",
                            operation = "getWorkoutById",
                            analyticsContext = mapOf(
                                "workout_id" to params.sessionId.value,
                                "exception" to error.javaClass.simpleName
                            )
                        )
                    }
                    Result.failure(liftrixError)
                }
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load workout session for editing: ${e.message}")
            Result.failure(
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to load workout session: ${e.message}",
                    operation = "getWorkoutSessionForEditing",
                    analyticsContext = mapOf(
                        "workout_id" to params.sessionId.value,
                        "exception" to e.javaClass.simpleName
                    )
                )
            )
        }
    }

    /**
     * Validates the request parameters
     */
    private fun validateRequest(params: GetWorkoutSessionForEditingRequest): LiftrixResult<Unit> {
        val violations = mutableListOf<String>()
        
        if (params.sessionId.value.isBlank()) {
            violations.add("Session ID cannot be blank")
        }
        
        return if (violations.isEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(
                LiftrixError.ValidationError(
                    field = "GetWorkoutSessionForEditingRequest",
                    violations = violations,
                    analyticsContext = mapOf("operation" to "validate_editing_request")
                )
            )
        }
    }

    /**
     * Validates edit permissions for the workout
     */
    private fun validateEditPermissions(
        session: Workout, 
        currentUserId: String, 
        params: GetWorkoutSessionForEditingRequest
    ): EditPermissions {
        // Check if user owns the workout
        if (session.userId != currentUserId && !params.allowCrossUserEditing) {
            return EditPermissions(
                canEdit = false,
                reason = "User does not own this workout"
            )
        }
        
        // Check if workout is in a state that allows editing
        // For example, completed workouts might have restrictions
        if (session.status == com.example.liftrix.domain.model.WorkoutStatus.COMPLETED) {
            // Historical workouts can be edited but with warnings
            return EditPermissions(
                canEdit = true,
                reason = null,
                hasWarnings = true,
                warnings = listOf("Editing a completed workout may affect historical data")
            )
        }
        
        return EditPermissions(canEdit = true)
    }

    /**
     * Creates comprehensive editing data with all necessary context
     */
    private fun createEditingData(session: Workout, permissions: EditPermissions): WorkoutSessionEditingData {
        val totalSets = session.exercises.sumOf { it.sets.size }
        val completedSets = session.exercises.sumOf { exercise ->
            exercise.sets.count { it.completedAt != null }
        }
        
        return WorkoutSessionEditingData(
            session = session,
            originalCreatedAt = session.createdAt,
            lastModified = session.updatedAt,
            isHistoricalSession = session.status == com.example.liftrix.domain.model.WorkoutStatus.COMPLETED,
            totalExercises = session.exercises.size,
            totalSets = totalSets,
            completedSets = completedSets,
            sessionDuration = session.getDuration(),
            canEdit = permissions.canEdit,
            editWarnings = permissions.warnings,
            hasEditWarnings = permissions.hasWarnings
        )
    }
}

/**
 * Internal class for managing edit permissions
 */
private data class EditPermissions(
    val canEdit: Boolean,
    val reason: String? = null,
    val hasWarnings: Boolean = false,
    val warnings: List<String> = emptyList()
)

/**
 * Request parameters for GetWorkoutSessionForEditingUseCase
 */
data class GetWorkoutSessionForEditingRequest(
    val sessionId: WorkoutId,
    val allowCrossUserEditing: Boolean = false // For admin/coach access
)

/**
 * Response data for workout session editing with historical context
 */
data class WorkoutSessionEditingData(
    val session: Workout,
    val originalCreatedAt: java.time.Instant,
    val lastModified: java.time.Instant?,
    val isHistoricalSession: Boolean,
    val totalExercises: Int,
    val totalSets: Int,
    val completedSets: Int,
    val sessionDuration: java.time.Duration?,
    val canEdit: Boolean,
    val editWarnings: List<String> = emptyList(),
    val hasEditWarnings: Boolean = false
) {
    val completionPercentage: Float
        get() = if (totalSets > 0) (completedSets.toFloat() / totalSets) * 100f else 0f
        
    val hasModifications: Boolean
        get() = lastModified != null && lastModified != originalCreatedAt
        
    val isCompletelyFinished: Boolean
        get() = completedSets == totalSets && totalSets > 0
        
    val estimatedTimeRemaining: java.time.Duration?
        get() = if (sessionDuration != null && completionPercentage > 0f && completionPercentage < 100f) {
            val averageTimePerSet = sessionDuration.toMinutes() / completedSets.coerceAtLeast(1)
            val remainingSets = totalSets - completedSets
            java.time.Duration.ofMinutes((averageTimePerSet * remainingSets).toLong())
        } else null
}