package com.example.liftrix.domain.usecase

import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.domain.service.WeightMemoryService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateWorkoutWithExercisesUseCaseTest {

    private lateinit var useCase: CreateWorkoutWithExercisesUseCase
    private val workoutRepository: WorkoutRepository = mockk()
    private val exerciseLibraryRepository: ExerciseLibraryRepository = mockk()
    private val weightMemoryService: WeightMemoryService = mockk()

    private val sampleExerciseLibrary = ExerciseLibrary(
        id = "exercise1",
        name = "Bench Press",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BARBELL,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
        movementPattern = "Push",
        difficultyLevel = 3,
        instructions = "Lie on bench and press barbell",
        isCompound = true,
        searchableTerms = listOf("chest", "press", "barbell")
    )

    private val sampleBodyweightExercise = ExerciseLibrary(
        id = "exercise2",
        name = "Push-ups",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BODYWEIGHT_ONLY,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
        movementPattern = "Push",
        difficultyLevel = 1,
        instructions = "Perform push-ups with proper form",
        isCompound = true,
        searchableTerms = listOf("chest", "bodyweight", "push")
    )

    @Before
    fun setup() {
        useCase = CreateWorkoutWithExercisesUseCase(
            workoutRepository,
            exerciseLibraryRepository,
            weightMemoryService
        )

        // Setup default successful mocks
        coEvery { workoutRepository.saveWorkout(any()) } returns Result.success(Unit)
        coEvery { weightMemoryService.getLastUsedWeight(any(), any()) } returns Result.success(null)
        coEvery { weightMemoryService.updateExerciseWeight(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
    }

    @Test
    fun `invoke creates workout successfully with valid request`() = runTest {
        // Given
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "user123",
            name = "Push Day",
            exerciseRequests = listOf(
                CreateWorkoutWithExercisesUseCase.ExerciseRequest(
                    libraryExercise = sampleExerciseLibrary,
                    targetSets = 3,
                    targetReps = 10,
                    targetWeight = Weight.fromKilograms(80.0)
                )
            )
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isSuccess)
        val workout = result.getOrNull()!!
        
        assertEquals("user123", workout.userId)
        assertEquals("Push Day", workout.name)
        assertEquals(1, workout.exercises.size)
        assertEquals("Bench Press", workout.exercises[0].libraryExercise.name)
        assertEquals(3, workout.exercises[0].sets.size)
        assertEquals(WorkoutStatus.PLANNED, workout.status)
        
        coVerify { workoutRepository.saveWorkout(any()) }
        coVerify { weightMemoryService.updateExerciseWeight("user123", "exercise1", 80.0f, 10, 3, null) }
    }

    @Test
    fun `invoke integrates weight memory for weight-based exercises`() = runTest {
        // Given
        val lastUsedWeight = 75.0f
        coEvery { weightMemoryService.getLastUsedWeight("user123", "exercise1") } returns Result.success(lastUsedWeight)
        
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "user123",
            name = "Push Day",
            exerciseRequests = listOf(
                CreateWorkoutWithExercisesUseCase.ExerciseRequest(
                    libraryExercise = sampleExerciseLibrary,
                    targetSets = 3,
                    targetReps = 10
                    // No target weight specified - should use weight memory
                )
            )
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isSuccess)
        val workout = result.getOrNull()!!
        val exercise = workout.exercises[0]
        
        assertNotNull(exercise.targetWeight)
        assertEquals(75.0, exercise.targetWeight!!.kilograms)
        
        // All sets should have the weight from memory
        exercise.sets.forEach { set ->
            assertNotNull(set.weight)
            assertEquals(75.0, set.weight!!.kilograms)
        }
        
        coVerify { weightMemoryService.getLastUsedWeight("user123", "exercise1") }
    }

    @Test
    fun `invoke handles bodyweight exercises without weight memory`() = runTest {
        // Given
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "user123",
            name = "Bodyweight Workout",
            exerciseRequests = listOf(
                CreateWorkoutWithExercisesUseCase.ExerciseRequest(
                    libraryExercise = sampleBodyweightExercise,
                    targetSets = 3,
                    targetReps = 15
                )
            )
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isSuccess)
        val workout = result.getOrNull()!!
        val exercise = workout.exercises[0]
        
        assertEquals("Push-ups", exercise.libraryExercise.name)
        assertEquals(3, exercise.sets.size)
        
        // Should not have weight for bodyweight exercise
        exercise.sets.forEach { set ->
            assertEquals(null, set.weight)
        }
        
        // Should not call weight memory for bodyweight exercise
        coVerify(exactly = 0) { weightMemoryService.getLastUsedWeight("user123", "exercise2") }
    }

    @Test
    fun `invoke fails validation with blank user ID`() = runTest {
        // Given
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "",
            name = "Push Day",
            exerciseRequests = listOf(
                CreateWorkoutWithExercisesUseCase.ExerciseRequest(
                    libraryExercise = sampleExerciseLibrary,
                    targetSets = 3,
                    targetReps = 10
                )
            )
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is CreateWorkoutException)
        assertTrue(exception.message!!.contains("User ID cannot be blank"))
        
        coVerify(exactly = 0) { workoutRepository.saveWorkout(any()) }
    }

    @Test
    fun `invoke fails validation with blank workout name`() = runTest {
        // Given
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "user123",
            name = "",
            exerciseRequests = listOf(
                CreateWorkoutWithExercisesUseCase.ExerciseRequest(
                    libraryExercise = sampleExerciseLibrary,
                    targetSets = 3,
                    targetReps = 10
                )
            )
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is CreateWorkoutException)
        assertTrue(exception.message!!.contains("Workout name cannot be blank"))
    }

    @Test
    fun `invoke fails validation with empty exercise list`() = runTest {
        // Given
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "user123",
            name = "Empty Workout",
            exerciseRequests = emptyList()
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is CreateWorkoutException)
        assertTrue(exception.message!!.contains("Workout must have at least one exercise"))
    }

    @Test
    fun `invoke fails validation with too many exercises`() = runTest {
        // Given
        val tooManyExercises = (1..25).map { // More than MAX_EXERCISES (20)
            CreateWorkoutWithExercisesUseCase.ExerciseRequest(
                libraryExercise = sampleExerciseLibrary.copy(id = "exercise$it"),
                targetSets = 3,
                targetReps = 10
            )
        }
        
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "user123",
            name = "Too Many Exercises",
            exerciseRequests = tooManyExercises
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is CreateWorkoutException)
        assertTrue(exception.message!!.contains("cannot have more than"))
    }

    @Test
    fun `invoke fails validation with invalid target sets`() = runTest {
        // Given
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "user123",
            name = "Invalid Sets",
            exerciseRequests = listOf(
                CreateWorkoutWithExercisesUseCase.ExerciseRequest(
                    libraryExercise = sampleExerciseLibrary,
                    targetSets = 0, // Invalid
                    targetReps = 10
                )
            )
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is CreateWorkoutException)
        assertTrue(exception.message!!.contains("Target sets must be between 1 and"))
    }

    @Test
    fun `invoke handles workout repository failure`() = runTest {
        // Given
        coEvery { workoutRepository.saveWorkout(any()) } returns Result.failure(RuntimeException("Database error"))
        
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "user123",
            name = "Push Day",
            exerciseRequests = listOf(
                CreateWorkoutWithExercisesUseCase.ExerciseRequest(
                    libraryExercise = sampleExerciseLibrary,
                    targetSets = 3,
                    targetReps = 10
                )
            )
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is CreateWorkoutException)
        assertTrue(exception.message!!.contains("Failed to save workout"))
    }

    @Test
    fun `invoke creates workout with custom date`() = runTest {
        // Given
        val customDate = LocalDate.of(2024, 1, 15)
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "user123",
            name = "Custom Date Workout",
            date = customDate,
            exerciseRequests = listOf(
                CreateWorkoutWithExercisesUseCase.ExerciseRequest(
                    libraryExercise = sampleExerciseLibrary,
                    targetSets = 3,
                    targetReps = 10
                )
            )
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isSuccess)
        val workout = result.getOrNull()!!
        assertEquals(customDate, workout.date)
    }

    @Test
    fun `invoke creates workout with notes and template ID`() = runTest {
        // Given
        val templateId = WorkoutId.generate()
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "user123",
            name = "Template Workout",
            notes = "Created from template",
            templateId = templateId,
            exerciseRequests = listOf(
                CreateWorkoutWithExercisesUseCase.ExerciseRequest(
                    libraryExercise = sampleExerciseLibrary,
                    targetSets = 3,
                    targetReps = 10,
                    notes = "Focus on form"
                )
            )
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isSuccess)
        val workout = result.getOrNull()!!
        assertEquals("Created from template", workout.notes)
        assertEquals(templateId, workout.templateId)
        assertEquals("Focus on form", workout.exercises[0].notes)
    }

    @Test
    fun `invoke handles weight memory service failure gracefully`() = runTest {
        // Given
        coEvery { weightMemoryService.getLastUsedWeight(any(), any()) } returns Result.failure(RuntimeException("Service error"))
        
        val request = CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = "user123",
            name = "Push Day",
            exerciseRequests = listOf(
                CreateWorkoutWithExercisesUseCase.ExerciseRequest(
                    libraryExercise = sampleExerciseLibrary,
                    targetSets = 3,
                    targetReps = 10
                )
            )
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isSuccess) // Should still succeed despite weight memory failure
        val workout = result.getOrNull()!!
        assertEquals("Push Day", workout.name)
        
        // Should not have weight from memory
        val exercise = workout.exercises[0]
        assertEquals(null, exercise.targetWeight)
    }
} 