package com.example.liftrix.domain.usecase.ai

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.ai.WorkoutGenerationPreferences
import com.example.liftrix.domain.model.ai.WorkoutProgramGoal
import com.example.liftrix.domain.model.ai.WorkoutProgramLevel
import com.example.liftrix.domain.model.ai.WorkoutTrainingDay
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.Language
import com.example.liftrix.domain.service.WorkoutProgramGenerationService
import com.example.liftrix.domain.service.WorkoutProgramJsonResponse
import com.example.liftrix.domain.usecase.exercise.ExerciseQueryUseCase
import com.example.liftrix.domain.usecase.profile.ProfileQueryUseCase
import com.example.liftrix.domain.usecase.template.TemplateCommandUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateWorkoutProgramUseCaseTest {

    private val profileQueryUseCase = mockk<ProfileQueryUseCase>()
    private val exerciseQueryUseCase = mockk<ExerciseQueryUseCase>()
    private val templateCommandUseCase = mockk<TemplateCommandUseCase>()
    private val generationService = mockk<WorkoutProgramGenerationService>()
    private val useCase = GenerateWorkoutProgramUseCase(
        profileQueryUseCase = profileQueryUseCase,
        exerciseQueryUseCase = exerciseQueryUseCase,
        templateCommandUseCase = templateCommandUseCase,
        generationService = generationService,
        promptBuilder = WorkoutGenerationPromptBuilder(),
        validator = ValidateGeneratedWorkoutProgramUseCase(),
        cache = WorkoutGenerationCache()
    )

    @Test
    fun `generates and saves templates through template use case`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(listOf(catalog()))
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(validJson(), 100, 50, "test"))
        coEvery {
            templateCommandUseCase.create(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(template())

        val result = useCase(
            userId = "user-1",
            prompt = "Create a 1-day beginner workout using dumbbells only",
            language = Language.ENGLISH,
            saveAfterGeneration = true
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().savedTemplates.size)
        coVerify(exactly = 1) {
            templateCommandUseCase.create(
                userId = "user-1",
                name = any(),
                folderId = null,
                description = any(),
                exercises = any(),
                estimatedDurationMinutes = 45,
                difficultyLevel = 2
            )
        }
    }

    @Test
    fun `reuses cache for repeated request`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(listOf(catalog()))
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(validJson(), 100, 50, "test"))

        useCase("user-1", "Create a 1-day beginner workout using dumbbells only")
        val second = useCase("user-1", "Create a 1-day beginner workout using dumbbells only")

        assertTrue(second.getOrThrow().cacheHit)
        coVerify(exactly = 1) {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `save after generation does not reuse unsaved preview cache`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(listOf(catalog()))
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(validJson(), 100, 50, "test"))
        coEvery {
            templateCommandUseCase.create(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(template())

        useCase("user-1", "Create a 1-day beginner workout using dumbbells only")
        val saved = useCase(
            userId = "user-1",
            prompt = "Create a 1-day beginner workout using dumbbells only",
            saveAfterGeneration = true
        )

        assertTrue(saved.isSuccess)
        assertEquals(1, saved.getOrThrow().savedTemplates.size)
        coVerify(exactly = 2) {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        }
        coVerify(exactly = 1) {
            templateCommandUseCase.create(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `returns token metadata for quota accounting`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(listOf(catalog()))
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(validJson(), 123, 456, "test-model"))

        val result = useCase("user-1", "Create a 1-day beginner workout using dumbbells only")

        assertTrue(result.isSuccess)
        assertEquals(123, result.getOrThrow().tokensUsed)
        assertEquals(456, result.getOrThrow().processingTimeMs)
        assertEquals("test-model", result.getOrThrow().modelVersion)
    }

    @Test
    fun `parses markdown wrapped JSON response`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(listOf(catalog()))
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse("```json\n${validJson()}\n```", 100, 50, "test"))

        val result = useCase("user-1", "Create a 1-day beginner workout using dumbbells only")

        assertTrue(result.isSuccess)
        assertEquals("Beginner Dumbbell Plan", result.getOrThrow().program.workoutName)
    }

    @Test
    fun `parses wrapped program response without repair`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(listOf(catalog()))
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(wrappedJson(), 100, 50, "test"))

        val result = useCase("user-1", "Create a 1-day beginner workout using dumbbells only")

        assertTrue(result.isSuccess)
        assertEquals("Beginner Dumbbell Plan", result.getOrThrow().program.workoutName)
        assertEquals(0, result.getOrThrow().repairAttempts)
        coVerify(exactly = 0) {
            generationService.repairProgramJson(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `canonicalizes concatenated v2 response and applies reviewed training days`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(listOf(catalog()))
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(
            WorkoutProgramJsonResponse(
                json = explicitV2Json() + "\n" + explicitV2Json(),
                tokensUsed = 100,
                processingTimeMs = 50,
                modelVersion = "test"
            )
        )

        val result = useCase(
            userId = "user-1",
            preferences = WorkoutGenerationPreferences(
                goal = WorkoutProgramGoal.GENERAL_FITNESS,
                level = WorkoutProgramLevel.BEGINNER,
                availableEquipment = setOf(Equipment.DUMBBELLS),
                trainingDays = listOf(WorkoutTrainingDay.MONDAY, WorkoutTrainingDay.WEDNESDAY),
                sessionDurationMinutes = 45
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(WorkoutTrainingDay.MONDAY, WorkoutTrainingDay.WEDNESDAY),
            result.getOrThrow().program.days.map { it.scheduledDay }
        )
        coVerify(exactly = 0) {
            generationService.repairProgramJson(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `returns failure for malformed response after failed repair`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(listOf(catalog()))
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse("not json", 100, 50, "test"))
        coEvery {
            generationService.repairProgramJson(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse("still not json", 50, 25, "test"))

        val result = useCase("user-1", "Create a 1-day beginner workout using dumbbells only")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("repair response was not valid workout JSON") == true)
    }

    @Test
    fun `rejects unknown fields instead of lenient success parsing`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(listOf(catalog()))
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(unknownFieldJson(), 100, 50, "test"))
        coEvery {
            generationService.repairProgramJson(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse("still not json", 50, 25, "test"))

        val result = useCase("user-1", "Create a 1-day beginner workout using dumbbells only")

        assertTrue(result.isFailure)
        coVerify(exactly = 1) {
            generationService.repairProgramJson(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Ignore("Local repair fallback was removed; invalid AI repair responses now fail closed.")
    @Test
    fun `local repair clamps beginner reps above fifteen after identical AI repair`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(bodyweightCatalog())
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(invalidBeginnerJson(), 100, 50, "test"))
        coEvery {
            generationService.repairProgramJson(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(invalidBeginnerJson(), 50, 25, "test"))

        val result = useCase("user-1", "Create a 1-day beginner bodyweight workout")

        assertTrue(result.isSuccess)
        val bicycleCrunches = result.getOrThrow().program.days.first().exercises
            .first { it.exerciseId == "bicycle_crunches" }
        assertEquals(15, bicycleCrunches.repsMax)
    }

    @Ignore("Local repair fallback was removed; invalid AI repair responses now fail closed.")
    @Test
    fun `local repair clamps beginner reps below eight after identical AI repair`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(bodyweightCatalog())
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(invalidBeginnerJson(), 100, 50, "test"))
        coEvery {
            generationService.repairProgramJson(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(invalidBeginnerJson(), 50, 25, "test"))

        val result = useCase("user-1", "Create a 1-day beginner bodyweight workout")

        assertTrue(result.isSuccess)
        val replacedBurpee = result.getOrThrow().program.days.first().exercises
            .first { it.exerciseId == "jumping_jacks" }
        assertEquals(8, replacedBurpee.repsMin)
        assertEquals(8, replacedBurpee.repsMax)
    }

    @Ignore("Local repair fallback was removed; invalid AI repair responses now fail closed.")
    @Test
    fun `local repair replaces above beginner exercises with compatible catalog exercises`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(bodyweightCatalog())
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(invalidBeginnerJson(), 100, 50, "test"))
        coEvery {
            generationService.repairProgramJson(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(invalidBeginnerJson(), 50, 25, "test"))

        val result = useCase("user-1", "Create a 1-day beginner bodyweight workout")

        assertTrue(result.isSuccess)
        val exerciseIds = result.getOrThrow().program.days.first().exercises.map { it.exerciseId }
        assertTrue("bodyweight_squat" in exerciseIds)
        assertTrue("jumping_jacks" in exerciseIds)
        assertTrue("wall_sit" !in exerciseIds)
        assertTrue("burpees" !in exerciseIds)
    }

    @Ignore("Local repair fallback was removed; invalid AI repair responses now fail closed.")
    @Test
    fun `identical AI repair does not fail when local repair can validate program`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(bodyweightCatalog())
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(invalidBeginnerJson(), 100, 50, "test"))
        coEvery {
            generationService.repairProgramJson(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(invalidBeginnerJson(), 50, 25, "test"))

        val result = useCase("user-1", "Create a 1-day beginner bodyweight workout")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().repairAttempts)
    }

    @Ignore("Local repair fallback was removed; invalid AI repair responses now fail closed.")
    @Test
    fun `locally repaired beginner program is saved after validation passes`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(bodyweightCatalog())
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(invalidBeginnerJson(), 100, 50, "test"))
        coEvery {
            generationService.repairProgramJson(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(invalidBeginnerJson(), 50, 25, "test"))
        coEvery {
            templateCommandUseCase.create(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(template())

        val result = useCase(
            userId = "user-1",
            prompt = "Create a 1-day beginner bodyweight workout",
            saveAfterGeneration = true
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().savedTemplates.size)
        coVerify(exactly = 1) {
            templateCommandUseCase.create(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `propagates template save failure`() = runTest {
        coEvery { profileQueryUseCase.getById("user-1") } returns Result.success(null)
        coEvery { exerciseQueryUseCase.invoke() } returns Result.success(listOf(catalog()))
        coEvery {
            generationService.generateProgramJson(any(), any(), any(), any(), any())
        } returns Result.success(WorkoutProgramJsonResponse(validJson(), 100, 50, "test"))
        coEvery {
            templateCommandUseCase.create(any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(
            LiftrixError.BusinessLogicError(
                code = "TEMPLATE_CREATION_FAILED",
                errorMessage = "Save failed"
            )
        )

        val result = useCase(
            userId = "user-1",
            prompt = "Create a 1-day beginner workout using dumbbells only",
            language = Language.ENGLISH,
            saveAfterGeneration = true
        )

        assertTrue(result.isFailure)
        assertEquals("Save failed", result.exceptionOrNull()?.message)
    }

    private fun validJson() = """
        {
          "schema_version": "1.0",
          "workout_name": "Beginner Dumbbell Plan",
          "goal": "general_fitness",
          "level": "beginner",
          "days": [
            {
              "day_name": "Day 1",
              "estimated_duration_minutes": 45,
              "exercises": [
                {
                  "exercise_id": "db_row",
                  "exercise_name": "Dumbbell Row",
                  "primary_muscle": "BACK",
                  "equipment": "DUMBBELLS",
                  "sets": 3,
                  "type": "REPS",
                  "reps_min": 8,
                  "reps_max": 12,
                  "duration_seconds": null,
                  "is_unilateral": false,
                  "rest_seconds": 90,
                  "notes": "Control each rep."
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun wrappedJson() = """
        {
          "schema_version": "1.0",
          "program": {
            "program_name": "Beginner Dumbbell Plan",
            "goal": "general_fitness",
            "level": "beginner",
            "days": [
              {
                "day_name": "Day 1",
                "estimated_duration_minutes": 45,
                "exercises": [
                  {
                    "exercise_id": "db_row",
                    "exercise_name": "Dumbbell Row",
                    "primary_muscle": "BACK",
                    "equipment": "DUMBBELLS",
                    "sets": 3,
                    "type": "REPS",
                    "reps_min": 8,
                    "reps_max": 12,
                    "duration_seconds": null,
                    "is_unilateral": false,
                    "rest_seconds": 90,
                    "notes": "Control each rep."
                  }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    private fun explicitV2Json() = """
        {
          "schema_version": "2.0",
          "program": {
            "workout_name": "Reviewed Dumbbell Plan",
            "description": "A safe beginner plan.",
            "goal": "general_fitness",
            "level": "beginner",
            "days": [
              {
                "day_name": "Day 1",
                "focus": "Back",
                "estimated_duration_minutes": 45,
                "warm_up": {"duration_minutes": 5, "steps": ["Easy mobility"]},
                "exercises": [
                  {
                    "exercise_id": "db_row",
                    "exercise_name": "Dumbbell Row",
                    "primary_muscle": "BACK",
                    "equipment": "DUMBBELLS",
                    "sets": 3,
                    "type": "REPS",
                    "reps_min": 8,
                    "reps_max": 12,
                    "duration_seconds": null,
                    "is_unilateral": false,
                    "rest_seconds": 90
                  }
                ],
                "cool_down": {"duration_minutes": 5, "steps": ["Easy breathing"]}
              },
              {
                "day_name": "Day 2",
                "focus": "Back",
                "estimated_duration_minutes": 45,
                "warm_up": {"duration_minutes": 5, "steps": ["Easy mobility"]},
                "exercises": [
                  {
                    "exercise_id": "db_row",
                    "exercise_name": "Dumbbell Row",
                    "primary_muscle": "BACK",
                    "equipment": "DUMBBELLS",
                    "sets": 3,
                    "type": "REPS",
                    "reps_min": 8,
                    "reps_max": 12,
                    "duration_seconds": null,
                    "is_unilateral": false,
                    "rest_seconds": 90
                  }
                ],
                "cool_down": {"duration_minutes": 5, "steps": ["Easy breathing"]}
              }
            ]
          }
        }
    """.trimIndent()

    private fun unknownFieldJson() = """
        {
          "schema_version": "1.0",
          "unexpected_field": "must fail strict parsing",
          "program": {
            "workout_name": "Beginner Dumbbell Plan",
            "goal": "general_fitness",
            "level": "beginner",
            "days": []
          }
        }
    """.trimIndent()

    private fun invalidBeginnerJson() = """
        {
          "schema_version": "1.0",
          "workout_name": "Beginner Bodyweight Plan",
          "goal": "general_fitness",
          "level": "beginner",
          "days": [
            {
              "day_name": "Day 1",
              "estimated_duration_minutes": 45,
              "exercises": [
                {
                  "exercise_id": "bicycle_crunches",
                  "exercise_name": "Bicycle Crunches",
                  "primary_muscle": "ABS",
                  "equipment": "BODYWEIGHT_ONLY",
                  "sets": 3,
                  "type": "REPS",
                  "reps_min": 12,
                  "reps_max": 20,
                  "duration_seconds": null,
                  "is_unilateral": false,
                  "rest_seconds": 60
                },
                {
                  "exercise_id": "calf_raises",
                  "exercise_name": "Calf Raises",
                  "primary_muscle": "CALVES",
                  "equipment": "BODYWEIGHT_ONLY",
                  "sets": 3,
                  "type": "REPS",
                  "reps_min": 12,
                  "reps_max": 20,
                  "duration_seconds": null,
                  "is_unilateral": false,
                  "rest_seconds": 60
                },
                {
                  "exercise_id": "wall_sit",
                  "exercise_name": "Wall Sit",
                  "primary_muscle": "LEGS",
                  "equipment": "BODYWEIGHT_ONLY",
                  "sets": 3,
                  "type": "TIME",
                  "reps_min": null,
                  "reps_max": null,
                  "duration_seconds": 30,
                  "is_unilateral": false,
                  "rest_seconds": 60
                },
                {
                  "exercise_id": "burpees",
                  "exercise_name": "Burpees",
                  "primary_muscle": "FULL_BODY",
                  "equipment": "BODYWEIGHT_ONLY",
                  "sets": 3,
                  "type": "REPS",
                  "reps_min": 5,
                  "reps_max": 8,
                  "duration_seconds": null,
                  "is_unilateral": false,
                  "rest_seconds": 60
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun bodyweightCatalog() = listOf(
        catalog(
            id = "bicycle_crunches",
            name = "Bicycle Crunches",
            equipment = Equipment.BODYWEIGHT_ONLY,
            primaryMuscleGroup = ExerciseCategory.ABS,
            difficultyLevel = 2
        ),
        catalog(
            id = "calf_raises",
            name = "Calf Raises",
            equipment = Equipment.BODYWEIGHT_ONLY,
            primaryMuscleGroup = ExerciseCategory.CALVES,
            difficultyLevel = 2
        ),
        catalog(
            id = "wall_sit",
            name = "Wall Sit",
            equipment = Equipment.BODYWEIGHT_ONLY,
            primaryMuscleGroup = ExerciseCategory.LEGS,
            difficultyLevel = 4
        ),
        catalog(
            id = "bodyweight_squat",
            name = "Bodyweight Squat",
            equipment = Equipment.BODYWEIGHT_ONLY,
            primaryMuscleGroup = ExerciseCategory.LEGS,
            difficultyLevel = 2
        ),
        catalog(
            id = "burpees",
            name = "Burpees",
            equipment = Equipment.BODYWEIGHT_ONLY,
            primaryMuscleGroup = ExerciseCategory.FULL_BODY,
            difficultyLevel = 5
        ),
        catalog(
            id = "jumping_jacks",
            name = "Jumping Jacks",
            equipment = Equipment.BODYWEIGHT_ONLY,
            primaryMuscleGroup = ExerciseCategory.FULL_BODY,
            difficultyLevel = 2
        )
    )

    private fun catalog(
        id: String = "db_row",
        name: String = "Dumbbell Row",
        equipment: Equipment = Equipment.DUMBBELLS,
        primaryMuscleGroup: ExerciseCategory = ExerciseCategory.BACK,
        difficultyLevel: Int = 2
    ) = ExerciseLibrary(
        id = id,
        name = name,
        primaryMuscleGroup = primaryMuscleGroup,
        equipment = equipment,
        secondaryMuscleGroups = emptyList(),
        movementPattern = "General",
        difficultyLevel = difficultyLevel,
        instructions = null,
        isCompound = true,
        searchableTerms = listOf("row")
    )

    private fun template(): WorkoutTemplate = WorkoutTemplate.create(
        userId = "user-1",
        name = "Beginner Dumbbell Plan - Day 1",
        folderId = "folder-1",
        exercises = listOf(
            TemplateExercise(
                exerciseId = com.example.liftrix.domain.model.ExerciseId.fromString("db_row"),
                name = "Dumbbell Row",
                primaryMuscle = ExerciseCategory.BACK,
                equipment = Equipment.DUMBBELLS,
                targetSets = 3,
                orderIndex = 0
            )
        )
    )
}
