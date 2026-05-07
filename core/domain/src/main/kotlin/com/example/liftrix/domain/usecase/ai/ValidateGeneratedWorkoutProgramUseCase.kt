package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ai.GeneratedPrescriptionType
import com.example.liftrix.domain.model.ai.GeneratedWorkoutExercise
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.ai.ModifiedWorkoutProgramResponse
import com.example.liftrix.domain.model.ai.WorkoutModificationSignificance
import com.example.liftrix.domain.model.ai.WorkoutGenerationRequest
import com.example.liftrix.domain.model.ai.WorkoutGenerationValidationResult
import com.example.liftrix.domain.model.ai.WorkoutProgramLevel
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import javax.inject.Inject
import kotlin.math.roundToInt

class ValidateGeneratedWorkoutProgramUseCase @Inject constructor() {

    operator fun invoke(
        program: GeneratedWorkoutProgram,
        request: WorkoutGenerationRequest,
        exerciseCatalog: List<ExerciseLibrary>
    ): LiftrixResult<WorkoutGenerationValidationResult> {
        val violations = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val catalogById = exerciseCatalog.associateBy { it.id }

        validateProgramShape(program, request, violations)
        program.days.forEachIndexed { dayIndex, day ->
            if (day.exercises.size !in MIN_EXERCISES_PER_DAY..MAX_EXERCISES_PER_DAY) {
                violations.add("days[$dayIndex].exercises must contain $MIN_EXERCISES_PER_DAY-$MAX_EXERCISES_PER_DAY exercises")
            }
            day.exercises.forEachIndexed { exerciseIndex, exercise ->
                validateExercise(
                    exercise = exercise,
                    path = "days[$dayIndex].exercises[$exerciseIndex]",
                    request = request,
                    catalogById = catalogById,
                    violations = violations,
                    warnings = warnings
                )
            }
        }
        validateWeeklyBalance(program, violations, warnings)

        return if (violations.isEmpty()) {
            Result.success(WorkoutGenerationValidationResult(program, warnings))
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "generated_workout_program",
                    violations = violations,
                    errorMessage = violations.joinToString("; "),
                    analyticsContext = mapOf("operation" to "VALIDATE_GENERATED_WORKOUT_PROGRAM")
                )
            )
        }
    }

    fun validateModification(
        response: ModifiedWorkoutProgramResponse,
        sourceProgram: GeneratedWorkoutProgram,
        sourceId: String,
        request: WorkoutGenerationRequest,
        exerciseCatalog: List<ExerciseLibrary>
    ): LiftrixResult<WorkoutGenerationValidationResult> {
        val violations = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (response.source.sourceId != sourceId) {
            violations.add("source.source_id must preserve the resolved source")
        }
        if (response.source.sourceName.isBlank()) {
            violations.add("source.source_name is required")
        }
        if (response.changes.isEmpty()) {
            violations.add("changes must include at least one change summary")
        }
        response.changes.forEachIndexed { index, change ->
            if (change.before.isBlank()) violations.add("changes[$index].before is required")
            if (change.after.isBlank()) violations.add("changes[$index].after is required")
            if (change.reason.isBlank() || change.reason.length > MAX_CHANGE_REASON_LENGTH) {
                violations.add("changes[$index].reason must be 1-$MAX_CHANGE_REASON_LENGTH characters")
            }
        }
        response.optionalQuestion?.let { question ->
            if (question.length > MAX_OPTIONAL_QUESTION_LENGTH) {
                violations.add("optional_question must be $MAX_OPTIONAL_QUESTION_LENGTH characters or fewer")
            }
            if (UNSAFE_TERMS.containsMatchIn(question)) {
                violations.add("optional_question contains unsafe programming")
            }
        }
        if (response.significance == WorkoutModificationSignificance.SIGNIFICANT && !response.confirmationRequired) {
            violations.add("significant modifications require confirmation")
        }

        val baseValidation = invoke(response.program, request, exerciseCatalog)
        baseValidation.fold(
            onSuccess = { warnings.addAll(it.warnings) },
            onFailure = { error ->
                if (error is LiftrixError.ValidationError) {
                    violations.add(error.errorMessage.ifBlank { error.violations.joinToString("; ") })
                } else {
                    violations.add(error.message ?: "Modified program failed validation")
                }
            }
        )

        validateSafeProgression(sourceProgram, response.program, request.normalizedConstraints.level, violations, warnings)

        return if (violations.isEmpty()) {
            Result.success(WorkoutGenerationValidationResult(response.program, warnings))
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "modified_workout_program",
                    violations = violations,
                    errorMessage = violations.joinToString("; "),
                    analyticsContext = mapOf("operation" to "VALIDATE_MODIFIED_WORKOUT_PROGRAM")
                )
            )
        }
    }

    private fun validateProgramShape(
        program: GeneratedWorkoutProgram,
        request: WorkoutGenerationRequest,
        violations: MutableList<String>
    ) {
        if (program.schemaVersion != GeneratedWorkoutProgram.SCHEMA_VERSION) {
            violations.add("schema_version must be ${GeneratedWorkoutProgram.SCHEMA_VERSION}")
        }
        if (program.workoutName.isBlank() || program.workoutName.length > 100) {
            violations.add("workout_name must be 1-100 characters")
        }
        if (program.days.size != request.normalizedConstraints.daysPerWeek) {
            violations.add("days count must match requested days_per_week")
        }
        if (program.days.size !in MIN_DAYS_PER_WEEK..MAX_DAYS_PER_WEEK) {
            violations.add("days count must be $MIN_DAYS_PER_WEEK-$MAX_DAYS_PER_WEEK")
        }
    }

    private fun validateExercise(
        exercise: GeneratedWorkoutExercise,
        path: String,
        request: WorkoutGenerationRequest,
        catalogById: Map<String, ExerciseLibrary>,
        violations: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val catalogExercise = catalogById[exercise.exerciseId]
        if (catalogExercise == null) {
            violations.add("$path.exercise_id is not in the exercise catalog")
        } else {
            if (exercise.exerciseName != catalogExercise.name) violations.add("$path.exercise_name must match catalog")
            if (exercise.primaryMuscle != catalogExercise.primaryMuscleGroup) violations.add("$path.primary_muscle must match catalog")
            if (exercise.equipment != catalogExercise.equipment) violations.add("$path.equipment must match catalog")
            if (request.normalizedConstraints.level == WorkoutProgramLevel.BEGINNER && catalogExercise.difficultyLevel > 3) {
                violations.add("$path uses an exercise above beginner difficulty")
            }
            if (exercise.isUnilateral && !canBeUnilateral(catalogExercise)) {
                warnings.add("$path is marked unilateral but catalog movement is not clearly unilateral")
            }
        }

        if (exercise.equipment !in request.normalizedConstraints.allowedEquipment) {
            violations.add("$path uses forbidden equipment ${exercise.equipment}")
        }
        if (exercise.equipment in request.normalizedConstraints.excludedEquipment) {
            violations.add("$path uses excluded equipment ${exercise.equipment}")
        }
        if (exercise.sets !in 1..5) violations.add("$path.sets must be 1-5")
        if (request.normalizedConstraints.level == WorkoutProgramLevel.BEGINNER && exercise.sets > 3) {
            violations.add("$path beginner sets must be 1-3")
        }
        if (exercise.restSeconds !in 30..180) violations.add("$path.rest_seconds must be 30-180")
        exercise.notes?.let {
            if (it.length > 240) violations.add("$path.notes must be 240 characters or fewer")
            if (UNSAFE_TERMS.containsMatchIn(it)) violations.add("$path.notes contain unsafe programming")
        }
        validatePrescription(exercise, path, request, violations)
    }

    private fun validatePrescription(
        exercise: GeneratedWorkoutExercise,
        path: String,
        request: WorkoutGenerationRequest,
        violations: MutableList<String>
    ) {
        when (exercise.type) {
            GeneratedPrescriptionType.REPS -> {
                val min = exercise.repsMin
                val max = exercise.repsMax
                if (min == null || max == null) {
                    violations.add("$path REPS prescription requires reps_min and reps_max")
                } else {
                    if (min !in 1..30 || max !in 1..30 || min > max) {
                        violations.add("$path reps range must be ordered and within 1-30")
                    }
                    if (request.normalizedConstraints.level == WorkoutProgramLevel.BEGINNER && (min < 8 || max > 15)) {
                        violations.add("$path beginner reps must stay within 8-15")
                    }
                }
                if (exercise.durationSeconds != null) violations.add("$path REPS prescription requires duration_seconds null")
            }
            GeneratedPrescriptionType.TIME -> {
                if (exercise.repsMin != null || exercise.repsMax != null) {
                    violations.add("$path TIME prescription requires reps_min and reps_max null")
                }
                if (exercise.durationSeconds !in 10..300) {
                    violations.add("$path duration_seconds must be 10-300")
                }
            }
        }
    }

    private fun validateWeeklyBalance(
        program: GeneratedWorkoutProgram,
        violations: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val setsByMuscle = program.days
            .flatMap { it.exercises }
            .groupBy { normalizeMuscleGroup(it.primaryMuscle) }
            .mapValues { (_, exercises) -> exercises.sumOf { it.sets } }

        if (setsByMuscle.isEmpty()) return
        val totalSets = setsByMuscle.values.sum()
        val dominanceLimit = (totalSets * MAX_MUSCLE_DOMINANCE_RATIO).roundToInt().coerceAtLeast(1)
        val dominant = setsByMuscle.filterValues { it > dominanceLimit }
        if (dominant.isNotEmpty()) {
            warnings.add("weekly muscle balance favors ${dominant.keys.joinToString()}")
        }
    }

    private fun validateSafeProgression(
        sourceProgram: GeneratedWorkoutProgram,
        modifiedProgram: GeneratedWorkoutProgram,
        level: WorkoutProgramLevel,
        violations: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val sourceExercises = sourceProgram.days.flatMap { it.exercises }
        val modifiedExercises = modifiedProgram.days.flatMap { it.exercises }
        if (sourceExercises.isEmpty() || modifiedExercises.isEmpty()) return

        val sourceSets = sourceExercises.sumOf { it.sets }
        val modifiedSets = modifiedExercises.sumOf { it.sets }
        val sourceVolume = sourceExercises.sumOf { it.sets * (it.repsMax ?: it.repsMin ?: 1) }
        val modifiedVolume = modifiedExercises.sumOf { it.sets * (it.repsMax ?: it.repsMin ?: 1) }
        val maxSetIncrease = when (level) {
            WorkoutProgramLevel.BEGINNER -> 2
            WorkoutProgramLevel.INTERMEDIATE -> 4
            WorkoutProgramLevel.ADVANCED -> 6
        }
        val maxVolumeRatio = when (level) {
            WorkoutProgramLevel.BEGINNER -> 1.20
            WorkoutProgramLevel.INTERMEDIATE -> 1.30
            WorkoutProgramLevel.ADVANCED -> 1.40
        }

        if (modifiedSets - sourceSets > maxSetIncrease) {
            violations.add("modified weekly sets increase too aggressively for $level")
        }
        if (sourceVolume > 0 && modifiedVolume > sourceVolume * maxVolumeRatio) {
            violations.add("modified weekly volume increase is unsafe for $level")
        }
        if (modifiedExercises.size < sourceExercises.size / 2) {
            warnings.add("modified program removes more than half of source exercises")
        }
    }

    private fun normalizeMuscleGroup(category: ExerciseCategory): ExerciseCategory = when (category) {
        ExerciseCategory.BICEPS, ExerciseCategory.TRICEPS -> ExerciseCategory.ARMS
        ExerciseCategory.QUADRICEPS, ExerciseCategory.HAMSTRINGS, ExerciseCategory.GLUTES, ExerciseCategory.CALVES -> ExerciseCategory.LEGS
        ExerciseCategory.ABS -> ExerciseCategory.CORE
        else -> category
    }

    private fun canBeUnilateral(exercise: ExerciseLibrary): Boolean {
        val text = "${exercise.name} ${exercise.movementPattern}".lowercase()
        return listOf("single", "one-arm", "one arm", "one-leg", "one leg", "lunge", "split", "side").any { text.contains(it) }
    }

    companion object {
        private const val MIN_DAYS_PER_WEEK = 1
        private const val MAX_DAYS_PER_WEEK = 6
        private const val MIN_EXERCISES_PER_DAY = 1
        private const val MAX_EXERCISES_PER_DAY = 8
        private const val MAX_MUSCLE_DOMINANCE_RATIO = 0.65
        private const val MAX_CHANGE_REASON_LENGTH = 240
        private const val MAX_OPTIONAL_QUESTION_LENGTH = 240
        private val UNSAFE_TERMS = Regex("\\b(max out|one rep max|through pain|ignore pain|rehab|diagnose)\\b")
    }
}
