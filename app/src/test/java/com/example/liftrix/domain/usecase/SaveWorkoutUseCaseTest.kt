package com.example.liftrix.domain.usecase

import com.example.liftrix.TestDataFactory
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveWorkoutUseCaseTest {

    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var saveWorkoutUseCase: SaveWorkoutUseCase

    @Before
    fun setup() {
        workoutRepository = mockk()
        saveWorkoutUseCase = SaveWorkoutUseCase(workoutRepository)
    }

    @Test
    fun `should save workout successfully when valid`() = runTest {
        val workout = TestDataFactory.sampleWorkout
        coEvery { workoutRepository.saveWorkout(workout) } returns Result.success(Unit)

        val result = saveWorkoutUseCase(workout)

        assertTrue(result.isSuccess)
        coVerify { workoutRepository.saveWorkout(workout) }
    }

    @Test
    fun `should fail when workout has blank userId`() = runTest {
        val workout = TestDataFactory.sampleWorkout.copy(userId = "")

        val result = saveWorkoutUseCase(workout)

        assertTrue(result.isFailure)
        assertEquals("Workout must have a valid user ID", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should fail when workout has empty userId`() = runTest {
        val workout = TestDataFactory.sampleWorkout.copy(userId = "   ")

        val result = saveWorkoutUseCase(workout)

        assertTrue(result.isFailure)
        assertEquals("Workout must have a valid user ID", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should propagate repository failure`() = runTest {
        val workout = TestDataFactory.sampleWorkout
        val exception = Exception("Database error")
        coEvery { workoutRepository.saveWorkout(workout) } returns Result.failure(exception)

        val result = saveWorkoutUseCase(workout)

        assertTrue(result.isFailure)
        assertEquals("Database error", result.exceptionOrNull()?.message)
        coVerify { workoutRepository.saveWorkout(workout) }
    }

    @Test
    fun `should handle repository throwing exception`() = runTest {
        val workout = TestDataFactory.sampleWorkout
        coEvery { workoutRepository.saveWorkout(workout) } throws RuntimeException("Unexpected error")

        val result = saveWorkoutUseCase(workout)

        assertTrue(result.isFailure)
        assertEquals("Unexpected error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should not call repository when userId validation fails`() = runTest {
        val workout = TestDataFactory.sampleWorkout.copy(userId = "")

        saveWorkoutUseCase(workout)

        coVerify(exactly = 0) { workoutRepository.saveWorkout(any()) }
    }

    @Test
    fun `should handle valid workout with all fields populated`() = runTest {
        val workout = TestDataFactory.completedWorkout
        coEvery { workoutRepository.saveWorkout(workout) } returns Result.success(Unit)

        val result = saveWorkoutUseCase(workout)

        assertTrue(result.isSuccess)
        coVerify { workoutRepository.saveWorkout(workout) }
    }

    @Test
    fun `should handle minimal valid workout`() = runTest {
        val minimalWorkout = TestDataFactory.createWorkout(
            userId = "valid-user-id",
            name = "Minimal Workout",
            exerciseCount = 1
        )
        coEvery { workoutRepository.saveWorkout(minimalWorkout) } returns Result.success(Unit)

        val result = saveWorkoutUseCase(minimalWorkout)

        assertTrue(result.isSuccess)
        coVerify { workoutRepository.saveWorkout(minimalWorkout) }
    }
} 