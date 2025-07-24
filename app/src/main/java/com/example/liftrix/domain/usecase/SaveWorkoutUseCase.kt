package com.example.liftrix.domain.usecase

import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.Workout
import timber.log.Timber
import javax.inject.Inject

class SaveWorkoutUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository
) {

    /**
     * Save a workout to local database and queue for sync
     * Validates that workout has a valid user ID before saving
     */
    suspend operator fun invoke(workout: Workout): Result<Unit> {
        return try {
            // Validate workout has userId
            if (workout.userId.isBlank()) {
                val error = IllegalArgumentException("Workout must have a valid user ID")
                Timber.e("Attempted to save workout without user ID: ${workout.id.value}")
                return Result.failure(error)
            }
            
            val result = workoutRepository.saveWorkout(workout)
            
            if (result.isSuccess) {
                Timber.d("Workout saved successfully: ${workout.id.value} for user: ${workout.userId}")
            } else {
                Timber.e("Failed to save workout: ${workout.id.value} for user: ${workout.userId}")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Exception while saving workout: ${workout.id.value}")
            Result.failure(e)
        }
    }
} 