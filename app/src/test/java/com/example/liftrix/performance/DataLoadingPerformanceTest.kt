package com.example.liftrix.performance

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.core.cache.CacheManager
import com.example.liftrix.core.cache.CacheKey
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.service.AnalyticsServiceImpl
import com.example.liftrix.service.CalorieServiceImpl
import com.example.liftrix.service.ProgressDataServiceImpl
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes

/**
 * Data loading performance validation tests to ensure response time targets are met:
 * - Service response times under 500ms for standard operations
 * - Cache hit rate above 80% for repeated operations
 * - Database query performance optimization
 * - Concurrent operation performance
 * - Large dataset handling efficiency
 * 
 * These tests validate the performance optimizations implemented in PERF-002 through PERF-004.
 */
@RunWith(AndroidJUnit4::class)
class DataLoadingPerformanceTest {
    
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    companion object {
        private const val SERVICE_RESPONSE_TARGET_MS = 500L
        private const val CACHE_HIT_RATE_TARGET = 80.0
        private const val LARGE_DATASET_TARGET_MS = 1000L
        private const val CONCURRENT_OPERATIONS_TARGET_MS = 2000L
    }
    
    @Test
    fun `progress data service response time meets target`() = runTest {
        val mockRepository = createMockProgressStatsRepository()
        val cacheManager = CacheManager()
        val service = ProgressDataServiceImpl(mockRepository, cacheManager, Dispatchers.IO)
        
        // Warm up
        service.getVolumeData("test_user", TimeRange.MONTH)
        
        val startTime = System.currentTimeMillis()
        
        val result = service.getVolumeData("test_user", TimeRange.MONTH)
        
        val responseTime = System.currentTimeMillis() - startTime
        
        assertTrue(
            "Progress data service response time ${responseTime}ms exceeds target ${SERVICE_RESPONSE_TARGET_MS}ms",
            responseTime < SERVICE_RESPONSE_TARGET_MS
        )
        
        assertTrue("Service should return success", result is LiftrixResult.Success)
    }
    
    @Test
    fun `analytics service response time meets target`() = runTest {
        val mockWidgetManager = mockk<com.example.liftrix.service.AnalyticsWidgetManager>(relaxed = true)
        val mockPreferencesRepository = mockk<com.example.liftrix.domain.repository.WidgetPreferencesRepository>(relaxed = true)
        val mockAnalyticsEngine = mockk<com.example.liftrix.service.AnalyticsEngine>(relaxed = true)
        
        coEvery { mockAnalyticsEngine.calculateWidgetData(any(), any()) } returns mockk(relaxed = true)
        
        val service = AnalyticsServiceImpl(mockWidgetManager, mockPreferencesRepository, mockAnalyticsEngine)
        
        val startTime = System.currentTimeMillis()
        
        val result = service.getWidgetData("test_user", AnalyticsWidget.TotalVolume)
        
        val responseTime = System.currentTimeMillis() - startTime
        
        assertTrue(
            "Analytics service response time ${responseTime}ms exceeds target ${SERVICE_RESPONSE_TARGET_MS}ms",
            responseTime < SERVICE_RESPONSE_TARGET_MS
        )
        
        assertTrue("Service should return success", result is LiftrixResult.Success)
    }
    
    @Test
    fun `calorie service response time meets target`() = runTest {
        val mockCalculator = mockk<com.example.liftrix.domain.model.analytics.CalorieCalculator>(relaxed = true)
        val mockMetRepository = mockk<com.example.liftrix.domain.repository.MetDataRepository>(relaxed = true)
        
        coEvery { mockCalculator.calculateCalorieSummary(any()) } returns mockk(relaxed = true)
        
        val service = CalorieServiceImpl(mockCalculator, mockMetRepository, Dispatchers.IO)
        
        val startTime = System.currentTimeMillis()
        
        val result = service.getCalorieSummary("test_user")
        
        val responseTime = System.currentTimeMillis() - startTime
        
        assertTrue(
            "Calorie service response time ${responseTime}ms exceeds target ${SERVICE_RESPONSE_TARGET_MS}ms",
            responseTime < SERVICE_RESPONSE_TARGET_MS
        )
        
        assertTrue("Service should return success", result is LiftrixResult.Success)
    }
    
    @Test
    fun `cache hit rate meets target for repeated operations`() = runTest {
        val mockRepository = createMockProgressStatsRepository()
        val cacheManager = CacheManager()
        val service = ProgressDataServiceImpl(mockRepository, cacheManager, Dispatchers.IO)
        
        val userId = "test_user"
        val timeRange = TimeRange.MONTH
        val totalOperations = 100
        var cacheHits = 0
        
        // Perform initial request to populate cache
        service.getVolumeData(userId, timeRange)
        
        // Perform repeated operations
        repeat(totalOperations) {
            val startTime = System.nanoTime()
            service.getVolumeData(userId, timeRange)
            val responseTime = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms
            
            // Fast response indicates cache hit
            if (responseTime < 10) { // 10ms threshold for cache hit
                cacheHits++
            }
        }
        
        val cacheHitRate = (cacheHits.toDouble() / totalOperations) * 100
        
        assertTrue(
            "Cache hit rate ${cacheHitRate}% is below target ${CACHE_HIT_RATE_TARGET}%",
            cacheHitRate >= CACHE_HIT_RATE_TARGET
        )
    }
    
    @Test
    fun `large dataset loading performance`() = runTest {
        val mockRepository = createMockProgressStatsRepositoryWithLargeDataset()
        val cacheManager = CacheManager()
        val service = ProgressDataServiceImpl(mockRepository, cacheManager, Dispatchers.IO)
        
        val startTime = System.currentTimeMillis()
        
        val result = service.getVolumeData("test_user", TimeRange.YEAR) // Large dataset
        
        val responseTime = System.currentTimeMillis() - startTime
        
        assertTrue(
            "Large dataset loading time ${responseTime}ms exceeds target ${LARGE_DATASET_TARGET_MS}ms",
            responseTime < LARGE_DATASET_TARGET_MS
        )
        
        assertTrue("Service should handle large dataset", result is LiftrixResult.Success)
    }
    
    @Test
    fun `concurrent service operations performance`() = runTest {
        val mockRepository = createMockProgressStatsRepository()
        val cacheManager = CacheManager()
        val service = ProgressDataServiceImpl(mockRepository, cacheManager, Dispatchers.IO)
        
        val startTime = System.currentTimeMillis()
        
        // Simulate concurrent operations from multiple users
        val operations = List(20) { index ->
            async {
                service.getVolumeData("user_$index", TimeRange.MONTH)
                service.getProgressSummary("user_$index")
                service.getDurationData("user_$index", TimeRange.MONTH)
            }
        }
        
        operations.forEach { it.await() }
        
        val totalTime = System.currentTimeMillis() - startTime
        
        assertTrue(
            "Concurrent operations time ${totalTime}ms exceeds target ${CONCURRENT_OPERATIONS_TARGET_MS}ms",
            totalTime < CONCURRENT_OPERATIONS_TARGET_MS
        )
    }
    
    @Test
    fun `cache memory efficiency with large datasets`() = runTest {
        val cacheManager = CacheManager(maxSize = 50, defaultTtl = 15.minutes)
        
        val startTime = System.currentTimeMillis()
        
        // Fill cache with large datasets
        repeat(100) { index ->
            val key = CacheKey.VolumeData("user_$index", TimeRange.MONTH)
            val largeData = generateLargeVolumeData(1000) // 1000 data points
            cacheManager.put(key, largeData)
        }
        
        val cacheOperationTime = System.currentTimeMillis() - startTime
        
        // Cache operations should be efficient even with large data
        assertTrue(
            "Cache operations with large datasets took ${cacheOperationTime}ms, too slow",
            cacheOperationTime < 200L // 200ms for cache operations
        )
        
        // Verify cache size limit is respected
        var itemCount = 0
        repeat(100) { index ->
            val key = CacheKey.VolumeData("user_$index", TimeRange.MONTH)
            if (cacheManager.get<List<VolumeDataPoint>>(key) != null) {
                itemCount++
            }
        }
        
        assertTrue(
            "Cache should respect size limit, found $itemCount items (expected ≤ 50)",
            itemCount <= 50
        )
    }
    
    @Test
    fun `memoization performance with expensive calculations`() = runTest {
        val memoizationCache = com.example.liftrix.core.cache.MemoizationCache<String, Double>()
        
        // First calculation (cache miss)
        val startTime1 = System.currentTimeMillis()
        val result1 = memoizationCache.memoize("expensive_calc") {
            // Simulate expensive calculation
            Thread.sleep(100)
            Math.PI * 1000
        }
        val time1 = System.currentTimeMillis() - startTime1
        
        // Second calculation (cache hit)
        val startTime2 = System.currentTimeMillis()
        val result2 = memoizationCache.memoize("expensive_calc") {
            // This should not execute due to memoization
            Thread.sleep(100)
            Math.PI * 1000
        }
        val time2 = System.currentTimeMillis() - startTime2
        
        assertTrue("Results should be identical", result1 == result2)
        assertTrue("First calculation should take significant time", time1 >= 100)
        assertTrue("Second calculation should be fast due to memoization", time2 < 10)
    }
    
    @Test
    fun `service layer benchmarking`() {
        val mockRepository = createMockProgressStatsRepository()
        val cacheManager = CacheManager()
        val service = ProgressDataServiceImpl(mockRepository, cacheManager, Dispatchers.IO)
        
        benchmarkRule.measureRepeated {
            runBlocking {
                service.getVolumeData("benchmark_user", TimeRange.MONTH)
            }
        }
    }
    
    private fun createMockProgressStatsRepository(): ProgressStatsRepository {
        return mockk<ProgressStatsRepository>().apply {
            coEvery { getWorkoutVolumeData(any(), any(), any()) } returns flowOf(
                generateTestVolumeData(30)
            )
            coEvery { getProgressSummary(any()) } returns flowOf(
                mockk(relaxed = true)
            )
            coEvery { getWorkoutDurationData(any(), any(), any()) } returns flowOf(
                generateTestDurationData(30)
            )
            coEvery { getWorkoutFrequencyData(any(), any(), any()) } returns flowOf(
                generateTestFrequencyData(30)
            )
        }
    }
    
    private fun createMockProgressStatsRepositoryWithLargeDataset(): ProgressStatsRepository {
        return mockk<ProgressStatsRepository>().apply {
            coEvery { getWorkoutVolumeData(any(), any(), any()) } returns flowOf(
                generateLargeVolumeData(365) // One year of data
            )
            coEvery { getProgressSummary(any()) } returns flowOf(
                mockk(relaxed = true)
            )
        }
    }
    
    private fun generateTestVolumeData(size: Int): List<VolumeDataPoint> {
        return (1..size).map { index ->
            VolumeDataPoint(
                date = LocalDate(2024, 1, index % 28 + 1),
                volume = (index * 100).toDouble()
            )
        }
    }
    
    private fun generateLargeVolumeData(size: Int): List<VolumeDataPoint> {
        return (1..size).map { index ->
            VolumeDataPoint(
                date = LocalDate(2024, 1, index % 28 + 1),
                volume = (index * 100 + Math.random() * 500).toDouble()
            )
        }
    }
    
    private fun generateTestDurationData(size: Int): List<com.example.liftrix.domain.repository.DurationDataPoint> {
        return (1..size).map { index ->
            com.example.liftrix.domain.repository.DurationDataPoint(
                date = LocalDate(2024, 1, index % 28 + 1),
                duration = index * 60 // seconds
            )
        }
    }
    
    private fun generateTestFrequencyData(size: Int): List<com.example.liftrix.domain.repository.FrequencyDataPoint> {
        return (1..size).map { index ->
            com.example.liftrix.domain.repository.FrequencyDataPoint(
                date = LocalDate(2024, 1, index % 28 + 1),
                workoutCount = index % 7 + 1
            )
        }
    }
}