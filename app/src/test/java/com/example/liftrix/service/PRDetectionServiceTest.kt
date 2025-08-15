package com.example.liftrix.service

import com.example.liftrix.domain.model.exercise.Exercise
import com.example.liftrix.domain.model.workout.Set
import com.example.liftrix.domain.model.workout.WorkoutSession
import com.example.liftrix.domain.model.analytics.PersonalRecord
import com.example.liftrix.domain.repository.ExerciseRepository
import com.example.liftrix.domain.repository.WorkoutRepository
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Comprehensive tests for PRDetectionService implementation
 * 
 * Tests personal record detection logic, historical comparison,
 * various PR scenarios, and edge cases with proper error handling.
 */
@RunWith(JUnit4::class)
class PRDetectionServiceTest {

    private lateinit var prDetectionService: PRDetectionServiceImpl
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var workoutRepository: WorkoutRepository

    private val testUserId = "user-123"
    private val testExerciseId = "bench-press-1"
    private val testExercise = Exercise(
        id = testExerciseId,
        name = "Bench Press",
        muscleGroups = listOf("Chest", "Triceps"),
        equipment = "Barbell",
        instructions = listOf("Setup", "Execute"),
        difficulty = "Intermediate"
    )

    @Before
    fun setup() {
        exerciseRepository = mockk()
        workoutRepository = mockk()
        
        prDetectionService = PRDetectionServiceImpl(
            exerciseRepository = exerciseRepository,
            workoutRepository = workoutRepository
        )
    }

    // ==========================================
    // Weight PR Detection Tests
    // ==========================================

    @Test
    fun `detectPersonalRecords should identify new 1RM weight PR`() = runTest {
        // Given - historical workouts with lower max weight
        val historicalWorkouts = listOf(
            createWorkoutSession("workout-1", testExerciseId, 
                listOf(createSet(135.0, 5), createSet(135.0, 5), createSet(135.0, 4)),
                daysAgo = 30
            ),
            createWorkoutSession("workout-2", testExerciseId,
                listOf(createSet(140.0, 3), createSet(140.0, 3), createSet(135.0, 4)),
                daysAgo = 15
            )
        )

        // Current workout with new weight PR
        val currentWorkout = createWorkoutSession("current-workout", testExerciseId,
            listOf(createSet(145.0, 1), createSet(135.0, 5), createSet(135.0, 5)),
            daysAgo = 0
        )

        coEvery { exerciseRepository.getExerciseById(testExerciseId) } returns LiftrixResult.Success(testExercise)
        coEvery { workoutRepository.getUserWorkoutHistory(testUserId, testExerciseId, any()) } returns 
            LiftrixResult.Success(historicalWorkouts)

        // When
        val result = prDetectionService.detectPersonalRecords(testUserId, currentWorkout)

        // Then
        assertTrue("PR detection should succeed", result.isSuccess)
        val prs = result.getOrNull()!!
        
        assertEquals("Should detect 1 PR", 1, prs.size)
        val pr = prs.first()
        assertEquals("Should be weight PR", PersonalRecord.Type.WEIGHT, pr.type)
        assertEquals("Should be bench press", testExercise.name, pr.exerciseName)
        assertEquals("New weight should be 145.0", 145.0, pr.newValue, 0.01)
        assertEquals("Previous weight should be 140.0", 140.0, pr.previousValue, 0.01)
    }

    @Test
    fun `detectPersonalRecords should identify volume PR`() = runTest {
        // Given - historical workouts with lower total volume
        val historicalWorkouts = listOf(
            createWorkoutSession("workout-1", testExerciseId,
                listOf(createSet(135.0, 5), createSet(135.0, 5), createSet(135.0, 5)), // 2025 lbs total
                daysAgo = 20
            )
        )

        // Current workout with higher volume
        val currentWorkout = createWorkoutSession("current-workout", testExerciseId,
            listOf(createSet(135.0, 8), createSet(135.0, 8), createSet(135.0, 6)), // 2970 lbs total
            daysAgo = 0
        )

        coEvery { exerciseRepository.getExerciseById(testExerciseId) } returns LiftrixResult.Success(testExercise)
        coEvery { workoutRepository.getUserWorkoutHistory(testUserId, testExerciseId, any()) } returns 
            LiftrixResult.Success(historicalWorkouts)

        // When
        val result = prDetectionService.detectPersonalRecords(testUserId, currentWorkout)

        // Then
        assertTrue("PR detection should succeed", result.isSuccess)
        val prs = result.getOrNull()!!
        
        val volumePR = prs.find { it.type == PersonalRecord.Type.VOLUME }
        assertNotNull("Should detect volume PR", volumePR)
        assertEquals("New volume should be higher", 2970.0, volumePR!!.newValue, 0.01)
        assertEquals("Previous volume should be 2025.0", 2025.0, volumePR.previousValue, 0.01)
    }

    @Test
    fun `detectPersonalRecords should identify rep PR at same weight`() = runTest {
        // Given - historical workouts with same weight but fewer reps
        val historicalWorkouts = listOf(
            createWorkoutSession("workout-1", testExerciseId,
                listOf(createSet(185.0, 3), createSet(165.0, 5), createSet(155.0, 8)),
                daysAgo = 10
            )
        )

        // Current workout with rep PR at same weight
        val currentWorkout = createWorkoutSession("current-workout", testExerciseId,
            listOf(createSet(185.0, 5), createSet(165.0, 6), createSet(155.0, 8)), // 5 reps at 185 vs previous 3
            daysAgo = 0
        )

        coEvery { exerciseRepository.getExerciseById(testExerciseId) } returns LiftrixResult.Success(testExercise)
        coEvery { workoutRepository.getUserWorkoutHistory(testUserId, testExerciseId, any()) } returns 
            LiftrixResult.Success(historicalWorkouts)

        // When
        val result = prDetectionService.detectPersonalRecords(testUserId, currentWorkout)

        // Then
        assertTrue("PR detection should succeed", result.isSuccess)
        val prs = result.getOrNull()!!
        
        val repPR = prs.find { it.type == PersonalRecord.Type.REPS }
        assertNotNull("Should detect rep PR", repPR)
        assertEquals("New reps should be 5", 5.0, repPR!!.newValue, 0.01)
        assertEquals("Previous reps should be 3", 3.0, repPR.previousValue, 0.01)
        assertTrue("Should include weight context", repPR.context.contains("185.0"))
    }

    @Test
    fun `detectPersonalRecords should handle first-time exercise (no history)`() = runTest {
        // Given - no historical workouts (first time doing this exercise)
        val currentWorkout = createWorkoutSession("first-workout", testExerciseId,
            listOf(createSet(95.0, 8), createSet(95.0, 8), createSet(95.0, 6)),
            daysAgo = 0
        )

        coEvery { exerciseRepository.getExerciseById(testExerciseId) } returns LiftrixResult.Success(testExercise)
        coEvery { workoutRepository.getUserWorkoutHistory(testUserId, testExerciseId, any()) } returns 
            LiftrixResult.Success(emptyList())

        // When
        val result = prDetectionService.detectPersonalRecords(testUserId, currentWorkout)

        // Then
        assertTrue("PR detection should succeed even with no history", result.isSuccess)
        val prs = result.getOrNull()!!
        
        assertEquals("Should detect multiple first-time PRs", 3, prs.size)
        assertTrue("Should include weight PR", prs.any { it.type == PersonalRecord.Type.WEIGHT })
        assertTrue("Should include volume PR", prs.any { it.type == PersonalRecord.Type.VOLUME })
        assertTrue("Should include rep PR", prs.any { it.type == PersonalRecord.Type.REPS })
    }

    // ==========================================
    // Edge Cases and Error Handling
    // ==========================================

    @Test
    fun `detectPersonalRecords should handle missing exercise data`() = runTest {
        // Given
        val currentWorkout = createWorkoutSession("current-workout", testExerciseId, 
            listOf(createSet(135.0, 5)), daysAgo = 0)

        val exerciseError = LiftrixError.NotFoundError("Exercise not found")
        coEvery { exerciseRepository.getExerciseById(testExerciseId) } returns LiftrixResult.Error(exerciseError)

        // When
        val result = prDetectionService.detectPersonalRecords(testUserId, currentWorkout)

        // Then
        assertTrue("PR detection should fail when exercise not found", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.NotFoundError
        assertNotNull("Should return not found error", error)
        assertTrue("Should indicate exercise not found", error!!.errorMessage.contains("Exercise not found"))
    }

    @Test
    fun `detectPersonalRecords should handle workout history fetch failure`() = runTest {
        // Given
        val currentWorkout = createWorkoutSession("current-workout", testExerciseId, 
            listOf(createSet(135.0, 5)), daysAgo = 0)

        coEvery { exerciseRepository.getExerciseById(testExerciseId) } returns LiftrixResult.Success(testExercise)
        
        val historyError = LiftrixError.DatabaseError("Failed to fetch workout history")
        coEvery { workoutRepository.getUserWorkoutHistory(testUserId, testExerciseId, any()) } returns 
            LiftrixResult.Error(historyError)

        // When
        val result = prDetectionService.detectPersonalRecords(testUserId, currentWorkout)

        // Then
        assertTrue("PR detection should fail when history fetch fails", result.isFailure)
        val error = result.exceptionOrNull() as? LiftrixError.DatabaseError
        assertNotNull("Should return database error", error)
    }

    @Test
    fun `detectPersonalRecords should handle empty workout session`() = runTest {
        // Given - workout session with no sets for the target exercise
        val emptyWorkout = createWorkoutSession("empty-workout", "different-exercise", 
            listOf(createSet(100.0, 5)), daysAgo = 0)

        coEvery { exerciseRepository.getExerciseById(testExerciseId) } returns LiftrixResult.Success(testExercise)

        // When
        val result = prDetectionService.detectPersonalRecords(testUserId, emptyWorkout)

        // Then
        assertTrue("PR detection should succeed but return empty results", result.isSuccess)
        val prs = result.getOrNull()!!
        assertTrue("Should return no PRs for irrelevant exercises", prs.isEmpty())
    }

    @Test
    fun `detectPersonalRecords should not detect PR when performance declined`() = runTest {
        // Given - historical workouts with better performance
        val historicalWorkouts = listOf(
            createWorkoutSession("better-workout", testExerciseId,
                listOf(createSet(155.0, 5), createSet(155.0, 5), createSet(155.0, 4)), // Better than current
                daysAgo = 10
            )
        )

        // Current workout with worse performance
        val currentWorkout = createWorkoutSession("current-workout", testExerciseId,
            listOf(createSet(135.0, 5), createSet(135.0, 5), createSet(135.0, 5)), // Worse than historical
            daysAgo = 0
        )

        coEvery { exerciseRepository.getExerciseById(testExerciseId) } returns LiftrixResult.Success(testExercise)
        coEvery { workoutRepository.getUserWorkoutHistory(testUserId, testExerciseId, any()) } returns 
            LiftrixResult.Success(historicalWorkouts)

        // When
        val result = prDetectionService.detectPersonalRecords(testUserId, currentWorkout)

        // Then
        assertTrue("PR detection should succeed", result.isSuccess)
        val prs = result.getOrNull()!!
        assertTrue("Should not detect any PRs when performance declined", prs.isEmpty())
    }

    @Test
    fun `detectPersonalRecords should handle multiple exercises in same workout`() = runTest {
        // Given - workout with multiple exercises, only one should have PR
        val squatExerciseId = "squat-1"
        val squatExercise = testExercise.copy(id = squatExerciseId, name = "Squat")
        
        val currentWorkout = WorkoutSession(
            id = "multi-exercise-workout",
            userId = testUserId,
            name = "Push/Pull Day",
            startTime = LocalDateTime.now(),
            endTime = LocalDateTime.now().plusHours(1),
            exercises = listOf(
                // Bench press with PR
                createExerciseInSession(testExerciseId, listOf(createSet(145.0, 1))),
                // Squat without PR
                createExerciseInSession(squatExerciseId, listOf(createSet(185.0, 5)))
            ),
            totalDuration = 3600
        )

        coEvery { exerciseRepository.getExerciseById(testExerciseId) } returns LiftrixResult.Success(testExercise)
        coEvery { exerciseRepository.getExerciseById(squatExerciseId) } returns LiftrixResult.Success(squatExercise)
        
        coEvery { workoutRepository.getUserWorkoutHistory(testUserId, testExerciseId, any()) } returns 
            LiftrixResult.Success(listOf(createWorkoutSession("old-bench", testExerciseId, 
                listOf(createSet(135.0, 1)), daysAgo = 30)))
                
        coEvery { workoutRepository.getUserWorkoutHistory(testUserId, squatExerciseId, any()) } returns 
            LiftrixResult.Success(listOf(createWorkoutSession("old-squat", squatExerciseId, 
                listOf(createSet(200.0, 5)), daysAgo = 30))) // Better previous performance

        // When
        val result = prDetectionService.detectPersonalRecords(testUserId, currentWorkout)

        // Then
        assertTrue("PR detection should succeed", result.isSuccess)
        val prs = result.getOrNull()!!
        
        assertEquals("Should detect PR only for bench press", 1, prs.size)
        assertEquals("PR should be for bench press", testExercise.name, prs.first().exerciseName)
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private fun createWorkoutSession(id: String, exerciseId: String, sets: List<Set>, daysAgo: Int): WorkoutSession {
        val workoutDate = LocalDateTime.now().minusDays(daysAgo.toLong())
        return WorkoutSession(
            id = id,
            userId = testUserId,
            name = "Test Workout",
            startTime = workoutDate,
            endTime = workoutDate.plusHours(1),
            exercises = listOf(createExerciseInSession(exerciseId, sets)),
            totalDuration = 3600
        )
    }

    private fun createExerciseInSession(exerciseId: String, sets: List<Set>) = 
        WorkoutSession.ExerciseInSession(
            exerciseId = exerciseId,
            sets = sets,
            restTime = 120,
            notes = ""
        )

    private fun createSet(weight: Double, reps: Int) = Set(
        id = "set-${System.nanoTime()}",
        weight = weight,
        reps = reps,
        isCompleted = true,
        restTime = 120,
        rpe = null,
        notes = ""
    )
}