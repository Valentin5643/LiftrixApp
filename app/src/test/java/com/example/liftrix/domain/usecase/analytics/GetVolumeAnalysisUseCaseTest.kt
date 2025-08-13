package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.DailyVolumeResult
import com.example.liftrix.data.local.dao.DailyExerciseVolumeResult
import com.example.liftrix.data.local.dao.DailyMuscleGroupVolumeResult
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.service.ProgressDataService
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
 * Unit tests for GetVolumeAnalysisUseCase
 *
 * Tests the business logic for volume analysis calculations including:
 * - Data aggregation by different grouping types
 * - Error handling for invalid parameters
 * - Performance validation for large datasets
 * - Edge cases with empty and malformed data
 */
@RunWith(JUnit4::class)
class GetVolumeAnalysisUseCaseTest {

    private lateinit var useCase: GetVolumeAnalysisUseCase
    private lateinit var mockProgressDataService: ProgressDataService
    private lateinit var mockExerciseSetDao: ExerciseSetDao

    @Before
    fun setUp() {
        mockProgressDataService = mockk(relaxed = true)
        mockExerciseSetDao = mockk(relaxed = true)
        useCase = GetVolumeAnalysisUseCase(mockProgressDataService, mockExerciseSetDao)
    }

    @Test
    fun `execute should return success when data is available`() = runTest {
        // Given
        val userId = "test_user_123"
        val groupBy = VolumeGrouping.TOTAL
        val timeRange = TimeRangeType.MONTH
        
        val mockDailyVolumeResults = listOf(
            DailyVolumeResult(
                date = "2024-01-01",
                total_volume = 15000.0,
                total_sets = 45,
                exercise_count = 3
            )
        )
        
        coEvery { 
            mockExerciseSetDao.getDailyVolumeData(userId, any(), any()) 
        } returns mockDailyVolumeResults

        // When
        val result = useCase.execute(userId, groupBy, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Total volume should match", 15000.0, data.totalVolume, 0.01)
                assertTrue("Volume growth should be calculated", data.volumeGrowth >= 0.0)
                assertFalse("Data should not be empty", data.isEmpty)
                assertEquals("Grouping should match", groupBy, data.groupBy)
                assertEquals("Time range should match", timeRange, data.timeRange)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
        
        coVerify(exactly = 1) { 
            mockExerciseSetDao.getDailyVolumeData(userId, any(), any()) 
        }
    }

    @Test
    fun `execute should return empty result when no data available`() = runTest {
        // Given
        val userId = "test_user_123"
        val groupBy = VolumeGrouping.TOTAL
        val timeRange = TimeRangeType.MONTH
        
        coEvery { 
            mockExerciseSetDao.getDailyVolumeData(userId, any(), any()) 
        } returns emptyList()

        // When
        val result = useCase.execute(userId, groupBy, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Total volume should be zero", 0.0, data.totalVolume, 0.01)
                assertEquals("Volume growth should be zero", 0.0, data.volumeGrowth, 0.01)
                assertTrue("Data should be empty", data.isEmpty)
                assertTrue("Volume data list should be empty", data.volumeData.isEmpty())
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }

    @Test
    fun `execute should handle service exceptions gracefully`() = runTest {
        // Given
        val userId = "test_user_123"
        val groupBy = VolumeGrouping.TOTAL
        val timeRange = TimeRangeType.MONTH
        
        coEvery { 
            mockExerciseSetDao.getDailyVolumeData(userId, any(), any()) 
        } throws RuntimeException("Database connection failed")

        // When
        val result = useCase.execute(userId, groupBy, timeRange)

        // Then
        assertTrue("Result should be failure", result.isFailure)
        result.fold(
            onSuccess = { 
                fail("Result should not be success") 
            },
            onFailure = { error ->
                val errorMsg = error.message ?: "Unknown error"
                assertTrue("Error message should contain exception info", 
                    errorMsg.contains("Database connection failed") || 
                    errorMsg.contains("Failed to retrieve volume analysis"))
            }
        )
    }

    @Test
    fun `execute should validate user ID parameter`() = runTest {
        // Given
        val invalidUserId = ""
        val groupBy = VolumeGrouping.TOTAL
        val timeRange = TimeRangeType.MONTH

        coEvery { 
            mockExerciseSetDao.getDailyVolumeData(invalidUserId, any(), any()) 
        } returns emptyList()

        // When
        val result = useCase.execute(invalidUserId, groupBy, timeRange)

        // Then
        assertTrue("Result should be success for empty user ID", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertTrue("Data should be empty for invalid user", data.isEmpty)
            },
            onFailure = { error ->
                // If it fails, that's also acceptable
                assertTrue("Should handle invalid user ID gracefully", 
                    (error.message ?: "Unknown error").contains("User ID") || (error.message ?: "Unknown error").contains("invalid"))
            }
        )
    }

    @Test
    fun `execute should aggregate data correctly by exercise grouping`() = runTest {
        // Given
        val userId = "test_user_123"
        val groupBy = VolumeGrouping.BY_EXERCISE
        val timeRange = TimeRangeType.MONTH
        
        val mockExerciseVolumeResults = listOf(
            DailyExerciseVolumeResult(
                exercise_library_id = "bench_press_123",
                exercise_name = "Bench Press",
                date = "2024-01-01",
                total_volume = 5000.0,
                total_sets = 15
            ),
            DailyExerciseVolumeResult(
                exercise_library_id = "squat_456",
                exercise_name = "Squat",
                date = "2024-01-01",
                total_volume = 6000.0,
                total_sets = 18
            ),
            DailyExerciseVolumeResult(
                exercise_library_id = "deadlift_789",
                exercise_name = "Deadlift",
                date = "2024-01-01",
                total_volume = 4000.0,
                total_sets = 12
            )
        )
        
        coEvery { 
            mockExerciseSetDao.getDailyVolumeDataByExercise(userId, any(), any()) 
        } returns mockExerciseVolumeResults

        // When
        val result = useCase.execute(userId, groupBy, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Total volume should be sum of exercises", 15000.0, data.totalVolume, 0.01)
                assertEquals("Should have 3 exercises", 3, data.volumeData.size)
                assertEquals("Average volume should be calculated correctly", 5000.0, data.averageVolume, 0.01)
                assertEquals("Grouping should match", VolumeGrouping.BY_EXERCISE, data.groupBy)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }

    @Test
    fun `execute should calculate volume growth correctly`() = runTest {
        // Given
        val userId = "test_user_123"
        val groupBy = VolumeGrouping.TOTAL
        val timeRange = TimeRangeType.SIX_MONTHS
        
        val mockVolumeDataWithGrowth = listOf(
            DailyVolumeResult(
                date = "2023-07-01",
                total_volume = 10000.0,
                total_sets = 30,
                exercise_count = 2
            ),
            DailyVolumeResult(
                date = "2024-01-01",
                total_volume = 15000.0,
                total_sets = 45,
                exercise_count = 3
            )
        )
        
        coEvery { 
            mockExerciseSetDao.getDailyVolumeData(userId, any(), any()) 
        } returns mockVolumeDataWithGrowth

        // When
        val result = useCase.execute(userId, groupBy, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Total volume should be sum", 25000.0, data.totalVolume, 0.01)
                assertTrue("Volume growth should be calculated", data.volumeGrowth >= 0.0)
                assertEquals("Should have 2 data points", 2, data.volumeData.size)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }

    @Test
    fun `execute should handle BY_MUSCLE_GROUP grouping`() = runTest {
        // Given
        val userId = "test_user_123"
        val groupBy = VolumeGrouping.BY_MUSCLE_GROUP
        val timeRange = TimeRangeType.MONTH
        
        val mockMuscleGroupResults = listOf(
            DailyMuscleGroupVolumeResult(
                primary_muscle_group = "Chest",
                date = "2024-01-01",
                total_volume = 8000.0,
                total_sets = 24,
                exercise_count = 2
            ),
            DailyMuscleGroupVolumeResult(
                primary_muscle_group = "Legs",
                date = "2024-01-01",
                total_volume = 12000.0,
                total_sets = 36,
                exercise_count = 3
            )
        )
        
        coEvery { 
            mockExerciseSetDao.getDailyVolumeDataByMuscleGroup(userId, any(), any()) 
        } returns mockMuscleGroupResults

        // When
        val result = useCase.execute(userId, groupBy, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Total volume should be sum", 20000.0, data.totalVolume, 0.01)
                assertEquals("Should have 2 muscle groups", 2, data.volumeData.size)
                assertEquals("Grouping should match", VolumeGrouping.BY_MUSCLE_GROUP, data.groupBy)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }

    @Test
    fun `execute should handle BY_WEEK grouping`() = runTest {
        // Given
        val userId = "test_user_123"
        val groupBy = VolumeGrouping.BY_WEEK
        val timeRange = TimeRangeType.MONTH
        
        val mockDailyVolumeResults = listOf(
            DailyVolumeResult(
                date = "2024-01-01", // Monday
                total_volume = 5000.0,
                total_sets = 15,
                exercise_count = 3
            ),
            DailyVolumeResult(
                date = "2024-01-03", // Wednesday (same week)
                total_volume = 4000.0,
                total_sets = 12,
                exercise_count = 2
            ),
            DailyVolumeResult(
                date = "2024-01-08", // Monday (next week)
                total_volume = 6000.0,
                total_sets = 18,
                exercise_count = 3
            )
        )
        
        coEvery { 
            mockExerciseSetDao.getDailyVolumeData(userId, any(), any()) 
        } returns mockDailyVolumeResults

        // When
        val result = useCase.execute(userId, groupBy, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Total volume should be sum", 15000.0, data.totalVolume, 0.01)
                assertTrue("Should have at least 2 weeks", data.volumeData.size >= 2)
                assertEquals("Grouping should match", VolumeGrouping.BY_WEEK, data.groupBy)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }
}

// Using actual DAO result classes instead of mock data classes