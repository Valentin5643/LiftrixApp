package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram
import com.example.liftrix.domain.model.ai.WorkoutGenerationRequest
import com.example.liftrix.domain.model.ai.WorkoutProgramGoal
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.WorkoutProgramGenerationService
import com.example.liftrix.domain.service.WorkoutProgramJsonResponse
import com.example.liftrix.domain.service.Language
import com.example.liftrix.domain.usecase.exercise.ExerciseQueryUseCase
import com.example.liftrix.domain.usecase.profile.ProfileQueryUseCase
import com.example.liftrix.domain.usecase.template.TemplateCommandUseCase
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest

class GenerateWorkoutProgramUseCaseParsingTest {

    private lateinit var useCase: GenerateWorkoutProgramUseCase
    private lateinit var profileQueryUseCase: ProfileQueryUseCase
    private lateinit var exerciseQueryUseCase: ExerciseQueryUseCase
    private lateinit var generationService: WorkoutProgramGenerationService

    @Before
    fun setUp() {
        profileQueryUseCase = mockk(relaxed = true)
        exerciseQueryUseCase = mockk(relaxed = true)
        generationService = mockk(relaxed = true)
        useCase = GenerateWorkoutProgramUseCase(
            profileQueryUseCase = profileQueryUseCase,
            exerciseQueryUseCase = exerciseQueryUseCase,
            templateCommandUseCase = mockk<TemplateCommandUseCase>(relaxed = true),
            generationService = generationService,
            promptBuilder = WorkoutGenerationPromptBuilder(),
            validator = ValidateGeneratedWorkoutProgramUseCase(),
            cache = WorkoutGenerationCache()
        )
    }

    @Test
    fun `parseProgram accepts wrapped program days with day field`() {
        val result = parseProgram(validProgramJson(dayField = """"day": 1,"""))

        assertTrue(result.isSuccess)
        val program = result.getOrThrow()
        assertEquals("Day 1", program.days.first().dayName)
    }

    @Test
    fun `parseProgram accepts wrapped program days without day field`() {
        val result = parseProgram(validProgramJson(dayField = ""))

        assertTrue(result.isSuccess)
        val program = result.getOrThrow()
        assertEquals("Day 1", program.days.first().dayName)
    }

    @Test
    fun `parseProgram invalid JSON still fails with parse error`() {
        val result = parseProgram("""{"schema_version":"1.0","program":{"workout_name":"Broken","days":[""")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is LiftrixError.BusinessLogicError)
        assertEquals("AI_WORKOUT_GENERATION_PARSE_FAILED", (error as LiftrixError.BusinessLogicError).code)
    }

    @Test
    fun `buildRequest does not default unspecified equipment to bodyweight only`() {
        val request = buildRequest("make me a strength program")

        assertEquals(3, request.normalizedConstraints.daysPerWeek)
        assertEquals(WorkoutProgramGoal.STRENGTH, request.normalizedConstraints.goal)
        assertEquals(Equipment.entries.toSet(), request.normalizedConstraints.allowedEquipment)
    }

    @Test
    fun `buildRequest extracts gym days goal duration and equipment`() {
        val request = buildRequest("make me a 5 day 60 minute gym hypertrophy program")

        assertEquals(5, request.normalizedConstraints.daysPerWeek)
        assertEquals(60, request.normalizedConstraints.sessionDurationMinutes)
        assertEquals(WorkoutProgramGoal.HYPERTROPHY, request.normalizedConstraints.goal)
        assertTrue(request.normalizedConstraints.allowedEquipment.contains(Equipment.DUMBBELLS))
        assertTrue(request.normalizedConstraints.allowedEquipment.contains(Equipment.BARBELL))
        assertTrue(request.normalizedConstraints.allowedEquipment.contains(Equipment.CABLE_MACHINE))
        assertFalse(request.normalizedConstraints.allowedEquipment.contains(Equipment.BODYWEIGHT_ONLY))
    }

    @Test
    fun `buildRequest supports mixed bodyweight and equipment prompts`() {
        val request = buildRequest("make me a bodyweight and dumbbell routine")

        assertTrue(request.normalizedConstraints.allowedEquipment.contains(Equipment.BODYWEIGHT_ONLY))
        assertTrue(request.normalizedConstraints.allowedEquipment.contains(Equipment.DUMBBELLS))
    }

    @Test
    fun `invoke returns failure reason when generation and repair fail validation`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(dumbbellCatalog)
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(
            WorkoutProgramJsonResponse(
                json = oneDayDumbbellProgramJson(),
                tokensUsed = 100,
                processingTimeMs = 250,
                modelVersion = "test"
            )
        )
        coEvery {
            generationService.repairProgramJson(any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(
            LiftrixError.BusinessLogicError(
                code = "REPAIR_FAILED",
                errorMessage = "repair unavailable"
            )
        )

        val result = useCase(
            userId = "user-1",
            prompt = "make me a 3 day dumbbell program",
            language = Language.ENGLISH
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is LiftrixError.BusinessLogicError)
        val businessError = error as LiftrixError.BusinessLogicError
        assertEquals("AI_WORKOUT_GENERATION_INVALID", businessError.code)
        assertTrue(businessError.errorMessage.contains("days count", ignoreCase = true))
        assertTrue(businessError.errorMessage.contains("Repair failed", ignoreCase = true))
    }

    private fun parseProgram(json: String): Result<GeneratedWorkoutProgram> {
        val method = GenerateWorkoutProgramUseCase::class.java.getDeclaredMethod(
            "parseProgram",
            String::class.java,
            List::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(useCase, json, catalog) as Result<GeneratedWorkoutProgram>
    }

    private fun buildRequest(prompt: String): WorkoutGenerationRequest {
        val method = GenerateWorkoutProgramUseCase::class.java.getDeclaredMethod(
            "buildRequest",
            String::class.java,
            String::class.java,
            Language::class.java,
            com.example.liftrix.domain.model.UserProfile::class.java,
            Boolean::class.javaPrimitiveType!!
        )
        method.isAccessible = true
        return method.invoke(useCase, "user-1", prompt, Language.ENGLISH, null, false) as WorkoutGenerationRequest
    }

    private fun validProgramJson(dayField: String): String =
        """
        {
          "schema_version": "1.0",
          "program": {
            "workout_name": "Beginner Plan",
            "goal": "general_fitness",
            "level": "beginner",
            "days": [
              {
                $dayField
                "day_name": "Day 1",
                "estimated_duration_minutes": 45,
                "exercises": [
                  {
                    "exercise_id": "push-up",
                    "exercise_name": "Push-Up",
                    "primary_muscle": "CHEST",
                    "equipment": "BODYWEIGHT_ONLY",
                    "sets": 2,
                    "type": "REPS",
                    "reps_min": 8,
                    "reps_max": 12,
                    "duration_seconds": null,
                    "is_unilateral": false,
                    "rest_seconds": 60
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()

    private fun oneDayDumbbellProgramJson(): String =
        """
        {
          "schema_version": "1.0",
          "program": {
            "workout_name": "Invalid One Day Dumbbell Plan",
            "goal": "general_fitness",
            "level": "beginner",
            "days": [
              {
                "day_name": "Day 1",
                "estimated_duration_minutes": 45,
                "exercises": [
                  {
                    "exercise_id": "dumbbell-bench-press",
                    "exercise_name": "Dumbbell Bench Press",
                    "primary_muscle": "CHEST",
                    "equipment": "DUMBBELLS",
                    "sets": 2,
                    "exercise_type": "strength",
                    "reps_min": 8,
                    "reps_max": 12,
                    "duration_seconds": null,
                    "is_unilateral": false,
                    "rest_seconds": 60
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()

    private companion object {
        val catalog = listOf(
            ExerciseLibrary(
                id = "push-up",
                name = "Push-Up",
                primaryMuscleGroup = ExerciseCategory.CHEST,
                equipment = Equipment.BODYWEIGHT_ONLY,
                secondaryMuscleGroups = listOf(ExerciseCategory.TRICEPS),
                movementPattern = "Push",
                difficultyLevel = 1,
                instructions = "Keep a straight line and press from the floor.",
                isCompound = true,
                searchableTerms = listOf("push", "push-up", "chest")
            )
        )

        val dumbbellCatalog = listOf(
            ExerciseLibrary(
                id = "dumbbell-bench-press",
                name = "Dumbbell Bench Press",
                primaryMuscleGroup = ExerciseCategory.CHEST,
                equipment = Equipment.DUMBBELLS,
                secondaryMuscleGroups = listOf(ExerciseCategory.TRICEPS),
                movementPattern = "Horizontal Push",
                difficultyLevel = 2,
                instructions = "Press the dumbbells under control.",
                isCompound = true,
                searchableTerms = listOf("dumbbell", "bench", "press", "chest")
            )
        )
    }
}
