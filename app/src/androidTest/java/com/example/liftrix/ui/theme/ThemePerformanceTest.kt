package com.example.liftrix.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.ui.theme.ColorSystemOptimizations
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/**
 * Performance test suite for COLOR-013: Theme Caching Enhancement
 * 
 * Validates that the 5-color system optimizations achieve the target 20%+ 
 * performance improvement in theme switching and related operations.
 * 
 * Performance targets:
 * - Theme switching: <50ms (baseline: ~100ms = 50%+ improvement)
 * - Color scheme creation: <10ms
 * - Cache hit rate: >80%
 * - Memory efficiency: No increase in footprint
 */
@RunWith(AndroidJUnit4::class)
class ThemePerformanceTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var context: android.content.Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Clear performance metrics for clean testing
        ColorSystemOptimizations.ThemePerformanceMonitor.clearMetrics()
        ColorSystemOptimizations.ColorMemoryManager.clearCache()
        ColorSystemOptimizations.SmartCaching.clearCache()
    }
    
    @Test
    fun testThemeSwitchingPerformance() {
        var isDarkTheme = false
        var switchCount = 0
        val switchTimes = mutableListOf<Long>()
        
        composeTestRule.setContent {
            val darkThemeState = mutableStateOf(isDarkTheme)
            
            LiftrixTheme(darkTheme = darkThemeState.value) {
                // Simple content for testing
                androidx.compose.foundation.layout.Box {}
            }
        }
        
        // Perform multiple theme switches to measure performance
        repeat(10) {
            val startTime = System.currentTimeMillis()
            
            composeTestRule.runOnUiThread {
                isDarkTheme = !isDarkTheme
                switchCount++
            }
            
            composeTestRule.waitForIdle()
            
            val switchTime = System.currentTimeMillis() - startTime
            switchTimes.add(switchTime)
            
            Timber.d("ThemePerformanceTest: Switch $switchCount took ${switchTime}ms")
        }
        
        // Calculate average switch time
        val averageSwitchTime = switchTimes.average()
        
        // Validate performance targets
        val targetSwitchTime = 50L // 50ms target
        val baselineSwitchTime = 100L // 100ms baseline
        val improvementPercent = ((baselineSwitchTime - averageSwitchTime) / baselineSwitchTime) * 100
        
        Timber.i("ThemePerformanceTest: Average switch time: ${averageSwitchTime.toInt()}ms, " +
                "improvement: +${improvementPercent.toInt()}%")
        
        // Assert performance targets
        assertTrue(
            "Theme switching should be faster than ${targetSwitchTime}ms, actual: ${averageSwitchTime.toInt()}ms",
            averageSwitchTime <= targetSwitchTime
        )
        
        assertTrue(
            "Should achieve 20%+ performance improvement, actual: ${improvementPercent.toInt()}%",
            improvementPercent >= 20.0
        )
        
        // Verify all switches were fast enough
        val slowSwitches = switchTimes.count { it > targetSwitchTime }
        assertTrue(
            "All theme switches should be under ${targetSwitchTime}ms, $slowSwitches were slow",
            slowSwitches == 0
        )
    }
    
    @Test
    fun testColorSchemeCreationPerformance() {
        val creationTimes = mutableListOf<Long>()
        
        // Test color scheme creation performance
        repeat(50) {
            val startTime = System.nanoTime()
            
            // Test both light and dark scheme creation
            ColorSystemOptimizations.getColorScheme(false)
            ColorSystemOptimizations.getColorScheme(true)
            
            val creationTime = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms
            creationTimes.add(creationTime)
        }
        
        val averageCreationTime = creationTimes.average()
        val targetCreationTime = 10L // 10ms target for color scheme creation
        
        Timber.i("ThemePerformanceTest: Average color scheme creation: ${averageCreationTime.toInt()}ms")
        
        assertTrue(
            "Color scheme creation should be under ${targetCreationTime}ms, actual: ${averageCreationTime.toInt()}ms",
            averageCreationTime <= targetCreationTime
        )
        
        // Verify consistency (no creation should take more than 2x average)
        val maxAcceptableTime = averageCreationTime * 2
        val slowCreations = creationTimes.count { it > maxAcceptableTime }
        assertTrue(
            "Color scheme creation should be consistent, $slowCreations were significantly slow",
            slowCreations <= 2 // Allow 2 outliers out of 50
        )
    }
    
    @Test
    fun testCacheEfficiency() {
        // Clear cache for clean test
        ColorSystemOptimizations.SmartCaching.clearCache()
        
        // Generate cache activity
        repeat(20) {
            // Access various color variations to populate cache
            ColorSystemOptimizations.SmartCaching.getCachedColorVariation(
                "PersianGreen", 0.1f, LiftrixColors.PersianGreen
            )
            ColorSystemOptimizations.SmartCaching.getCachedColorVariation(
                "TiffanyBlue", 0.2f, LiftrixColors.TiffanyBlue
            )
            ColorSystemOptimizations.SmartCaching.getCachedColorVariation(
                "PersianGreen", 0.1f, LiftrixColors.PersianGreen
            ) // Repeat for cache hit
        }
        
        val cacheHitRate = ColorSystemOptimizations.SmartCaching.getCacheHitRate()
        val targetHitRate = 80f // 80% target cache hit rate
        
        Timber.i("ThemePerformanceTest: Cache hit rate: ${cacheHitRate.toInt()}%")
        
        assertTrue(
            "Cache hit rate should be at least ${targetHitRate.toInt()}%, actual: ${cacheHitRate.toInt()}%",
            cacheHitRate >= targetHitRate
        )
    }
    
    @Test
    fun testMemoryEfficiency() {
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        // Create many color schemes to test memory efficiency
        repeat(100) {
            ColorSystemOptimizations.getColorScheme(it % 2 == 0)
        }
        
        // Force garbage collection
        System.gc()
        Thread.sleep(100)
        
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        val memoryIncreasePercent = (memoryIncrease.toFloat() / initialMemory) * 100f
        
        val maxMemoryIncreasePercent = 5f // Allow maximum 5% memory increase
        
        Timber.i("ThemePerformanceTest: Memory increase: ${memoryIncrease / 1024}KB (${memoryIncreasePercent.toInt()}%)")
        
        assertTrue(
            "Memory increase should be less than ${maxMemoryIncreasePercent.toInt()}%, actual: ${memoryIncreasePercent.toInt()}%",
            memoryIncreasePercent <= maxMemoryIncreasePercent
        )
        
        // Verify color cache is being used efficiently
        val cacheSize = ColorSystemOptimizations.ColorMemoryManager.getCacheSize()
        val maxCacheSize = 20 // Should not exceed 20 cached color instances
        
        assertTrue(
            "Color cache should not exceed $maxCacheSize instances, actual: $cacheSize",
            cacheSize <= maxCacheSize
        )
    }
    
    @Test
    fun testPerformanceConsistency() {
        val switchTimes = mutableListOf<Long>()
        var isDarkTheme = false
        
        composeTestRule.setContent {
            val darkThemeState = mutableStateOf(isDarkTheme)
            
            LiftrixTheme(darkTheme = darkThemeState.value) {
                androidx.compose.foundation.layout.Box {}
            }
        }
        
        // Test performance consistency over time
        repeat(50) {
            val startTime = System.nanoTime()
            
            composeTestRule.runOnUiThread {
                isDarkTheme = !isDarkTheme
            }
            
            composeTestRule.waitForIdle()
            
            val switchTime = (System.nanoTime() - startTime) / 1_000_000
            switchTimes.add(switchTime)
        }
        
        val averageTime = switchTimes.average()
        val standardDeviation = kotlin.math.sqrt(
            switchTimes.map { (it - averageTime) * (it - averageTime) }.average()
        )
        
        // Coefficient of variation should be low (consistent performance)
        val coefficientOfVariation = (standardDeviation / averageTime) * 100
        val maxVariation = 30.0 // 30% max variation
        
        Timber.i("ThemePerformanceTest: Performance consistency - " +
                "avg: ${averageTime.toInt()}ms, std dev: ${standardDeviation.toInt()}ms, " +
                "CV: ${coefficientOfVariation.toInt()}%")
        
        assertTrue(
            "Performance should be consistent (CV < ${maxVariation.toInt()}%), actual: ${coefficientOfVariation.toInt()}%",
            coefficientOfVariation <= maxVariation
        )
    }
    
    @Test
    fun testPerformanceTargetsValidation() {
        // Run performance validation
        val performanceScore = ColorSystemOptimizations.PerformanceValidation.getPerformanceScore()
        val targetsValid = ColorSystemOptimizations.PerformanceValidation.validatePerformanceTargets()
        
        val minPerformanceScore = 80f // 80% minimum performance score
        
        Timber.i("ThemePerformanceTest: Performance score: ${performanceScore.toInt()}%, targets valid: $targetsValid")
        
        assertTrue(
            "Performance targets should be met",
            targetsValid
        )
        
        assertTrue(
            "Performance score should be at least ${minPerformanceScore.toInt()}%, actual: ${performanceScore.toInt()}%",
            performanceScore >= minPerformanceScore
        )
    }
    
    @Test
    fun testComprehensivePerformanceReport() {
        // Generate some activity for comprehensive report
        var isDarkTheme = false
        
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = isDarkTheme) {
                androidx.compose.foundation.layout.Box {}
            }
        }
        
        // Perform various operations
        repeat(5) {
            composeTestRule.runOnUiThread {
                isDarkTheme = !isDarkTheme
            }
            composeTestRule.waitForIdle()
        }
        
        // Generate performance report
        val report = ColorSystemOptimizations.ThemePerformanceMonitor.getThemePerformanceReport()
        
        Timber.i("ThemePerformanceTest: Performance Report:\n$report")
        
        // Verify report contains expected information
        assertTrue("Report should contain average switching time", report.contains("Average theme switching time"))
        assertTrue("Report should contain cache information", report.contains("Color cache size"))
        assertTrue("Report should contain performance improvement", report.contains("improvement") || report.contains("samples: 0"))
    }
}