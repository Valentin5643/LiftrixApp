package com.example.liftrix.domain.usecase.import

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.portability.ParsedWorkout
import com.example.liftrix.domain.model.portability.ImportValidationError
import com.example.liftrix.domain.model.portability.ErrorSeverity
import com.example.liftrix.domain.service.ExerciseMappingService
import java.time.LocalDateTime
import javax.inject.Inject

class ValidateImportUseCase @Inject constructor(
    private val exerciseMappingService: ExerciseMappingService
) {
    
    suspend fun invoke(
        workouts: List<ParsedWorkout>,
        userId: String
    ): LiftrixResult<ImportValidationResult> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "IMPORT_VALIDATION_FAILED",
                errorMessage = "Failed to validate import data",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "workout_count" to workouts.size.toString(),
                    "operation" to "IMPORT_VALIDATION",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        val errors = mutableListOf<ImportValidationError>()
        val warnings = mutableListOf<ImportValidationError>()
        var validWorkouts = 0
        var validExercises = 0
        var validSets = 0
        val unmappedExercises = mutableSetOf<String>()
        
        for ((workoutIndex, workout) in workouts.withIndex()) {
            val workoutErrors = validateWorkout(workout, workoutIndex)
            errors.addAll(workoutErrors.filter { it.severity == ErrorSeverity.ERROR || it.severity == ErrorSeverity.CRITICAL })
            warnings.addAll(workoutErrors.filter { it.severity == ErrorSeverity.WARNING })
            
            if (workoutErrors.none { it.severity == ErrorSeverity.CRITICAL }) {
                validWorkouts++
                
                for ((exerciseIndex, exercise) in workout.exercises.withIndex()) {
                    val exerciseErrors = validateExercise(exercise, workoutIndex, exerciseIndex)
                    errors.addAll(exerciseErrors.filter { it.severity == ErrorSeverity.ERROR || it.severity == ErrorSeverity.CRITICAL })
                    warnings.addAll(exerciseErrors.filter { it.severity == ErrorSeverity.WARNING })
                    
                    // Check exercise mapping
                    val mappingResult = exerciseMappingService.mapExerciseName(exercise.name)
                    mappingResult.fold(
                        onSuccess = { mappedId ->
                            if (mappedId == null) {
                                unmappedExercises.add(exercise.name)
                                warnings.add(
                                    ImportValidationError(
                                        field = "workout[$workoutIndex].exercise[$exerciseIndex].name",
                                        message = "Exercise '${exercise.name}' could not be mapped to existing exercise library",
                                        severity = ErrorSeverity.WARNING
                                    )
                                )
                            }
                        },
                        onFailure = {
                            errors.add(
                                ImportValidationError(
                                    field = "workout[$workoutIndex].exercise[$exerciseIndex].name",
                                    message = "Failed to validate exercise mapping for '${exercise.name}'",
                                    severity = ErrorSeverity.ERROR
                                )
                            )
                        }
                    )
                    
                    if (exerciseErrors.none { it.severity == ErrorSeverity.CRITICAL }) {
                        validExercises++
                        
                        for ((setIndex, set) in exercise.sets.withIndex()) {
                            val setErrors = validateSet(set, workoutIndex, exerciseIndex, setIndex)
                            errors.addAll(setErrors.filter { it.severity == ErrorSeverity.ERROR || it.severity == ErrorSeverity.CRITICAL })
                            warnings.addAll(setErrors.filter { it.severity == ErrorSeverity.WARNING })
                            
                            if (setErrors.none { it.severity == ErrorSeverity.CRITICAL }) {
                                validSets++
                            }
                        }
                    }
                }
            }
        }
        
        // Validate overall import constraints
        val globalErrors = validateGlobalConstraints(workouts)
        errors.addAll(globalErrors.filter { it.severity == ErrorSeverity.ERROR || it.severity == ErrorSeverity.CRITICAL })
        warnings.addAll(globalErrors.filter { it.severity == ErrorSeverity.WARNING })
        
        ImportValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            totalWorkouts = workouts.size,
            validWorkouts = validWorkouts,
            totalExercises = workouts.sumOf { it.exercises.size },
            validExercises = validExercises,
            totalSets = workouts.sumOf { workout -> workout.exercises.sumOf { it.sets.size } },
            validSets = validSets,
            unmappedExercises = unmappedExercises.toList()
        )
    }
    
    private fun validateWorkout(workout: ParsedWorkout, index: Int): List<ImportValidationError> {
        val errors = mutableListOf<ImportValidationError>()
        
        // Validate workout name
        if (workout.name.isBlank()) {
            errors.add(
                ImportValidationError(
                    field = "workout[$index].name",
                    message = "Workout name cannot be empty",
                    severity = ErrorSeverity.ERROR
                )
            )
        } else if (workout.name.length > 200) {
            errors.add(
                ImportValidationError(
                    field = "workout[$index].name",
                    message = "Workout name too long (max 200 characters)",
                    severity = ErrorSeverity.ERROR
                )
            )
        }
        
        // Validate workout date
        if (workout.date.isAfter(LocalDateTime.now().plusDays(1))) {
            errors.add(
                ImportValidationError(
                    field = "workout[$index].date",
                    message = "Workout date cannot be in the future",
                    severity = ErrorSeverity.ERROR
                )
            )
        }
        
        if (workout.date.isBefore(LocalDateTime.now().minusYears(10))) {
            errors.add(
                ImportValidationError(
                    field = "workout[$index].date",
                    message = "Workout date is too far in the past (>10 years)",
                    severity = ErrorSeverity.WARNING
                )
            )
        }
        
        // Validate duration
        workout.duration?.let { duration ->
            if (duration <= 0) {
                errors.add(
                    ImportValidationError(
                        field = "workout[$index].duration",
                        message = "Workout duration must be positive",
                        severity = ErrorSeverity.ERROR
                    )
                )
            } else if (duration > 86400) { // More than 24 hours
                errors.add(
                    ImportValidationError(
                        field = "workout[$index].duration",
                        message = "Workout duration exceeds 24 hours",
                        severity = ErrorSeverity.WARNING
                    )
                )
            }
        }
        
        // Validate exercises
        if (workout.exercises.isEmpty()) {
            errors.add(
                ImportValidationError(
                    field = "workout[$index].exercises",
                    message = "Workout must contain at least one exercise",
                    severity = ErrorSeverity.CRITICAL
                )
            )
        } else if (workout.exercises.size > 50) {
            errors.add(
                ImportValidationError(
                    field = "workout[$index].exercises",
                    message = "Workout contains unusually many exercises (${workout.exercises.size})",
                    severity = ErrorSeverity.WARNING
                )
            )
        }
        
        return errors
    }
    
    private fun validateExercise(exercise: com.example.liftrix.domain.model.portability.ParsedExercise, workoutIndex: Int, exerciseIndex: Int): List<ImportValidationError> {
        val errors = mutableListOf<ImportValidationError>()
        
        // Validate exercise name
        if (exercise.name.isBlank()) {
            errors.add(
                ImportValidationError(
                    field = "workout[$workoutIndex].exercise[$exerciseIndex].name",
                    message = "Exercise name cannot be empty",
                    severity = ErrorSeverity.CRITICAL
                )
            )
        } else if (exercise.name.length > 100) {
            errors.add(
                ImportValidationError(
                    field = "workout[$workoutIndex].exercise[$exerciseIndex].name",
                    message = "Exercise name too long (max 100 characters)",
                    severity = ErrorSeverity.ERROR
                )
            )
        }
        
        // Validate sets
        if (exercise.sets.isEmpty()) {
            errors.add(
                ImportValidationError(
                    field = "workout[$workoutIndex].exercise[$exerciseIndex].sets",
                    message = "Exercise must contain at least one set",
                    severity = ErrorSeverity.ERROR
                )
            )
        } else if (exercise.sets.size > 100) {
            errors.add(
                ImportValidationError(
                    field = "workout[$workoutIndex].exercise[$exerciseIndex].sets",
                    message = "Exercise contains unusually many sets (${exercise.sets.size})",
                    severity = ErrorSeverity.WARNING
                )
            )
        }
        
        // Validate rest time
        exercise.restTime?.let { restTime ->
            if (restTime < 0) {
                errors.add(
                    ImportValidationError(
                        field = "workout[$workoutIndex].exercise[$exerciseIndex].restTime",
                        message = "Rest time cannot be negative",
                        severity = ErrorSeverity.ERROR
                    )
                )
            } else if (restTime > 3600) { // More than 1 hour
                errors.add(
                    ImportValidationError(
                        field = "workout[$workoutIndex].exercise[$exerciseIndex].restTime",
                        message = "Rest time exceeds 1 hour",
                        severity = ErrorSeverity.WARNING
                    )
                )
            }
        }
        
        return errors
    }
    
    private fun validateSet(set: com.example.liftrix.domain.model.portability.ParsedSet, workoutIndex: Int, exerciseIndex: Int, setIndex: Int): List<ImportValidationError> {
        val errors = mutableListOf<ImportValidationError>()
        
        // Validate that at least one metric is present
        if (set.reps == null && set.weight == null && set.distance == null && set.duration == null) {
            errors.add(
                ImportValidationError(
                    field = "workout[$workoutIndex].exercise[$exerciseIndex].set[$setIndex]",
                    message = "Set must have at least one metric (reps, weight, distance, or duration)",
                    severity = ErrorSeverity.ERROR
                )
            )
        }
        
        // Validate reps
        set.reps?.let { reps ->
            if (reps <= 0) {
                errors.add(
                    ImportValidationError(
                        field = "workout[$workoutIndex].exercise[$exerciseIndex].set[$setIndex].reps",
                        message = "Reps must be positive",
                        severity = ErrorSeverity.ERROR
                    )
                )
            } else if (reps > 1000) {
                errors.add(
                    ImportValidationError(
                        field = "workout[$workoutIndex].exercise[$exerciseIndex].set[$setIndex].reps",
                        message = "Reps value seems unrealistic (${reps})",
                        severity = ErrorSeverity.WARNING
                    )
                )
            }
        }
        
        // Validate weight
        set.weight?.let { weight ->
            if (weight <= 0) {
                errors.add(
                    ImportValidationError(
                        field = "workout[$workoutIndex].exercise[$exerciseIndex].set[$setIndex].weight",
                        message = "Weight must be positive",
                        severity = ErrorSeverity.ERROR
                    )
                )
            } else if (weight > 1000) { // More than 1000kg
                errors.add(
                    ImportValidationError(
                        field = "workout[$workoutIndex].exercise[$exerciseIndex].set[$setIndex].weight",
                        message = "Weight value seems unrealistic (${weight}kg)",
                        severity = ErrorSeverity.WARNING
                    )
                )
            }
        }
        
        // Validate distance
        set.distance?.let { distance ->
            if (distance <= 0) {
                errors.add(
                    ImportValidationError(
                        field = "workout[$workoutIndex].exercise[$exerciseIndex].set[$setIndex].distance",
                        message = "Distance must be positive",
                        severity = ErrorSeverity.ERROR
                    )
                )
            } else if (distance > 100000) { // More than 100km
                errors.add(
                    ImportValidationError(
                        field = "workout[$workoutIndex].exercise[$exerciseIndex].set[$setIndex].distance",
                        message = "Distance value seems unrealistic (${distance}m)",
                        severity = ErrorSeverity.WARNING
                    )
                )
            }
        }
        
        // Validate duration
        set.duration?.let { duration ->
            if (duration <= 0) {
                errors.add(
                    ImportValidationError(
                        field = "workout[$workoutIndex].exercise[$exerciseIndex].set[$setIndex].duration",
                        message = "Duration must be positive",
                        severity = ErrorSeverity.ERROR
                    )
                )
            } else if (duration > 86400) { // More than 24 hours
                errors.add(
                    ImportValidationError(
                        field = "workout[$workoutIndex].exercise[$exerciseIndex].set[$setIndex].duration",
                        message = "Duration exceeds 24 hours",
                        severity = ErrorSeverity.WARNING
                    )
                )
            }
        }
        
        return errors
    }
    
    private fun validateGlobalConstraints(workouts: List<ParsedWorkout>): List<ImportValidationError> {
        val errors = mutableListOf<ImportValidationError>()
        
        // Check for excessive data size
        val totalSets = workouts.sumOf { workout -> workout.exercises.sumOf { it.sets.size } }
        if (totalSets > 50000) {
            errors.add(
                ImportValidationError(
                    field = "global",
                    message = "Import contains too many sets (${totalSets}). Maximum recommended: 50,000",
                    severity = ErrorSeverity.ERROR
                )
            )
        }
        
        // Check for duplicate workouts (same date and name)
        val duplicates = workouts
            .groupBy { "${it.date.toLocalDate()}_${it.name}" }
            .filter { it.value.size > 1 }
        
        if (duplicates.isNotEmpty()) {
            errors.add(
                ImportValidationError(
                    field = "global",
                    message = "Found ${duplicates.size} groups of duplicate workouts (same date and name)",
                    severity = ErrorSeverity.WARNING
                )
            )
        }
        
        // Check date range
        val dates = workouts.map { it.date }
        if (dates.isNotEmpty()) {
            val earliestDate = dates.minOrNull()!!
            val latestDate = dates.maxOrNull()!!
            
            if (latestDate.isAfter(LocalDateTime.now().plusDays(1))) {
                errors.add(
                    ImportValidationError(
                        field = "global",
                        message = "Some workouts have future dates",
                        severity = ErrorSeverity.ERROR
                    )
                )
            }
            
            if (earliestDate.isBefore(LocalDateTime.now().minusYears(20))) {
                errors.add(
                    ImportValidationError(
                        field = "global",
                        message = "Some workouts are more than 20 years old",
                        severity = ErrorSeverity.WARNING
                    )
                )
            }
        }
        
        return errors
    }
}

data class ImportValidationResult(
    val isValid: Boolean,
    val errors: List<ImportValidationError>,
    val warnings: List<ImportValidationError>,
    val totalWorkouts: Int,
    val validWorkouts: Int,
    val totalExercises: Int,
    val validExercises: Int,
    val totalSets: Int,
    val validSets: Int,
    val unmappedExercises: List<String>
)