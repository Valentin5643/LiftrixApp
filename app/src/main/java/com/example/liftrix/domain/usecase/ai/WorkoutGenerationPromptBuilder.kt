package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.ai.WorkoutGenerationCatalogExercise
import com.example.liftrix.domain.model.ai.WorkoutGenerationInputPayload
import com.example.liftrix.domain.model.ai.WorkoutGenerationPrompt
import com.example.liftrix.domain.model.ai.WorkoutGenerationRequest
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutGenerationPromptBuilder @Inject constructor() {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    fun build(
        request: WorkoutGenerationRequest,
        exerciseCatalog: List<ExerciseLibrary>
    ): WorkoutGenerationPrompt {
        val rankedCatalog = rankCatalog(request, exerciseCatalog)
            .take(MAX_CATALOG_EXERCISES)
            .map(WorkoutGenerationCatalogExercise::fromExercise)

        val payload = WorkoutGenerationInputPayload(
            userPrompt = request.userPrompt.trim(),
            normalizedConstraints = request.normalizedConstraints,
            personalization = request.personalization,
            exerciseCatalog = rankedCatalog
        )

        return WorkoutGenerationPrompt(
            systemPrompt = SYSTEM_PROMPT,
            inputPayload = json.encodeToString(payload),
            catalogHash = sha256(rankedCatalog.joinToString("|") { it.exerciseId })
        )
    }

    private fun rankCatalog(
        request: WorkoutGenerationRequest,
        exerciseCatalog: List<ExerciseLibrary>
    ): List<ExerciseLibrary> {
        val targetMaxDifficulty = when (request.normalizedConstraints.level) {
            com.example.liftrix.domain.model.ai.WorkoutProgramLevel.BEGINNER -> 3
            com.example.liftrix.domain.model.ai.WorkoutProgramLevel.INTERMEDIATE -> 6
            com.example.liftrix.domain.model.ai.WorkoutProgramLevel.ADVANCED -> 10
        }

        val compatible = exerciseCatalog
            .filter { it.equipment in request.normalizedConstraints.allowedEquipment }
            .filter { it.equipment !in request.normalizedConstraints.excludedEquipment }

        val byCategory = compatible.groupBy { it.primaryMuscleGroup }
        val diversified = ExerciseCategory.entries.flatMap { category ->
            byCategory[category].orEmpty()
                .sortedWith(
                    compareBy<ExerciseLibrary> { if (it.difficultyLevel <= targetMaxDifficulty) 0 else 1 }
                        .thenBy { it.difficultyLevel }
                        .thenByDescending { it.isCompound }
                        .thenBy { it.name }
                )
                .take(CATEGORY_PICK_LIMIT)
        }

        return (diversified + compatible)
            .distinctBy { it.id }
            .sortedWith(
                compareBy<ExerciseLibrary> { if (it.difficultyLevel <= targetMaxDifficulty) 0 else 1 }
                    .thenBy { it.primaryMuscleGroup.getPriority() }
                    .thenBy { it.difficultyLevel }
                    .thenBy { it.name }
            )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MAX_CATALOG_EXERCISES = 40
        private const val CATEGORY_PICK_LIMIT = 4

        val SYSTEM_PROMPT = """
            You are Liftrix Workout Program Generator.
            Return only valid JSON. Do not return Markdown, comments, explanations, or code fences.
            Return exactly one top-level object using this response wrapper: {"schema_version":"${GeneratedWorkoutProgram.SCHEMA_VERSION}","program":{"workout_name":"string","goal":"general_fitness","level":"beginner","days":[]}}.
            The program object must contain workout_name, goal, level, and days. Do not use program_name.
            Generate workout programs that are safe, realistic, and directly usable by the Liftrix app.
            Use only exercises from the provided exercise_catalog. Copy exercise_id, exercise_name, primary_muscle, and equipment exactly from the catalog.
            Only use exercise_ids from the provided compatible exercise_catalog. Do not invent exercise IDs or use exercises outside that catalog.
            Respect every requested constraint: number of days, fitness level, goal, equipment, injuries/exclusions, available time, and user preferences.
            Do not use exercises above the requested fitness level. Beginner programs may only use catalog exercises with beginner-compatible difficulty.
            If a requested constraint cannot be satisfied with the catalog, return {"schema_version":"${GeneratedWorkoutProgram.SCHEMA_VERSION}","error":{"code":"UNSATISFIABLE_REQUEST","message":"..."}} using the strict error schema.
            For BEGINNER level, all rep-based exercises must have reps_min >= 8 and reps_max <= 15.
            For beginner users, keep sessions to 4-6 exercises, 2-3 sets per exercise, 8-15 reps for all rep-based work, 45-120 seconds rest, and avoid advanced/high-skill movements.
            If an exercise cannot satisfy beginner constraints, choose another compatible catalog exercise instead.
            For each exercise, use exercise_type for broad category values like strength or cardio. Do not use a field named type.
            Represent exercise prescription with explicit fields: reps_min, reps_max, duration_seconds, and is_unilateral. Use reps_min/reps_max for rep-based exercises and duration_seconds for timed exercises. Never encode "each side" or seconds inside a reps string.
            Do not include medical claims, diagnosis, rehab prescriptions, or unsafe intensity instructions.
        """.trimIndent()
    }
}
