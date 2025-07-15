package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetWorkoutByIdUseCaseTest {
    
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var errorHandler: ErrorHandler
    private lateinit var getWorkoutByIdUseCase: GetWorkoutByIdUseCase
    
    @Before
    fun setup() {
        workoutRepository = mockk()
        errorHandler = mockk()
        getWorkoutByIdUseCase = GetWorkoutByIdUseCase(workoutRepository, errorHandler)
    }
    
    @Test
    fun `when valid request provided and workout exists, should return workout`() = runTest {
        // Given
        val workoutId = WorkoutId.generate()
        val userId = "user123"
        val request = GetWorkoutByIdRequest(workoutId, userId)
        val expectedWorkout = mockk<Workout> {
            coEvery { this@mockk.userId } returns userId
        }
        
        coEvery { workoutRepository.getWorkoutById(workoutId, userId) } returns LiftrixResult.success(expectedWorkout)
        
        // When
        val result = getWorkoutByIdUseCase(request)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedWorkout, result.getOrNull())
        coVerify { workoutRepository.getWorkoutById(workoutId, userId) }
    }
    
    @Test
    fun `when valid request provided and workout does not exist, should return null`() = runTest {
        // Given
        val workoutId = WorkoutId.generate()
        val userId = "user123"
        val request = GetWorkoutByIdRequest(workoutId, userId)
        
        coEvery { workoutRepository.getWorkoutById(workoutId, userId) } returns LiftrixResult.success(null)
        
        // When
        val result = getWorkoutByIdUseCase(request)
        
        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        coVerify { workoutRepository.getWorkoutById(workoutId, userId) }
    }
    
    @Test
    fun `when user ID is blank, should return validation error`() = runTest {
        // Given
        val workoutId = WorkoutId.generate()
        val request = GetWorkoutByIdRequest(workoutId, "")
        
        // When
        val result = getWorkoutByIdUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.violations.any { it.contains("User ID is required") })
    }
    
    @Test
    fun `when workout ID is blank, should return validation error`() = runTest {
        // Given
        val workoutId = WorkoutId("")
        val request = GetWorkoutByIdRequest(workoutId, "user123")
        
        // When
        val result = getWorkoutByIdUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.violations.any { it.contains("Workout ID cannot be blank") })
    }
    
    @Test
    fun `when workout exists but belongs to different user, should return authentication error`() = runTest {
        // Given
        val workoutId = WorkoutId.generate()
        val requestingUserId = "user123"
        val workoutOwnerId = "user456"
        val request = GetWorkoutByIdRequest(workoutId, requestingUserId)
        val workout = mockk<Workout> {
            coEvery { userId } returns workoutOwnerId
        }
        
        coEvery { workoutRepository.getWorkoutById(workoutId, requestingUserId) } returns LiftrixResult.success(workout)
        
        // When
        val result = getWorkoutByIdUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.AuthenticationError>(error)
        assertTrue(error.message.contains("Access denied"))
        assertEquals("WORKOUT_ACCESS_DENIED", error.errorCode)
    }
    
    @Test
    fun `when repository returns error, should propagate error`() = runTest {
        // Given
        val workoutId = WorkoutId.generate()
        val userId = "user123"
        val request = GetWorkoutByIdRequest(workoutId, userId)
        val repositoryError = LiftrixError.DatabaseError(errorMessage = "Database connection failed")
        
        coEvery { workoutRepository.getWorkoutById(workoutId, userId) } returns liftrixFailure(repositoryError)
        
        // When
        val result = getWorkoutByIdUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(repositoryError, result.exceptionOrNull())
    }
    
    @Test
    fun `when workout exists and user IDs match, should return workout successfully`() = runTest {
        // Given
        val workoutId = WorkoutId.generate()
        val userId = "user123"
        val request = GetWorkoutByIdRequest(workoutId, userId)
        val workout = mockk<Workout> {
            coEvery { this@mockk.userId } returns userId
        }
        
        coEvery { workoutRepository.getWorkoutById(workoutId, userId) } returns LiftrixResult.success(workout)
        
        // When
        val result = getWorkoutByIdUseCase(request)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(workout, result.getOrNull())
    }
    
    @Test
    fun `when multiple validation errors occur, should return all violations`() = runTest {
        // Given
        val workoutId = WorkoutId("")
        val request = GetWorkoutByIdRequest(workoutId, "")
        
        // When
        val result = getWorkoutByIdUseCase(request)
        
        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertEquals(2, error.violations.size)
        assertTrue(error.violations.any { it.contains("User ID is required") })
        assertTrue(error.violations.any { it.contains("Workout ID cannot be blank") })
    }
}