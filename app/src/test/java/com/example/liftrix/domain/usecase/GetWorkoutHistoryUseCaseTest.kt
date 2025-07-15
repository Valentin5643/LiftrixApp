package com.example.liftrix.domain.usecase

import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

/**
 * Unit tests for GetWorkoutHistoryUseCase
 * 
 * Tests all business logic scenarios including:
 * - Successful history retrieval with various pagination scenarios
 * - Error handling for authentication failures
 * - Parameter validation
 * - Repository error handling
 * - Flow emission patterns
 */
class GetWorkoutHistoryUseCaseTest {

    private val mockWorkoutRepository: WorkoutRepository = mockk()
    private val mockGetCurrentUserIdUseCase: GetCurrentUserIdUseCase = mockk()
    
    private lateinit var useCase: GetWorkoutHistoryUseCase
    
    private val testUserId = "test-user-123"
    private val testWorkoutSummaries = listOf(
        createTestWorkoutSummary("workout-1", "Push Day", LocalDate.now()),
        createTestWorkoutSummary("workout-2", "Pull Day", LocalDate.now().minusDays(1)),
        createTestWorkoutSummary("workout-3", "Leg Day", LocalDate.now().minusDays(2))
    )

    @Before
    fun setup() {
        useCase = GetWorkoutHistoryUseCase(
            workoutRepository = mockWorkoutRepository,
            getCurrentUserIdUseCase = mockGetCurrentUserIdUseCase
        )
    }

    @Test
    fun `execute returns paginated workout history on success`() = runTest {
        // Arrange
        val limit = 20
        val offset = 0
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId
        every { 
            mockWorkoutRepository.getUserWorkoutHistory(testUserId, limit, offset) 
        } returns flowOf(testWorkoutSummaries)

        // Act
        val result = useCase.execute(limit, offset).first()

        // Assert
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return expected workout summaries", testWorkoutSummaries, result.getOrNull())
        
        // Verify repository was called with correct parameters
        coVerify { mockGetCurrentUserIdUseCase() }
        coVerify { mockWorkoutRepository.getUserWorkoutHistory(testUserId, limit, offset) }
    }

    @Test
    fun `execute returns success with empty list when no workouts exist`() = runTest {
        // Arrange
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId
        every { 
            mockWorkoutRepository.getUserWorkoutHistory(testUserId, any(), any()) 
        } returns flowOf(emptyList())

        // Act
        val result = useCase.execute().first()

        // Assert
        assertTrue("Result should be successful", result.isSuccess)
        assertTrue("Should return empty list", result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `execute handles pagination parameters correctly`() = runTest {
        // Arrange
        val limit = 10
        val offset = 20
        val paginatedResults = testWorkoutSummaries.take(1) // Simulate pagination
        
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId
        every { 
            mockWorkoutRepository.getUserWorkoutHistory(testUserId, limit, offset) 
        } returns flowOf(paginatedResults)

        // Act
        val result = useCase.execute(limit, offset).first()

        // Assert
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return paginated results", paginatedResults, result.getOrNull())
        coVerify { mockWorkoutRepository.getUserWorkoutHistory(testUserId, limit, offset) }
    }

    @Test
    fun `execute uses default parameters when not specified`() = runTest {
        // Arrange
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId
        every { 
            mockWorkoutRepository.getUserWorkoutHistory(testUserId, 20, 0) 
        } returns flowOf(testWorkoutSummaries)

        // Act
        val result = useCase.execute().first()

        // Assert
        assertTrue("Result should be successful", result.isSuccess)
        coVerify { mockWorkoutRepository.getUserWorkoutHistory(testUserId, 20, 0) }
    }

    @Test
    fun `execute fails when user is not authenticated`() = runTest {
        // Arrange
        coEvery { mockGetCurrentUserIdUseCase() } returns null

        // Act
        val result = useCase.execute().first()

        // Assert
        assertTrue("Result should be failure", result.isFailure)
        assertTrue(
            "Should contain authentication error message",
            result.exceptionOrNull()?.message?.contains("User not authenticated") == true
        )
        
        // Verify repository was not called
        coVerify(exactly = 0) { mockWorkoutRepository.getUserWorkoutHistory(any(), any(), any()) }
    }

    @Test
    fun `execute validates limit parameter is positive`() = runTest {
        // Arrange
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId

        // Act & Assert
        assertThrows("Should throw IllegalArgumentException for negative limit", IllegalArgumentException::class.java) {
            runTest {
                useCase.execute(limit = -1, offset = 0).first()
            }
        }
        
        assertThrows("Should throw IllegalArgumentException for zero limit", IllegalArgumentException::class.java) {
            runTest {
                useCase.execute(limit = 0, offset = 0).first()
            }
        }
    }

    @Test
    fun `execute validates offset parameter is non-negative`() = runTest {
        // Arrange
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId

        // Act & Assert
        assertThrows("Should throw IllegalArgumentException for negative offset", IllegalArgumentException::class.java) {
            runTest {
                useCase.execute(limit = 20, offset = -1).first()
            }
        }
    }

    @Test
    fun `execute validates limit does not exceed maximum`() = runTest {
        // Arrange
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId

        // Act & Assert
        assertThrows("Should throw IllegalArgumentException for limit exceeding maximum", IllegalArgumentException::class.java) {
            runTest {
                useCase.execute(limit = 101, offset = 0).first() // MAX_LIMIT is 100
            }
        }
    }

    @Test
    fun `execute handles repository exceptions gracefully`() = runTest {
        // Arrange
        val repositoryException = RuntimeException("Database connection failed")
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId
        every { 
            mockWorkoutRepository.getUserWorkoutHistory(testUserId, any(), any()) 
        } throws repositoryException

        // Act
        val result = useCase.execute().first()

        // Assert
        assertTrue("Result should be failure", result.isFailure)
        assertEquals("Should contain repository exception", repositoryException, result.exceptionOrNull())
    }

    @Test
    fun `execute handles authentication use case exceptions`() = runTest {
        // Arrange
        val authException = RuntimeException("Authentication service unavailable")
        coEvery { mockGetCurrentUserIdUseCase() } throws authException

        // Act
        val result = useCase.execute().first()

        // Assert
        assertTrue("Result should be failure", result.isFailure)
        assertEquals("Should contain authentication exception", authException, result.exceptionOrNull())
    }

    @Test
    fun `getHistoryCount returns total workout count on success`() = runTest {
        // Arrange
        val expectedCount = 42
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId
        coEvery { mockWorkoutRepository.getWorkoutHistoryCount(testUserId) } returns expectedCount

        // Act
        val result = useCase.getHistoryCount()

        // Assert
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return expected count", expectedCount, result.getOrNull())
        coVerify { mockWorkoutRepository.getWorkoutHistoryCount(testUserId) }
    }

    @Test
    fun `getHistoryCount fails when user is not authenticated`() = runTest {
        // Arrange
        coEvery { mockGetCurrentUserIdUseCase() } returns null

        // Act
        val result = useCase.getHistoryCount()

        // Assert
        assertTrue("Result should be failure", result.isFailure)
        assertTrue(
            "Should contain authentication error message",
            result.exceptionOrNull()?.message?.contains("User not authenticated") == true
        )
        
        // Verify repository was not called
        coVerify(exactly = 0) { mockWorkoutRepository.getWorkoutHistoryCount(any()) }
    }

    @Test
    fun `getHistoryCount handles repository exceptions gracefully`() = runTest {
        // Arrange
        val repositoryException = RuntimeException("Database error")
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId
        coEvery { mockWorkoutRepository.getWorkoutHistoryCount(testUserId) } throws repositoryException

        // Act
        val result = useCase.getHistoryCount()

        // Assert
        assertTrue("Result should be failure", result.isFailure)
        assertEquals("Should contain repository exception", repositoryException, result.exceptionOrNull())
    }

    @Test
    fun `getHistoryCount handles authentication exceptions`() = runTest {
        // Arrange
        val authException = RuntimeException("Auth service error")
        coEvery { mockGetCurrentUserIdUseCase() } throws authException

        // Act
        val result = useCase.getHistoryCount()

        // Assert
        assertTrue("Result should be failure", result.isFailure)
        assertEquals("Should contain authentication exception", authException, result.exceptionOrNull())
    }

    @Test
    fun `execute handles large pagination offsets correctly`() = runTest {
        // Arrange
        val limit = 20
        val largeOffset = 1000
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId
        every { 
            mockWorkoutRepository.getUserWorkoutHistory(testUserId, limit, largeOffset) 
        } returns flowOf(emptyList()) // No results for high offset

        // Act
        val result = useCase.execute(limit, largeOffset).first()

        // Assert
        assertTrue("Result should be successful", result.isSuccess)
        assertTrue("Should return empty list for high offset", result.getOrNull()?.isEmpty() == true)
        coVerify { mockWorkoutRepository.getUserWorkoutHistory(testUserId, limit, largeOffset) }
    }

    @Test
    fun `execute maintains proper Flow emission pattern`() = runTest {
        // Arrange
        val batch1 = testWorkoutSummaries.take(2)
        val batch2 = testWorkoutSummaries.drop(2)
        
        coEvery { mockGetCurrentUserIdUseCase() } returns testUserId
        every { 
            mockWorkoutRepository.getUserWorkoutHistory(testUserId, any(), any()) 
        } returns flowOf(batch1, batch2) // Multiple emissions

        // Act
        val results = mutableListOf<Result<List<WorkoutSummary>>>()
        useCase.execute().collect { result ->
            results.add(result)
        }

        // Assert
        assertEquals("Should receive all emissions", 2, results.size)
        assertTrue("First emission should be successful", results[0].isSuccess)
        assertTrue("Second emission should be successful", results[1].isSuccess)
        assertEquals("First emission should contain first batch", batch1, results[0].getOrNull())
        assertEquals("Second emission should contain second batch", batch2, results[1].getOrNull())
    }

    /**
     * Helper function to create test WorkoutSummary objects
     */
    private fun createTestWorkoutSummary(
        id: String,
        name: String,
        date: LocalDate,
        exerciseCount: Int = 5,
        completedSets: Int = 15,
        totalSets: Int = 15,
        status: WorkoutStatus = WorkoutStatus.COMPLETED
    ) = WorkoutSummary(
        id = WorkoutId(id),
        userId = testUserId,
        name = name,
        date = date,
        duration = Duration.ofMinutes(45),
        exerciseCount = exerciseCount,
        completedSets = completedSets,
        totalSets = totalSets,
        status = status,
        completionPercentage = (completedSets.toDouble() / totalSets.toDouble()) * 100.0
    )
} 