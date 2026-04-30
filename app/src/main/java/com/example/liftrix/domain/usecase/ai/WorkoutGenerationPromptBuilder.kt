package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.ai.WorkoutAiContextSnapshot
import com.example.liftrix.domain.model.ai.WorkoutGenerationCatalogExercise
import com.example.liftrix.domain.model.ai.WorkoutGenerationInputPayload
import com.example.liftrix.domain.model.ai.WorkoutGenerationPrompt
import com.example.liftrix.domain.model.ai.WorkoutGenerationRequest
import com.example.liftrix.domain.model.ai.WorkoutProgramAiTask
import com.example.liftrix.domain.model.ai.WorkoutProgramSourceReference
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    fun buildModification(
        userPrompt: String,
        sourceReference: WorkoutProgramSourceReference,
        sourceProgram: GeneratedWorkoutProgram,
        contextSnapshot: WorkoutAiContextSnapshot
    ): WorkoutGenerationPrompt = buildTaskPrompt(
        task = WorkoutProgramAiTask.MODIFY_WORKOUT_PROGRAM,
        userPrompt = userPrompt,
        sourceReference = sourceReference,
        sourceProgram = sourceProgram,
        contextSnapshot = contextSnapshot
    )

    fun buildProgressionUpdate(
        userPrompt: String,
        sourceReference: WorkoutProgramSourceReference,
        sourceProgram: GeneratedWorkoutProgram,
        contextSnapshot: WorkoutAiContextSnapshot
    ): WorkoutGenerationPrompt = buildTaskPrompt(
        task = WorkoutProgramAiTask.UPDATE_PLAN_FROM_PROGRESS,
        userPrompt = userPrompt,
        sourceReference = sourceReference,
        sourceProgram = sourceProgram,
        contextSnapshot = contextSnapshot
    )

    private fun buildTaskPrompt(
        task: WorkoutProgramAiTask,
        userPrompt: String,
        sourceReference: WorkoutProgramSourceReference,
        sourceProgram: GeneratedWorkoutProgram,
        contextSnapshot: WorkoutAiContextSnapshot
    ): WorkoutGenerationPrompt {
        val cappedContext = contextSnapshot.copy(
            exerciseCatalog = contextSnapshot.exerciseCatalog.take(MAX_CATALOG_EXERCISES)
        )
        val payload = WorkoutModificationPromptPayload(
            task = task,
            userPrompt = userPrompt.trim(),
            sourceReference = sourceReference,
            sourceProgram = sourceProgram,
            contextSnapshot = cappedContext
        )
        val catalogHash = sha256(
            listOf(
                task.name,
                sourceReference.sourceType.name,
                sourceReference.sourceId,
                cappedContext.exerciseCatalog.joinToString("|") { it.exerciseId }
            ).joinToString("|")
        )

        return WorkoutGenerationPrompt(
            systemPrompt = "$SYSTEM_PROMPT\n\n$MODIFICATION_SYSTEM_PROMPT",
            inputPayload = json.encodeToString(payload),
            catalogHash = catalogHash
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

        private val MODIFICATION_SYSTEM_PROMPT = """
            For modify_workout_program and update_plan_from_progress tasks, preserve the provided source_reference and return ModifiedWorkoutProgramResponse JSON.
            The response must include source, program, changes, significance, confirmation_required, and optional_question.
            Each change summary must include type, before, after, and a brief reason.
            Treat source_program as editable plan context only; do not resolve or modify completed workout sessions.
            Use context_snapshot only as bounded personalization context. Do not infer private profile details that are not present in the payload.
            Keep exercise substitutions within context_snapshot.exercise_catalog and preserve valid exercise_id values.
        """.trimIndent()
    }
}

@Serializable
private data class WorkoutModificationPromptPayload(
    val task: WorkoutProgramAiTask,
    @SerialName("schema_version")
    val schemaVersion: String = GeneratedWorkoutProgram.SCHEMA_VERSION,
    @SerialName("user_prompt")
    val userPrompt: String,
    @SerialName("source_reference")
    val sourceReference: WorkoutProgramSourceReference,
    @SerialName("source_program")
    val sourceProgram: GeneratedWorkoutProgram,
    @SerialName("context_snapshot")
    val contextSnapshot: WorkoutAiContextSnapshot,
    @SerialName("output_contract")
    val outputContract: String = "Return ModifiedWorkoutProgramResponse JSON with source, program, changes including brief reason fields, significance, confirmation_required, and optional_question."
)
