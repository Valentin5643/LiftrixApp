package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.domain.repository.WorkoutRepository
import com.example.liftrix.service.AnalyticsEngine
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
 * Unit tests for GetMuscleGroupAnalyticsUseCase
 *
 * Tests the business logic for muscle group analysis including:
 * - Volume distribution calculations across muscle groups
 * - Balance analysis and recommendations
 * - Specific muscle group drill-down functionality
 * - Error handling for invalid muscle group parameters
 */
@RunWith(JUnit4::class)
class GetMuscleGroupAnalyticsUseCaseTest {

    private lateinit var useCase: GetMuscleGroupAnalyticsUseCase
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockAnalyticsEngine: AnalyticsEngine

    @Before
    fun setUp() {
        mockWorkoutRepository = mockk(relaxed = true)
        mockAnalyticsEngine = mockk(relaxed = true)
        useCase = GetMuscleGroupAnalyticsUseCase(mockWorkoutRepository, mockAnalyticsEngine)
    }

    @Test
    fun `execute should return success with muscle group distribution data`() = runTest {
        // Given
        val userId = "test_user_123"
        val muscleGroup: MuscleGroup? = null // All muscle groups
        val timeRange = TimeRangeType.MONTH
        
        val mockAnalyticsData = MuscleGroupAnalyticsData(
            distribution = mapOf(
                MuscleGroup.CHEST to 35.0,
                MuscleGroup.BACK to 30.0,
                MuscleGroup.LEGS to 25.0,
                MuscleGroup.SHOULDERS to 10.0
            ),
            totalVolume = 50000.0,
            balanceScore = 85.0,
            recommendations = listOf("Increase shoulder volume", "Maintain current chest focus"),
            selectedMuscleGroup = null,
            timeRange = timeRange
        )
        
        coEvery { 
            mockAnalyticsEngine.calculateMuscleGroupAnalytics(userId, muscleGroup, any())
        } returns LiftrixResult.success(mockAnalyticsData)

        // When
        val result = useCase.execute(userId, muscleGroup, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Should have 4 muscle groups", 4, data.distribution.size)
                assertEquals("Total volume should match", 50000.0, data.totalVolume, 0.01)
                assertEquals("Balance score should match", 85.0, data.balanceScore, 0.01)
                assertTrue("Should have recommendations", data.recommendations.isNotEmpty())
                assertEquals("Time range should match", timeRange, data.timeRange)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
        
        coVerify(exactly = 1) { 
            mockAnalyticsEngine.calculateMuscleGroupAnalytics(userId, muscleGroup, any())
        }
    }

    @Test
    fun `execute should return specific muscle group data when muscle group is specified`() = runTest {
        // Given
        val userId = "test_user_123"
        val specificMuscleGroup = MuscleGroup.CHEST
        val timeRange = TimeRangeType.SIX_MONTHS
        
        val mockSpecificData = MuscleGroupAnalyticsData(
            distribution = mapOf(
                MuscleGroup.CHEST to 100.0 // Only chest data
            ),
            totalVolume = 20000.0,
            balanceScore = 0.0, // Not applicable for single muscle group
            recommendations = listOf("Try incline variations", "Add more chest volume"),
            selectedMuscleGroup = specificMuscleGroup,
            timeRange = timeRange,
            exercises = listOf(
                ExerciseVolumeData("bench_press", "Bench Press", 12000.0),
                ExerciseVolumeData("incline_press", "Incline Press", 8000.0)
            )
        )
        
        coEvery { 
            mockAnalyticsEngine.calculateMuscleGroupAnalytics(userId, specificMuscleGroup, any())
        } returns LiftrixResult.success(mockSpecificData)

        // When
        val result = useCase.execute(userId, specificMuscleGroup, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Should have only 1 muscle group", 1, data.distribution.size)
                assertEquals("Should be 100% chest", 100.0, data.distribution[MuscleGroup.CHEST], 0.01)
                assertEquals("Selected muscle group should match", specificMuscleGroup, data.selectedMuscleGroup)
                assertEquals("Should have exercise breakdown", 2, data.exercises?.size ?: 0)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }

    @Test
    fun `execute should handle analytics engine exceptions gracefully`() = runTest {
        // Given
        val userId = "test_user_123"
        val muscleGroup: MuscleGroup? = null
        val timeRange = TimeRangeType.MONTH
        
        coEvery { 
            mockAnalyticsEngine.calculateMuscleGroupAnalytics(userId, muscleGroup, any())
        } returns LiftrixResult.failure(
            com.example.liftrix.domain.model.error.LiftrixError.AnalyticsError(
                errorMessage = "Failed to calculate muscle group analytics",
                operation = "MUSCLE_GROUP_DISTRIBUTION"
            )
        )

        // When
        val result = useCase.execute(userId, muscleGroup, timeRange)

        // Then
        assertTrue("Result should be failure", result.isFailure)
        result.fold(
            onSuccess = { 
                fail("Result should not be success") 
            },
            onFailure = { error ->
                assertTrue("Error should contain analytics error info", 
                    error.message.contains("muscle group analytics"))
            }
        )
    }

    @Test
    fun `execute should validate user ID parameter`() = runTest {
        // Given
        val invalidUserId = ""
        val muscleGroup: MuscleGroup? = null
        val timeRange = TimeRangeType.MONTH

        // When
        val result = useCase.execute(invalidUserId, muscleGroup, timeRange)

        // Then
        assertTrue("Result should be failure for invalid user ID", result.isFailure)
    }

    @Test
    fun `execute should return empty distribution when no workout data exists`() = runTest {
        // Given
        val userId = "test_user_123"
        val muscleGroup: MuscleGroup? = null
        val timeRange = TimeRangeType.MONTH
        
        val mockEmptyData = MuscleGroupAnalyticsData(
            distribution = emptyMap(),
            totalVolume = 0.0,
            balanceScore = 0.0,
            recommendations = listOf("Start your fitness journey! Add some workouts."),
            selectedMuscleGroup = null,
            timeRange = timeRange
        )
        
        coEvery { 
            mockAnalyticsEngine.calculateMuscleGroupAnalytics(userId, muscleGroup, any())
        } returns LiftrixResult.success(mockEmptyData)

        // When
        val result = useCase.execute(userId, muscleGroup, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertTrue("Distribution should be empty", data.distribution.isEmpty())
                assertEquals("Total volume should be zero", 0.0, data.totalVolume, 0.01)
                assertTrue("Should have at least one recommendation", data.recommendations.isNotEmpty())
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }

    @Test
    fun `execute should calculate balance score correctly for well-balanced training`() = runTest {
        // Given
        val userId = "test_user_123"
        val muscleGroup: MuscleGroup? = null
        val timeRange = TimeRangeType.MONTH
        
        val mockBalancedData = MuscleGroupAnalyticsData(
            distribution = mapOf(
                MuscleGroup.CHEST to 25.0,
                MuscleGroup.BACK to 25.0,
                MuscleGroup.LEGS to 25.0,
                MuscleGroup.SHOULDERS to 25.0
            ),
            totalVolume = 40000.0,
            balanceScore = 100.0, // Perfect balance
            recommendations = listOf("Excellent balance! Keep it up!"),
            selectedMuscleGroup = null,
            timeRange = timeRange
        )
        
        coEvery { 
            mockAnalyticsEngine.calculateMuscleGroupAnalytics(userId, muscleGroup, any())
        } returns LiftrixResult.success(mockBalancedData)

        // When
        val result = useCase.execute(userId, muscleGroup, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Balance score should be perfect", 100.0, data.balanceScore, 0.01)
                assertTrue("All muscle groups should have equal distribution",
                    data.distribution.values.all { it == 25.0 })
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }

    @Test
    fun `execute should identify muscle group imbalances and provide recommendations`() = runTest {
        // Given
        val userId = "test_user_123"
        val muscleGroup: MuscleGroup? = null
        val timeRange = TimeRangeType.MONTH
        
        val mockImbalancedData = MuscleGroupAnalyticsData(
            distribution = mapOf(
                MuscleGroup.CHEST to 60.0, // Too much chest
                MuscleGroup.BACK to 15.0,  // Not enough back
                MuscleGroup.LEGS to 20.0,  // Moderate legs
                MuscleGroup.SHOULDERS to 5.0 // Too little shoulders
            ),
            totalVolume = 30000.0,
            balanceScore = 45.0, // Poor balance
            recommendations = listOf(
                "Increase back training to balance chest work",
                "Add more shoulder exercises",
                "Consider reducing chest volume slightly"
            ),
            selectedMuscleGroup = null,
            timeRange = timeRange
        )
        
        coEvery { 
            mockAnalyticsEngine.calculateMuscleGroupAnalytics(userId, muscleGroup, any())
        } returns LiftrixResult.success(mockImbalancedData)

        // When
        val result = useCase.execute(userId, muscleGroup, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertTrue("Balance score should indicate imbalance", data.balanceScore < 70.0)
                assertTrue("Should have multiple recommendations", data.recommendations.size >= 2)
                assertEquals("Chest should dominate", 60.0, data.distribution[MuscleGroup.CHEST], 0.01)
                assertTrue("Shoulders should be underrepresented", 
                    data.distribution[MuscleGroup.SHOULDERS]!! < 10.0)
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
private data class MuscleGroupAnalyticsData(
    val distribution: Map<MuscleGroup, Double>,
    val totalVolume: Double,
    val balanceScore: Double,
    val recommendations: List<String>,
    val selectedMuscleGroup: MuscleGroup?,
    val timeRange: TimeRangeType,
    val exercises: List<ExerciseVolumeData>? = null
)

private data class ExerciseVolumeData(
    val exerciseId: String,
    val exerciseName: String,
    val volume: Double
)