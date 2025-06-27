package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutStatus

import com.example.liftrix.domain.service.AnalyticsService
import java.time.Duration
import javax.inject.Inject

/**
 * Use case for logging workout-related analytics events
 */
class LogWorkoutEventUseCase @Inject constructor(
    private val analyticsService: AnalyticsService
) {
    
    /**
     * Logs workout start event
     */
    suspend fun logWorkoutStart(workout: Workout): Result<Unit> {
        if (workout.userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Workout must have a valid user ID"))
        }
        
        return analyticsService.logWorkoutStart(
            userId = workout.userId,
            workoutId = workout.id.value,
            workoutName = workout.name
        )
    }
    
    /**
     * Logs workout completion event with metrics
     */
    suspend fun logWorkoutComplete(workout: Workout): Result<Unit> {
        if (workout.userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Workout must have a valid user ID"))
        }
        
        if (workout.status != WorkoutStatus.COMPLETED) {
            return Result.failure(IllegalArgumentException("Workout must be completed to log completion event"))
        }
        
        val metrics = workout.getMetrics()
        val durationMinutes = workout.getDuration()?.toMinutes()
        
        return analyticsService.logWorkoutComplete(
            userId = workout.userId,
            workoutId = workout.id.value,
            workoutName = workout.name,
            metrics = metrics,
            durationMinutes = durationMinutes
        )
    }
    
    /**
     * Logs workout status change events
     */
    suspend fun logWorkoutStatusChange(
        workout: Workout,
        previousStatus: WorkoutStatus
    ): Result<Unit> {
        return when (workout.status) {
            WorkoutStatus.IN_PROGRESS -> {
                if (previousStatus == WorkoutStatus.PLANNED) {
                    logWorkoutStart(workout)
                } else {
                    Result.success(Unit) // Resume or other status change, no specific event
                }
            }
            WorkoutStatus.COMPLETED -> logWorkoutComplete(workout)
            else -> Result.success(Unit) // No specific event for other status changes
        }
    }
    
    /**
     * Logs unified workout creation event
     */
    suspend fun logWorkoutCreationEvent(
        workout: Workout,
        workoutType: String = "unified"
    ): Result<Unit> {
        if (workout.userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Workout must have a valid user ID"))
        }
        
        return analyticsService.logWorkoutCreationEvent(
            userId = workout.userId,
            workoutId = workout.id.value,
            workoutName = workout.name,
            workoutType = workoutType,
            exerciseCount = workout.exercises.size
        )
    }
    
    /**
     * Logs exercise selection event with method tracking
     */
    suspend fun logExerciseSelectionEvent(
        userId: String,
        exerciseId: String,
        exerciseName: String,
        selectionMethod: String
    ): Result<Unit> {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID must not be blank"))
        }
        
        return analyticsService.logExerciseSelectionEvent(
            userId = userId,
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            selectionMethod = selectionMethod
        )
    }
} 