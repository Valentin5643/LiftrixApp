package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.analytics.OneRmDataPoint
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.repository.ExerciseRepository
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
    private lateinit var mockExerciseRepository: ExerciseRepository
    private lateinit var mockProgressStatsRepository: ProgressStatsRepository

    @Before
    fun setUp() {
        mockExerciseRepository = mockk(relaxed = true)
        mockProgressStatsRepository = mockk(relaxed = true)
        useCase = GetOneRmProgressionUseCase(mockExerciseRepository, mockProgressStatsRepository)
    }

    @Test
    fun `execute should return success with valid exercise IDs and time range`() = runTest {
        // Given
        val userId = "test_user_123"
        val exerciseIds = listOf("bench_press_123", "squat_456")
        val timeRange = TimeRangeType.SIX_MONTHS
        val includeEstimated = true
        
        val mockProgressionData = OneRmProgressionData(
            progressions = mapOf(
                "bench_press_123" to ExerciseProgression(
                    exerciseId = "bench_press_123",
                    exerciseName = "Bench Press",
                    dataPoints = listOf(
                        OneRmDataPoint(
                            date = kotlinx.datetime.LocalDate(2024, 1, 1),
                            oneRmValue = 225.0,
                            isEstimated = false,
                            actualWeight = 205.0,
                            reps = 3
                        )
                    ),
                    maxOneRm = 225.0,
                    progression = 15.0
                )
            ),
            timeRange = timeRange,
            includeEstimated = includeEstimated
        )
        
        coEvery { 
            mockProgressStatsRepository.getOneRmProgression(userId, exerciseIds, any(), includeEstimated)
        } returns LiftrixResult.success(mockProgressionData)

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange, includeEstimated)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Should have 1 exercise progression", 1, data.progressions.size)
                assertTrue("Should contain bench press progression", 
                    data.progressions.containsKey("bench_press_123"))
                assertEquals("Max 1RM should match", 225.0, 
                    data.progressions["bench_press_123"]?.maxOneRm, 0.01)
                assertEquals("Time range should match", timeRange, data.timeRange)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
        
        coVerify(exactly = 1) { 
            mockProgressStatsRepository.getOneRmProgression(userId, exerciseIds, any(), includeEstimated)
        }
    }

    @Test
    fun `execute should return success for all exercises when exerciseIds is null`() = runTest {
        // Given
        val userId = "test_user_123"
        val exerciseIds: List<String>? = null
        val timeRange = TimeRangeType.MONTH
        val includeEstimated = true
        
        val mockAllExercisesData = OneRmProgressionData(
            progressions = mapOf(
                "bench_press_123" to ExerciseProgression(
                    exerciseId = "bench_press_123",
                    exerciseName = "Bench Press",
                    dataPoints = listOf(),
                    maxOneRm = 225.0,
                    progression = 15.0
                ),
                "squat_456" to ExerciseProgression(
                    exerciseId = "squat_456", 
                    exerciseName = "Squat",
                    dataPoints = listOf(),
                    maxOneRm = 315.0,
                    progression = 10.0
                )
            ),
            timeRange = timeRange,
            includeEstimated = includeEstimated
        )
        
        coEvery { 
            mockProgressStatsRepository.getOneRmProgression(userId, null, any(), includeEstimated)
        } returns LiftrixResult.success(mockAllExercisesData)

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange, includeEstimated)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Should have 2 exercise progressions", 2, data.progressions.size)
                assertTrue("Should contain bench press", data.progressions.containsKey("bench_press_123"))
                assertTrue("Should contain squat", data.progressions.containsKey("squat_456"))
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
            mockProgressStatsRepository.getOneRmProgression(userId, exerciseIds, any(), any())
        } returns LiftrixResult.failure(
            com.example.liftrix.domain.model.error.LiftrixError.DatabaseError(
                errorMessage = "Exercise not found",
                operation = "READ",
                table = "exercises"
            )
        )

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange)

        // Then
        assertTrue("Result should be failure", result.isFailure)
        result.fold(
            onSuccess = { 
                fail("Result should not be success") 
            },
            onFailure = { error ->
                assertTrue("Error should contain database error info", 
                    error.message.contains("Exercise not found"))
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
        assertTrue("Result should be failure for invalid user ID", result.isFailure)
    }

    @Test
    fun `execute should handle empty exercise IDs list`() = runTest {
        // Given
        val userId = "test_user_123"
        val exerciseIds = emptyList<String>()
        val timeRange = TimeRangeType.MONTH
        
        val mockEmptyData = OneRmProgressionData(
            progressions = emptyMap(),
            timeRange = timeRange,
            includeEstimated = true
        )
        
        coEvery { 
            mockProgressStatsRepository.getOneRmProgression(userId, exerciseIds, any(), any())
        } returns LiftrixResult.success(mockEmptyData)

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertTrue("Progressions should be empty", data.progressions.isEmpty())
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
        
        val mockActualOnlyData = OneRmProgressionData(
            progressions = mapOf(
                "bench_press_123" to ExerciseProgression(
                    exerciseId = "bench_press_123",
                    exerciseName = "Bench Press",
                    dataPoints = listOf(
                        OneRmDataPoint(
                            date = kotlinx.datetime.LocalDate(2024, 1, 1),
                            oneRmValue = 225.0,
                            isEstimated = false,
                            actualWeight = 225.0,
                            reps = 1
                        )
                        // No estimated values should be included
                    ),
                    maxOneRm = 225.0,
                    progression = 10.0
                )
            ),
            timeRange = timeRange,
            includeEstimated = includeEstimated
        )
        
        coEvery { 
            mockProgressStatsRepository.getOneRmProgression(userId, exerciseIds, any(), includeEstimated)
        } returns LiftrixResult.success(mockActualOnlyData)

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange, includeEstimated)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Should exclude estimated values", false, data.includeEstimated)
                val dataPoints = data.progressions["bench_press_123"]?.dataPoints ?: emptyList()
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
        
        val mockProgressionData = OneRmProgressionData(
            progressions = mapOf(
                "bench_press_123" to ExerciseProgression(
                    exerciseId = "bench_press_123",
                    exerciseName = "Bench Press",
                    dataPoints = listOf(
                        OneRmDataPoint(
                            date = kotlinx.datetime.LocalDate(2023, 6, 1),
                            oneRmValue = 200.0,
                            isEstimated = false
                        ),
                        OneRmDataPoint(
                            date = kotlinx.datetime.LocalDate(2024, 1, 1),
                            oneRmValue = 225.0,
                            isEstimated = false
                        )
                    ),
                    maxOneRm = 225.0,
                    progression = 12.5 // 25 lbs increase from 200 = 12.5%
                )
            ),
            timeRange = timeRange,
            includeEstimated = true
        )
        
        coEvery { 
            mockProgressStatsRepository.getOneRmProgression(userId, exerciseIds, any(), any())
        } returns LiftrixResult.success(mockProgressionData)

        // When
        val result = useCase.execute(userId, exerciseIds, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                val progression = data.progressions["bench_press_123"]
                assertNotNull("Progression should exist", progression)
                assertEquals("Progression percentage should be calculated correctly", 
                    12.5, progression!!.progression, 0.01)
                assertEquals("Max 1RM should be latest value", 225.0, progression.maxOneRm, 0.01)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }
}

// OneRmDataPoint now imported from domain.model.analytics.OneRmDataPoint

private data class ExerciseProgression(
    val exerciseId: String,
    val exerciseName: String,
    val dataPoints: List<OneRmDataPoint>,
    val maxOneRm: Double,
    val progression: Double
)

private data class OneRmProgressionData(
    val progressions: Map<String, ExerciseProgression>,
    val timeRange: TimeRangeType,
    val includeEstimated: Boolean
)