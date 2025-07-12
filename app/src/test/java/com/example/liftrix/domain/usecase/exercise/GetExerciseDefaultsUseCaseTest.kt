package com.example.liftrix.domain.usecase.exercise

import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.entity.ExerciseEntity
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.WorkoutRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for GetExerciseDefaultsUseCase
 * 
 * Tests smart defaults calculation including:
 * - History-based defaults with recency weighting
 * - Exercise type-based fallbacks
 * - Error handling and graceful degradation
 * - Edge cases with insufficient data
 */
class GetExerciseDefaultsUseCaseTest {

    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var exerciseSetDao: ExerciseSetDao
    private lateinit var useCase: GetExerciseDefaultsUseCase

    private val testUserId = "user123"
    private val testExerciseId = ExerciseId.generate()
    
    private val benchPressExercise = ExerciseLibrary(
        id = "bench-press",
        name = "Bench Press",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BARBELL,
        secondaryMuscleGroups = listOf(ExerciseCategory.TRICEPS),
        movementPattern = "Push",
        difficultyLevel = 5,
        instructions = "Bench press instructions",
        isCompound = true,
        searchableTerms = listOf("bench", "press")
    )
    
    private val bicepCurlExercise = ExerciseLibrary(
        id = "bicep-curl",
        name = "Bicep Curl",
        primaryMuscleGroup = ExerciseCategory.BICEPS,
        equipment = Equipment.DUMBBELLS,
        secondaryMuscleGroups = emptyList(),
        movementPattern = "Pull",
        difficultyLevel = 2,
        instructions = "Bicep curl instructions",
        isCompound = false,
        searchableTerms = listOf("bicep", "curl")
    )

    @Before
    fun setUp() {
        workoutRepository = mockk()
        exerciseDao = mockk()
        exerciseSetDao = mockk()
        
        useCase = GetExerciseDefaultsUseCase(
            workoutRepository = workoutRepository,
            exerciseDao = exerciseDao,
            exerciseSetDao = exerciseSetDao
        )
    }

    @Test
    fun `invoke with sufficient history returns history-based defaults`() = runTest {
        // Arrange
        val exerciseHistory = createMockExerciseHistory(5) // 5 entries, enough for history
        val mockSets = createMockSetsForHistory()
        
        coEvery { 
            exerciseDao.getExerciseHistory(testUserId, benchPressExercise.id, 10) 
        } returns exerciseHistory
        
        exerciseHistory.forEachIndexed { index, exercise ->
            coEvery { 
                exerciseSetDao.getSetsByExercise(exercise.id) 
            } returns mockSets[index]
        }

        // Act
        val result = useCase(testExerciseId, testUserId, benchPressExercise)

        // Assert
        assertTrue(result.isSuccess)
        val defaults = result.getOrNull()!!
        assertEquals(DefaultSource.HISTORY, defaults.source)
        assertTrue(defaults.sets in ExerciseDefaults.MIN_SETS..ExerciseDefaults.MAX_SETS)
        assertTrue(defaults.reps.count > 0)
        assertTrue(defaults.restTimeSeconds in ExerciseDefaults.MIN_REST_SECONDS..ExerciseDefaults.MAX_REST_SECONDS)
        assertNotNull(defaults.weight) // Should have weight from history
        
        coVerify { exerciseDao.getExerciseHistory(testUserId, benchPressExercise.id, 10) }
    }

    @Test
    fun `invoke with insufficient history falls back to exercise type defaults`() = runTest {
        // Arrange
        val exerciseHistory = createMockExerciseHistory(2) // Only 2 entries, insufficient
        
        coEvery { 
            exerciseDao.getExerciseHistory(testUserId, benchPressExercise.id, 10) 
        } returns exerciseHistory

        // Act
        val result = useCase(testExerciseId, testUserId, benchPressExercise)

        // Assert
        assertTrue(result.isSuccess)
        val defaults = result.getOrNull()!!
        assertEquals(DefaultSource.EXERCISE_TYPE, defaults.source)
        // Should be compound exercise defaults for chest
        assertEquals(4, defaults.sets) // Compound movements get 4 sets
        assertEquals(6, defaults.reps.count) // Lower reps for compound
        assertEquals(120, defaults.restTimeSeconds) // Longer rest for compound
        assertNull(defaults.weight) // No weight history
        
        coVerify { exerciseDao.getExerciseHistory(testUserId, benchPressExercise.id, 10) }
    }

    @Test
    fun `invoke with no history uses exercise type defaults for isolation exercise`() = runTest {
        // Arrange
        coEvery { 
            exerciseDao.getExerciseHistory(testUserId, bicepCurlExercise.id, 10) 
        } returns emptyList()

        // Act
        val result = useCase(testExerciseId, testUserId, bicepCurlExercise)

        // Assert
        assertTrue(result.isSuccess)
        val defaults = result.getOrNull()!!
        assertEquals(DefaultSource.EXERCISE_TYPE, defaults.source)
        // Should be isolation exercise defaults for arms
        assertEquals(3, defaults.sets) // Standard sets for isolation
        assertEquals(10, defaults.reps.count) // Standard reps for isolation
        assertEquals(60, defaults.restTimeSeconds) // Shorter rest for arms
        assertNull(defaults.weight)
    }

    @Test
    fun `invoke with bodyweight exercise returns appropriate defaults`() = runTest {
        // Arrange
        val pushUpExercise = benchPressExercise.copy(
            equipment = Equipment.BODYWEIGHT_ONLY,
            name = "Push-up"
        )
        
        coEvery { 
            exerciseDao.getExerciseHistory(testUserId, pushUpExercise.id, 10) 
        } returns emptyList()

        // Act
        val result = useCase(testExerciseId, testUserId, pushUpExercise)

        // Assert
        assertTrue(result.isSuccess)
        val defaults = result.getOrNull()!!
        assertEquals(DefaultSource.EXERCISE_TYPE, defaults.source)
        assertEquals(3, defaults.sets)
        assertEquals(12, defaults.reps.count) // Bodyweight default
        assertEquals(60, defaults.restTimeSeconds) // Bodyweight rest time
        assertNull(defaults.weight) // No weight for bodyweight
    }

    @Test
    fun `invoke with core exercise returns appropriate defaults`() = runTest {
        // Arrange
        val plankExercise = benchPressExercise.copy(
            primaryMuscleGroup = ExerciseCategory.CORE,
            equipment = Equipment.BODYWEIGHT_ONLY,
            name = "Plank"
        )
        
        coEvery { 
            exerciseDao.getExerciseHistory(testUserId, plankExercise.id, 10) 
        } returns emptyList()

        // Act
        val result = useCase(testExerciseId, testUserId, plankExercise)

        // Assert
        assertTrue(result.isSuccess)
        val defaults = result.getOrNull()!!
        assertEquals(DefaultSource.EXERCISE_TYPE, defaults.source)
        assertEquals(3, defaults.sets)
        assertEquals(20, defaults.reps.count) // Core exercises get higher reps
        assertEquals(60, defaults.restTimeSeconds)
        assertNull(defaults.weight)
    }

    @Test
    fun `invoke handles dao exceptions gracefully`() = runTest {
        // Arrange
        coEvery { 
            exerciseDao.getExerciseHistory(testUserId, benchPressExercise.id, 10) 
        } throws RuntimeException("Database error")

        // Act
        val result = useCase(testExerciseId, testUserId, benchPressExercise)

        // Assert
        assertTrue(result.isSuccess)
        val defaults = result.getOrNull()!!
        assertEquals(DefaultSource.FALLBACK, defaults.source)
        assertEquals(3, defaults.sets) // Fallback defaults
        assertEquals(10, defaults.reps.count)
        assertEquals(90, defaults.restTimeSeconds)
        assertNull(defaults.weight)
    }

    @Test
    fun `invoke with corrupted set data falls back gracefully`() = runTest {
        // Arrange
        val exerciseHistory = createMockExerciseHistory(5)
        val corruptedSets = listOf(
            listOf<ExerciseSetEntity>(), // Empty sets
            listOf(createMockSet(0, null, null)), // Invalid reps/weight
            listOf(createMockSet(1, -5, -10f)) // Negative values
        )
        
        coEvery { 
            exerciseDao.getExerciseHistory(testUserId, benchPressExercise.id, 10) 
        } returns exerciseHistory
        
        exerciseHistory.take(3).forEachIndexed { index, exercise ->
            coEvery { 
                exerciseSetDao.getSetsByExercise(exercise.id) 
            } returns corruptedSets[index]
        }
        
        // Remaining exercises should have valid data
        exerciseHistory.drop(3).forEach { exercise ->
            coEvery { 
                exerciseSetDao.getSetsByExercise(exercise.id) 
            } returns emptyList()
        }

        // Act
        val result = useCase(testExerciseId, testUserId, benchPressExercise)

        // Assert
        assertTrue(result.isSuccess)
        val defaults = result.getOrNull()!!
        assertEquals(DefaultSource.EXERCISE_TYPE, defaults.source) // Should fall back to type defaults
    }

    @Test
    fun `history-based defaults apply recency weighting correctly`() = runTest {
        // Arrange - create history with recent higher reps and older lower reps
        val oldTimestamp = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 // 30 days ago
        val recentTimestamp = System.currentTimeMillis() - 1L * 24 * 60 * 60 * 1000 // 1 day ago
        
        val exerciseHistory = listOf(
            createMockExercise(1, oldTimestamp),
            createMockExercise(2, oldTimestamp),
            createMockExercise(3, recentTimestamp),
            createMockExercise(4, recentTimestamp),
            createMockExercise(5, recentTimestamp)
        )
        
        // Old workouts: 5 reps, Recent workouts: 12 reps
        val oldSets = listOf(createMockSet(1, 5, 100f), createMockSet(2, 5, 100f))
        val recentSets = listOf(createMockSet(1, 12, 120f), createMockSet(2, 12, 120f))
        
        coEvery { 
            exerciseDao.getExerciseHistory(testUserId, benchPressExercise.id, 10) 
        } returns exerciseHistory
        
        // Old exercises get old sets
        coEvery { exerciseSetDao.getSetsByExercise(exerciseHistory[0].id) } returns oldSets
        coEvery { exerciseSetDao.getSetsByExercise(exerciseHistory[1].id) } returns oldSets
        
        // Recent exercises get recent sets
        coEvery { exerciseSetDao.getSetsByExercise(exerciseHistory[2].id) } returns recentSets
        coEvery { exerciseSetDao.getSetsByExercise(exerciseHistory[3].id) } returns recentSets
        coEvery { exerciseSetDao.getSetsByExercise(exerciseHistory[4].id) } returns recentSets

        // Act
        val result = useCase(testExerciseId, testUserId, benchPressExercise)

        // Assert
        assertTrue(result.isSuccess)
        val defaults = result.getOrNull()!!
        assertEquals(DefaultSource.HISTORY, defaults.source)
        
        // Recent values (12 reps) should be weighted more heavily than old values (5 reps)
        // So average should be closer to 12 than 8.5 (unweighted average)
        assertTrue("Expected reps > 9 due to recency weighting, got ${defaults.reps.count}", 
                   defaults.reps.count > 9)
    }

    @Test
    fun `ExerciseDefaults applyToTemplateExercise works correctly`() {
        // Arrange
        val originalExercise = TemplateExercise(
            exerciseId = testExerciseId,
            name = "Test Exercise",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.BARBELL,
            targetSets = null,
            targetReps = null,
            targetWeight = null,
            restTimeSeconds = null,
            notes = null,
            orderIndex = 0
        )
        
        val defaults = ExerciseDefaults(
            sets = 4,
            reps = Reps(8),
            weight = Weight(100.0),
            restTimeSeconds = 120,
            source = DefaultSource.HISTORY
        )

        // Act
        val updatedExercise = defaults.applyToTemplateExercise(originalExercise)

        // Assert
        assertEquals(4, updatedExercise.targetSets)
        assertEquals(8, updatedExercise.targetReps?.count)
        assertEquals(100.0, updatedExercise.targetWeight?.kilograms, 0.01)
        assertEquals(120, updatedExercise.restTimeSeconds)
        // Other fields should remain unchanged
        assertEquals(originalExercise.exerciseId, updatedExercise.exerciseId)
        assertEquals(originalExercise.name, updatedExercise.name)
        assertEquals(originalExercise.notes, updatedExercise.notes)
    }

    // Helper methods for creating test data

    private fun createMockExerciseHistory(count: Int): List<ExerciseEntity> {
        return (1..count).map { index ->
            createMockExercise(index.toLong(), System.currentTimeMillis() - (index * 24 * 60 * 60 * 1000L))
        }
    }

    private fun createMockExercise(id: Long, timestamp: Long): ExerciseEntity {
        return ExerciseEntity(
            id = id,
            workoutId = "workout$id",
            exerciseLibraryId = benchPressExercise.id,
            orderIndex = 0,
            targetSets = 3,
            targetReps = 10,
            targetWeightKg = 100f,
            targetTimeSeconds = null,
            targetDistanceMeters = null,
            notes = null,
            lastUsedWeightKg = null,
            weightMemoryUpdatedAt = null,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }

    private fun createMockSetsForHistory(): List<List<ExerciseSetEntity>> {
        return listOf(
            listOf(createMockSet(1, 8, 80f), createMockSet(2, 8, 80f), createMockSet(3, 8, 80f)),
            listOf(createMockSet(1, 10, 85f), createMockSet(2, 10, 85f), createMockSet(3, 9, 85f)),
            listOf(createMockSet(1, 6, 90f), createMockSet(2, 6, 90f), createMockSet(3, 6, 90f)),
            listOf(createMockSet(1, 12, 75f), createMockSet(2, 12, 75f), createMockSet(3, 10, 75f)),
            listOf(createMockSet(1, 8, 82f), createMockSet(2, 8, 82f), createMockSet(3, 8, 82f))
        )
    }

    private fun createMockSet(setNumber: Int, reps: Int?, weightKg: Float?): ExerciseSetEntity {
        return ExerciseSetEntity(
            id = setNumber.toLong(),
            exerciseId = 1L,
            setNumber = setNumber,
            reps = reps,
            weightKg = weightKg,
            timeSeconds = null,
            distanceMeters = null,
            rpe = null,
            completedAt = if (reps != null && reps > 0) System.currentTimeMillis() else null,
            notes = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
} 