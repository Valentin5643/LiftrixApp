package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutStatus
import java.time.LocalDate

/**
 * Domain service for workout validation logic.
 *
 * Centralizes workout validation rules previously duplicated across:
 * - CreateWorkoutUseCase
 * - UpdateWorkoutSessionUseCase
 * - CreateWorkoutWithExercisesUseCase
 * - SaveWorkoutUseCase
 *
 * Provides comprehensive validation for workout entities with consistent error handling.
 */
interface WorkoutValidationService {

    /**
     * Validates workout name against business rules.
     *
     * Rules:
     * - Cannot be blank or empty
     * - Cannot exceed MAX_NAME_LENGTH (100 characters)
     * - Must not contain special characters that break database constraints
     *
     * @param name Workout name to validate
     * @return LiftrixResult with validated name or ValidationError
     */
    fun validateWorkoutName(name: String): LiftrixResult<String>

    /**
     * Validates workout date against business rules.
     *
     * Rules:
     * - Cannot be null
     * - Cannot be in the future (max: today)
     * - Cannot be older than 2 years (configurable)
     *
     * @param date Workout date to validate
     * @return LiftrixResult with validated date or ValidationError
     */
    fun validateWorkoutDate(date: LocalDate): LiftrixResult<LocalDate>

    /**
     * Validates exercise list based on workout status.
     *
     * Rules:
     * - For COMPLETED workouts: Must have at least 1 exercise with completed sets
     * - For ACTIVE workouts: Must have at least 1 exercise (can be incomplete)
     * - For PLANNED workouts: Can be empty (template scenario)
     * - Cannot exceed MAX_EXERCISES_PER_WORKOUT (50)
     * - All exercises must have valid exercise IDs
     * - No duplicate exercise IDs in the same workout
     *
     * @param exercises List of exercises to validate
     * @param status Workout status (affects validation rules)
     * @return LiftrixResult with validated exercises or ValidationError
     */
    fun validateExerciseList(exercises: List<Exercise>, status: WorkoutStatus): LiftrixResult<List<Exercise>>

    /**
     * Validates workout notes.
     *
     * Rules:
     * - Optional (null/blank is valid)
     * - Cannot exceed MAX_NOTES_LENGTH (500 characters)
     * - Must not contain special characters that break database constraints
     *
     * @param notes Workout notes to validate
     * @return LiftrixResult with validated notes or ValidationError
     */
    fun validateWorkoutNotes(notes: String?): LiftrixResult<String?>

    /**
     * Validates complete workout entity with all business rules.
     *
     * Combines all validation methods for comprehensive workout validation.
     * Useful for create/update operations that need to validate the entire entity.
     *
     * Rules:
     * - All individual field validations must pass
     * - Total workout volume must be < 100,000 kg (sanity check for bad data)
     * - For COMPLETED workouts: Must have duration > 0
     *
     * @param workout Workout entity to validate
     * @return LiftrixResult with validated workout or ValidationError with all violations
     */
    fun validateWorkout(workout: Workout): LiftrixResult<Workout>

    /**
     * Validates workout status transition.
     *
     * Rules:
     * - PLANNED → ACTIVE: Always allowed
     * - PLANNED → COMPLETED: Not allowed (must go through ACTIVE)
     * - ACTIVE → COMPLETED: Allowed only if workout has completed sets
     * - ACTIVE → PLANNED: Not allowed (cannot revert to planned)
     * - COMPLETED → ACTIVE: Not allowed (cannot revert completed workout)
     * - COMPLETED → PLANNED: Not allowed
     *
     * @param currentStatus Current workout status
     * @param newStatus Target workout status
     * @param workout Workout entity (needed to check completed sets)
     * @return LiftrixResult with Unit or ValidationError with transition rules violation
     */
    fun validateStatusTransition(
        currentStatus: WorkoutStatus,
        newStatus: WorkoutStatus,
        workout: Workout
    ): LiftrixResult<Unit>

    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_NOTES_LENGTH = 500
        const val MAX_EXERCISES_PER_WORKOUT = 50
        const val MAX_WORKOUT_VOLUME_KG = 100_000.0
        const val MAX_WORKOUT_AGE_YEARS = 2L
    }
}
