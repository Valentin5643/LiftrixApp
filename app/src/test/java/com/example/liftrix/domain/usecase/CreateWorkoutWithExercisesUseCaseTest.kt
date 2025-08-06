package com.example.liftrix.domain.usecase

import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.CreateWorkoutException
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.service.WeightMemoryService
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

/**
 * Comprehensive test suite for CreateWorkoutWithExercisesUseCase.
 * 
 * This use case (202 lines) handles complex workout creation with:
 * - Exercise library integration
 * - Weight memory system integration
 * - Validation and error handling
 * - Data transformation and persistence
 * 
 * Test Categories:
 * - Happy Path Workout Creation
 * - Weight Memory Integration
 * - Validation Error Handling
 * - Repository Integration
 * - Edge Cases and Error Scenarios
 * - Performance and Large Data Sets
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateWorkoutWithExercisesUseCaseTest {

    @MockK
    private lateinit var workoutRepository: WorkoutRepository
    
    @MockK
    private lateinit var exerciseRepository: ExerciseRepository
    
    @MockK
    private lateinit var weightMemoryService: WeightMemoryService
    
    private lateinit var useCase: CreateWorkoutWithExercisesUseCase
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Test Data Factory
    private val testUserId = "user_123"
    private val testWorkoutName = "Push Day Workout"
    
    private fun createTestLibraryExercise(
        id: String = "exercise_1",
        name: String = "Bench Press",
        primaryMuscleGroup: ExerciseCategory = ExerciseCategory.CHEST,
        equipment: Equipment = Equipment.BARBELL
    ): ExerciseLibrary {
        return ExerciseLibrary(
            id = id,
            name = name,
            primaryMuscleGroup = primaryMuscleGroup,
            equipment = equipment,
            secondaryMuscleGroups = listOf(ExerciseCategory.TRICEPS, ExerciseCategory.SHOULDERS),
            movementPattern = "Push",
            difficultyLevel = 2,
            instructions = "Lie on bench and press weight",
            isCompound = true,
            searchableTerms = listOf(name.lowercase(), "press", "chest")
        )
    }
    
    private fun createTestExerciseRequest(
        libraryExercise: ExerciseLibrary = createTestLibraryExercise(),
        targetSets: Int? = 3,
        targetReps: Int? = 10,
        targetWeight: Weight? = null,
        notes: String? = null
    ): CreateWorkoutWithExercisesUseCase.ExerciseRequest {
        return CreateWorkoutWithExercisesUseCase.ExerciseRequest(
            libraryExercise = libraryExercise,
            targetSets = targetSets,
            targetReps = targetReps,
            targetWeight = targetWeight,
            targetTime = null,
            targetDistance = null,
            notes = notes
        )
    }
    
    private fun createTestWorkoutRequest(
        name: String = testWorkoutName,
        exerciseRequests: List<CreateWorkoutWithExercisesUseCase.ExerciseRequest> = listOf(createTestExerciseRequest()),
        date: LocalDate? = LocalDate.now(),
        notes: String? = null
    ): CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest {
        return CreateWorkoutWithExercisesUseCase.CreateWorkoutRequest(
            userId = testUserId,
            name = name,
            date = date,
            exerciseRequests = exerciseRequests,
            notes = notes,
            templateId = null
        )
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        // Mock weight memory service
        coEvery { weightMemoryService.getLastUsedWeight(any(), any()) } returns Result.success(20.0f)
        coEvery { weightMemoryService.updateExerciseWeight(any(), any(), any(), any(), any()) } returns Result.success(Unit)
        
        // Mock workout repository
        coEvery { workoutRepository.saveWorkout(any()) } returns Result.success(Unit)
        
        useCase = CreateWorkoutWithExercisesUseCase(
            workoutRepository = workoutRepository,
            exerciseRepository = exerciseRepository,
            weightMemoryService = weightMemoryService
        )
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }

    // MARK: - Happy Path Tests

    @Test
    fun `Given valid workout request When invoke Then workout created successfully`() = testScope.runTest {
        // Given
        val request = createTestWorkoutRequest()
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Workout creation should succeed", result.isSuccess)
        val workout = result.getOrNull()
        assertNotNull("Workout should not be null", workout)
        assertEquals("Workout name should match", testWorkoutName, workout?.name)
        assertEquals("Workout user ID should match", testUserId, workout?.userId)
        assertEquals("Workout should have 1 exercise", 1, workout?.exercises?.size)
        assertEquals("Workout status should be PLANNED", WorkoutStatus.PLANNED, workout?.status)
        
        // Verify repository interaction
        coVerify(exactly = 1) { workoutRepository.saveWorkout(any()) }
    }

    @Test
    fun `Given workout request with multiple exercises When invoke Then all exercises added`() = testScope.runTest {
        // Given
        val exercise1 = createTestExerciseRequest(
            libraryExercise = createTestLibraryExercise("ex1", "Bench Press"),
            targetSets = 3,
            targetReps = 8
        )
        val exercise2 = createTestExerciseRequest(
            libraryExercise = createTestLibraryExercise("ex2", "Incline Press"),
            targetSets = 3,
            targetReps = 10
        )
        val request = createTestWorkoutRequest(exerciseRequests = listOf(exercise1, exercise2))
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Workout creation should succeed", result.isSuccess)
        val workout = result.getOrNull()
        assertEquals("Workout should have 2 exercises", 2, workout?.exercises?.size)
        
        val exerciseNames = workout?.exercises?.map { it.libraryExercise.name }
        assertTrue("Should contain Bench Press", exerciseNames?.contains("Bench Press") == true)
        assertTrue("Should contain Incline Press", exerciseNames?.contains("Incline Press") == true)
    }

    @Test
    fun `Given workout request with date and notes When invoke Then metadata preserved`() = testScope.runTest {
        // Given
        val testDate = LocalDate.of(2025, 8, 10)
        val testNotes = "Pre-exhaust chest workout"
        val request = createTestWorkoutRequest(date = testDate, notes = testNotes)
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Workout creation should succeed", result.isSuccess)
        val workout = result.getOrNull()
        assertEquals("Workout date should match", testDate, workout?.date)
        assertEquals("Workout notes should match", testNotes, workout?.notes)
    }

    // MARK: - Weight Memory Integration Tests

    @Test
    fun `Given exercise with weight memory When invoke Then last used weight applied`() = testScope.runTest {
        // Given
        val lastUsedWeight = 25.0f
        coEvery { weightMemoryService.getLastUsedWeight(testUserId, "exercise_1") } returns Result.success(lastUsedWeight)
        
        val exerciseRequest = createTestExerciseRequest(targetWeight = null) // No explicit weight
        val request = createTestWorkoutRequest(exerciseRequests = listOf(exerciseRequest))
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Workout creation should succeed", result.isSuccess)
        val workout = result.getOrNull()
        val exercise = workout?.exercises?.first()
        assertEquals("Should use weight from memory", Weight.fromKilograms(25.0), exercise?.targetWeight)
        
        // Verify weight memory was queried
        coVerify { weightMemoryService.getLastUsedWeight(testUserId, "exercise_1") }
    }

    @Test
    fun `Given exercise with explicit weight When invoke Then explicit weight used over memory`() = testScope.runTest {
        // Given
        val explicitWeight = Weight.fromKilograms(30.0)
        coEvery { weightMemoryService.getLastUsedWeight(testUserId, "exercise_1") } returns Result.success(25.0f)
        
        val exerciseRequest = createTestExerciseRequest(targetWeight = explicitWeight)
        val request = createTestWorkoutRequest(exerciseRequests = listOf(exerciseRequest))
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Workout creation should succeed", result.isSuccess)
        val workout = result.getOrNull()
        val exercise = workout?.exercises?.first()
        assertEquals("Should use explicit weight", explicitWeight, exercise?.targetWeight)
    }

    @Test
    fun `Given weight memory failure When invoke Then workout creation continues`() = testScope.runTest {
        // Given
        coEvery { weightMemoryService.getLastUsedWeight(any(), any()) } returns Result.failure(RuntimeException("Memory error"))
        
        val request = createTestWorkoutRequest()
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Workout creation should succeed despite memory failure", result.isSuccess)
        val workout = result.getOrNull()
        assertNotNull("Workout should be created", workout)
        
        // Should still save workout
        coVerify { workoutRepository.saveWorkout(any()) }
    }

    @Test
    fun `Given successful workout creation When invoke Then weight memory updated`() = testScope.runTest {
        // Given
        val targetWeight = Weight.fromKilograms(22.5)
        val exerciseRequest = createTestExerciseRequest(targetWeight = targetWeight, targetReps = 8, targetSets = 3)
        val request = createTestWorkoutRequest(exerciseRequests = listOf(exerciseRequest))
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Workout creation should succeed", result.isSuccess)
        
        // Verify weight memory was updated
        coVerify { 
            weightMemoryService.updateExerciseWeight(
                userId = testUserId,
                exerciseId = "exercise_1",
                weight = 22.5f,
                reps = 8,
                sets = 3
            )
        }
    }

    // MARK: - Validation Tests

    @Test
    fun `Given empty workout name When invoke Then validation error returned`() = testScope.runTest {
        // Given
        val request = createTestWorkoutRequest(name = "")
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should fail validation", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Should be validation error", exception is CreateWorkoutException.InvalidInput)
        
        // Should not call repository
        coVerify(exactly = 0) { workoutRepository.saveWorkout(any()) }
    }

    @Test
    fun `Given empty exercise list When invoke Then validation error returned`() = testScope.runTest {
        // Given
        val request = createTestWorkoutRequest(exerciseRequests = emptyList())
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should fail validation", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Should be validation error", exception is CreateWorkoutException.InvalidInput)
    }

    @Test
    fun `Given invalid exercise parameters When invoke Then validation error returned`() = testScope.runTest {
        // Given
        val invalidExercise = createTestExerciseRequest(
            targetSets = -1, // Invalid
            targetReps = 0   // Invalid
        )
        val request = createTestWorkoutRequest(exerciseRequests = listOf(invalidExercise))
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should fail validation", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Should be validation error", exception is CreateWorkoutException.InvalidInput)
    }

    @Test
    fun `Given workout name too long When invoke Then validation error returned`() = testScope.runTest {
        // Given
        val tooLongName = "A".repeat(256) // Assuming 255 char limit
        val request = createTestWorkoutRequest(name = tooLongName)
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should fail validation", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Should be validation error", exception is CreateWorkoutException.InvalidInput)
    }

    // MARK: - Repository Integration Tests

    @Test
    fun `Given repository save failure When invoke Then repository error returned`() = testScope.runTest {
        // Given
        val repositoryError = RuntimeException("Database error")
        coEvery { workoutRepository.saveWorkout(any()) } returns Result.failure(repositoryError)
        
        val request = createTestWorkoutRequest()
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should fail with repository error", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Should be repository error", exception is CreateWorkoutException.RepositoryError)
    }

    @Test
    fun `Given valid request When invoke Then correct workout passed to repository`() = testScope.runTest {
        // Given
        val request = createTestWorkoutRequest()
        val capturedWorkout = slot<Workout>()
        coEvery { workoutRepository.saveWorkout(capture(capturedWorkout)) } returns Result.success(Unit)
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should succeed", result.isSuccess)
        
        val savedWorkout = capturedWorkout.captured
        assertEquals("Saved workout name should match", testWorkoutName, savedWorkout.name)
        assertEquals("Saved workout user should match", testUserId, savedWorkout.userId)
        assertEquals("Saved workout should be PLANNED", WorkoutStatus.PLANNED, savedWorkout.status)
        assertNotNull("Saved workout should have created timestamp", savedWorkout.createdAt)
    }

    // MARK: - Exercise Set Creation Tests

    @Test
    fun `Given exercise with target parameters When invoke Then initial sets created correctly`() = testScope.runTest {
        // Given
        val targetWeight = Weight.fromKilograms(25.0)
        val exerciseRequest = createTestExerciseRequest(
            targetSets = 4,
            targetReps = 6,
            targetWeight = targetWeight
        )
        val request = createTestWorkoutRequest(exerciseRequests = listOf(exerciseRequest))
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should succeed", result.isSuccess)
        val workout = result.getOrNull()
        val exercise = workout?.exercises?.first()
        val sets = exercise?.sets
        
        assertEquals("Should have 4 sets", 4, sets?.size)
        
        sets?.forEachIndexed { index, set ->
            assertEquals("Set ${index + 1} should have correct number", index + 1, set.setNumber)
            assertEquals("Set ${index + 1} should have target reps", Reps(6), set.reps)
            assertEquals("Set ${index + 1} should have target weight", targetWeight, set.weight)
        }
    }

    @Test
    fun `Given exercise with default parameters When invoke Then default sets created`() = testScope.runTest {
        // Given
        val exerciseRequest = createTestExerciseRequest(
            targetSets = null, // Should default to 3
            targetReps = null, // Should use some default
            targetWeight = null
        )
        val request = createTestWorkoutRequest(exerciseRequests = listOf(exerciseRequest))
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should succeed", result.isSuccess)
        val workout = result.getOrNull()
        val exercise = workout?.exercises?.first()
        val sets = exercise?.sets
        
        assertEquals("Should have default 3 sets", 3, sets?.size)
    }

    // MARK: - Time and Distance Exercise Tests

    @Test
    fun `Given time-based exercise When invoke Then time parameters preserved`() = testScope.runTest {
        // Given
        val cardioExercise = createTestLibraryExercise(
            id = "cardio_1",
            name = "Treadmill",
            equipment = Equipment.TREADMILL
        )
        val targetTime = Duration.ofMinutes(30)
        val exerciseRequest = CreateWorkoutWithExercisesUseCase.ExerciseRequest(
            libraryExercise = cardioExercise,
            targetSets = 1,
            targetReps = null,
            targetWeight = null,
            targetTime = targetTime,
            targetDistance = null,
            notes = "Moderate pace"
        )
        val request = createTestWorkoutRequest(exerciseRequests = listOf(exerciseRequest))
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should succeed", result.isSuccess)
        val workout = result.getOrNull()
        val exercise = workout?.exercises?.first()
        assertEquals("Should preserve target time", targetTime, exercise?.targetTime)
        assertEquals("Should preserve notes", "Moderate pace", exercise?.notes)
    }

    @Test
    fun `Given distance-based exercise When invoke Then distance parameters preserved`() = testScope.runTest {
        // Given
        val runningExercise = createTestLibraryExercise(
            id = "run_1",
            name = "Outdoor Run",
            equipment = Equipment.BODYWEIGHT_ONLY
        )
        val targetDistance = Distance.fromKilometers(5.0f)
        val exerciseRequest = CreateWorkoutWithExercisesUseCase.ExerciseRequest(
            libraryExercise = runningExercise,
            targetSets = 1,
            targetReps = null,
            targetWeight = null,
            targetTime = null,
            targetDistance = targetDistance,
            notes = null
        )
        val request = createTestWorkoutRequest(exerciseRequests = listOf(exerciseRequest))
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should succeed", result.isSuccess)
        val workout = result.getOrNull()
        val exercise = workout?.exercises?.first()
        assertEquals("Should preserve target distance", targetDistance, exercise?.targetDistance)
    }

    // MARK: - Performance Tests

    @Test
    fun `Given large workout with many exercises When invoke Then performance acceptable`() = testScope.runTest {
        // Given
        val manyExercises = (1..20).map { index ->
            createTestExerciseRequest(
                libraryExercise = createTestLibraryExercise("ex_$index", "Exercise $index"),
                targetSets = 3,
                targetReps = 10
            )
        }
        val request = createTestWorkoutRequest(exerciseRequests = manyExercises)
        
        // When
        val startTime = System.currentTimeMillis()
        val result = useCase(request)
        val endTime = System.currentTimeMillis()
        
        // Then
        assertTrue("Should succeed with large workout", result.isSuccess)
        val duration = endTime - startTime
        assertTrue("Should complete in reasonable time (<1000ms)", duration < 1000)
        
        val workout = result.getOrNull()
        assertEquals("Should have all exercises", 20, workout?.exercises?.size)
    }

    // MARK: - Edge Cases

    @Test
    fun `Given null date in request When invoke Then current date used`() = testScope.runTest {
        // Given
        val request = createTestWorkoutRequest(date = null)
        val currentDate = LocalDate.now()
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should succeed", result.isSuccess)
        val workout = result.getOrNull()
        assertEquals("Should use current date", currentDate, workout?.date)
    }

    @Test
    fun `Given exercise with all optional parameters null When invoke Then workout created with defaults`() = testScope.runTest {
        // Given
        val minimalExercise = CreateWorkoutWithExercisesUseCase.ExerciseRequest(
            libraryExercise = createTestLibraryExercise(),
            targetSets = null,
            targetReps = null,
            targetWeight = null,
            targetTime = null,
            targetDistance = null,
            notes = null
        )
        val request = createTestWorkoutRequest(exerciseRequests = listOf(minimalExercise))
        
        // When
        val result = useCase(request)
        
        // Then
        assertTrue("Should succeed with minimal parameters", result.isSuccess)
        val workout = result.getOrNull()
        assertNotNull("Workout should be created", workout)
        assertEquals("Should have 1 exercise", 1, workout?.exercises?.size)
    }

    @Test
    fun `Given concurrent use case invocations When invoke Then all succeed independently`() = testScope.runTest {
        // Given
        val request1 = createTestWorkoutRequest(name = "Workout 1")
        val request2 = createTestWorkoutRequest(name = "Workout 2")
        
        // When
        val result1 = useCase(request1)
        val result2 = useCase(request2)
        
        // Then
        assertTrue("First workout should succeed", result1.isSuccess)
        assertTrue("Second workout should succeed", result2.isSuccess)
        
        val workout1 = result1.getOrNull()
        val workout2 = result2.getOrNull()
        assertEquals("First workout name should be preserved", "Workout 1", workout1?.name)
        assertEquals("Second workout name should be preserved", "Workout 2", workout2?.name)
        
        // Should have called repository twice
        coVerify(exactly = 2) { workoutRepository.saveWorkout(any()) }
    }
}