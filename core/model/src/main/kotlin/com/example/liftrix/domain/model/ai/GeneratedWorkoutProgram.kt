package com.example.liftrix.domain.model.ai

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GeneratedWorkoutProgram(
    @SerialName("schema_version")
    val schemaVersion: String = SCHEMA_VERSION,
    @SerialName("workout_name")
    @JsonNames("program_name")
    val workoutName: String,
    val description: String = "",
    val goal: WorkoutProgramGoal,
    val level: WorkoutProgramLevel,
    val days: List<GeneratedWorkoutDay>
) {
    companion object {
        const val SCHEMA_VERSION = "2.0"
    }
}

@Serializable
data class GeneratedWorkoutProgramResponse(
    @SerialName("schema_version")
    val schemaVersion: String? = null,
    val program: GeneratedWorkoutProgram
)

@Serializable
data class ModifiedWorkoutProgramResponse(
    @SerialName("schema_version")
    val schemaVersion: String = GeneratedWorkoutProgram.SCHEMA_VERSION,
    val task: WorkoutProgramAiTask = WorkoutProgramAiTask.MODIFY_WORKOUT_PROGRAM,
    val source: WorkoutProgramSourceReference,
    val program: GeneratedWorkoutProgram,
    val changes: List<WorkoutProgramChangeSummary>,
    val significance: WorkoutModificationSignificance = WorkoutModificationSignificance.MINOR,
    @SerialName("confirmation_required")
    val confirmationRequired: Boolean = true,
    @SerialName("optional_question")
    val optionalQuestion: String? = null
)

@Serializable
data class WorkoutProgramSourceReference(
    @SerialName("source_type")
    val sourceType: WorkoutProgramSourceType,
    @SerialName("source_id")
    val sourceId: String,
    @SerialName("source_name")
    val sourceName: String
)

@Serializable
enum class WorkoutProgramSourceType {
    @SerialName("template")
    TEMPLATE,
    @SerialName("generated_preview")
    GENERATED_PREVIEW
}

@Serializable
data class WorkoutProgramChangeSummary(
    val type: WorkoutProgramChangeType,
    @SerialName("exercise_id")
    val exerciseId: String? = null,
    val before: String,
    val after: String,
    val reason: String
)

@Serializable
enum class WorkoutProgramChangeType {
    @SerialName("sets")
    SETS,
    @SerialName("reps")
    REPS,
    @SerialName("rest")
    REST,
    @SerialName("exercise_substitution")
    EXERCISE_SUBSTITUTION,
    @SerialName("order")
    ORDER,
    @SerialName("notes")
    NOTES,
    @SerialName("difficulty")
    DIFFICULTY,
    @SerialName("duration")
    DURATION
}

@Serializable
enum class WorkoutModificationSignificance {
    @SerialName("minor")
    MINOR,
    @SerialName("significant")
    SIGNIFICANT
}

@Serializable
enum class WorkoutProgramAiTask {
    @SerialName("generate_workout_program")
    GENERATE_WORKOUT_PROGRAM,
    @SerialName("modify_workout_program")
    MODIFY_WORKOUT_PROGRAM,
    @SerialName("update_plan_from_progress")
    UPDATE_PLAN_FROM_PROGRESS
}

enum class WorkoutModificationSaveMode {
    COPY,
    OVERWRITE
}

@Serializable
data class GeneratedWorkoutDay(
    @SerialName("day_name")
    val dayName: String,
    @SerialName("scheduled_day")
    val scheduledDay: WorkoutTrainingDay? = null,
    val focus: String = "",
    @SerialName("estimated_duration_minutes")
    val estimatedDurationMinutes: Int,
    @SerialName("warm_up")
    val warmUp: GeneratedWorkoutPhase = GeneratedWorkoutPhase(),
    val exercises: List<GeneratedWorkoutExercise>,
    @SerialName("cool_down")
    val coolDown: GeneratedWorkoutPhase = GeneratedWorkoutPhase()
)

@Serializable
data class GeneratedWorkoutPhase(
    @SerialName("duration_minutes")
    val durationMinutes: Int = 0,
    val steps: List<String> = emptyList()
)

@Serializable
data class GeneratedWorkoutExercise(
    @SerialName("exercise_id")
    val exerciseId: String,
    @SerialName("exercise_name")
    val exerciseName: String,
    @SerialName("primary_muscle")
    val primaryMuscle: ExerciseCategory,
    val equipment: Equipment,
    val sets: Int,
    val type: GeneratedPrescriptionType,
    @SerialName("reps_min")
    val repsMin: Int? = null,
    @SerialName("reps_max")
    val repsMax: Int? = null,
    @SerialName("duration_seconds")
    val durationSeconds: Int? = null,
    @SerialName("is_unilateral")
    val isUnilateral: Boolean,
    @SerialName("rest_seconds")
    val restSeconds: Int,
    val notes: String? = null
)

@Serializable
enum class GeneratedPrescriptionType {
    REPS,
    TIME
}

@Serializable
enum class WorkoutProgramGoal {
    @SerialName("fat_loss")
    FAT_LOSS,
    @SerialName("hypertrophy")
    HYPERTROPHY,
    @SerialName("strength")
    STRENGTH,
    @SerialName("endurance")
    ENDURANCE,
    @SerialName("general_fitness")
    GENERAL_FITNESS
}

@Serializable
enum class WorkoutProgramLevel {
    @SerialName("beginner")
    BEGINNER,
    @SerialName("intermediate")
    INTERMEDIATE,
    @SerialName("advanced")
    ADVANCED
}

@Serializable
data class GeneratedWorkoutProgramError(
    @SerialName("schema_version")
    val schemaVersion: String = GeneratedWorkoutProgram.SCHEMA_VERSION,
    val error: GeneratedWorkoutErrorBody
)

@Serializable
data class GeneratedWorkoutErrorBody(
    val code: GeneratedWorkoutErrorCode,
    val message: String
)

@Serializable
enum class GeneratedWorkoutErrorCode {
    UNSATISFIABLE_REQUEST,
    UNSAFE_REQUEST,
    NEEDS_CLARIFICATION
}
