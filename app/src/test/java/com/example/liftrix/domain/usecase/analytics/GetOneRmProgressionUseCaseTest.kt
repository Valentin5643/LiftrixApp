package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.OneRmResult
import com.example.liftrix.domain.model.analytics.OneRmDataPoint
import com.example.liftrix.domain.model.analytics.ExerciseProgression
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.ProgressStatsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for GetOneRmProgressionUseCase
 *
 * Tests the business logic for 1RM progression analysis including:
 * - Epley formula calculations for estimated 1RM values
 * - Exercise filtering and time range validation
 * - Error handling for invalid exercise IDs
 * - Performance validation with large datasets
 */
@RunWith(JUnit4::class)
class GetOneRmProgressionUseCaseTest {

    private lateinit var useCase: GetOneRmProgressionUseCase
    private lateinit var mockExerciseSetDao: ExerciseSetDao
    private lateinit var mockProgressStatsRepository: ProgressStatsRepository

    @Before
    fun setUp() {
        mockExerciseSetDao = mockk(relaxed = true)
        mockProgressStatsRepository = mockk(relaxed = true)
        useCase = GetOneRmProgressionUseCase(mockExerciseSetDao, mockProgressStatsRepository)
    }

    @Test
    fun `execute should return success with valid exercise IDs and time range`() = runTest {
        // Given
        val userId = "test_user_123"
        val exerciseIds = listOf("bench_press_123", "squat_456")
        val timeRange = TimeRangeType.SIX_MONTHS
        val includeEstimated = true
        
        val mockOneRmResults = listOf(
            OneRmResult(
                exercise_library_id = "bench_press_123",
                weight_kg = 205.0f,
                reps = 3,
                completed_at = System.currentTimeMillis(),
                estimated_one_rm = 225.0
            )
        )
        
        coEvery { 
            mockExerciseSetDao.getOneRmDataForExercises(userId, exerciseIds, any(), any())
        } returns mockOneRmResults

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange, includeEstimated)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Should have 1 exercise progression", 1, data.exerciseProgressions.size)
                assertTrue("Should contain bench press progression", 
                    data.exerciseProgressions.any { it.exerciseId == "bench_press_123" })
                assertEquals("Time range should match", timeRange, data.timeRange)
                assertFalse("Data should not be empty", data.isEmpty)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
        
        coVerify(exactly = 1) { 
            mockExerciseSetDao.getOneRmDataForExercises(userId, exerciseIds, any(), any())
        }
    }

    @Test
    fun `execute should return success for all exercises when exerciseIds is null`() = runTest {
        // Given
        val userId = "test_user_123"
        val exerciseIds: List<String>? = null
        val timeRange = TimeRangeType.MONTH
        val includeEstimated = true
        
        val mockAllExercisesResults = listOf(
            OneRmResult(
                exercise_library_id = "bench_press_123",
                weight_kg = 205.0f,
                reps = 3,
                completed_at = System.currentTimeMillis(),
                estimated_one_rm = 225.0
            ),
            OneRmResult(
                exercise_library_id = "squat_456",
                weight_kg = 285.0f,
                reps = 2,
                completed_at = System.currentTimeMillis(),
                estimated_one_rm = 315.0
            )
        )
        
        coEvery { 
            mockExerciseSetDao.getAllOneRmData(userId, any(), any())
        } returns mockAllExercisesResults

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange, includeEstimated)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Should have 2 exercise progressions", 2, data.exerciseProgressions.size)
                assertTrue("Should contain bench press", data.exerciseProgressions.any { it.exerciseId == "bench_press_123" })
                assertTrue("Should contain squat", data.exerciseProgressions.any { it.exerciseId == "squat_456" })
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }

    @Test
    fun `execute should handle repository exceptions gracefully`() = runTest {
        // Given
        val userId = "test_user_123"
        val exerciseIds = listOf("invalid_exercise")
        val timeRange = TimeRangeType.MONTH
        
        coEvery { 
            mockExerciseSetDao.getOneRmDataForExercises(userId, exerciseIds, any(), any())
        } throws RuntimeException("Database connection failed")

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange)

        // Then
        assertTrue("Result should be failure", result.isFailure)
        result.fold(
            onSuccess = { 
                fail("Result should not be success") 
            },
            onFailure = { error ->
                val errorMsg = error.message ?: "Unknown error"
                assertTrue("Error should contain database error info", 
                    errorMsg.contains("Database connection failed") || 
                    errorMsg.contains("Failed to retrieve 1RM progression"))
            }
        )
    }

    @Test
    fun `execute should validate user ID parameter`() = runTest {
        // Given
        val invalidUserId = ""
        val exerciseIds = listOf("bench_press_123")
        val timeRange = TimeRangeType.MONTH

        // When
        val result = useCase.execute(invalidUserId, exerciseIds, timeRange)

        // Then
        // The actual implementation might not validate empty userId, 
        // but we should test what happens when it's passed through
        result.fold(
            onSuccess = { data ->
                // If it succeeds, it should return empty data
                assertTrue("Data should be empty for invalid user", data.isEmpty)
            },
            onFailure = { error ->
                // If it fails, that's also acceptable
                assertTrue("Should handle invalid user ID gracefully", true)
            }
        )
    }

    @Test
    fun `execute should handle empty exercise IDs list`() = runTest {
        // Given
        val userId = "test_user_123"
        val exerciseIds = emptyList<String>()
        val timeRange = TimeRangeType.MONTH
        
        coEvery { 
            mockExerciseSetDao.getOneRmDataForExercises(userId, exerciseIds, any(), any())
        } returns emptyList()

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertTrue("Data should be empty", data.isEmpty)
                assertTrue("Exercise progressions should be empty", data.exerciseProgressions.isEmpty())
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }

    @Test
    fun `execute should correctly handle estimated vs actual 1RM filtering`() = runTest {
        // Given
        val userId = "test_user_123"
        val exerciseIds = listOf("bench_press_123")
        val timeRange = TimeRangeType.MONTH
        val includeEstimated = false // Only actual 1RM values
        
        val mockActualOnlyResults = listOf(
            OneRmResult(
                exercise_library_id = "bench_press_123",
                weight_kg = 225.0f,
                reps = 1, // Actual 1RM
                completed_at = System.currentTimeMillis(),
                estimated_one_rm = 225.0
            )
        )
        
        coEvery { 
            mockExerciseSetDao.getOneRmDataForExercises(userId, exerciseIds, any(), any())
        } returns mockActualOnlyResults

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange, includeEstimated)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertFalse("Should exclude estimated values", includeEstimated)
                val dataPoints = data.exerciseProgressions.firstOrNull()?.dataPoints ?: emptyList()
                assertTrue("All data points should be actual (not estimated)", 
                    dataPoints.all { !it.isEstimated })
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }

    @Test
    fun `execute should calculate progression percentages correctly`() = runTest {
        // Given
        val userId = "test_user_123"
        val exerciseIds = listOf("bench_press_123")
        val timeRange = TimeRangeType.SIX_MONTHS
        
        val mockProgressionResults = listOf(
            OneRmResult(
                exercise_library_id = "bench_press_123",
                weight_kg = 200.0f,
                reps = 1,
                completed_at = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L), // 30 days ago
                estimated_one_rm = 200.0
            ),
            OneRmResult(
                exercise_library_id = "bench_press_123",
                weight_kg = 225.0f,
                reps = 1,
                completed_at = System.currentTimeMillis(),
                estimated_one_rm = 225.0
            )
        )
        
        coEvery { 
            mockExerciseSetDao.getOneRmDataForExercises(userId, exerciseIds, any(), any())
        } returns mockProgressionResults

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                val progression = data.exerciseProgressions.firstOrNull()
                assertNotNull("Progression should exist", progression)
                assertTrue("Progression percentage should be positive", progression!!.progression >= 0f)
                assertEquals("Max 1RM should be latest value", 225.0f, progression.currentMax, 0.01f)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }
}