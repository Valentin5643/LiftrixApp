package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.MuscleGroupDistributionResult
import com.example.liftrix.data.local.dao.MuscleGroupVolumeResult
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.repository.workout.WorkoutRepository
// Domain models are defined in the GetMuscleGroupAnalyticsUseCase file
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
    private lateinit var mockExerciseDao: ExerciseDao
    private lateinit var mockExerciseSetDao: ExerciseSetDao
    private lateinit var mockAnalyticsEngine: AnalyticsEngine

    @Before
    fun setUp() {
        mockWorkoutRepository = mockk(relaxed = true)
        mockExerciseDao = mockk(relaxed = true)
        mockExerciseSetDao = mockk(relaxed = true)
        mockAnalyticsEngine = mockk(relaxed = true)
        useCase = GetMuscleGroupAnalyticsUseCase(
            mockWorkoutRepository,
            mockExerciseDao,
            mockExerciseSetDao,
            mockAnalyticsEngine
        )
    }

    @Test
    fun `execute should return success with muscle group distribution data`() = runTest {
        // Given
        val userId = "test_user_123"
        val muscleGroup: MuscleGroup? = null // All muscle groups
        val timeRange = TimeRangeType.MONTH
        
        val mockDistributionData = listOf(
            MuscleGroupDistributionResult("Chest", 10, 5, 8),
            MuscleGroupDistributionResult("Back", 8, 4, 7),
            MuscleGroupDistributionResult("Legs", 6, 3, 6),
            MuscleGroupDistributionResult("Shoulders", 4, 2, 4)
        )
        
        val mockVolumeData = listOf(
            MuscleGroupVolumeResult("Chest", 17500.0, 10, 50),
            MuscleGroupVolumeResult("Back", 15000.0, 8, 40),
            MuscleGroupVolumeResult("Legs", 12500.0, 6, 35),
            MuscleGroupVolumeResult("Shoulders", 5000.0, 4, 15)
        )
        
        coEvery { 
            mockExerciseDao.getMuscleGroupDistribution(userId, any(), any())
        } returns mockDistributionData
        
        coEvery { 
            mockExerciseSetDao.getVolumeDataByMuscleGroup(userId, any(), any())
        } returns mockVolumeData

        // When
        val result = useCase.execute(userId, muscleGroup, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Should have 4 muscle groups", 4, data.muscleGroupDistribution.size)
                assertEquals("Total volume should match", 50000.0, data.totalVolume, 0.01)
                assertEquals("Balance score should match", 85.0, data.balanceAnalysis.balanceScore, 0.01)
                assertTrue("Should have recommendations", data.recommendations.isNotEmpty())
                assertEquals("Time range should match", timeRange, data.timeRange)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
        
        coVerify(exactly = 1) { 
            mockExerciseDao.getMuscleGroupDistribution(userId, any(), any())
        }
        coVerify(exactly = 1) { 
            mockExerciseSetDao.getVolumeDataByMuscleGroup(userId, any(), any())
        }
    }

    @Test
    fun `execute should return specific muscle group data when muscle group is specified`() = runTest {
        // Given
        val userId = "test_user_123"
        val specificMuscleGroup = MuscleGroup.CHEST
        val timeRange = TimeRangeType.SIX_MONTHS
        
        val mockSpecificData = MuscleGroupAnalyticsData(
            muscleGroupDistribution = listOf(
                MuscleGroupData(MuscleGroup.CHEST, 15, 8, 10, 20000.0, 60, 100.0)
            ),
            balanceAnalysis = BalanceAnalysis(
                imbalances = listOf(),
                balanceScore = 0.0, // Not applicable for single muscle group
                mostTrained = MuscleGroup.CHEST,
                leastTrained = MuscleGroup.CHEST
            ),
            recommendations = listOf("Try incline variations", "Add more chest volume"),
            totalVolume = 20000.0,
            totalExercises = 15,
            timeRange = timeRange,
            targetMuscleGroup = specificMuscleGroup,
            isEmpty = false
        )
        
        val mockChestDistributionData = listOf(
            MuscleGroupDistributionResult("Chest", 15, 8, 10)
        )
        
        val mockChestVolumeData = listOf(
            MuscleGroupVolumeResult("Chest", 20000.0, 15, 60)
        )
        
        coEvery { 
            mockExerciseDao.getMuscleGroupDistribution(userId, any(), any())
        } returns mockChestDistributionData
        
        coEvery { 
            mockExerciseSetDao.getVolumeDataByMuscleGroup(userId, any(), any())
        } returns mockChestVolumeData

        // When
        val result = useCase.execute(userId, specificMuscleGroup, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertEquals("Should have only 1 muscle group", 1, data.muscleGroupDistribution.size)
                assertEquals("Should be 100% chest", 100.0, data.muscleGroupDistribution.first().percentage, 0.01)
                assertEquals("Selected muscle group should match", specificMuscleGroup, data.targetMuscleGroup)
                assertEquals("Should have chest muscle group", MuscleGroup.CHEST, data.muscleGroupDistribution.first().muscleGroup)
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
            mockExerciseDao.getMuscleGroupDistribution(userId, any(), any())
        } throws RuntimeException("Database connection failed")

        // When
        val result = useCase.execute(userId, muscleGroup, timeRange)

        // Then
        assertTrue("Result should be failure", result.isFailure)
        result.fold(
            onSuccess = { 
                fail("Result should not be success") 
            },
            onFailure = { error ->
                assertTrue("Error should contain calculation error info", 
                    (error.message ?: "Unknown error").contains("Failed to retrieve muscle group analytics"))
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
        
        // Mock empty data from DAOs
        coEvery { 
            mockExerciseDao.getMuscleGroupDistribution(userId, any(), any())
        } returns emptyList()
        
        coEvery { 
            mockExerciseSetDao.getVolumeDataByMuscleGroup(userId, any(), any())
        } returns emptyList()

        // When
        val result = useCase.execute(userId, muscleGroup, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertTrue("Distribution should be empty", data.muscleGroupDistribution.isEmpty())
                assertEquals("Total volume should be zero", 0.0, data.totalVolume, 0.01)
                assertTrue("Should have at least one recommendation", data.recommendations.isNotEmpty())
                assertTrue("Should be marked as empty", data.isEmpty)
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
            muscleGroupDistribution = listOf(
                MuscleGroupData(MuscleGroup.CHEST, 8, 4, 6, 10000.0, 30, 25.0),
                MuscleGroupData(MuscleGroup.BACK, 8, 4, 6, 10000.0, 30, 25.0),
                MuscleGroupData(MuscleGroup.LEGS, 8, 4, 6, 10000.0, 30, 25.0),
                MuscleGroupData(MuscleGroup.SHOULDERS, 8, 4, 6, 10000.0, 30, 25.0)
            ),
            balanceAnalysis = BalanceAnalysis(
                imbalances = emptyList(),
                balanceScore = 100.0, // Perfect balance
                mostTrained = MuscleGroup.CHEST,
                leastTrained = MuscleGroup.SHOULDERS
            ),
            recommendations = listOf("Excellent balance! Keep it up!"),
            totalVolume = 40000.0,
            totalExercises = 32,
            timeRange = timeRange,
            targetMuscleGroup = null,
            isEmpty = false
        )
        
        val mockBalancedDistributionData = listOf(
            MuscleGroupDistributionResult("Chest", 8, 4, 6),
            MuscleGroupDistributionResult("Back", 8, 4, 6),
            MuscleGroupDistributionResult("Legs", 8, 4, 6),
            MuscleGroupDistributionResult("Shoulders", 8, 4, 6)
        )
        
        val mockBalancedVolumeData = listOf(
            MuscleGroupVolumeResult("Chest", 10000.0, 8, 30),
            MuscleGroupVolumeResult("Back", 10000.0, 8, 30),
            MuscleGroupVolumeResult("Legs", 10000.0, 8, 30),
            MuscleGroupVolumeResult("Shoulders", 10000.0, 8, 30)
        )
        
        coEvery { 
            mockExerciseDao.getMuscleGroupDistribution(userId, any(), any())
        } returns mockBalancedDistributionData
        
        coEvery { 
            mockExerciseSetDao.getVolumeDataByMuscleGroup(userId, any(), any())
        } returns mockBalancedVolumeData

        // When
        val result = useCase.execute(userId, muscleGroup, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertTrue("Balance score should be high", data.balanceAnalysis.balanceScore >= 90.0)
                assertTrue("All muscle groups should have equal distribution",
                    data.muscleGroupDistribution.all { it.percentage == 25.0 })
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
            muscleGroupDistribution = listOf(
                MuscleGroupData(MuscleGroup.CHEST, 20, 8, 10, 18000.0, 60, 60.0),
                MuscleGroupData(MuscleGroup.BACK, 5, 3, 4, 4500.0, 15, 15.0),
                MuscleGroupData(MuscleGroup.LEGS, 8, 4, 6, 6000.0, 20, 20.0),
                MuscleGroupData(MuscleGroup.SHOULDERS, 3, 2, 3, 1500.0, 5, 5.0)
            ),
            balanceAnalysis = BalanceAnalysis(
                imbalances = listOf(
                    MuscleGroupImbalance(MuscleGroup.CHEST, 60.0, 25.0, 35.0, ImbalanceSeverity.HIGH),
                    MuscleGroupImbalance(MuscleGroup.SHOULDERS, 5.0, 25.0, 20.0, ImbalanceSeverity.MEDIUM)
                ),
                balanceScore = 45.0, // Poor balance
                mostTrained = MuscleGroup.CHEST,
                leastTrained = MuscleGroup.SHOULDERS
            ),
            recommendations = listOf(
                "Increase back training to balance chest work",
                "Add more shoulder exercises",
                "Consider reducing chest volume slightly"
            ),
            totalVolume = 30000.0,
            totalExercises = 36,
            timeRange = timeRange,
            targetMuscleGroup = null,
            isEmpty = false
        )
        
        val mockImbalancedDistributionData = listOf(
            MuscleGroupDistributionResult("Chest", 20, 8, 10),
            MuscleGroupDistributionResult("Back", 5, 3, 4),
            MuscleGroupDistributionResult("Legs", 8, 4, 6),
            MuscleGroupDistributionResult("Shoulders", 3, 2, 3)
        )
        
        val mockImbalancedVolumeData = listOf(
            MuscleGroupVolumeResult("Chest", 18000.0, 20, 60),
            MuscleGroupVolumeResult("Back", 4500.0, 5, 15),
            MuscleGroupVolumeResult("Legs", 6000.0, 8, 20),
            MuscleGroupVolumeResult("Shoulders", 1500.0, 3, 5)
        )
        
        coEvery { 
            mockExerciseDao.getMuscleGroupDistribution(userId, any(), any())
        } returns mockImbalancedDistributionData
        
        coEvery { 
            mockExerciseSetDao.getVolumeDataByMuscleGroup(userId, any(), any())
        } returns mockImbalancedVolumeData

        // When
        val result = useCase.execute(userId, muscleGroup, timeRange)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        result.fold(
            onSuccess = { data ->
                assertTrue("Balance score should indicate imbalance", data.balanceAnalysis.balanceScore < 70.0)
                assertTrue("Should have multiple recommendations", data.recommendations.size >= 1)
                val chestData = data.muscleGroupDistribution.find { it.muscleGroup == MuscleGroup.CHEST }
                assertTrue("Chest should dominate", (chestData?.percentage ?: 0.0) > 50.0)
                val shoulderData = data.muscleGroupDistribution.find { it.muscleGroup == MuscleGroup.SHOULDERS }
                assertTrue("Shoulders should be underrepresented", 
                    (shoulderData?.percentage ?: 0.0) < 10.0)
            },
            onFailure = { 
                fail("Result should not be failure") 
            }
        )
    }
}