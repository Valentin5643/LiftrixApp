package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutStatus
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

/**
 * Implementation of WorkoutValidationService.
 *
 * Provides comprehensive workout validation with detailed error messages
 * and analytics context for tracking validation failures.
 */
class WorkoutValidationServiceImpl @Inject constructor() : WorkoutValidationService {

    override fun validateWorkoutName(name: String): LiftrixResult<String> {
        val violations = mutableListOf<String>()

        if (name.isBlank()) {
            violations.add("Workout name is required")
        }

        if (name.length > WorkoutValidationService.MAX_NAME_LENGTH) {
            violations.add("Workout name cannot exceed ${WorkoutValidationService.MAX_NAME_LENGTH} characters")
        }

        // Check for special characters that break database constraints
        val invalidChars = Regex("[<>\"'%;()&+]")
        if (invalidChars.containsMatchIn(name)) {
            violations.add("Workout name contains invalid special characters")
        }

        return if (violations.isEmpty()) {
            Result.success(name.trim())
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "name",
                    violations = violations,
                    analyticsContext = mapOf("operation" to "VALIDATE_WORKOUT_NAME")
                )
            )
        }
    }

    override fun validateWorkoutDate(date: LocalDate): LiftrixResult<LocalDate> {
        val violations = mutableListOf<String>()
        val today = LocalDate.now()

        if (date.isAfter(today)) {
            violations.add("Workout date cannot be in the future")
        }

        val age = Period.between(date, today)
        if (age.years >= WorkoutValidationService.MAX_WORKOUT_AGE_YEARS) {
            violations.add("Workout date cannot be older than ${WorkoutValidationService.MAX_WORKOUT_AGE_YEARS} years")
        }

        return if (violations.isEmpty()) {
            Result.success(date)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "date",
                    violations = violations,
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_WORKOUT_DATE",
                        "date" to date.toString(),
                        "today" to today.toString()
                    )
                )
            )
        }
    }

    override fun validateExerciseList(
        exercises: List<Exercise>,
        status: WorkoutStatus
    ): LiftrixResult<List<Exercise>> {
        val violations = mutableListOf<String>()

        // Check exercise count
        when (status) {
            WorkoutStatus.COMPLETED -> {
                if (exercises.isEmpty()) {
                    violations.add("Completed workout must have at least 1 exercise")
                }
                val hasCompletedSets = exercises.any { exercise ->
                    exercise.sets.any { it.isCompleted }
                }
                if (!hasCompletedSets) {
                    violations.add("Completed workout must have at least 1 completed set")
                }
            }
            WorkoutStatus.IN_PROGRESS -> {
                if (exercises.isEmpty()) {
                    violations.add("In-progress workout must have at least 1 exercise")
                }
            }
            WorkoutStatus.PLANNED -> {
                // Planned workouts (templates) can be empty
            }
            WorkoutStatus.PAUSED -> {
                if (exercises.isEmpty()) {
                    violations.add("Paused workout must have at least 1 exercise")
                }
            }
            WorkoutStatus.CANCELLED -> {
                // Cancelled workouts can have any state
            }
        }

        // Check max exercises limit
        if (exercises.size > WorkoutValidationService.MAX_EXERCISES_PER_WORKOUT) {
            violations.add("Workout cannot have more than ${WorkoutValidationService.MAX_EXERCISES_PER_WORKOUT} exercises")
        }

        // Check for duplicate exercise IDs
        val exerciseIds = exercises.map { it.id }
        val duplicates = exerciseIds.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            violations.add("Workout contains duplicate exercises: ${duplicates.keys.joinToString()}")
        }

        // Validate individual exercises
        exercises.forEachIndexed { index, exercise ->
            if (exercise.id.value.isBlank()) {
                violations.add("Exercise at position ${index + 1} has invalid exercise ID")
            }

            if (exercise.sets.isEmpty() && status != WorkoutStatus.PLANNED) {
                violations.add("Exercise '${exercise.libraryExercise.name}' has no sets")
            }

            // Validate sets - check actual weight and reps values
            exercise.sets.forEachIndexed { setIndex, set ->
                // Weight validation (nullable, if present must be positive)
                set.weight?.let { weight ->
                    if (weight.kilograms < 0) {
                        violations.add("Exercise '${exercise.libraryExercise.name}' set ${setIndex + 1} has invalid weight")
                    }
                }

                // Reps validation (nullable, if present must be positive)
                set.reps?.let { reps ->
                    if (reps.count <= 0) {
                        violations.add("Exercise '${exercise.libraryExercise.name}' set ${setIndex + 1} has invalid reps")
                    }
                }
            }
        }

        return if (violations.isEmpty()) {
            Result.success(exercises)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "exercises",
                    violations = violations,
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_EXERCISE_LIST",
                        "status" to status.name,
                        "exercise_count" to exercises.size.toString()
                    )
                )
            )
        }
    }

    override fun validateWorkoutNotes(notes: String?): LiftrixResult<String?> {
        val violations = mutableListOf<String>()

        // Null or blank is valid
        if (notes.isNullOrBlank()) {
            return Result.success(notes)
        }

        if (notes.length > WorkoutValidationService.MAX_NOTES_LENGTH) {
            violations.add("Workout notes cannot exceed ${WorkoutValidationService.MAX_NOTES_LENGTH} characters")
        }

        // Check for special characters that break database constraints
        val invalidChars = Regex("[<>\"%;()&+]")
        if (invalidChars.containsMatchIn(notes)) {
            violations.add("Workout notes contain invalid special characters")
        }

        return if (violations.isEmpty()) {
            Result.success(notes.trim())
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "notes",
                    violations = violations,
                    analyticsContext = mapOf("operation" to "VALIDATE_WORKOUT_NOTES")
                )
            )
        }
    }

    override fun validateWorkout(workout: Workout): LiftrixResult<Workout> {
        val allViolations = mutableListOf<String>()

        // Validate name
        validateWorkoutName(workout.name).fold(
            onSuccess = { /* Valid */ },
            onFailure = { error ->
                if (error is LiftrixError.ValidationError) {
                    allViolations.addAll(error.violations)
                }
            }
        )

        // Validate date
        validateWorkoutDate(workout.date).fold(
            onSuccess = { /* Valid */ },
            onFailure = { error ->
                if (error is LiftrixError.ValidationError) {
                    allViolations.addAll(error.violations)
                }
            }
        )

        // Validate exercises
        validateExerciseList(workout.exercises, workout.status).fold(
            onSuccess = { /* Valid */ },
            onFailure = { error ->
                if (error is LiftrixError.ValidationError) {
                    allViolations.addAll(error.violations)
                }
            }
        )

        // Validate notes
        validateWorkoutNotes(workout.notes).fold(
            onSuccess = { /* Valid */ },
            onFailure = { error ->
                if (error is LiftrixError.ValidationError) {
                    allViolations.addAll(error.violations)
                }
            }
        )

        // Additional whole-workout validations
        if (workout.status == WorkoutStatus.COMPLETED) {
            val duration = workout.getDuration()
            if (duration == null || duration.isZero || duration.isNegative) {
                allViolations.add("Completed workout must have duration > 0")
            }
        }

        // Sanity check: Total volume should not exceed 100,000 kg
        val totalVolume = workout.calculateTotalVolume()
        if (totalVolume.kilograms > WorkoutValidationService.MAX_WORKOUT_VOLUME_KG) {
            allViolations.add("Total workout volume (${totalVolume.kilograms.toInt()} kg) exceeds maximum allowed (${WorkoutValidationService.MAX_WORKOUT_VOLUME_KG.toInt()} kg)")
        }

        return if (allViolations.isEmpty()) {
            Result.success(workout)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "workout",
                    violations = allViolations,
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_WORKOUT",
                        "workout_id" to workout.id.value,
                        "status" to workout.status.name,
                        "exercise_count" to workout.exercises.size.toString()
                    )
                )
            )
        }
    }

    override fun validateStatusTransition(
        currentStatus: WorkoutStatus,
        newStatus: WorkoutStatus,
        workout: Workout
    ): LiftrixResult<Unit> {
        val violations = mutableListOf<String>()

        // Define valid transitions
        val validTransitions = mapOf(
            WorkoutStatus.PLANNED to setOf(WorkoutStatus.IN_PROGRESS),
            WorkoutStatus.IN_PROGRESS to setOf(WorkoutStatus.PAUSED, WorkoutStatus.COMPLETED, WorkoutStatus.CANCELLED),
            WorkoutStatus.PAUSED to setOf(WorkoutStatus.IN_PROGRESS, WorkoutStatus.COMPLETED, WorkoutStatus.CANCELLED),
            WorkoutStatus.COMPLETED to emptySet(), // No transitions from COMPLETED
            WorkoutStatus.CANCELLED to emptySet() // No transitions from CANCELLED
        )

        if (currentStatus == newStatus) {
            // Same status is valid (no-op)
            return Result.success(Unit)
        }

        val allowedTransitions = validTransitions[currentStatus] ?: emptySet()
        if (newStatus !in allowedTransitions) {
            violations.add("Invalid status transition: ${currentStatus.name} → ${newStatus.name}")
        }

        // Additional validation for IN_PROGRESS → COMPLETED
        if (currentStatus == WorkoutStatus.IN_PROGRESS && newStatus == WorkoutStatus.COMPLETED) {
            val hasCompletedSets = workout.exercises.any { exercise ->
                exercise.sets.any { it.isCompleted }
            }
            if (!hasCompletedSets) {
                violations.add("Cannot mark workout as COMPLETED without at least 1 completed set")
            }
        }

        // Additional validation for PAUSED → COMPLETED
        if (currentStatus == WorkoutStatus.PAUSED && newStatus == WorkoutStatus.COMPLETED) {
            val hasCompletedSets = workout.exercises.any { exercise ->
                exercise.sets.any { it.isCompleted }
            }
            if (!hasCompletedSets) {
                violations.add("Cannot mark workout as COMPLETED without at least 1 completed set")
            }
        }

        return if (violations.isEmpty()) {
            Result.success(Unit)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "status",
                    violations = violations,
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_STATUS_TRANSITION",
                        "current_status" to currentStatus.name,
                        "new_status" to newStatus.name,
                        "workout_id" to workout.id.value
                    )
                )
            )
        }
    }
}
