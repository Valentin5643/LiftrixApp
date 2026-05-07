package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ai.GeneratedPrescriptionType
import com.example.liftrix.domain.model.ai.GeneratedWorkoutDay
import com.example.liftrix.domain.model.ai.GeneratedWorkoutExercise
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.ai.WorkoutGenerationConstraints
import com.example.liftrix.domain.model.ai.WorkoutGenerationRequest
import kotlin.test.Test
import kotlin.test.assertTrue

class ValidateGeneratedWorkoutProgramUseCaseTest {

    private val validator = ValidateGeneratedWorkoutProgramUseCase()

    @Test
    fun `accepts valid beginner dumbbell program`() {
        val catalog = listOf(catalog("db_row", "Dumbbell Row", Equipment.DUMBBELLS))
        val program = program(exercise("db_row", "Dumbbell Row", Equipment.DUMBBELLS))

        val result = validator(program, request(), catalog)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `rejects forbidden equipment`() {
        val catalog = listOf(catalog("bb_row", "Barbell Row", Equipment.BARBELL))
        val program = program(exercise("bb_row", "Barbell Row", Equipment.BARBELL))

        val result = validator(program, request(), catalog)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("forbidden equipment") == true)
    }

    @Test
    fun `rejects excessive beginner sets`() {
        val catalog = listOf(catalog("db_row", "Dumbbell Row", Equipment.DUMBBELLS))
        val program = program(exercise("db_row", "Dumbbell Row", Equipment.DUMBBELLS, sets = 5))

        val result = validator(program, request(), catalog)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("beginner sets") == true)
    }

    private fun request() = WorkoutGenerationRequest(
        userId = "user-1",
        userPrompt = "Create a dumbbell workout",
        normalizedConstraints = WorkoutGenerationConstraints(
            daysPerWeek = 1,
            allowedEquipment = setOf(Equipment.DUMBBELLS),
            excludedEquipment = setOf(Equipment.BARBELL)
        )
    )

    private fun program(exercise: GeneratedWorkoutExercise) = GeneratedWorkoutProgram(
        workoutName = "Beginner Plan",
        goal = com.example.liftrix.domain.model.ai.WorkoutProgramGoal.GENERAL_FITNESS,
        level = com.example.liftrix.domain.model.ai.WorkoutProgramLevel.BEGINNER,
        days = listOf(
            GeneratedWorkoutDay(
                dayName = "Day 1",
                estimatedDurationMinutes = 45,
                exercises = listOf(exercise)
            )
        )
    )

    private fun exercise(
        id: String,
        name: String,
        equipment: Equipment,
        sets: Int = 3
    ) = GeneratedWorkoutExercise(
        exerciseId = id,
        exerciseName = name,
        primaryMuscle = ExerciseCategory.BACK,
        equipment = equipment,
        sets = sets,
        type = GeneratedPrescriptionType.REPS,
        repsMin = 8,
        repsMax = 12,
        durationSeconds = null,
        isUnilateral = false,
        restSeconds = 90
    )

    private fun catalog(id: String, name: String, equipment: Equipment) = ExerciseLibrary(
        id = id,
        name = name,
        primaryMuscleGroup = ExerciseCategory.BACK,
        equipment = equipment,
        secondaryMuscleGroups = emptyList(),
        movementPattern = "Pull",
        difficultyLevel = 2,
        instructions = null,
        isCompound = true,
        searchableTerms = listOf(id)
    )
}
