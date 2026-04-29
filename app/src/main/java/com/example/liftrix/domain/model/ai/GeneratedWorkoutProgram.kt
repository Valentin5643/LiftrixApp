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
    val goal: WorkoutProgramGoal,
    val level: WorkoutProgramLevel,
    val days: List<GeneratedWorkoutDay>
) {
    companion object {
        const val SCHEMA_VERSION = "1.0"
    }
}

@Serializable
data class GeneratedWorkoutProgramResponse(
    @SerialName("schema_version")
    val schemaVersion: String? = null,
    val program: GeneratedWorkoutProgram
)

@Serializable
data class GeneratedWorkoutDay(
    @SerialName("day_name")
    val dayName: String,
    @SerialName("estimated_duration_minutes")
    val estimatedDurationMinutes: Int,
    val exercises: List<GeneratedWorkoutExercise>
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
