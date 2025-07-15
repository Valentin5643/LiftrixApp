package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateWorkoutUseCaseTest {
    
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var errorHandler: ErrorHandler
    private lateinit var createWorkoutUseCase: CreateWorkoutUseCase
    
    @Before
    fun setup() {
        workoutRepository = mockk()
        errorHandler = mockk()
        createWorkoutUseCase = CreateWorkoutUseCase(workoutRepository, errorHandler)
    }
    
    @Test
    fun `when valid request provided, should create workout successfully`() = runTest {
        // Given
        val request = CreateWorkoutRequest(
            userId = "user123",
            name = "Push Day",
            date = LocalDate.now(),
            status = WorkoutStatus.PLANNED
        )
        val expectedWorkout = mockk<Workout>()
        
        coEvery { workoutRepository.getActiveWorkout(any()) } returns LiftrixResult.success(null)
        coEvery { workoutRepository.getWorkoutsByDate(any(), any()) } returns LiftrixResult.success(emptyList())
        coEvery { workoutRepository.createWorkout(any()) } returns LiftrixResult.success(expectedWorkout)
        
        // When
        val result = createWorkoutUseCase(request)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedWorkout, result.getOrNull())
        coVerify { workoutRepository.createWorkout(any()) }
    }
    
    @Test
    fun `when user ID is blank, should return validation error`() = runTest {
        // Given
        val request = CreateWorkoutRequest(
            userId = "",
            name = "Push Day",
            date = LocalDate.now()
        )
        
        // When
        val result = createWorkoutUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.violations.any { it.contains("User ID is required") })
    }
    
    @Test
    fun `when workout name is blank, should return validation error`() = runTest {
        // Given
        val request = CreateWorkoutRequest(
            userId = "user123",
            name = "",
            date = LocalDate.now()
        )
        
        // When
        val result = createWorkoutUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.violations.any { it.contains("Workout name is required") })
    }
    
    @Test
    fun `when workout name exceeds max length, should return validation error`() = runTest {
        // Given
        val longName = "a".repeat(Workout.MAX_NAME_LENGTH + 1)
        val request = CreateWorkoutRequest(
            userId = "user123",
            name = longName,
            date = LocalDate.now()
        )
        
        // When
        val result = createWorkoutUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.violations.any { it.contains("cannot exceed") })
    }
    
    @Test
    fun `when creating active workout and user already has active workout, should return business logic error`() = runTest {
        // Given
        val request = CreateWorkoutRequest(
            userId = "user123",
            name = "Push Day",
            date = LocalDate.now(),
            status = WorkoutStatus.IN_PROGRESS
        )
        val existingActiveWorkout = mockk<Workout> {
            coEvery { id } returns WorkoutId.generate()
        }
        
        coEvery { workoutRepository.getActiveWorkout("user123") } returns LiftrixResult.success(existingActiveWorkout)
        
        // When
        val result = createWorkoutUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.BusinessLogicError>(error)
        assertEquals("ACTIVE_WORKOUT_EXISTS", error.code)
    }
    
    @Test
    fun `when workout name already exists on same date, should return business logic error`() = runTest {
        // Given
        val request = CreateWorkoutRequest(
            userId = "user123",
            name = "Push Day",
            date = LocalDate.now(),
            status = WorkoutStatus.PLANNED
        )
        val existingWorkout = mockk<Workout> {
            coEvery { name } returns "Push Day"
        }
        
        coEvery { workoutRepository.getActiveWorkout(any()) } returns LiftrixResult.success(null)
        coEvery { workoutRepository.getWorkoutsByDate(any(), any()) } returns LiftrixResult.success(listOf(existingWorkout))
        
        // When
        val result = createWorkoutUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.BusinessLogicError>(error)
        assertEquals("DUPLICATE_WORKOUT_NAME", error.code)
    }
    
    @Test
    fun `when active workout has no exercises, should return validation error`() = runTest {
        // Given
        val request = CreateWorkoutRequest(
            userId = "user123",
            name = "Push Day",
            date = LocalDate.now(),
            status = WorkoutStatus.IN_PROGRESS,
            exercises = emptyList()
        )
        
        // When
        val result = createWorkoutUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.violations.any { it.contains("must have at least one exercise") })
    }
    
    @Test
    fun `when date is more than 1 day in future, should return validation error`() = runTest {
        // Given
        val request = CreateWorkoutRequest(
            userId = "user123",
            name = "Push Day",
            date = LocalDate.now().plusDays(2)
        )
        
        // When
        val result = createWorkoutUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.violations.any { it.contains("more than 1 day in the future") })
    }
    
    @Test
    fun `when repository returns error, should propagate error`() = runTest {
        // Given
        val request = CreateWorkoutRequest(
            userId = "user123",
            name = "Push Day",
            date = LocalDate.now()
        )
        val repositoryError = LiftrixError.DatabaseError(errorMessage = "Database connection failed")
        
        coEvery { workoutRepository.getActiveWorkout(any()) } returns LiftrixResult.success(null)
        coEvery { workoutRepository.getWorkoutsByDate(any(), any()) } returns liftrixFailure(repositoryError)
        
        // When
        val result = createWorkoutUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(repositoryError, result.exceptionOrNull())
    }
}