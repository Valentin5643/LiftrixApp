package com.example.liftrix.performance

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.core.cache.CacheManager
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.service.AnalyticsService
import com.example.liftrix.service.ProgressDataService
import com.example.liftrix.ui.progress.AnalyticsWidgetViewModel
import com.example.liftrix.ui.progress.ProgressChartsViewModel
import com.example.liftrix.ui.progress.UserPreferencesViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes

/**
 * Memory usage validation tests to ensure performance targets are met:
 * - Memory usage within acceptable limits (50MB increase max)
 * - Flow collection memory leak prevention
 * - ViewModel lifecycle memory management
 * - Cache memory usage optimization
 * 
 * These tests validate the memory optimizations implemented in PERF-001 through PERF-004.
 */
@RunWith(AndroidJUnit4::class)
class MemoryUsageTest {
    
    companion object {
        private const val MEMORY_INCREASE_LIMIT_MB = 50L
        private const val MEMORY_LEAK_THRESHOLD_MB = 10L
        private const val CACHE_MEMORY_LIMIT_MB = 25L
    }
    
    @Test
    fun `progress dashboard ViewModels memory usage is within limits`() = runTest {
        val memoryBefore = getMemoryUsage()
        
        // Create multiple ViewModels simulating dashboard loading
        val progressDataService = createMockProgressDataService()
        val analyticsService = createMockAnalyticsService()
        val authRepository = createMockAuthRepository()
        val errorHandler = createMockErrorHandler()
        
        val chartsViewModel = ProgressChartsViewModel(
            progressDataService = progressDataService,
            authRepository = authRepository,
            errorHandler = errorHandler
        )
        
        val widgetViewModel = AnalyticsWidgetViewModel(
            analyticsService = analyticsService,
            authRepository = authRepository,
            errorHandler = errorHandler
        )
        
        val preferencesViewModel = UserPreferencesViewModel(
            preferencesService = mockk(relaxed = true),
            errorHandler = errorHandler
        )
        
        // Simulate typical dashboard operations
        chartsViewModel.handleEvent(
            com.example.liftrix.ui.progress.ProgressChartsEvent.RefreshChart(
                com.example.liftrix.ui.progress.components.ChartType.VOLUME
            )
        )
        
        widgetViewModel.handleEvent(
            com.example.liftrix.ui.progress.AnalyticsWidgetEvent.LoadWidget("total_volume")
        )
        
        // Wait for state updates
        advanceTimeBy(1000)
        
        val memoryAfter = getMemoryUsage()
        val memoryIncrease = (memoryAfter - memoryBefore) / (1024 * 1024) // Convert to MB
        
        // Verify memory increase is within acceptable limits
        assertTrue(
            "Memory usage increased by ${memoryIncrease}MB, exceeds ${MEMORY_INCREASE_LIMIT_MB}MB limit",
            memoryIncrease < MEMORY_INCREASE_LIMIT_MB
        )
        
        // Clean up
        chartsViewModel.viewModelScope.cancel()
        widgetViewModel.viewModelScope.cancel()
        preferencesViewModel.viewModelScope.cancel()
    }
    
    @Test
    fun `Flow collection does not leak memory after ViewModel destruction`() = runTest {
        val memoryBefore = getMemoryUsage()
        
        // Create and destroy ViewModels multiple times to detect leaks
        repeat(5) {
            val progressDataService = createMockProgressDataService()
            val authRepository = createMockAuthRepository()
            val errorHandler = createMockErrorHandler()
            
            val viewModel = ProgressChartsViewModel(
                progressDataService = progressDataService,
                authRepository = authRepository,
                errorHandler = errorHandler
            )
            
            // Trigger Flow collection
            viewModel.handleEvent(
                com.example.liftrix.ui.progress.ProgressChartsEvent.RefreshAll
            )
            
            // Simulate state collection
            advanceTimeBy(100)
            
            // Properly cancel ViewModel scope
            viewModel.viewModelScope.cancel()
        }
        
        // Force garbage collection
        System.gc()
        Thread.sleep(500)
        System.gc()
        
        val memoryAfter = getMemoryUsage()
        val memoryIncrease = (memoryAfter - memoryBefore) / (1024 * 1024) // Convert to MB
        
        // Verify no significant memory increase (indicating leaks)
        assertTrue(
            "Memory leak detected: ${memoryIncrease}MB increase after ViewModel cleanup",
            memoryIncrease < MEMORY_LEAK_THRESHOLD_MB
        )
    }
    
    @Test
    fun `cache manager memory usage is bounded and efficient`() = runTest {
        val memoryBefore = getMemoryUsage()
        
        // Create cache manager with realistic size
        val cacheManager = CacheManager(maxSize = 100, defaultTtl = 15.minutes)
        
        // Fill cache with test data
        repeat(150) { index ->
            val key = com.example.liftrix.core.cache.CacheKey.VolumeData(
                userId = "user$index",
                timeRange = TimeRange.MONTH
            )
            val data = generateLargeTestData(1024) // 1KB test data
            cacheManager.put(key, data)
        }
        
        val memoryAfter = getMemoryUsage()
        val memoryIncrease = (memoryAfter - memoryBefore) / (1024 * 1024) // Convert to MB
        
        // Verify cache memory usage is bounded
        assertTrue(
            "Cache memory usage ${memoryIncrease}MB exceeds ${CACHE_MEMORY_LIMIT_MB}MB limit",
            memoryIncrease < CACHE_MEMORY_LIMIT_MB
        )
        
        // Verify LRU eviction worked (should only have 100 items)
        var itemCount = 0
        repeat(150) { index ->
            val key = com.example.liftrix.core.cache.CacheKey.VolumeData(
                userId = "user$index",
                timeRange = TimeRange.MONTH
            )
            if (cacheManager.get<ByteArray>(key) != null) {
                itemCount++
            }
        }
        
        assertTrue(
            "Cache should have evicted items, found $itemCount items (expected ≤ 100)",
            itemCount <= 100
        )
    }
    
    @Test
    fun `service layer memory usage with concurrent operations`() = runTest {
        val memoryBefore = getMemoryUsage()
        
        val progressDataService = createMockProgressDataService()
        val analyticsService = createMockAnalyticsService()
        
        // Simulate concurrent service operations
        val operations = List(20) {
            async {
                progressDataService.getVolumeData("user$it", TimeRange.MONTH)
                analyticsService.getWidgetData("user$it", com.example.liftrix.domain.model.analytics.AnalyticsWidget.TotalVolume)
                progressDataService.getProgressSummary("user$it")
            }
        }
        
        // Wait for all operations to complete
        operations.forEach { it.await() }
        
        val memoryAfter = getMemoryUsage()
        val memoryIncrease = (memoryAfter - memoryBefore) / (1024 * 1024) // Convert to MB
        
        // Verify concurrent operations don't cause excessive memory usage
        assertTrue(
            "Concurrent service operations memory usage ${memoryIncrease}MB exceeds limit",
            memoryIncrease < 30L // 30MB limit for concurrent operations
        )
    }
    
    @Test
    fun `memoization cache memory usage is efficient`() = runTest {
        val memoryBefore = getMemoryUsage()
        
        val memoizationCache = com.example.liftrix.core.cache.MemoizationCache<String, ByteArray>()
        
        // Fill memoization cache with computation results
        repeat(50) { index ->
            val key = "computation_$index"
            val result = generateLargeTestData(2048) // 2KB test data
            memoizationCache.memoize(key) { result }
        }
        
        val memoryAfter = getMemoryUsage()
        val memoryIncrease = (memoryAfter - memoryBefore) / (1024 * 1024) // Convert to MB
        
        // Verify memoization cache memory is reasonable
        assertTrue(
            "Memoization cache memory ${memoryIncrease}MB exceeds reasonable limit",
            memoryIncrease < 15L // 15MB limit for memoization
        )
    }
    
    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
    
    private fun generateLargeTestData(sizeBytes: Int): ByteArray {
        return ByteArray(sizeBytes) { (it % 256).toByte() }
    }
    
    private fun createMockProgressDataService(): ProgressDataService {
        return mockk<ProgressDataService>().apply {
            coEvery { getVolumeData(any(), any()) } returns Result.success(emptyList())
            coEvery { getProgressSummary(any()) } returns Result.success(
                mockk(relaxed = true)
            )
            coEvery { getDurationData(any(), any()) } returns Result.success(emptyList())
            coEvery { getFrequencyData(any(), any()) } returns Result.success(emptyList())
        }
    }
    
    private fun createMockAnalyticsService(): AnalyticsService {
        return mockk<AnalyticsService>().apply {
            coEvery { getWidgetData(any(), any()) } returns Result.success(
                mockk(relaxed = true)
            )
            coEvery { getAllWidgetData(any()) } returns Result.success(emptyMap())
            coEvery { refreshWidgetData(any(), any()) } returns Result.success(Unit)
        }
    }
    
    private fun createMockAuthRepository(): AuthRepository {
        return mockk<AuthRepository>().apply {
            every { currentUser } returns flowOf(mockk(relaxed = true))
            every { currentUserId } returns flowOf("test_user_id")
            every { isAuthenticated } returns flowOf(true)
        }
    }
    
    private fun createMockErrorHandler(): ErrorHandler {
        return mockk<ErrorHandler>(relaxed = true)
    }
}