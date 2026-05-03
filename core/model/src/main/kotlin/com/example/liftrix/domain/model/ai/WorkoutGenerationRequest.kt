package com.example.liftrix.domain.model.ai

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.service.Language
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutGenerationRequest(
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_prompt")
    val userPrompt: String,
    @SerialName("normalized_constraints")
    val normalizedConstraints: WorkoutGenerationConstraints,
    val personalization: WorkoutGenerationPersonalization = WorkoutGenerationPersonalization(),
    @SerialName("save_after_generation")
    val saveAfterGeneration: Boolean = false
)

@Serializable
data class WorkoutGenerationConstraints(
    @SerialName("days_per_week")
    val daysPerWeek: Int = 3,
    val level: WorkoutProgramLevel = WorkoutProgramLevel.BEGINNER,
    val goal: WorkoutProgramGoal = WorkoutProgramGoal.GENERAL_FITNESS,
    @SerialName("allowed_equipment")
    val allowedEquipment: Set<Equipment> = setOf(Equipment.BODYWEIGHT_ONLY),
    @SerialName("excluded_equipment")
    val excludedEquipment: Set<Equipment> = Equipment.entries.toSet() - allowedEquipment,
    @SerialName("session_duration_minutes")
    val sessionDurationMinutes: Int = 45,
    val language: Language = Language.ENGLISH
)

@Serializable
data class WorkoutGenerationPersonalization(
    @SerialName("age_band")
    val ageBand: String = "adult",
    @SerialName("profile_goals")
    val profileGoals: List<FitnessGoal> = emptyList(),
    @SerialName("profile_equipment")
    val profileEquipment: Set<Equipment> = emptySet(),
    @SerialName("experience_level")
    val experienceLevel: WorkoutProgramLevel = WorkoutProgramLevel.BEGINNER,
    @SerialName("known_exclusions")
    val knownExclusions: List<String> = emptyList()
)

@Serializable
data class WorkoutAiContextSnapshot(
    @SerialName("user_id")
    val userId: String,
    @SerialName("available_equipment")
    val availableEquipment: Set<Equipment> = setOf(Equipment.BODYWEIGHT_ONLY),
    @SerialName("other_equipment_summary")
    val otherEquipmentSummary: String? = null,
    @SerialName("fitness_goals")
    val fitnessGoals: List<FitnessGoal> = emptyList(),
    @SerialName("goal_priority")
    val goalPriority: Map<FitnessGoal, Int> = emptyMap(),
    @SerialName("age_band")
    val ageBand: String = "adult",
    @SerialName("profile_completion_percent")
    val profileCompletionPercent: Int = 0,
    @SerialName("weight_unit")
    val weightUnit: String = "system",
    @SerialName("experience_level")
    val experienceLevel: WorkoutProgramLevel = WorkoutProgramLevel.BEGINNER,
    @SerialName("recent_history")
    val recentHistory: WorkoutAiHistorySummary = WorkoutAiHistorySummary(),
    @SerialName("exercise_catalog")
    val exerciseCatalog: List<WorkoutGenerationCatalogExercise> = emptyList()
)

@Serializable
data class WorkoutAiHistorySummary(
    @SerialName("total_workouts")
    val totalWorkouts: Int = 0,
    @SerialName("current_streak")
    val currentStreak: Int = 0,
    @SerialName("completed_history_count")
    val completedHistoryCount: Int = 0,
    @SerialName("recent_exercise_count")
    val recentExerciseCount: Int = 0
)

@Serializable
data class WorkoutGenerationPrompt(
    @SerialName("system_prompt")
    val systemPrompt: String,
    @SerialName("input_payload")
    val inputPayload: String,
    @SerialName("catalog_hash")
    val catalogHash: String
)

@Serializable
data class WorkoutGenerationInputPayload(
    val task: String = "generate_workout_program",
    @SerialName("schema_version")
    val schemaVersion: String = GeneratedWorkoutProgram.SCHEMA_VERSION,
    @SerialName("user_prompt")
    val userPrompt: String,
    @SerialName("normalized_constraints")
    val normalizedConstraints: WorkoutGenerationConstraints,
    val personalization: WorkoutGenerationPersonalization,
    @SerialName("exercise_catalog")
    val exerciseCatalog: List<WorkoutGenerationCatalogExercise>,
    @SerialName("output_contract")
    val outputContract: String = "Return JSON as {schema_version, program}. Program must use workout_name and exercise_type, not program_name or type."
)

@Serializable
data class WorkoutGenerationCatalogExercise(
    @SerialName("exercise_id")
    val exerciseId: String,
    @SerialName("exercise_name")
    val exerciseName: String,
    @SerialName("primary_muscle")
    val primaryMuscle: ExerciseCategory,
    val equipment: Equipment,
    @SerialName("movement_pattern")
    val movementPattern: String,
    @SerialName("difficulty_level")
    val difficultyLevel: Int,
    @SerialName("is_compound")
    val isCompound: Boolean
) {
    companion object {
        fun fromExercise(exercise: ExerciseLibrary): WorkoutGenerationCatalogExercise =
            WorkoutGenerationCatalogExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                primaryMuscle = exercise.primaryMuscleGroup,
                equipment = exercise.equipment,
                movementPattern = exercise.movementPattern,
                difficultyLevel = exercise.difficultyLevel,
                isCompound = exercise.isCompound
            )
    }
}
