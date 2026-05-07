package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.ai.WorkoutGenerationConstraints
import com.example.liftrix.domain.model.ai.WorkoutGenerationPersonalization
import com.example.liftrix.domain.model.ai.WorkoutGenerationRequest
import com.example.liftrix.domain.model.ai.WorkoutProgramLevel
import com.example.liftrix.domain.model.ai.GeneratedPrescriptionType
import com.example.liftrix.domain.model.ai.GeneratedWorkoutDay
import com.example.liftrix.domain.model.ai.GeneratedWorkoutExercise
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.ai.WorkoutAiContextSnapshot
import com.example.liftrix.domain.model.ai.WorkoutAiHistorySummary
import com.example.liftrix.domain.model.ai.WorkoutGenerationCatalogExercise
import com.example.liftrix.domain.model.ai.WorkoutProgramGoal
import com.example.liftrix.domain.model.ai.WorkoutProgramSourceReference
import com.example.liftrix.domain.model.ai.WorkoutProgramSourceType
import com.example.liftrix.domain.service.Language
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutGenerationPromptBuilderTest {

    private val builder = WorkoutGenerationPromptBuilder()

    @Test
    fun `build includes strict prompt and minimized profile data`() {
        val request = WorkoutGenerationRequest(
            userId = "user-1",
            userPrompt = "Create a 3-day beginner dumbbell workout",
            normalizedConstraints = WorkoutGenerationConstraints(
                allowedEquipment = setOf(Equipment.DUMBBELLS),
                language = Language.ENGLISH
            ),
            personalization = WorkoutGenerationPersonalization(
                ageBand = "adult",
                profileGoals = listOf(FitnessGoal.BUILD_MUSCLE),
                profileEquipment = setOf(Equipment.DUMBBELLS),
                experienceLevel = WorkoutProgramLevel.BEGINNER
            )
        )

        val prompt = builder.build(request, listOf(exercise("db_row", Equipment.DUMBBELLS)))

        assertTrue(prompt.systemPrompt.contains("Return only valid JSON"))
        assertTrue(prompt.systemPrompt.contains("For BEGINNER level, all rep-based exercises must have reps_min >= 8 and reps_max <= 15"))
        assertTrue(prompt.systemPrompt.contains("Do not use exercises above the requested fitness level"))
        assertTrue(prompt.systemPrompt.contains("Only use exercise_ids from the provided compatible exercise_catalog"))
        assertTrue(prompt.systemPrompt.contains("If an exercise cannot satisfy beginner constraints"))
        assertTrue(prompt.inputPayload.contains("\"profile_goals\""))
        assertTrue(prompt.inputPayload.contains("\"DUMBBELLS\""))
        assertFalse(prompt.inputPayload.contains("user-1"))
    }

    @Test
    fun `build filters catalog to allowed equipment`() {
        val request = WorkoutGenerationRequest(
            userId = "user-1",
            userPrompt = "Dumbbells only",
            normalizedConstraints = WorkoutGenerationConstraints(
                allowedEquipment = setOf(Equipment.DUMBBELLS),
                excludedEquipment = setOf(Equipment.BARBELL)
            )
        )

        val prompt = builder.build(
            request,
            listOf(
                exercise("db_row", Equipment.DUMBBELLS),
                exercise("bb_row", Equipment.BARBELL)
            )
        )

        assertTrue(prompt.inputPayload.contains("db_row"))
        assertFalse(prompt.inputPayload.contains("bb_row"))
    }

    @Test
    fun `build modification prompt includes source context and change contract`() {
        val prompt = builder.buildModification(
            userPrompt = "Make this workout easier",
            sourceReference = sourceReference(),
            sourceProgram = sourceProgram(),
            contextSnapshot = contextSnapshot()
        )

        assertTrue(prompt.inputPayload.contains("\"task\":\"modify_workout_program\""))
        assertTrue(prompt.inputPayload.contains("\"source_reference\""))
        assertTrue(prompt.inputPayload.contains("\"source_program\""))
        assertTrue(prompt.inputPayload.contains("\"context_snapshot\""))
        assertTrue(prompt.inputPayload.contains("brief reason"))
        assertTrue(prompt.systemPrompt.contains("preserve the provided source_reference"))
        assertFalse(prompt.inputPayload.contains("catalog-extra"))
    }

    @Test
    fun `build progression prompt uses progression task and bounded history context`() {
        val prompt = builder.buildProgressionUpdate(
            userPrompt = "Update my plan based on progress",
            sourceReference = sourceReference(),
            sourceProgram = sourceProgram(),
            contextSnapshot = contextSnapshot()
        )

        assertTrue(prompt.inputPayload.contains("\"task\":\"update_plan_from_progress\""))
        assertTrue(prompt.inputPayload.contains("\"completed_history_count\":12"))
        assertTrue(prompt.inputPayload.contains("\"source_id\":\"template-1\""))
        assertFalse(prompt.inputPayload.contains("raw workout history"))
    }

    private fun exercise(id: String, equipment: Equipment) = ExerciseLibrary(
        id = id,
        name = "Exercise $id",
        primaryMuscleGroup = ExerciseCategory.BACK,
        equipment = equipment,
        secondaryMuscleGroups = emptyList(),
        movementPattern = "Pull",
        difficultyLevel = 2,
        instructions = null,
        isCompound = true,
        searchableTerms = listOf(id)
    )

    private fun sourceReference() = WorkoutProgramSourceReference(
        sourceType = WorkoutProgramSourceType.TEMPLATE,
        sourceId = "template-1",
        sourceName = "Push Day"
    )

    private fun sourceProgram() = GeneratedWorkoutProgram(
        workoutName = "Push Day",
        goal = WorkoutProgramGoal.HYPERTROPHY,
        level = WorkoutProgramLevel.BEGINNER,
        days = listOf(
            GeneratedWorkoutDay(
                dayName = "Day 1",
                estimatedDurationMinutes = 45,
                exercises = listOf(
                    GeneratedWorkoutExercise(
                        exerciseId = "db_press",
                        exerciseName = "Dumbbell Press",
                        primaryMuscle = ExerciseCategory.CHEST,
                        equipment = Equipment.DUMBBELLS,
                        sets = 3,
                        type = GeneratedPrescriptionType.REPS,
                        repsMin = 8,
                        repsMax = 12,
                        isUnilateral = false,
                        restSeconds = 90
                    )
                )
            )
        )
    )

    private fun contextSnapshot() = WorkoutAiContextSnapshot(
        userId = "user-1",
        availableEquipment = setOf(Equipment.DUMBBELLS),
        fitnessGoals = listOf(FitnessGoal.BUILD_MUSCLE),
        ageBand = "adult",
        weightUnit = "kg",
        recentHistory = WorkoutAiHistorySummary(
            totalWorkouts = 16,
            completedHistoryCount = 12,
            recentExerciseCount = 6
        ),
        exerciseCatalog = (1..45).map { index ->
            WorkoutGenerationCatalogExercise(
                exerciseId = if (index == 45) "catalog-extra" else "catalog-$index",
                exerciseName = "Exercise $index",
                primaryMuscle = ExerciseCategory.CHEST,
                equipment = Equipment.DUMBBELLS,
                movementPattern = "Push",
                difficultyLevel = 2,
                isCompound = true
            )
        }
    )
}
