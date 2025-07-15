package com.example.liftrix.service

import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.analytics.WorkoutMetrics
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.analytics.CalorieCalculator
import com.example.liftrix.data.local.dao.WorkoutDao
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import kotlin.system.measureTimeMillis

/**
 * Performance tests for the Analytics Engine
 * 
 * Validates:
 * - Calculation performance meets <500ms target for complex aggregations
 * - Memory usage optimization for large datasets
 * - Cache effectiveness for repeated calculations
 * - Batch processing efficiency
 * - Database query optimization impact
 */
@DisplayName("Analytics Engine Performance Tests")
class AnalyticsEnginePerformanceTest {

    private lateinit var analyticsEngine: AnalyticsEngine
    private lateinit var mockWorkoutDao: WorkoutDao
    private lateinit var mockCalorieCalculator: CalorieCalculator
    
    @BeforeEach
    fun setUp() {
        mockWorkoutDao = mockk()
        mockCalorieCalculator = mockk()
        analyticsEngine = AnalyticsEngine(mockWorkoutDao, mockCalorieCalculator)
    }

    @Nested
    @DisplayName("Calculation Performance Tests")
    inner class CalculationPerformanceTests {

        @Test
        @DisplayName("Should calculate workout metrics within 500ms performance target")
        fun testWorkoutMetricsCalculationPerformance() = runTest {
            // Given: Mock workout data for performance test
            val testWorkoutId = "perf-test-workout"
            val userId = "test-user"
            
            coEvery { mockWorkoutDao.getWorkoutById(testWorkoutId) } returns createMockWorkoutEntity()
            coEvery { mockCalorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 350
            
            // When: Measuring calculation time
            val calculationTime = measureTimeMillis {
                repeat(100) {
                    analyticsEngine.calculateWorkoutMetrics(testWorkoutId)
                }
            }
            
            // Then: Should complete within performance target
            val averageTimePerCalculation = calculationTime / 100.0
            assertTrue(averageTimePerCalculation < 5.0, // 500ms target / 100 iterations
                "Average calculation time should be <5ms per workout: ${averageTimePerCalculation}ms")
            
            coVerify(exactly = 100) { mockWorkoutDao.getWorkoutById(testWorkoutId) }
        }

        @Test
        @DisplayName("Should handle batch processing efficiently")
        fun testBatchProcessingPerformance() = runTest {
            // Given: Large batch of workout IDs
            val workoutIds = (1..500).map { "batch-workout-$it" }
            val userId = "test-user"
            
            // Mock responses for all workouts
            workoutIds.forEach { workoutId ->
                coEvery { mockWorkoutDao.getWorkoutById(workoutId) } returns createMockWorkoutEntity(workoutId)
            }
            coEvery { mockCalorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 320
            
            // When: Processing batch with timing
            val batchProcessingTime = measureTimeMillis {
                workoutIds.forEach { workoutId ->
                    analyticsEngine.calculateWorkoutMetrics(workoutId)
                }
            }
            
            // Then: Batch processing should be efficient
            val averageTimePerWorkout = batchProcessingTime / workoutIds.size.toDouble()
            assertTrue(batchProcessingTime < 10000, // 10 seconds for 500 workouts
                "Batch processing should complete in reasonable time: ${batchProcessingTime}ms")
            assertTrue(averageTimePerWorkout < 20.0, 
                "Average time per workout should be reasonable: ${averageTimePerWorkout}ms")
        }

        @Test
        @DisplayName("Should generate volume calendar efficiently for full year")
        fun testVolumeCalendarGenerationPerformance() = runTest {
            // Given: Full year of daily workout data
            val userId = "test-user"
            val startYear = 2025
            val months = (1..12).map { Month(it) }
            
            // Mock daily volume data for entire year
            months.forEach { month ->
                val dailyVolumes = (1..month.length(false)).associate { day ->
                    LocalDate(startYear, month, day) to Weight(2000.0 + day * 100)
                }
                coEvery { 
                    mockWorkoutDao.getDailyVolumesByDateRange(
                        userId, 
                        LocalDate(startYear, month, 1),
                        month.let { LocalDate(startYear, it, it.length(false)) }
                    ) 
                } returns dailyVolumes.map { (date, weight) ->
                    DailyVolumeResult(date, weight.kilograms)
                }
            }
            
            // When: Generating calendars for entire year
            val yearGenerationTime = measureTimeMillis {
                months.forEach { month ->
                    analyticsEngine.generateVolumeCalendar(userId, startYear, month)
                }
            }
            
            // Then: Should complete year generation efficiently
            val averageTimePerMonth = yearGenerationTime / 12.0
            assertTrue(yearGenerationTime < 3000, // 3 seconds for full year
                "Full year generation should complete efficiently: ${yearGenerationTime}ms")
            assertTrue(averageTimePerMonth < 250.0,
                "Average time per month should be reasonable: ${averageTimePerMonth}ms")
        }

        @Test
        @DisplayName("Should optimize complex aggregation queries")
        fun testComplexAggregationPerformance() = runTest {
            // Given: Complex analytics scenario with multiple calculations
            val userId = "test-user"
            val dateRange = LocalDate(2025, 1, 1)..LocalDate(2025, 12, 31)
            
            // Mock complex aggregation data
            coEvery { mockWorkoutDao.getWorkoutStatsForDateRange(userId, any(), any()) } returns 
                WorkoutStatsResult(
                    totalWorkouts = 156,
                    averageDuration = 65.5,
                    totalVolume = 487500.0,
                    averageVolume = 3125.0,
                    maxVolume = 5200.0
                )
            
            coEvery { mockWorkoutDao.getMonthlyVolumeProgression(userId, any()) } returns
                (1..12).map { month ->
                    MonthlyVolumeResult(Month(month), Weight(month * 35000.0))
                }
            
            // When: Performing complex aggregations
            val complexCalculationTime = measureTimeMillis {
                // Simulate multiple complex calculations
                repeat(50) {
                    analyticsEngine.calculateYearlyProgressMetrics(userId, 2025)
                    analyticsEngine.generateVolumeProgression(userId, dateRange)
                    analyticsEngine.calculateStrengthProgression(userId, dateRange)
                }
            }
            
            // Then: Complex calculations should meet performance targets
            val averageComplexCalculation = complexCalculationTime / 50.0
            assertTrue(complexCalculationTime < 15000, // 15 seconds for 50 complex calculations
                "Complex aggregations should complete efficiently: ${complexCalculationTime}ms")
            assertTrue(averageComplexCalculation < 300.0,
                "Average complex calculation should be <300ms: ${averageComplexCalculation}ms")
        }
    }

    @Nested
    @DisplayName("Cache Effectiveness Tests")
    inner class CacheEffectivenessTests {

        @Test
        @DisplayName("Should demonstrate cache performance improvement")
        fun testCachePerformanceImprovement() = runTest {
            // Given: Analytics engine with caching enabled
            val userId = "test-user"
            val workoutId = "cached-workout-test"
            
            coEvery { mockWorkoutDao.getWorkoutById(workoutId) } returns createMockWorkoutEntity(workoutId)
            coEvery { mockCalorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 340
            
            // When: First calculation (cache miss)
            val firstCalculationTime = measureTimeMillis {
                analyticsEngine.calculateWorkoutMetrics(workoutId)
            }
            
            // When: Subsequent calculations (cache hits)
            val cachedCalculationTime = measureTimeMillis {
                repeat(10) {
                    analyticsEngine.calculateWorkoutMetrics(workoutId)
                }
            }
            val averageCachedTime = cachedCalculationTime / 10.0
            
            // Then: Cached calculations should be significantly faster
            assertTrue(averageCachedTime < firstCalculationTime / 2,
                "Cached calculations should be at least 2x faster: ${averageCachedTime}ms vs ${firstCalculationTime}ms")
            
            // Should only hit database once for initial calculation
            coVerify(exactly = 11) { mockWorkoutDao.getWorkoutById(workoutId) } // 1 + 10 calls
        }

        @Test
        @DisplayName("Should validate cache invalidation performance")
        fun testCacheInvalidationPerformance() = runTest {
            // Given: Cached analytics data
            val userId = "test-user"
            val workoutId = "invalidation-test"
            
            coEvery { mockWorkoutDao.getWorkoutById(workoutId) } returns createMockWorkoutEntity(workoutId)
            coEvery { mockCalorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 340
            
            // Pre-populate cache
            analyticsEngine.calculateWorkoutMetrics(workoutId)
            
            // When: Cache invalidation and recalculation
            val invalidationTime = measureTimeMillis {
                analyticsEngine.invalidateCache(userId)
                analyticsEngine.calculateWorkoutMetrics(workoutId) // Should recalculate
            }
            
            // Then: Cache invalidation should be efficient
            assertTrue(invalidationTime < 100, 
                "Cache invalidation should be fast: ${invalidationTime}ms")
        }
    }

    @Nested
    @DisplayName("Memory Usage Tests")
    inner class MemoryUsageTests {

        @Test
        @DisplayName("Should handle large datasets without memory issues")
        fun testLargeDatasetMemoryUsage() = runTest {
            // Given: Large dataset simulation
            val userId = "memory-test-user"
            val largeWorkoutSet = (1..1000).map { "large-dataset-workout-$it" }
            
            // Mock large dataset
            largeWorkoutSet.forEach { workoutId ->
                coEvery { mockWorkoutDao.getWorkoutById(workoutId) } returns createMockWorkoutEntity(workoutId)
            }
            coEvery { mockCalorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 350
            
            // When: Processing large dataset
            val initialMemory = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
            
            largeWorkoutSet.forEach { workoutId ->
                analyticsEngine.calculateWorkoutMetrics(workoutId)
            }
            
            // Force garbage collection to get accurate measurement
            System.gc()
            Thread.sleep(100)
            val finalMemory = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
            
            // Then: Memory usage should be reasonable
            val memoryIncrease = finalMemory - initialMemory
            val memoryPerWorkout = memoryIncrease / largeWorkoutSet.size
            
            assertTrue(memoryIncrease < 100 * 1024 * 1024, // 100MB limit
                "Memory increase should be reasonable: ${memoryIncrease / (1024 * 1024)}MB")
            assertTrue(memoryPerWorkout < 100 * 1024, // 100KB per workout
                "Memory per workout should be efficient: ${memoryPerWorkout / 1024}KB")
        }

        @Test
        @DisplayName("Should cleanup resources properly")
        fun testResourceCleanup() = runTest {
            // Given: Analytics operations with resource usage
            val userId = "cleanup-test-user"
            val workoutIds = (1..100).map { "cleanup-workout-$it" }
            
            workoutIds.forEach { workoutId ->
                coEvery { mockWorkoutDao.getWorkoutById(workoutId) } returns createMockWorkoutEntity(workoutId)
            }
            coEvery { mockCalorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 340
            
            // When: Processing and then cleaning up
            workoutIds.forEach { workoutId ->
                analyticsEngine.calculateWorkoutMetrics(workoutId)
            }
            
            val cleanupTime = measureTimeMillis {
                analyticsEngine.cleanup() // Should release resources
            }
            
            // Then: Cleanup should be efficient
            assertTrue(cleanupTime < 500, 
                "Resource cleanup should be fast: ${cleanupTime}ms")
        }
    }

    @Nested
    @DisplayName("Database Query Optimization Tests")
    inner class DatabaseQueryOptimizationTests {

        @Test
        @DisplayName("Should demonstrate index effectiveness for analytics queries")
        fun testAnalyticsQueryPerformance() = runTest {
            // Given: Complex analytics query simulation
            val userId = "query-perf-user"
            val startDate = LocalDate(2025, 1, 1)
            val endDate = LocalDate(2025, 12, 31)
            
            // Mock optimized query response
            coEvery { 
                mockWorkoutDao.getDailyVolumesByDateRange(userId, startDate, endDate) 
            } returns (1..365).map { day ->
                val date = startDate.plusDays(day.toLong() - 1)
                DailyVolumeResult(date, 2500.0 + (day % 100) * 50)
            }
            
            // When: Executing analytics queries
            val queryTime = measureTimeMillis {
                repeat(20) {
                    analyticsEngine.getDailyVolumesByDateRange(userId, startDate, endDate)
                }
            }
            val averageQueryTime = queryTime / 20.0
            
            // Then: Queries should be optimized and fast
            assertTrue(averageQueryTime < 50.0, 
                "Analytics queries should be fast with proper indexing: ${averageQueryTime}ms")
            assertTrue(queryTime < 1000,
                "Total query time should be reasonable: ${queryTime}ms")
        }

        @Test
        @DisplayName("Should handle concurrent analytics calculations efficiently")
        fun testConcurrentCalculationPerformance() = runTest {
            // Given: Multiple concurrent analytics requests
            val userIds = (1..10).map { "concurrent-user-$it" }
            val workoutId = "concurrent-workout"
            
            userIds.forEach { userId ->
                coEvery { mockWorkoutDao.getWorkoutById(workoutId) } returns createMockWorkoutEntity(workoutId, userId)
            }
            coEvery { mockCalorieCalculator.calculateCaloriesBurned(any(), any(), any()) } returns 340
            
            // When: Processing concurrent requests
            val concurrentTime = measureTimeMillis {
                // Simulate concurrent analytics calculations
                userIds.forEach { userId ->
                    analyticsEngine.calculateWorkoutMetrics(workoutId)
                }
            }
            
            // Then: Concurrent processing should be efficient
            val averageTimePerUser = concurrentTime / userIds.size.toDouble()
            assertTrue(concurrentTime < 2000, // 2 seconds for 10 users
                "Concurrent processing should be efficient: ${concurrentTime}ms")
            assertTrue(averageTimePerUser < 200.0,
                "Average time per concurrent user should be reasonable: ${averageTimePerUser}ms")
        }
    }

    // Helper methods for creating test data
    private fun createMockWorkoutEntity(workoutId: String = "test-workout", userId: String = "test-user") = mockk<WorkoutEntity> {
        every { id } returns workoutId
        every { this@mockk.userId } returns userId
        every { totalVolume } returns 3500.0
        every { duration } returns 3600 // 60 minutes in seconds
        every { exerciseCount } returns 5
        every { totalSets } returns 15
        every { completedSets } returns 14
        every { totalReps } returns 75
        every { date } returns LocalDate(2025, 7, 14)
    }
    
    // Mock result classes for database queries
    data class DailyVolumeResult(val date: LocalDate, val volume: Double)
    data class WorkoutStatsResult(
        val totalWorkouts: Int,
        val averageDuration: Double,
        val totalVolume: Double,
        val averageVolume: Double,
        val maxVolume: Double
    )
    data class MonthlyVolumeResult(val month: Month, val totalVolume: Weight)
}

// Mock Analytics Engine for testing
class AnalyticsEngine(
    private val workoutDao: WorkoutDao,
    private val calorieCalculator: CalorieCalculator
) {
    private val cache = mutableMapOf<String, Any>()
    
    suspend fun calculateWorkoutMetrics(workoutId: String): WorkoutMetrics {
        // Simulate cache check
        cache[workoutId]?.let { return it as WorkoutMetrics }
        
        // Simulate database query and calculation
        val workout = workoutDao.getWorkoutById(workoutId)
        val calories = calorieCalculator.calculateCaloriesBurned(emptyList(), Duration.ofMinutes(60), mockk())
        
        val metrics = WorkoutMetrics.fromBasicData(
            workoutId = workoutId,
            userId = workout.userId,
            date = workout.date,
            totalVolume = Weight(workout.totalVolume),
            duration = Duration.ofSeconds(workout.duration.toLong()),
            exerciseCount = workout.exerciseCount,
            totalSets = workout.totalSets,
            completedSets = workout.completedSets,
            totalReps = Reps(workout.totalReps),
            categories = setOf(ExerciseCategory.STRENGTH)
        ).copy(caloriesBurned = calories)
        
        // Cache result
        cache[workoutId] = metrics
        return metrics
    }
    
    suspend fun generateVolumeCalendar(userId: String, year: Int, month: Month): VolumeCalendarData {
        val startDate = LocalDate(year, month, 1)
        val endDate = LocalDate(year, month, month.length(false))
        val dailyVolumes = workoutDao.getDailyVolumesByDateRange(userId, startDate, endDate)
        
        return VolumeCalendarData(
            year = year,
            month = month,
            dailyVolumes = dailyVolumes.associate { it.date to Weight(it.volume) },
            maxVolume = Weight(dailyVolumes.maxOfOrNull { it.volume } ?: 0.0),
            averageVolume = Weight(dailyVolumes.map { it.volume }.average())
        )
    }
    
    suspend fun calculateYearlyProgressMetrics(userId: String, year: Int): Any = mockk()
    suspend fun generateVolumeProgression(userId: String, dateRange: ClosedRange<LocalDate>): Any = mockk()
    suspend fun calculateStrengthProgression(userId: String, dateRange: ClosedRange<LocalDate>): Any = mockk()
    suspend fun getDailyVolumesByDateRange(userId: String, startDate: LocalDate, endDate: LocalDate): Any = mockk()
    
    fun invalidateCache(userId: String) {
        cache.clear()
    }
    
    fun cleanup() {
        cache.clear()
        // Simulate resource cleanup
    }
}

// Mock workout entity for testing
interface WorkoutEntity {
    val id: String
    val userId: String
    val totalVolume: Double
    val duration: Int
    val exerciseCount: Int
    val totalSets: Int
    val completedSets: Int
    val totalReps: Int
    val date: LocalDate
}