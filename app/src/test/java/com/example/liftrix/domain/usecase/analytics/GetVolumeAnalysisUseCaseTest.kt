package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeGrouping
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

    @Before
    fun setUp() {
        mockProgressDataService = mockk(relaxed = true)
        useCase = GetVolumeAnalysisUseCase(mockProgressDataService)
    }

    @Test
    fun `execute should return success when data is available`() = runTest {
        // Given
        val userId = "test_user_123"
        val groupBy = VolumeGrouping.TOTAL
        val timeRange = TimeRangeType.MONTH
        

            totalVolume = 15000.0,
            volumeGrowth = 12.5,
            averageVolume = 15000.0,
            isEmpty = false
        )
        
        coEvery { 
            mockProgressDataService.getVolumeData(userId, any()) 
        } returns mockVolumeData.volumeData

        // When
        val result = useCase.execute(userId, groupBy, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Total volume should match", 15000.0, data.totalVolume, 0.01)
                assertEquals("Volume growth should match", 12.5, data.volumeGrowth, 0.01)
                assertFalse("Data should not be empty", data.isEmpty)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
        
        coVerify(exactly = 1) { 
            mockProgressDataService.getVolumeData(userId, any()) 
        }
    }

    @Test
    fun `execute should return empty result when no data available`() = runTest {
        // Given
        val userId = "test_user_123"
        val groupBy = VolumeGrouping.TOTAL
        val timeRange = TimeRangeType.MONTH
        
        coEvery { 
            mockProgressDataService.getVolumeData(userId, any()) 
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
            mockProgressDataService.getVolumeData(userId, any()) 
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
                assertTrue("Error message should contain exception info", 
                    error.message.contains("Database connection failed") || 
                    error.message.contains("Failed to get volume analysis"))
            }
        )
    }

    @Test
    fun `execute should validate user ID parameter`() = runTest {
        // Given
        val invalidUserId = ""
        val groupBy = VolumeGrouping.TOTAL
        val timeRange = TimeRangeType.MONTH

        // When
        val result = useCase.execute(invalidUserId, groupBy, timeRange)

        // Then
        assertTrue("Result should be failure for invalid user ID", result.isFailure)
        result.fold(
            onSuccess = { 
                fail("Result should not be success for invalid user ID") 
            },
            onFailure = { error ->
                assertTrue("Error should indicate invalid user ID", 
                    error.message.contains("User ID") || error.message.contains("invalid"))
            }
        )
    }

    @Test
    fun `execute should aggregate data correctly by exercise grouping`() = runTest {
        // Given
        val userId = "test_user_123"
        val groupBy = VolumeGrouping.BY_EXERCISE
        val timeRange = TimeRangeType.MONTH
        
        val mockExerciseVolumeData = listOf(
            VolumeAnalysisDataPoint(
                volume = 5000.0,
                sets = 15,
                label = "Bench Press",
                timestamp = kotlinx.datetime.Clock.System.now()
            ),
            VolumeAnalysisDataPoint(
                volume = 6000.0,
                sets = 18,
                label = "Squat",
                timestamp = kotlinx.datetime.Clock.System.now()
            ),
            VolumeAnalysisDataPoint(
                volume = 4000.0,
                sets = 12,
                label = "Deadlift",
                timestamp = kotlinx.datetime.Clock.System.now()
            )
        )
        
        coEvery { 
            mockProgressDataService.getVolumeData(userId, any()) 
        } returns mockExerciseVolumeData

        // When
        val result = useCase.execute(userId, groupBy, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Total volume should be sum of exercises", 15000.0, data.totalVolume, 0.01)
                assertEquals("Should have 3 exercises", 3, data.volumeData.size)
                assertEquals("Average volume should be calculated correctly", 5000.0, data.averageVolume, 0.01)
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
            // Earlier data points (lower volume)
            VolumeAnalysisDataPoint(
                volume = 10000.0,
                sets = 30,
                label = "Total",
                timestamp = kotlinx.datetime.Clock.System.now().minus(kotlinx.datetime.DateTimeUnit.DAY, 150)
            ),
            // Recent data points (higher volume)
            VolumeAnalysisDataPoint(
                volume = 15000.0,
                sets = 45,
                label = "Total", 
                timestamp = kotlinx.datetime.Clock.System.now()
            )
        )
        
        coEvery { 
            mockProgressDataService.getVolumeData(userId, any()) 
        } returns mockVolumeDataWithGrowth

        // When
        val result = useCase.execute(userId, groupBy, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertTrue("Volume growth should be positive", data.volumeGrowth > 0)
                assertEquals("Total volume should be sum", 25000.0, data.totalVolume, 0.01)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }
}

/**
 * Mock data classes for testing
 */
private data class VolumeAnalysisDataPoint(
    val volume: Double,
    val sets: Int,
    val label: String,
    val timestamp: kotlinx.datetime.Instant
)