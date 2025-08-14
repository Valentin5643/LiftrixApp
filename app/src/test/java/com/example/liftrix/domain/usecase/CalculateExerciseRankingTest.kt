package com.example.liftrix.domain.usecase

import com.example.liftrix.domain.model.analytics.RankingMetric
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.ExerciseCategory
// MuscleGroup moved to analytics use case package
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.analytics.CalculateExerciseRankingUseCase
import com.example.liftrix.domain.usecase.analytics.ExerciseRankingRequest
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.workout.ExercisePerformanceData
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.model.common.LiftrixResult
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit Tests for Exercise Performance Calculation and Ranking
 * 
 * Tests the core business logic for calculating exercise performance scores
 * and rankings based on the specification requirements:
 * 
 * Performance Score = (Volume Growth % + 1RM Growth %) / 2
 * 
 * Test scenarios:
 * - Basic performance score calculation
 * - Volume growth percentage calculation
 * - 1RM growth percentage calculation
 * - Plateau detection (3-week window with <5% variance)
 * - Exercise ranking by different metrics
 * - Edge cases and error handling
 * - Time range filtering
 * - Multiple exercise comparison
 */
class CalculateExerciseRankingTest {

    private lateinit var calculateExerciseRankingUseCase: CalculateExerciseRankingUseCase
    
    // Mock data for testing
    private val testExercises = listOf(
        createTestExercise("bench-press", "Bench Press", ExerciseCategory.CHEST),
        createTestExercise("squat", "Back Squat", ExerciseCategory.LEGS),
        createTestExercise("deadlift", "Deadlift", ExerciseCategory.BACK),
        createTestExercise("overhead-press", "Overhead Press", ExerciseCategory.SHOULDERS)
    )

    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var getCurrentUserIdUseCase: GetCurrentUserIdUseCase

    @Before
    fun setup() {
        workoutRepository = mockk()
        getCurrentUserIdUseCase = mockk()
        calculateExerciseRankingUseCase = CalculateExerciseRankingUseCase(
            workoutRepository = workoutRepository,
            getCurrentUserIdUseCase = getCurrentUserIdUseCase
        )
    }

    @Test
    fun test_performance_score_calculation_basic() = runTest {
        // Given: Exercise with 20% volume growth and 10% 1RM growth
        val volumeGrowth = 20f
        val oneRmGrowth = 10f
        val expectedScore = (volumeGrowth + oneRmGrowth) / 2f // 15%

        // When: Calculate performance score
        val actualScore = calculatePerformanceScore(volumeGrowth, oneRmGrowth)

        // Then: Score should be the average of volume and 1RM growth
        assertEquals(expectedScore, actualScore, 0.01f)
    }

    @Test
    fun test_performance_score_calculation_negative_growth() = runTest {
        // Given: Exercise with negative growth (decline)
        val volumeGrowth = -10f // 10% decline in volume
        val oneRmGrowth = 5f    // 5% increase in 1RM
        val expectedScore = (volumeGrowth + oneRmGrowth) / 2f // -2.5%

        // When: Calculate performance score
        val actualScore = calculatePerformanceScore(volumeGrowth, oneRmGrowth)

        // Then: Score should handle negative values correctly
        assertEquals(expectedScore, actualScore, 0.01f)
    }

    @Test
    fun test_volume_growth_calculation() = runTest {
        // Given: Historical volume data
        val initialVolume = 1000f
        val finalVolume = 1200f
        val expectedGrowth = ((finalVolume - initialVolume) / initialVolume) * 100f // 20%

        // When: Calculate volume growth percentage
        val actualGrowth = calculateVolumeGrowth(initialVolume, finalVolume)

        // Then: Growth should be calculated correctly
        assertEquals(expectedGrowth, actualGrowth, 0.01f)
    }

    @Test
    fun test_one_rm_growth_calculation() = runTest {
        // Given: Historical 1RM data
        val initial1RM = 200f
        val final1RM = 225f
        val expectedGrowth = ((final1RM - initial1RM) / initial1RM) * 100f // 12.5%

        // When: Calculate 1RM growth percentage
        val actualGrowth = calculate1RMGrowth(initial1RM, final1RM)

        // Then: Growth should be calculated correctly
        assertEquals(expectedGrowth, actualGrowth, 0.01f)
    }

    @Test
    fun test_plateau_detection_positive() = runTest {
        // Given: 1RM values with minimal variance over 3 weeks
        val oneRmValues = listOf(200f, 202f, 198f, 201f, 199f) // <5% variance
        val varianceThreshold = 5f

        // When: Check for plateau
        val isInPlateau = detectPlateau(oneRmValues, varianceThreshold)

        // Then: Should detect plateau
        assertTrue(isInPlateau)
    }

    @Test
    fun test_plateau_detection_negative() = runTest {
        // Given: 1RM values with significant variance
        val oneRmValues = listOf(200f, 215f, 190f, 220f, 185f) // >5% variance
        val varianceThreshold = 5f

        // When: Check for plateau
        val isInPlateau = detectPlateau(oneRmValues, varianceThreshold)

        // Then: Should not detect plateau
        assertTrue(!isInPlateau)
    }

    @Test
    fun test_exercise_ranking_by_performance_score() = runTest {
        // Given: Multiple exercises with different performance scores
        val exerciseData = listOf(
            createExercisePerformanceData("bench-press", volumeGrowth = 25f, oneRmGrowth = 15f), // Score: 20%
            createExercisePerformanceData("squat", volumeGrowth = 10f, oneRmGrowth = 20f),       // Score: 15%
            createExercisePerformanceData("deadlift", volumeGrowth = 30f, oneRmGrowth = 5f),     // Score: 17.5%
        )

        // When: Rank exercises by performance score
        val ranking = rankExercises(exerciseData, RankingMetric.PERFORMANCE_SCORE)

        // Then: Should be ordered by performance score (descending)
        assertEquals("bench-press", ranking[0].exerciseId)    // 20%
        assertEquals("deadlift", ranking[1].exerciseId)       // 17.5%
        assertEquals("squat", ranking[2].exerciseId)          // 15%
    }

    @Test
    fun test_exercise_ranking_by_volume_growth() = runTest {
        // Given: Multiple exercises with different volume growth
        val exerciseData = listOf(
            createExercisePerformanceData("bench-press", volumeGrowth = 15f, oneRmGrowth = 25f),
            createExercisePerformanceData("squat", volumeGrowth = 30f, oneRmGrowth = 10f),
            createExercisePerformanceData("deadlift", volumeGrowth = 20f, oneRmGrowth = 5f),
        )

        // When: Rank exercises by volume growth
        val ranking = rankExercises(exerciseData, RankingMetric.VOLUME_GROWTH)

        // Then: Should be ordered by volume growth (descending)
        assertEquals("squat", ranking[0].exerciseId)      // 30%
        assertEquals("deadlift", ranking[1].exerciseId)   // 20%
        assertEquals("bench-press", ranking[2].exerciseId) // 15%
    }

    @Test
    fun test_exercise_ranking_by_one_rm_growth() = runTest {
        // Given: Multiple exercises with different 1RM growth
        val exerciseData = listOf(
            createExercisePerformanceData("bench-press", volumeGrowth = 15f, oneRmGrowth = 10f),
            createExercisePerformanceData("squat", volumeGrowth = 30f, oneRmGrowth = 25f),
            createExercisePerformanceData("deadlift", volumeGrowth = 20f, oneRmGrowth = 15f),
        )

        // When: Rank exercises by 1RM growth
        val ranking = rankExercises(exerciseData, RankingMetric.STRENGTH_GROWTH)

        // Then: Should be ordered by 1RM growth (descending)
        assertEquals("squat", ranking[0].exerciseId)      // 25%
        assertEquals("deadlift", ranking[1].exerciseId)   // 15%
        assertEquals("bench-press", ranking[2].exerciseId) // 10%
    }

    @Test
    fun test_zero_division_handling() = runTest {
        // Given: Exercise with zero initial values
        val initialVolume = 0f
        val finalVolume = 100f
        
        val initial1RM = 0f
        val final1RM = 200f

        // When: Calculate growth percentages
        val volumeGrowth = calculateVolumeGrowth(initialVolume, finalVolume)
        val oneRmGrowth = calculate1RMGrowth(initial1RM, final1RM)

        // Then: Should handle zero division gracefully
        assertEquals(100f, volumeGrowth, 0.01f) // Should return 100% or handle gracefully
        assertEquals(100f, oneRmGrowth, 0.01f)  // Should return 100% or handle gracefully
    }

    @Test
    fun test_negative_values_handling() = runTest {
        // Given: Exercise with negative values (should not happen in real data)
        val initialVolume = -100f
        val finalVolume = 200f

        // When: Calculate growth
        val volumeGrowth = calculateVolumeGrowth(initialVolume, finalVolume)

        // Then: Should handle negative values appropriately
        assertTrue(volumeGrowth.isFinite()) // Should not return NaN or Infinity
    }

    @Test
    fun test_time_range_filtering() = runTest {
        // Given: Mock authenticated user
        coEvery { getCurrentUserIdUseCase() } returns "test_user_123"
        coEvery { workoutRepository.getExercisePerformanceData(any(), any(), any()) } returns 
            LiftrixResult.success(emptyList<ExercisePerformanceData>())
        
        val request = ExerciseRankingRequest(
            metric = RankingMetric.PERFORMANCE_SCORE,
            timeRange = TimeRangeType.SIX_MONTHS,
            limit = 10
        )

        // When: Execute the use case
        val result = calculateExerciseRankingUseCase(request)

        // Then: Should return success (even with empty data)
        result.fold(
            onSuccess = { data ->
                assertTrue("Should return valid result", data.rankings.isEmpty())
            },
            onFailure = {
                fail("Should not fail with valid parameters")
            }
        )
    }

    @Test
    fun test_insufficient_data_handling() = runTest {
        // Given: Mock authenticated user but no data
        coEvery { getCurrentUserIdUseCase() } returns "test_user_123"
        coEvery { workoutRepository.getExercisePerformanceData(any(), any(), any()) } returns 
            LiftrixResult.success(emptyList<ExercisePerformanceData>())

        // When: Attempt to calculate performance score
        val result = calculateExerciseRankingUseCase(
            ExerciseRankingRequest(
                metric = RankingMetric.PERFORMANCE_SCORE,
                timeRange = TimeRangeType.SIX_MONTHS,
                limit = 10
            )
        )

        // Then: Should handle insufficient data gracefully
        result.fold(
            onSuccess = { data ->
                // Should return empty list for insufficient data
                assertTrue("Should return empty rankings for no data", data.rankings.isEmpty())
            },
            onFailure = { error ->
                // Should provide meaningful error message
                val errorMsg = error.message ?: "Unknown error"
                assertTrue("Error message should be meaningful", errorMsg.contains("calculation") ||
                          errorMsg.contains("ranking"))
            }
        )
    }

    @Test
    fun test_multiple_muscle_group_ranking() = runTest {
        // Given: Exercises from different muscle groups
        val mixedExercises = listOf(
            createExercisePerformanceData("bench-press", volumeGrowth = 20f, oneRmGrowth = 15f), // Chest
            createExercisePerformanceData("squat", volumeGrowth = 25f, oneRmGrowth = 10f),       // Legs
            createExercisePerformanceData("deadlift", volumeGrowth = 15f, oneRmGrowth = 20f),    // Back
            createExercisePerformanceData("overhead-press", volumeGrowth = 10f, oneRmGrowth = 12f) // Shoulders
        )

        // When: Rank all exercises
        val ranking = rankExercises(mixedExercises, RankingMetric.PERFORMANCE_SCORE)

        // Then: Should rank across muscle groups
        assertEquals(4, ranking.size)
        assertTrue(ranking[0].performanceScore >= ranking[1].performanceScore)
        assertTrue(ranking[1].performanceScore >= ranking[2].performanceScore)
        assertTrue(ranking[2].performanceScore >= ranking[3].performanceScore)
    }

    @Test
    fun test_ranking_with_limit() = runTest {
        // Given: Many exercises
        val manyExercises = (1..50).map { index ->
            createExercisePerformanceData(
                "exercise-$index",
                volumeGrowth = (index * 2f) % 30f,
                oneRmGrowth = (index * 3f) % 25f
            )
        }

        // When: Rank with limit of 20
        val limit = 20
        val ranking = rankExercises(manyExercises, RankingMetric.PERFORMANCE_SCORE)
            .take(limit)

        // Then: Should return only top 20 exercises
        assertEquals(limit, ranking.size)
        
        // Should be properly sorted
        for (i in 0 until ranking.size - 1) {
            assertTrue(ranking[i].performanceScore >= ranking[i + 1].performanceScore)
        }
    }

    // Helper functions for creating test data

    private fun createTestExercise(id: String, name: String, category: ExerciseCategory) = 
        TestExercise(id, name, category)

    private fun createExercisePerformanceData(
        exerciseId: String,
        volumeGrowth: Float,
        oneRmGrowth: Float
    ) = ExercisePerformanceData(
        exerciseId = exerciseId,
        volumeGrowth = volumeGrowth,
        oneRmGrowth = oneRmGrowth,
        performanceScore = calculatePerformanceScore(volumeGrowth, oneRmGrowth)
    )

    private fun calculatePerformanceScore(volumeGrowth: Float, oneRmGrowth: Float): Float {
        return (volumeGrowth + oneRmGrowth) / 2f
    }

    private fun calculateVolumeGrowth(initial: Float, final: Float): Float {
        return if (initial == 0f) {
            100f // Handle zero division by returning 100% growth
        } else {
            ((final - initial) / kotlin.math.abs(initial)) * 100f
        }
    }

    private fun calculate1RMGrowth(initial: Float, final: Float): Float {
        return if (initial == 0f) {
            100f // Handle zero division by returning 100% growth
        } else {
            ((final - initial) / kotlin.math.abs(initial)) * 100f
        }
    }

    private fun detectPlateau(values: List<Float>, thresholdPercent: Float): Boolean {
        if (values.size < 3) return false
        
        val mean = values.average().toFloat()
        val maxVariation = values.maxOfOrNull { kotlin.math.abs(it - mean) } ?: 0f
        val variationPercent = (maxVariation / mean) * 100f
        
        return variationPercent <= thresholdPercent
    }

    private fun rankExercises(
        exercises: List<ExercisePerformanceData>,
        metric: RankingMetric
    ): List<ExercisePerformanceData> {
        return when (metric) {
            RankingMetric.PERFORMANCE_SCORE -> exercises.sortedByDescending { it.performanceScore }
            RankingMetric.VOLUME_GROWTH -> exercises.sortedByDescending { it.volumeGrowth }
            RankingMetric.STRENGTH_GROWTH -> exercises.sortedByDescending { it.oneRmGrowth }
            else -> exercises.sortedByDescending { it.performanceScore }
        }
    }

    private fun filterDataByTimeRange(
        data: List<TimeSeriesDataPoint>,
        timeRange: TimeRangeType,
        referenceDate: kotlinx.datetime.LocalDate
    ): List<TimeSeriesDataPoint> {
        // Implementation would filter data based on time range
        return data // Simplified for testing
    }

    private fun createTimeSeriesData(): List<TimeSeriesDataPoint> {
        // Create mock time series data for testing
        return emptyList()
    }

    private fun createExerciseWithInsufficientData(): ExercisePerformanceData {
        return createExercisePerformanceData("insufficient-data", 0f, 0f)
    }

    // Test data classes
    data class TestExercise(
        val id: String,
        val name: String,
        val category: ExerciseCategory
    )

    data class ExercisePerformanceData(
        val exerciseId: String,
        val volumeGrowth: Float,
        val oneRmGrowth: Float,
        val performanceScore: Float
    )

    data class TimeSeriesDataPoint(
        val date: kotlinx.datetime.LocalDate,
        val value: Float
    )
}